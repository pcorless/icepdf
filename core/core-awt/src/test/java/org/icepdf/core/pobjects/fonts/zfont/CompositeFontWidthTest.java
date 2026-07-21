/*
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
package org.icepdf.core.pobjects.fonts.zfont;

import org.icepdf.core.pobjects.DictionaryEntries;
import org.icepdf.core.pobjects.Name;
import org.icepdf.core.pobjects.Reference;
import org.icepdf.core.util.Library;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

/**
 * Exercises {@link CompositeFont#parseWidths()}, the parser for a CID font's
 * {@code /W} array (PDF 32000-1 9.7.4.3).  Widths are stored as CID/width
 * groups in one of two forms:
 * <pre>
 *   c [w1 w2 ...]       widths for the run of CIDs starting at c
 *   c_first c_last w    a single width w for every CID in [c_first, c_last]
 * </pre>
 * Any element of the array may be given indirectly (references are legal
 * anywhere an object is expected, ISO 32000 7.3.9); the group's inline width
 * array in particular is sometimes emitted as its own indirect object.  These
 * tests drive the parser from hand-built dictionaries rather than a sample
 * document so the various encodings are covered directly.
 */
public class CompositeFontWidthTest {

    private static final Name W = new Name("W");
    private static final Name DW = new Name("DW");

    /**
     * Minimal concrete {@link CompositeFont} that exposes the parsed width
     * table.  {@code parseWidths} is the only behaviour under test; the abstract
     * CIDToGIDMap hook is a no-op.
     */
    private static final class TestCompositeFont extends CompositeFont {
        TestCompositeFont(Library library, DictionaryEntries entries) {
            super(library, entries);
        }

        @Override
        protected void parseCidToGidMap() throws IOException {
            // not exercised
        }

        float[] parse() {
            parseWidths();
            return widths;
        }
    }

    private static float[] parseWidths(Library library, DictionaryEntries entries) {
        return new TestCompositeFont(library, entries).parse();
    }

    private static DictionaryEntries fontDict(Object wArray) {
        DictionaryEntries entries = new DictionaryEntries();
        entries.put(new Name("Type"), new Name("Font"));
        entries.put(new Name("Subtype"), new Name("CIDFontType0"));
        entries.put(W, wArray);
        return entries;
    }

    @DisplayName("/W - inline width array: c [w1 w2 ...]")
    @Test
    public void testInlineWidthArray() {
        Library library = new Library();
        List<Object> w = new ArrayList<>();
        w.add(0);
        w.add(new ArrayList<>(List.of(250, 576, 576, 250)));

        float[] widths = parseWidths(library, fontDict(w));

        // the parser sizes the table to maxCid + 1, leaving one trailing slot.
        assertArrayEquals(new float[]{0.250f, 0.576f, 0.576f, 0.250f, 0.0f}, widths, 0.0001f);
    }

    @DisplayName("/W - indirect width array: c <ref-to-[w1 w2 ...]> (GH-515)")
    @Test
    public void testIndirectWidthArray() {
        Library library = new Library();
        // the c [w...] group's width array is emitted as its own indirect object.
        ArrayList<Object> widthValues = new ArrayList<>(List.of(250, 576, 576, 250));
        Reference ref = new Reference(49, 0);
        library.addObject(widthValues, ref);

        List<Object> w = new ArrayList<>();
        w.add(0);
        w.add(ref);

        float[] widths = parseWidths(library, fontDict(w));

        // before the fix the Reference matched neither branch and the table came
        // back length-1 all-zero, collapsing every glyph onto the default width.
        assertArrayEquals(new float[]{0.250f, 0.576f, 0.576f, 0.250f, 0.0f}, widths, 0.0001f);
    }

    @DisplayName("/W - range form: c_first c_last w")
    @Test
    public void testRangeForm() {
        Library library = new Library();
        // CIDs 1..3 all get width 500.
        List<Object> w = new ArrayList<>();
        w.add(1);
        w.add(3);
        w.add(500);

        float[] widths = parseWidths(library, fontDict(w));

        assertArrayEquals(new float[]{0.0f, 0.500f, 0.500f, 0.500f}, widths, 0.0001f);
    }

    @DisplayName("/W - mixed inline group followed by an indirect group")
    @Test
    public void testMixedInlineAndIndirectGroups() {
        Library library = new Library();
        ArrayList<Object> secondGroup = new ArrayList<>(List.of(700, 800));
        Reference ref = new Reference(50, 0);
        library.addObject(secondGroup, ref);

        List<Object> w = new ArrayList<>();
        w.add(0);
        w.add(new ArrayList<>(List.of(250, 576)));
        w.add(5);
        w.add(ref);

        float[] widths = parseWidths(library, fontDict(w));

        // table sized to maxCid + 1 (highest CID is 6, so length 8).
        float[] expected = new float[8];
        expected[0] = 0.250f;
        expected[1] = 0.576f;
        expected[5] = 0.700f;
        expected[6] = 0.800f;
        assertArrayEquals(expected, widths, 0.0001f);
    }
}
