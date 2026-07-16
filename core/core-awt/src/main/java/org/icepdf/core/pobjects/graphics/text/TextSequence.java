/*
 * Copyright 2006-2019 ICEsoft Technologies Canada Corp.
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

import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;

/**
 * A reading-order view over a {@link PageText}.  The page's sorted lines
 * ({@link PageText#getPageLines()}) are flattened into a single canonical character
 * sequence: every glyph's unicode in order, with a synthetic {@code '\n'} between lines.
 * All offsets exposed by this class are character offsets into that canonical string
 * ({@code 0 .. length()}).
 * <br>
 * This is the primitive that maps between three spaces the viewer cares about:
 * <ul>
 *     <li>a page-space point and a caret offset ({@link #caretAt}),</li>
 *     <li>a caret offset and the underlying glyph/word/line ({@link #glyphAt},
 *     {@link #wordRange}, {@link #lineRange}),</li>
 *     <li>a range of offsets and the geometry that highlights it ({@link #rectsFor}).</li>
 * </ul>
 * All geometry is in page space, the same space as {@link GlyphText#getBounds()}.
 * <br>
 * Instances are immutable once built and are cached by {@link PageText}; the cache is
 * invalidated whenever the page re-sorts (optional-content visibility change, re-parse).
 *
 * @see PageText#getTextSequence()
 * @since 7.5
 */
public final class TextSequence {

    // reading-order glyphs and their parallel offset / structure arrays
    private final GlyphText[] glyphs;
    private final int[] glyphCharStart;   // char offset of glyph start
    private final int[] glyphCharEnd;     // char offset of glyph end (exclusive)
    private final int[] glyphLine;        // sorted-line index of glyph
    private final int[] glyphWord;        // word index of glyph

    // per sorted line
    private final LineText[] lines;
    private final int[] lineStart;        // char offset of line start
    private final int[] lineEnd;          // char offset of line end (before the newline)
    private final int[] lineFirstGlyph;   // first glyph index on line
    private final int[] lineLastGlyph;    // last glyph index on line

    // per word
    private final WordText[] words;
    private final int[] wordStart;
    private final int[] wordEnd;
    private final int[] wordLine;         // sorted-line index of each word
    private final IdentityHashMap<WordText, Integer> wordIndex;

    private final String canonical;
    private final IdentityHashMap<GlyphText, Integer> glyphIndex;

    // lazily built normalized search corpus: runs of whitespace collapsed to a single space,
    // with a map back to canonical offsets.  Enables searching over a stable, reading-order string.
    private String searchText;
    private int[] searchToCanonical;

    TextSequence(PageText pageText) {
        List<LineText> pageLines = pageText.getPageLines();

        List<GlyphText> gl = new ArrayList<>(256);
        List<Integer> gcs = new ArrayList<>(256), gce = new ArrayList<>(256);
        List<Integer> gLine = new ArrayList<>(256), gWord = new ArrayList<>(256);
        List<LineText> ll = new ArrayList<>(64);
        List<Integer> ls = new ArrayList<>(64), le = new ArrayList<>(64);
        List<Integer> lFirst = new ArrayList<>(64), lLast = new ArrayList<>(64);
        List<WordText> wl = new ArrayList<>(128);
        List<Integer> ws = new ArrayList<>(128), we = new ArrayList<>(128), wLine = new ArrayList<>(128);

        StringBuilder sb = new StringBuilder(1024);
        int lineIdx = 0;
        for (LineText line : pageLines) {
            if (line.getWords() == null || line.getWords().isEmpty()) continue;
            int lineFirst = gl.size();
            boolean lineHasGlyph = false;
            ll.add(line);
            ls.add(sb.length());
            for (WordText word : line.getWords()) {
                if (word.getGlyphs() == null || word.getGlyphs().isEmpty()) continue;
                int wordIdx = wl.size();
                wl.add(word);
                wLine.add(lineIdx);
                ws.add(sb.length());
                for (GlyphText glyph : word.getGlyphs()) {
                    String u = glyph.getUnicode() == null ? "" : glyph.getUnicode();
                    gl.add(glyph);
                    gcs.add(sb.length());
                    sb.append(u);
                    gce.add(sb.length());
                    gLine.add(lineIdx);
                    gWord.add(wordIdx);
                    lineHasGlyph = true;
                }
                we.add(sb.length());
            }
            if (!lineHasGlyph) {
                // line held only empty words; roll it back so we don't emit a stray newline.
                ll.remove(ll.size() - 1);
                ls.remove(ls.size() - 1);
                continue;
            }
            lFirst.add(lineFirst);
            lLast.add(gl.size() - 1);
            le.add(sb.length());
            sb.append('\n');   // synthetic line break between lines
            lineIdx++;
        }
        // drop trailing newline so the last line has no dangling offset
        if (sb.length() > 0 && sb.charAt(sb.length() - 1) == '\n') sb.setLength(sb.length() - 1);

        canonical = sb.toString();
        glyphs = gl.toArray(new GlyphText[0]);
        glyphCharStart = toIntArray(gcs);
        glyphCharEnd = toIntArray(gce);
        glyphLine = toIntArray(gLine);
        glyphWord = toIntArray(gWord);
        lines = ll.toArray(new LineText[0]);
        lineStart = toIntArray(ls);
        lineEnd = toIntArray(le);
        lineFirstGlyph = toIntArray(lFirst);
        lineLastGlyph = toIntArray(lLast);
        words = wl.toArray(new WordText[0]);
        wordStart = toIntArray(ws);
        wordEnd = toIntArray(we);
        wordLine = toIntArray(wLine);

        glyphIndex = new IdentityHashMap<>(glyphs.length * 2);
        for (int i = 0; i < glyphs.length; i++) glyphIndex.put(glyphs[i], i);
        wordIndex = new IdentityHashMap<>(words.length * 2);
        for (int i = 0; i < words.length; i++) wordIndex.put(words[i], i);
    }

    // ------------------------------------------------------------------
    // size / text
    // ------------------------------------------------------------------

    public int length() {
        return canonical.length();
    }

    public boolean isEmpty() {
        return glyphs.length == 0;
    }

    public int lineCount() {
        return lines.length;
    }

    public int glyphCount() {
        return glyphs.length;
    }

    public CharSequence text() {
        return canonical;
    }

    public String text(int start, int end) {
        int lo = Math.max(0, Math.min(start, end));
        int hi = Math.min(length(), Math.max(start, end));
        return lo >= hi ? "" : canonical.substring(lo, hi);
    }

    public String text(OffsetRange range) {
        return range == null ? "" : text(range.getStart(), range.getEnd());
    }

    public OffsetRange fullRange() {
        return OffsetRange.of(0, length());
    }

    // ------------------------------------------------------------------
    // search corpus
    // ------------------------------------------------------------------

    /**
     * A normalized, reading-order corpus for searching: every run of whitespace (spaces, tabs,
     * newlines, non-breaking spaces) is collapsed to a single space.  Matches found in this string
     * map back to canonical offsets via {@link #searchToCanonicalRange}, and from there to glyphs,
     * words and highlight rectangles.  Lazily built and cached.
     *
     * @return the normalized search corpus.
     */
    public String searchText() {
        if (searchText == null) buildSearchText();
        return searchText;
    }

    /**
     * Maps a half-open range in {@link #searchText()} back to the corresponding canonical
     * {@link OffsetRange}.
     *
     * @param searchStart inclusive start offset in the search corpus
     * @param searchEnd   exclusive end offset in the search corpus
     * @return canonical offset range
     */
    public OffsetRange searchToCanonicalRange(int searchStart, int searchEnd) {
        if (searchText == null) buildSearchText();
        int max = searchText.length();
        int s = Math.max(0, Math.min(searchStart, max));
        int e = Math.max(0, Math.min(searchEnd, max));
        return OffsetRange.of(searchToCanonical[s], searchToCanonical[e]);
    }

    private void buildSearchText() {
        StringBuilder sb = new StringBuilder(canonical.length());
        int[] map = new int[canonical.length() + 1];
        boolean previousWhitespace = false;
        for (int i = 0; i < canonical.length(); i++) {
            char c = canonical.charAt(i);
            boolean whitespace = c == 160 || Character.isWhitespace(c);
            if (whitespace) {
                if (!previousWhitespace) {
                    map[sb.length()] = i;
                    sb.append(' ');
                    previousWhitespace = true;
                }
            } else {
                map[sb.length()] = i;
                sb.append(c);
                previousWhitespace = false;
            }
        }
        map[sb.length()] = canonical.length();
        searchText = sb.toString();
        searchToCanonical = Arrays.copyOf(map, sb.length() + 1);
    }

    // ------------------------------------------------------------------
    // hit testing (page space)
    // ------------------------------------------------------------------

    /**
     * Maps a page-space point to the nearest caret.  Total: for any point (including
     * margins and off-page) a valid caret in {@code [0, length()]} is returned.
     *
     * @param pagePoint point in page space
     * @return nearest caret
     */
    public Caret caretAt(Point2D pagePoint) {
        if (glyphs.length == 0) return new Caret(0, Bias.FORWARD);
        int line = nearestLine(pagePoint.getY());
        int first = lineFirstGlyph[line], last = lineLastGlyph[line];
        double px = pagePoint.getX();
        Rectangle2D.Double fb = glyphs[first].getBounds();
        Rectangle2D.Double lb = glyphs[last].getBounds();
        if (px <= fb.getMinX()) return new Caret(glyphCharStart[first], Bias.FORWARD);
        if (px >= lb.getMaxX()) return new Caret(glyphCharEnd[last], Bias.BACKWARD);
        for (int i = first; i <= last; i++) {
            Rectangle2D.Double b = glyphs[i].getBounds();
            if (px >= b.getMinX() && px <= b.getMaxX()) {
                return px >= b.getCenterX()
                        ? new Caret(glyphCharEnd[i], Bias.BACKWARD)
                        : new Caret(glyphCharStart[i], Bias.FORWARD);
            }
        }
        // in a gap between glyphs - snap to the nearer boundary
        for (int i = first; i < last; i++) {
            double gapMid = (glyphs[i].getBounds().getMaxX() + glyphs[i + 1].getBounds().getMinX()) / 2;
            if (px < gapMid) return new Caret(glyphCharEnd[i], Bias.BACKWARD);
        }
        return new Caret(glyphCharEnd[last], Bias.BACKWARD);
    }

    /**
     * @param pagePoint point in page space
     * @return true only when the point falls within a glyph's bounding box (drives the
     * text-selection cursor icon).
     */
    public boolean hitsText(Point2D pagePoint) {
        for (GlyphText glyph : glyphs) {
            if (glyph.getBounds().contains(pagePoint)) return true;
        }
        return false;
    }

    private int nearestLine(double y) {
        int best = 0;
        double bestDist = Double.MAX_VALUE;
        for (int i = 0; i < lines.length; i++) {
            Rectangle2D.Double b = lines[i].getBounds();
            if (y >= b.getMinY() && y <= b.getMaxY()) return i;
            double d = Math.min(Math.abs(y - b.getMinY()), Math.abs(y - b.getMaxY()));
            if (d < bestDist) {
                bestDist = d;
                best = i;
            }
        }
        return best;
    }

    // ------------------------------------------------------------------
    // offset <-> content
    // ------------------------------------------------------------------

    /**
     * @param offset char offset
     * @return the glyph whose span covers {@code offset} (downstream), or {@code null}
     * when the offset falls on a line break or past the end.
     */
    public GlyphText glyphAt(int offset) {
        int i = firstGlyphEndingAfter(offset);
        if (i < glyphs.length && glyphCharStart[i] <= offset) return glyphs[i];
        return null;
    }

    /**
     * @param glyph glyph to locate
     * @return the glyph's start char offset, or {@code -1} if not part of this sequence.
     */
    public int offsetOf(GlyphText glyph) {
        Integer i = glyphIndex.get(glyph);
        return i == null ? -1 : glyphCharStart[i];
    }

    /**
     * @param offset char offset
     * @return the sorted-line index that contains {@code offset}.
     */
    public int lineIndexOf(int offset) {
        if (lines.length == 0) return -1;
        int lo = 0, hi = lines.length - 1, ans = 0;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            if (lineStart[mid] <= offset) {
                ans = mid;
                lo = mid + 1;
            } else {
                hi = mid - 1;
            }
        }
        return ans;
    }

    // ------------------------------------------------------------------
    // geometry (page space)
    // ------------------------------------------------------------------

    public List<Rectangle2D.Double> rectsFor(int start, int end) {
        int lo = Math.max(0, Math.min(start, end));
        int hi = Math.min(length(), Math.max(start, end));
        List<Rectangle2D.Double> out = new ArrayList<>();
        if (lo >= hi) return out;
        int curLine = -1;
        Rectangle2D.Double acc = null;
        for (int i = firstGlyphEndingAfter(lo); i < glyphs.length && glyphCharStart[i] < hi; i++) {
            if (glyphLine[i] != curLine) {
                if (acc != null) out.add(acc);
                acc = (Rectangle2D.Double) glyphs[i].getBounds().clone();
                curLine = glyphLine[i];
            } else {
                acc.add(glyphs[i].getBounds());
            }
        }
        if (acc != null) out.add(acc);
        return out;
    }

    public List<Rectangle2D.Double> rectsFor(OffsetRange range) {
        return range == null ? new ArrayList<>() : rectsFor(range.getStart(), range.getEnd());
    }

    /**
     * A thin (zero-width) rectangle at the caret position for painting a caret bar.
     *
     * @param caret caret to locate
     * @return caret bar rectangle in page space
     */
    public Rectangle2D.Double caretRect(Caret caret) {
        int off = caret.getOffset();
        GlyphText g = glyphAt(off);
        if (g != null && glyphCharStart[glyphIndex.get(g)] == off) {
            Rectangle2D.Double b = g.getBounds();
            return new Rectangle2D.Double(b.getMinX(), b.getMinY(), 0, b.getHeight());
        }
        // caret at the trailing edge of the previous glyph (line/word/text end)
        int i = firstGlyphEndingAfter(off) - 1;
        if (i < 0) i = 0;
        if (i >= glyphs.length) i = glyphs.length - 1;
        if (glyphs.length == 0) return new Rectangle2D.Double();
        Rectangle2D.Double b = glyphs[i].getBounds();
        return new Rectangle2D.Double(b.getMaxX(), b.getMinY(), 0, b.getHeight());
    }

    // ------------------------------------------------------------------
    // boundary snapping
    // ------------------------------------------------------------------

    public OffsetRange wordRange(int offset) {
        if (words.length == 0) return OffsetRange.of(0, 0);
        int gi = glyphIndexNear(offset);
        int wi = glyphWord[gi];
        return OffsetRange.of(wordStart[wi], wordEnd[wi]);
    }

    public OffsetRange lineRange(int offset) {
        if (lines.length == 0) return OffsetRange.of(0, 0);
        int li = lineIndexOf(offset);
        return OffsetRange.of(lineStart[li], lineEnd[li]);
    }

    public int nextBoundary(int offset, BreakType type, boolean forward) {
        switch (type) {
            case WORD:
                OffsetRange w = wordRange(offset);
                if (forward) return offset < w.getEnd() ? w.getEnd() : clampOffset(offset + 1);
                return offset > w.getStart() ? w.getStart() : clampOffset(offset - 1);
            case LINE:
                OffsetRange l = lineRange(offset);
                return forward ? l.getEnd() : l.getStart();
            case GLYPH:
            default:
                return clampOffset(forward ? offset + 1 : offset - 1);
        }
    }

    // ------------------------------------------------------------------
    // vertical navigation
    // ------------------------------------------------------------------

    public Caret caretAbove(Caret caret, double goalX) {
        int li = lineIndexOf(caret.getOffset());
        if (li <= 0) return null;
        return new Caret(caretAt(new Point2D.Double(goalX, lines[li - 1].getBounds().getCenterY())).getOffset(),
                caret.getBias());
    }

    public Caret caretBelow(Caret caret, double goalX) {
        int li = lineIndexOf(caret.getOffset());
        if (li < 0 || li >= lines.length - 1) return null;
        return new Caret(caretAt(new Point2D.Double(goalX, lines[li + 1].getBounds().getCenterY())).getOffset(),
                caret.getBias());
    }

    /**
     * Caret at the given line index nearest {@code goalX}; used to land the caret when vertical
     * navigation crosses onto this page.
     *
     * @param lineIndex sorted-line index (clamped into range)
     * @param goalX     preferred page-space x column
     * @return caret on that line
     */
    public Caret caretAtLine(int lineIndex, double goalX) {
        if (lines.length == 0) return new Caret(0, Bias.FORWARD);
        int li = Math.max(0, Math.min(lineIndex, lines.length - 1));
        return caretAt(new Point2D.Double(goalX, lines[li].getBounds().getCenterY()));
    }

    // ------------------------------------------------------------------
    // range -> content (used by the viewer write-through bridge)
    // ------------------------------------------------------------------

    public List<GlyphText> glyphsIn(OffsetRange range) {
        List<GlyphText> out = new ArrayList<>();
        if (range == null || range.isEmpty()) return out;
        int lo = range.getStart(), hi = range.getEnd();
        for (int i = firstGlyphEndingAfter(lo); i < glyphs.length && glyphCharStart[i] < hi; i++) {
            out.add(glyphs[i]);
        }
        return out;
    }

    public List<WordText> wordsIn(OffsetRange range) {
        List<WordText> out = new ArrayList<>();
        if (range == null || range.isEmpty()) return out;
        int lo = range.getStart(), hi = range.getEnd();
        int lastWord = -1;
        for (int i = firstGlyphEndingAfter(lo); i < glyphs.length && glyphCharStart[i] < hi; i++) {
            if (glyphWord[i] != lastWord) {
                out.add(words[glyphWord[i]]);
                lastWord = glyphWord[i];
            }
        }
        return out;
    }

    /**
     * @param wordText a word in this sequence
     * @return the sorted line that contains the word, or {@code null} if not part of this sequence.
     */
    public LineText lineOf(WordText wordText) {
        Integer wi = wordIndex.get(wordText);
        return wi == null ? null : lines[wordLine[wi]];
    }

    /**
     * @param wordText a word in this sequence
     * @return the word's offset range, or {@code null} if it is not part of this sequence.
     */
    public OffsetRange rangeOf(WordText wordText) {
        for (int i = 0; i < words.length; i++) {
            if (words[i] == wordText) return OffsetRange.of(wordStart[i], wordEnd[i]);
        }
        return null;
    }

    // ------------------------------------------------------------------
    // internals
    // ------------------------------------------------------------------

    /** First glyph index whose end offset is strictly greater than {@code offset}. */
    private int firstGlyphEndingAfter(int offset) {
        int lo = 0, hi = glyphs.length;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (glyphCharEnd[mid] <= offset) lo = mid + 1;
            else hi = mid;
        }
        return lo;
    }

    /** Nearest glyph index to {@code offset} (covering, else the one just before/after). */
    private int glyphIndexNear(int offset) {
        int i = firstGlyphEndingAfter(offset);
        if (i >= glyphs.length) return glyphs.length - 1;
        if (glyphCharStart[i] <= offset) return i;       // offset is inside glyph i
        return i;                                         // offset is before glyph i -> use i
    }

    private int clampOffset(int offset) {
        return Math.max(0, Math.min(length(), offset));
    }

    private static int[] toIntArray(List<Integer> list) {
        int[] a = new int[list.size()];
        for (int i = 0; i < a.length; i++) a[i] = list.get(i);
        return a;
    }
}
