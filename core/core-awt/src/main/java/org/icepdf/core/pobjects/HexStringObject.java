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
            Logger.getLogger(HexStringObject.class.getName());

    /** UTF-16BE byte order marker, as hexadecimal digits: marks 4 digit character codes. */
    private static final String BYTE_ORDER_MARKER = "FEFF";

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

    /**
     * Encodes bytes as hexadecimal digits, two per byte.
     *
     * @param byteArray bytes to encode
     * @return hexadecimal digits, upper case
     */
    public static String encodeHexString(byte[] byteArray) {
        return toHex(byteArray).toString();
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
            hex.append(BYTE_ORDER_MARKER);
            for (int i = 0, max = contents.length(); i < max; i++) {
                // 4 digits per character: the high byte first, so 'A' is 0041 and not 4100
                char aChar = contents.charAt(i);
                appendHexByte(hex, aChar >> 8);
                appendHexByte(hex, aChar);
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
            boolean notUCS2 = font.getToUnicode() != null
                    && font.getToUnicode().getName() != null
                    && !font.getToUnicode().getName().contains("UCS2");
            StringBuilder tmp = new StringBuilder(length);
            // attempt to detect mulibyte encoded strings.
            for (int i = 0; i < length; i += charOffset) {
                String first = stringData.substring(i, i + 2);
                if (first.charAt(0) != '0') {
                    // check range for possible 2 byte char ie mixed mode.
                    charValue = getUnsignedInt(first);
                    if (notUCS2 && font.canDisplay((char) charValue) && font.getSource() != null) {
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
     * The bytes the hex digits encode, two digits per byte.  The constructor has already stripped
     * whitespace and padded an odd digit count with a trailing zero (PDF 32000-1 7.3.4.3), so the
     * digits always pair up.
     */
    public byte[] getRawBytes() {
        int digits = stringData.length();
        byte[] bytes = new byte[digits / 2];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) getUnsignedInt(i * 2, 2);
        }
        return bytes;
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
     * True when the digits open with the UTF-16BE byte order marker, meaning the string is a
     * sequence of 4 digit (2 byte) character codes rather than 2 digit ones.  Caller has already
     * checked that there are at least {@link #BYTE_ORDER_MARKER} digits to read.
     */
    private static boolean isByteOrderMarked(StringBuilder hh) {
        for (int i = 0; i < BYTE_ORDER_MARKER.length(); i++) {
            if (Character.toUpperCase(hh.charAt(i)) != BYTE_ORDER_MARKER.charAt(i)) {
                return false;
            }
        }
        return true;
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
        // can't decrypt an empty string.  A string shorter than the marker cannot carry one either,
        // and testing for it used to read past the end: <FE> matched the first two digits and then
        // threw indexing the third.
        if (hh == null || hh.length() < BYTE_ORDER_MARKER.length()) {
            return hh == null ? new StringBuilder() : getRawHexToString();
        }

        StringBuilder sb;
        // special case, test for not a 4 byte character code format
        if (!isByteOrderMarked(hh)) {
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
