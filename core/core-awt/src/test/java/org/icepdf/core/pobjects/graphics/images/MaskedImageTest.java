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
package org.icepdf.core.pobjects.graphics.images;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The stencil a {@code /Mask} is reduced to must read the decoded mask exactly as
 * {@link ImageUtility#applyExplicitMask(BufferedImage, BufferedImage)} does, whichever way round its
 * palette is: the combined image is still built from the stencil for everything that is not a paint.
 */
public class MaskedImageTest {

    /** 11 wide, so a row does not fill its last byte; paints on a diagonal band. */
    private static boolean paints(int x, int y) {
        return (x + y) % 3 == 0;
    }

    private static BufferedImage binary(byte[] map) {
        BufferedImage mask = new BufferedImage(11, 5, BufferedImage.TYPE_BYTE_BINARY,
                new IndexColorModel(1, 2, map, map, map));
        for (int y = 0; y < 5; y++) {
            for (int x = 0; x < 11; x++) {
                mask.setRGB(x, y, paints(x, y) ? 0xFF000000 : 0xFFFFFFFF);
            }
        }
        return mask;
    }

    private static void assertStencil(BufferedImage mask, String what) {
        BufferedImage stencil = MaskedImage.toStencil(mask);
        assertEquals(BufferedImage.TYPE_BYTE_BINARY, stencil.getType(), what);
        for (int y = 0; y < 5; y++) {
            for (int x = 0; x < 11; x++) {
                assertEquals(paints(x, y) ? 0xFF000000 : 0xFFFFFFFF, stencil.getRGB(x, y),
                        what + " at " + x + "," + y);
            }
        }
    }

    @DisplayName("a one bit mask, black at index 0, copies across")
    @Test
    public void binaryMask() {
        assertStencil(binary(new byte[]{0, (byte) 255}), "black first");
    }

    @DisplayName("a one bit mask, white at index 0, copies across inverted")
    @Test
    public void invertedBinaryMask() {
        assertStencil(binary(new byte[]{(byte) 255, 0}), "white first");
    }

    @DisplayName("any other mask is read pixel by pixel, as applyExplicitMask reads it")
    @Test
    public void argbMask() {
        BufferedImage mask = new BufferedImage(11, 5, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 5; y++) {
            for (int x = 0; x < 11; x++) {
                // transparent black and transparent white leave; for a mask with alpha, opaque white paints
                mask.setRGB(x, y, paints(x, y) ? (x % 2 == 0 ? 0xFF102030 : 0xFFFFFFFF)
                        : (x % 2 == 0 ? 0 : 0x00FFFFFF));
            }
        }
        assertStencil(mask, "argb");
    }

    @DisplayName("composite() is what applyExplicitMask makes of the base and the decoded mask")
    @Test
    public void compositeMatchesExplicitMask() {
        BufferedImage base = new BufferedImage(11, 5, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 5; y++) {
            for (int x = 0; x < 11; x++) {
                base.setRGB(x, y, (x * 20) << 16 | (y * 40) << 8 | 0x80);
            }
        }
        BufferedImage mask = binary(new byte[]{(byte) 255, 0});
        BufferedImage expected = ImageUtility.applyExplicitMask(base, mask);
        BufferedImage actual = new MaskedImage(base, MaskedImage.toStencil(mask)).composite();
        for (int y = 0; y < 5; y++) {
            for (int x = 0; x < 11; x++) {
                assertEquals(expected.getRGB(x, y), actual.getRGB(x, y), x + "," + y);
            }
        }
    }
}
