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

import org.icepdf.core.pobjects.graphics.images.jpx.Jpeg2000Fixtures;
import org.icepdf.core.pobjects.graphics.images.jpx.Jpeg2000Fixtures.Outcome;
import org.icepdf.core.pobjects.graphics.images.jpx.Jpeg2000Fixtures.Reference;
import org.icepdf.core.pobjects.graphics.images.jpx.Jpeg2000Fixtures.Result;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Provenance: ORIGINAL.
 * <p>
 * The GH-553 encoder bug in a fixture we own: six tile-parts per tile, TNsot says five. The
 * conformance baseline records what the reader does with the stream as-is; this pins that ICEpdf's
 * repair turns it back into the intact stream and that the repaired stream decodes exactly.
 */
public class Jpeg2000TilePartsRepairTest {

    @DisplayName("the repair restores the TNsot-undercount fixture to the stream the encoder wrote")
    @Test
    public void repairRestoresIntactStream() {
        byte[] broken = Jpeg2000Fixtures.bytes("tnsot-undercount.j2k");
        byte[] intact = Jpeg2000Fixtures.bytes("tileparts-res.j2k");
        assertFalse(java.util.Arrays.equals(broken, intact));
        assertArrayEquals(intact, Jpeg2000TileParts.repairTilePartCounts(broken));
    }

    @DisplayName("the repaired TNsot-undercount fixture decodes exactly")
    @Test
    public void repairedStreamDecodesExactly() throws Exception {
        assumeTrue(Jpeg2000Fixtures.readerSpi() != null, "no JPEG 2000 reader installed");
        Reference ref = Jpeg2000Fixtures.references().stream()
                .filter(r -> r.name.equals("tnsot-undercount")).findFirst().orElseThrow(IllegalStateException::new);
        byte[] repaired = Jpeg2000TileParts.repairTilePartCounts(Jpeg2000Fixtures.bytes(ref.file));
        BufferedImage image = Jpeg2000Fixtures.withTimeout(() -> {
            try (ImageInputStream in = Jpeg2000Fixtures.stream(repaired)) {
                ImageReader reader = Jpeg2000Fixtures.reader(in);
                try {
                    reader.setInput(in, true, true);
                    return reader.read(0);
                } finally {
                    reader.dispose();
                }
            }
        });
        Result r = new Result();
        Jpeg2000Fixtures.compare(ref, Jpeg2000Fixtures.components(ref), image.getRaster(), image.getColorModel(), r);
        assertEquals(Outcome.EXACT, r.outcome, r.summary());
    }
}
