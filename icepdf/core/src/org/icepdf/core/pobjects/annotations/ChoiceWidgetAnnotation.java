/*
 * Copyright 2006-2014 ICEsoft Technologies Inc.
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
import org.icepdf.core.pobjects.acroform.VariableText;
import org.icepdf.core.pobjects.fonts.FontFile;
import org.icepdf.core.pobjects.graphics.Shapes;
import org.icepdf.core.pobjects.graphics.TextSprite;
import org.icepdf.core.pobjects.graphics.TextState;
import org.icepdf.core.pobjects.graphics.commands.ColorDrawCmd;
import org.icepdf.core.pobjects.graphics.commands.DrawCmd;
import org.icepdf.core.pobjects.graphics.commands.TextSpriteDrawCmd;
import org.icepdf.core.pobjects.graphics.commands.TransformDrawCmd;
import org.icepdf.core.util.Library;

import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * Represents a Acroform Choice widget and manages the appearance streams
 * for the various appearance states.
 *
 * @since 5.1
 */
public class ChoiceWidgetAnnotation extends AbstractWidgetAnnotation {

    public ChoiceWidgetAnnotation(Library l, HashMap h) {
        super(l, h);
        fieldDictionary = new ChoiceFieldDictionary(library, entries);
    }

    public void resetAppearanceStream(double dx, double dy, AffineTransform pageTransform) {
        final ChoiceFieldDictionary choiceFieldDictionary =
                (ChoiceFieldDictionary) fieldDictionary;
        ChoiceFieldDictionary.ChoiceFieldType choiceFieldType =
                choiceFieldDictionary.getChoiceFieldType();

        // clear previous shapes.
        Appearance appearance = appearances.get(currentAppearance);
        AppearanceState appearanceState = appearance.getSelectedAppearanceState();
        Rectangle2D bbox = appearanceState.getBbox();
        Shapes shapes = appearanceState.getShapes();
        VariableText variableText = fieldDictionary.getVariableText();
        String contents = (String) fieldDictionary.getFieldValue();
        FontFile fontFile = null;
        // remove previous text objects
        if (shapes == null) {
            shapes = new Shapes();
            appearanceState.setShapes(shapes);
        } else {
            // remove any previous text

            ArrayList<DrawCmd> drawShapes = appearanceState.getShapes().getShapes();
            DrawCmd tmp;
            for (int i = 0; i < drawShapes.size(); i++) {
                tmp = drawShapes.get(i);
                if (tmp instanceof TextSpriteDrawCmd) {
                    if (fontFile == null) {
                        TextSpriteDrawCmd cmd = (TextSpriteDrawCmd) tmp;
                        TextSprite textSprite = cmd.getTextSprite();
                        fontFile = textSprite.getFont();
                    }
                    drawShapes.remove(i);
                }
            }
        }

        if (choiceFieldType == ChoiceFieldDictionary.ChoiceFieldType.CHOICE_COMBO ||
                choiceFieldType == ChoiceFieldDictionary.ChoiceFieldType.CHOICE_EDITABLE_COMBO) {
            // find the first instance of TextSprite, update the text and remove
            // any other occurrences.
            // setup the space for the AP content stream.
            AffineTransform af = new AffineTransform();
            af.scale(1, -1);
            af.translate(-bbox.getMinX(), -bbox.getMaxY());

            // adjust of the border offset, offset is define in viewer,
            // so we can't use the constant because of dependency issues.
            double insets = 2 * pageTransform.getScaleX();
            af.translate(insets, -insets);
            shapes.add(new TransformDrawCmd(af));

            fontFile = fontFile.deriveFont(variableText.getSize());
            // init font's metrics
            fontFile.echarAdvance(' ');
            TextSprite textSprites =
                    new TextSprite(fontFile,
                            contents.length(),
                            new AffineTransform(),null);
            textSprites.setRMode(TextState.MODE_FILL);
            textSprites.setStrokeColor(variableText.getColor());
            textSprites.setFontName(variableText.getFontName());
            textSprites.setFontSize(variableText.getSize());

            float lineHeight = (float) (fontFile.getAscent());
            float advanceX = (float) bbox.getMinX();
            float advanceY = (float) bbox.getMinY();
            float currentX;
            float currentY = advanceY + lineHeight;

            float lastx = 0;
            float newAdvanceX;
            char currentChar;
            for (int i = 0, max = contents.length(); i < max; i++) {
                currentChar = contents.charAt(i);
                newAdvanceX = (float) fontFile.echarAdvance(currentChar).getX();
                currentX = advanceX + lastx;
                lastx += newAdvanceX;
                // get normalized from from text sprite
                textSprites.addText(
                        String.valueOf(currentChar), // cid
                        String.valueOf(currentChar), // unicode value
                        currentX, currentY, newAdvanceX);
            }
            // actual font.
            shapes.add(new ColorDrawCmd(variableText.getColor()));
            shapes.add(new TextSpriteDrawCmd(textSprites));

        } else if (choiceFieldType == ChoiceFieldDictionary.ChoiceFieldType.CHOICE_LIST_SINGLE_SELECT ||
                choiceFieldType == ChoiceFieldDictionary.ChoiceFieldType.CHOICE_LIST_MULTIPLE_SELECT) {
            // construct the postScript for a list component.

        }
    }

    public void reset() {

    }
}
