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
package org.icepdf.fx.view;

import org.icepdf.core.pobjects.PDimension;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.IntFunction;

import static org.junit.jupiter.api.Assertions.*;

class PageSizesTest {

    /** Page i is 100 + i wide and 200 tall; reads are counted. */
    private static final class Reader implements IntFunction<PDimension> {
        int reads;

        public PDimension apply(int i) {
            reads++;
            return new PDimension(100 + i, 200);
        }
    }

    @Test
    void onlyTheFirstPagesAreReadTheRestAreTheirAverage() {
        Reader reader = new Reader();
        PageSizes sizes = new PageSizes();
        sizes.load(100, reader, false);
        assertEquals(PageSizes.READ_AHEAD, reader.reads);
        assertEquals(100, sizes.count());
        assertEquals(109, sizes.width(9));
        assertFalse(sizes.isEstimated(9));
        assertTrue(sizes.isEstimated(50));
        assertEquals(104.5, sizes.width(50), 1e-9, "average of 100..109");
        assertEquals(200, sizes.height(50));
    }

    @Test
    void resolveReadsEstimatedPagesAndReportsTheCurrentOne() {
        PageSizes sizes = new PageSizes();
        sizes.load(100, new Reader(), false);
        PageSizes.Resolution r = sizes.resolve(List.of(5, 50, 60), 50);
        assertTrue(r.changed());
        assertTrue(r.currentChanged());
        assertEquals(150, sizes.width(50));
        assertFalse(sizes.isEstimated(60));
        assertFalse(sizes.resolve(List.of(50, 60), 50).changed(), "already read");
    }

    @Test
    void reloadKeepsWhatWasReadWhenAsked() {
        Reader reader = new Reader();
        PageSizes sizes = new PageSizes();
        sizes.load(100, reader, false);
        sizes.resolve(List.of(70), 0);
        sizes.load(100, reader, true);
        assertFalse(sizes.isEstimated(70), "re-read for the new rotation");
        sizes.load(100, reader, false);
        assertTrue(sizes.isEstimated(70), "a new document starts over");
    }

    @Test
    void unreadablePagesAreLetter() {
        PageSizes sizes = new PageSizes();
        sizes.load(2, i -> null, false);
        assertEquals(612, sizes.width(1));
        assertEquals(792, sizes.height(1));
    }

    @Test
    void fitZoom() {
        PageSizes sizes = new PageSizes();
        sizes.load(3, i -> new PDimension(i == 1 ? 300 : 200, 400), false);
        assertTrue(Double.isNaN(sizes.fitZoom(FitMode.NONE, ViewMode.CONTINUOUS, false, 0, 10, 800, 600)));
        assertEquals((800 - 20) / 200.0, sizes.fitZoom(FitMode.WIDTH, ViewMode.SINGLE_PAGE, false, 0, 10, 800, 600));
        assertEquals((800 - 20) / 300.0, sizes.fitZoom(FitMode.WIDTH, ViewMode.CONTINUOUS, false, 0, 10, 800, 600),
                "continuous fit width takes the widest page");
        assertEquals((600 - 20) / 400.0, sizes.fitZoom(FitMode.PAGE, ViewMode.SINGLE_PAGE, false, 0, 10, 800, 600));
    }

    @Test
    void facingPartners() {
        PageSizes sizes = new PageSizes();
        sizes.load(5, i -> new PDimension(100, 100), false);
        assertEquals(1, sizes.partnerOf(0, false));
        assertEquals(0, sizes.partnerOf(1, false));
        assertEquals(-1, sizes.partnerOf(4, false), "the last odd page stands alone");
        assertEquals(-1, sizes.partnerOf(0, true), "the cover stands alone");
        assertEquals(2, sizes.partnerOf(1, true));
    }
}
