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

import org.icepdf.fx.i18n.Messages;

import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Dialog;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.Window;
import org.icepdf.core.pobjects.annotations.Annotation;
import org.icepdf.core.pobjects.annotations.CircleAnnotation;
import org.icepdf.core.pobjects.annotations.FreeTextAnnotation;
import org.icepdf.core.pobjects.annotations.InkAnnotation;
import org.icepdf.core.pobjects.annotations.LineAnnotation;
import org.icepdf.core.pobjects.annotations.MarkupAnnotation;
import org.icepdf.core.pobjects.annotations.SquareAnnotation;
import org.icepdf.core.pobjects.annotations.TextAnnotation;
import org.icepdf.core.pobjects.annotations.TextMarkupAnnotation;
import org.icepdf.fx.panels.AnnotationSummary;
import org.icepdf.fx.view.AnnotationProperties;
import org.icepdf.fx.view.PdfView;
import org.icepdf.fx.view.ToolMode;

import java.util.List;
import java.util.Optional;

/**
 * The right-click menu of an annotation: change its colour from the standard colours, the ones
 * used lately or a new one; delete it; and (when the application supplies a dialog) its properties.
 * Every change goes through {@link PdfView} so it is one undoable edit.
 */
public final class AnnotationContextMenu {

    /** The ten standard colours of the Swing viewer's colour drop-down, with names. */
    static final String[] STANDARD_NAMES = {"darkRed", "red", "orange", "yellow", "lightGreen", "green", "lightBlue",
            "blue", "darkBlue", "purple"};
    static final java.awt.Color[] STANDARD = {
            new java.awt.Color(192, 0, 0), new java.awt.Color(255, 0, 0), new java.awt.Color(255, 192, 0),
            new java.awt.Color(255, 255, 0), new java.awt.Color(146, 208, 80), new java.awt.Color(0, 176, 80),
            new java.awt.Color(0, 176, 240), new java.awt.Color(0, 112, 192), new java.awt.Color(32, 76, 112),
            new java.awt.Color(112, 48, 160)};

    private AnnotationContextMenu() {
    }

    /**
     * Gives a view this menu, with the {@link AnnotationPropertiesDialog} for Properties…: right-click
     * an annotation the document lets the user edit.
     *
     * @param colors the colours picked lately; null keeps them for this view only
     */
    public static void install(PdfView view, ColorHistory colors) {
        install(view, colors, null);
    }

    /**
     * As {@link #install(PdfView, ColorHistory)}, with "Use as Default" for an annotation a drawing
     * tool makes: its colour becomes the tool's in {@code toolColors}.
     *
     * @param toolColors the drawing tools' colours; null leaves the item out
     */
    public static void install(PdfView view, ColorHistory colors,
                               javafx.collections.ObservableMap<ToolMode, Color> toolColors) {
        ColorHistory history = colors != null ? colors : ColorHistory.inMemory();
        view.setAnnotationMenuFactory(annotation -> create(view, annotation, history, toolColors, target -> {
            Window owner = view.getScene() != null ? view.getScene().getWindow() : null;
            AnnotationPropertiesDialog.show(owner, target).ifPresent(change -> view.applyAnnotationProperties(target, change));
        }));
    }

    /**
     * Builds the menu for {@code annotation}, or null when nothing in it applies.
     *
     * @param properties shows the properties dialog for the annotation; null leaves the item out
     */
    public static ContextMenu create(PdfView view, Annotation annotation, ColorHistory colors,
                                     java.util.function.Consumer<Annotation> properties) {
        return create(view, annotation, colors, null, properties);
    }

    /**
     * Builds the menu for {@code annotation}, or null when nothing in it applies.
     *
     * @param toolColors the drawing tools' colours, for "Use as Default"; null leaves the item out
     * @param properties shows the properties dialog for the annotation; null leaves the item out
     */
    public static ContextMenu create(PdfView view, Annotation annotation, ColorHistory colors,
                                     javafx.collections.ObservableMap<ToolMode, Color> toolColors,
                                     java.util.function.Consumer<Annotation> properties) {
        ContextMenu menu = new ContextMenu();
        if (AnnotationProperties.hasColor(annotation)) menu.getItems().add(colourMenu(view, annotation, colors));
        MenuItem useAsDefault = toolColors != null ? useAsDefault(annotation, toolColors) : null;
        if (useAsDefault != null) menu.getItems().add(useAsDefault);
        if (AnnotationProperties.supports(annotation) && properties != null) {
            MenuItem item = new MenuItem(Messages.get("annotationMenu.properties"));
            item.setOnAction(e -> properties.accept(annotation));
            menu.getItems().add(item);
        }
        if (AnnotationSummary.privacyEnabled() && AnnotationSummary.canBePrivate(annotation)) {
            menu.getItems().add(privateItem(view, (MarkupAnnotation) annotation));
        }
        MenuItem delete = new MenuItem(Messages.get("annotationMenu.delete"));
        delete.setOnAction(e -> {
            view.selectAnnotation(annotation);
            view.deleteSelectedAnnotation();
        });
        if (!menu.getItems().isEmpty()) menu.getItems().add(new SeparatorMenuItem());
        menu.getItems().add(delete);
        return menu;
    }

    /** "Private", ticked when it is: marks the comment and its replies private or public. */
    static CheckMenuItem privateItem(PdfView view, MarkupAnnotation markup) {
        CheckMenuItem item = new CheckMenuItem(Messages.get("annotationMenu.private"));
        item.setSelected(markup.getFlagPrivateContents());
        item.setDisable(!view.isAnnotationEditingAllowed());
        item.setOnAction(e -> view.setAnnotationsPrivate(AnnotationSummary.withReplies(markup), item.isSelected()));
        return item;
    }

    static Menu colourMenu(PdfView view, Annotation annotation, ColorHistory colors) {
        Menu menu = new Menu(Messages.get("annotationMenu.changeColour"));
        menu.getItems().setAll(colourItems(annotation.getColor(), colors, owner(view), color -> {
            if (AnnotationProperties.hasColor(annotation)) {
                view.applyAnnotationProperties(annotation, AnnotationProperties.ofColor(color));
            }
        }));
        return menu;
    }

    /**
     * "Use as Default for Highlight" (and so on): the annotation's colour becomes the colour of the
     * tool that makes its kind.  Null for an annotation no tool makes, or one without a colour;
     * disabled when the tool has that colour already.
     */
    static MenuItem useAsDefault(Annotation annotation, javafx.collections.ObservableMap<ToolMode, Color> toolColors) {
        ToolMode mode = toolFor(annotation);
        java.awt.Color awt = annotation instanceof FreeTextAnnotation text ? text.getFontColor() : annotation.getColor();
        if (mode == null || awt == null) return null;
        Color colour = fx(awt);
        MenuItem item = new MenuItem(Messages.format("annotationMenu.useAsDefault",
                Messages.get(org.icepdf.fx.ri.actions.tools.ToolModeAction.id(mode))));
        Rectangle box = new Rectangle(14, 14, colour);
        box.setStroke(Color.gray(0, 0.35));
        item.setGraphic(box);
        Color current = toolColors.get(mode);
        item.setDisable(colour.equals(current != null ? current : PdfView.defaultToolColor(mode)));
        item.setOnAction(e -> toolColors.put(mode, colour));
        return item;
    }

    /** The drawing tool that makes annotations like {@code annotation}, or null. */
    static ToolMode toolFor(Annotation annotation) {
        if (annotation instanceof TextMarkupAnnotation) {
            org.icepdf.core.pobjects.Name subtype = annotation.getSubType();
            if (TextMarkupAnnotation.SUBTYPE_HIGHLIGHT.equals(subtype)) return ToolMode.HIGHLIGHT;
            if (TextMarkupAnnotation.SUBTYPE_UNDERLINE.equals(subtype)) return ToolMode.UNDERLINE;
            if (TextMarkupAnnotation.SUBTYPE_STRIKE_OUT.equals(subtype)) return ToolMode.STRIKE_OUT;
            if (TextMarkupAnnotation.SUBTYPE_SQUIGGLY.equals(subtype)) return ToolMode.SQUIGGLY;
            return null;
        }
        if (annotation instanceof TextAnnotation) return ToolMode.NOTE;
        if (annotation instanceof FreeTextAnnotation) return ToolMode.FREE_TEXT;
        if (annotation instanceof InkAnnotation) return ToolMode.INK;
        if (annotation instanceof SquareAnnotation) return ToolMode.RECTANGLE;
        if (annotation instanceof CircleAnnotation) return ToolMode.ELLIPSE;
        if (annotation instanceof LineAnnotation) return ToolMode.LINE;
        return null;
    }

    /**
     * The menu of a drawing tool's button: the colour its new annotations get, chosen as an
     * annotation's colour is, and back to the tool's default.
     *
     * @param current the tool's colour now
     * @param custom  the user chose {@code current}, so there is a default to go back to
     * @param choose  told the colour picked, or null for the default
     */
    public static ContextMenu toolColourMenu(Color current, boolean custom, ColorHistory colors, Window owner,
                                             java.util.function.Consumer<Color> choose) {
        ContextMenu menu = new ContextMenu();
        menu.getItems().setAll(colourItems(awt(current), colors, owner, c -> choose.accept(fx(c))));
        MenuItem reset = new MenuItem(Messages.get("annotationMenu.defaultColour"));
        reset.setDisable(!custom);
        reset.setOnAction(e -> choose.accept(null));
        menu.getItems().addAll(new SeparatorMenuItem(), reset);
        return menu;
    }

    /**
     * The standard colours, the recent ones and More Colours…; picking one tells {@code choose} and
     * puts it in the history.  {@code current} is shown disabled.
     */
    static List<MenuItem> colourItems(java.awt.Color current, ColorHistory colors, Window owner,
                                      java.util.function.Consumer<java.awt.Color> choose) {
        java.util.function.Consumer<java.awt.Color> pick = color -> {
            choose.accept(color);
            if (colors != null) colors.used(color);
        };
        List<MenuItem> items = new java.util.ArrayList<>();
        int currentRgb = current != null ? current.getRGB() & 0xFFFFFF : -1;
        for (int i = 0; i < STANDARD.length; i++) {
            items.add(swatch(Messages.get("annotationMenu.colour." + STANDARD_NAMES[i]), STANDARD[i], currentRgb, pick));
        }
        List<java.awt.Color> recent = colors != null ? colors.recent() : List.of();
        List<java.awt.Color> shown = recent.stream().filter(c -> (c.getRGB() & 0xFFFFFF) != currentRgb).toList();
        if (!shown.isEmpty()) {
            items.add(new SeparatorMenuItem());
            for (java.awt.Color c : shown) {
                items.add(swatch(String.format("#%06X", c.getRGB() & 0xFFFFFF), c, currentRgb, pick));
            }
        }
        MenuItem more = new MenuItem(Messages.get("annotationMenu.moreColours"));
        more.setOnAction(e -> chooseColour(owner, current).ifPresent(pick));
        items.add(new SeparatorMenuItem());
        items.add(more);
        return items;
    }

    private static MenuItem swatch(String label, java.awt.Color color, int currentRgb,
                                   java.util.function.Consumer<java.awt.Color> pick) {
        MenuItem item = new MenuItem(label);
        Rectangle box = new Rectangle(14, 14, fx(color));
        box.setStroke(Color.gray(0, 0.35));
        item.setGraphic(box);
        item.setDisable((color.getRGB() & 0xFFFFFF) == currentRgb);
        item.setOnAction(e -> pick.accept(color));
        return item;
    }

    /** Asks for any colour with the colour picker's own custom-colour editor. */
    private static Optional<java.awt.Color> chooseColour(Window owner, java.awt.Color current) {
        ColorPicker picker = new ColorPicker(current != null ? fx(current) : Color.RED);
        Dialog<Color> dialog = new Dialog<>();
        dialog.setTitle(Messages.get("annotationMenu.colourTitle"));
        dialog.getDialogPane().setContent(new StackPane(picker));
        dialog.getDialogPane().getButtonTypes().addAll(javafx.scene.control.ButtonType.OK,
                javafx.scene.control.ButtonType.CANCEL);
        dialog.setResultConverter(b -> b == javafx.scene.control.ButtonType.OK ? picker.getValue() : null);
        if (owner != null) dialog.initOwner(owner);
        return dialog.showAndWait().map(AnnotationContextMenu::awt);
    }

    private static Window owner(PdfView view) {
        return view.getScene() != null ? view.getScene().getWindow() : null;
    }

    private static Color fx(java.awt.Color c) {
        return Color.rgb(c.getRed(), c.getGreen(), c.getBlue());
    }

    private static java.awt.Color awt(Color c) {
        return c == null ? null : new java.awt.Color((float) c.getRed(), (float) c.getGreen(), (float) c.getBlue());
    }
}
