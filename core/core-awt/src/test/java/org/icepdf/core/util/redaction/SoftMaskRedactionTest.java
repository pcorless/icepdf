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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Redacting an image that carries an {@code /SMask}.
 * <p>
 * A soft mask is a greyscale image whose samples are the base image's alpha - white opaque, black
 * transparent (§11.6.5.3). Leaving it alone costs a redaction twice over: the block burned into the
 * base image is only visible where the mask lets it show, so a redaction over a transparent area
 * paints something nobody can see; and the mask still carries the outline of what was removed, which
 * for a cut-out signature or logo is recognisable on its own.
 * <p>
 * The fixture's mask is 4x1 running dark to light - 0, 80, 160, 255 - so the covered samples started
 * at different values and there is something to tell apart.
 */
public class SoftMaskRedactionTest {

    private static final String FIXTURE = "soft_masked_image.pdf";

    /**
     * The image spans x 20..100. Covering its left half takes in the first two of the four samples.
     */
    @DisplayName("a redaction over a soft-masked image makes the covered area opaque")
    @Test
    public void redactionMakesTheSoftMaskOpaque() throws Exception {
        assertEquals("ff ff a0 ff", softMaskSamples(new Rectangle(20, 140, 40, 40)),
                "the covered samples should be fully opaque, the rest untouched");
    }

    /**
     * The control. Filling the redaction colour into a soft mask - which is what treating it like any
     * other image would do - writes black, and black in a soft mask is fully <em>transparent</em>: the
     * redaction would erase itself. So "the samples changed" is not enough; they have to have changed
     * in the right direction, and the samples clear of the redaction have to be left alone.
     */
    @DisplayName("a soft mask clear of the redaction is untouched")
    @Test
    public void softMaskOutsideTheRedactionIsUnchanged() throws Exception {
        // Bottom-left of the page, nowhere near the image at y 140..180.
        assertEquals("00 50 a0 ff", softMaskSamples(new Rectangle(0, 0, 15, 15)),
                "the soft mask should be exactly as it started");
    }


    // -- colour-key masks -------------------------------------------------------------------------

    /**
     * A colour-key {@code /Mask} names colours to drop rather than paint. The fixture drops
     * near-black and the redaction colour is black, so the block burned into the image is dropped
     * along with it - the pixels are gone, but a reader sees the page behind the image, which is
     * indistinguishable from a redaction that never ran.
     * <p>
     * Reported rather than worked around: nudging the colour would change what the caller asked for,
     * and dropping the mask would make every other masked-out part of the image opaque.
     */
    @DisplayName("a colour-key mask that would hide the redaction is reported")
    @Test
    public void colourKeyMaskHidingTheRedactionIsReported() throws Exception {
        RedactionReport report = redactColourKeyed(Color.BLACK);

        assertEquals(1, report.getImagesBurned(), "the image was still burned");
        assertEquals(1, report.getWarnings().size(), "warnings: " + report.getWarnings());
        assertTrue(report.getWarnings().get(0).getDetail().contains("colour-key"),
                report.getWarnings().get(0).getDetail());
    }

    /**
     * The control: a redaction colour outside the masked range paints normally, and says nothing.
     */
    @DisplayName("a colour-key mask clear of the redaction colour is not reported")
    @Test
    public void colourKeyMaskThatDoesNotHideTheRedactionIsQuiet() throws Exception {
        RedactionReport report = redactColourKeyed(Color.RED);

        assertEquals(1, report.getImagesBurned(), "the image was burned");
        assertEquals(List.of(), report.getWarnings(),
                "red is outside the masked range, so nothing is hidden");
    }

    private RedactionReport redactColourKeyed(Color colour) throws Exception {
        Document document = new Document();
        document.setFile(Paths.get("src/test/resources/redaction/colour_key_masked_image.pdf").toString());
        try {
            Page page = document.getPageTree().getPage(0);
            page.init();
            page.addAnnotation(RedactionFixtures.redactionOver(document, new Rectangle(20, 140, 40, 40)), true);
            Redactor.configure(document, RedactionRequest.ofAnnotations()
                    .with(RedactionOptions.defaults().redactionColor(colour)));

            document.saveToOutputStream(new ByteArrayOutputStream(), WriteMode.FULL_UPDATE);
            return document.getRedactionReport();
        } finally {
            document.dispose();
        }
    }


    /**
     * A scanned page is one large image with as many redactions drawn over it as the operator needed,
     * so two annotations covering different parts of one image is the ordinary case, not an edge one.
     * <p>
     * The mask has to carry both. Each burn decodes the mask afresh from the stream, so unless the
     * result of the previous one is what the next starts from, only the last redaction survives in
     * the mask - and the first is left with a transparent hole in the shape of what was removed.
     */
    @DisplayName("two redactions over one image both reach the soft mask")
    @Test
    public void twoRedactionsBothReachTheSoftMask() throws Exception {
        // Samples sit at x 20..40, 40..60, 60..80, 80..100. Cover the middle two, separately.
        assertEquals("00 ff ff ff", softMaskSamples(
                        new Rectangle(42, 145, 15, 20), new Rectangle(62, 145, 15, 20)),
                "both covered samples should be opaque");
    }


    /**
     * The other way a mask gets burned more than once: one image drawn at two places, each with its
     * own redaction. The image and its mask are a single shared object, but each placement is a
     * separate pass, so a pass that decodes the mask from the stream starts from the original and
     * throws away what the previous placement burned into it.
     */
    @DisplayName("a soft mask carries redactions from every placement of its image")
    @Test
    public void softMaskCarriesEveryPlacement() throws Exception {
        Document document = new Document();
        document.setFile(Paths.get("src/test/resources/redaction/soft_masked_drawn_twice.pdf").toString());
        byte[] redacted;
        try {
            Page page = document.getPageTree().getPage(0);
            page.init();
            // Second sample of the upper placement (y 140..180), third of the lower (y 40..80).
            page.addAnnotation(RedactionFixtures.redactionOver(document, new Rectangle(42, 145, 15, 20)), true);
            page.addAnnotation(RedactionFixtures.redactionOver(document, new Rectangle(62, 45, 15, 20)), true);
            Redactor.configure(document, RedactionRequest.ofAnnotations());
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.saveToOutputStream(out, WriteMode.FULL_UPDATE);
            redacted = out.toByteArray();
        } finally {
            document.dispose();
        }

        Document written = new Document();
        written.setByteArray(redacted, 0, redacted.length, "redacted");
        try {
            Page page = written.getPageTree().getPage(0);
            page.init();
            ImageStream image = (ImageStream) page.getResources().getXObject(new Name("Im0"));
            StringBuilder out = new StringBuilder();
            for (byte sample : image.getImageParams().getSMaskImageStream().getDecodedStreamBytes()) {
                if (out.length() > 0) {
                    out.append(' ');
                }
                out.append(String.format("%02x", sample));
            }
            assertEquals("00 ff ff ff", out.toString(),
                    "both placements' redactions should be in the shared mask");
        } finally {
            written.dispose();
        }
    }

    // -- helpers ---------------------------------------------------------------------------------

    private String softMaskSamples(Rectangle... areas) throws Exception {
        Document document = new Document();
        document.setFile(Paths.get("src/test/resources/redaction/" + FIXTURE).toString());
        byte[] redacted;
        try {
            Page page = document.getPageTree().getPage(0);
            page.init();
            for (Rectangle area : areas) {
                page.addAnnotation(RedactionFixtures.redactionOver(document, area), true);
            }
            Redactor.configure(document, RedactionRequest.ofAnnotations());

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.saveToOutputStream(out, WriteMode.FULL_UPDATE);
            redacted = out.toByteArray();
        } finally {
            document.dispose();
        }

        Document written = new Document();
        written.setByteArray(redacted, 0, redacted.length, "redacted");
        try {
            Page page = written.getPageTree().getPage(0);
            page.init();
            Resources resources = page.getResources();
            ImageStream image = (ImageStream) resources.getXObject(new Name("Im0"));
            ImageStream softMask = image.getImageParams().getSMaskImageStream();
            StringBuilder out = new StringBuilder();
            for (byte sample : softMask.getDecodedStreamBytes()) {
                if (out.length() > 0) {
                    out.append(' ');
                }
                out.append(String.format("%02x", sample));
            }
            return out.toString();
        } finally {
            written.dispose();
        }
    }
}
