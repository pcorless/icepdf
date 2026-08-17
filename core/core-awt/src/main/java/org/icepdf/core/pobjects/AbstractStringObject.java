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
     * @return the decrypted text.
     */
    public String getDecryptedLiteralString(SecurityManager securityManager) {
        return Utils.convertByteArrayToByteString(getDecryptedRawBytes(securityManager));
    }

    /**
     * The string's bytes, decrypted, with no character interpretation of any kind.
     * <p>
     * This is the primitive to use for a string that carries binary data rather than text - an
     * indexed colour space's lookup table, say.  {@link #getDecryptedLiteralString} is the wrong
     * tool there: it hands back text, and for a hexadecimal string that means byte order marker
     * handling that would eat the first two bytes of a lookup table that happened to begin FE FF.
     *
     * @param securityManager security manager associated with parent document, null if the document
     *                        is not encrypted
     * @return the decrypted bytes; never null, may be empty
     */
    public byte[] getDecryptedRawBytes(SecurityManager securityManager) {
        if (isModified) {
            // authored since the document was opened, so already plain
            return getRawBytes();
        }
        return crypt(getRawBytes(), reference, securityManager);
    }

    /**
     * The string's bytes ready to be written to an encrypted document: plain text in, cipher text
     * out.  The counterpart of {@link #getDecryptedRawBytes}, and the only thing a writer needs.
     * <p>
     * It takes the reference rather than using the field because the two can differ, and at write
     * time the caller's is the authoritative one: objects can be renumbered on a full update, and
     * the per object key is derived from the number the object is being written under.
     *
     * @param writeReference  the reference the object is being written under
     * @param securityManager security manager associated with parent document
     * @return the encrypted bytes; never null, may be empty
     */
    public byte[] getEncryptedRawBytes(Reference writeReference, SecurityManager securityManager) {
        return crypt(getRawBytes(), writeReference, securityManager);
    }

    /**
     * Runs the document's cipher over some bytes.  The standard security handler's ciphers are
     * symmetric, so this is both directions; which one it is depends only on what went in.
     * <p>
     * Byte in, byte out, deliberately.  This was a String based method, which worked only because
     * every character happened to hold one byte - and it made it possible to hand it the wrong
     * thing entirely: the writer used to encrypt getHexString(), the ASCII of the digits, rather
     * than the bytes those digits stand for.
     *
     * @param bytes           the bytes to run through the cipher
     * @param reference       object reference, part of the per object key
     * @param securityManager security manager for document, null if the document is not encrypted
     * @return the transformed bytes, or the input unchanged when there is nothing to do
     */
    private static byte[] crypt(byte[] bytes, Reference reference, SecurityManager securityManager) {
        if (securityManager == null || reference == null) {
            return bytes;
        }
        return securityManager.decrypt(reference, securityManager.getDecryptionKey(), bytes);
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
