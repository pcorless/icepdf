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
import org.icepdf.core.pobjects.annotations.Annotation;
import org.icepdf.core.pobjects.annotations.BorderStyle;
import org.icepdf.core.pobjects.annotations.CircleAnnotation;
import org.icepdf.core.pobjects.annotations.FreeTextAnnotation;
import org.icepdf.core.pobjects.annotations.LineAnnotation;
import org.icepdf.core.pobjects.annotations.MarkupAnnotation;
import org.icepdf.core.pobjects.annotations.PopupAnnotation;
import org.icepdf.core.pobjects.annotations.SquareAnnotation;
import org.icepdf.core.pobjects.annotations.TextAnnotation;
import org.icepdf.core.pobjects.annotations.WidgetAnnotation;

import java.awt.Color;

/**
 * The properties of an annotation that the viewer lets the user change, as one value: a colour
 * change from a menu and a properties dialog both build one of these and hand it to
 * {@link PdfView#applyAnnotationProperties}, which records a single undoable edit.
 * <p>
 * A null component means "leave as it is".  {@link #capture} reads every component the annotation
 * has (a full snapshot, used to undo); {@link #apply} writes the non-null ones that the annotation's
 * type supports and ignores the rest, so a dialog can fill a record without checking types twice.
 *
 * @param color       the annotation colour (stroke / markup colour)
 * @param fillColor   interior colour (square, circle, line, free text)
 * @param fillEnabled whether the interior is filled (square, circle, free text)
 * @param opacity     0 to 1 (markup annotations)
 * @param borderWidth border stroke width in points
 * @param borderStyle border style name ({@code BorderStyle.BORDER_STYLE_*})
 * @param startArrow  line start ending
 * @param endArrow    line end ending
 * @param fontName    free text font
 * @param fontSize    free text size
 * @param fontColor   free text colour
 * @param iconName    sticky note icon
 * @param print       the Print flag
 * @param noZoom      the NoZoom flag
 * @param noRotate    the NoRotate flag
 * @param readOnly    the ReadOnly flag
 */
public record AnnotationProperties(Color color, Color fillColor, Boolean fillEnabled, Float opacity,
                                   Float borderWidth, Name borderStyle, Name startArrow, Name endArrow,
                                   String fontName, Float fontSize, Color fontColor, Name iconName,
                                   Boolean print, Boolean noZoom, Boolean noRotate, Boolean readOnly) {

    /** True if no component is set, so applying it changes nothing. */
    public boolean isEmpty() {
        return color == null && fillColor == null && fillEnabled == null && opacity == null && borderWidth == null
                && borderStyle == null && startArrow == null && endArrow == null && fontName == null
                && fontSize == null && fontColor == null && iconName == null && print == null && noZoom == null
                && noRotate == null && readOnly == null;
    }

    /** A change of colour only. */
    public static AnnotationProperties ofColor(Color color) {
        return new AnnotationProperties(color, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null);
    }

    /** True if the user can change this annotation's properties: widgets and popups can't. */
    public static boolean supports(Annotation annotation) {
        return annotation != null && !(annotation instanceof WidgetAnnotation) && !(annotation instanceof PopupAnnotation);
    }

    /** True if the annotation has a colour to change. */
    public static boolean hasColor(Annotation annotation) {
        return supports(annotation) && !(annotation instanceof org.icepdf.core.pobjects.annotations.LinkAnnotation);
    }

    /** Everything the annotation has, as it is now. */
    public static AnnotationProperties capture(Annotation a) {
        BorderStyle border = a.getBorderStyle();
        Color fill = null;
        Boolean fillOn = null;
        if (a instanceof SquareAnnotation s) {
            fill = s.getFillColor();
            fillOn = s.isFillColor();
        } else if (a instanceof CircleAnnotation c) {
            fill = c.getFillColor();
            fillOn = c.isFillColor();
        } else if (a instanceof LineAnnotation l) {
            fill = l.getInteriorColor();
        } else if (a instanceof FreeTextAnnotation f) {
            fill = f.getFillColor();
            fillOn = f.isFillType();
        }
        LineAnnotation line = a instanceof LineAnnotation l ? l : null;
        FreeTextAnnotation text = a instanceof FreeTextAnnotation f ? f : null;
        return new AnnotationProperties(a.getColor(), fill, fillOn,
                a instanceof MarkupAnnotation m ? m.getOpacity() : null,
                border != null ? border.getStrokeWidth() : null, border != null ? border.getBorderStyle() : null,
                line != null ? line.getStartArrow() : null, line != null ? line.getEndArrow() : null,
                text != null ? text.getFontName() : null, text != null ? text.getFontSize() : null,
                text != null ? text.getFontColor() : null,
                a instanceof TextAnnotation t ? t.getIconName() : null,
                a.getFlagPrint(), a.getFlagNoZoom(), a.getFlagNoRotate(), a.getFlagReadOnly());
    }

    /** Writes the non-null components the annotation's type supports. */
    public void apply(Annotation a) {
        if (color != null) a.setColor(color);
        if (a instanceof MarkupAnnotation m && opacity != null) m.setOpacity(opacity);
        if (borderWidth != null || borderStyle != null) {
            BorderStyle border = a.getBorderStyle();
            if (border == null) {
                border = new BorderStyle();
                a.setBorderStyle(border);
            }
            if (borderWidth != null) border.setStrokeWidth(borderWidth);
            if (borderStyle != null) border.setBorderStyle(borderStyle);
        }
        if (a instanceof SquareAnnotation s) {
            if (fillColor != null) s.setFillColor(fillColor);
            if (fillEnabled != null) s.setFillColor(fillEnabled.booleanValue());
        } else if (a instanceof CircleAnnotation c) {
            if (fillColor != null) c.setFillColor(fillColor);
            if (fillEnabled != null) c.setFillColor(fillEnabled.booleanValue());
        } else if (a instanceof LineAnnotation l) {
            if (fillColor != null) l.setInteriorColor(fillColor);
            if (startArrow != null) l.setStartArrow(startArrow);
            if (endArrow != null) l.setEndArrow(endArrow);
        } else if (a instanceof FreeTextAnnotation f) {
            if (fillColor != null) f.setFillColor(fillColor);
            if (fillEnabled != null) f.setFillType(fillEnabled);
            if (fontName != null) f.setFontName(fontName);
            if (fontSize != null) f.setFontSize(fontSize);
            if (fontColor != null) f.setFontColor(fontColor);
        } else if (a instanceof TextAnnotation t && iconName != null) {
            t.setIconName(iconName);
        }
        if (print != null) a.setFlag(Annotation.FLAG_PRINT, print);
        if (noZoom != null) a.setFlag(Annotation.FLAG_NO_ZOOM, noZoom);
        if (noRotate != null) a.setFlag(Annotation.FLAG_NO_ROTATE, noRotate);
        if (readOnly != null) a.setFlag(Annotation.FLAG_READ_ONLY, readOnly);
    }
}
