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
package org.icepdf.core.pobjects.graphics;

import org.icepdf.core.pobjects.DictionaryEntries;
import org.icepdf.core.pobjects.Name;
import org.icepdf.core.util.Library;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CalGray (8.6.5.2): A^Gamma is the luminance Y, shown as an sRGB grey.
 */
public class CalGrayTest {

    private static CalGray calGray(Object gamma) {
        DictionaryEntries entries = new DictionaryEntries();
        // D50, whose Z (0.8249) is well away from its Y
        entries.put(new Name("WhitePoint"), Arrays.asList(0.9642f, 1f, 0.8249f));
        entries.put(new Name("Gamma"), gamma);
        return new CalGray(new Library(), entries);
    }

    private static void assertGrey(int expected, int argb) {
        int grey = argb & 0xff;
        assertTrue(Math.abs(grey - expected) <= 2, "expected grey " + expected + " but was " + grey);
    }

    @Test
    public void greyIsTheLuminance() {
        // 0.5^2.2 = 0.2176 linear is sRGB 0.502; the Z it showed made it about 118
        assertGrey(128, calGray(2.2f).getColor(new float[]{0.5f}).getRGB());
        assertGrey(255, calGray(2.2f).getColor(new float[]{1f}).getRGB());
        assertGrey(0, calGray(2.2f).getColor(new float[]{0f}).getRGB());
    }

    @Test
    public void anIntegerGammaIsRead() {
        // /Gamma 2: 0.5^2 = 0.25 linear is sRGB 0.537; ignored, it was gamma 1 and about 188
        assertGrey(137, calGray(2).getColor(new float[]{0.5f}).getRGB());
    }
}
