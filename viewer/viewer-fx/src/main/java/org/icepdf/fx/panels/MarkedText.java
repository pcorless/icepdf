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
package org.icepdf.fx.panels;

import org.icepdf.core.pobjects.Page;
import org.icepdf.core.pobjects.annotations.TextMarkupAnnotation;
import org.icepdf.core.pobjects.annotations.utils.QuadPoints;
import org.icepdf.core.pobjects.graphics.text.GlyphText;
import org.icepdf.core.pobjects.graphics.text.OffsetRange;
import org.icepdf.core.pobjects.graphics.text.TextSequence;

import java.awt.Shape;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;

/**
 * The page text a highlight, underline, strike-out or squiggly marks: the glyphs whose centres lie
 * in its /QuadPoints.  A comment that is only a highlight has no text of its own; this is what it
 * is about.
 */
public final class MarkedText {

    private MarkedText() {
    }

    /**
     * The text under a text markup annotation on {@code page}, runs on separate lines joined by
     * "…"; "" when it has no quads or there is no text under them.  Reads the page's text, so call
     * it off the FX thread.
     */
    public static String of(Page page, TextMarkupAnnotation markup) throws InterruptedException {
        Shape[] quads = QuadPoints.parseQuadPoints(markup.getLibrary(), markup.getEntries());
        if (quads == null || quads.length == 0 || page == null || page.getViewText() == null) return "";
        return of(page.getViewText().getTextSequence(), quads);
    }

    /** The text of {@code sequence} under {@code quads} (page space). */
    static String of(TextSequence sequence, Shape[] quads) {
        Rectangle2D[] boxes = new Rectangle2D[quads.length];
        Rectangle2D all = null;
        for (int i = 0; i < quads.length; i++) {
            boxes[i] = quads[i].getBounds2D();
            all = all == null ? boxes[i] : all.createUnion(boxes[i]);
        }
        List<String> runs = new ArrayList<>();
        int start = -1, end = -1;
        for (int offset = 0, length = sequence.length(); offset < length; offset++) {
            GlyphText glyph = sequence.glyphAt(offset);
            // a line break (no glyph) joins a run only if the run carries on after it.
            if (glyph == null) continue;
            Rectangle2D.Double b = glyph.getBounds();
            boolean in = all.contains(b.getCenterX(), b.getCenterY()) && inside(boxes, b.getCenterX(), b.getCenterY());
            if (in) {
                if (start < 0) start = offset;
                end = offset + 1;
            } else if (start >= 0) {
                runs.add(sequence.extractText(OffsetRange.of(start, end)).strip());
                start = -1;
            }
        }
        if (start >= 0) runs.add(sequence.extractText(OffsetRange.of(start, end)).strip());
        runs.removeIf(String::isEmpty);
        return String.join(" … ", runs);
    }

    /**
     * Inside a quad's bounds.  The quads themselves aren't tested: many writers order the corners
     * as a "Z" (the order Acrobat uses), which as a polygon is a bow tie whose centre is outside.
     */
    private static boolean inside(Rectangle2D[] boxes, double x, double y) {
        for (Rectangle2D box : boxes) {
            if (box.contains(x, y)) return true;
        }
        return false;
    }
}
