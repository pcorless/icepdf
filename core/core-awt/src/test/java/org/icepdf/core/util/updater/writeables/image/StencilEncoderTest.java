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
package org.icepdf.core.util.updater.writeables.image;

import org.icepdf.core.pobjects.DictionaryEntries;
import org.icepdf.core.pobjects.Name;
import org.icepdf.core.pobjects.graphics.images.ImageStream;
import org.icepdf.core.util.Library;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.zip.InflaterInputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The stencil encoder writes 0 where a stencil paints and 1 where it leaves the page alone, whichever form the
 * decoded stencil takes (GH-697).
 */
public class StencilEncoderTest {

    private static int[] bits(ImageStream encoded, int width, int height) throws Exception {
        ByteArrayOutputStream inflated = new ByteArrayOutputStream();
        try (InflaterInputStream in = new InflaterInputStream(new ByteArrayInputStream(encoded.getRawBytes()))) {
            in.transferTo(inflated);
        }
        byte[] packed = inflated.toByteArray();
        int stride = (width + 7) / 8;
        int[] bits = new int[width * height];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                bits[y * width + x] = (packed[y * stride + x / 8] >> (7 - x % 8)) & 1;
            }
        }
        return bits;
    }

    private static ImageStream stream(BufferedImage decoded) {
        DictionaryEntries entries = new DictionaryEntries();
        entries.put(new Name("Width"), decoded.getWidth());
        entries.put(new Name("Height"), decoded.getHeight());
        entries.put(new Name("ImageMask"), true);
        ImageStream stream = new ImageStream(new Library(), entries, ByteBuffer.wrap(new byte[0]));
        stream.setDecodedImage(decoded);
        return stream;
    }

    @DisplayName("an image's /Mask decoded black and white, with no alpha: black paints, white doesn't")
    @Test
    public void maskWithoutAlpha() throws Exception {
        // as a JBIG2 /Mask decodes: read by alpha, every pixel was "paint" and the mask hid nothing
        BufferedImage mask = new BufferedImage(16, 2, BufferedImage.TYPE_BYTE_BINARY);
        Graphics2D g = mask.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 16, 2);
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, 4, 2);
        g.dispose();
        assertFalse(mask.getColorModel().hasAlpha());
        int[] bits = bits(new StencilEncoder(stream(mask)).encode(), 16, 2);
        assertEquals(0, bits[0], "black paints");
        assertEquals(0, bits[3]);
        assertEquals(1, bits[4], "white leaves the page alone");
        assertEquals(1, bits[15]);
    }

    @DisplayName("a stencil decoded for painting: opaque paints, transparent doesn't, whatever the fill colour")
    @Test
    public void paintedStencil() throws Exception {
        BufferedImage painted = new BufferedImage(8, 1, BufferedImage.TYPE_INT_ARGB);
        // painted in white: the colour can't be what decides
        for (int x = 0; x < 4; x++) {
            painted.setRGB(x, 0, 0xFFFFFFFF);
        }
        int[] bits = bits(new StencilEncoder(stream(painted)).encode(), 8, 1);
        assertEquals(0, bits[0]);
        assertEquals(1, bits[5]);
    }
}
