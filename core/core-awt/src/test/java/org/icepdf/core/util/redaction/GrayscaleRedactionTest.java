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
import org.icepdf.core.pobjects.graphics.images.ImageStream;
import org.icepdf.core.util.updater.WriteMode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.*;
import java.io.ByteArrayOutputStream;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A greyscale image stays greyscale through a burn.
 * <p>
 * The burn converted every image it touched to RGB, which is three bytes a pixel for a one-channel
 * image and, on the scanned page that most redaction work is done on, most of the file. Nothing about
 * the redaction needed it - the conversion was a shortcut taken while the feature was being built.
 * <p>
 * The fixture is 4x1 running dark to light, so the samples a redaction covers started out different
 * from each other and from the value written over them.
 */
public class GrayscaleRedactionTest {

    private static final String FIXTURE = "gray_image.pdf";

    /**
     * The image spans x 20..100, so covering its left half takes the first two of four samples.
     */
    @DisplayName("a redacted greyscale image is still greyscale")
    @Test
    public void greyscaleSurvivesTheBurn() throws Exception {
        ImageStream redacted = redact(new Rectangle(20, 140, 40, 40));

        assertEquals(new Name("DeviceGray"), redacted.getEntries().get(new Name("ColorSpace")),
                "the colour space should not have been widened to RGB");
        assertEquals(8, redacted.getImageParams().getBitsPerComponent(), "one 8-bit channel");
        // One byte per pixel, not three. Black is 0 in DeviceGray, and the uncovered samples stand.
        assertEquals("00 00 a0 ff", samples(redacted), "covered samples black, the rest untouched");
    }

    /**
     * The control. Without it "the samples are right" is equally well explained by an image the burn
     * never touched, and the interesting assertion above - that the data is a third of the size -
     * would hold just as well for a file that was never redacted.
     */
    @DisplayName("a greyscale image clear of the redaction is untouched")
    @Test
    public void greyscaleOutsideTheRedactionIsUnchanged() throws Exception {
        ImageStream redacted = redact(new Rectangle(0, 0, 15, 15));

        assertEquals("00 50 a0 ff", samples(redacted), "the image should be exactly as it started");
    }

    // -- helpers ---------------------------------------------------------------------------------

    private ImageStream redact(Rectangle area) throws Exception {
        Document document = new Document();
        document.setFile(Paths.get("src/test/resources/redaction/" + FIXTURE).toString());
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

    private String samples(ImageStream imageStream) throws Exception {
        StringBuilder out = new StringBuilder();
        for (byte sample : imageStream.getDecodedStreamBytes()) {
            if (out.length() > 0) {
                out.append(' ');
            }
            out.append(String.format("%02x", sample));
        }
        return out.toString();
    }
}
