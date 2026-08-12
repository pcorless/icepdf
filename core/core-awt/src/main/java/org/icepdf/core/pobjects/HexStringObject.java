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

import org.icepdf.core.pobjects.security.SecurityManager;

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

    /**
     * <p>Creates a new hexadecimal string object holding the given text, for strings created after
     * the document was parsed - an annotation property value, say.  The counterpart of
     * {@link LiteralStringObject#LiteralStringObject(String, Reference)}, and like it the string is
     * marked modified so the writers know its contents are plain text that still needs encrypting.
     * </p>
     *
     * @param string    the text to hold, unencrypted
     * @param reference of parent PObject, needed to encrypt on write
     */
    public HexStringObject(String string, Reference reference) {
        this.reference = reference;
        this.isModified = true;
        stringData = encodeHexString(string);
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
     * Reads a run of hexadecimal digits as an unsigned integer.  Private because the only caller
     * left is {@link #getRawBytes()}; the public form measured its offset in digits while the
     * literal string's identically named method measured bytes, and nothing used either.
     *
     * @param start  the beginning index, inclusive
     * @param offset the number of digits to process
     * @return unsigned integer value of the specified data range, 0 if out of range or unparsable
     */
    private int digitsToInt(int start, int offset) {
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
     * <p>Gets a literal String representation of this object's data.
     * The hexadecimal data is converted to an equivalent string representation.</p>
     *
     * @return a String representation of the object's data.
     */
    public String getLiteralString() {
        return hexToString(stringData).toString();
    }

    /**
     * Decrypts the bytes the digits encode and only then decodes them.
     * <p>
     * Order matters here in a way it does not for a literal string, which is why this overrides the
     * inherited implementation.  A hexadecimal string's byte order marker belongs to the plain text,
     * so decoding first - as decrypting {@link #getLiteralString()} does - reads the marker out of
     * cipher text, where it is not.  A UTF-16 title in an encrypted document came back as its raw
     * bytes, marker and interleaved nulls included, rather than as its text.
     *
     * @param securityManager security manager associated with parent document.
     */
    @Override
    public String getDecryptedLiteralString(SecurityManager securityManager) {
        if (isModified || securityManager == null || reference == null) {
            // already plain text, or nothing to decrypt with
            return getLiteralString();
        }
        return hexToString(toHex(getDecryptedRawBytes(securityManager))).toString();
    }

    /**
     * The digits to write for this string when the document is encrypted and the string is not:
     * the bytes the digits encode, encrypted, back in hexadecimal.
     * <p>
     * Encrypting {@link #getHexString()} instead would encrypt the ASCII of the digits rather than
     * the bytes they stand for.
     *
     * @param reference       parent object reference, part of the per object key
     * @param securityManager security manager associated with parent document.
     * @return hexadecimal digits of the encrypted bytes
     */
    public String getEncryptedHexString(Reference reference, SecurityManager securityManager) {
        return toHex(getEncryptedRawBytes(reference, securityManager)).toString();
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
            bytes[i] = (byte) digitsToInt(i * 2, 2);
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
    private static StringBuilder hexToString(StringBuilder hh) {

        // make sure we have a valid hex value to convert to string.
        // can't decrypt an empty string.  A string shorter than the marker cannot carry one either,
        // and testing for it used to read past the end: <FE> matched the first two digits and then
        // threw indexing the third.
        if (hh == null) {
            return new StringBuilder();
        }
        // special case, test for not a 4 byte character code format
        if (hh.length() < BYTE_ORDER_MARKER.length() || !isByteOrderMarked(hh)) {
            return rawHexToString(hh);
        }
        // otherwise, assume 4 byte character codes
        int length = hh.length();
        StringBuilder sb = new StringBuilder(length / 4);
        // make sure to skip the marker
        int i = BYTE_ORDER_MARKER.length();
        for (; i + 4 <= length; i = i + 4) {
            sb.append((char) Integer.parseInt(hh.substring(i, i + 4), 16));
        }
        // a trailing pair too short to make a code unit is still a byte worth keeping
        if (i + 2 <= length) {
            sb.append((char) Integer.parseInt(hh.substring(i, i + 2), 16));
        }
        return sb;
    }

    /**
     * Gets the raw string values not taking into account any special cases for FEFF byte
     * marking.
     *
     * @return two byte hex string converted to plain string.
     */
    public StringBuilder getRawHexToString() {
        return rawHexToString(stringData);
    }

    /**
     * One character per digit pair, no byte order marker interpretation.
     */
    private static StringBuilder rawHexToString(StringBuilder hex) {
        int length = hex.length();
        StringBuilder sb = new StringBuilder(length / 2);
        // a trailing odd digit cannot form a byte; the constructor pads, but a buffer built
        // elsewhere may not have
        for (int i = 0; i + 1 < length; i = i + 2) {
            sb.append((char) Integer.parseInt(hex.substring(i, i + 2), 16));
        }
        return sb;
    }

}
