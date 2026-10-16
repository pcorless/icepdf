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
import org.icepdf.fx.view.AnnotationProperties;
import org.icepdf.fx.view.PdfView;

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
        ColorHistory history = colors != null ? colors : ColorHistory.inMemory();
        view.setAnnotationMenuFactory(annotation -> create(view, annotation, history, target -> {
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
        ContextMenu menu = new ContextMenu();
        if (AnnotationProperties.hasColor(annotation)) menu.getItems().add(colourMenu(view, annotation, colors));
        if (AnnotationProperties.supports(annotation) && properties != null) {
            MenuItem item = new MenuItem(Messages.get("annotationMenu.properties"));
            item.setOnAction(e -> properties.accept(annotation));
            menu.getItems().add(item);
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

    static Menu colourMenu(PdfView view, Annotation annotation, ColorHistory colors) {
        Menu menu = new Menu(Messages.get("annotationMenu.changeColour"));
        java.awt.Color current = annotation.getColor();
        int currentRgb = current != null ? current.getRGB() & 0xFFFFFF : -1;
        for (int i = 0; i < STANDARD.length; i++) {
            menu.getItems().add(swatch(Messages.get("annotationMenu.colour." + STANDARD_NAMES[i]), STANDARD[i], currentRgb, view, annotation, colors));
        }
        List<java.awt.Color> recent = colors != null ? colors.recent() : List.of();
        List<java.awt.Color> shown = recent.stream().filter(c -> (c.getRGB() & 0xFFFFFF) != currentRgb).toList();
        if (!shown.isEmpty()) {
            menu.getItems().add(new SeparatorMenuItem());
            for (java.awt.Color c : shown) {
                menu.getItems().add(swatch(String.format("#%06X", c.getRGB() & 0xFFFFFF), c, currentRgb, view,
                        annotation, colors));
            }
        }
        MenuItem more = new MenuItem(Messages.get("annotationMenu.moreColours"));
        more.setOnAction(e -> chooseColour(view, annotation, colors, current));
        menu.getItems().addAll(new SeparatorMenuItem(), more);
        return menu;
    }

    private static MenuItem swatch(String label, java.awt.Color color, int currentRgb, PdfView view,
                                   Annotation annotation, ColorHistory colors) {
        MenuItem item = new MenuItem(label);
        Rectangle box = new Rectangle(14, 14, Color.rgb(color.getRed(), color.getGreen(), color.getBlue()));
        box.setStroke(Color.gray(0, 0.35));
        item.setGraphic(box);
        item.setDisable((color.getRGB() & 0xFFFFFF) == currentRgb);
        item.setOnAction(e -> apply(view, annotation, colors, color));
        return item;
    }

    private static void apply(PdfView view, Annotation annotation, ColorHistory colors, java.awt.Color color) {
        if (AnnotationProperties.hasColor(annotation)) {
            view.applyAnnotationProperties(annotation, AnnotationProperties.ofColor(color));
        }
        if (colors != null) colors.used(color);
    }

    /** Asks for any colour with the colour picker's own custom-colour editor. */
    private static void chooseColour(PdfView view, Annotation annotation, ColorHistory colors, java.awt.Color current) {
        ColorPicker picker = new ColorPicker(current != null
                ? Color.rgb(current.getRed(), current.getGreen(), current.getBlue()) : Color.RED);
        Dialog<Color> dialog = new Dialog<>();
        dialog.setTitle(Messages.get("annotationMenu.colourTitle"));
        dialog.getDialogPane().setContent(new StackPane(picker));
        dialog.getDialogPane().getButtonTypes().addAll(javafx.scene.control.ButtonType.OK,
                javafx.scene.control.ButtonType.CANCEL);
        dialog.setResultConverter(b -> b == javafx.scene.control.ButtonType.OK ? picker.getValue() : null);
        Window owner = view.getScene() != null ? view.getScene().getWindow() : null;
        if (owner != null) dialog.initOwner(owner);
        Optional<Color> chosen = dialog.showAndWait();
        chosen.ifPresent(c -> apply(view, annotation, colors, new java.awt.Color((float) c.getRed(),
                (float) c.getGreen(), (float) c.getBlue())));
    }
}
