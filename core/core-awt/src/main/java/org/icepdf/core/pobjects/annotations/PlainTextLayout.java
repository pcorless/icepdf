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
 *
 * The line breaking follows Apache PDFBox's PlainText (org.apache.pdfbox.pdmodel.interactive,
 * Apache License 2.0), rewritten for ICEpdf's font metrics; see NOTICE.
 */
package org.icepdf.core.pobjects.annotations;

import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Plain (not rich) text of a form field broken into paragraphs and lines, and written as text
 * operators.  A paragraph is what a line break in the value starts; a line is what fits the width.
 * Breaks fall where {@link BreakIterator#getLineInstance()} allows, and a word wider than the whole
 * line is split between characters, at least one to a line.
 */
final class PlainTextLayout {

    /** One laid-out line: its text and its advance, trailing white space not counted. */
    static final class Line {
        final String text;
        final float width;

        Line(String text, float width) {
            this.text = text;
            this.width = width;
        }
    }

    private PlainTextLayout() {
    }

    /**
     * Splits a value into paragraphs at its line breaks.  Tabs become spaces, and an empty paragraph
     * keeps a space so it still takes a line, as Acrobat shows it.
     */
    static List<String> paragraphs(String value) {
        if (value == null || value.isEmpty()) {
            return Collections.singletonList("");
        }
        String[] parts = value.replace('\t', ' ').split("\\R", -1);
        int count = parts.length;
        // a value ending in a line break doesn't open another paragraph.
        while (count > 1 && parts[count - 1].isEmpty()) count--;
        List<String> paragraphs = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            paragraphs.add(parts[i].isEmpty() ? " " : parts[i]);
        }
        return paragraphs;
    }

    /**
     * Breaks one paragraph into lines no wider than {@code width} (a single character wider than the
     * width still gets a line of its own).
     */
    static List<Line> lines(String paragraph, FieldFontMetrics metrics, float fontSize, float width) {
        if (width <= 0) {
            return Collections.emptyList();
        }
        List<Line> lines = new ArrayList<>();
        BreakIterator breaks = BreakIterator.getLineInstance();
        breaks.setText(paragraph);
        StringBuilder line = new StringBuilder();
        float lineWidth = 0;
        int start = breaks.first();
        for (int end = breaks.next(); end != BreakIterator.DONE; start = end, end = breaks.next()) {
            String word = paragraph.substring(start, end);
            float wordWidth = metrics.width(word, fontSize);
            // a word ending in white space fits if it does without the white space.
            float fitWidth = wordWidth - trailingSpaceWidth(word, metrics, fontSize);
            if (line.length() > 0 && lineWidth + fitWidth > width) {
                lines.add(line(line.toString(), lineWidth, metrics, fontSize));
                line.setLength(0);
                lineWidth = 0;
            }
            // a word wider than a whole line: as many characters as fit, then the rest.
            while (line.length() == 0 && fitWidth > width && word.length() > 1) {
                int fits = fittingChars(word, metrics, fontSize, width);
                lines.add(line(word.substring(0, fits), metrics.width(word.substring(0, fits), fontSize),
                        metrics, fontSize));
                word = word.substring(fits);
                wordWidth = metrics.width(word, fontSize);
                fitWidth = wordWidth - trailingSpaceWidth(word, metrics, fontSize);
            }
            line.append(word);
            lineWidth += wordWidth;
        }
        if (line.length() > 0 || lines.isEmpty()) {
            lines.add(line(line.toString(), lineWidth, metrics, fontSize));
        }
        return lines;
    }

    private static Line line(String text, float width, FieldFontMetrics metrics, float fontSize) {
        return new Line(text, width - trailingSpaceWidth(text, metrics, fontSize));
    }

    private static float trailingSpaceWidth(String text, FieldFontMetrics metrics, float fontSize) {
        int end = text.length();
        while (end > 0 && Character.isWhitespace(text.charAt(end - 1))) end--;
        return end == text.length() ? 0 : metrics.width(text.substring(end), fontSize);
    }

    /** The longest prefix of {@code word} that fits {@code width}, at least one character. */
    private static int fittingChars(String word, FieldFontMetrics metrics, float fontSize, float width) {
        float advance = 0;
        int i = 0;
        while (i < word.length()) {
            int next = i + Character.charCount(word.codePointAt(i));
            advance += metrics.width(word.substring(i, next), fontSize);
            if (advance > width) break;
            i = next;
        }
        return Math.max(i, Character.charCount(word.codePointAt(0)));
    }

    /**
     * @return the left offset of a line of {@code lineWidth} in a box of {@code width}, for the field's
     * quadding: 0 left, 1 centred, 2 right
     */
    static float alignOffset(int quadding, float width, float lineWidth) {
        if (lineWidth >= width) return 0;
        switch (quadding) {
            case 1:
                return (width - lineWidth) / 2;
            case 2:
                return width - lineWidth;
            default:
                return 0;
        }
    }

    /**
     * Writes lines as text operators, inside a {@code BT ... ET} whose font is already set: the first
     * line's baseline starts at (x, y), and each line below is {@code leading} lower.
     */
    static void write(StringBuilder out, List<Line> lines, FieldFontMetrics metrics, int quadding, float x,
                      float y, float width, float leading) {
        float lastX = 0, lastY = 0;
        boolean first = true;
        for (Line line : lines) {
            float lineX = x + alignOffset(quadding, width, line.width);
            float lineY = first ? y : lastY - leading;
            // Td is relative to the previous line's start.
            out.append(FieldAppearanceGenerator.number(first ? lineX : lineX - lastX)).append(' ')
                    .append(FieldAppearanceGenerator.number(first ? lineY : -leading)).append(" Td ");
            metrics.getEncoder().appendShowString(out, line.text).append(" Tj\n");
            lastX = lineX;
            lastY = lineY;
            first = false;
        }
    }
}
