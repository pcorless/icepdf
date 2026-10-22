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

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A hand-drawn signature: strokes of points in the drawing pad's space, plus the colour and line
 * weight to draw them with.  Kept as strokes rather than pixels so the colour and weight can change
 * after drawing and the image is made at whatever resolution the field needs.
 * <p>
 * {@link #format()} / {@link #parse} are a small text form, one stroke per line of "x,y" pairs, for
 * keeping the signature between sessions.
 */
public final class Ink {

    private final List<List<double[]>> strokes = new ArrayList<>();
    private Color color = new Color(0x1A237E);
    private double width = 2.5;

    public List<List<double[]>> strokes() {
        return strokes;
    }

    public boolean isEmpty() {
        return strokes.stream().allMatch(List::isEmpty);
    }

    public void clear() {
        strokes.clear();
    }

    public Color getColor() {
        return color;
    }

    public void setColor(Color color) {
        this.color = color != null ? color : Color.BLACK;
    }

    /** Line weight in pad units (the pad is drawn 1:1). */
    public double getWidth() {
        return width;
    }

    public void setWidth(double width) {
        this.width = Math.max(0.5, width);
    }

    /** The strokes as one smoothed path: quadratic curves through the midpoints of each pair of points. */
    public Path2D toPath() {
        Path2D.Double path = new Path2D.Double();
        for (List<double[]> stroke : strokes) {
            if (stroke.isEmpty()) continue;
            double[] p0 = stroke.get(0);
            path.moveTo(p0[0], p0[1]);
            if (stroke.size() == 1) {
                // a dot: a zero-length line still gets the round cap.
                path.lineTo(p0[0], p0[1]);
                continue;
            }
            for (int i = 1; i < stroke.size() - 1; i++) {
                double[] p = stroke.get(i);
                double[] n = stroke.get(i + 1);
                path.quadTo(p[0], p[1], (p[0] + n[0]) / 2, (p[1] + n[1]) / 2);
            }
            double[] last = stroke.get(stroke.size() - 1);
            path.lineTo(last[0], last[1]);
        }
        return path;
    }

    public BasicStroke stroke() {
        return new BasicStroke((float) width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
    }

    /**
     * The signature cropped to its ink, on a transparent background, {@code scale} pixels per pad
     * unit; null when nothing is drawn.
     */
    public BufferedImage toImage(double scale) {
        if (isEmpty()) return null;
        Path2D path = toPath();
        Rectangle2D bounds = stroke().createStrokedShape(path).getBounds2D();
        int w = Math.max(1, (int) Math.ceil(bounds.getWidth() * scale));
        int h = Math.max(1, (int) Math.ceil(bounds.getHeight() * scale));
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g.scale(scale, scale);
            g.translate(-bounds.getX(), -bounds.getY());
            g.setColor(color);
            g.setStroke(stroke());
            g.draw(path);
        } finally {
            g.dispose();
        }
        return image;
    }

    /** The strokes as text: one line per stroke, space separated "x,y" pairs. */
    public String format() {
        StringBuilder out = new StringBuilder();
        for (List<double[]> stroke : strokes) {
            if (stroke.isEmpty()) continue;
            for (double[] p : stroke) {
                out.append(String.format(Locale.ROOT, "%.1f,%.1f ", p[0], p[1]));
            }
            out.setLength(out.length() - 1);
            out.append('\n');
        }
        return out.toString();
    }

    /** Replaces the strokes with those in {@code text} ({@link #format()}); bad pairs are skipped. */
    public void parse(String text) {
        strokes.clear();
        if (text == null) return;
        for (String line : text.split("\\R")) {
            List<double[]> stroke = new ArrayList<>();
            for (String pair : line.trim().split("\\s+")) {
                int comma = pair.indexOf(',');
                if (comma < 0) continue;
                try {
                    stroke.add(new double[]{Double.parseDouble(pair.substring(0, comma)),
                            Double.parseDouble(pair.substring(comma + 1))});
                } catch (NumberFormatException ignored) {
                    // a hand-edited bad pair
                }
            }
            if (!stroke.isEmpty()) strokes.add(stroke);
        }
    }
}
