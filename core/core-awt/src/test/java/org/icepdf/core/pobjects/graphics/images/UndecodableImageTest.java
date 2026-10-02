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
import org.icepdf.core.pobjects.graphics.GraphicsState;
import org.icepdf.core.pobjects.graphics.Shapes;
import org.icepdf.core.util.Library;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.*;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * When a codec can't decode an image, the stream still holds compressed bytes; reading those as samples
 * painted noise, and for an image mask a solid block of the fill colour over the page.  Such an image is
 * skipped; the raw fallback is kept for plain sample data only.
 */
public class UndecodableImageTest {

    private static BufferedImage getImage(String filter, boolean imageMask, byte[] data) {
        DictionaryEntries entries = new DictionaryEntries();
        entries.put(new Name("Type"), new Name("XObject"));
        entries.put(new Name("Subtype"), new Name("Image"));
        entries.put(new Name("Width"), 8);
        entries.put(new Name("Height"), 8);
        entries.put(new Name("BitsPerComponent"), imageMask ? 1 : 8);
        if (imageMask) {
            entries.put(new Name("ImageMask"), true);
        } else {
            entries.put(new Name("ColorSpace"), new Name("DeviceGray"));
        }
        if (filter != null) {
            entries.put(new Name("Filter"), new Name(filter));
        }
        GraphicsState graphicsState = new GraphicsState(new Shapes());
        graphicsState.setFillColor(Color.BLACK);
        return new ImageStream(new Library(), entries, data).getImage(graphicsState, null);
    }

    private static byte[] garbage(int length) {
        byte[] data = new byte[length];
        for (int i = 0; i < length; i++) {
            data[i] = (byte) (i * 37 + 11);
        }
        return data;
    }

    @DisplayName("an image mask JBIG2 can't decode is skipped, not painted as a solid block")
    @Test
    public void undecodableJbig2MaskIsSkipped() {
        assertNull(getImage("JBIG2Decode", true, garbage(64)));
    }

    @DisplayName("a JPEG that can't be decoded is skipped, not painted as noise")
    @Test
    public void undecodableJpegIsSkipped() {
        assertNull(getImage("DCTDecode", false, garbage(64)));
    }

    @DisplayName("plain sample data still decodes")
    @Test
    public void rawSamplesDecode() {
        BufferedImage image = getImage(null, false, new byte[64]);
        assertNotNull(image);
        assertEquals(8, image.getWidth());
    }
}
