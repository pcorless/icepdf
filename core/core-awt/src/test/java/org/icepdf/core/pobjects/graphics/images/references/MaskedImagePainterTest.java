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
package org.icepdf.core.pobjects.graphics.images.references;

import org.icepdf.core.pobjects.DictionaryEntries;
import org.icepdf.core.pobjects.Name;
import org.icepdf.core.pobjects.Resources;
import org.icepdf.core.pobjects.graphics.GraphicsState;
import org.icepdf.core.pobjects.graphics.Shapes;
import org.icepdf.core.pobjects.graphics.images.ImageStream;
import org.icepdf.core.pobjects.graphics.images.MaskedImage;
import org.icepdf.core.pobjects.graphics.images.MaskedImageAccess;
import org.icepdf.core.util.Library;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.*;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.*;

/**
 * An image with an explicit stencil {@code /Mask} finer than itself, the shape of a scanned page: a low
 * resolution colour layer and a sharp one bit mask.  The paint path keeps the two apart
 * ({@link MaskedImage}) and combines them only where it draws; whatever it draws must be what drawing the
 * combined image would have drawn.
 */
public class MaskedImagePainterTest {

    private final Library library = new Library();

    /**
     * A 2x2 DeviceRGB base (red, green / blue, yellow) under a 4x4 stencil whose top-left and bottom-right
     * quarters are painted: one image pixel each, with a hole punched in the top-left one.
     */
    private ImageStream maskedImage() {
        DictionaryEntries maskEntries = new DictionaryEntries();
        maskEntries.put(new Name("Type"), new Name("XObject"));
        maskEntries.put(new Name("Subtype"), new Name("Image"));
        maskEntries.put(new Name("Width"), 4);
        maskEntries.put(new Name("Height"), 4);
        maskEntries.put(new Name("ImageMask"), true);
        maskEntries.put(new Name("BitsPerComponent"), 1);
        // a stencil sample of 0 paints: rows of 4 bits, top two paint the left half, bottom two the right
        byte[] maskRows = {(byte) 0x3F, (byte) 0x7F, (byte) 0xCF, (byte) 0xCF};
        ImageStream mask = new ImageStream(library, maskEntries, maskRows);

        DictionaryEntries entries = new DictionaryEntries();
        entries.put(new Name("Type"), new Name("XObject"));
        entries.put(new Name("Subtype"), new Name("Image"));
        entries.put(new Name("Width"), 2);
        entries.put(new Name("Height"), 2);
        entries.put(new Name("ColorSpace"), new Name("DeviceRGB"));
        entries.put(new Name("BitsPerComponent"), 8);
        entries.put(new Name("Mask"), mask);
        byte[] rgb = {
                (byte) 255, 0, 0, 0, (byte) 255, 0,
                0, 0, (byte) 255, (byte) 255, (byte) 255, 0};
        return new ImageStream(library, entries, rgb);
    }

    private Resources resources() {
        return new Resources(library, new DictionaryEntries());
    }

    private static GraphicsState graphicsState() {
        return new GraphicsState(new Shapes());
    }

    /** A grey page with the image drawn over the whole of it, the image's unit square scaled to fit. */
    private static BufferedImage page(int size, java.util.function.Consumer<Graphics2D> paint) {
        BufferedImage page = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = page.createGraphics();
        g.setColor(Color.GRAY);
        g.fillRect(0, 0, size, size);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.scale(size, size);
        paint.accept(g);
        g.dispose();
        return page;
    }

    @DisplayName("the paint path keeps an explicit stencil mask apart; getImage still applies it")
    @Test
    public void paintImageDefersTheMask() {
        BufferedImage paint = maskedImage().getPaintImage(graphicsState(), resources());
        assertTrue(paint instanceof MaskedImage, "paint image keeps the mask apart");
        MaskedImage masked = (MaskedImage) paint;
        assertEquals(2, masked.getBase().getWidth(), "base at its own resolution");
        assertEquals(4, masked.getStencil().getWidth(), "stencil at its own resolution");

        BufferedImage image = maskedImage().getImage(graphicsState(), resources());
        assertFalse(image instanceof MaskedImage, "getImage hands out pixels, mask applied");
        assertEquals(4, image.getWidth(), "combined at the finer resolution, as before");
    }

    @DisplayName("painting the parts apart draws what drawing the combined image draws")
    @Test
    public void paintMatchesComposite() {
        MaskedImage masked = (MaskedImage) maskedImage().getPaintImage(graphicsState(), resources());
        BufferedImage combined = masked.composite();
        // whole device pixels per stencil pixel, where nearest neighbour is exact either way
        for (int size : new int[]{40, 64}) {
            BufferedImage expected = page(size, g -> g.drawImage(combined, 0, 0, 1, 1, null));
            BufferedImage actual = page(size, g -> MaskedImagePainter.paint(g, masked, 0, 0, 1, 1));
            assertPixelsEqual(expected, actual, "size " + size);
        }
        // spot check the picture itself at 40: the stencil's hole shows the page, the base's colours show
        BufferedImage actual = page(40, g -> MaskedImagePainter.paint(g, masked, 0, 0, 1, 1));
        assertEquals(0xFF0000, actual.getRGB(5, 5) & 0xFFFFFF, "red quarter painted");
        assertEquals(Color.GRAY.getRGB(), actual.getRGB(15, 15), "stencil hole leaves the page");
        assertEquals(0xFFFF00, actual.getRGB(35, 35) & 0xFFFFFF, "yellow quarter painted");
        assertEquals(Color.GRAY.getRGB(), actual.getRGB(5, 35), "masked quarter leaves the page");
    }

    @DisplayName("a paint clipped to tiles joins up with no seams")
    @Test
    public void tiledPaintHasNoSeams() {
        MaskedImage masked = (MaskedImage) maskedImage().getPaintImage(graphicsState(), resources());
        BufferedImage whole = page(64, g -> MaskedImagePainter.paint(g, masked, 0, 0, 1, 1));
        BufferedImage tiled = new BufferedImage(64, 64, BufferedImage.TYPE_INT_RGB);
        for (int ty = 0; ty < 64; ty += 13) {
            for (int tx = 0; tx < 64; tx += 13) {
                Graphics2D g = tiled.createGraphics();
                g.setColor(Color.GRAY);
                g.fillRect(tx, ty, 13, 13);
                g.setClip(tx, ty, 13, 13);
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                        RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
                g.scale(64, 64);
                MaskedImagePainter.paint(g, masked, 0, 0, 1, 1);
                g.dispose();
            }
        }
        assertPixelsEqual(whole, tiled, "tiled");
    }

    @DisplayName("a large stencil minified is drawn from its mip levels, anti-aliased")
    @Test
    public void minifiedStencilIsFiltered() {
        // one bit stencil, a one pixel vertical stroke every 4 columns: point sampling at 1/4 would miss them
        BufferedImage stencil = new BufferedImage(256, 256, BufferedImage.TYPE_BYTE_BINARY);
        for (int y = 0; y < 256; y++) {
            for (int x = 0; x < 256; x++) {
                stencil.setRGB(x, y, x % 4 == 1 ? 0xFF000000 : 0xFFFFFFFF);
            }
        }
        BufferedImage base = new BufferedImage(16, 16, BufferedImage.TYPE_INT_RGB);
        Graphics2D bg = base.createGraphics();
        bg.setColor(Color.BLACK);
        bg.fillRect(0, 0, 16, 16);
        bg.dispose();
        MaskedImage masked = MaskedImageAccess.create(base, stencil);
        BufferedImage page = new BufferedImage(64, 64, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = page.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 64, 64);
        g.scale(64, 64);
        MaskedImagePainter.paint(g, masked, 0, 0, 1, 1);
        g.dispose();
        // every column carries a quarter of a stroke: an even grey, not white with the odd black column
        for (int x = 0; x < 64; x++) {
            int grey = page.getRGB(x, 32) & 255;
            assertTrue(grey > 150 && grey < 230, "column " + x + " grey " + grey);
        }
    }

    private static void assertPixelsEqual(BufferedImage expected, BufferedImage actual, String what) {
        int bad = 0;
        for (int y = 0; y < expected.getHeight(); y++) {
            for (int x = 0; x < expected.getWidth(); x++) {
                if (expected.getRGB(x, y) != actual.getRGB(x, y)) {
                    bad++;
                }
            }
        }
        assertEquals(0, bad, what + ": pixels differing");
    }
}
