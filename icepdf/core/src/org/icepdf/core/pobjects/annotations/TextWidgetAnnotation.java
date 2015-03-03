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
import org.icepdf.core.pobjects.acroform.VariableText;
import org.icepdf.core.pobjects.fonts.FontFile;
import org.icepdf.core.pobjects.fonts.FontManager;
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
 * Text field (field type Tx) is a box or space for text fill-in data typically
 * entered from a keyboard. The text may be restricted to a single line or may
 * be permitted to span multiple lines, depending on the setting of the Multi line
 * flag in the field dictionary’s Ff entry. Table 228 shows the flags pertaining
 * to this type of field. A text field shall have a field type of Tx. A conforming
 * PDF file, and a conforming processor shall obey the usage guidelines as
 * defined by the big flags below.
 *
 * @since 5.1
 */
public class TextWidgetAnnotation extends AbstractWidgetAnnotation {

    protected FontFile fontFile;

    public TextWidgetAnnotation(Library l, HashMap h) {
        super(l, h);
        fieldDictionary = new TextFieldDictionary(library, entries);
        VariableText variableText = fieldDictionary.getVariableText();
        fontFile = FontManager.getInstance().getInstance(variableText.getFontName(), 0);
    }

    public void resetAppearanceStream(double dx, double dy, AffineTransform pageTransform) {

        // we won't touch password fields, we'll used the orginal display
        TextFieldDictionary textFieldDictionary = (TextFieldDictionary) fieldDictionary;
        TextFieldDictionary.TextFieldType textFieldType = textFieldDictionary.getTextFieldType();
        if (textFieldType == TextFieldDictionary.TextFieldType.TEXT_PASSWORD) {
            return;
        }

        Appearance appearance = appearances.get(currentAppearance);
        AppearanceState appearanceState = appearance.getSelectedAppearanceState();
        Rectangle2D bbox = appearanceState.getBbox();
        Shapes shapes = appearanceState.getShapes();
        VariableText variableText = fieldDictionary.getVariableText();
        String contents = (String) fieldDictionary.getFieldValue();

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
                    drawShapes.remove(i);
                }
            }
        }

        // add new text object, the ideas is that we preserve any border information
        // that was part of the appearance stream.

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

        float lineHeight = (float) (fontFile.getAscent() + fontFile.getDescent());

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
            if (!(currentChar == '\n' || currentChar == '\r')) {
                textSprites.addText(
                        String.valueOf(currentChar), // cid
                        String.valueOf(currentChar), // unicode value
                        currentX, currentY, newAdvanceX);
            } else {
                // move back to start of next line
                currentY += lineHeight;
                advanceX = (float) bbox.getMinX();
                lastx = 0;
            }
        }
//        BasicStroke stroke;
//        if (strokeType && borderStyle.isStyleDashed()) {
//            stroke = new BasicStroke(
//                    borderStyle.getStrokeWidth(), BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
//                    borderStyle.getStrokeWidth() * 2.0f, borderStyle.getDashArray(), 0.0f);
//        } else {
//            stroke = new BasicStroke(borderStyle.getStrokeWidth());
//        }
//
//        // background colour
//        shapes.add(new ShapeDrawCmd(new Rectangle2D.Double(bbox.getX(), bbox.getY()+10,
//                bbox.getWidth()-10, bbox.getHeight()- 10)));
//        if (fillType) {
//            shapes.add(new ColorDrawCmd(fillColor));
//            shapes.add(new FillDrawCmd());
//        }
//        // border
//        if (strokeType) {
//            shapes.add(new StrokeDrawCmd(stroke));
//            shapes.add(new ColorDrawCmd(color));
//            shapes.add(new DrawDrawCmd());
//        }
        // actual font.
        shapes.add(new ColorDrawCmd(variableText.getColor()));
        shapes.add(new TextSpriteDrawCmd(textSprites));
    }


    public void reset() {
        // set the  fields value (V) to the default value defined by the DV key.
        TextFieldDictionary textFieldDictionary = (TextFieldDictionary) fieldDictionary;
        textFieldDictionary.setFieldValue(textFieldDictionary.getDefaultFieldValue());
    }
}
