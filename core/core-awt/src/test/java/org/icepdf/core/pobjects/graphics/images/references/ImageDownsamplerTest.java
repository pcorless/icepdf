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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests the zoom-aware minification of GH-608.
 * <p>
 * The anchor is a scanned page: one-bit samples whose strokes are a single pixel wide.  Java2D
 * point-samples when it shrinks an image, so at a quarter size three of every four columns are
 * skipped and a one-pixel vertical stroke survives only when it happens to land on a sampled
 * column.  Drawn from a box-filtered level, every stroke leaves a grey trace however small the
 * page is drawn.
 */
public class ImageDownsamplerTest {

    /**
     * A white one-bit page with a black one-pixel vertical stroke every {@code pitch} columns,
     * starting at column 1 so a stride-4 point sample starting at 0 misses them all.
     */
    private static BufferedImage strokes(int w, int h, int pitch) {
        byte[] map = {(byte) 0, (byte) 255};
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_BINARY,
                new IndexColorModel(1, 2, map, map, map));
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                image.setRGB(x, y, x % pitch == 1 ? 0xFF000000 : 0xFFFFFFFF);
            }
        }
        return image;
    }

    private static BufferedImage paint(BufferedImage image, double scale, boolean downsample) {
        int w = (int) Math.round(image.getWidth() * scale), h = (int) Math.round(image.getHeight() * scale);
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.scale(w, h);
        BufferedImage drawn = image;
        if (downsample) {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            drawn = ImageDownsampler.select(g, image, 0, 0, 1, 1);
        }
        g.drawImage(drawn, 0, 0, 1, 1, null);
        g.dispose();
        return out;
    }

    private static int darkestInRow(BufferedImage image, int y) {
        int darkest = 255;
        for (int x = 0; x < image.getWidth(); x++) {
            darkest = Math.min(darkest, image.getRGB(x, y) & 255);
        }
        return darkest;
    }

    @Test
    @DisplayName("Point sampling at a quarter size loses one-pixel strokes; the mip level keeps them")
    public void quarterSizeKeepsThinStrokes() {
        BufferedImage page = strokes(400, 40, 4);
        // control: the plain draw really does drop the strokes, or the test proves nothing
        assertEquals(255, darkestInRow(paint(page, 0.25, false), 5));
        int darkest = darkestInRow(paint(page, 0.25, true), 5);
        assertTrue(darkest < 220, "strokes should leave a grey trace, darkest was " + darkest);
    }

    @Test
    @DisplayName("No level is made until the image is shrunk by more than half")
    public void mildMinificationUsesSource() {
        BufferedImage page = strokes(400, 40, 4);
        BufferedImage out = new BufferedImage(240, 24, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.scale(240, 24);
        assertSame(page, ImageDownsampler.select(g, page, 0, 0, 1, 1));
        g.dispose();
    }

    @Test
    @DisplayName("The level chosen leaves bilinear a shrink between a half and one")
    public void levelChoice() {
        BufferedImage page = strokes(1600, 1600, 4);
        BufferedImage out = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.scale(100, 100);
        // 1600 -> 100 is 1/16: halve three times to 200, which bilinear halves again reading
        // every pixel; a fourth halving would only repeat that
        BufferedImage level = ImageDownsampler.select(g, page, 0, 0, 1, 1);
        assertEquals(200, level.getWidth());
        // the same level is handed back, not rebuilt
        assertSame(level, ImageDownsampler.select(g, page, 0, 0, 1, 1));
        g.dispose();
    }

    @Test
    @DisplayName("Opaque grey sources get one byte per pixel levels; colour and alpha get ARGB")
    public void levelFormats() {
        assertEquals(BufferedImage.TYPE_BYTE_GRAY, ImageDownsampler.halveSource(strokes(8, 8, 4)).getType());
        BufferedImage rgb = new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB);
        assertEquals(BufferedImage.TYPE_INT_ARGB_PRE, ImageDownsampler.halveSource(rgb).getType());
    }

    @Test
    @DisplayName("Transparent pixels do not bleed their colour into the average")
    public void alphaWeightedAverage() {
        BufferedImage src = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
        // one opaque red pixel, three fully transparent green ones
        src.setRGB(0, 0, 0xFFFF0000);
        src.setRGB(1, 0, 0x0000FF00);
        src.setRGB(0, 1, 0x0000FF00);
        src.setRGB(1, 1, 0x0000FF00);
        int argb = ImageDownsampler.halveSource(src).getRGB(0, 0);
        assertEquals(64, argb >>> 24, 1);
        assertEquals(255, (argb >> 16) & 255, 2);
        assertEquals(0, (argb >> 8) & 255);
    }

    @Test
    @DisplayName("Odd sizes round up so the last row and column are kept")
    public void oddSizes() {
        BufferedImage level = ImageDownsampler.halveSource(strokes(5, 3, 4));
        assertEquals(3, level.getWidth());
        assertEquals(2, level.getHeight());
    }
}
