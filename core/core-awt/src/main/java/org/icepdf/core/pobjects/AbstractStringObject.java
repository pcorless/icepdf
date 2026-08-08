/*
 * Copyright 2026 Patrick Corless
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.icepdf.core.pobjects;

import org.icepdf.core.pobjects.security.SecurityManager;
import org.icepdf.core.util.Utils;

public abstract class AbstractStringObject implements StringObject {

    /**
     * Hex digits, upper case.  PDF 32000-1 7.3.4.3 accepts either case on input, but a hexadecimal
     * string is written in upper case, and {@link #getHexString()} answers in that form for both
     * implementations.
     */
    private static final char[] HEX_DIGITS = "0123456789ABCDEF".toCharArray();

    // Reference is need for standard encryption
    protected Reference reference;
    // if isModified, string is always unencrypted, otherwise is the raw string data which can be
    // encrypted or not.
    protected StringBuilder stringData;

    // modified string need to be encrypted when writing to file.
    protected boolean isModified;

    /**
     * The length of the underlying object's data.  Both implementations measure the data they
     * actually hold: character count for a literal string, hexadecimal digit count for a hex string.
     *
     * @return length of the object's data.
     */
    public int getLength() {
        return stringData.length();
    }

    /**
     * Appends one byte to {@code out} as two hexadecimal digits.  The single place either
     * implementation turns a byte into hex.
     *
     * @param out   buffer to append to
     * @param value byte value; only the low eight bits are read
     * @return {@code out}, for chaining
     */
    protected static StringBuilder appendHexByte(StringBuilder out, int value) {
        out.append(HEX_DIGITS[(value & 0xF0) >>> 4]);
        out.append(HEX_DIGITS[value & 0x0F]);
        return out;
    }

    /**
     * Encodes bytes as a hexadecimal string, two digits per byte.
     *
     * @param bytes bytes to encode
     * @return hexadecimal digits, upper case
     */
    protected static StringBuilder toHex(byte[] bytes) {
        StringBuilder out = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            appendHexByte(out, b);
        }
        return out;
    }

    /**
     * Gets the decrypted stringData value of the data using the key provided by the
     * security manager.
     *
     * @param securityManager security manager associated with parent document.
     */
    public String getDecryptedLiteralString(SecurityManager securityManager) {
        if (!isModified) {
            return encryption(getLiteralString(), reference, securityManager);
        } else {
            return getLiteralString();
        }
    }

    /**
     * Decrypts or encrypts a string.
     *
     * @param string          string to encrypt or decrypt
     * @param securityManager security manager for document.
     * @return encrypted or decrypted string, depends on value of decrypt param.
     */
    public String encryption(String string, Reference reference, SecurityManager securityManager) {
        // get the security manager instance
        if (securityManager != null && reference != null) {
            // get the key
            byte[] key = securityManager.getDecryptionKey();

            // convert string to bytes.
            byte[] textBytes = Utils.convertByteCharSequenceToByteArray(string);

            // Decrypt/encrypt String
            textBytes = securityManager.decrypt(reference, key, textBytes);

            // convert back to a string
            return Utils.convertByteArrayToByteString(textBytes);
        }
        return string;
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
     * Indicates a string has been modified.
     *
     * @return string has been modified and may no longer be encrypted.
     */
    @Override
    public boolean isModified() {
        return isModified;
    }
}
