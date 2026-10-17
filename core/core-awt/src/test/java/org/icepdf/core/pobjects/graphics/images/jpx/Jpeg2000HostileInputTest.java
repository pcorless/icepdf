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
package org.icepdf.core.pobjects.graphics.images.jpx;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Provenance: ORIGINAL.
 * <p>
 * Damaged and malicious streams derived from a valid fixture. The requirement for a reader we ship
 * is: an exception (or a warning and a partial image), within bounded time, never a hang, an
 * {@link OutOfMemoryError} or an unchecked exception other than the documented ones. Outcomes are
 * pinned per reader, as in {@link Jpeg2000ConformanceTest}; JJ2000's are recorded, not endorsed.
 */
public class Jpeg2000HostileInputTest {

    private static Jpeg2000Baseline baseline;

    static List<String> hostile() {
        return Jpeg2000Fixtures.hostile();
    }

    @BeforeAll
    static void load() {
        baseline = new Jpeg2000Baseline("hostile");
    }

    @AfterAll
    static void report() {
        baseline.write();
    }

    @DisplayName("hostile stream ends as the reader's baseline says")
    @ParameterizedTest(name = "{0}")
    @MethodSource("hostile")
    public void endsAsBaseline(String file) throws Exception {
        assumeTrue(Jpeg2000Fixtures.readerSpi() != null, "no JPEG 2000 reader installed");
        byte[] data = Jpeg2000Fixtures.bytes(file);
        String outcome, detail;
        long start = System.nanoTime();
        try {
            detail = Jpeg2000Fixtures.withTimeout(() -> decode(data));
            outcome = detail.startsWith("decoded") ? "DECODED" : "NO_READER";
        } catch (TimeoutException te) {
            outcome = "TIMEOUT";
            detail = "no result after " + Jpeg2000Fixtures.TIMEOUT_SECONDS + "s";
        } catch (Throwable t) {
            outcome = t instanceof OutOfMemoryError ? "OOM"
                    : t instanceof java.io.IOException ? "IOEXCEPTION"
                    : "UNCHECKED";
            detail = t.getClass().getName() + (t.getMessage() == null ? "" : ": " + t.getMessage());
        }
        long ms = (System.nanoTime() - start) / 1_000_000;
        String id = file.replace("hostile/", "").replace(".j2k", "");
        baseline.record(id, outcome, detail + " (" + (ms < 1000 ? "<1s" : ms / 1000 + "s") + ")");
        if (Jpeg2000Baseline.blessing()) return;
        String expected = baseline.expected(id);
        assertNotNull(expected, "no baseline entry for " + id + " (" + outcome + " " + detail
                + "); run with -Djpx.bless=true");
        assertEquals(expected, outcome, id + ": " + detail);
    }

    private static String decode(byte[] data) throws Exception {
        try (ImageInputStream in = Jpeg2000Fixtures.stream(data)) {
            ImageReader reader = Jpeg2000Fixtures.reader(in);
            if (reader == null) return "no reader claims the stream";
            try {
                reader.setInput(in, true, true);
                BufferedImage image = reader.read(0, reader.getDefaultReadParam());
                return "decoded " + image.getWidth() + "x" + image.getHeight();
            } finally {
                reader.dispose();
            }
        }
    }
}
