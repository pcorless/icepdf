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
package org.icepdf.core.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests {@link ColorUtil}, which resolves the CSS colour names an annotation or a form field may
 * name instead of giving numbers.
 * <p>
 * Almost all of the class is one generated 147-way string switch whose arms index a parallel array
 * of values, with the index constants maintained by hand and documented as "real index plus one".
 * A switch of that shape fails in exactly one way - an arm off by one from the array - and the
 * result is a colour that is wrong but perfectly plausible, since its neighbours in the table are
 * other real colours.
 * <p>
 * The checks below are in two groups, and the difference matters.  The first group asserts values
 * known independently of this code: the colours whose definitions are fixed by the CSS
 * specification, the pairs of names that are defined to be the same colour, and the two places
 * where Java's own constants disagree with CSS.  Those can fail.  The second is a snapshot of the
 * whole table taken from the source as it stands; it cannot show that today's values are right,
 * only that a later edit changed one, and it exercises every arm of the switch.
 */
public class ColorUtilTest {

    // ------------------------------------------------------------------
    // values fixed by the CSS specification
    // ------------------------------------------------------------------

    @DisplayName("the sixteen original HTML colours have the values the specification gives them")
    @Test
    public void originalHtmlColours() {
        assertEquals(0x000000, ColorUtil.convertNamedColor("black"));
        assertEquals(0xFFFFFF, ColorUtil.convertNamedColor("white"));
        assertEquals(0xFF0000, ColorUtil.convertNamedColor("red"));
        assertEquals(0x00FF00, ColorUtil.convertNamedColor("lime"));
        assertEquals(0x0000FF, ColorUtil.convertNamedColor("blue"));
        assertEquals(0xFFFF00, ColorUtil.convertNamedColor("yellow"));
        assertEquals(0x00FFFF, ColorUtil.convertNamedColor("aqua"));
        assertEquals(0xFF00FF, ColorUtil.convertNamedColor("fuchsia"));
        assertEquals(0x808080, ColorUtil.convertNamedColor("gray"));
        assertEquals(0xC0C0C0, ColorUtil.convertNamedColor("silver"));
        assertEquals(0x800000, ColorUtil.convertNamedColor("maroon"));
        assertEquals(0x808000, ColorUtil.convertNamedColor("olive"));
        assertEquals(0x008000, ColorUtil.convertNamedColor("green"));
        assertEquals(0x800080, ColorUtil.convertNamedColor("purple"));
        assertEquals(0x008080, ColorUtil.convertNamedColor("teal"));
        assertEquals(0x000080, ColorUtil.convertNamedColor("navy"));
    }

    @DisplayName("green is not lime, and Java's Color.GREEN is the other one")
    @Test
    public void greenIsTheDarkOne() {
        // The single most confusable pair in the table: CSS green is half intensity, and the full
        // intensity colour is called lime.  Java's Color.GREEN is the full intensity one, so a
        // table built by reading Java's constants across would put the wrong value under "green".
        assertEquals(0x008000, ColorUtil.convertNamedColor("green"));
        assertEquals(0x00FF00, ColorUtil.convertNamedColor("lime"));
        assertNotEquals(ColorUtil.convertNamedColor("green"), Color.GREEN.getRGB() & 0xFFFFFF);
        assertEquals(Color.GREEN.getRGB() & 0xFFFFFF, ColorUtil.convertNamedColor("lime"));
    }

    @DisplayName("names defined to be the same colour resolve to the same value")
    @Test
    public void aliasesAgree() {
        // These pairs are two spellings of one entry in the specification, and they sit apart in
        // the table, so an arm that slipped by one would separate them.
        String[][] aliases = {
                {"aqua", "cyan"}, {"fuchsia", "magenta"}, {"gray", "grey"},
                {"darkgray", "darkgrey"}, {"lightgray", "lightgrey"},
                {"darkslategray", "darkslategrey"}, {"dimgray", "dimgrey"},
                {"lightslategray", "lightslategrey"}, {"slategray", "slategrey"},
        };
        for (String[] pair : aliases) {
            assertEquals(ColorUtil.convertNamedColor(pair[0]), ColorUtil.convertNamedColor(pair[1]),
                    pair[0] + " and " + pair[1] + " are the same colour");
        }
    }

    @DisplayName("the greys run from dark to light in the order their names claim")
    @Test
    public void greysAreOrdered() {
        // An independent structural check: whatever the exact values, these have to increase.
        int[] greys = {
                ColorUtil.convertNamedColor("black"),
                ColorUtil.convertNamedColor("darkslategray"),
                ColorUtil.convertNamedColor("dimgray"),
                ColorUtil.convertNamedColor("gray"),
                ColorUtil.convertNamedColor("darkgray"),
                ColorUtil.convertNamedColor("silver"),
                ColorUtil.convertNamedColor("lightgray"),
                ColorUtil.convertNamedColor("gainsboro"),
                ColorUtil.convertNamedColor("whitesmoke"),
                ColorUtil.convertNamedColor("white"),
        };
        for (int i = 1; i < greys.length; i++) {
            assertTrue(greys[i] > greys[i - 1],
                    "grey " + i + " (" + Integer.toHexString(greys[i]) + ") should be lighter than "
                            + Integer.toHexString(greys[i - 1]));
        }
    }

    @DisplayName("a colour named dark is darker than the same colour without it")
    @Test
    public void darkVariantsAreDarker() {
        String[] bases = {"red", "green", "blue", "cyan", "magenta", "orange", "violet", "khaki"};
        for (String base : bases) {
            int plain = ColorUtil.convertNamedColor(base);
            int dark = ColorUtil.convertNamedColor("dark" + base);
            assertTrue(dark >= 0, "dark" + base + " should be a known colour");
            assertTrue(brightness(dark) < brightness(plain),
                    "dark" + base + " should be darker than " + base);
        }
    }

    private static int brightness(int rgb) {
        return ((rgb >> 16) & 0xFF) + ((rgb >> 8) & 0xFF) + (rgb & 0xFF);
    }

    // ------------------------------------------------------------------
    // resolving a name
    // ------------------------------------------------------------------

    @DisplayName("an unknown name resolves to nothing")
    @Test
    public void unknownName() {
        assertEquals(-1, ColorUtil.convertNamedColor("nosuchcolour"));
        assertEquals(-1, ColorUtil.convertNamedColor(""));
        // added to CSS in 2014, after the list this table was built from
        assertEquals(-1, ColorUtil.convertNamedColor("rebeccapurple"));
    }

    @DisplayName("convertNamedColor matches lower case only")
    @Test
    public void nameLookupIsCaseSensitive() {
        // Worth pinning because the two entry points disagree: this one takes the name as given,
        // while convertColorNameToRGB lowercases first, so "Red" works through one and not the
        // other.
        assertEquals(0xFF0000, ColorUtil.convertNamedColor("red"));
        assertEquals(-1, ColorUtil.convertNamedColor("Red"));
        assertEquals(-1, ColorUtil.convertNamedColor("RED"));
    }

    @DisplayName("every name in the table resolves to a colour in range")
    @Test
    public void everyNameResolves() {
        for (int i = 0; i < NAMES_AND_VALUES.length; i += 2) {
            String name = NAMES_AND_VALUES[i];
            int value = ColorUtil.convertNamedColor(name);
            assertTrue(value >= 0, name + " should be a known colour");
            assertTrue(value <= 0xFFFFFF, name + " should fit in twenty four bits");
        }
    }

    @DisplayName("the table holds no name twice")
    @Test
    public void namesAreUnique() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < NAMES_AND_VALUES.length; i += 2) {
            assertTrue(seen.add(NAMES_AND_VALUES[i]), NAMES_AND_VALUES[i] + " appears twice");
        }
        assertEquals(147, seen.size(), "the CSS list this table was built from holds 147 names");
    }

    // ------------------------------------------------------------------
    // the other entry points
    // ------------------------------------------------------------------

    @DisplayName("convertColorNameToRGB renders a name as upper case hex")
    @Test
    public void nameToRgb() {
        assertEquals("#000000", ColorUtil.convertColorNameToRGB("black"));
        assertEquals("#FFFFFF", ColorUtil.convertColorNameToRGB("white"));
        assertEquals("#FF0000", ColorUtil.convertColorNameToRGB("red"));
        assertEquals("#F0F8FF", ColorUtil.convertColorNameToRGB("aliceblue"));
    }

    @DisplayName("convertColorNameToRGB accepts any case, unlike convertNamedColor")
    @Test
    public void nameToRgbLowercasesFirst() {
        assertEquals("#FF0000", ColorUtil.convertColorNameToRGB("Red"));
        assertEquals("#FF0000", ColorUtil.convertColorNameToRGB("RED"));
    }

    @DisplayName("convertColorNameToRGB hands back a name it does not know, unchanged")
    @Test
    public void nameToRgbUnknown() {
        // The caller is expected to pass the result on regardless, so an unknown name has to come
        // through rather than becoming null or an empty string.
        assertEquals("nosuchcolour", ColorUtil.convertColorNameToRGB("nosuchcolour"));
        assertEquals("#123456", ColorUtil.convertColorNameToRGB("#123456"));
    }

    @DisplayName("convertColorToRGB renders a Color as upper case hex, dropping the alpha")
    @Test
    public void colorToRgb() {
        assertEquals("#000000", ColorUtil.convertColorToRGB(Color.BLACK));
        assertEquals("#FFFFFF", ColorUtil.convertColorToRGB(Color.WHITE));
        assertEquals("#FF0000", ColorUtil.convertColorToRGB(Color.RED));
        assertEquals("#0A0B0C", ColorUtil.convertColorToRGB(new Color(10, 11, 12)));
        assertEquals("#010203", ColorUtil.convertColorToRGB(new Color(1, 2, 3, 128)),
                "a translucent colour still renders its six hex digits");
    }

    @DisplayName("convertColor reads hex, with or without the leading hash")
    @Test
    public void convertColorHex() {
        assertEquals(0xFF0000, ColorUtil.convertColor("#FF0000"));
        assertEquals(0xFF0000, ColorUtil.convertColor("FF0000"));
        assertEquals(0x00FF00, ColorUtil.convertColor("#00ff00"));
        assertEquals(0x000000, ColorUtil.convertColor("#000000"));
        assertEquals(0xFFFFFF, ColorUtil.convertColor("#FFFFFF"));
    }

    @DisplayName("convertColor falls back to the name table when the text is not hex")
    @Test
    public void convertColorByName() {
        assertEquals(0xFF0000, ColorUtil.convertColor("red"));
        assertEquals(0x008000, ColorUtil.convertColor("green"));
        assertEquals(-1, ColorUtil.convertColor("nosuchcolour"));
    }

    @DisplayName("no colour name is also readable as hex, so the two never collide")
    @Test
    public void namesAndHexDoNotCollide() {
        // convertColor tries hex first, so a name spelled only from the letters a to f would be
        // read as a number instead.  None of the 147 is, and this is what would notice if a later
        // addition were.
        for (int i = 0; i < NAMES_AND_VALUES.length; i += 2) {
            String name = NAMES_AND_VALUES[i];
            assertTrue(!name.matches("[0-9a-fA-F]+"),
                    name + " would be read as a hex number rather than a name");
        }
    }

    // ------------------------------------------------------------------
    // the table as it stands
    // ------------------------------------------------------------------

    @DisplayName("every name still resolves to the value it resolved to when this was written")
    @Test
    public void tableSnapshot() {
        // A snapshot of the table taken from the source, not an independent authority: it cannot
        // show that a value is right, only that it changed.  What it is good for is the switch
        // itself - every one of the 147 arms is taken here - so an arm that slips against the
        // array, which is how this shape of generated code fails, is caught by whichever names
        // moved.
        for (int i = 0; i < NAMES_AND_VALUES.length; i += 2) {
            String name = NAMES_AND_VALUES[i];
            assertEquals(NAMES_AND_VALUES[i + 1], ColorUtil.convertColorNameToRGB(name), name);
        }
    }

    /** Name and rendered value, as the table read when these tests were written. */
    private static final String[] NAMES_AND_VALUES = {
            "aliceblue", "#F0F8FF",
            "antiquewhite", "#FAEBD7",
            "aqua", "#00FFFF",
            "aquamarine", "#7FFFD4",
            "azure", "#F0FFFF",
            "beige", "#F5F5DC",
            "bisque", "#FFE4C4",
            "black", "#000000",
            "blanchedalmond", "#FFEBCD",
            "blue", "#0000FF",
            "blueviolet", "#8A2BE2",
            "brown", "#A52A2A",
            "burlywood", "#DEB887",
            "cadetblue", "#5F9EA0",
            "chartreuse", "#7FFF00",
            "chocolate", "#D2691E",
            "coral", "#FF7F50",
            "cornflowerblue", "#6495ED",
            "cornsilk", "#FFF8DC",
            "crimson", "#DC143C",
            "cyan", "#00FFFF",
            "darkblue", "#00008B",
            "darkcyan", "#008B8B",
            "darkgoldenrod", "#B8860B",
            "darkgray", "#A9A9A9",
            "darkgreen", "#006400",
            "darkgrey", "#A9A9A9",
            "darkkhaki", "#BDB76B",
            "darkmagenta", "#8B008B",
            "darkolivegreen", "#556B2F",
            "darkorange", "#FF8C00",
            "darkorchid", "#9932CC",
            "darkred", "#8B0000",
            "darksalmon", "#E9967A",
            "darkseagreen", "#8FBC8F",
            "darkslateblue", "#483D8B",
            "darkslategray", "#2F4F4F",
            "darkslategrey", "#2F4F4F",
            "darkturquoise", "#00CED1",
            "darkviolet", "#9400D3",
            "deeppink", "#FF1493",
            "deepskyblue", "#00BFFF",
            "dimgray", "#696969",
            "dimgrey", "#696969",
            "dodgerblue", "#1E90FF",
            "firebrick", "#B22222",
            "floralwhite", "#FFFAF0",
            "forestgreen", "#228B22",
            "fuchsia", "#FF00FF",
            "gainsboro", "#DCDCDC",
            "ghostwhite", "#F8F8FF",
            "gold", "#FFD700",
            "goldenrod", "#DAA520",
            "gray", "#808080",
            "grey", "#808080",
            "green", "#008000",
            "greenyellow", "#ADFF2F",
            "honeydew", "#F0FFF0",
            "hotpink", "#FF69B4",
            "indianred", "#CD5C5C",
            "indigo", "#4B0082",
            "ivory", "#FFFFF0",
            "khaki", "#F0E68C",
            "lavender", "#E6E6FA",
            "lavenderblush", "#FFF0F5",
            "lawngreen", "#7CFC00",
            "lemonchiffon", "#FFFACD",
            "lightblue", "#ADD8E6",
            "lightcoral", "#F08080",
            "lightcyan", "#E0FFFF",
            "lightgoldenrodyellow", "#FAFAD2",
            "lightgray", "#D3D3D3",
            "lightgreen", "#90EE90",
            "lightgrey", "#D3D3D3",
            "lightpink", "#FFB6C1",
            "lightsalmon", "#FFA07A",
            "lightseagreen", "#20B2AA",
            "lightskyblue", "#87CEFA",
            "lightslategray", "#778899",
            "lightslategrey", "#778899",
            "lightsteelblue", "#B0C4DE",
            "lightyellow", "#FFFFE0",
            "lime", "#00FF00",
            "limegreen", "#32CD32",
            "linen", "#FAF0E6",
            "magenta", "#FF00FF",
            "maroon", "#800000",
            "mediumaquamarine", "#66CDAA",
            "mediumblue", "#0000CD",
            "mediumorchid", "#BA55D3",
            "mediumpurple", "#9370DB",
            "mediumseagreen", "#3CB371",
            "mediumslateblue", "#7B68EE",
            "mediumspringgreen", "#00FA9A",
            "mediumturquoise", "#48D1CC",
            "mediumvioletred", "#C71585",
            "midnightblue", "#191970",
            "mintcream", "#F5FFFA",
            "mistyrose", "#FFE4E1",
            "moccasin", "#FFE4B5",
            "navajowhite", "#FFDEAD",
            "navy", "#000080",
            "oldlace", "#FDF5E6",
            "olive", "#808000",
            "olivedrab", "#6B8E23",
            "orange", "#FFA500",
            "orangered", "#FF4500",
            "orchid", "#DA70D6",
            "palegoldenrod", "#EEE8AA",
            "palegreen", "#98FB98",
            "paleturquoise", "#AFEEEE",
            "palevioletred", "#DB7093",
            "papayawhip", "#FFEFD5",
            "peachpuff", "#FFDAB9",
            "peru", "#CD853F",
            "pink", "#FFC0CB",
            "plum", "#DDA0DD",
            "powderblue", "#B0E0E6",
            "purple", "#800080",
            "red", "#FF0000",
            "rosybrown", "#BC8F8F",
            "royalblue", "#4169E1",
            "saddlebrown", "#8B4513",
            "salmon", "#FA8072",
            "sandybrown", "#F4A460",
            "seagreen", "#2E8B57",
            "seashell", "#FFF5EE",
            "sienna", "#A0522D",
            "silver", "#C0C0C0",
            "skyblue", "#87CEEB",
            "slateblue", "#6A5ACD",
            "slategray", "#708090",
            "slategrey", "#708090",
            "snow", "#FFFAFA",
            "springgreen", "#00FF7F",
            "steelblue", "#4682B4",
            "tan", "#D2B48C",
            "teal", "#008080",
            "thistle", "#D8BFD8",
            "tomato", "#FF6347",
            "turquoise", "#40E0D0",
            "violet", "#EE82EE",
            "wheat", "#F5DEB3",
            "white", "#FFFFFF",
            "whitesmoke", "#F5F5F5",
            "yellow", "#FFFF00",
            "yellowgreen", "#9ACD32",
    };
}
