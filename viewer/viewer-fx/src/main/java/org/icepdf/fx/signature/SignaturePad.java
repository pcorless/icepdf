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
package org.icepdf.fx.signature;

import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;

import java.util.ArrayList;
import java.util.List;

/**
 * A small pad to draw a signature on with the mouse, pen or finger: a colour, a line weight and
 * Clear.  It edits an {@link Ink}; {@link #setOnChanged} hears about every finished stroke and
 * every colour or weight change.
 */
public class SignaturePad extends VBox {

    public static final double PAD_WIDTH = 360;
    public static final double PAD_HEIGHT = 120;

    private final Ink ink;
    private final Canvas canvas = new Canvas(PAD_WIDTH, PAD_HEIGHT);
    private final Label hint = new Label("Sign here");
    private List<double[]> current;
    private Runnable onChanged = () -> { };

    public SignaturePad(Ink ink) {
        this.ink = ink;
        getStyleClass().add("pdf-signature-pad");
        setSpacing(6);

        hint.setStyle("-fx-text-fill: #9e9e9e; -fx-font-size: 16px;");
        hint.setMouseTransparent(true);
        StackPane surface = new StackPane(canvas, hint);
        surface.setStyle("-fx-background-color: white; -fx-border-color: #bdbdbd;");
        surface.setMaxWidth(Region.USE_PREF_SIZE);
        surface.setCursor(javafx.scene.Cursor.CROSSHAIR);

        ColorPicker color = new ColorPicker(toFx(ink.getColor()));
        color.getStyleClass().add(ColorPicker.STYLE_CLASS_BUTTON);
        color.setTooltip(new Tooltip("Ink colour"));
        color.valueProperty().addListener((o, a, b) -> {
            ink.setColor(toAwt(b));
            changed();
        });
        Slider weight = new Slider(1, 6, ink.getWidth());
        weight.setPrefWidth(110);
        weight.setTooltip(new Tooltip("Line weight"));
        weight.valueProperty().addListener((o, a, b) -> {
            ink.setWidth(b.doubleValue());
            changed();
        });
        Button clear = new Button("Clear");
        clear.setOnAction(e -> {
            ink.clear();
            changed();
        });
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox controls = new HBox(8, color, new Label("Weight"), weight, spacer, clear);
        controls.setAlignment(Pos.CENTER_LEFT);
        controls.setMaxWidth(PAD_WIDTH);

        canvas.setOnMousePressed(e -> {
            if (e.getButton() != MouseButton.PRIMARY) return;
            current = new ArrayList<>();
            ink.strokes().add(current);
            add(e.getX(), e.getY());
        });
        canvas.setOnMouseDragged(e -> {
            if (current != null) add(e.getX(), e.getY());
        });
        canvas.setOnMouseReleased(e -> {
            if (current == null) return;
            current = null;
            changed();
        });

        getChildren().addAll(surface, controls);
        redraw();
    }

    /** Called after a stroke, a colour or weight change, or Clear. */
    public void setOnChanged(Runnable onChanged) {
        this.onChanged = onChanged != null ? onChanged : () -> { };
    }

    public Ink getInk() {
        return ink;
    }

    private void add(double x, double y) {
        x = Math.max(0, Math.min(PAD_WIDTH, x));
        y = Math.max(0, Math.min(PAD_HEIGHT, y));
        if (!current.isEmpty()) {
            double[] last = current.get(current.size() - 1);
            // skip jitter: points closer than a pixel add nothing but size.
            if (Math.hypot(x - last[0], y - last[1]) < 1) return;
        }
        current.add(new double[]{x, y});
        redraw();
    }

    private void changed() {
        redraw();
        onChanged.run();
    }

    private void redraw() {
        GraphicsContext g = canvas.getGraphicsContext2D();
        g.clearRect(0, 0, PAD_WIDTH, PAD_HEIGHT);
        // a signing line, as on paper.
        g.setStroke(Color.web("#e0e0e0"));
        g.setLineWidth(1);
        g.strokeLine(16, PAD_HEIGHT - 28.5, PAD_WIDTH - 16, PAD_HEIGHT - 28.5);
        g.setStroke(toFx(ink.getColor()));
        g.setLineWidth(ink.getWidth());
        g.setLineCap(StrokeLineCap.ROUND);
        g.setLineJoin(StrokeLineJoin.ROUND);
        for (List<double[]> stroke : ink.strokes()) {
            if (stroke.isEmpty()) continue;
            g.beginPath();
            double[] p0 = stroke.get(0);
            g.moveTo(p0[0], p0[1]);
            if (stroke.size() == 1) g.lineTo(p0[0], p0[1]);
            for (int i = 1; i < stroke.size() - 1; i++) {
                double[] p = stroke.get(i);
                double[] n = stroke.get(i + 1);
                g.quadraticCurveTo(p[0], p[1], (p[0] + n[0]) / 2, (p[1] + n[1]) / 2);
            }
            if (stroke.size() > 1) {
                double[] last = stroke.get(stroke.size() - 1);
                g.lineTo(last[0], last[1]);
            }
            g.stroke();
        }
        hint.setVisible(ink.isEmpty());
    }

    private static Color toFx(java.awt.Color c) {
        return Color.rgb(c.getRed(), c.getGreen(), c.getBlue());
    }

    private static java.awt.Color toAwt(Color c) {
        return new java.awt.Color((float) c.getRed(), (float) c.getGreen(), (float) c.getBlue());
    }
}
