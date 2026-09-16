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
package org.icepdf.core.pobjects.annotations.utils;

import org.icepdf.core.pobjects.Name;
import org.icepdf.core.pobjects.StateManager;
import org.icepdf.core.pobjects.graphics.Shapes;
import org.icepdf.core.pobjects.graphics.commands.DrawCmd;
import org.icepdf.core.pobjects.graphics.commands.TransformDrawCmd;
import org.icepdf.core.pobjects.structure.CrossReferenceRoot;
import org.icepdf.core.util.Library;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Where an image lands when it is written into an appearance stream.
 * <p>
 * An appearance is drawn once, when it is created, and then only looked at - so a placement that is
 * wrong produces a signature sitting over its own text, or half off the edge of its field, with
 * nothing to say so.  These read the transform that is actually written rather than a rendering of
 * it, since that transform is the placement.
 */
public class ContentWriterUtilsTest {

    private final Library library = new Library();

    {
        library.setStateManager(new StateManager(new CrossReferenceRoot(library)));
    }

    /** A wide image, so that fitting it to a region is decided by the width. */
    private static BufferedImage image() {
        BufferedImage image = new BufferedImage(400, 160, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = image.createGraphics();
        g2d.setColor(Color.RED);
        g2d.fillRect(0, 0, 400, 160);
        g2d.dispose();
        return image;
    }

    /**
     * Where the image ends up, as a rectangle in the shapes' own space.  The placement is written as
     * a transform of the unit square with a negative height, which is how a PDF image's own upward
     * axis is flipped back into the appearance's downward one.
     */
    private Rectangle2D placementOf(int scale, Rectangle2D region) {
        Shapes shapes = new Shapes();
        assertNotNull(ContentWriterUtils.addImageToShapes(library, new Name("img"), null, null,
                image(), shapes, region, scale));

        AffineTransform placement = null;
        for (DrawCmd command : shapes.getShapes()) {
            if (command instanceof TransformDrawCmd) {
                placement = ((TransformDrawCmd) command).getAffineTransform();
            }
        }
        assertNotNull(placement, "the image should have been given a placement");
        double width = placement.getScaleX();
        double height = -placement.getScaleY();
        return new Rectangle2D.Double(placement.getTranslateX(),
                placement.getTranslateY() - height, width, height);
    }

    @DisplayName("at full scale the image fits the region it was given")
    @Test
    public void fullScaleFitsTheRegion() {
        Rectangle2D region = new Rectangle2D.Float(10, 20, 200, 200);
        Rectangle2D placed = placementOf(100, region);

        // Wider than it is tall, so the width is what runs out first.
        assertEquals(200, placed.getWidth(), 0.01);
        assertEquals(80, placed.getHeight(), 0.01, "and the aspect ratio is kept");
        assertTrue(region.contains(placed), placed + " should sit inside " + region);
    }

    @DisplayName("a smaller scale is a share of the region, not of the image's pixel size")
    @Test
    public void scaleIsAShareOfTheRegion() {
        // Measured against the region, the same percentage means the same thing for a 200 pixel
        // stamp and a 2000 pixel scan of the same signature.  Measured against the image's own
        // size - which is what it used to be - it does not.
        Rectangle2D region = new Rectangle2D.Float(10, 20, 200, 200);
        Rectangle2D half = placementOf(50, region);

        assertEquals(100, half.getWidth(), 0.01);
        assertEquals(40, half.getHeight(), 0.01);
    }

    @DisplayName("the image is centred in its region")
    @Test
    public void imageIsCentredInTheRegion() {
        Rectangle2D region = new Rectangle2D.Float(10, 20, 200, 200);
        Rectangle2D placed = placementOf(50, region);

        assertEquals(region.getCenterX(), placed.getCenterX(), 0.01);
        assertEquals(region.getCenterY(), placed.getCenterY(), 0.01);
    }

    @DisplayName("an image asked for larger than its region is still held to it")
    @Test
    public void oversizedImageIsHeldToTheRegion() {
        // Drawn at the size asked for it would be clipped by the appearance's bounding box, losing
        // part of the signature with nothing to say that it had.
        Rectangle2D region = new Rectangle2D.Float(0, 0, 50, 50);
        Rectangle2D placed = placementOf(400, region);

        assertTrue(region.contains(placed), placed + " should sit inside " + region);
        assertEquals(50, placed.getWidth(), 0.01, "it should fill the region, not overflow it");
    }
}
