/*
 * Version: MPL 1.1/GPL 2.0/LGPL 2.1
 *
 * "The contents of this file are subject to the Mozilla Public License
 * Version 1.1 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
 * License for the specific language governing rights and limitations under
 * the License.
 *
 * The Original Code is ICEpdf 3.0 open source software code, released
 * May 1st, 2009. The Initial Developer of the Original Code is ICEsoft
 * Technologies Canada, Corp. Portions created by ICEsoft are Copyright (C)
 * 2004-2011 ICEsoft Technologies Canada, Corp. All Rights Reserved.
 *
 * Contributor(s): _____________________.
 *
 * Alternatively, the contents of this file may be used under the terms of
 * the GNU Lesser General Public License Version 2.1 or later (the "LGPL"
 * License), in which case the provisions of the LGPL License are
 * applicable instead of those above. If you wish to allow use of your
 * version of this file only under the terms of the LGPL License and not to
 * allow others to use your version of this file under the MPL, indicate
 * your decision by deleting the provisions above and replace them with
 * the notice and other provisions required by the LGPL License. If you do
 * not delete the provisions above, a recipient may use your version of
 * this file under either the MPL or the LGPL License."
 *
 */
package org.icepdf.core.pobjects;

import org.icepdf.core.pobjects.fonts.Font;
import org.icepdf.core.pobjects.fonts.FontFile;
import org.icepdf.core.pobjects.security.SecurityManager;
import org.icepdf.core.util.Utils;

/**
 * <p>This class represents a PDF Literal String Object.  Literal String
 * objects are written as a sequence of literal characters enclosed in
 * parentheses ().</p>
 *
 * @since 2.0
 */
public class LiteralStringObject implements StringObject {

    // core data used to represent the literal string information
    private StringBuilder stringData;

    private static char[] hexChar = {'0', '1', '2', '3', '4', '5', '6',
            '7', '8', '9', 'a', 'b', 'c', 'd',
            'e', 'f'};
    // Reference is need for standard encryption
    Reference reference;

    /**
     * <p>Creates a new literal string object so that it represents the same
     * sequence of bytes as in the bytes argument.  In other words, the
     * initial content of the literal string is the characters represented
     * by the byte data.</p>
     *
     * @param bytes array of bytes which will be interpreted as literal
     *              character data.
     */
    public LiteralStringObject(byte[] bytes) {
        this(new StringBuilder(bytes.length).append(new String(bytes)));
    }

    /**
     * <p>Creates a new literal string object so that it represents the same
     * sequence of character data specifed by the argument.</p>
     *
     * @param string the initial contents of the literal string object
     */
    public LiteralStringObject(String string) {
        // append string data
        stringData = new StringBuilder(string);
    }

    /**
     * <p>Creates a new literal string object so that it represents the same
     * sequence of character data specifed by the arguments.  The string
     * value is assumed to be unencrypted and will be encrytped.  The
     * method #LiteralStringObject(String string) should be used if the string
     * is allready encrypted. This method is used for creating new
     * LiteralStringObject's that are created post document parse. </p>
     *
     * @param string    the initial contents of the literal string object,
     *                  unencrypted.
     * @param reference of parent PObject
     * @param securityManager security manager used ot encrypt the string.
     */
    public LiteralStringObject(String string, Reference reference,
                               SecurityManager securityManager) {
        // append string data
        this.reference = reference;
        // decrypt the string. 
        stringData = new StringBuilder(
                encryption(string, false, securityManager));
    }

    /**
     * <p>Creates a new literal string object so that it represents the same
     * sequence of character data specifed by the argument. The first and last
     * characters of the StringBuffer are removed.  This constructor should
     * only be used in the context of the parser which has leading and ending
     * parentheses which are removed by this method.</p>
     *
     * @param stringBuffer the initial contents of the literal string object
     */
    public LiteralStringObject(StringBuilder stringBuffer) {
        // remove parentheses, passed in by parser
        stringBuffer.deleteCharAt(0);
        stringBuffer.deleteCharAt(stringBuffer.length() - 1);
        // append string data
        stringData = new StringBuilder(stringBuffer.length());
        stringData.append(stringBuffer.toString());
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
     * specifed font and format.  The font is used to verify that the
     * specific character codes can be rendered; if they cannot they may be
     * removed or combined with the next character code to get a displayable
     * character code.
     *
     * @param fontFormat the type of pdf font which will be used to display
     *                   the text.  Valid values are CID_FORMAT and SIMPLE_FORMAT for Adobe
     *                   Composite and Simple font types respectively
     * @param font       font used to render the the literal string data.
     * @return StringBuffer which contains all renderaable characters for the
     *         given font.
     */
    public StringBuilder getLiteralStringBuffer(final int fontFormat, FontFile font) {
        if (fontFormat == Font.SIMPLE_FORMAT) {
            int charOffset = 1;
            int length = getLength();
            StringBuilder tmp = new StringBuilder(length);
            int lastIndex = 0;
            int charValue;
            for (int i = 0; i < length; i += charOffset) {
                charValue = getUnsignedInt(i - lastIndex, lastIndex + charOffset);
                // it is possible to have some cid's that are zero
                if (charValue >= 0) {//&& font.canDisplayEchar((char)charValue)){
                    tmp.append((char) charValue);
                    lastIndex = 0;
                } else {
                    lastIndex += charOffset;
                }
            }
            return tmp;
        } else if (fontFormat == Font.CID_FORMAT) {
            int charOffset = 2;
            int length = getLength();
            int charValue;
            StringBuilder tmp = new StringBuilder(length);
            for (int i = 0; i < length; i += charOffset) {
                if (font.getToUnicode()!= null &&
                        font.getToUnicode().isOneByte(i)){
                    charOffset = 1;
                }
                charValue = getUnsignedInt(i, charOffset);
                if (font.canDisplayEchar((char) charValue)) {
                    tmp.append((char) charValue);
                }
            }
            return tmp;
        }
        return null;
    }

    /**
     * The length of the the underlying object's data.
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

    /**
     * Sets the parent PDF object's reference.
     *
     * @param reference parent object reference.
     */
    public void setReference(Reference reference) {
        this.reference = reference;
    }

    /**
     * Sets the parent PDF object's reference.
     *
     * @return returns the reference used for encryption.
     */
    public Reference getReference() {
        return reference;
    }

    /**
     * Gets the decrypted literal string value of the data using the key provided by the
     * security manager.
     *
     * @param securityManager security manager associated with parent document.
     */
    public String getDecryptedLiteralString(SecurityManager securityManager) {
        return encryption(stringData.toString(), true, securityManager);
    }

    /**
     * Decryptes or encrtypes a string. 
     *
     * @param string string to encrypt or decrypt
     * @param decrypt true to decrypt string, false otherwise;
     * @param securityManager security manager for document.
     * @return encrypted or decrypted string, depends on value of decrypt param.
     */
    public String encryption(String string, boolean decrypt,
                                         SecurityManager securityManager) {
        // get the security manager instance
        if (securityManager != null && reference != null) {
            // get the key
            byte[] key = securityManager.getDecryptionKey();

            // convert string to bytes.
            byte[] textBytes =
                Utils.convertByteCharSequenceToByteArray(string);

            // Decrypt String
            if (decrypt){
                textBytes = securityManager.decrypt(reference,
                    key,
                    textBytes);
            }else{
                textBytes = securityManager.encrypt(reference,
                    key,
                    textBytes);
            }

            // convert back to a string
            return Utils.convertByteArrayToByteString(textBytes);
        }
        return string;
    }

}