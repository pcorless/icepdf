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
package org.icepdf.core.pobjects.graphics.commands;

import org.icepdf.core.pobjects.OptionalContents;
import org.icepdf.core.pobjects.graphics.OptionalContentState;
import org.icepdf.core.pobjects.graphics.PaintTimer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

public class DrawDrawCmdTest {

    private static final int WHITE = 0xFFFFFFFF;

    private static OptionalContentState layer(boolean visible) {
        OptionalContentState state = new OptionalContentState();
        state.add(new OptionalContents() {
            public boolean isVisible() {
                return visible;
            }

            public boolean isOCG() {
                return true;
            }

            public void init() {
            }
        });
        return state;
    }

    /** Strokes {@code shape} in black on white and returns the pixel at (x, y). */
    private static int stroke(Shape shape, OptionalContentState state, int x, int y) {
        BufferedImage image = new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 100, 100);
        g.setColor(Color.BLACK);
        g.setClip(0, 0, 100, 100);
        new DrawDrawCmd().paintOperand(g, null, shape, null, new AffineTransform(), state, false,
                new PaintTimer());
        g.dispose();
        return image.getRGB(x, y);
    }

    @DisplayName("a hairline on a hidden layer is not drawn")
    @Test
    public void hiddenHairline() {
        // Hairlines skip the clip test; they used to skip the visibility test with it, as the
        // condition read "visible && inClip || thin".
        Shape hairline = new Line2D.Double(10, 50, 90, 50);
        assertNotEquals(WHITE, stroke(hairline, layer(true), 50, 50));
        assertEquals(WHITE, stroke(hairline, layer(false), 50, 50));
    }

    @DisplayName("a shape on a hidden layer is not drawn")
    @Test
    public void hiddenShape() {
        Shape box = new Rectangle2D.Double(10, 10, 80, 80);
        assertNotEquals(WHITE, stroke(box, layer(true), 10, 50));
        assertEquals(WHITE, stroke(box, layer(false), 10, 50));
    }
}
