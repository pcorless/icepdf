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

import org.icepdf.core.pobjects.DictionaryEntries;
import org.icepdf.core.pobjects.Name;
import org.icepdf.core.util.Library;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DctDecoderTest {

    private static BufferedImage decode(int width, int height) throws IOException {
        // left half black, right half white, so a subsampled read can be checked for content, not just size
        BufferedImage source = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = source.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, width, height);
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, width / 2, height);
        g.dispose();
        ByteArrayOutputStream jpeg = new ByteArrayOutputStream();
        ImageIO.write(source, "jpeg", jpeg);

        DictionaryEntries entries = new DictionaryEntries();
        entries.put(new Name("Type"), new Name("XObject"));
        entries.put(new Name("Subtype"), new Name("Image"));
        entries.put(new Name("Width"), width);
        entries.put(new Name("Height"), height);
        entries.put(new Name("BitsPerComponent"), 8);
        entries.put(new Name("ColorSpace"), new Name("DeviceRGB"));
        entries.put(new Name("Filter"), new Name("DCTDecode"));
        ImageStream imageStream = new ImageStream(new Library(), entries, jpeg.toByteArray());
        return new DctDecoder(imageStream, null).decode();
    }

    @DisplayName("an image within the size limit is decoded at full size")
    @Test
    public void normalImageIsNotScaled() throws IOException {
        BufferedImage image = decode(400, 16);
        assertNotNull(image);
        assertEquals(400, image.getWidth());
        assertEquals(16, image.getHeight());
    }

    @DisplayName("a really big image is subsampled while decoding, to about the preferred size")
    @Test
    public void reallyBigImageIsSubsampled() throws IOException {
        // 12000 wide is past the 10000 px limit; read every 8th pixel, 1500 px wide.  The full raster was
        // decoded first and then scaled, which on a 12848 x 27733 page scan is 1GB per image.
        BufferedImage image = decode(12000, 16);
        assertNotNull(image);
        assertEquals(12000 / 8, image.getWidth());
        assertEquals(2, image.getHeight());
        // the content survives: black on the left, white on the right
        assertTrue((image.getRGB(10, 0) & 0xFF) < 64);
        assertTrue((image.getRGB(image.getWidth() - 10, 0) & 0xFF) > 192);
    }

    @DisplayName("the subsampling step keeps the longest edge at or above the preferred size")
    @Test
    public void subsamplingFor() {
        DctDecoder decoder = new DctDecoder(null, null);
        assertEquals(1, decoder.subsamplingFor(9000, 9000));
        assertEquals(18, decoder.subsamplingFor(12848, 27733));
        assertTrue(27733 / decoder.subsamplingFor(12848, 27733) >= AbstractImageDecoder.preferredSize);
        assertEquals(6, decoder.subsamplingFor(10001, 100));
    }
}
