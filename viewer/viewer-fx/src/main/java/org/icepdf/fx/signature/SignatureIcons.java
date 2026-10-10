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

import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Polyline;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;

/**
 * Validity icons drawn with shapes (no image resources): a green tick for valid, an amber warning
 * for an unknown identity, a red cross for invalid, a grey question mark when unchecked.
 */
public final class SignatureIcons {

    public static final Color VALID = Color.rgb(46, 125, 50);
    public static final Color UNKNOWN = Color.rgb(230, 160, 0);
    public static final Color INVALID = Color.rgb(198, 40, 40);
    public static final Color OTHER = Color.rgb(120, 120, 120);
    public static final Color SIGN_HERE = Color.rgb(21, 101, 192);

    private SignatureIcons() {
    }

    /** The colour of a verdict. */
    public static Color color(SignatureStatus.Verdict verdict) {
        return switch (verdict) {
            case VALID -> VALID;
            case UNKNOWN -> UNKNOWN;
            case INVALID -> INVALID;
            case UNSIGNED -> SIGN_HERE;
            default -> OTHER;
        };
    }

    /** An icon {@code size} px square for a verdict. */
    public static Node icon(SignatureStatus.Verdict verdict, double size) {
        double s = size;
        double c = s / 2;
        Group icon = new Group();
        icon.getStyleClass().addAll("pdf-signature-icon", "pdf-signature-" + verdict.name().toLowerCase());
        if (verdict == SignatureStatus.Verdict.UNKNOWN) {
            Polygon triangle = new Polygon(c, s * 0.04, s * 0.98, s * 0.94, s * 0.02, s * 0.94);
            triangle.setFill(UNKNOWN);
            triangle.setStroke(Color.WHITE);
            triangle.setStrokeWidth(Math.max(0.5, s / 20));
            triangle.setStrokeLineJoin(StrokeLineJoin.ROUND);
            icon.getChildren().addAll(triangle, stroke(new Line(c, s * 0.36, c, s * 0.64), s),
                    dot(c, s * 0.79, s));
            return icon;
        }
        Circle disc = new Circle(c, c, c * 0.96, color(verdict));
        disc.setStroke(Color.WHITE);
        disc.setStrokeWidth(Math.max(0.5, s / 20));
        icon.getChildren().add(disc);
        switch (verdict) {
            case VALID -> icon.getChildren().add(stroke(new Polyline(s * 0.28, s * 0.52, s * 0.44, s * 0.68,
                    s * 0.73, s * 0.34), s));
            case INVALID -> icon.getChildren().addAll(stroke(new Line(s * 0.33, s * 0.33, s * 0.67, s * 0.67), s),
                    stroke(new Line(s * 0.67, s * 0.33, s * 0.33, s * 0.67), s));
            // a pen over a signing line: "sign here".
            case UNSIGNED -> icon.getChildren().addAll(stroke(new Line(s * 0.34, s * 0.62, s * 0.66, s * 0.30), s),
                    stroke(new Line(s * 0.27, s * 0.72, s * 0.73, s * 0.72), s));
            default -> icon.getChildren().addAll(stroke(new Polyline(s * 0.36, s * 0.38, s * 0.42, s * 0.27,
                    s * 0.58, s * 0.27, s * 0.64, s * 0.38, s * 0.5, s * 0.5, s * 0.5, s * 0.6), s), dot(c, s * 0.76, s));
        }
        return icon;
    }

    private static javafx.scene.shape.Shape stroke(javafx.scene.shape.Shape shape, double size) {
        shape.setFill(null);
        shape.setStroke(Color.WHITE);
        shape.setStrokeWidth(Math.max(1, size / 8));
        shape.setStrokeLineCap(StrokeLineCap.ROUND);
        shape.setStrokeLineJoin(StrokeLineJoin.ROUND);
        return shape;
    }

    private static Circle dot(double x, double y, double size) {
        return new Circle(x, y, Math.max(0.6, size / 14), Color.WHITE);
    }
}
