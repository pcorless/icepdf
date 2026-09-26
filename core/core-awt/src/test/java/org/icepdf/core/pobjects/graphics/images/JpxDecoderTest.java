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
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class JpxDecoderTest {

    @DisplayName("a JPEG 2000 image within the size limit is decoded at full size")
    @Test
    public void normalImageIsNotScaled() throws IOException {
        BufferedImage image = DctDecoderTest.decode(400, 16, "jpeg2000", "JPXDecode", true);
        assertNotNull(image);
        assertEquals(400, image.getWidth());
        assertEquals(16, image.getHeight());
    }

    @DisplayName("a really big JPEG 2000 image is subsampled while decoding, to about the preferred size")
    @Test
    public void reallyBigImageIsSubsampled() throws IOException {
        BufferedImage image = DctDecoderTest.decode(12000, 16, "jpeg2000", "JPXDecode", true);
        assertNotNull(image);
        assertEquals(12000 / 8, image.getWidth());
        assertEquals(2, image.getHeight());
        assertTrue((image.getRGB(10, 0) & 0xFF) < 64);
        assertTrue((image.getRGB(image.getWidth() - 10, 0) & 0xFF) > 192);
    }
}
