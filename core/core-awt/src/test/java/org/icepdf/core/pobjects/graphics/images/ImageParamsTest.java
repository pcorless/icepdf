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

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests the decoded-size arithmetic in {@link ImageParams}, which presizes the buffer every image stream is
 * decoded into.
 */
public class ImageParamsTest {

    private static ImageParams params(int width, int height, int bitsPerComponent) {
        DictionaryEntries entries = new DictionaryEntries();
        entries.put(new Name("Width"), width);
        entries.put(new Name("Height"), height);
        entries.put(new Name("BitsPerComponent"), bitsPerComponent);
        entries.put(new Name("ColorSpace"), new Name("DeviceGray"));
        return new ImageParams(new Library(), entries, null);
    }

    @DisplayName("data length - a small image is width x height x components x bpc / 8")
    @Test
    public void smallImage() {
        assertEquals(100 * 50, params(100, 50, 8).getDataLength());
        assertEquals(100 * 50 / 8, params(100, 50, 1).getDataLength());
    }

    @DisplayName("data length - a large print image doesn't overflow before the divide by 8")
    @Test
    public void largeImageDoesNotOverflow() {
        // 18480 x 16734 x 8 bits is 2.47e9 before the divide: int arithmetic wrapped it negative, the presize
        // was ignored, and the decode buffer grew 3MB at a time to 1.2GB (602695.pdf, 32s for one page).
        assertEquals(18480L * 16734, params(18480, 16734, 8).getDataLength());
    }

    @DisplayName("data length - a size no array can hold is capped, not wrapped")
    @Test
    public void oversizedImageIsCapped() {
        assertEquals(Integer.MAX_VALUE - 8, params(100000, 100000, 16).getDataLength());
    }
}
