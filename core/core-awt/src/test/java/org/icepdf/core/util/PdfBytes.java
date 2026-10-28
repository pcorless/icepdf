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
package org.icepdf.core.util;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/**
 * A written PDF as text a test can search, object streams included.
 * <p>
 * A full save packs dictionaries into compressed object streams (GH-636), so a string or a key
 * written in one - an annotation's /Contents, a font's /FontFile2 - is not in the file's bytes as
 * written. A test that searched the bytes for it would find nothing, and a test that checked it was
 * gone would pass whether it was or not. This appends each object stream's decoded contents to the
 * file's own bytes.
 */
public final class PdfBytes {

    // how the writer writes an object stream's dictionary
    private static final Pattern OBJECT_STREAM =
            Pattern.compile("/Type /ObjStm [^>]*/Filter /FlateDecode /Length (\\d+) >>\\s*stream\\r?\\n");

    private PdfBytes() {
    }

    /**
     * @param pdf a written document
     * @return its bytes as ISO-8859-1 text, followed by the decoded contents of its object streams
     */
    public static String text(byte[] pdf) {
        String raw = new String(pdf, StandardCharsets.ISO_8859_1);
        StringBuilder text = new StringBuilder(raw);
        Matcher matcher = OBJECT_STREAM.matcher(raw);
        while (matcher.find()) {
            int length = Integer.parseInt(matcher.group(1));
            text.append('\n').append(new String(inflate(pdf, matcher.end(), length), StandardCharsets.ISO_8859_1));
        }
        return text.toString();
    }

    private static byte[] inflate(byte[] data, int offset, int length) {
        Inflater inflater = new Inflater();
        try {
            inflater.setInput(data, offset, length);
            ByteArrayOutputStream out = new ByteArrayOutputStream(length * 4);
            byte[] buffer = new byte[8192];
            while (!inflater.finished()) {
                int count = inflater.inflate(buffer);
                if (count == 0 && (inflater.needsInput() || inflater.needsDictionary())) break;
                out.write(buffer, 0, count);
            }
            return out.toByteArray();
        } catch (DataFormatException e) {
            throw new IllegalStateException("object stream at " + offset + " does not inflate", e);
        } finally {
            inflater.end();
        }
    }
}
