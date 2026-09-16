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
import org.icepdf.core.pobjects.structure.Header;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Writes out the head of the document.  Nothing fancy uses the overused âãÏÓ marker and the version
 * specified in the original document.
 *
 * @since 7.2
 */
public class HeaderWriter extends BaseWriter {

    private static final byte[] commentMarker = "%".getBytes(StandardCharsets.ISO_8859_1);

    /**
     * The four bytes above 127 that follow the header, which tell anything transferring the file
     * that it is binary and must not be translated (PDF 32000-1 7.5.2).
     * <p>
     * Written as the byte values rather than as characters of a string literal.  As a literal it
     * was encoded with whatever charset the platform defaulted to, which made it eight bytes on a
     * UTF-8 machine and four on a Latin-1 one - the same source producing different files - and it
     * depended on the compiler being told the right encoding for this file as well.
     */
    private static final byte[] FOUR_BYTES = {(byte) 0xE2, (byte) 0xE3, (byte) 0xCF, (byte) 0xD3};

    public void write(Header header, CountingOutputStream output) throws IOException {
        output.write(commentMarker);
        output.write(header.getWriterVersion().getBytes(StandardCharsets.ISO_8859_1));
        output.write(NEWLINE);
        output.write(commentMarker);
        output.write(FOUR_BYTES);
        output.write(NEWLINE);
    }
}
