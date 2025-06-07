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

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * <p>This class represents a PDF Hexadecimal String Object.  Hexadecimal String
 * objects are written as a sequence of literal characters enclosed in
 * angled brackets &lt;&gt;.</p>
 *
 * @since 2.0
 */
public class HexStringObject extends AbstractStringObject {

    private static final Logger logger =
            Logger.getLogger(HexStringObject.class.toString());

    /**
     * <p>Creates a new hexadecimal string object so that it represents the same
     * sequence of character data specified by the argument. This constructor should
     * only be used in the context of the parser which has leading and ending
     * angled brackets which are removed by this method.</p>
     *
     * Old parser, just used for cmap parsing now.
     *
     * @param stringBuffer the initial contents of the hexadecimal string object
     */
    public HexStringObject(StringBuilder stringBuffer) {
        // remove angled brackets, passed in by parser
        stringBuffer.deleteCharAt(0);
        stringBuffer.deleteCharAt(stringBuffer.length() - 1);
        // append string data
        stringData = new StringBuilder(stringBuffer.length());
        stringData.append(normalizeHex(stringBuffer, 2).toString());
    }

    /**
     * Content and object parser hex string creation
     *
     * @param string raw hex string
     */
    public HexStringObject(String string) {
        stringData = new StringBuilder(string.length());
        stringData.append(normalizeHex(new StringBuilder(string), 2).toString());
    }

    public static HexStringObject createHexString(String literalstring) {
        StringBuilder hexString = encodeHexString(literalstring);
        return new HexStringObject(hexString.toString());
    }

    public static String encodeHexString(byte[] byteArray) {
        StringBuffer hexStringBuffer = new StringBuffer();
        for (int i = 0; i < byteArray.length; i++) {
            hexStringBuffer.append(byteToHex(byteArray[i]));
        }
        return hexStringBuffer.toString();
    }

    private static String byteToHex(byte num) {
        char[] hexDigits = new char[2];
        hexDigits[0] = Character.forDigit((num >> 4) & 0xF, 16);
        hexDigits[1] = Character.forDigit((num & 0xF), 16);
        return new String(hexDigits);
    }

    /**
     * Encodes the given contents string into a 4 byte hex string.  This allows us to easily account for
     * mixed encoding of 2-byte and 4 byte string content.
     *
     * @param contents string to be encoded into hex format.
     * @return original content stream with contents encoded in the hex string format.
     */
    public static StringBuilder encodeHexString(String contents) {
        StringBuilder hex = new StringBuilder();
        if (contents != null && !contents.isEmpty()) {
            char[] chars = contents.toCharArray();
            hex.append("FEFF");
            String hexCode;
            for (char aChar : chars) {
                hexCode = Integer.toHexString(aChar);
                if (hexCode.length() == 2) {
                    hexCode = "00" + hexCode;
                } else if (hexCode.length() == 1) {
                    hexCode = "000" + hexCode;
                }
                hex.append(hexCode);
            }
        }
        return hex;
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
            return 0;
        int unsignedInt = 0;
        try {
            unsignedInt = Integer.parseInt(
                    stringData.substring(start, start + offset), 16);
        } catch (NumberFormatException e) {
            int finalUnsignedInt = unsignedInt;
            logger.log(Level.FINER, () -> "Number Format Exception " + finalUnsignedInt + " " + stringData.substring(start, start + offset));
        }
        return unsignedInt;
    }

    public int getUnsignedInt(String data) {
        int unsignedInt = 0;
        try {
            unsignedInt = Integer.parseInt(data, 16);
        } catch (NumberFormatException e) {
            int finalUnsignedInt = unsignedInt;
            logger.log(Level.FINER, () -> "Number Format Exception " + finalUnsignedInt);
        }
        return unsignedInt;
    }

    /**
     * <p>Returns a string representation of the object.
     * The hex data is converted to an equivalent string representation</p>
     *
     * @return a string representing the object.
     */
    public String toString() {
        return getLiteralString();
    }

    /**
     * <p>Gets a hexadecimal String representation of this object's data, which
     * is in fact, the raw data contained in this object</p>
     *
     * @return a String representation of the object's data in hexadecimal notation.
     */
    public String getHexString() {
        return stringData.toString().toUpperCase();
    }

    /**
     * <p>Gets a hexadecimal StringBuffer representation of this object's data,
     * which is in fact the raw data contained in this object.</p>
     *
     * @return a StringBuffer representation of the objects data in hexadecimal.
     */
    public StringBuilder getHexStringBuffer() {
        return stringData;
    }

    /**
     * <p>Gets a literal StringBuffer representation of this object's data.
     * The hexadecimal data is converted to an equivalent string representation</p>
     *
     * @return a StringBuffer representation of the object's data.
     */
    public StringBuilder getLiteralStringBuffer() {
        return hexToString(stringData);
    }

    /**
     * <p>Gets a literal String representation of this object's data.
     * The hexadecimal data is converted to an equivalent string representation.</p>
     *
     * @return a String representation of the object's data.
     */
    public String getLiteralString() {
        return hexToString(stringData).toString();
    }

    /**
     * <p>Gets a literal String representation of this object's data using the
     * specifed font and format.  The font is used to verify that the
     * specific character codes can be rendered; if they can not, they may be
     * removed or combined with the next character code to get a displayable
     * character code.
     *
     * @param fontFormat the type of font which will be used to display
     *                   the text.  Valid values are CID_FORMAT and SIMPLE_FORMAT for Adobe
     *                   Composite and Simple font types respectively
     * @param font       font used to render the literal string data.
     * @return StringBuffer which contains all renderaable characters for the
     * given font.
     */
    public StringBuilder getLiteralStringBuffer(final int fontFormat, FontFile font) {
        if (fontFormat == Font.SIMPLE_FORMAT) {
            stringData = new StringBuilder(normalizeHex(stringData, 2).toString());
            int charOffset = 2;
            int length = getLength();
            StringBuilder tmp = new StringBuilder(length);
            int lastIndex = 0;
            int charValue;
            int offset;
            for (int i = 0; i < length; i += charOffset) {
                offset = lastIndex + charOffset;
                charValue = getUnsignedInt(i - lastIndex, offset);
                // 0 cid is valid, so we have ot be careful we don't exclude the
                // cid 00 = 0 or 0000 = 0, not 0000 = 00.
                // removed font check as it was causing problems with a lot of Latin based hex strings
                // may need to revisit in the future when getting back to multibyte encodings.
                if (!(offset < length && charValue == 0)) {
                    tmp.append((char) charValue);
                    lastIndex = 0;
                } else {
                    lastIndex += charOffset;
                }
            }
            return tmp;
        } else if (fontFormat == Font.CID_FORMAT) {
            stringData = new StringBuilder(normalizeHex(stringData, 4).toString());
            int charOffset = 2;
            int length = getLength();
            int charValue;
            StringBuilder tmp = new StringBuilder(length);
            // attempt to detect mulibyte encoded strings.
            for (int i = 0; i < length; i += charOffset) {
                String first = stringData.substring(i, i + 2);
                if (first.charAt(0) != '0') {
                    // check range for possible 2 byte char ie mixed mode.
                    charValue = getUnsignedInt(first);
                    if (!font.getToUnicode().getName().contains("UCS2") &&
                            font.canDisplay((char) charValue) && font.getSource() != null) {
                        tmp.append((char) charValue);
                    } else {
                        charValue = getUnsignedInt(i, 4);
                        if (font.canDisplay((char) charValue)) {
                            tmp.append((char) charValue);
                            i += 2;
                        }
                    }
                } else {
                    charValue = getUnsignedInt(i, 4);
                    // should never have a 4 digit zero value.
                    if (font.canDisplay((char) charValue)) {
                        tmp.append((char) charValue);
                        i += 2;
                    }
                }
            }
            return tmp;
        }
        return null;
    }

    /**
     * The length of the underlying objects data.
     *
     * @return length of object's data.
     */
    public int getLength() {
        return stringData.length();
    }

    /**
     * Utility method to removed all none hex character from the string and
     * ensure that the length is an even length.
     *
     * @param hex  hex data to normalize
     * @param step 2 or 4 character codes.
     * @return normalized pure hex StringBuffer
     */
    private static StringBuilder normalizeHex(StringBuilder hex, int step) {
        // strip and white space
        int length = hex.length();
        for (int i = 0; i < length; i++) {
            if (isNoneHexChar(hex.charAt(i))) {
                hex.deleteCharAt(i);
                length--;
                i--;
            }
        }
        length = hex.length();
        if (step == 2) {
            // pre append 0's to uneven length, be careful as the 0020 isn't the same as 2000
            if (length % 2 != 0) {
                // this was done for variable byte font encoding,  this seems risky to preappend, pulling
                hex = hex.append("0");//new StringBuilder("0").append(hex);
            }
        }
        if (step == 4) {
            if (length % 4 != 0) {
                hex = new StringBuilder("00").append(hex);
            }
        }
        return hex;
    }

    /**
     * Utility method to test if the char is a none hexadecimal char.
     *
     * @param c charact to text
     * @return true if the character is a none hexadecimal character
     */
    private static boolean isNoneHexChar(char c) {
        // make sure the char is the following
        return !(((c >= 48) && (c <= 57)) || // 0-9
                ((c >= 65) && (c <= 70)) ||  // A-F
                ((c >= 97) && (c <= 102)));  // a-f
    }

    /**
     * Utility method for converting a hexadecimal string to a literal string.
     *
     * @param hh StringBuffer containing data in hexadecimal form.
     * @return StringBuffer containing data in literal form.
     */
    private StringBuilder hexToString(StringBuilder hh) {

        // make sure we have a valid hex value to convert to string.
        // can't decrypt an empty string.
        if (hh != null && hh.length() == 0) {
            return new StringBuilder();
        }

        StringBuilder sb;
        // special case, test for not a 4 byte character code format
        if (!((hh.charAt(0) == 'F' | hh.charAt(0) == 'f')
                && (hh.charAt(1) == 'E' | hh.charAt(1) == 'e')
                && (hh.charAt(2) == 'F' | hh.charAt(2) == 'f')
                && (hh.charAt(3) == 'F') | hh.charAt(3) == 'f')) {
            return getRawHexToString();
        }
        // otherwise, assume 4 byte character codes
        else {
            int length = hh.length();
            // check for the need to add padding
            if (((length - 4) / 4) % 2 != 0) {
                hh.append("00");
            }
            sb = new StringBuilder(length / 4);
            String subStr;
            // make sure to skip the marker
            for (int i = 4; i < length; i = i + 4) {
                subStr = hh.substring(i, i + 4);
                sb.append((char) Integer.parseInt(subStr, 16));
            }
            return sb;
        }
    }

    /**
     * Gets the raw string values not taking into account any special cases for FEFF byte
     * marking.
     *
     * @return two byte hex string converted to plain string.
     */
    public StringBuilder getRawHexToString() {

        StringBuilder sb;

        int length = stringData.length();
        sb = new StringBuilder(length / 2);
        String subStr;

        for (int i = 0; i < length; i = i + 2) {
            subStr = stringData.substring(i, i + 2);
            sb.append((char) Integer.parseInt(subStr, 16));
        }
        return sb;
    }

}
