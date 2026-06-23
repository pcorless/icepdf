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
package org.icepdf.core.pobjects.filters;

import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.Reference;
import org.icepdf.core.pobjects.Stream;
import org.icepdf.core.util.Library;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.List;
import java.util.zip.CRC32;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Characterization smoke test for {@link LZWDecode}.  The R&amp;D-05-Carbon
 * fixture carries several LZWDecode streams; this decodes all of them and
 * locks the aggregate result (stream count, total decoded length and CRC) so a
 * change to the decoder implementation is caught if it alters the output.
 */
public class LZWDecodeTest {

    private static final class Signature {
        int count;
        long totalBytes;
        long crc;
    }

    private Signature decodeAllLzwStreams() throws Exception {
        Document document = new Document();
        try (InputStream in = LZWDecodeTest.class.getResourceAsStream("/updater/R&D-05-Carbon.pdf")) {
            document.setInputStream(in, "R&D-05-Carbon.pdf");
        }
        Library library = document.getCatalog().getLibrary();
        int max = library.getCrossReferenceRoot().getNextAvailableReferenceNumber();

        Signature sig = new Signature();
        CRC32 crc = new CRC32();
        for (int i = 1; i < max; i++) {
            Object obj = library.getObject(new Reference(i, 0));
            if (obj instanceof Stream) {
                Stream stream = (Stream) obj;
                List<?> filters = stream.getFilterNames();
                boolean isLzw = false;
                if (filters != null) {
                    for (Object f : filters) {
                        if (String.valueOf(f).contains("LZW")) {
                            isLzw = true;
                            break;
                        }
                    }
                }
                if (isLzw) {
                    byte[] decoded = stream.getDecodedStreamBytes(0);
                    if (decoded != null) {
                        sig.count++;
                        sig.totalBytes += decoded.length;
                        crc.update(decoded);
                    }
                }
            }
        }
        sig.crc = crc.getValue();
        document.dispose();
        return sig;
    }

    // Golden signature captured from the reference LZWDecode implementation over
    // the fixture's eleven LZW streams.  A decoder change that alters any decoded
    // byte (or the set of streams that decode) will change one of these.
    private static final int GOLDEN_COUNT = 11;
    private static final long GOLDEN_TOTAL_BYTES = 61007L;
    private static final long GOLDEN_CRC = 89430579L;

    @Test
    @DisplayName("LZW streams decode to a stable signature")
    public void lzwStreamsDecodeToStableSignature() throws Exception {
        Signature sig = decodeAllLzwStreams();
        assertTrue(sig.count > 0, "expected at least one LZWDecode stream in the fixture");
        assertEquals(GOLDEN_COUNT, sig.count, "number of LZW streams decoded");
        assertEquals(GOLDEN_TOTAL_BYTES, sig.totalBytes, "total decoded byte count");
        assertEquals(GOLDEN_CRC, sig.crc, "CRC32 of all decoded LZW bytes");
    }
}