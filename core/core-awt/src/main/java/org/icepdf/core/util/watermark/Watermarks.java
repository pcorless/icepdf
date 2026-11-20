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

import org.icepdf.core.pobjects.*;
import org.icepdf.core.pobjects.annotations.Annotation;
import org.icepdf.core.pobjects.annotations.WatermarkAnnotation;
import org.icepdf.core.pobjects.fonts.AFM;
import org.icepdf.core.util.Library;

import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Adds, finds and removes watermarks for a whole document.
 * <p>
 * Each page gets at most one {@link WatermarkAnnotation}: applying a watermark replaces any the page
 * already has. Pages of the same size and rotation share one appearance stream, so a long
 * document carries the watermark's content once, not once a page. The text is laid out as the
 * page is shown, so a header stays along the top edge of a rotated page.
 * <p>
 * Changes go through the document's state manager like any annotation edit, so they're written by
 * the next save. An incremental save keeps the earlier revision, without the watermark, in the
 * file; write with {@code WriteMode.FULL_UPDATE} when that revision must not survive.
 *
 * @since 8.1
 */
public final class Watermarks {

    /** Distance from the page edge to a header or footer, in points. */
    static final float EDGE_MARGIN = 18f;
    /** How much of the page's diagonal an overlay with no font size spans. */
    static final float OVERLAY_SPAN = 0.7f;

    private static final Name FONT_NAME = new Name("WmF0");
    private static final Name GRAPHICS_STATE_NAME = new Name("WmGS0");
    private static final Charset WIN_ANSI = Charset.forName("windows-1252");

    private Watermarks() {
    }

    /**
     * Puts the watermark on every page, replacing any watermark a page already has.
     *
     * @return the number of pages watermarked
     */
    public static int apply(Document document, Watermark watermark) throws InterruptedException {
        return apply(document, watermark, 0, document.getNumberOfPages() - 1);
    }

    /**
     * Puts the watermark on pages {@code first} to {@code last} (zero-based, inclusive), replacing any
     * watermark those pages already have.
     *
     * @return the number of pages watermarked
     */
    public static int apply(Document document, Watermark watermark, int first, int last)
            throws InterruptedException {
        Objects.requireNonNull(watermark, "watermark");
        PageTree pages = document.getPageTree();
        checkRange(first, last, pages.getNumberOfPages());
        Map<String, Reference> shared = new HashMap<>();
        for (int i = first; i <= last; i++) {
            Page page = pages.getPage(i);
            page.init();
            removeFrom(page);
            Rectangle2D box = page.getPageBoundary(Page.BOUNDARY_CROPBOX).toJava2dCoordinates();
            int rotation = clockwiseRotation(page);
            String key = (float) box.getWidth() + "x" + (float) box.getHeight() + "@" + rotation;
            Reference appearance = shared.get(key);
            if (appearance == null) {
                appearance = createAppearance(page.getLibrary(), watermark, box.getWidth(), box.getHeight(), rotation);
                shared.put(key, appearance);
            }
            page.addAnnotation(createAnnotation(page.getLibrary(), watermark, box, appearance));
        }
        return last - first + 1;
    }

    /** Every watermark annotation in the document, in page order, including ones other tools wrote. */
    public static List<WatermarkAnnotation> find(Document document) throws InterruptedException {
        List<WatermarkAnnotation> found = new ArrayList<>();
        PageTree pages = document.getPageTree();
        for (int i = 0; i < pages.getNumberOfPages(); i++) {
            found.addAll(on(pages.getPage(i)));
        }
        return found;
    }

    /** The watermark annotations on one page: normally none or one. */
    public static List<WatermarkAnnotation> on(Page page) throws InterruptedException {
        page.init();
        List<WatermarkAnnotation> found = new ArrayList<>();
        List<Annotation> annotations = page.getAnnotations();
        if (annotations != null) {
            for (Annotation annotation : annotations) {
                if (annotation instanceof WatermarkAnnotation) {
                    found.add((WatermarkAnnotation) annotation);
                }
            }
        }
        return found;
    }

    /**
     * Removes every watermark annotation from the document.
     *
     * @return the number removed
     */
    public static int remove(Document document) throws InterruptedException {
        int removed = 0;
        PageTree pages = document.getPageTree();
        for (int i = 0; i < pages.getNumberOfPages(); i++) {
            Page page = pages.getPage(i);
            page.init();
            removed += removeFrom(page);
        }
        return removed;
    }

    private static int removeFrom(Page page) throws InterruptedException {
        List<WatermarkAnnotation> existing = on(page);
        for (WatermarkAnnotation annotation : existing) {
            page.deleteAnnotation(annotation);
        }
        return existing.size();
    }

    private static void checkRange(int first, int last, int pageCount) {
        if (first < 0 || last >= pageCount || first > last) {
            throw new IllegalArgumentException("pages " + first + " to " + last + " aren't in a document of "
                    + pageCount + " pages");
        }
    }

    /** The page's /Rotate as the PDF states it: clockwise, 0, 90, 180 or 270. */
    static int clockwiseRotation(Page page) {
        // icepdf keeps rotation counter-clockwise, the Java2D way
        int counterClockwise = Math.round(page.getTotalRotation(0) / 90f) * 90;
        return (360 - counterClockwise) % 360;
    }

    private static WatermarkAnnotation createAnnotation(Library library, Watermark watermark, Rectangle2D box,
                                                        Reference appearance) throws InterruptedException {
        StateManager stateManager = library.getStateManager();
        DictionaryEntries entries = new DictionaryEntries();
        entries.put(Dictionary.TYPE_KEY, Annotation.TYPE_VALUE);
        entries.put(Dictionary.SUBTYPE_KEY, Annotation.SUBTYPE_WATERMARK);
        entries.put(Annotation.RECTANGLE_KEY, PRectangle.getPRectangleVector(box));
        DictionaryEntries appearances = new DictionaryEntries();
        appearances.put(Annotation.APPEARANCE_STREAM_NORMAL_KEY, appearance);
        entries.put(Annotation.APPEARANCE_STREAM_KEY, appearances);

        WatermarkAnnotation annotation = new WatermarkAnnotation(library, entries);
        annotation.setPObjectReference(stateManager.getNewReferenceNumber());
        annotation.setNew(true);
        annotation.init();
        annotation.setContents(watermark.getText());
        annotation.setFlag(Annotation.FLAG_PRINT, true);
        // a deterrent, not protection: viewers that honour these won't let the watermark be moved,
        // deleted or edited without first changing its properties
        annotation.setFlag(Annotation.FLAG_LOCKED, true);
        annotation.setFlag(Annotation.FLAG_LOCKED_CONTENTS, true);
        return annotation;
    }

    private static Reference createAppearance(Library library, Watermark watermark, double width, double height,
                                              int rotation) {
        StateManager stateManager = library.getStateManager();
        DictionaryEntries font = new DictionaryEntries();
        font.put(Dictionary.TYPE_KEY, new Name("Font"));
        font.put(Dictionary.SUBTYPE_KEY, new Name("Type1"));
        font.put(new Name("BaseFont"), new Name(watermark.getFont().getBaseFont()));
        font.put(new Name("Encoding"), new Name("WinAnsiEncoding"));
        DictionaryEntries fonts = new DictionaryEntries();
        fonts.put(FONT_NAME, font);

        DictionaryEntries graphicsState = new DictionaryEntries();
        graphicsState.put(Dictionary.TYPE_KEY, new Name("ExtGState"));
        graphicsState.put(new Name("ca"), watermark.getOpacity());
        graphicsState.put(new Name("CA"), watermark.getOpacity());
        DictionaryEntries graphicsStates = new DictionaryEntries();
        graphicsStates.put(GRAPHICS_STATE_NAME, graphicsState);

        DictionaryEntries resources = new DictionaryEntries();
        resources.put(new Name("Font"), fonts);
        resources.put(new Name("ExtGState"), graphicsStates);

        DictionaryEntries entries = new DictionaryEntries();
        entries.put(Form.TYPE_KEY, Form.TYPE_VALUE);
        entries.put(Form.SUBTYPE_KEY, Form.SUB_TYPE_VALUE);
        entries.put(Form.BBOX_KEY, Arrays.asList(0, 0, (float) width, (float) height));
        entries.put(Form.RESOURCES_KEY, resources);

        byte[] content = content(watermark, width, height, rotation);
        Reference reference = stateManager.getNewReferenceNumber();
        Form form = new Form(library, entries, content);
        form.setPObjectReference(reference);
        form.setRawBytes(content);
        library.addObject(form, reference);
        stateManager.addChange(new PObject(form, reference));
        return reference;
    }

    /**
     * The appearance's content stream: the text laid out on the page as shown (width and height
     * swapped for a quarter turn), then mapped back to the unrotated page the appearance is drawn on.
     */
    static byte[] content(Watermark watermark, double width, double height, int rotation) {
        boolean quarterTurn = rotation == 90 || rotation == 270;
        double shownWidth = quarterTurn ? height : width;
        double shownHeight = quarterTurn ? width : height;

        AFM metrics = AFM.AFMs.get(watermark.getFont().getBaseFont().toLowerCase(Locale.ROOT));
        byte[] encoded = encode(watermark.getText());
        double unitWidth = textWidth(metrics, encoded) / 1000.0;   // at 1 point
        double capHeight = (metrics != null && metrics.getCapHeight() > 0 ? metrics.getCapHeight() : 718) / 1000.0;

        float size = watermark.getFontSize();
        AffineTransform text;
        if (watermark.getPosition() == Watermark.Position.OVERLAY) {
            double diagonal = Math.hypot(shownWidth, shownHeight);
            if (size == 0) {
                size = (float) Math.min(OVERLAY_SPAN * diagonal / unitWidth, Math.min(shownWidth, shownHeight) / 4);
            }
            text = AffineTransform.getTranslateInstance(shownWidth / 2, shownHeight / 2);
            text.rotate(Math.atan2(shownHeight, shownWidth));
            text.translate(-unitWidth * size / 2, -capHeight * size / 2);
        } else {
            if (size == 0) {
                size = Watermark.DEFAULT_FONT_SIZE;
            }
            // shrink rather than run off the page
            double room = shownWidth - 2 * EDGE_MARGIN;
            if (unitWidth * size > room && room > 0) {
                size = (float) (room / unitWidth);
            }
            double x = (shownWidth - unitWidth * size) / 2;
            double y = watermark.getPosition() == Watermark.Position.HEADER
                    ? shownHeight - EDGE_MARGIN - capHeight * size
                    : EDGE_MARGIN;
            text = AffineTransform.getTranslateInstance(x, y);
        }

        float[] rgb = watermark.getColor().getRGBColorComponents(null);
        StringBuilder out = new StringBuilder();
        out.append("q\n/").append(GRAPHICS_STATE_NAME).append(" gs\n");
        out.append(number(rgb[0])).append(' ').append(number(rgb[1])).append(' ').append(number(rgb[2]))
                .append(" rg\n");
        out.append(matrix(shownToPage(rotation, width, height))).append(" cm\n");
        out.append("BT\n/").append(FONT_NAME).append(' ').append(number(size)).append(" Tf\n");
        out.append(matrix(text)).append(" Tm\n<");
        for (byte b : encoded) {
            out.append(String.format("%02X", b & 0xff));
        }
        out.append("> Tj\nET\nQ\n");
        return out.toString().getBytes(StandardCharsets.US_ASCII);
    }

    /** Maps the page as shown back to the page's own space, for its clockwise rotation. */
    static AffineTransform shownToPage(int rotation, double width, double height) {
        switch (rotation) {
            case 90:
                return new AffineTransform(0, 1, -1, 0, width, 0);
            case 180:
                return new AffineTransform(-1, 0, 0, -1, width, height);
            case 270:
                return new AffineTransform(0, -1, 1, 0, 0, height);
            default:
                return new AffineTransform();
        }
    }

    /** The text in WinAnsiEncoding; a character it can't hold becomes '?'. */
    static byte[] encode(String text) {
        CharsetEncoder encoder = WIN_ANSI.newEncoder();
        ByteBuffer out = ByteBuffer.allocate(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            try {
                ByteBuffer one = encoder.encode(CharBuffer.wrap(new char[]{c}));
                out.put(one.hasRemaining() ? one.get() : (byte) '?');
            } catch (CharacterCodingException e) {
                out.put((byte) '?');
            }
        }
        return Arrays.copyOf(out.array(), out.position());
    }

    /**
     * Width in thousandths of the font size, from the standard font's metrics. {@link AFM} keeps
     * widths in file order, already in font units; the standard fonts list codes 32 to 126 first and
     * in order, which is printable ASCII in WinAnsiEncoding too. Other characters get the average.
     */
    private static double textWidth(AFM metrics, byte[] encoded) {
        if (metrics == null) {
            return 500.0 * encoded.length;
        }
        float[] widths = metrics.getWidths();
        double average = metrics.getAvgWidth() > 0 ? metrics.getAvgWidth() * 1000 : 500;
        double total = 0;
        for (byte b : encoded) {
            int code = b & 0xff;
            float w = code >= 32 && code <= 126 && widths != null ? widths[code - 32] * 1000 : 0;
            total += w > 0 ? w : average;
        }
        return total;
    }

    private static String matrix(AffineTransform t) {
        return number(t.getScaleX()) + " " + number(t.getShearY()) + " " + number(t.getShearX()) + " "
                + number(t.getScaleY()) + " " + number(t.getTranslateX()) + " " + number(t.getTranslateY());
    }

    private static String number(double value) {
        if (Math.abs(value) < 0.00005) {
            return "0";
        }
        String s = String.format(Locale.ROOT, "%.4f", value);
        s = s.replaceAll("0+$", "");
        return s.endsWith(".") ? s.substring(0, s.length() - 1) : s;
    }
}
