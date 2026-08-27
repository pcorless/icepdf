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
package org.icepdf.core.util.redaction;

import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.Name;
import org.icepdf.core.pobjects.Page;
import org.icepdf.core.pobjects.Resources;
import org.icepdf.core.pobjects.graphics.images.ImageStream;
import org.icepdf.core.util.updater.WriteMode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.*;
import java.io.ByteArrayOutputStream;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Redacting an {@code /ImageMask true} stencil.
 * <p>
 * A stencil is the odd one out among images: one bit per pixel, no colour space, and each sample
 * selects "paint whatever fill colour is in force" or "leave this pixel alone" - PDF 32000-1
 * §8.9.6.2. There is no black in it to paint over, which is what the general image burn assumed.
 * <p>
 * The fixture's stencil is 8x8, drawn red across x 20..120 and y 100..150 of the page. Its top half
 * paints and its bottom half does not, so a redaction can be checked against a region whose halves
 * started out different.
 */
public class StencilRedactionTest {

    /**
     * The redaction covers the left half of the image, so the left four columns of every row must
     * end up painting - including the bottom rows, which did not before - and the right four columns
     * must be exactly as they started.
     */
    @DisplayName("a redaction over a stencil sets the covered samples to paint")
    @Test
    public void redactionSetsCoveredSamplesToPaint() throws Exception {
        byte[] rows = redactedStencilRows(new Rectangle(20, 100, 50, 50));

        // 0 paints. Rows 0-3 painted already; rows 4-7 did not, so only their left half changes.
        assertEquals("00 00 00 00 0f 0f 0f 0f", hex(rows),
                "left half painting throughout, right half untouched");
    }

    /**
     * The dictionary has to keep describing a stencil. Sending one through the general raster
     * encoder produced an image still declared {@code /ImageMask true} but carrying eight-bit RGB
     * samples and a {@code /ColorSpace}, a combination the specification does not allow - a reader is
     * told to read one-bit stencil data and handed something else entirely.
     */
    @DisplayName("a redacted stencil is still a stencil")
    @Test
    public void redactedStencilKeepsItsDictionary() throws Exception {
        Document document = redact(new Rectangle(20, 100, 50, 50));
        try {
            ImageStream stencil = stencil(document);
            assertTrue(stencil.getImageParams().isImageMask(), "/ImageMask should survive");
            assertEquals(1, stencil.getImageParams().getBitsPerComponent(),
                    "a stencil is one bit per pixel");
            assertNull(stencil.getEntries().get(new Name("ColorSpace")),
                    "a stencil has no colour space");
        } finally {
            document.dispose();
        }
    }

    /**
     * The control. Without it "the samples changed" is equally well explained by the burn rewriting
     * every stencil it is handed, redaction or no redaction.
     */
    @DisplayName("a stencil clear of the redaction is untouched")
    @Test
    public void stencilOutsideTheRedactionIsUnchanged() throws Exception {
        // Bottom-left corner of the page, nowhere near the image at y 100..150.
        byte[] rows = redactedStencilRows(new Rectangle(0, 0, 15, 15));

        assertEquals("00 00 00 00 ff ff ff ff", hex(rows), "the stencil should be as it started");
    }

    // -- helpers ---------------------------------------------------------------------------------

    private byte[] redactedStencilRows(Rectangle area) throws Exception {
        Document document = redact(area);
        try {
            return stencil(document).getDecodedStreamBytes();
        } finally {
            document.dispose();
        }
    }

    /**
     * @return the redacted document, reopened from the bytes that were written
     */
    private Document redact(Rectangle area) throws Exception {
        Document document = new Document();
        document.setFile(Paths.get("src/test/resources/redaction/stencil_mask.pdf").toString());
        byte[] redacted;
        try {
            Page page = document.getPageTree().getPage(0);
            page.init();
            page.addAnnotation(RedactionFixtures.redactionOver(document, area), true);
            Redactor.configure(document, RedactionRequest.ofAnnotations());

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.saveToOutputStream(out, WriteMode.FULL_UPDATE);
            redacted = out.toByteArray();
        } finally {
            document.dispose();
        }
        Document written = new Document();
        written.setByteArray(redacted, 0, redacted.length, "redacted");
        return written;
    }

    private ImageStream stencil(Document document) throws Exception {
        Page page = document.getPageTree().getPage(0);
        page.init();
        Resources resources = page.getResources();
        Object image = resources.getXObject(new Name("Im0"));
        if (!(image instanceof ImageStream)) {
            throw new IllegalStateException("the fixture is meant to have a stencil");
        }
        return (ImageStream) image;
    }

    /**
     * One byte per row of the 8x8 stencil, which is how the fixture is built.
     */
    private String hex(byte[] bytes) {
        StringBuilder out = new StringBuilder();
        for (byte b : bytes) {
            if (out.length() > 0) {
                out.append(' ');
            }
            out.append(String.format("%02x", b));
        }
        return out.toString();
    }
}
