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
import org.icepdf.core.pobjects.annotations.RedactionAnnotation;
import org.icepdf.core.pobjects.graphics.images.ImageStream;
import org.icepdf.core.util.updater.WriteMode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.*;
import java.io.ByteArrayOutputStream;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A redaction is burned in the colour it is drawn in.
 * <p>
 * A redaction annotation carries a colour, and that colour is what the viewer paints its marker in.
 * The burn used to ignore it and use {@link RedactionOptions#getRedactionColor()} for everything, so
 * a redaction the user made red left a red marker over pixels burned black. On a scanned page, where
 * the burn is the whole visible result, the user's choice simply did not happen.
 * <p>
 * The fixture is a 2x2 image drawn across page x 20..80, y 140..180 - one image, so both redactions
 * land in the same raster and the two colours have to survive together.
 */
public class RedactionColourTest {

    @DisplayName("each redaction is burned in its own colour")
    @Test
    public void eachRedactionUsesItsOwnColour() throws Exception {
        Document document = new Document();
        document.setFile(Paths.get("src/test/resources/redaction/image_drawn_twice.pdf").toString());
        byte[] redacted;
        try {
            Page page = document.getPageTree().getPage(0);
            page.init();
            // The first placement covers x 20..80, y 140..180; its two columns split at x 50.
            page.addAnnotation(RedactionFixtures.redactionOver(document,
                    new Rectangle(20, 140, 29, 40), Color.RED), true);
            page.addAnnotation(RedactionFixtures.redactionOver(document,
                    new Rectangle(51, 140, 29, 40), Color.BLUE), true);
            Redactor.configure(document, RedactionRequest.ofAnnotations());

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.saveToOutputStream(out, WriteMode.FULL_UPDATE);
            redacted = out.toByteArray();
        } finally {
            document.dispose();
        }

        // Three bytes a pixel, two pixels a row: left column red, right column blue, both rows.
        assertEquals("ff0000 0000ff ff0000 0000ff", samples(redacted),
                "each column burned in the colour its own redaction was drawn in");
    }

    /**
     * The option is what a redaction that names no colour of its own falls back to - which is what a
     * headless caller building annotations from search hits produces.
     */
    @DisplayName("a redaction with no colour of its own falls back to the option")
    @Test
    public void redactionWithoutAColourUsesTheOption() throws Exception {
        Document document = new Document();
        document.setFile(Paths.get("src/test/resources/redaction/image_drawn_twice.pdf").toString());
        byte[] redacted;
        try {
            Page page = document.getPageTree().getPage(0);
            page.init();
                page.addAnnotation(colourlessRedaction(document), true);
            Redactor.configure(document, RedactionRequest.ofAnnotations()
                    .with(RedactionOptions.defaults().redactionColor(Color.GREEN)));

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.saveToOutputStream(out, WriteMode.FULL_UPDATE);
            redacted = out.toByteArray();
        } finally {
            document.dispose();
        }

        assertEquals("00ff00 00ff00 00ff00 00ff00", samples(redacted),
                "every covered pixel in the option's colour");
    }

    /**
     * A redaction that names no colour, which is what a caller building annotations from search hits
     * without setting one produces.
     */
    private RedactionAnnotation colourlessRedaction(Document document) {
        RedactionAnnotation annotation = RedactionFixtures.redactionOver(document,
                new Rectangle(10, 130, 80, 60));
        // No /C at all, which is the only way an annotation reaches the burn without a colour.
        annotation.getEntries().remove(RedactionAnnotation.COLOR_KEY);
        return annotation;
    }

    /**
     * The image's samples as written, grouped a pixel at a time.
     * <p>
     * Read as bytes rather than decoded: this fixture's stream decodes reliably only once something
     * has already read it, so a decode here is a test of the harness as much as of the redaction.
     */
    private String samples(byte[] pdf) throws Exception {
        Document document = new Document();
        document.setByteArray(pdf, 0, pdf.length, "redacted");
        try {
            Page page = document.getPageTree().getPage(0);
            page.init();
            ImageStream image = (ImageStream) page.getResources().getXObject(new Name("Im0"));
            byte[] bytes = image.getDecodedStreamBytes();
            StringBuilder out = new StringBuilder();
            for (int i = 0; i < bytes.length; i++) {
                if (i > 0 && i % 3 == 0) {
                    out.append(' ');
                }
                out.append(String.format("%02x", bytes[i]));
            }
            return out.toString();
        } finally {
            document.dispose();
        }
    }
}
