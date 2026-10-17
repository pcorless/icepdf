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

import org.icepdf.core.pobjects.graphics.images.jpx.Jpeg2000Fixtures.Reference;
import org.icepdf.core.pobjects.graphics.images.jpx.Jpeg2000Fixtures.Result;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Provenance: ORIGINAL.
 * <p>
 * Decodes every generated fixture with the installed JPEG 2000 ImageIO reader the way ICEpdf does
 * ({@code read(0, param).getRaster()}) and scores it against the OpenJPEG reference components.
 * <p>
 * The test pins each reader's current outcome in its baseline file, so it passes for JJ2000 with
 * all of JJ2000's gaps recorded, and fails the moment any outcome changes, in either direction.
 * A decoder that improves a case re-blesses (-Djpx.bless=true) and the diff of the baseline shows
 * exactly what got better.
 */
public class Jpeg2000ConformanceTest {

    private static Jpeg2000Baseline baseline;

    static List<Reference> references() {
        return Jpeg2000Fixtures.references();
    }

    @BeforeAll
    static void load() {
        baseline = new Jpeg2000Baseline("conformance");
    }

    @AfterAll
    static void report() {
        baseline.write();
    }

    @DisplayName("fixture decodes as the reader's baseline says")
    @ParameterizedTest(name = "{0}")
    @MethodSource("references")
    public void decodesAsBaseline(Reference ref) {
        assumeTrue(Jpeg2000Fixtures.readerSpi() != null, "no JPEG 2000 reader installed");
        Result r = Jpeg2000Fixtures.decodeAndCompare(ref);
        baseline.record(ref.id(), r.outcome.name(), r.summary());
        if (Jpeg2000Baseline.blessing()) return;
        String expected = baseline.expected(ref.id());
        assertNotNull(expected, "no baseline entry for " + ref.id() + " (" + r.summary()
                + "); run with -Djpx.bless=true");
        assertEquals(expected, r.outcome.name(), ref.id() + ": " + r.summary());
    }
}
