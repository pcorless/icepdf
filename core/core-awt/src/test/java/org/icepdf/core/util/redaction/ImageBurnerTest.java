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

import org.icepdf.core.pobjects.*;
import org.icepdf.core.pobjects.graphics.images.ImageStream;
import java.util.Iterator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Where a burn lands inside an image.
 * <p>
 * Deliberately checks the decoded image rather than a render of the page: the redaction annotation
 * paints a black rectangle over the same area, so a page render looks correct whether or not the
 * pixels underneath were ever touched. Only the raster tells the truth.
 */
public class ImageBurnerTest {

    /**
     * The fixture places an 8x8 image with {@code cm [0 60 -60 0 200 100]}, a quarter turn. Under
     * that placement the image's top-left quadrant - the red one - covers user space x 140..170,
     * y 100..130. A mapping taken from the placement's bounding box cannot tell that quadrant from
     * another, because the box is square and carries no notion of which way the image faces, so it
     * blacks out the wrong one.
     */
    @DisplayName("a burn lands on the right quadrant of a rotated image")
    @Test
    public void burnLandsOnTheCorrectQuadrantOfARotatedImage() throws Exception {
        Document document = new Document();
        document.setFile(Paths.get("src/test/resources/redaction/rotated_image.pdf").toString());
        try {
            Page page = document.getPageTree().getPage(0);
            page.init();

            // Inset slightly so the fill cannot depend on how a boundary pixel rounds.
            page.addAnnotation(RedactionFixtures.redactionOver(document,
                    new Rectangle(142, 102, 26, 26)), true);
            Redactor.redact(document, RedactionRequest.ofAnnotations());

            BufferedImage burned = burnedImage(document);
            assertNotNull(burned, "the burn should have left a decoded image behind");
            assertEquals(8, burned.getWidth(), "fixture image should be 8x8");

            // The red quadrant is the one under the redaction.
            assertTrue(isBlack(burned, 1, 1), "top-left quadrant should be burned, was " +
                    describe(burned, 1, 1));
            assertTrue(isBlack(burned, 2, 2), "top-left quadrant should be burned, was " +
                    describe(burned, 2, 2));
            // The other three are untouched, and each keeps its own colour - which also shows the
            // burn did not simply blacken everything.
            assertEquals("#00FF00", describe(burned, 6, 1), "top-right quadrant should be green");
            assertEquals("#0000FF", describe(burned, 1, 6), "bottom-left quadrant should be blue");
            assertEquals("#FFFFFF", describe(burned, 6, 6), "bottom-right quadrant should be white");
        } finally {
            document.dispose();
        }
    }

    // -- helpers ---------------------------------------------------------------------------------


    /**
     * The burned raster, taken from the change the burn registered rather than from the page's
     * resources: resolving the resource again yields a different ImageStream instance, which has
     * never been burned and still holds the original samples.
     */
    private BufferedImage burnedImage(Document document) {
        StateManager stateManager = document.getCatalog().getLibrary().getStateManager();
        Iterator<StateManager.Change> changes = stateManager.iteratorSortedByObjectNumber();
        while (changes.hasNext()) {
            Object changed = changes.next().getPObject().getObject();
            if (changed instanceof ImageStream) {
                return ((ImageStream) changed).getDecodedImage();
            }
        }
        return null;
    }

    private boolean isBlack(BufferedImage image, int x, int y) {
        int rgb = image.getRGB(x, y) & 0xFFFFFF;
        return rgb == 0;
    }

    private String describe(BufferedImage image, int x, int y) {
        return String.format("#%06X", image.getRGB(x, y) & 0xFFFFFF);
    }
}
