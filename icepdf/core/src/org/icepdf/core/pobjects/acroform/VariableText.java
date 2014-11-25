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
package org.icepdf.core.pobjects.acroform;

import org.icepdf.core.pobjects.Dictionary;
import org.icepdf.core.pobjects.Name;
import org.icepdf.core.pobjects.StringObject;
import org.icepdf.core.pobjects.graphics.DeviceCMYK;
import org.icepdf.core.pobjects.graphics.PColorSpace;
import org.icepdf.core.util.Library;
import org.icepdf.core.util.Parser;

import java.awt.*;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Stack;

/**
 * When the contents and properties of a field are known in advance, its visual
 * appearance can be specified by an appearance stream defined in the PDF file
 * (see 12.5.5, “Appearance Streams,” and 12.5.6.19, “Widget Annotations”). In
 * some cases, however, the field may contain text whose value is not known
 * until viewing time.
 *
 * @since 5.1
 */
public class VariableText extends Dictionary {

    /**
     * The default appearance string containing a sequence of valid page-content
     * graphics or text state operators that define such properties as the field’s
     * text size and colour.
     */
    public static final Name DA_KEY = new Name("DA");

    /**
     * A code specifying the form of quadding (justification) that shall be used
     * in displaying the text:
     * 0 Left-justified
     * 1 Centered
     * 2 Right-justified
     * Default value: 0 (left-justified).
     */
    public static final Name Q_KEY = new Name("Q");

    /**
     * A default style string, as described in 12.7.3.4, “Rich Text Strings.”
     */
    public static final Name DS_KEY = new Name("DS");

    /**
     * A rich text string, as described in 12.7.3.4, “Rich Text Strings.”
     */
    public static final Name RV_KEY = new Name("RV");
    protected QUADING quading = QUADING.LEFT_JUSTIFIED;
    protected int size = 12;
    protected String fontName = "Helvetic";
    protected Color color = Color.BLACK;

    public VariableText(Library library, HashMap entries) {
        super(library, entries);

        // parse out quading
        Number value = library.getInt(entries, Q_KEY);
        int quad = value.intValue();
        switch (quad) {
            case 0:
                quading = QUADING.LEFT_JUSTIFIED;
                break;
            case 1:
                quading = QUADING.CENTERED;
                break;
            case 2:
                quading = QUADING.RIGHT_JUSTIFIED;
                break;
            default:
                quading = QUADING.LEFT_JUSTIFIED;
                break;
        }
        // parse out fontName, size and color.
        // /ZaDb 12 Tf 0 g
        Object defaultAppearance = library.getObject(entries, DA_KEY);
        if (defaultAppearance instanceof StringObject) {
            org.icepdf.core.pobjects.security.SecurityManager securityManager =
                    library.getSecurityManager();
            String defaultVariableTextDAField = ((StringObject) defaultAppearance)
                    .getDecryptedLiteralString(securityManager);
            Parser parser = new Parser(new ByteArrayInputStream(defaultVariableTextDAField.getBytes()));
            try {
                for (Object tmp = parser.getToken(); tmp != null; tmp = parser.getToken()) {
                    if (tmp instanceof Name) {
                        fontName = ((Name) tmp).getName();
                    } else if (tmp instanceof Number) {
                        size = ((Number) tmp).intValue();
                    } else if (tmp instanceof String) {
                        // we have the Tj, try to get the color
                        tmp = parser.getToken();
                        Stack<Object> stack = new Stack<Object>();
                        while (tmp instanceof Number) {
                            stack.push(tmp);
                            tmp = parser.getToken();
                        }
                        // derive color
                        if (stack.size() == 1) {
                            float gray = ((Number) stack.pop()).floatValue();
                            gray = gray > 1 ? gray / 255.0f : gray;
                            // Stroke Color Gray
                            color = new Color(gray, gray, gray);
                        } else if (stack.size() == 3) {
                            float b = ((Number) stack.pop()).floatValue();
                            float gg = ((Number) stack.pop()).floatValue();
                            float r = ((Number) stack.pop()).floatValue();
                            b = Math.max(0.0f, Math.min(1.0f, b));
                            gg = Math.max(0.0f, Math.min(1.0f, gg));
                            r = Math.max(0.0f, Math.min(1.0f, r));
                            color = new Color(r, gg, b);
                        } else if (stack.size() == 4) {
                            float k = ((Number) stack.pop()).floatValue();
                            float y = ((Number) stack.pop()).floatValue();
                            float m = ((Number) stack.pop()).floatValue();
                            float c = ((Number) stack.pop()).floatValue();

                            PColorSpace pColorSpace =
                                    PColorSpace.getColorSpace(library, DeviceCMYK.DEVICECMYK_KEY);
                            // set stroke colour
                            color = pColorSpace.getColor(
                                    PColorSpace.reverse(new float[]{c, m, y, k}), true);
                        }
                    }
                }
            } catch (IOException e) {
                // silent end of string parse.
            }
        }

    }

    public int getSize() {
        return size;
    }

    public String getFontName() {
        return fontName;
    }

    public Color getColor() {
        return color;
    }

    public static enum QUADING {
        LEFT_JUSTIFIED, CENTERED, RIGHT_JUSTIFIED
    }
}
