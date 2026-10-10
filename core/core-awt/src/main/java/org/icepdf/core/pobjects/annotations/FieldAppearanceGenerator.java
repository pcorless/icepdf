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
 * The layout rules follow Apache PDFBox's AppearanceGeneratorHelper and PlainTextFormatter
 * (org.apache.pdfbox.pdmodel.interactive, Apache License 2.0), which follow Acrobat; rewritten for
 * ICEpdf's object model; see NOTICE.
 */
package org.icepdf.core.pobjects.annotations;

import org.icepdf.core.pobjects.DictionaryEntries;
import org.icepdf.core.pobjects.Name;
import org.icepdf.core.pobjects.acroform.ChoiceFieldDictionary;
import org.icepdf.core.pobjects.acroform.FieldDictionary;
import org.icepdf.core.pobjects.acroform.InteractiveForm;
import org.icepdf.core.pobjects.acroform.TextFieldDictionary;
import org.icepdf.core.pobjects.acroform.VariableTextFieldDictionary;
import org.icepdf.core.pobjects.fonts.Font;
import org.icepdf.core.util.Library;

import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Builds the normal appearance of a text or choice field from its value, the way Acrobat does
 * (PDF 32000-1 12.7.3.3): the {@code /DA} font and colour, the {@code /Q} quadding, a 1pt padding
 * inside the border, single-line text centred on its cap height, multi-line text wrapped, comb fields
 * one character to a cell, list boxes with their selection highlighted, font size 0 fitted to the
 * box, and the {@code /MK} rotation, border and background.
 * <p>
 * Only the {@code /Tx BMC ... EMC} part of an existing appearance is replaced, so whatever the
 * document drew around it stays - unless the widget has an {@code /MK}, in which case the whole
 * appearance is drawn from it, as Acrobat does.
 */
final class FieldAppearanceGenerator {

    /** The generated appearance stream: content, form bbox and matrix, and the font it uses. */
    static final class Result {
        final String content;
        final Rectangle2D bbox;
        final AffineTransform matrix;
        final Name fontName;

        Result(String content, Rectangle2D bbox, AffineTransform matrix, Name fontName) {
            this.content = content;
            this.bbox = bbox;
            this.matrix = matrix;
            this.fontName = fontName;
        }
    }

    /** Acrobat's list box selection colour, whatever the existing appearance used. */
    private static final float[] LIST_HIGHLIGHT = {153 / 255f, 193 / 255f, 215 / 255f};
    private static final float DEFAULT_FONT_SIZE = 12;
    private static final float MINIMUM_FONT_SIZE = 4;
    private static final float DEFAULT_PADDING = 0.5f;
    private static final String DEFAULT_APPEARANCE = "/Helv 0 Tf 0 g";
    /** The font a field falls back to, by the resource name Acrobat gives Helvetica. */
    static final Name STANDARD_FONT = new Name("Helv");

    private static final Pattern TF = Pattern.compile("/([^\\s/\\[\\]()<>{}%]+)\\s+([-+]?(?:\\d+\\.?\\d*|\\.\\d+))\\s+Tf");
    private static final Pattern TX_BMC = Pattern.compile("/Tx\\s*BMC");
    private static final Pattern EMC = Pattern.compile("(?<![A-Za-z])EMC(?![A-Za-z])");
    private static final Pattern LINE_BREAKS = Pattern.compile("\\r\\n|[\\n\\u000B\\f\\r\\u0085\\u2028\\u2029]");

    private final AbstractWidgetAnnotation<?> widget;
    private final VariableTextFieldDictionary field;
    private final Library library;

    private String defaultAppearance;
    private Name fontName;
    private float fontSize;
    private FieldFontMetrics metrics;

    private FieldAppearanceGenerator(AbstractWidgetAnnotation<?> widget) {
        this.widget = widget;
        this.field = (VariableTextFieldDictionary) widget.getFieldDictionary();
        this.library = widget.getLibrary();
    }

    /**
     * @param widget          a text or choice widget
     * @param existingContent the appearance's current content stream, or null when it has none
     * @param existingBox     the appearance's current bbox, or null
     * @param existingMatrix  the appearance's current matrix, or null
     */
    static Result generate(AbstractWidgetAnnotation<?> widget, String existingContent, Rectangle2D existingBox,
                           AffineTransform existingMatrix) {
        return new FieldAppearanceGenerator(widget).build(existingContent, existingBox, existingMatrix);
    }

    private Result build(String existingContent, Rectangle2D existingBox, AffineTransform existingMatrix) {
        resolveDefaultAppearance();
        Font font = widget.getTextFont(fontName);
        if (font == null || !isWritable(font)) {
            // a /DA font the document doesn't have would draw nothing, and a composite font with a
            // CMap other than Identity can't be written from Unicode without the CMap reversed: use
            // Helvetica, as the field appearance did before (and as a reader substitutes).
            useStandardFont();
            font = widget.getTextFont(fontName);
            if (font != null && !isWritable(font)) font = null;
        }
        metrics = FieldFontMetrics.of(font);

        AppearanceCharacteristics characteristics = widget.getAppearanceCharacteristics();
        int rotation = characteristics != null ? characteristics.getRotation() : 0;
        Rectangle2D rect = widget.getUserSpaceRectangle();
        double width = Math.abs(rect.getWidth()), height = Math.abs(rect.getHeight());
        if (rotation == 90 || rotation == 270) {
            double swap = width;
            width = height;
            height = swap;
        }
        Rectangle2D bbox;
        AffineTransform matrix;
        if (existingBox != null && Math.abs(Math.abs(existingBox.getWidth()) - width) <= 1
                && Math.abs(Math.abs(existingBox.getHeight()) - height) <= 1 && existingBox.getWidth() > 0
                && existingBox.getHeight() > 0) {
            // the existing appearance fits the widget: keep its box and matrix.
            bbox = existingBox;
            matrix = existingMatrix != null ? existingMatrix : new AffineTransform();
        } else {
            bbox = new Rectangle2D.Float(0, 0, (float) width, (float) height);
            matrix = rotationMatrix(bbox, rotation);
        }

        StringBuilder out = new StringBuilder();
        String suffix = "EMC\n";
        // Acrobat redraws the whole appearance from /MK when there is one, and so must an empty one.
        if (characteristics != null || existingContent == null || existingContent.trim().isEmpty()) {
            drawBackgroundAndBorder(out, characteristics, bbox);
            out.append("/Tx BMC\n");
        } else {
            Matcher bmc = TX_BMC.matcher(existingContent);
            if (bmc.find()) {
                out.append(existingContent, 0, bmc.end()).append('\n');
                // the last EMC after it closes the /Tx section; everything from there on stays.
                Matcher emc = EMC.matcher(existingContent);
                int emcStart = -1;
                while (emc.find()) {
                    if (emc.start() >= bmc.end()) emcStart = emc.start();
                }
                if (emcStart >= 0) suffix = existingContent.substring(emcStart);
            } else {
                out.append(existingContent).append("\n/Tx BMC\n");
            }
        }
        drawText(out, bbox);
        out.append(suffix);
        if (!suffix.endsWith("\n")) out.append('\n');
        return new Result(out.toString(), bbox, matrix, fontName);
    }

    /** The {@code /DA} in effect: the widget's, an ancestor field's, else the AcroForm's. */
    private void resolveDefaultAppearance() {
        String da = string(widget.getEntries(), VariableTextFieldDictionary.DA_KEY);
        FieldDictionary ancestor = field.getParent();
        for (int depth = 0; da == null && ancestor != null && depth < 32; depth++) {
            da = string(ancestor.getEntries(), VariableTextFieldDictionary.DA_KEY);
            ancestor = ancestor.getParent();
        }
        InteractiveForm form = library.getCatalog() != null ? library.getCatalog().getInteractiveForm() : null;
        if (da == null && form != null) {
            da = string(form.getEntries(), VariableTextFieldDictionary.DA_KEY);
        }
        if (da == null || !TF.matcher(da).find()) {
            da = (da != null ? da + " " : "") + DEFAULT_APPEARANCE;
        }
        Matcher tf = TF.matcher(da);
        MatchResult last = null;
        while (tf.find()) last = tf.toMatchResult();
        defaultAppearance = da;
        fontName = new Name(last.group(1));
        try {
            fontSize = Float.parseFloat(last.group(2));
        } catch (NumberFormatException e) {
            fontSize = 0;
        }
    }

    /**
     * Whether text can be written in this font from Unicode: a simple font through its encoding, a
     * composite font only with an Identity CMap (two-byte codes; see FontTextEncoder).
     */
    private static boolean isWritable(Font font) {
        if (font.getSubTypeFormat() != Font.CID_FORMAT) return true;
        Name encoding = font.getEncoding();
        return encoding != null && encoding.getName().startsWith("Identity-");
    }

    /** Swaps the /DA font for {@link #STANDARD_FONT}, keeping its size and colour. */
    private void useStandardFont() {
        Matcher tf = TF.matcher(defaultAppearance);
        int start = -1, end = -1;
        while (tf.find()) {
            start = tf.start(1);
            end = tf.end(1);
        }
        defaultAppearance = defaultAppearance.substring(0, start) + STANDARD_FONT.getName()
                + defaultAppearance.substring(end);
        fontName = STANDARD_FONT;
    }

    private String string(DictionaryEntries entries, Name key) {
        Object value = library.getObject(entries, key);
        if (value instanceof org.icepdf.core.pobjects.StringObject) {
            return org.icepdf.core.util.Utils.convertStringObject(library, (org.icepdf.core.pobjects.StringObject) value);
        }
        return value instanceof String ? (String) value : null;
    }

    /** The {@code /DA} with its font size replaced by the size the text is laid out at. */
    private String defaultAppearance(float size) {
        Matcher tf = TF.matcher(defaultAppearance);
        int start = -1, end = -1;
        while (tf.find()) {
            start = tf.start(2);
            end = tf.end(2);
        }
        return defaultAppearance.substring(0, start) + number(size) + defaultAppearance.substring(end);
    }

    /** {@code /Q}: the widget's, an ancestor field's, else the AcroForm's; 0 left, 1 centred, 2 right. */
    private int quadding() {
        Object value = library.getObject(widget.getEntries(), VariableTextFieldDictionary.Q_KEY);
        FieldDictionary ancestor = field.getParent();
        for (int depth = 0; !(value instanceof Number) && ancestor != null && depth < 32; depth++) {
            value = library.getObject(ancestor.getEntries(), VariableTextFieldDictionary.Q_KEY);
            ancestor = ancestor.getParent();
        }
        InteractiveForm form = library.getCatalog() != null ? library.getCatalog().getInteractiveForm() : null;
        if (!(value instanceof Number) && form != null) {
            value = library.getObject(form.getEntries(), VariableTextFieldDictionary.Q_KEY);
        }
        int q = value instanceof Number ? ((Number) value).intValue() : 0;
        return q >= 0 && q <= 2 ? q : 0;
    }

    private int maxLength() {
        Object value = library.getObject(widget.getEntries(), TextFieldDictionary.MAX_LENGTH_KEY);
        FieldDictionary ancestor = field.getParent();
        for (int depth = 0; !(value instanceof Number) && ancestor != null && depth < 32; depth++) {
            value = library.getObject(ancestor.getEntries(), TextFieldDictionary.MAX_LENGTH_KEY);
            ancestor = ancestor.getParent();
        }
        return value instanceof Number ? ((Number) value).intValue() : -1;
    }

    private boolean isMultiLine() {
        return field instanceof TextFieldDictionary && ((TextFieldDictionary) field).isMultiLine();
    }

    private boolean isListBox() {
        return field instanceof ChoiceFieldDictionary
                && ((ChoiceFieldDictionary) field).getChoiceFieldType() == ChoiceFieldDictionary.ChoiceFieldType.CHOICE_LIST_SINGLE_SELECT
                || field instanceof ChoiceFieldDictionary
                && ((ChoiceFieldDictionary) field).getChoiceFieldType() == ChoiceFieldDictionary.ChoiceFieldType.CHOICE_LIST_MULTIPLE_SELECT;
    }

    /** A comb field: MaxLen set, and not multi-line, password or file select (PDF 32000-1 table 228). */
    private boolean isComb(int maxLength) {
        if (!(field instanceof TextFieldDictionary) || maxLength <= 0) return false;
        TextFieldDictionary text = (TextFieldDictionary) field;
        return text.isComb() && !text.isMultiLine() && !text.isFileSelect()
                && text.getTextFieldType() != TextFieldDictionary.TextFieldType.TEXT_PASSWORD;
    }

    /** The text shown: a text field's value, or the label of a combo box's selected option. */
    private String value() {
        Object value = field.getFieldValue();
        String text = value instanceof String ? (String) value : value != null ? value.toString() : "";
        if (field instanceof ChoiceFieldDictionary) {
            List<ChoiceFieldDictionary.ChoiceOption> options = ((ChoiceFieldDictionary) field).getOptions();
            if (options != null) {
                for (ChoiceFieldDictionary.ChoiceOption option : options) {
                    if (text.equals(option.getValue()) && option.getLabel() != null) return option.getLabel();
                }
            }
        }
        if (!isMultiLine()) {
            // a single-line field shows its line breaks as spaces, as Acrobat does when typed into.
            text = LINE_BREAKS.matcher(text).replaceAll(" ");
        }
        return text;
    }

    /** Background and border from {@code /MK} and {@code /BS}, before the {@code /Tx} content. */
    private void drawBackgroundAndBorder(StringBuilder out, AppearanceCharacteristics characteristics, Rectangle2D bbox) {
        if (characteristics == null) return;
        float[] background = characteristics.getBackgroundColor();
        if (background != null) {
            out.append(colour(background, false)).append(rect(bbox)).append(" re f\n");
        }
        float[] borderColour = characteristics.getBorderColor();
        BorderStyle style = widget.getBorderStyle();
        float lineWidth = borderColour != null ? 1 : 0;
        if (style != null && style.getStrokeWidth() > 0) lineWidth = style.getStrokeWidth();
        if (lineWidth <= 0 || borderColour == null) return;

        out.append(colour(borderColour, true));
        if (lineWidth != 1) out.append(number(lineWidth)).append(" w\n");
        Rectangle2D edge = inset(bbox, Math.max(DEFAULT_PADDING, lineWidth / 2));
        if (style != null && style.isStyleUnderline()) {
            out.append(number((float) bbox.getMinX())).append(' ').append(number((float) edge.getMinY())).append(" m ")
                    .append(number((float) bbox.getMaxX())).append(' ').append(number((float) edge.getMinY())).append(" l S\n");
        } else {
            if (style != null && style.isStyleDashed()) {
                float[] dash = style.getDashArray();
                out.append('[');
                if (dash == null || dash.length == 0) {
                    out.append('3');
                } else {
                    for (int i = 0; i < dash.length; i++) out.append(i > 0 ? " " : "").append(number(dash[i]));
                }
                out.append("] 0 d\n");
            }
            out.append(rect(edge)).append(" re s\n");
            if (style != null && (style.isStyleBeveled() || style.isStyleInset())) {
                drawBevel(out, bbox, lineWidth, style.isStyleBeveled(), background);
            }
        }
        // a comb field's cell dividers.
        int maxLength = maxLength();
        if (isComb(maxLength)) {
            float cell = (float) bbox.getWidth() / maxLength;
            for (int i = 1; i < maxLength; i++) {
                float x = (float) bbox.getMinX() + cell * i;
                out.append(number(x)).append(' ').append(number((float) edge.getMinY())).append(" m ")
                        .append(number(x)).append(' ').append(number((float) edge.getMaxY())).append(" l\n");
            }
            out.append("S\n");
        }
    }

    /**
     * The two bevel bands inside a beveled or inset border, as Acrobat draws them: the upper-left band
     * light (white, or mid gray when inset) and the lower-right band darker (the background at half
     * intensity, or light gray when inset).
     */
    private static void drawBevel(StringBuilder out, Rectangle2D bbox, float w, boolean beveled, float[] background) {
        float x0 = (float) bbox.getMinX(), y0 = (float) bbox.getMinY();
        float x1 = (float) bbox.getMaxX(), y1 = (float) bbox.getMaxY();
        out.append(beveled ? "1 g\n" : "0.5 g\n");
        out.append(number(x0 + w)).append(' ').append(number(y0 + w)).append(" m ")
                .append(number(x0 + w)).append(' ').append(number(y1 - w)).append(" l ")
                .append(number(x1 - w)).append(' ').append(number(y1 - w)).append(" l ")
                .append(number(x1 - 2 * w)).append(' ').append(number(y1 - 2 * w)).append(" l ")
                .append(number(x0 + 2 * w)).append(' ').append(number(y1 - 2 * w)).append(" l ")
                .append(number(x0 + 2 * w)).append(' ').append(number(y0 + 2 * w)).append(" l f\n");
        if (beveled && background != null) {
            float[] darker = new float[background.length];
            for (int i = 0; i < darker.length; i++) {
                // CMYK darkens by adding ink; the others by halving.
                darker[i] = background.length == 4 ? Math.min(1, background[i] + (1 - background[i]) / 2) : background[i] / 2;
            }
            out.append(colour(darker, false));
        } else {
            out.append("0.75 g\n");
        }
        out.append(number(x1 - w)).append(' ').append(number(y1 - w)).append(" m ")
                .append(number(x1 - w)).append(' ').append(number(y0 + w)).append(" l ")
                .append(number(x0 + w)).append(' ').append(number(y0 + w)).append(" l ")
                .append(number(x0 + 2 * w)).append(' ').append(number(y0 + 2 * w)).append(" l ")
                .append(number(x1 - 2 * w)).append(' ').append(number(y0 + 2 * w)).append(" l ")
                .append(number(x1 - 2 * w)).append(' ').append(number(y1 - 2 * w)).append(" l f\n");
    }

    /** The clipped text block: {@code q <clip> W n [highlight] BT <DA> <text> ET Q}. */
    private void drawText(StringBuilder out, Rectangle2D bbox) {
        // Acrobat pads by the border width, at least 1, twice: once to the clip, again to the text;
        // a beveled or inset border's bands take another border width.
        BorderStyle style = widget.getBorderStyle();
        float borderWidth = style != null ? style.getStrokeWidth() : 0;
        if (style != null && (style.isStyleBeveled() || style.isStyleInset())) borderWidth *= 2;
        float padding = Math.max(1f, borderWidth);
        Rectangle2D clip = inset(bbox, padding);
        Rectangle2D content = inset(clip, padding);

        boolean listBox = isListBox();
        int maxLength = maxLength();
        boolean comb = isComb(maxLength);
        String value = listBox ? "" : value();
        float size = fontSize;
        if (size == 0) {
            size = listBox ? DEFAULT_FONT_SIZE : autoFontSize(value, content);
        }

        out.append("q\n").append(rect(clip)).append(" re W n\n");
        List<Integer> selected = null;
        int topIndex = 0;
        if (listBox) {
            ChoiceFieldDictionary choice = (ChoiceFieldDictionary) field;
            selected = selectedIndexes(choice);
            topIndex = topIndex(choice, selected, content, size);
            drawListHighlight(out, bbox, selected, topIndex, size);
        }
        out.append("BT\n").append(defaultAppearance(size)).append('\n');

        float scale = size / 1000f;
        if (comb) {
            drawComb(out, bbox, value, maxLength, size);
        } else if (listBox) {
            drawListOptions(out, content, topIndex, size);
        } else if (isMultiLine()) {
            float leading = metrics.getBoundingBoxHeight() * scale;
            float y = (float) content.getMaxY() - leading;
            List<PlainTextLayout.Line> lines = new ArrayList<>();
            for (String paragraph : PlainTextLayout.paragraphs(value)) {
                lines.addAll(PlainTextLayout.lines(paragraph, metrics, size, (float) content.getWidth()));
            }
            PlainTextLayout.write(out, lines, metrics, quadding(), (float) content.getMinX(), y,
                    (float) content.getWidth(), leading);
        } else {
            float y = singleLineBaseline(clip, content, size);
            List<PlainTextLayout.Line> line = new ArrayList<>(1);
            line.add(new PlainTextLayout.Line(value, metrics.width(value, size)));
            PlainTextLayout.write(out, line, metrics, quadding(), (float) content.getMinX(), y,
                    (float) content.getWidth(), 0);
        }
        out.append("ET\nQ\n");
    }

    /**
     * A single line's baseline: the capitals centred in the clip; but if a descender would fall out
     * of it, raised to fit, and if the capitals don't fit at all, sat on the clip's bottom edge.
     */
    private float singleLineBaseline(Rectangle2D clip, Rectangle2D content, float size) {
        float scale = size / 1000f;
        float cap = metrics.getCapHeight() * scale;
        float descent = metrics.getDescent() * scale;
        float bottom = (float) clip.getMinY();
        float clipHeight = (float) clip.getHeight();
        if (cap > clipHeight) {
            return bottom - descent;
        }
        float y = bottom + (clipHeight - cap) / 2;
        if (y - bottom < -descent) {
            float descentBased = -descent + (float) content.getMinY();
            float capBased = (float) content.getHeight() - (float) content.getMinY() - cap;
            y = Math.min(descentBased, Math.max(y, capBased));
        }
        return y;
    }

    /**
     * Font size 0 is auto-size: a single line as large as fits the box's height and its width; multi-
     * line text the largest whole size from 4 to 12 whose wrapped lines fit the height.
     */
    private float autoFontSize(String value, Rectangle2D content) {
        if (isMultiLine()) {
            List<String> paragraphs = PlainTextLayout.paragraphs(value);
            float fits = MINIMUM_FONT_SIZE;
            for (float size = MINIMUM_FONT_SIZE; size <= DEFAULT_FONT_SIZE; size++) {
                int lines = 0;
                for (String paragraph : paragraphs) {
                    lines += PlainTextLayout.lines(paragraph, metrics, size, (float) content.getWidth()).size();
                }
                if (metrics.getBoundingBoxHeight() * size / 1000f * lines > content.getHeight()) break;
                fits = size;
            }
            return fits;
        }
        float textHeight = (metrics.getCapHeight() - metrics.getDescent()) / 1000f;
        if (textHeight <= 0) textHeight = metrics.getBoundingBoxHeight() / 1000f;
        float heightBased = (float) content.getHeight() / textHeight;
        float unitWidth = metrics.width(value, 1);
        if (unitWidth <= 0) return Math.max(1, heightBased);
        return Math.max(1, Math.min(heightBased, (float) content.getWidth() / unitWidth));
    }

    /** A comb field: one character centred in each cell, the run placed by the quadding. */
    private void drawComb(StringBuilder out, Rectangle2D bbox, String value, int maxLength, float size) {
        if (value.isEmpty()) return;
        int count = Math.min(value.length(), maxLength);
        float cell = (float) bbox.getWidth() / maxLength;
        int q = quadding();
        int firstCell = q == 2 ? maxLength - count : q == 1 ? (maxLength - count) / 2 : 0;
        float baseline = (float) bbox.getMinY() + ((float) bbox.getHeight() - metrics.getAscent() * size / 1000f) / 2;
        float lastX = 0;
        for (int i = 0; i < count; i++) {
            String c = value.substring(i, i + 1);
            float x = (float) bbox.getMinX() + cell * (firstCell + i) + (cell - metrics.width(c, size)) / 2;
            out.append(number(i == 0 ? x : x - lastX)).append(' ').append(number(i == 0 ? baseline : 0)).append(" Td ");
            metrics.getEncoder().appendShowString(out, c).append(" Tj\n");
            lastX = x;
        }
    }

    /** The selected options: {@code /I}, else the options whose export value is in {@code /V}. */
    private List<Integer> selectedIndexes(ChoiceFieldDictionary choice) {
        List<Integer> selected = new ArrayList<>();
        List<Integer> indexes = choice.getIndexes();
        List<ChoiceFieldDictionary.ChoiceOption> options = choice.getOptions();
        if (indexes != null && !indexes.isEmpty()) {
            selected.addAll(indexes);
        } else if (options != null) {
            Object value = choice.getFieldValue();
            List<String> values = new ArrayList<>();
            if (value instanceof List) {
                for (Object v : (List<?>) value) {
                    Object resolved = library.getObject(v);
                    if (resolved instanceof org.icepdf.core.pobjects.StringObject) {
                        values.add(org.icepdf.core.util.Utils.convertStringObject(library,
                                (org.icepdf.core.pobjects.StringObject) resolved));
                    } else if (resolved != null) {
                        values.add(resolved.toString());
                    }
                }
            } else if (value != null && !value.toString().isEmpty()) {
                values.add(value.toString());
            }
            for (int i = 0; i < options.size(); i++) {
                if (values.contains(options.get(i).getValue())) selected.add(i);
            }
        }
        return selected;
    }

    /**
     * The first option shown: {@code /TI}; or, when there is none and the first selection would be
     * below the box, scrolled just far enough to show it.
     */
    private int topIndex(ChoiceFieldDictionary choice, List<Integer> selected, Rectangle2D content, float size) {
        int top = choice.getTopIndex();
        if (top > 0 || library.getObject(widget.getEntries(), ChoiceFieldDictionary.TI_KEY) != null) {
            return Math.max(0, top);
        }
        if (!selected.isEmpty()) {
            float row = metrics.getBoundingBoxHeight() * size / 1000f;
            int visible = row > 0 ? Math.max(1, (int) Math.floor(content.getHeight() / row)) : 1;
            int first = selected.get(0);
            if (first >= visible) return first - visible + 1;
        }
        return 0;
    }

    private void drawListHighlight(StringBuilder out, Rectangle2D bbox, List<Integer> selected, int topIndex, float size) {
        if (selected.isEmpty()) return;
        float row = metrics.getBoundingBoxHeight() * size / 1000f;
        Rectangle2D edge = inset(bbox, 1);
        out.append(colour(LIST_HIGHLIGHT, false));
        for (int index : selected) {
            if (index < topIndex) continue;
            float y = (float) edge.getMaxY() - row * (index - topIndex + 1) + 2;
            out.append(number((float) edge.getMinX())).append(' ').append(number(y)).append(' ')
                    .append(number((float) edge.getWidth())).append(' ').append(number(row)).append(" re f\n");
        }
    }

    /** List box options from the top index down, one to a row; the clip hides what doesn't fit. */
    private void drawListOptions(StringBuilder out, Rectangle2D content, int topIndex, float size) {
        List<ChoiceFieldDictionary.ChoiceOption> options = ((ChoiceFieldDictionary) field).getOptions();
        if (options == null || topIndex >= options.size()) return;
        float scale = size / 1000f;
        float row = metrics.getBoundingBoxHeight() * scale;
        int visible = row > 0 ? (int) Math.ceil(content.getHeight() / row) + 1 : options.size();
        List<PlainTextLayout.Line> lines = new ArrayList<>();
        for (int i = topIndex; i < options.size() && i < topIndex + visible; i++) {
            String label = options.get(i).getLabel() != null ? options.get(i).getLabel() : options.get(i).getValue();
            if (label == null) label = "";
            lines.add(new PlainTextLayout.Line(label, metrics.width(label, size)));
        }
        float y = (float) content.getMaxY() - metrics.getAscent() * scale;
        PlainTextLayout.write(out, lines, metrics, quadding(), (float) content.getMinX(), y,
                (float) content.getWidth(), row);
    }

    /** A rotation by {@code /MK /R}, moved back so the rotated box starts at the origin. */
    private static AffineTransform rotationMatrix(Rectangle2D bbox, int rotation) {
        if (rotation == 0) return new AffineTransform();
        AffineTransform rotate = AffineTransform.getRotateInstance(Math.toRadians(rotation));
        Rectangle2D turned = rotate.createTransformedShape(bbox).getBounds2D();
        AffineTransform matrix = AffineTransform.getTranslateInstance(-turned.getMinX(), -turned.getMinY());
        matrix.concatenate(rotate);
        // snap the cos/sin of a right angle to exact 0/1.
        double[] m = new double[6];
        matrix.getMatrix(m);
        for (int i = 0; i < 4; i++) m[i] = Math.rint(m[i]);
        return new AffineTransform(m);
    }

    private static Rectangle2D inset(Rectangle2D box, float by) {
        return new Rectangle2D.Float((float) box.getMinX() + by, (float) box.getMinY() + by,
                (float) Math.max(0, box.getWidth() - 2 * by), (float) Math.max(0, box.getHeight() - 2 * by));
    }

    private static String rect(Rectangle2D r) {
        return number((float) r.getMinX()) + " " + number((float) r.getMinY()) + " "
                + number((float) r.getWidth()) + " " + number((float) r.getHeight());
    }

    /** A colour operator for 1 (gray), 3 (RGB) or 4 (CMYK) components. */
    private static String colour(float[] c, boolean stroke) {
        StringBuilder out = new StringBuilder();
        for (float v : c) out.append(number(v)).append(' ');
        String op = c.length == 1 ? "g" : c.length == 4 ? "k" : "rg";
        return out.append(stroke ? op.toUpperCase() : op).append('\n').toString();
    }

    /** A number for a content stream: at most four decimals, no exponent, no trailing zeros. */
    static String number(float value) {
        if (value == Math.rint(value) && Math.abs(value) < 1e9) {
            return Long.toString((long) value);
        }
        return new BigDecimal(value).setScale(4, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }
}
