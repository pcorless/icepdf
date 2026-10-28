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
package org.icepdf.core.util.updater.writeables;

import org.icepdf.core.io.CountingOutputStream;
import org.icepdf.core.pobjects.LiteralStringObject;
import org.icepdf.core.pobjects.PObject;
import org.icepdf.core.pobjects.security.SecurityManager;
import org.icepdf.core.util.Utils;

import java.io.IOException;

public class LiteralStringWriter extends BaseWriter {

    protected static final byte[] BEGIN_LITERAL_STRING = "(".getBytes();
    protected static final byte[] END_LITERAL_STRING = ")".getBytes();

    protected static final String LITERAL_REGEX = "(?=[()\\\\])";
    protected static final String LITERAL_REPLACEMENT = "\\\\";

    public LiteralStringWriter(SecurityManager securityManager) {
        this(securityManager, false);
    }

    /**
     * @param plainStrings write every string as plain text, deciphering any read encrypted from the
     *                     file: for the contents of an object stream, which is encrypted as a whole
     */
    public LiteralStringWriter(SecurityManager securityManager, boolean plainStrings) {
        this.securityManager = securityManager;
        this.plainStrings = plainStrings;
    }

    public void write(PObject pObject, CountingOutputStream output) throws IOException {
        LiteralStringObject writeable = (LiteralStringObject) pObject.getObject();
        if (plainStrings) {
            byte[] plain = writeable.getDecryptedRawBytes(securityManager);
            writeRaw(escape(Utils.convertByteArrayToByteString(plain)), output);
        } else if (pObject.isDoNotEncrypt()) {
            // written as it is - but still escaped: a trailer /ID can hold a backslash or a
            // parenthesis like any other bytes, and unescaped it ends the string early.
            writeRaw(escape(writeable.getLiteralString()), output);
        } else if (securityManager != null) {
            if (!writeable.isEncryptedFor(pObject.getReference())) {
                // plain text - authored since the document was opened, or read from an object
                // stream - or enciphered for another object: encrypt it for this one.  The cipher
                // runs over bytes; the byte string that comes back is only turned into characters
                // so the delimiters can be escaped.
                byte[] encrypted = writeable.getEncryptedRawBytes(pObject.getReference(), securityManager);
                String writeableString = Utils.convertByteArrayToByteString(encrypted);
                writeRaw(escape(writeableString), output);
            } else {
                // read from this object in the file: already in the state it should be written in
                writeRaw(escape(writeable.getLiteralString()), output);
            }
        } else {
            // plain string make sure it's properly escaped.
            String escaped = escape(writeable.getLiteralString());
            writeRaw(escaped, output);
        }
    }

    /**
     * Escapes what can't stand for itself in a literal string: the delimiters and backslash, and the
     * end-of-line bytes.  A reader turns an unescaped CR, LF or CR LF in a string into a single LF
     * (PDF 32000-1 7.3.4.2), so written raw they don't survive - and in an encryption dictionary's
     * /O or /U, or a string written plain into an object stream, that is a corrupted key or value.
     */
    static String escape(String value) {
        StringBuilder out = null;
        for (int i = 0, length = value.length(); i < length; i++) {
            char c = value.charAt(i);
            String escaped = c == '(' ? "\\(" : c == ')' ? "\\)" : c == '\\' ? "\\\\"
                    : c == '\r' ? "\\r" : c == '\n' ? "\\n" : null;
            if (escaped != null && out == null) {
                out = new StringBuilder(length + 16).append(value, 0, i);
            }
            if (out != null) {
                if (escaped != null) out.append(escaped);
                else out.append(c);
            }
        }
        return out == null ? value : out.toString();
    }

    public void write(String writeable, CountingOutputStream output) throws IOException {
        output.write(BEGIN_LITERAL_STRING);
        writeByteString(writeable, output);
        output.write(END_LITERAL_STRING);
    }

    public void writeRaw(String writeable, CountingOutputStream output) throws IOException {
        output.write(BEGIN_LITERAL_STRING);
        byte[] textBytes = Utils.convertByteCharSequenceToByteArray(writeable);
        output.write(textBytes);
        output.write(END_LITERAL_STRING);
    }
}
