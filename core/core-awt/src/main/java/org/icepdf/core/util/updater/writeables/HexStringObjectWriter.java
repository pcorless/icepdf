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
import org.icepdf.core.pobjects.HexStringObject;
import org.icepdf.core.pobjects.PObject;
import org.icepdf.core.pobjects.security.SecurityManager;
import org.icepdf.core.util.Utils;

import java.io.IOException;

public class HexStringObjectWriter extends BaseWriter {

    private static final byte[] BEGIN_HEX_STRING = "<".getBytes();
    private static final byte[] END_HEX_STRING = ">".getBytes();

    // no escaping table here on purpose: what gets written is always hexadecimal digits, and
    // "0123456789ABCDEF" contains none of the characters that would need escaping

    public HexStringObjectWriter(SecurityManager securityManager) {
        this.securityManager = securityManager;
    }

    public void write(PObject pObject, CountingOutputStream output) throws IOException {
        HexStringObject writeable = (HexStringObject) pObject.getObject();
        if (!pObject.isDoNotEncrypt() && securityManager != null && writeable.isModified()) {
            // A string authored since the document was opened holds plain text, so it is the one
            // case that needs encrypting on the way out.  The bytes the digits stand for are what
            // gets encrypted, and the cipher text goes back out as digits.
            writeRaw(writeable.getEncryptedHexString(pObject.getReference(), securityManager), output);
        } else {
            // Everything else is already in the state it should be written in: a string read from
            // the file is still exactly as encrypted as the file it came from, and where there is no
            // security manager nothing is encrypted at all.
            //
            // Note this writes the DIGITS.  Writing toString() here, as this used to, wrote the
            // decoded text between the angle brackets, and re-parsing kept only those characters
            // that happened to be hexadecimal: a 62 digit string came back as EBEAE0.
            writeRaw(writeable.getHexString(), output);
        }
    }

    public void write(String writeable, CountingOutputStream output) throws IOException {
        output.write(BEGIN_HEX_STRING);
        writeByteString(writeable, output);
        output.write(END_HEX_STRING);
    }

    public void writeRaw(String writeable, CountingOutputStream output) throws IOException {
        output.write(BEGIN_HEX_STRING);
        byte[] textBytes = Utils.convertByteCharSequenceToByteArray(writeable);
        output.write(textBytes);
        output.write(END_HEX_STRING);
    }
}