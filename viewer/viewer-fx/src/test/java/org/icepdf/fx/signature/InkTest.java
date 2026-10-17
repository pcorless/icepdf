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
package org.icepdf.fx.signature;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.*;

class InkTest {

    @DisplayName("strokes survive the text form; bad pairs are skipped")
    @Test
    void roundTrip() {
        Ink ink = new Ink();
        ink.parse("10,20 30.5,40\n\n5,5 junk 7,9,\n100,1");
        assertEquals(3, ink.strokes().size());
        Ink copy = new Ink();
        copy.parse(ink.format());
        assertEquals(ink.format(), copy.format());
        assertArrayEquals(new double[]{30.5, 40}, copy.strokes().get(0).get(1));
    }

    @DisplayName("the image is cropped to the ink, on a transparent background, in the ink's colour")
    @Test
    void image() {
        Ink ink = new Ink();
        assertNull(ink.toImage(2), "nothing drawn");
        ink.parse("10,10 110,10");
        ink.setColor(Color.RED);
        ink.setWidth(4);
        BufferedImage image = ink.toImage(2);
        // 100 units long plus the round caps, 4 units thick, at 2 px per unit.
        assertEquals(208, image.getWidth(), 1);
        assertEquals(8, image.getHeight(), 1);
        int middle = image.getRGB(image.getWidth() / 2, image.getHeight() / 2);
        assertEquals(0xFFFF0000, middle);
        assertEquals(0, image.getRGB(0, 0) >>> 24, "corner outside the round cap is transparent");
    }

    @DisplayName("a single tap is a dot, not nothing")
    @Test
    void dot() {
        Ink ink = new Ink();
        ink.parse("50,50");
        assertFalse(ink.isEmpty());
        assertNotNull(ink.toImage(1));
    }
}
