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
package org.icepdf.core.pobjects;

import org.icepdf.core.pobjects.fonts.Font;
import org.icepdf.core.pobjects.fonts.FontFile;
import org.icepdf.core.util.StringOffsetBuilder;
import org.icepdf.core.util.Utils;

/**
 * <p>This class represents a PDF Literal String Object.  Literal String
 * objects are written as a sequence of literal characters enclosed in
 * parentheses ().</p>
 *
 * @since 2.0
 */
public class LiteralStringObject extends AbstractStringObject {

    private static final char[] hexChar = {'0', '1', '2', '3', '4', '5', '6',
            '7', '8', '9', 'a', 'b', 'c', 'd',
            'e', 'f'};

    /**
     * <p>Creates a new literal string object so that it represents the same
     * sequence of character data specified by the argument.</p>
     *
     * Created via the content and object parsers.
     *
     * @param string the initial contents of the literal string object
     */
    public LiteralStringObject(String string) {
        stringData = new StringBuilder(string);
    }

    public LiteralStringObject(StringBuilder chars, boolean dif) {
        stringData = chars;
    }

    /**
     * <p>Creates a new literal string object so that it represents the same
     * sequence of character data specified by the arguments. This method is used for creating new
     * LiteralStringObject's that are created post document parse, like annotation
     * property values. </p>
     *
     * @param string          the initial contents of the literal string object,
     *                        unencrypted.
     * @param reference       of parent PObject
     */
    public LiteralStringObject(String string, Reference reference) {
        this.reference = reference;
        this.isModified = true;
        // convert string to octal encoded.
        stringData = new StringBuilder(Utils.convertStringToOctal(string));
    }

    /**
     * <p>Creates a new literal string object so that it represents the same
     * sequence of character data specifed by the argument. The first and last
     * characters of the StringBuffer are removed.  This constructor should
     * only be used in the context of the parser which has leading and ending
     * parentheses which are removed by this method.</p>
     *
     * called from old Parser used for cmap parsing,  hopefully this can be rmeoved one day.
     *
     * @param stringBuffer the initial contents of the literal string object
     */
    public LiteralStringObject(StringBuilder stringBuffer) {
        // remove parentheses, passed in by parser
        stringBuffer.deleteCharAt(0);
        stringBuffer.deleteCharAt(stringBuffer.length() - 1);
        // append string data
        stringData = new StringBuilder(stringBuffer.length());
        stringData.append(stringBuffer);
    }

    /**
     * Gets the integer value of the hexidecimal data specified by the start and
     * offset parameters.
     *
     * @param start  the begining index, inclusive
     * @param offset the length of bytes to process
     * @return unsigned integer value of the specifed data range
     */
    public int getUnsignedInt(int start, int offset) {
        if (start < 0 || stringData.length() < (start + offset))
            return stringData.charAt(0);

        if (offset == 1) {
            return stringData.charAt(start);
        }
        if (offset == 2) {
            return ((stringData.charAt(start) & 0xFF) << 8) |
                    ((stringData.charAt(start + 1)) & 0xFF);
        } else if (offset == 4) {
            return ((stringData.charAt(start) & 0xFF) << 24) |
                    ((stringData.charAt(start + 1) & 0xFF) << 16) |
                    ((stringData.charAt(start + 2) & 0xFF) << 8) |
                    ((stringData.charAt(start + 3)) & 0xFF);
        } else {
            return 0;
        }
    }

    /**
     * <p>Returns a string representation of the object.</p>
     *
     * @return a string representing the object.
     */
    public String toString() {
        return stringData.toString();
    }

    /**
     * <p>Gets a hexadecimal String representation of this object's data, which
     * is converted to hexadecimal form.</p>
     *
     * @return a String representation of the objects data.
     */
    public String getHexString() {
        return stringToHex(stringData).toString();
    }

    /**
     * <p>Gets a hexadecimal StringBuffer representation of this object's data,
     * which is converted to hexadecimal form.</p>
     *
     * @return a StringBufffer representation of the object's data in hexadecimal
     *         notation.
     */
    public StringBuilder getHexStringBuffer() {
        return stringToHex(stringData);
    }

    /**
     * <p>Gets a literal StringBuffer representation of this object's data
     * which is in fact, the raw data contained in this object.</p>
     *
     * @return a StringBuffer representation of the object's data.
     */
    public StringBuilder getLiteralStringBuffer() {
        return stringData;
    }

    /**
     * <p>Gets a literal String representation of this object's data,
     * which is in fact, the raw data contained in this object.</p>
     *
     * @return a String representation of the object's data.
     */
    public String getLiteralString() {
        return stringData.toString();
    }

    /**
     * <p>Gets a literal String representation of this object's data using the
     * specified font and format.  The font is used to verify that the
     * specific character codes can be rendered; if they cannot, they may be
     * removed or combined with the next character code to get a displayable
     * character code.
     *
     * @param fontFormat the type of pdf font which will be used to display
     *                   the text.  Valid values are CID_FORMAT and SIMPLE_FORMAT for Adobe
     *                   Composite and Simple font types respectively
     * @param font       font used to render the literal string data.
     * @return StringBuffer which contains all renderable characters for the
     *         given font.
     */
    public StringOffsetBuilder getLiteralStringBuffer(final int fontFormat, FontFile font) {

        if (fontFormat == Font.SIMPLE_FORMAT
                || (font.getByteEncoding() == FontFile.ByteEncoding.ONE_BYTE)) {
            return new StringOffsetBuilder(stringData, 1);
        } else if (fontFormat == Font.CID_FORMAT) {
            int length = getLength();
            int charValue;
            StringOffsetBuilder tmp = new StringOffsetBuilder(length);
            if (font.getByteEncoding() == FontFile.ByteEncoding.MIXED_BYTE) {
                int charOffset = 1;
                for (int i = 0; i < length; i += charOffset) {
                    // check range for possible 2 byte char.
                    charValue = getUnsignedInt(i, 1);
                    if (font.canDisplay((char) charValue)) {
                        tmp.append((char) charValue, 1);
                    } else {
                        int charValue2 = getUnsignedInt(i, 2);
                        if (font.canDisplay((char) charValue2)) {
                            tmp.append((char) charValue2, 2);
                            i += 1;
                        }
                    }
                }
            } else {
                // we have default 2bytes.
                int charOffset = 2;
                for (int i = 0; i < length; i += charOffset) {
                    int charValue2 = getUnsignedInt(i, 2);
                    if (font.canDisplay((char) charValue2)) {
                        tmp.append((char) charValue2, 2);
                    }
                }
            }
            return tmp;
        }
        return null;
    }

    /**
     * The length of the underlying object's data.
     *
     * @return length of objcts data.
     */
    public int getLength() {
        return stringData.length();
    }

    /**
     * Utility method for converting literal strings to hexadecimal.
     *
     * @param string StringBuffer in literal form
     * @return StringBuffer in hexadecial form
     */
    private StringBuilder stringToHex(StringBuilder string) {
        StringBuilder hh = new StringBuilder(string.length() * 2);
        int charCode;
        for (int i = 0, max = string.length(); i < max; i++) {
            charCode = string.charAt(i);
            hh.append(hexChar[(charCode & 0xf0) >>> 4]);
            hh.append(hexChar[charCode & 0x0f]);
        }
        return hh;
    }

}