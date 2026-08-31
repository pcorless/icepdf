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

import org.icepdf.core.util.Utils;

/**
 * <p>This class represents a PDF Literal String Object.  Literal String
 * objects are written as a sequence of literal characters enclosed in
 * parentheses ().</p>
 *
 * @since 2.0
 */
public class LiteralStringObject extends AbstractStringObject {

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
     * <p>Gets a literal String representation of this object's data,
     * which is in fact, the raw data contained in this object.</p>
     *
     * @return a String representation of the object's data.
     */
    public String getLiteralString() {
        return stringData.toString();
    }

    /**
     * The string's bytes.  A literal string's data is stored one byte per char, so this is a
     * straight narrowing.
     */
    public byte[] getRawBytes() {
        int length = stringData.length();
        byte[] bytes = new byte[length];
        for (int i = 0; i < length; i++) {
            bytes[i] = (byte) stringData.charAt(i);
        }
        return bytes;
    }

    /**
     * Utility method for converting literal strings to hexadecimal.
     *
     * @param string StringBuffer in literal form
     * @return StringBuffer in hexadecimal form
     */
    private static StringBuilder stringToHex(StringBuilder string) {
        StringBuilder hh = new StringBuilder(string.length() * 2);
        for (int i = 0, max = string.length(); i < max; i++) {
            appendHexByte(hh, string.charAt(i));
        }
        return hh;
    }

}