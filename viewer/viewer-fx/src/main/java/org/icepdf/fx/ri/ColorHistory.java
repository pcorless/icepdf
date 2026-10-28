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

/** Where the application remembers the annotation colours the user picked; the viewer reports and shows them. */
public interface ColorHistory {

    /** System property: how many recent colours are kept and offered, {@value #DEFAULT_MAX_RECENT} by default. */
    String MAX_RECENT_PROPERTY = "org.icepdf.fx.annotation.recentColors";
    int DEFAULT_MAX_RECENT = 5;

    /** The number of recent colours to keep: {@link #MAX_RECENT_PROPERTY}, at least one. */
    static int maxRecent() {
        return Math.max(1, Integer.getInteger(MAX_RECENT_PROPERTY, DEFAULT_MAX_RECENT));
    }

    /** Recently chosen colours, most recent first. */
    List<Color> recent();

    void used(Color color);

    /** A history that lives only as long as the viewer, for when the application doesn't supply one. */
    static ColorHistory inMemory() {
        return new ColorHistory() {
            private final List<Color> colors = new ArrayList<>();

            @Override
            public List<Color> recent() {
                return List.copyOf(colors);
            }

            @Override
            public void used(Color color) {
                Color opaque = new Color(color.getRGB() & 0xFFFFFF);
                colors.remove(opaque);
                colors.add(0, opaque);
                while (colors.size() > maxRecent()) colors.remove(colors.size() - 1);
            }
        };
    }
}
