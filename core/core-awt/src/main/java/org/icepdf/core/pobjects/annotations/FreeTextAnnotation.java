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
package org.icepdf.core.pobjects.annotations;

import org.icepdf.core.pobjects.*;
import org.icepdf.core.pobjects.annotations.utils.ContentWriterUtils;
import org.icepdf.core.pobjects.fonts.FontFile;
import org.icepdf.core.pobjects.graphics.Shapes;
import org.icepdf.core.pobjects.graphics.commands.*;
import org.icepdf.core.util.ColorUtil;
import org.icepdf.core.util.Defs;
import org.icepdf.core.util.Library;

import javax.swing.text.DefaultStyledDocument;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A free text annotation (PDF 1.3) displays text directly on the page. Unlike
 * an ordinary text annotation (see 12.5.6.4, "Text Annotations"), a free text
 * annotation has no open or closed state; instead of being displayed in a pop-up
 * window, the text shall be always visible. Table 174 shows the annotation
 * dictionary entries specific to this type of annotation. 12.7.3.3,
 * "Variable Text" describes the process of using these entries to generate the
 * appearance of the text in these annotations.
 *
 * @since 5.0
 */
public class FreeTextAnnotation extends MarkupAnnotation {

    private static final Logger logger =
            Logger.getLogger(FreeTextAnnotation.class.toString());

    public static final int INSETS = 5;

    /**
     * (Required) The default appearance string that shall be used in formatting
     * the text (see 12.7.3.3, "Variable Text").
     * <br>
     * The annotation dictionary’s AP entry, if present, shall take precedence
     * over the DA entry; see Table 168 and 12.5.5, "Appearance Streams."
     */
    public static final Name DA_KEY = new Name("DA");
    /**
     * (Optional; PDF 1.4) A code specifying the form of quadding
     * (justification) that shall be used in displaying the annotation’s text:
     * 0 - Left-justified
     * 1 - Centered
     * 2 - Right-justified
     * Default value: 0 (left-justified).
     */
    public static final Name Q_KEY = new Name("Q");
    /**
     * (Optional; PDF 1.5) A default style string, as described in 12.7.3.4,
     * "Rich Text Strings."
     */
    public static final Name DS_KEY = new Name("DS");
    /**
     * (Optional; meaningful only if IT is FreeTextCallout; PDF 1.6) An array of
     * four or six numbers specifying a callout line attached to the free text
     * annotation. Six numbers [ x1 y1 x2 y2 x3 y3 ] represent the starting,
     * knee point, and ending coordinates of the line in default user space, as
     * shown in Figure 8.4. Four numbers [ x1 y1 x2 y2 ] represent the starting
     * and ending coordinates of the line.
     */
    public static final Name CL_KEY = new Name("CL");

    /*
      (Optional; PDF 1.6) A name describing the intent of the free text
      annotation (see also the IT entry in Table 170). The following values
      shall be valid:
      <br>
      FreeTextThe annotation is intended to function as a plain free-text
      annotation. A plain free-text annotation is also known as a text box comment.
      FreeTextCallout The annotation is intended to function as a callout. The
      callout is associated with an area on the page through the callout line
      specified in CL.
      <br>
      FreeTextTypeWriterThe annotation is intended to function as a click-to-type
      or typewriter object and no callout line is drawn.
      Default value: FreeText
     */
//    public static final Name IT_KEY = new Name("IT");

    /**
     * (Optional; PDF 1.6) A border effect dictionary (see Table 167) used in
     * conjunction with the border style dictionary specified by the BS entry.
     */
    public static final Name BE_KEY = new Name("BE");

    /**
     * (Optional; PDF 1.6) A set of four numbers describing the numerical
     * differences between two rectangles: the Rect entry of the annotation and
     * a rectangle contained within that rectangle. The inner rectangle is where
     * the annotation’s text should be displayed. Any border styles and/or border
     * effects specified by BS and BE entries, respectively, shall be applied to
     * the border of the inner rectangle.
     * <br>
     * The four numbers correspond to the differences in default user space
     * between the left, top, right, and bottom coordinates of Rect and those
     * of the inner rectangle, respectively. Each value shall be greater than
     * or equal to 0. The sum of the top and bottom differences shall be less
     * than the height of Rect, and the sum of the left and right differences
     * shall be less than the width of Rect.
     */
    public static final Name RD_KEY = new Name("RD");
    /**
     * (Optional; PDF 1.6) A border style dictionary (see Table 166) specifying
     * the line width and dash pattern that shall be used in drawing the
     * annotation’s border.
     * <br>
     * The annotation dictionary’s AP entry, if present, takes precedence over
     * the BS entry; see Table 164 and 12.5.5, "Appearance Streams".
     */
    public static final Name BS_KEY = new Name("BS");
    /**
     * (Optional; meaningful only if CL is present; PDF 1.6) A name specifying
     * the line ending style that shall be used in drawing the callout line
     * specified in CL. The name shall specify the line ending style for the
     * endpoint defined by the pairs of coordinates (x1, y1). Table 176 shows
     * the possible line ending styles.
     * <br>
     * Default value: None.
     */
    public static final Name LE_KEY = new Name("LE");
    /**
     * Left-justified quadding
     */
    public static final int QUADDING_LEFT_JUSTIFIED = 0;
    /**
     * Right-justified quadding
     */
    public static final int QUADDING_CENTER_JUSTIFIED = 1;
    /**
     * Center-justified quadding
     */
    public static final int QUADDING_RIGHT_JUSTIFIED = 2;
    public static final Name EMBEDDED_FONT_NAME = new Name("ice1");

    public static Color defaultFontColor;
    public static Color defaultFillColor;
    public static Color defaultBorderColor;
    public static int defaultFontSize;

    static {

        // sets annotation free text font colour
        try {
            String color = Defs.sysProperty(
                    "org.icepdf.core.views.page.annotation.freeText.font.color", "#000000");
            int colorValue = ColorUtil.convertColor(color);
            defaultFontColor =
                    new Color(colorValue >= 0 ? colorValue :
                            Integer.parseInt("000000", 16));
        } catch (NumberFormatException e) {
            if (logger.isLoggable(Level.WARNING)) {
                logger.warning("Error reading free text annotation font colour");
            }
        }

        // sets annotation free text fill colour
        try {
            String color = Defs.sysProperty(
                    "org.icepdf.core.views.page.annotation.freeText.fill.color", "#ffffff");
            int colorValue = ColorUtil.convertColor(color);
            defaultFillColor =
                    new Color(colorValue >= 0 ? colorValue :
                            Integer.parseInt("ffffff", 16));
        } catch (NumberFormatException e) {
            if (logger.isLoggable(Level.WARNING)) {
                logger.warning("Error reading free text annotation fill colour");
            }
        }

        // sets annotation free text fill colour
        try {
            String color = Defs.sysProperty(
                    "org.icepdf.core.views.page.annotation.freeText.border.color", "#cccccc");
            int colorValue = ColorUtil.convertColor(color);
            defaultBorderColor =
                    new Color(colorValue >= 0 ? colorValue :
                            Integer.parseInt("000000", 16));
        } catch (NumberFormatException e) {
            if (logger.isLoggable(Level.WARNING)) {
                logger.warning("Error reading free text annotation fill colour");
            }
        }

        // sets annotation free text fill colour
        try {
            defaultFontSize = Defs.sysPropertyInt(
                    "org.icepdf.core.views.page.annotation.freeText.font.size", 12);
        } catch (NumberFormatException e) {
            if (logger.isLoggable(Level.WARNING)) {
                logger.warning("Error reading free text annotation fill colour");
            }
        }

    }

    protected String defaultAppearance;
    protected int quadding = QUADDING_LEFT_JUSTIFIED;
    protected String defaultStylingString;
    protected boolean hideRenderedOutput;
    protected String richText;

    // appearance properties not to be confused with annotation properties,
    // these properties are updated by the UI components and used to regenerate
    // the annotations appearance stream and other needed properties on edits.
    private String fontName = "Helvetica";
    private int fontStyle = Font.PLAIN;
    private int fontSize = defaultFontSize;
    private Color fontColor = defaultFontColor;
    // fill
    private boolean fillType = false;
    private Color fillColor = defaultFillColor;
    // stroke
    private boolean strokeType = false;

    // editing placeholder
    protected DefaultStyledDocument document;

    // font file is cached to avoid expensive lookups.
    protected FontFile fontFile;
    protected boolean fontPropertyChanged;

    public FreeTextAnnotation(Library library, DictionaryEntries dictionaryEntries) {
        super(library, dictionaryEntries);
    }

    public void init() throws InterruptedException {
        super.init();

        Appearance appearance = appearances.get(APPEARANCE_STREAM_NORMAL_KEY);
        Shapes shapes = null;
        if (appearance != null) {
            AppearanceState appearanceState = appearance.getSelectedAppearanceState();
            shapes = appearanceState.getShapes();
        }
        // re-get colour so we can check for a null entry
        if (library.getObject(entries, COLOR_KEY) != null &&
                getObject(APPEARANCE_STREAM_KEY) != null) {
            // iterate over shapes and try and find the fill and stroke colors.
            if (shapes != null) {
                Color currentColor = Color.BLACK;
                for (DrawCmd drawCmd : shapes.getShapes()) {
                    if (drawCmd instanceof ColorDrawCmd) {
                        currentColor = ((ColorDrawCmd) drawCmd).getColor();
                    } else if (drawCmd instanceof FillDrawCmd) {
                        fillType = true;
                        fillColor = new Color(currentColor.getRGB());
                    } else if (drawCmd instanceof DrawDrawCmd) {
                        strokeType = true;
                        color = new Color(currentColor.getRGB());
                    }
                }
            }
        } else {
            color = Color.BLACK;
        }

        defaultAppearance = library.getString(entries, DA_KEY);

        if (library.getObject(entries, Q_KEY) != null) {
            quadding = library.getInt(entries, Q_KEY);
        }
        // find rich text string
        Object tmp = library.getObject(entries, RC_KEY);
        if (tmp instanceof StringObject) {
            StringObject tmpRichText = (StringObject) tmp;
            richText = tmpRichText.getDecryptedLiteralString(library.getSecurityManager());
        }

        // default style string
        if (library.getObject(entries, DS_KEY) != null) {
            defaultStylingString = getString(DS_KEY);
        }

        // set the default quadding value
        if (library.getObject(entries, Q_KEY) == null) {
            entries.put(Q_KEY, 0);
        }
        // free text.
        if (library.getObject(entries, IT_KEY) == null) {
            entries.put(IT_KEY, new Name("FreeText"));
        }

        // check for defaultStylingString and if so parse out the
        // font style, weight and name.
        parseDefaultStylingString();
        // try and generate an appearance stream.
        resetNullAppearanceStream();
    }

    /**
     * Gets an instance of a FreeTextAnnotation that has valid Object Reference.
     *
     * @param library document library
     * @param rect    bounding rectangle in user space
     * @return new FreeTextAnnotation Instance.
     */
    public static FreeTextAnnotation getInstance(Library library,
                                                 Rectangle rect) {
        // state manager
        StateManager stateManager = library.getStateManager();

        // create a new entries to hold the annotation properties
        DictionaryEntries entries = new DictionaryEntries();
        // set default link annotation values.
        entries.put(Dictionary.TYPE_KEY, Annotation.TYPE_VALUE);
        entries.put(Dictionary.SUBTYPE_KEY, Annotation.SUBTYPE_FREE_TEXT);
        // coordinates
        if (rect != null) {
            entries.put(Annotation.RECTANGLE_KEY,
                    PRectangle.getPRectangleVector(rect));
        } else {
            entries.put(Annotation.RECTANGLE_KEY, new Rectangle(10, 10, 50, 100));
        }

        // create the new instance
        FreeTextAnnotation freeTextAnnotation = null;
        try {
            freeTextAnnotation = new FreeTextAnnotation(library, entries);
            freeTextAnnotation.init();
            freeTextAnnotation.setPObjectReference(stateManager.getNewReferenceNumber());
            freeTextAnnotation.setNew(true);

            // set default flags.
            freeTextAnnotation.setFlag(Annotation.FLAG_READ_ONLY, false);
            freeTextAnnotation.setFlag(Annotation.FLAG_NO_ROTATE, false);
            freeTextAnnotation.setFlag(Annotation.FLAG_NO_ZOOM, false);
            freeTextAnnotation.setFlag(Annotation.FLAG_PRINT, true);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.fine("FreeTextAnnotation initialization interrupted.");
        }
        return freeTextAnnotation;
    }

    @Override
    public void render(Graphics2D origG, int renderHintType, float totalRotation, float userZoom, boolean tabSelected) {
        // suspend the rendering when the UI tools are present.
        if (!hideRenderedOutput) {
            super.render(origG, renderHintType, totalRotation, userZoom, tabSelected);
        }
    }

    @Override
    public void resetAppearanceStream(double dx, double dy, AffineTransform pageTransform, boolean isNew) {

        Appearance appearance = appearances.get(currentAppearance);
        AppearanceState appearanceState = appearance.getSelectedAppearanceState();
        appearanceState.setMatrix(new AffineTransform());
        appearanceState.setShapes(new Shapes());

        Rectangle2D bbox = appearanceState.getBbox();
        bbox.setRect(0, 0, bbox.getWidth(), bbox.getHeight());

        Shapes shapes = ContentWriterUtils.createAppearanceShapes(appearanceState, INSETS, INSETS);

        AffineTransform matrix = appearanceState.getMatrix();

        // iterate over each line of text painting the strings.
        if (content == null) {
            setContents("");
        }

        // create the new font to draw with
        if (fontFile == null || fontPropertyChanged) {
            fontFile = ContentWriterUtils.createFont(fontName);
            fontPropertyChanged = false;
        }
        fontFile = fontFile.deriveFont(fontSize);

        BasicStroke stroke;
        if (strokeType && borderStyle.isStyleDashed()) {
            stroke = new BasicStroke(
                    borderStyle.getStrokeWidth(), BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                    borderStyle.getStrokeWidth() * 2.0f, borderStyle.getDashArray(), 0.0f);
        } else {
            stroke = new BasicStroke(borderStyle.getStrokeWidth());
        }

        // apply opacity graphics state.
        shapes.add(new GraphicsStateCmd(EXT_GSTATE_NAME));
        shapes.add(new AlphaDrawCmd(
                AlphaComposite.getInstance(AlphaComposite.SRC_OVER, opacity)));

        // background colour
        shapes.add(new ShapeDrawCmd(new Rectangle2D.Double(bbox.getX(), bbox.getY(),
                bbox.getWidth() - INSETS * 2, bbox.getHeight() - INSETS * 2)));
        if (fillType) {
            shapes.add(new ColorDrawCmd(fillColor));
            shapes.add(new FillDrawCmd());
        }
        // border
        if (strokeType) {
            shapes.add(new StrokeDrawCmd(stroke));
            shapes.add(new ColorDrawCmd(color));
            shapes.add(new DrawDrawCmd());
        }
        // actual font.
        float borderOffsetX = borderStyle.getStrokeWidth() / 2 + 1;  // 1 pixel padding
        float borderOffsetY = borderStyle.getStrokeWidth() / 2;
        // is generally going to be zero, and af takes care of the offset for inset.
        float advanceX = (float) bbox.getMinX() + borderOffsetX;
        float advanceY = (float) bbox.getMinY() + borderOffsetY;
        ContentWriterUtils.addTextSpritesToShapes(fontFile, advanceX, advanceY, shapes, fontSize, 0, fontColor,
                content);

        // update the appearance stream
        // create/update the appearance stream of the xObject.
        StateManager stateManager = library.getStateManager();
        Form form = updateAppearanceStream(shapes, bbox, matrix,
                PostScriptEncoder.generatePostScript(shapes.getShapes()), isNew);
        generateExternalGraphicsState(form, opacity);
        ContentWriterUtils.setAppearance(this, form, appearanceState, stateManager, isNew);
        form.addFontResource(ContentWriterUtils.createDefaultFontDictionary(fontName));

        // build out a few backwards compatible strings.
        StringBuilder dsString = new StringBuilder("font-size:")
                .append(fontSize).append("pt;")
                .append("font-family:").append(fontName).append(";")
                .append("color:").append(ColorUtil.convertColorToRGB(fontColor))
                .append(";");
        if (fontStyle == Font.BOLD) {
            dsString.append("font-weight:bold;");
        }
        if (fontStyle == Font.ITALIC) {
            dsString.append("font-style:italic;");
        }
        if (fontStyle == Font.PLAIN) {
            dsString.append("font-style:normal;");
        }
        setString(DS_KEY, dsString.toString());

        // write out the  color
        if (fillType) {
            Color color = this.color;
            // no AP stream then we need to set color as the fillColor, just the
            // way it is.  spec is a bit weak for FreeText.
            if (entries.get(APPEARANCE_STREAM_KEY) == null) {
                color = fillColor;
            }
            float[] compArray = new float[3];
            color.getColorComponents(compArray);
            java.util.List<Float> colorValues = new ArrayList<>(compArray.length);
            for (float comp : compArray) {
                colorValues.add(comp);
            }
            entries.put(COLOR_KEY, colorValues);
        } else {
            entries.remove(COLOR_KEY);
        }

        // write out the default content test.
        setContents(content);

        // build out the rich text string.
        Object[] colorArgument = new Object[]{dsString};
        MessageFormat formatter = new MessageFormat(BODY_START);
        StringBuilder rcString = new StringBuilder(formatter.format(colorArgument));
        String[] lines = content.split("[\\r\\n]+");
        for (String line : lines) {
            rcString.append("<p>").append(line).append("</p>");
        }
        rcString.append(BODY_END);
        setString(RC_KEY, rcString.toString());
    }

    public String getDefaultStylingString() {
        return defaultStylingString;
    }

    public void clearShapes() {
        Appearance appearance = appearances.get(currentAppearance);
        AppearanceState appearanceState = appearance.getSelectedAppearanceState();
        appearanceState.setShapes(null);
    }

    public void setDocument(DefaultStyledDocument document) {
        this.document = document;
    }

    public boolean isHideRenderedOutput() {
        return hideRenderedOutput;
    }

    // print. Consider making it static.
    public void setHideRenderedOutput(boolean hideRenderedOutput) {
        this.hideRenderedOutput = hideRenderedOutput;
    }

    public String getDefaultAppearance() {
        return defaultAppearance;
    }

    public void setDefaultAppearance(String defaultAppearance) {
        this.defaultAppearance = defaultAppearance;
        entries.put(DA_KEY, defaultAppearance);
    }

    public int getQuadding() {
        return quadding;
    }

    public void setQuadding(int quadding) {
        this.quadding = quadding;
        entries.put(Q_KEY, quadding);
    }

    public String getRichText() {
        return richText;
    }

    public void setRichText(String richText) {
        this.richText = richText;
    }

    public Color getFontColor() {
        return fontColor;
    }

    public void setFontColor(Color fontColor) {
        this.fontColor = new Color(fontColor.getRGB());
    }

    public Color getFillColor() {
        return fillColor;
    }

    public void setFillColor(Color fillColor) {
        this.fillColor = new Color(fillColor.getRGB());
    }

    public String getFontName() {
        return fontName;
    }

    public void setFontName(String fontName) {
        this.fontName = fontName;
        fontPropertyChanged = true;
    }

    public int getFontStyle() {
        return fontStyle;
    }

    public void setFontStyle(int fontStyle) {
        this.fontStyle = fontStyle;
    }

    public int getFontSize() {
        return fontSize;
    }

    public void setFontSize(int fontSize) {
        this.fontSize = fontSize;
        fontPropertyChanged = true;
    }

    public boolean isFillType() {
        return fillType;
    }

    public boolean isFontPropertyChanged() {
        return fontPropertyChanged;
    }

    public void setFillType(boolean fillType) {
        this.fillType = fillType;
    }

    public boolean isStrokeType() {
        return strokeType;
    }

    public void setStrokeType(boolean strokeType) {
        this.strokeType = strokeType;
    }

    public void setDefaultStylingString(String defaultStylingString) {
        this.defaultStylingString = defaultStylingString;
        parseDefaultStylingString();
        entries.put(DS_KEY, defaultStylingString);
    }

    private void parseDefaultStylingString() {
        if (defaultStylingString != null) {
            StringTokenizer toker = new StringTokenizer(defaultStylingString, ";");
            while (toker.hasMoreElements()) {
                String cssProperty = (String) toker.nextElement();
                if (cssProperty != null && cssProperty.contains("font-family")) {
                    fontName = cssProperty.substring(cssProperty.indexOf(":") + 1).trim();
                } else if (cssProperty != null && cssProperty.contains("color")) {
                    String colorString = cssProperty.substring(cssProperty.indexOf(":") + 1).trim();
                    fontColor = new Color(ColorUtil.convertColor(colorString));
                } else if (cssProperty != null && cssProperty.contains("font-weight")) {
                    String fontStyle = cssProperty.substring(cssProperty.indexOf(":") + 1).trim();
                    switch (fontStyle) {
                        case "normal":
                            this.fontStyle = Font.PLAIN;
                            break;
                        case "italic":
                            this.fontStyle = Font.ITALIC;
                            break;
                        case "bold":
                            this.fontStyle = Font.BOLD;
                            break;
                    }
                } else if (cssProperty != null && cssProperty.contains("font-size")) {
                    String fontSize = cssProperty.substring(cssProperty.indexOf(":") + 1).trim();
                    fontSize = fontSize.substring(0, fontSize.indexOf('p'));
                    try {
                        this.fontSize = (int) Float.parseFloat(fontSize);
                    } catch (NumberFormatException e) {
                        logger.finer("Error parsing font size: " + fontSize);
                    }
                }
            }
        }
    }

    public static final String BODY_START =
            "<?xml version=\"1.0\"?><body xmlns=\"http://www.w3.org/1999/xhtml\" xmlns:xfa=\"https://www.xfa" +
                    ".org/schema/xfa-data/1.0/\" xfa:APIVersion=\"Acrobat:11.0.0\" xfa:spec=\"2.0.2\"  " +
                    "style=\"{0}\">";

    public static final String BODY_END = "</body>";

}