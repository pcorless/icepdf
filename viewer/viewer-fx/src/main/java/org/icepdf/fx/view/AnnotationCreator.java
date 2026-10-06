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
package org.icepdf.fx.view;

import org.icepdf.core.pobjects.Name;
import org.icepdf.core.pobjects.PDate;
import org.icepdf.core.pobjects.PObject;
import org.icepdf.core.pobjects.annotations.*;
import org.icepdf.core.util.Library;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Builds new annotations from view-space geometry (page view px, y down) the way the Swing viewer's
 * tool handlers do - {@code AnnotationFactory.buildAnnotation} with a page-space bounding box, the
 * type's properties, author and date, then {@code resetAppearanceStream} through the page-space
 * transform - without any toolkit.  Each builder returns the annotation un-added; the caller adds
 * it (and its popup) as one undoable edit, see {@link AnnotationEdits#add}.
 * <p>
 * Ported from viewer-awt {@code HighLightAnnotationHandler}, {@code TextAnnotationHandler},
 * {@code SquareAnnotationHandler}, {@code CircleAnnotationHandler}, {@code LineAnnotationHandler},
 * {@code InkAnnotationHandler} and {@code FreeTextAnnotationHandler} (see PROVENANCE.md).
 */
final class AnnotationCreator {

    /** The sticky-note icon size, as Swing's TextAnnotationHandler. */
    static final int NOTE_ICON_SIZE = 23;
    /** Default popup size in points. */
    static final int POPUP_WIDTH = 200;
    static final int POPUP_HEIGHT = 140;
    /** Free text box default size in points, before the user resizes it. */
    static final int FREE_TEXT_WIDTH = 160;
    static final int FREE_TEXT_HEIGHT = 40;

    /** Who and how: author, colour, opacity (0-255) and stroke width for new annotations. */
    record Style(String author, Color color, int opacity, float strokeWidth) {
    }

    private AnnotationCreator() {
    }

    /**
     * A highlight, underline, strike-out (or squiggly) over text: {@code viewRects} are the selected
     * text's rectangles in page view space (selection rects through the page transform).
     */
    static TextMarkupAnnotation textMarkup(Library library, Name subtype, List<Rectangle2D> viewRects, String contents,
                                           AffineTransform toPageSpace, Style style) {
        ArrayList<Shape> bounds = new ArrayList<>(viewRects);
        GeneralPath path = new GeneralPath();
        for (Shape shape : bounds) path.append(shape, false);
        Rectangle tBbox = Annotation.commonBoundsNormalization(new GeneralPath(path), toPageSpace);
        TextMarkupAnnotation annotation = (TextMarkupAnnotation) AnnotationFactory.buildAnnotation(library, subtype, tBbox);
        annotation.setContents(contents != null && !contents.isEmpty() ? contents : subtype.toString());
        annotation.setCreationDate(PDate.formatDateTime(new Date()));
        annotation.setTitleText(style.author());
        annotation.setColor(style.color());
        annotation.setOpacity(style.opacity());
        annotation.setMarkupBounds(bounds);
        annotation.setMarkupPath(path);
        annotation.setBBox(tBbox);
        annotation.resetAppearanceStream(toPageSpace);
        // as Swing: settle the bbox on the rect it produced, avoiding a rounding drift.
        annotation.syncBBoxToUserSpaceRectangle(annotation.getUserSpaceRectangle());
        return annotation;
    }

    /** A sticky note with its icon's top-left at a view point. */
    static TextAnnotation note(Library library, double viewX, double viewY, AffineTransform pageToView,
                               AffineTransform toPageSpace, Style style) {
        Rectangle icon = pageToView.createTransformedShape(new Rectangle(0, 0, NOTE_ICON_SIZE, NOTE_ICON_SIZE)).getBounds();
        Rectangle tBbox = Annotation.commonBoundsNormalization(
                new GeneralPath(new Rectangle((int) viewX, (int) viewY, icon.width, icon.height)), toPageSpace);
        TextAnnotation note = (TextAnnotation) AnnotationFactory.buildAnnotation(library, Annotation.SUBTYPE_TEXT, tBbox);
        note.setCreationDate(PDate.formatDateTime(new Date()));
        note.setTitleText(style.author());
        note.setContents("");
        note.setIconName(TextAnnotation.COMMENT_ICON);
        note.setColor(style.color());
        note.setOpacity(style.opacity());
        note.setState(TextAnnotation.STATE_UNMARKED);
        note.setBBox(new Rectangle(0, 0, tBbox.width, tBbox.height));
        note.resetAppearanceStream(toPageSpace);
        return note;
    }

    /** A rectangle or ellipse filling a view-space rectangle. */
    static MarkupAnnotation shape(Library library, boolean ellipse, Rectangle2D viewRect, AffineTransform toPageSpace,
                                  Style style) {
        int stroke = Math.max(1, Math.round(style.strokeWidth()));
        Rectangle draw = new Rectangle((int) Math.round(viewRect.getX()) - stroke, (int) Math.round(viewRect.getY()) - stroke,
                (int) Math.round(viewRect.getWidth()) + stroke * 2, (int) Math.round(viewRect.getHeight()) + stroke * 2);
        Rectangle tBbox = Annotation.commonBoundsNormalization(new GeneralPath(draw), toPageSpace);
        Rectangle rectangle = Annotation.commonBoundsNormalization(new GeneralPath(viewRect), toPageSpace);
        BorderStyle border = new BorderStyle();
        border.setStrokeWidth(style.strokeWidth());
        MarkupAnnotation annotation;
        if (ellipse) {
            CircleAnnotation circle = (CircleAnnotation) AnnotationFactory.buildAnnotation(library,
                    Annotation.SUBTYPE_CIRCLE, tBbox);
            circle.setRectangle(rectangle);
            circle.setBorderStyle(border);
            annotation = circle;
        } else {
            SquareAnnotation square = (SquareAnnotation) AnnotationFactory.buildAnnotation(library,
                    Annotation.SUBTYPE_SQUARE, tBbox);
            square.setRectangle(rectangle);
            square.setBorderStyle(border);
            annotation = square;
        }
        finishMarkup(annotation, style);
        annotation.setBBox(new Rectangle(0, 0, tBbox.width, tBbox.height));
        annotation.resetAppearanceStream(toPageSpace);
        return annotation;
    }

    /** A straight line between two view-space points. */
    static LineAnnotation line(Library library, Point2D viewStart, Point2D viewEnd, AffineTransform toPageSpace,
                               Style style) {
        Rectangle2D span = new Rectangle2D.Double(Math.min(viewStart.getX(), viewEnd.getX()) - 8,
                Math.min(viewStart.getY(), viewEnd.getY()) - 8,
                Math.abs(viewEnd.getX() - viewStart.getX()) + 16, Math.abs(viewEnd.getY() - viewStart.getY()) + 16);
        Rectangle tBbox = Annotation.commonBoundsNormalization(new GeneralPath(span), toPageSpace);
        LineAnnotation line = (LineAnnotation) AnnotationFactory.buildAnnotation(library, Annotation.SUBTYPE_LINE, tBbox);
        line.setStartArrow(LineAnnotation.LINE_END_NONE);
        line.setEndArrow(LineAnnotation.LINE_END_NONE);
        line.setStartOfLine(toPageSpace.transform(viewStart, null));
        line.setEndOfLine(toPageSpace.transform(viewEnd, null));
        BorderStyle border = new BorderStyle();
        border.setStrokeWidth(style.strokeWidth());
        line.setBorderStyle(border);
        line.setInteriorColor(style.color());
        finishMarkup(line, style);
        line.setContents(line.getSubType().toString());
        line.setBBox(tBbox);
        line.resetAppearanceStream(toPageSpace);
        return line;
    }

    /** Freehand ink along a view-space path. */
    static InkAnnotation ink(Library library, GeneralPath viewPath, AffineTransform toPageSpace, Style style) {
        Rectangle bBox = viewPath.getBounds();
        bBox.setRect(bBox.getX() - 5, bBox.getY() - 5, bBox.getWidth() + 10, bBox.getHeight() + 10);
        Rectangle tBbox = Annotation.commonBoundsNormalization(new GeneralPath(bBox), toPageSpace);
        InkAnnotation ink = (InkAnnotation) AnnotationFactory.buildAnnotation(library, Annotation.SUBTYPE_INK, tBbox);
        BorderStyle border = new BorderStyle();
        border.setStrokeWidth(style.strokeWidth());
        ink.setBorderStyle(border);
        finishMarkup(ink, style);
        ink.setInkPath(toPageSpace.createTransformedShape(viewPath));
        ink.setBBox(tBbox);
        ink.resetAppearanceStream(toPageSpace);
        return ink;
    }

    /** A free text box with its top-left at a view point, empty until edited. */
    static FreeTextAnnotation freeText(Library library, double viewX, double viewY, double zoom,
                                       AffineTransform toPageSpace, Style style) {
        Rectangle draw = new Rectangle((int) viewX, (int) viewY, (int) (FREE_TEXT_WIDTH * zoom),
                (int) (FREE_TEXT_HEIGHT * zoom));
        Rectangle tBbox = Annotation.commonBoundsNormalization(new GeneralPath(draw), toPageSpace);
        FreeTextAnnotation freeText = (FreeTextAnnotation) AnnotationFactory.buildAnnotation(library,
                Annotation.SUBTYPE_FREE_TEXT, tBbox);
        freeText.setCreationDate(PDate.formatDateTime(new Date()));
        freeText.setTitleText(style.author());
        freeText.setContents("");
        freeText.setFontColor(style.color());
        freeText.resetAppearanceStream(toPageSpace);
        return freeText;
    }

    /**
     * A popup for a new markup annotation, beside it, as Swing's TextAnnotationHandler.createPopupAnnotation:
     * registered with the library and state manager, parented, and open only for sticky notes.
     */
    static PopupAnnotation popup(Library library, MarkupAnnotation parent, boolean open, AffineTransform toPageSpace) {
        Rectangle2D r = parent.getUserSpaceRectangle();
        Rectangle bbox = new Rectangle((int) Math.round(r.getMaxX() + 10), (int) Math.round(r.getMaxY() - POPUP_HEIGHT),
                POPUP_WIDTH, POPUP_HEIGHT);
        PopupAnnotation popup = (PopupAnnotation) AnnotationFactory.buildAnnotation(library, Annotation.SUBTYPE_POPUP,
                bbox);
        library.getStateManager().addChange(new PObject(popup, popup.getPObjectReference()));
        library.addObject(popup, popup.getPObjectReference());
        popup.setOpen(open);
        popup.setParent(parent);
        parent.setPopupAnnotation(popup);
        popup.resetAppearanceStream(0, 0, toPageSpace);
        return popup;
    }

    private static void finishMarkup(MarkupAnnotation annotation, Style style) {
        annotation.setCreationDate(PDate.formatDateTime(new Date()));
        annotation.setTitleText(style.author());
        annotation.setColor(style.color());
        annotation.setOpacity(style.opacity());
    }
}
