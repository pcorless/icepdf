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
package org.icepdf.core.pobjects.annotations;

import org.icepdf.core.pobjects.Dictionary;
import org.icepdf.core.pobjects.DictionaryEntries;
import org.icepdf.core.pobjects.Name;
import org.icepdf.core.util.Library;

import java.util.List;

/**
 * A widget's appearance characteristics dictionary, its {@code /MK} entry (PDF 32000-1 12.5.6.19,
 * table 189): the rotation, border and background colours a reader uses when it builds the widget's
 * appearance itself.
 *
 * @since 7.5
 */
public class AppearanceCharacteristics extends Dictionary {

    /** The widget's {@code /MK} key. */
    public static final Name MK_KEY = new Name("MK");
    /** Rotation, a multiple of 90 degrees counterclockwise. */
    public static final Name R_KEY = new Name("R");
    /** Border colour. */
    public static final Name BC_KEY = new Name("BC");
    /** Background colour. */
    public static final Name BG_KEY = new Name("BG");

    public AppearanceCharacteristics(Library library, DictionaryEntries entries) {
        super(library, entries);
    }

    /**
     * @return the rotation normalised to 0, 90, 180 or 270
     */
    public int getRotation() {
        Object value = library.getObject(entries, R_KEY);
        int rotation = value instanceof Number ? ((Number) value).intValue() : 0;
        rotation %= 360;
        if (rotation < 0) rotation += 360;
        return (rotation / 90) * 90;
    }

    /**
     * @return the border colour components (one gray, three RGB or four CMYK), or null when there is
     * no border colour - an empty array means transparent, so it is null too
     */
    public float[] getBorderColor() {
        return colour(BC_KEY);
    }

    /**
     * @return the background colour components (one gray, three RGB or four CMYK), or null when the
     * background is transparent
     */
    public float[] getBackgroundColor() {
        return colour(BG_KEY);
    }

    private float[] colour(Name key) {
        Object value = library.getObject(entries, key);
        if (!(value instanceof List)) return null;
        List<?> list = (List<?>) value;
        if (list.size() != 1 && list.size() != 3 && list.size() != 4) return null;
        float[] components = new float[list.size()];
        for (int i = 0; i < components.length; i++) {
            Object component = list.get(i);
            if (!(component instanceof Number)) return null;
            components[i] = Math.max(0, Math.min(1, ((Number) component).floatValue()));
        }
        return components;
    }
}
