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
package org.icepdf.core.pobjects.graphics.images;

import org.icepdf.core.pobjects.filters.CCITTFaxDecoder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

/**
 * GH-240: FaxDecoder's retry when a fax's K does not match its data (iText 5.0.3 writes K 0 for Group 4).
 */
public class FaxDecoderTest {

    private static final Path FIXTURES = Path.of("src/test/resources/ccitt");

    @DisplayName("Group 4 data labelled K 0: the Group 4 retry starts from a white buffer")
    @ParameterizedTest(name = "K 0 retry: {0}")
    @ValueSource(strings = {"checker", "stripes", "vertical", "narrow"})
    public void wrongKRetryStartsClean(String picture) throws Exception {
        // These fixtures make the 1D decode throw part way, after it has set some black runs.  The retry used to
        // decode into that buffer, and as decoding only ever sets bits, the stray runs stayed in the image.
        byte[] pbm = Files.readAllBytes(FIXTURES.resolve(picture + ".pbm"));
        String[] header = new String(pbm, 0, Math.min(pbm.length, 40), StandardCharsets.ISO_8859_1).split("\\s+");
        int width = Integer.parseInt(header[1]);
        int height = Integer.parseInt(header[2]);
        int size = height * ((width + 7) >> 3);
        byte[] g4 = Files.readAllBytes(FIXTURES.resolve(picture + ".g4.ccitt"));

        byte[] expected = new byte[size];
        new CCITTFaxDecoder(1, width, height).decodeT6(expected, g4, 0, height);

        byte[] retried = FaxDecoder.ccittFaxDecodeCCITTFaxDecoder(g4, 0, false, width, height, size);
        assertArrayEquals(expected, retried, picture + ": the failed 1D attempt left black runs behind");
    }
}
