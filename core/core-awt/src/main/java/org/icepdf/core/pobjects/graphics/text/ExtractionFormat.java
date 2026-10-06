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
package org.icepdf.core.pobjects.graphics.text;

import org.icepdf.core.util.Defs;

import java.awt.geom.Rectangle2D;

/**
 * Formatting rules for extracted / copied text (see {@link TextSequence#extractText}).
 * <p>
 * Every visual line ends with {@link #LINE_SEPARATOR}.  When {@link #PARAGRAPHS paragraph detection}
 * is enabled a blank line is inserted at a detected paragraph boundary, so extracted text reads as
 * paragraphs rather than one run-on block.  Boundaries are found geometrically from the reading-order
 * line bounds &mdash; a reading-order jump to a new column/region, extra vertical leading between
 * lines, or a first-line indent &mdash; which the page's sort already makes available.
 * <p>
 * System properties (read once):
 * <ul>
 *     <li>{@code org.icepdf.core.views.page.text.paragraphDetection} &mdash; blank line between
 *     paragraphs; default {@code true}.</li>
 *     <li>{@code org.icepdf.core.views.page.text.lineEnding} &mdash; {@code lf} (default) or
 *     {@code crlf} for Windows-native line endings.</li>
 *     <li>{@code org.icepdf.core.views.page.text.reflow} &mdash; join a paragraph's wrapped lines
 *     into one line (space-joined, de-hyphenating soft line-break hyphens) instead of one output line
 *     per visual line; default {@code false}.  Paragraph boundaries still produce a blank line.</li>
 * </ul>
 *
 * @since 7.5
 */
final class ExtractionFormat {

    /** Insert a blank line at detected paragraph boundaries. */
    static final boolean PARAGRAPHS;
    /** Reflow each paragraph's wrapped lines into a single line (space-join + de-hyphenate). */
    static final boolean REFLOW;
    /** Line ending appended after every line ("\n" or "\r\n"). */
    static final String LINE_SEPARATOR;

    /** Vertical whitespace over this multiple of the median line height starts a new paragraph. */
    private static final double PARA_GAP_RATIO = 0.5;
    /** A line indented past its predecessor by this multiple of the median line height starts one. */
    private static final double INDENT_RATIO = 0.75;

    static {
        PARAGRAPHS = Defs.booleanProperty(
                "org.icepdf.core.views.page.text.paragraphDetection", true);
        REFLOW = Defs.booleanProperty(
                "org.icepdf.core.views.page.text.reflow", false);
        String ending = Defs.sysProperty("org.icepdf.core.views.page.text.lineEnding", "lf");
        LINE_SEPARATOR = "crlf".equalsIgnoreCase(ending == null ? "" : ending.trim()) ? "\r\n" : "\n";
    }

    private ExtractionFormat() {
    }

    /** True when {@code line} ends with a letter immediately followed by a hyphen (a line-break
     *  hyphen); such a join is direct (no space) whether or not the hyphen is dropped. */
    static boolean endsWithWordHyphen(CharSequence line) {
        int n = line.length();
        return n >= 2 && line.charAt(n - 1) == '-' && Character.isLetter(line.charAt(n - 2));
    }

    /**
     * When reflowing a line that {@link #endsWithWordHyphen ends with a word hyphen}, decides whether
     * the hyphen is a <em>soft</em> line-break hyphen to drop (de-hyphenate) rather than a real
     * compound hyphen to keep.  Soft when the next line resumes with a lowercase letter whose first
     * token has no internal hyphen ("acti-" + "vates" &rarr; "activates").  A capitalised
     * continuation ("well-\nKnown") or a hyphenated continuation token ("sequence-" + "of-events")
     * signals a real compound, so the hyphen is kept ("sequence-of-events").
     */
    static boolean deHyphenate(CharSequence line, CharSequence nextLine) {
        if (!endsWithWordHyphen(line)) {
            return false;
        }
        int i = 0;
        while (i < nextLine.length() && Character.isWhitespace(nextLine.charAt(i))) i++;
        if (i >= nextLine.length() || !Character.isLowerCase(nextLine.charAt(i))) {
            return false;
        }
        for (int j = i; j < nextLine.length() && !Character.isWhitespace(nextLine.charAt(j)); j++) {
            if (nextLine.charAt(j) == '-') return false;   // continuation token is itself hyphenated
        }
        return true;
    }

    /**
     * Whether a paragraph boundary sits between two consecutive reading-order lines.
     *
     * @param above    the earlier line in reading order
     * @param below    the later line
     * @param medianHeight median line height of the page, the scale for the gap/indent tests
     * @return true if a blank line should separate them
     */
    static boolean isParagraphBreak(LineText above, LineText below, double medianHeight) {
        Rectangle2D.Double a = above.getBounds();
        Rectangle2D.Double b = below.getBounds();
        // reading order moved up the page (a new column, region or page) - always a paragraph break.
        if (b.getCenterY() > a.getCenterY()) {
            return true;
        }
        if (medianHeight <= 0) {
            return false;
        }
        // extra vertical leading between the line boxes.
        double gap = a.getMinY() - b.getMaxY();
        if (gap > medianHeight * PARA_GAP_RATIO) {
            return true;
        }
        // first-line indent: the next line starts noticeably further right than this one.
        return b.getMinX() - a.getMinX() > medianHeight * INDENT_RATIO;
    }
}
