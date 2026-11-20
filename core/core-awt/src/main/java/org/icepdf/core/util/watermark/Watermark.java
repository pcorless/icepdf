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
package org.icepdf.core.util.watermark;

import java.awt.Color;
import java.util.Objects;

/**
 * What a watermark looks like: its text, font, size, colour, opacity and position. Immutable; the
 * {@code with...} methods return a changed copy.
 * <pre>
 * Watermark draft = Watermark.of("DRAFT").withPosition(Watermark.Position.OVERLAY);
 * Watermarks.apply(document, draft);
 * </pre>
 *
 * @since 8.1
 */
public final class Watermark {

    /** Where the watermark sits on the page, as the page is shown (after its rotation). */
    public enum Position {
        /** Centred along the top edge. */
        HEADER,
        /** Centred along the bottom edge. */
        FOOTER,
        /** Across the page from the bottom-left corner to the top-right, centred. */
        OVERLAY
    }

    /**
     * The fonts a watermark can use: standard 14 fonts, which every viewer has, so nothing is
     * embedded in the document.
     */
    public enum StandardFont {
        HELVETICA("Helvetica"),
        HELVETICA_BOLD("Helvetica-Bold"),
        TIMES_ROMAN("Times-Roman"),
        TIMES_BOLD("Times-Bold"),
        COURIER("Courier"),
        COURIER_BOLD("Courier-Bold");

        private final String baseFont;

        StandardFont(String baseFont) {
            this.baseFont = baseFont;
        }

        /** The PDF base font name, such as {@code Helvetica-Bold}. */
        public String getBaseFont() {
            return baseFont;
        }
    }

    /** Font size used when none is given: readable along an edge. */
    public static final float DEFAULT_FONT_SIZE = 12f;

    private final String text;
    private final StandardFont font;
    private final float fontSize;
    private final Color color;
    private final float opacity;
    private final Position position;

    private Watermark(String text, StandardFont font, float fontSize, Color color, float opacity,
                      Position position) {
        this.text = text;
        this.font = font;
        this.fontSize = fontSize;
        this.color = color;
        this.opacity = opacity;
        this.position = position;
    }

    /**
     * A grey Helvetica watermark in the footer, at 12 points and half opacity.
     *
     * @param text the watermark's text, one line; line breaks become spaces
     * @throws IllegalArgumentException when the text is empty
     */
    public static Watermark of(String text) {
        return new Watermark(clean(text), StandardFont.HELVETICA, DEFAULT_FONT_SIZE, Color.GRAY, 0.5f,
                Position.FOOTER);
    }

    public Watermark withText(String text) {
        return new Watermark(clean(text), font, fontSize, color, opacity, position);
    }

    public Watermark withFont(StandardFont font) {
        return new Watermark(text, Objects.requireNonNull(font, "font"), fontSize, color, opacity, position);
    }

    /**
     * @param fontSize in points; for {@link Position#OVERLAY}, {@code 0} fits the text to the page's
     *                 diagonal
     */
    public Watermark withFontSize(float fontSize) {
        if (fontSize < 0 || Float.isNaN(fontSize)) {
            throw new IllegalArgumentException("font size must be 0 or more: " + fontSize);
        }
        return new Watermark(text, font, fontSize, color, opacity, position);
    }

    public Watermark withColor(Color color) {
        return new Watermark(text, font, fontSize, Objects.requireNonNull(color, "color"), opacity, position);
    }

    /** @param opacity from 0 (invisible) to 1 (opaque) */
    public Watermark withOpacity(float opacity) {
        if (!(opacity >= 0 && opacity <= 1)) {
            throw new IllegalArgumentException("opacity must be between 0 and 1: " + opacity);
        }
        return new Watermark(text, font, fontSize, color, opacity, position);
    }

    public Watermark withPosition(Position position) {
        return new Watermark(text, font, fontSize, color, opacity, Objects.requireNonNull(position, "position"));
    }

    public String getText() {
        return text;
    }

    public StandardFont getFont() {
        return font;
    }

    public float getFontSize() {
        return fontSize;
    }

    public Color getColor() {
        return color;
    }

    public float getOpacity() {
        return opacity;
    }

    public Position getPosition() {
        return position;
    }

    private static String clean(String text) {
        String line = text == null ? "" : text.replaceAll("\\s+", " ").trim();
        if (line.isEmpty()) {
            throw new IllegalArgumentException("a watermark needs some text");
        }
        return line;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Watermark)) {
            return false;
        }
        Watermark w = (Watermark) o;
        return Float.compare(w.fontSize, fontSize) == 0 && Float.compare(w.opacity, opacity) == 0
                && text.equals(w.text) && font == w.font && color.equals(w.color) && position == w.position;
    }

    @Override
    public int hashCode() {
        return Objects.hash(text, font, fontSize, color, opacity, position);
    }

    @Override
    public String toString() {
        return "Watermark{" + position + ", " + font + " " + fontSize + "pt}";
    }
}
