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

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Named annotation colours ("Approved", "To do", ...): where the application keeps the user's
 * colour labels.  A label gives a colour a meaning; the annotation menus offer the labels first,
 * and the annotation board files each comment under the label its colour matches.  Labels belong
 * to the user, not to a document.
 */
public interface ColorLabels {

    /**
     * System property: how far, per red/green/blue channel (0-255), an annotation's colour may be
     * from a label's and still match it, {@value #DEFAULT_TOLERANCE} by default.  Annotations made
     * in other tools are often a few units off.
     */
    String TOLERANCE_PROPERTY = "org.icepdf.fx.annotation.labelTolerance";
    int DEFAULT_TOLERANCE = 8;

    /** One label: an opaque colour and its name. */
    record Label(Color color, String name) {
        public Label {
            Objects.requireNonNull(color, "color");
            color = new Color(color.getRGB() & 0xFFFFFF);
            name = name == null ? "" : name.strip();
        }
    }

    /** The labels, in the user's order. */
    List<Label> labels();

    /** Replaces the labels. */
    void setLabels(List<Label> labels);

    /** The match tolerance: {@link #TOLERANCE_PROPERTY}, 0 to 255. */
    static int tolerance() {
        return Math.max(0, Math.min(255, Integer.getInteger(TOLERANCE_PROPERTY, DEFAULT_TOLERANCE)));
    }

    /**
     * The label {@code color} belongs to: the nearest within {@link #tolerance()} on every channel
     * (the first in order on a tie), or null for none or a null colour.
     */
    static Label match(List<Label> labels, Color color) {
        return match(labels, color, tolerance());
    }

    /** As {@link #match(List, Color)}, with a given tolerance. */
    static Label match(List<Label> labels, Color color, int tolerance) {
        if (color == null || labels == null) return null;
        Label best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (Label label : labels) {
            Color c = label.color();
            int dr = Math.abs(c.getRed() - color.getRed());
            int dg = Math.abs(c.getGreen() - color.getGreen());
            int db = Math.abs(c.getBlue() - color.getBlue());
            if (dr > tolerance || dg > tolerance || db > tolerance) continue;
            int distance = dr + dg + db;
            if (distance < bestDistance) {
                best = label;
                bestDistance = distance;
            }
        }
        return best;
    }

    /** Labels that live only as long as the viewer, for when the application doesn't supply a store. */
    static ColorLabels inMemory() {
        return new ColorLabels() {
            private List<Label> labels = List.of();

            @Override
            public List<Label> labels() {
                return labels;
            }

            @Override
            public void setLabels(List<Label> labels) {
                this.labels = labels == null ? List.of() : List.copyOf(labels);
            }
        };
    }

    /**
     * The Swing viewer's colour labels as it stored them (its java.util.prefs node), for a one-time
     * import; null when there are none or they can't be read.
     */
    static String swingPreference() {
        try {
            java.util.prefs.Preferences node = java.util.prefs.Preferences.userRoot();
            return node.nodeExists("/org/icepdf/ri/util")
                    ? node.node("/org/icepdf/ri/util").get(SWING_KEY, null) : null;
        } catch (java.util.prefs.BackingStoreException | RuntimeException e) {
            return null;
        }
    }

    /** The Swing viewer's preference key for its colour labels. */
    String SWING_KEY = "application.viewer.preference.annotation.recent.color.labels";

    /**
     * Reads the Swing viewer's colour labels value ({@code rgb|label|rgb|label...}, the
     * {@code application.viewer.preference.annotation.recent.color.labels} preference); bad pairs
     * are skipped.
     */
    static List<Label> parseSwing(String value) {
        List<Label> labels = new ArrayList<>();
        if (value == null || value.isBlank()) return labels;
        String[] tokens = value.split("\\|", -1);
        for (int i = 0; i + 1 < tokens.length; i += 2) {
            try {
                labels.add(new Label(new Color(Integer.parseInt(tokens[i].trim())), tokens[i + 1]));
            } catch (NumberFormatException ignored) {
                // a hand-edited bad pair
            }
        }
        return labels;
    }
}
