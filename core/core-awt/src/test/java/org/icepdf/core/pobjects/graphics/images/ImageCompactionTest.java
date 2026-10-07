/*
 * Copyright 2026 Patrick Corless
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an "AS
 * IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */
package org.icepdf.core.pobjects.graphics.images;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link ImageUtility#compactImage}: an opaque image with at most 256 colours is repacked as 8-bit
 * indexed (a quarter of the memory) and must draw exactly as before; anything else is left alone.
 */
public class ImageCompactionTest {

    private static BufferedImage image(int type, int w, int h, java.util.function.IntBinaryOperator pixel) {
        BufferedImage image = new BufferedImage(w, h, type);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                image.setRGB(x, y, pixel.applyAsInt(x, y));
            }
        }
        return image;
    }

    private static void assertSamePixels(BufferedImage expected, BufferedImage actual) {
        assertEquals(expected.getWidth(), actual.getWidth());
        assertEquals(expected.getHeight(), actual.getHeight());
        for (int y = 0; y < expected.getHeight(); y++) {
            for (int x = 0; x < expected.getWidth(); x++) {
                assertEquals(expected.getRGB(x, y), actual.getRGB(x, y), "pixel " + x + "," + y);
            }
        }
    }

    @DisplayName("an opaque grey image packs to 8-bit and draws the same")
    @Test
    public void grey() {
        BufferedImage grey = image(BufferedImage.TYPE_INT_ARGB, 64, 8, (x, y) -> 0xFF000000 | (x * 4) * 0x010101);
        BufferedImage compact = ImageUtility.compactImage(grey);
        assertEquals(BufferedImage.TYPE_BYTE_INDEXED, compact.getType());
        assertSamePixels(grey, compact);
    }

    @DisplayName("an opaque image of a few colours (a tinted one-component image) packs to a palette, exactly")
    @Test
    public void fewColours() {
        // a Separation/DeviceN tint: 256 shades of one colour, not grey.
        BufferedImage tint = image(BufferedImage.TYPE_INT_ARGB, 256, 4,
                (x, y) -> 0xFF000000 | (x << 16) | ((x / 2) << 8) | 40);
        BufferedImage compact = ImageUtility.compactImage(tint);
        assertEquals(BufferedImage.TYPE_BYTE_INDEXED, compact.getType());
        assertSamePixels(tint, compact);
    }

    @DisplayName("more than 256 colours stays as it is")
    @Test
    public void manyColours() {
        BufferedImage photo = image(BufferedImage.TYPE_INT_ARGB, 300, 2, (x, y) -> 0xFF000000 | (x * 977));
        assertSame(photo, ImageUtility.compactImage(photo));
    }

    @DisplayName("a fully transparent image stays as it is (it isn't an opaque black one)")
    @Test
    public void fullyTransparent() {
        // all pixels 0x00000000: a scan that starts by assuming "same as 0" never checks alpha, and
        // the image came out as an opaque black box (toxic_mzimmerman, a JBIG2 slide deck).
        BufferedImage clear = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        assertSame(clear, ImageUtility.compactImage(clear));
    }

    @DisplayName("one translucent pixel anywhere keeps the image as it is")
    @Test
    public void oneTranslucentPixel() {
        BufferedImage almost = image(BufferedImage.TYPE_INT_ARGB, 16, 16,
                (x, y) -> x == 15 && y == 15 ? 0x80FFFFFF : 0xFFFFFFFF);
        assertSame(almost, ImageUtility.compactImage(almost));
    }

    @DisplayName("an RGB (no alpha) grey image packs too")
    @Test
    public void rgbGrey() {
        BufferedImage grey = image(BufferedImage.TYPE_INT_RGB, 16, 16, (x, y) -> (x * 16) * 0x010101);
        BufferedImage compact = ImageUtility.compactImage(grey);
        assertEquals(BufferedImage.TYPE_BYTE_INDEXED, compact.getType());
        assertSamePixels(grey, compact);
    }

    @DisplayName("unpacking gives back exactly what was packed: grey as 8-bit grey, colour as RGB")
    @Test
    public void unpack() {
        BufferedImage grey = image(BufferedImage.TYPE_INT_RGB, 16, 4, (x, y) -> (x * 16) * 0x010101);
        BufferedImage greyBack = ImageUtility.unpackIndexed(ImageUtility.compactImage(grey));
        assertEquals(BufferedImage.TYPE_BYTE_GRAY, greyBack.getType(), "DeviceGray stays grey");
        for (int x = 0; x < 16; x++) {
            assertEquals(x * 16, greyBack.getRaster().getSample(x, 0, 0), "grey level, not gamma-shifted");
        }

        BufferedImage tint = image(BufferedImage.TYPE_INT_ARGB, 32, 2, (x, y) -> 0xFF000000 | (x << 18) | 0x40);
        BufferedImage tintBack = ImageUtility.unpackIndexed(ImageUtility.compactImage(tint));
        assertEquals(BufferedImage.TYPE_INT_RGB, tintBack.getType());
        assertSamePixels(tint, tintBack);

        BufferedImage plain = new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB);
        assertSame(plain, ImageUtility.unpackIndexed(plain));
    }
}
