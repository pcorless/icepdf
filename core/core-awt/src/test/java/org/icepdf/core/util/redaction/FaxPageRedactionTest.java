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
import org.icepdf.core.pobjects.graphics.images.ImageDecoderFactory;
import org.icepdf.core.pobjects.graphics.images.ImageStream;
import org.icepdf.core.util.updater.WriteMode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Redacting a page that is a single CCITT Group 4 image - a scan or a fax.
 * <p>
 * This is the common shape of a document that gets redacted, and it is the one image kind written
 * back through {@code FaxEncoder}, which nothing else covers. Two things have to hold: the covered
 * area has to go, and everything else has to survive the decode and re-encode untouched. A bilevel
 * round trip is where an off-by-one in the black/white convention shows up as a negative of the
 * page, which would be both obvious and catastrophic.
 * <p>
 * The fixture is 64x32 drawn across page x 20..148, y 80..144.
 */
public class FaxPageRedactionTest {

    /** The image as it decodes: one character per 6 pixels, K for black. */
    private static final String CLEAN =
            ".....KKKKKK/.....KKKKKK/.........../.....KKKKKK/.....KKKKKK/";

    @DisplayName("a redaction over a fax page burns the covered area and leaves the rest")
    @Test
    public void redactionBurnsTheCoveredArea() throws Exception {
        // Page x 24..64 covers image columns 2..22; y 84..108 is its lower rows.
        String redacted = mapOf(redact(new Rectangle(24, 84, 40, 24)));

        assertEquals(".....KKKKKK/.....KKKKKK/.........../KKKK.KKKKKK/KKKK.KKKKKK/", redacted,
                "the covered columns of the lower rows should be black, everything else as it was");
    }

    /**
     * The control, and the one that would catch a bilevel convention error: a redaction that misses
     * the image must leave it decoding exactly as it did, through whatever re-encoding it goes.
     */
    @DisplayName("a fax page clear of the redaction survives unchanged")
    @Test
    public void faxOutsideTheRedactionIsUnchanged() throws Exception {
        assertEquals(CLEAN, mapOf(redact(new Rectangle(0, 0, 15, 15))),
                "the image should decode exactly as it started");
    }

    /**
     * A fax stays a fax. Written back as eight-bit greyscale it would still look right, and would be
     * eight times the samples for an image that only ever has two values - on a real scanned page,
     * a large file getting larger for nothing.
     */
    @DisplayName("a redacted fax page is still a bilevel CCITT image")
    @Test
    public void faxKeepsItsEncoding() throws Exception {
        ImageStream redacted = redact(new Rectangle(24, 84, 40, 24));

        assertEquals(1, redacted.getImageParams().getBitsPerComponent(), "still one bit per pixel");
        assertEquals(new Name("CCITTFaxDecode"), redacted.getEntries().get(new Name("Filter")),
                "still CCITT, not re-encoded as something bigger");
    }

    // -- helpers ---------------------------------------------------------------------------------

    private ImageStream redact(Rectangle area) throws Exception {
        Document document = new Document();
        document.setFile(Paths.get("src/test/resources/redaction/fax_page.pdf").toString());
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
        Page page = written.getPageTree().getPage(0);
        page.init();
        return (ImageStream) page.getResources().getXObject(new Name("Im0"));
    }

    /**
     * Decoded and sampled every sixth pixel, which is enough to see the shape and small enough to
     * assert as a literal.
     */
    private String mapOf(ImageStream imageStream) throws Exception {
        BufferedImage image = ImageDecoderFactory.createDecoder(imageStream, null).decode();
        StringBuilder map = new StringBuilder();
        for (int y = 2; y < image.getHeight(); y += 6) {
            for (int x = 2; x < image.getWidth(); x += 6) {
                map.append((image.getRGB(x, y) & 0xFF) < 128 ? 'K' : '.');
            }
            map.append('/');
        }
        return map.toString();
    }
}
