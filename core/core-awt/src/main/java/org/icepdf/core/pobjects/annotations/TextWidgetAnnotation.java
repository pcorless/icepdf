/*
 * Copyright 2006-2019 ICEsoft Technologies Canada Corp.
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

import java.nio.charset.StandardCharsets;
import org.icepdf.core.pobjects.*;
import org.icepdf.core.pobjects.acroform.FieldDictionary;
import org.icepdf.core.pobjects.acroform.TextFieldDictionary;
import org.icepdf.core.pobjects.acroform.VariableTextFieldDictionary;
import org.icepdf.core.pobjects.fonts.FontFile;
import org.icepdf.core.pobjects.fonts.FontManager;
import org.icepdf.core.pobjects.fonts.zfont.Encoding;
import org.icepdf.core.util.Library;

import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.util.logging.Level;

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

    private final TextFieldDictionary fieldDictionary;

    public TextWidgetAnnotation(Library l, DictionaryEntries h) {
        super(l, h);
        fieldDictionary = new TextFieldDictionary(library, entries);
        fontFile = fieldDictionary.getFont() != null ? fieldDictionary.getFont().getFont() : null;
        if (fontFile == null) {
            fontFile = FontManager.getInstance().initialize().getInstance(
                    fieldDictionary.getFontName().toString(), 0);
            fontFile = fontFile.deriveFont(Encoding.standardEncoding, null);
        }
    }

    /**
     * Rebuilds the appearance from the field's value: font, colour and size from {@code /DA},
     * quadding, padding and baseline as Acrobat lays them out, wrapping for multi-line fields, cells
     * for comb fields, and {@code /MK} border and background.  A password field keeps its appearance.
     */
    public void resetAppearanceStream(double dx, double dy, AffineTransform pageTransform) {
        if (fieldDictionary.getTextFieldType() == TextFieldDictionary.TextFieldType.TEXT_PASSWORD) {
            // nothing to do, let the password comp handle the look.
            return;
        }
        regenerateFieldAppearance();
    }

    /**
     * The appearance content as the field used to build it, splicing a single text operator into the
     * existing stream.  {@link #resetAppearanceStream} no longer uses it.
     *
     * @param currentContentStream the existing appearance content
     * @return the content with the field's value
     */
    public String buildTextWidgetContents(String currentContentStream) {

        // text widgets can be null, in this case we setup the default so we can add our own data.
        if (currentContentStream == null || currentContentStream.equals("")) {
            currentContentStream = " /Tx BMC q BT ET Q EMC";
        }
        String contents = (String) fieldDictionary.getFieldValue();
        int btStart = currentContentStream.indexOf("BMC") + 3;
        int etEnd = currentContentStream.lastIndexOf("EMC");

        String preBt;
        String postEt;
        String markedContent = "";
        if (btStart >= 0 && etEnd >= 0) {
            // grab the pre post marked content postscript.
            preBt = currentContentStream.substring(0, btStart) + " BT ";
            postEt = "ET " + currentContentStream.substring(etEnd);
            // marked content which we will use to try and find some data points.
            markedContent = currentContentStream.substring(btStart, etEnd).trim();
            // check for Q/q  as we'll lose textState that might have been collected.
            // internal stack manipulators are harder to predict behaviour
            if (markedContent.startsWith("q") && markedContent.endsWith("Q")) {
                markedContent = markedContent.substring(1, markedContent.length() - 1);
                markedContent = markedContent.substring(0, markedContent.length() - 2);
            }
        } else {
            preBt = "/Tx BMC q BT ";
            postEt = " ET Q EMC ";
        }

        // check for a bounding box definition
        Rectangle2D.Float bounds = findRectangle(preBt);
        boolean isfourthQuadrant = bounds != null && bounds.getHeight() < 0;

        // finally, build out the new content stream
        StringBuilder content = new StringBuilder();
        Page parentPage = getPage();
        String derivedAppearance = generateDefaultAppearance(markedContent,
                parentPage != null ? parentPage.getResources() : null, fieldDictionary);
        content.append(derivedAppearance);

        // apply the text offset, 4 is just a generic padding.
        if (!isfourthQuadrant) {
            double height = getBbox().getHeight();
            double size = fieldDictionary.getSize();
            double leading = fieldDictionary.getLeading();
            double lineHeight;
            if (leading > 0 && leading < height) {
                lineHeight = leading;
            } else {
                lineHeight = size;
            }
            // final correction to try and avoid any cropped text
            if (lineHeight > height ) {
                lineHeight = height;
            }
            double hOffset = Math.round(lineHeight + ((height - lineHeight)));
            content.append(lineHeight).append(" TL ");
            content.append(1).append(' ').append(hOffset).append(" Td ");
        } else {
            content.append(1).append(' ').append(3).append(" Td ");
        }
        // encode the text so it can be properly encoded in PDF string format
        // hex encode the text so that we better handle character codes > 127
        content = encodeString(content, contents);

        // build the final content stream.
        currentContentStream = preBt + content + postEt;
        return currentContentStream;
    }


    public void reset() {
        // set the  fields value (V) to the default value defined by the DV key.
        Object oldValue = fieldDictionary.getFieldValue();
        Object tmp = fieldDictionary.getDefaultFieldValue();
        if (tmp == null && fieldDictionary.getParent() != null) {
            // /DV is inheritable: a kid widget's default is its field's.
            tmp = fieldDictionary.getParent().getDefaultFieldValue();
        }
        if (tmp != null) {
            // apply the default value
            fieldDictionary.setFieldValue(tmp, getPObjectReference());
            persistReset(tmp);
            firePropertyChange("valueFieldReset", oldValue, fieldDictionary.getFieldValue());
        } else {
            // otherwise we remove the key
            fieldDictionary.getEntries().remove(FieldDictionary.V_KEY);
            fieldDictionary.setFieldValue("", getPObjectReference());
            persistReset("");
            firePropertyChange("valueFieldReset", oldValue, "");
        }
    }

    @Override
    public TextFieldDictionary getFieldDictionary() {
        return fieldDictionary;
    }

    public String generateDefaultAppearance(String content, Resources resources,
                                            VariableTextFieldDictionary variableTextFieldDictionary) {
        if (variableTextFieldDictionary != null) {
            return variableTextFieldDictionary.generateDefaultAppearance(content, resources);
        }
        return null;
    }
}
