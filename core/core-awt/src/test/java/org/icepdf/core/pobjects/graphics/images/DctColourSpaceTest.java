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

import org.icepdf.core.pobjects.DictionaryEntries;
import org.icepdf.core.pobjects.Name;
import org.icepdf.core.pobjects.Reference;
import org.icepdf.core.pobjects.Resources;
import org.icepdf.core.pobjects.graphics.GraphicsState;
import org.icepdf.core.pobjects.graphics.PColorSpace;
import org.icepdf.core.pobjects.graphics.Shapes;
import org.icepdf.core.util.Library;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * GH-617: DCTDecode images drawn through their colour space -- a YCbCr Lab JPEG is Lab after the inverse
 * transform, not sRGB -- and grey images that read back (getRGB, as soft masks are read) as the samples they draw.
 */
public class DctColourSpaceTest {

    /** A JFIF (YCbCr) JPEG of flat 16-pixel blocks, so the loss stays within a couple of levels. */
    private static byte[] jpeg(int imageType, int... rgbBlocks) throws IOException {
        BufferedImage source = new BufferedImage(16 * rgbBlocks.length, 16, imageType);
        Graphics2D g = source.createGraphics();
        for (int i = 0; i < rgbBlocks.length; i++) {
            g.setColor(new Color(rgbBlocks[i]));
            g.fillRect(i * 16, 0, 16, 16);
        }
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(source, "jpeg", out));
        return out.toByteArray();
    }

    private static DictionaryEntries image(int width, Object colourSpace) {
        DictionaryEntries entries = new DictionaryEntries();
        entries.put(new Name("Type"), new Name("XObject"));
        entries.put(new Name("Subtype"), new Name("Image"));
        entries.put(new Name("Width"), width);
        entries.put(new Name("Height"), 16);
        entries.put(new Name("BitsPerComponent"), 8);
        entries.put(new Name("ColorSpace"), colourSpace);
        entries.put(new Name("Filter"), new Name("DCTDecode"));
        return entries;
    }

    private static BufferedImage decode(Library library, DictionaryEntries entries, byte[] data) {
        GraphicsState graphicsState = new GraphicsState(new Shapes());
        graphicsState.setFillColor(Color.BLACK);
        BufferedImage image = new ImageStream(library, entries, data)
                .getImage(graphicsState, new Resources(library, new DictionaryEntries()));
        assertNotNull(image);
        return image;
    }

    private static void assertNear(int expected, int actual, int tolerance, String what) {
        for (int shift = 0; shift <= 16; shift += 8) {
            int e = expected >> shift & 0xff, a = actual >> shift & 0xff;
            assertTrue(Math.abs(e - a) <= tolerance, what + ": expected " + Integer.toHexString(expected & 0xffffff)
                    + " but was " + Integer.toHexString(actual & 0xffffff));
        }
    }

    @Test
    public void yCbCrLabJpegIsLab() throws IOException {
        Library library = new Library();
        DictionaryEntries labEntries = new DictionaryEntries();
        labEntries.put(new Name("WhitePoint"), Arrays.asList(0.9642f, 1f, 0.8249f));
        labEntries.put(new Name("Range"), Arrays.asList(-128f, 127f, -128f, 127f));
        List<Object> lab = Arrays.asList(new Name("Lab"), labEntries);
        PColorSpace space = new Resources(library, new DictionaryEntries()).getColorSpace(lab);
        // L* a* b* samples: white, a strong a* (magenta-red), a strong b* (yellow)
        int[] samples = {0xff8080, 0x80d080, 0xc080e0};
        BufferedImage image = decode(library, image(16 * samples.length, lab), jpeg(BufferedImage.TYPE_INT_RGB, samples));
        float[] range = space.getDefaultDecode(8);
        for (int i = 0; i < samples.length; i++) {
            float[] values = new float[3];
            for (int c = 0; c < 3; c++) {
                int sample = samples[i] >> (16 - 8 * c) & 0xff;
                values[c] = range[2 * c] + (range[2 * c + 1] - range[2 * c]) * sample / 255f;
            }
            // as sRGB the samples were drawn as themselves: grey, green, lilac
            assertNear(space.getColor(values).getRGB(), image.getRGB(i * 16 + 8, 8), 6, "block " + i);
        }
    }

    @Test
    public void grayJpegReadsBackAsItsSamples() throws IOException {
        Library library = new Library();
        BufferedImage image = decode(library, image(48, new Name("DeviceGray")),
                jpeg(BufferedImage.TYPE_BYTE_GRAY, 0x404040, 0x808080, 0xc0c0c0));
        // CS_GRAY read 64 back as 137
        assertNear(0x404040, image.getRGB(8, 8), 2, "64");
        assertNear(0x808080, image.getRGB(24, 8), 2, "128");
        assertNear(0xc0c0c0, image.getRGB(40, 8), 2, "192");
    }

    /** A DCT soft mask is read through getRGB; a 25% mask was applied as about 54%. */
    @Test
    public void graySoftMaskIsAppliedAsItsSamples() throws IOException {
        Library library = new Library();
        DictionaryEntries mask = image(16, new Name("DeviceGray"));
        ImageStream maskStream = new ImageStream(library, mask, jpeg(BufferedImage.TYPE_BYTE_GRAY, 0x404040));
        Reference maskReference = new Reference(901, 0);
        library.addObject(maskStream, maskReference);
        DictionaryEntries base = new DictionaryEntries();
        base.put(new Name("Type"), new Name("XObject"));
        base.put(new Name("Subtype"), new Name("Image"));
        base.put(new Name("Width"), 16);
        base.put(new Name("Height"), 16);
        base.put(new Name("BitsPerComponent"), 8);
        base.put(new Name("ColorSpace"), new Name("DeviceRGB"));
        base.put(new Name("SMask"), maskReference);
        byte[] red = new byte[16 * 16 * 3];
        for (int i = 0; i < red.length; i += 3) {
            red[i] = (byte) 0xff;
        }
        BufferedImage image = decode(library, base, red);
        int alpha = image.getRGB(8, 8) >>> 24;
        // the library holds the mask weakly; keep it reachable until the image is decoded
        assertNotNull(maskStream);
        assertTrue(Math.abs(alpha - 64) <= 2, "alpha " + alpha);
    }
}
