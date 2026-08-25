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
import org.icepdf.core.pobjects.Page;
import org.icepdf.core.util.updater.WriteMode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.*;
import java.io.ByteArrayOutputStream;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Redacting an image that is drawn more than once.
 * <p>
 * An image XObject is a shared object: one instance serves every placement of it, on every page. Where
 * it is drawn belongs to the {@code Do}, not to the image, so the area a redaction has to be tested
 * against is the placement's - and the fixture draws the same image at two of them.
 */
public class ImagePlacementRedactionTest {

    private static final String FIXTURE = "image_drawn_twice.pdf";

    /**
     * The first placement covers x 20..80, y 140..180.
     */
    @DisplayName("a redaction over the first placement burns the image")
    @Test
    public void redactionOverFirstPlacementBurns() throws Exception {
        assertEquals(1, imagesBurned(new Rectangle(20, 140, 60, 40)),
                "the redaction covers the first placement");
    }

    /**
     * The second placement covers x 20..80, y 40..80.
     * <p>
     * This is the one that was missed. Bounds were cached on the shared image stream the first time
     * they were asked for, so both placements tested against where the <em>first</em> one sat: a
     * redaction over the second matched nothing and the image was left as it was.
     */
    @DisplayName("a redaction over the second placement burns the image too")
    @Test
    public void redactionOverSecondPlacementBurns() throws Exception {
        assertEquals(1, imagesBurned(new Rectangle(20, 40, 60, 40)),
                "the redaction covers the second placement");
    }

    /**
     * The control. Without it "the burn happened" is equally well explained by burning every image
     * regardless of where the redaction is - which is the failure the cached bounds could also have
     * produced, in the other direction.
     */
    @DisplayName("a redaction clear of both placements burns nothing")
    @Test
    public void redactionOverNeitherPlacementBurnsNothing() throws Exception {
        // Right-hand side of the page; both placements end at x 80.
        assertEquals(0, imagesBurned(new Rectangle(200, 40, 60, 40)),
                "nothing covers either placement");
    }

    // -- helpers ---------------------------------------------------------------------------------

    private int imagesBurned(Rectangle area) throws Exception {
        Document document = new Document();
        document.setFile(Paths.get("src/test/resources/redaction/" + FIXTURE).toString());
        try {
            Page page = document.getPageTree().getPage(0);
            page.init();
            page.addAnnotation(RedactionFixtures.redactionOver(document, area), true);
            Redactor.configure(document, RedactionRequest.ofAnnotations());

            document.saveToOutputStream(new ByteArrayOutputStream(), WriteMode.FULL_UPDATE);
            return document.getRedactionReport().getImagesBurned();
        } finally {
            document.dispose();
        }
    }
}
