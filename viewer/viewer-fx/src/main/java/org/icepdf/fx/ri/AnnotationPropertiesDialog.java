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
package org.icepdf.fx.ri;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.Spinner;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.ButtonType;
import javafx.scene.layout.GridPane;
import javafx.scene.paint.Color;
import javafx.stage.Window;
import org.icepdf.core.pobjects.Name;
import org.icepdf.core.pobjects.annotations.Annotation;
import org.icepdf.core.pobjects.annotations.BorderStyle;
import org.icepdf.core.pobjects.annotations.CircleAnnotation;
import org.icepdf.core.pobjects.annotations.FreeTextAnnotation;
import org.icepdf.core.pobjects.annotations.InkAnnotation;
import org.icepdf.core.pobjects.annotations.LineAnnotation;
import org.icepdf.core.pobjects.annotations.MarkupAnnotation;
import org.icepdf.core.pobjects.annotations.SquareAnnotation;
import org.icepdf.core.pobjects.annotations.TextAnnotation;
import org.icepdf.fx.view.AnnotationProperties;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The properties dialog of an annotation, as the Swing viewer's but with OK and Cancel: nothing is
 * written until OK, and then as one undoable edit.  The fields shown depend on the annotation's
 * type; the result holds only what the user changed.
 */
public final class AnnotationPropertiesDialog {

    private static final String[] FONTS = {"Helvetica", "Helvetica-Oblique", "Helvetica-Bold", "Helvetica-BoldOblique",
            "Times-Roman", "Times-Italic", "Times-Bold", "Times-BoldItalic", "Courier", "Courier-Oblique",
            "Courier-Bold", "Courier-BoldOblique"};
    private static final String[] ICONS = {"Comment", "Check", "CheckMark", "Circle", "Cross", "CrossHairs", "Help",
            "Insert", "Key", "NewParagraph", "Paragraph", "RightArrow", "RightPointer", "Star", "UpLeftArrow",
            "UpArrow"};

    private AnnotationPropertiesDialog() {
    }

    /** Shows the dialog; the changes the user made, or empty on Cancel or when nothing changed. */
    public static Optional<AnnotationProperties> show(Window owner, Annotation annotation) {
        AnnotationProperties before = AnnotationProperties.capture(annotation);
        Fields fields = new Fields(annotation, before);
        Dialog<AnnotationProperties> dialog = new Dialog<>();
        dialog.setTitle(title(annotation) + " Properties");
        if (owner != null) dialog.initOwner(owner);
        DialogPane pane = dialog.getDialogPane();
        pane.getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        TabPane tabs = new TabPane(tab("Appearance", fields.appearance()), tab("Flags", fields.flags()));
        pane.setContent(tabs);
        dialog.setResultConverter(b -> b == ButtonType.OK ? fields.result() : null);
        return dialog.showAndWait().filter(p -> !p.isEmpty());
    }

    private static String title(Annotation a) {
        if (a instanceof FreeTextAnnotation) return "Free Text";
        if (a instanceof TextAnnotation) return "Note";
        if (a instanceof InkAnnotation) return "Ink";
        if (a instanceof LineAnnotation) return "Line";
        if (a instanceof SquareAnnotation) return "Rectangle";
        if (a instanceof CircleAnnotation) return "Ellipse";
        return "Annotation";
    }

    private static Tab tab(String name, Node content) {
        Tab tab = new Tab(name, content);
        tab.setClosable(false);
        return tab;
    }

    private static Color fx(java.awt.Color c) {
        return c == null ? Color.BLACK : Color.rgb(c.getRed(), c.getGreen(), c.getBlue());
    }

    private static java.awt.Color awt(Color c) {
        return new java.awt.Color((float) c.getRed(), (float) c.getGreen(), (float) c.getBlue());
    }

    private static boolean same(java.awt.Color a, java.awt.Color b) {
        return a != null && b != null && (a.getRGB() & 0xFFFFFF) == (b.getRGB() & 0xFFFFFF);
    }

    /** The controls for one annotation, and the read-back that keeps only what changed. */
    private static final class Fields {
        private final Annotation annotation;
        private final AnnotationProperties before;
        private ColorPicker color, fillColor, fontColor;
        private Slider opacity;
        private CheckBox fillEnabled, print, noZoom, noRotate, readOnly;
        private Spinner<Double> width;
        private ComboBox<String> style, startArrow, endArrow, font, icon;
        private Spinner<Integer> fontSize;

        Fields(Annotation annotation, AnnotationProperties before) {
            this.annotation = annotation;
            this.before = before;
        }

        Node appearance() {
            GridPane grid = grid();
            int row = 0;
            if (AnnotationProperties.hasColor(annotation)) {
                color = new ColorPicker(fx(before.color()));
                grid.addRow(row++, new Label(annotation instanceof FreeTextAnnotation ? "Border colour:" : "Colour:"), color);
            }
            if (annotation instanceof FreeTextAnnotation) {
                font = new ComboBox<>();
                font.getItems().addAll(FONTS);
                font.setValue(before.fontName());
                font.setEditable(true);
                fontSize = new Spinner<>(4, 200, Math.round(before.fontSize() != null ? before.fontSize() : 12));
                fontColor = new ColorPicker(fx(before.fontColor()));
                grid.addRow(row++, new Label("Font:"), font);
                grid.addRow(row++, new Label("Size:"), fontSize);
                grid.addRow(row++, new Label("Text colour:"), fontColor);
            }
            if (before.fillEnabled() != null || annotation instanceof LineAnnotation) {
                fillColor = new ColorPicker(fx(before.fillColor()));
                if (before.fillEnabled() != null) {
                    fillEnabled = new CheckBox("Fill");
                    fillEnabled.setSelected(before.fillEnabled());
                    fillColor.disableProperty().bind(fillEnabled.selectedProperty().not());
                    grid.addRow(row++, fillEnabled, fillColor);
                } else {
                    grid.addRow(row++, new Label("Interior colour:"), fillColor);
                }
            }
            if (before.startArrow() != null) {
                startArrow = endings(before.startArrow());
                endArrow = endings(before.endArrow());
                grid.addRow(row++, new Label("Start:"), startArrow);
                grid.addRow(row++, new Label("End:"), endArrow);
            }
            if (borderApplies()) {
                width = new Spinner<>(0.0, 50.0, before.borderWidth() != null ? before.borderWidth() : 1.0, 0.5);
                style = new ComboBox<>();
                style.getItems().addAll(STYLES.keySet());
                style.setValue(styleLabel(before.borderStyle()));
                grid.addRow(row++, new Label("Border width:"), width);
                grid.addRow(row++, new Label("Border style:"), style);
            }
            if (before.iconName() != null) {
                icon = new ComboBox<>();
                icon.getItems().addAll(ICONS);
                icon.setValue(before.iconName().getName());
                grid.addRow(row++, new Label("Icon:"), icon);
            }
            if (annotation instanceof MarkupAnnotation && before.opacity() != null) {
                opacity = new Slider(0, 100, Math.round(before.opacity() * 100));
                opacity.setShowTickLabels(true);
                opacity.setMajorTickUnit(25);
                grid.addRow(row, new Label("Opacity (%):"), opacity);
            }
            return grid;
        }

        Node flags() {
            GridPane grid = grid();
            print = new CheckBox("Print");
            print.setSelected(Boolean.TRUE.equals(before.print()));
            noZoom = new CheckBox("Keep size when zooming");
            noZoom.setSelected(Boolean.TRUE.equals(before.noZoom()));
            noRotate = new CheckBox("Keep orientation when rotating");
            noRotate.setSelected(Boolean.TRUE.equals(before.noRotate()));
            readOnly = new CheckBox("Read only (cannot be edited afterwards)");
            readOnly.setSelected(Boolean.TRUE.equals(before.readOnly()));
            grid.addRow(0, print);
            grid.addRow(1, noZoom);
            grid.addRow(2, noRotate);
            grid.addRow(3, readOnly);
            return grid;
        }

        private boolean borderApplies() {
            return annotation instanceof InkAnnotation || annotation instanceof LineAnnotation
                    || annotation instanceof SquareAnnotation || annotation instanceof CircleAnnotation
                    || annotation instanceof FreeTextAnnotation;
        }

        /** What the user changed; every other component stays null so it is left alone. */
        AnnotationProperties result() {
            java.awt.Color newColor = color != null && !same(awt(color.getValue()), before.color())
                    ? awt(color.getValue()) : null;
            java.awt.Color newFill = fillColor != null && !same(awt(fillColor.getValue()), before.fillColor())
                    ? awt(fillColor.getValue()) : null;
            Boolean newFillOn = fillEnabled != null && fillEnabled.isSelected() != before.fillEnabled()
                    ? fillEnabled.isSelected() : null;
            Float newOpacity = opacity != null && Math.abs(opacity.getValue() / 100 - before.opacity()) > 0.004
                    ? (float) (opacity.getValue() / 100) : null;
            Float newWidth = width != null && Math.abs(width.getValue() - orZero(before.borderWidth())) > 0.001
                    ? width.getValue().floatValue() : null;
            Name newStyle = style != null && !style.getValue().equals(styleLabel(before.borderStyle()))
                    ? STYLES.get(style.getValue()) : null;
            Name newStart = startArrow != null && !startArrow.getValue().equals(before.startArrow().getName())
                    ? new Name(startArrow.getValue()) : null;
            Name newEnd = endArrow != null && !endArrow.getValue().equals(before.endArrow().getName())
                    ? new Name(endArrow.getValue()) : null;
            String newFont = font != null && font.getValue() != null && !font.getValue().equals(before.fontName())
                    ? font.getValue() : null;
            Float newSize = fontSize != null && fontSize.getValue() != Math.round(before.fontSize())
                    ? fontSize.getValue().floatValue() : null;
            java.awt.Color newFontColor = fontColor != null && !same(awt(fontColor.getValue()), before.fontColor())
                    ? awt(fontColor.getValue()) : null;
            Name newIcon = icon != null && icon.getValue() != null
                    && !icon.getValue().equals(before.iconName().getName()) ? new Name(icon.getValue()) : null;
            return new AnnotationProperties(newColor, newFill, newFillOn, newOpacity, newWidth, newStyle, newStart,
                    newEnd, newFont, newSize, newFontColor, newIcon,
                    changed(print, before.print()), changed(noZoom, before.noZoom()),
                    changed(noRotate, before.noRotate()), changed(readOnly, before.readOnly()));
        }

        private static float orZero(Float value) {
            return value != null ? value : 0f;
        }

        private static Boolean changed(CheckBox box, Boolean was) {
            return box != null && box.isSelected() != Boolean.TRUE.equals(was) ? box.isSelected() : null;
        }

        private static ComboBox<String> endings(Name current) {
            ComboBox<String> box = new ComboBox<>();
            box.getItems().addAll("None", "OpenArrow", "ClosedArrow", "Diamond", "Square", "Circle");
            box.setValue(current != null ? current.getName() : "None");
            return box;
        }

        private static GridPane grid() {
            GridPane grid = new GridPane();
            grid.setHgap(10);
            grid.setVgap(8);
            grid.setPadding(new Insets(12));
            return grid;
        }
    }

    private static final Map<String, Name> STYLES = new LinkedHashMap<>();

    static {
        STYLES.put("Solid", BorderStyle.BORDER_STYLE_SOLID);
        STYLES.put("Dashed", BorderStyle.BORDER_STYLE_DASHED);
        STYLES.put("Beveled", BorderStyle.BORDER_STYLE_BEVELED);
        STYLES.put("Inset", BorderStyle.BORDER_STYLE_INSET);
        STYLES.put("Underline", BorderStyle.BORDER_STYLE_UNDERLINE);
    }

    private static String styleLabel(Name style) {
        for (Map.Entry<String, Name> e : STYLES.entrySet()) {
            if (e.getValue().equals(style)) return e.getKey();
        }
        return "Solid";
    }
}
