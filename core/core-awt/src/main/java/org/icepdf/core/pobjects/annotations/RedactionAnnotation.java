package org.icepdf.core.pobjects.annotations;

import org.icepdf.core.pobjects.*;
import org.icepdf.core.pobjects.graphics.Shapes;
import org.icepdf.core.pobjects.graphics.commands.*;
import org.icepdf.core.util.ColorUtil;
import org.icepdf.core.util.Defs;
import org.icepdf.core.util.Library;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.icepdf.core.pobjects.annotations.utils.QuadPoints.buildQuadPoints;
import static org.icepdf.core.pobjects.annotations.utils.QuadPoints.parseQuadPoints;

/**
 * RedactionAnnotations allow an area of content to be marked by the user for redaction.  This annotation type
 * does not actually remove the content instead it acts as a marker.   The content will only be removed if the
 * document is saved to an output stream using WriteMode.FULL_UPDATE.
 *
 * @since 7.2.0
 */
public class RedactionAnnotation extends MarkupAnnotation {

    private static final Logger logger =
            Logger.getLogger(RedactionAnnotation.class.toString());

    private static Color redactionColor;

    static {
        // sets annotation selected redaction colour, generally it's always going to be black, but can be configured
        try {
            String color = Defs.sysProperty(
                    "org.icepdf.core.views.page.annotation.redactionColor.highlight.color", "#000000");
            int colorValue = ColorUtil.convertColor(color);
            redactionColor = new Color(colorValue >= 0 ? colorValue : Integer.parseInt("000000", 16));
        } catch (NumberFormatException e) {
            if (logger.isLoggable(Level.WARNING)) {
                logger.warning("Error reading Text Markup Annotation redaction colour");
            }
        }
    }

    /*
     * (Optional) A form XObject specifying the overlay appearance for this redaction annotation. After this redaction
     * is applied and the affected content has been removed, the overlay appearance should be drawn such that its
     * origin lines up with the lower-left corner of the annotation rectangle. This form XObject is not necessarily
     * related to other annotation appearances, and may or may not be present in the AP dictionary. This entry takes
     * precedence over the IC, OverlayText, DA, and Q entries.
     */
    public static final Name RO_KEY = new Name("RO");

    /*
     * (Optional) A text string specifying the overlay text that should be drawn over the redacted region after the
     * affected content has been removed. This entry is ignored if the RO entry is present.
     */
    public static final Name OVERLAY_TEXT_KEY = new Name("OverlayText");

    /*
     * (Optional) If true, then the text specified by OverlayText should be repeated to fill the redacted region after
     * the affected content has been removed. This entry is ignored if the RO entry is present.
     * Default value: false.
     */
    public static final Name REPEAT_KEY = new Name("Repeat");

    /*
     * (Required if OverlayText is present, ignored otherwise) The appearance string to be used in formatting the
     * overlay text when it is drawn after the affected content has been removed (see 12.7.3.3, "Variable Text").
     * This entry is ignored if the RO entry is present.
     */
    public static final Name DA_KEY = new Name("DA");

    /**
     * (Optional) A code specifying the form of quadding
     * (justification) that shall be used in displaying the annotation’s text:
     * 0 - Left-justified
     * 1 - Centered
     * 2 - Right-justified
     * Default value: 0 (left-justified).
     */
    public static final Name Q_KEY = new Name("Q");

    public RedactionAnnotation(Library library, DictionaryEntries dictionaryEntries) {
        super(library, dictionaryEntries);
    }

    /**
     * Gets an instance of a TextMarkupAnnotation that has valid Object Reference.
     *
     * @param library document library
     * @param rect    bounding rectangle in user space
     * @return new RedactAnnotation Instance.
     */
    public static RedactionAnnotation getInstance(Library library, Rectangle rect) {
        // state manager
        StateManager stateManager = library.getStateManager();

        // create a new entries to hold the annotation properties
        DictionaryEntries entries = createCommonMarkupDictionary(Annotation.SUBTYPE_REDACT, rect);

        RedactionAnnotation redactAnnotation = null;
        try {
            redactAnnotation = new RedactionAnnotation(library, entries);
            redactAnnotation.init();
            entries.put(NM_KEY, new LiteralStringObject(String.valueOf(redactAnnotation.hashCode())));
            redactAnnotation.setPObjectReference(stateManager.getNewReferenceNumber());
            redactAnnotation.setNew(true);
            redactAnnotation.setModifiedDate(PDate.formatDateTime(new Date()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.fine("Text markup annotation instance creation was interrupted");
        }
        return redactAnnotation;
    }

    public void init() throws InterruptedException {
        super.init();
        // collect the quad points.
        quadrilaterals = parseQuadPoints(library, entries);
        color = getColor() == null ? redactionColor : getColor();

        // for editing purposes grab anny shapes from the AP Stream and
        // store them as markupBounds and markupPath. This works ok but
        // perhaps a better way would be to reapply the bound box
        Appearance appearance = appearances.get(currentAppearance);
        AppearanceState appearanceState = appearance.getSelectedAppearanceState();
        Shapes shapes = appearanceState.getShapes();
        if (shapes != null) {
            markupBounds = new ArrayList<>();
            markupPath = new GeneralPath();

            ShapeDrawCmd shapeDrawCmd;
            for (DrawCmd cmd : shapes.getShapes()) {
                if (cmd instanceof ShapeDrawCmd) {
                    shapeDrawCmd = (ShapeDrawCmd) cmd;
                    markupBounds.add(shapeDrawCmd.getShape());
                    markupPath.append(shapeDrawCmd.getShape(), false);
                }
            }

        }
        // try and generate an appearance stream.
        resetNullAppearanceStream();
    }

    @Override
    public void resetAppearanceStream(double dx, double dy, AffineTransform pageSpace, boolean isNew) {
        // check if we have anything to reset.
        if (markupBounds == null) {
            return;
        }

        Appearance appearance = appearances.get(currentAppearance);
        AppearanceState appearanceState = appearance.getSelectedAppearanceState();
        appearanceState.setShapes(new Shapes());

        Rectangle2D bbox = appearanceState.getBbox();
        AffineTransform matrix = appearanceState.getMatrix();
        Shapes shapes = appearanceState.getShapes();

        // set up the stroke from the border settings.
        BasicStroke stroke = new BasicStroke(1f);
        shapes.add(new StrokeDrawCmd(stroke));
        shapes.add(new GraphicsStateCmd(EXT_GSTATE_NAME));
        shapes.add(new AlphaDrawCmd(
                AlphaComposite.getInstance(AlphaComposite.SRC_OVER, opacity)));
        shapes.add(new ShapeDrawCmd(markupPath));
        shapes.add(new ColorDrawCmd(color));
        shapes.add(new FillDrawCmd());
        shapes.add(new AlphaDrawCmd(
                AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f)));

        // create the quad points
        entries.put(KEY_QUAD_POINTS, buildQuadPoints(markupBounds));
        setModifiedDate(PDate.formatDateTime(new Date()));

        // update the appearance stream
        // create/update the appearance stream of the xObject.
        Form form = updateAppearanceStream(shapes, bbox, matrix,
                PostScriptEncoder.generatePostScript(shapes.getShapes()), isNew);
        generateExternalGraphicsState(form, opacity);
    }
}
