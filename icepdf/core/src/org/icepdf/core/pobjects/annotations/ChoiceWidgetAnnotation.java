/*
 * Copyright 2006-2015 ICEsoft Technologies Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an "AS
 * IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */

package org.icepdf.core.pobjects.annotations;

import org.icepdf.core.pobjects.acroform.ChoiceFieldDictionary;
import org.icepdf.core.pobjects.graphics.Shapes;
import org.icepdf.core.pobjects.graphics.text.LineText;
import org.icepdf.core.pobjects.graphics.text.WordText;
import org.icepdf.core.util.Library;

import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.StringTokenizer;

import static org.icepdf.core.pobjects.acroform.ChoiceFieldDictionary.ChoiceFieldType;

/**
 * Represents a Acroform Choice widget and manages the appearance streams
 * for the various appearance states. This class can generate a postscript
 * stream that represents it current state.
 *
 * @since 5.1
 */
public class ChoiceWidgetAnnotation extends AbstractWidgetAnnotation<ChoiceFieldDictionary> {

    private ChoiceFieldDictionary fieldDictionary;

    public ChoiceWidgetAnnotation(Library l, HashMap h) {
        super(l, h);
        fieldDictionary = new ChoiceFieldDictionary(library, entries);
    }

    /**
     * Some choices lists are lacking the /opt key so we need to do our best to generate the list from the shapes.
     *
     * @return list of potential options.
     */
    public ArrayList<ChoiceFieldDictionary.ChoiceOption> generateChoices() {
        Shapes shapes = getShapes();
        if (shapes != null) {
            ArrayList<ChoiceFieldDictionary.ChoiceOption> options = new ArrayList<ChoiceFieldDictionary.ChoiceOption>();
            String tmp;
            ArrayList<LineText> pageLines = shapes.getPageText().getPageLines();
            for (LineText lines : pageLines) {
                for (WordText word : lines.getWords()) {
                    tmp = word.toString();
                    if (!(tmp.isEmpty() || tmp.equals(" "))) {
                        options.add(fieldDictionary.buildChoiceOption(tmp, tmp));
                    }
                }
            }
            return options;
        }
        return new ArrayList<ChoiceFieldDictionary.ChoiceOption>();
    }

    /**
     * Resets the appearance stream for this instance using the current state.  The mark content section of the stream
     * is found and the edit it make to best of our ability.
     * @param dx x offset of the annotation
     * @param dy y offset of the annotation
     * @param pageTransform current page transform.
     */
    public void resetAppearanceStream(double dx, double dy, AffineTransform pageTransform) {
        ChoiceFieldType choiceFieldType =
                fieldDictionary.getChoiceFieldType();

        // get at the original postscript as well alter the marked content
        Appearance appearance = appearances.get(currentAppearance);
        AppearanceState appearanceState = appearance.getSelectedAppearanceState();
        String currentContentStream = appearanceState.getOriginalContentStream();

        // alterations vary by choice type.
        if (choiceFieldType == ChoiceFieldType.CHOICE_COMBO ||
                choiceFieldType == ChoiceFieldType.CHOICE_EDITABLE_COMBO) {
            // relatively straight forward replace with new selected value.
            if (currentContentStream != null) {
                // remove first instance as we might have place holder text like 'select one'...
                int start = currentContentStream.indexOf('(');
                int end = currentContentStream.lastIndexOf(')');
                if (start >= 0 && end >= 0) {
                    String replace = currentContentStream.substring(start + 1, end);
                    String selectedField = (String) fieldDictionary.getFieldValue();
                    currentContentStream = currentContentStream.replace(replace, selectedField);
                }
            } else {
                // todo no stream and we will need to build one.
            }
        } else {
            // build out the complex choice list content stream
            currentContentStream = buildChoiceListContents(currentContentStream);
        }
        // finally create the shapes from the altered stream.
        if (currentContentStream != null) {
            appearanceState.setContentStream(currentContentStream.getBytes());
        }
    }


    public void reset() {
        // todo, default value and rest appearance stream.
    }

    @Override
    public ChoiceFieldDictionary getFieldDictionary() {
        return fieldDictionary;
    }

    public String buildChoiceListContents(String currentContentStream) {

        ArrayList<ChoiceFieldDictionary.ChoiceOption> choices = fieldDictionary.getOptions();
        // double check we have some choices to work with.
        if (choices == null) {
            // generate them from the content stream.
            choices = generateChoices();
            fieldDictionary.setOptions(choices);
        }
        int[] selections = fieldDictionary.getIndexes();
        // mark the indexes of the mark content.
        int bmcStart = currentContentStream.indexOf("BMC") + 3;
        int bmcEnd = currentContentStream.indexOf("EMC");
        // grab the pre post marked content postscript.
        String preBmc = currentContentStream.substring(0, bmcStart);
        String postEmc = currentContentStream.substring(bmcEnd);
        // marked content which we will use to try and find some data points.
        String markedContent = currentContentStream.substring(bmcStart, bmcEnd);

        // check for a bounding box definition
        Rectangle2D.Float bounds = findBoundRectangle(markedContent);

        // check to see if there is a selection box colour defined.
        float[] selectionColor = findSelectionColour(markedContent);

        // and finally look for a previous selection box,  this can be null, no default value
        Rectangle2D.Float selectionRectangle = findSelectionRectangle(markedContent);
        float lineHeight = 13.87f;
        if (selectionRectangle != null) {
            lineHeight = selectionRectangle.height;
        }

        // we need to plot out where the opt text is going to go as well as the background colour and text colour
        // for any selected items. So we update the choices model to reflect the current selection state.
        boolean isSelection = false;
        if (selections != null) {
            for (int i = 0, max = choices.size(); i < max; i++) {
                for (int selection : selections) {
                    if (selection == i) {
                        choices.get(i).setIsSelected(true);
                        isSelection = true;
                    } else {
                        choices.get(i).setIsSelected(false);
                    }
                }
            }
        }
        // figure out offset range to insure a single selection is always visible
        int startIndex = 0, endIndex = choices.size();
        if (selections != null && selections.length == 1) {
            int numberLines = (int)Math.floor(bounds.height/lineHeight);
            // check if list is smaller then number of lines
            int selectedIndex = selections[0];
            if (choices.size() < numberLines){
                // nothing to do.
            }
            else if (selectedIndex < numberLines){
                endIndex  = numberLines + 1;
            }
            // check for bottom out range
            else if (endIndex - selectedIndex <= numberLines){
                startIndex = endIndex - numberLines;
            }
            // else mid range just need to start the index.
            else{
                startIndex = selectedIndex;
                endIndex = numberLines + 1;

            }
            // we have a single line
            if (startIndex > endIndex){
                endIndex = startIndex + 1;
            }
        }

        // finally build out the new content stream
        StringBuilder content = new StringBuilder();
        // bounding rectangle.
        content.append("q ").append(generateRectangle(bounds)).append("W n ");
        // apply selection highlight background.
        if (isSelection) {
            // apply colour
            content.append(selectionColor[0]).append(' ').append(selectionColor[1]).append(' ')
                    .append(selectionColor[2]).append(" rg ");
            // apply selection
            Rectangle2D.Float firstSelection;
            if (selectionRectangle == null) {
                firstSelection = new Rectangle2D.Float(bounds.x, bounds.y + bounds.height - lineHeight, bounds.width, lineHeight);
            } else {
                firstSelection = new Rectangle2D.Float(selectionRectangle.x, bounds.y + bounds.height - lineHeight,
                        selectionRectangle.width, lineHeight);
            }
            ChoiceFieldDictionary.ChoiceOption choice;
            for (int i = startIndex; i < endIndex; i++){
                choice = choices.get(i);
                // check if a selection rectangle was defined, if not we might have a custom style and we
                // avoid the selection background (only have one test case for this)
                if (choice.isSelected() && selectionRectangle != null) {
                    content.append(generateRectangle(firstSelection)).append("f ");
                }
                firstSelection.y -= lineHeight;
            }
        }
        // apply the ext.
        content.append("BT ");
        // apply font
        if (fieldDictionary.getDefaultAppearance() != null) {
            content.append(fieldDictionary.getDefaultAppearance());
        }else{ // common font and colour layout for most form elements.
            content.append("/Helv 12 Tf 0 g ");
        }
        // apply the line height
        content.append(lineHeight).append(" TL ");
        // apply the text offset, 4 is just a generic padding.
        content.append(4).append(' ').append(bounds.height + 4).append(" Td ");
        // print out text
        ChoiceFieldDictionary.ChoiceOption choice;
        for (int i = startIndex; i < endIndex; i++){
            choice = choices.get(i);
            if (choice.isSelected() && selectionRectangle != null) {
                content.append("1 g ");
            } else {
                content.append("0 g ");
            }
            content.append('(').append(choice.getLabel()).append(")' ");
        }
        content.append("ET Q");
        // build the final content stream.
        currentContentStream = preBmc + "\n" + content + "\n" + postEmc;
        System.out.println(content);

        return currentContentStream;
    }

    /**
     * Generally immediately after the BMC there is a rectangle that defines the actual size of the annotation.  If
     * found we can use this to make many assumptions and regenerate the content stream.
     *
     * @param markedContent content stream of the marked content.
     * @return a rectable either way, if the q # # # # re isn't found then we use the bbox as a potential bound.
     */
    private Rectangle2D.Float findBoundRectangle(String markedContent) {
        int selectionStart = markedContent.indexOf("q") + 1;
        int selectionEnd = markedContent.indexOf("re");
        if (selectionStart < selectionEnd && selectionEnd > 0) {
            String potentialNumbers = markedContent.substring(selectionStart, selectionEnd);
            float[] points = parseRectanglePoints(potentialNumbers);
            if (points != null) {
                return new Rectangle2D.Float(points[0], points[1], points[2], points[3]);
            }
        }
        // default to the bounding box.
        Rectangle2D bbox = getBbox();
        return new Rectangle2D.Float(1, 1, (float) bbox.getWidth(), (float) bbox.getHeight());
    }

    /**
     * The selection rectangle if present will help define the line height of the text.  If not present we can use
     * the default value 13.87 later which seems to be very common in the samples.
     *
     * @param markedContent content to look for "rg # # # # re".
     * @return selection rectangle, null if not found.
     */
    private Rectangle2D.Float findSelectionRectangle(String markedContent) {
        int selectionStart = markedContent.indexOf("rg") + 2;
        int selectionEnd = markedContent.lastIndexOf("re");
        if (selectionStart < selectionEnd && selectionEnd > 0) {
            String potentialNumbers = markedContent.substring(selectionStart, selectionEnd);
            float[] points = parseRectanglePoints(potentialNumbers);
            if (points != null) {
                return new Rectangle2D.Float(points[0], points[1], points[2], points[3]);
            }
        }
        return null;
    }

    /**
     * Simple utility to write Rectangle2D.Float in postscript.
     * @param rect Rectangle2D.Float to convert to postscript. Null value with throw null pointer exception.
     * @return postscript representation of the rect.
     */
    private String generateRectangle(Rectangle2D.Float rect) {
        return rect.x + " " + rect.y + " " + rect.width + " " + rect.height + " re ";
    }

    /**
     * Converts a given string of four numbers into an array of floats. If a conversion error is encountered
     * null value is returned.
     * @param potentialNumbers space separated string of four numbers.
     * @return list of four numbers, null if string can not be converted.
     */
    private float[] parseRectanglePoints(String potentialNumbers) {
        StringTokenizer toker = new StringTokenizer(potentialNumbers);
        float[] points = new float[4];
        int i = 0;
        while (toker.hasMoreTokens() && i < 4) {
            try {
                float tmp = Float.parseFloat(toker.nextToken());
                points[i] = tmp;
                i++;
            } catch (NumberFormatException e) {
                break;
            }
        }
        if (i == 4) {
            return points;
        } else {
            return null;
        }
    }

    /**
     * The selection colour is generally defined in DeviceRGB and occurs after the bounding box has been defined.
     * This utility method tries to parse out the colour information and return it in float[3].  If the data can't
     * be found then we return the default colour of new float[]{0.03922f, 0.14118f, 0.41569f}.
     *
     * @param markedContent content to look for colour info.
     * @return found colour data or new float[]{0.03922f, 0.14118f, 0.41569f}.
     */
    private float[] findSelectionColour(String markedContent) {
        int selectionStart = markedContent.indexOf("n") + 1;
        int selectionEnd = markedContent.lastIndexOf("rg");
        if (selectionStart < selectionEnd && selectionEnd > 0) {
            String potentialNumbers = markedContent.substring(selectionStart, selectionEnd);
            StringTokenizer toker = new StringTokenizer(potentialNumbers);
            float[] points = new float[3];
            int i = 0;
            while (toker.hasMoreTokens()) {
                try {
                    float tmp = Float.parseFloat(toker.nextToken());
                    points[i] = tmp;
                    i++;
                } catch (NumberFormatException e) {
                    break;
                }
            }
            if (i == 3) {
                return points;
            }
        }
        // default selection colour.
        return new float[]{0.03922f, 0.14118f, 0.41569f};
    }

}

