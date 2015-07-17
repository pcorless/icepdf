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

import org.icepdf.core.pobjects.acroform.TextFieldDictionary;
import org.icepdf.core.pobjects.fonts.FontFile;
import org.icepdf.core.pobjects.fonts.FontManager;
import org.icepdf.core.util.Library;

import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.util.HashMap;

/**
 * Text field (field type Text) is a box or space for text fill-in data typically
 * entered from a keyboard. The text may be restricted to a single line or may
 * be permitted to span multiple lines, depending on the setting of the Multi line
 * flag in the field dictionary’s Ff entry. Table 228 shows the flags pertaining
 * to this type of field. A text field shall have a field type of Text. A conforming
 * PDF file, and a conforming processor shall obey the usage guidelines as
 * defined by the big flags below.
 *
 * @since 5.1
 */
public class TextWidgetAnnotation extends AbstractWidgetAnnotation<TextFieldDictionary> {

    protected FontFile fontFile;

    private TextFieldDictionary fieldDictionary;

    public TextWidgetAnnotation(Library l, HashMap h) {
        super(l, h);
        fieldDictionary = new TextFieldDictionary(library, entries);
        fontFile = FontManager.getInstance().getInstance(fieldDictionary.getFontName(), 0);
    }

    public void resetAppearanceStream(double dx, double dy, AffineTransform pageTransform) {

        // we won't touch password fields, we'll used the orginal display
        TextFieldDictionary.TextFieldType textFieldType = fieldDictionary.getTextFieldType();
        if (textFieldType == TextFieldDictionary.TextFieldType.TEXT_PASSWORD) {
            // nothing to do, let the password comp handle the look.
        } else {
            // get at the original postscript as well alter the marked content
            Appearance appearance = appearances.get(currentAppearance);
            AppearanceState appearanceState = appearance.getSelectedAppearanceState();
            String currentContentStream = appearanceState.getOriginalContentStream();
            currentContentStream = buildTextWidgetContents(currentContentStream);

            // finally create the shapes from the altered stream.
            if (currentContentStream != null) {
                appearanceState.setContentStream(currentContentStream.getBytes());
            }
        }
    }

    public String buildTextWidgetContents(String currentContentStream) {

        // text widgets can be null, in this case we setup the default so we can add our own data.
        if (currentContentStream == null || currentContentStream.equals("")) {
            currentContentStream = " /Tx BMC q BT ET Q EMC";
        }
        String contents = (String) fieldDictionary.getFieldValue();
//        int btStart = currentContentStream.indexOf("BT") + 2;
//        int etEnd = currentContentStream.lastIndexOf("ET");
        int btStart = currentContentStream.indexOf("BMC") + 3;
        int etEnd = currentContentStream.lastIndexOf("EMC");

        String preBt = "";
        String postEt = "";
        String markedContent = "";
        if (btStart >= 0 && etEnd >= 0) {
            // grab the pre post marked content postscript.
            preBt = currentContentStream.substring(0, btStart) + " q BT ";
            postEt = " Q ET " + currentContentStream.substring(etEnd);
            // marked content which we will use to try and find some data points.
            markedContent = currentContentStream.substring(btStart, etEnd);
        } else {
            preBt = currentContentStream + " /Tx BMC q BT ";
            postEt = " ET Q EMC ";
        }

        // check for a bounding box definition
        Rectangle2D.Float bounds = findRectangle(preBt);
        boolean isfourthQuadrant = false;
        if (bounds != null && bounds.getHeight() < 0) {
            isfourthQuadrant = true;
        }

        // finally build out the new content stream
        StringBuilder content = new StringBuilder();
        // calculate line light
        double lineHeight = getLineHeight(fieldDictionary.getDefaultAppearance());

        // apply the default appearance.
        content.append(generateDefaultAppearance(markedContent, fieldDictionary.getDefaultAppearance()));

        // apply the text offset, 4 is just a generic padding.
        if (!isfourthQuadrant) {
            content.append(lineHeight).append(" TL ");
            // todo rework taking into account multi line height.
            content.append(2).append(' ').append((Math.round(getBbox().getHeight() * 100) / 100)).append(" Td ");
//            content.append(4).append(' ').append(lineHeight).append(" Td ");
        } else {
            content.append(2).append(' ').append(2).append(" Td ");
        }
        // encode the text so it can be properly encoded in PDF string format
        content = encodeLiteralString(content, contents);

        // build the final content stream.
        currentContentStream = preBt + " " + content + " " + postEt;
        return currentContentStream;
    }


    public void reset() {
        // set the  fields value (V) to the default value defined by the DV key.
        TextFieldDictionary textFieldDictionary = (TextFieldDictionary) fieldDictionary;
        textFieldDictionary.setFieldValue(textFieldDictionary.getDefaultFieldValue());
    }

    @Override
    public TextFieldDictionary getFieldDictionary() {
        return fieldDictionary;
    }
}
