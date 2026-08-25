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
package org.icepdf.core.util.redaction;

import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.Form;
import org.icepdf.core.pobjects.Name;
import org.icepdf.core.pobjects.Page;
import org.icepdf.core.pobjects.Resources;
import org.icepdf.core.pobjects.graphics.text.GlyphText;
import org.icepdf.core.pobjects.graphics.text.LineText;
import org.icepdf.core.pobjects.graphics.text.WordText;
import org.icepdf.core.util.updater.WriteMode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.*;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A form drawn more than once shares a single content stream between its placements.
 * <p>
 * The parser visits it once per placement, which is what a redaction needs in order to find the
 * glyphs each rectangle covers. Rewriting the stream once per placement is a different matter: the
 * second pass reads what the first one wrote, redacts that, and writes the result back, so the
 * removal is applied twice over.
 * <p>
 * The placement bug behind all of this only shows on the <em>second</em> and later opening of a
 * document within one JVM - the first open of a fresh file is correct - which is why it surfaced as a
 * golden test failing about one full run in three and never on its own.
 */
public class SharedFormRedactionTest {

    private static final String FIXTURE = "form_drawn_twice.pdf";

    /**
     * The fixture draws one form at two positions, each showing "repeated text". A search for
     * "repeated" finds it at both, so a search-driven redaction puts a rectangle over each - which
     * is what makes both parse passes flag something and rewrite, rather than only the first.
     */
    @DisplayName("a form drawn twice has its stream redacted once")
    @Test
    public void sharedFormStreamIsRedactedOnce() throws Exception {
        String form = redactedFormStream();

        // "repeated" goes and " text" stays. What must not happen is the removal being applied to
        // its own output: a second pass over the rewritten stream re-reads the TJ array the first
        // one produced and steps over the gap again, so the surviving glyphs drift.
        assertEquals(1, occurrences(form, "text"),
                "the surviving word should appear once, not be rewritten again:\n" + form);
        assertEquals(0, occurrences(form, "repeated"),
                "and the redacted word should be gone:\n" + form);
        assertEquals(1, occurrences(form, "TJ"),
                "one show operator, written once:\n" + form);
    }


    /**
     * The form is drawn at 20,150 and again at 20,60, so its glyphs must be reported at both.
     * <p>
     * They were not. The page was handed the form's own glyph objects for each placement, and mapping
     * them into page space for the second placement moved the ones the first placement had already
     * been given, so both ended up reporting the lower baseline.
     * <p>
     * The document is opened three times because the first open of a file gets it right and every
     * open after that does not - which is exactly why this arrived as a golden test that failed
     * roughly one full run in three and never when run on its own.
     */
    @DisplayName("a form drawn twice reports glyphs at both placements, however often it is opened")
    @Test
    public void eachPlacementKeepsItsOwnCoordinates() throws Exception {
        for (int open = 1; open <= 3; open++) {
            assertEquals(new TreeSet<>(List.of(59L, 149L)), glyphBaselines(),
                    "one baseline per placement, on open number " + open);
        }
    }

    /**
     * Every distinct glyph baseline on the page, rounded. Two placements, two baselines.
     */
    private Set<Long> glyphBaselines() throws Exception {
        Document document = new Document();
        document.setFile(Paths.get("src/test/resources/redaction/" + FIXTURE).toString());
        try {
            Page page = document.getPageTree().getPage(0);
            page.init();
            Set<Long> baselines = new TreeSet<>();
            for (LineText line : page.getViewText().getPageLines()) {
                for (WordText word : line.getWords()) {
                    for (GlyphText glyph : word.getGlyphs()) {
                        baselines.add(Math.round(glyph.getBounds().getY()));
                    }
                }
            }
            return baselines;
        } finally {
            document.dispose();
        }
    }

    // -- helpers ---------------------------------------------------------------------------------

    /**
     * Redacts the fixture and returns the form's content stream as written.
     */
    private String redactedFormStream() throws Exception {
        Document document = new Document();
        document.setFile(Paths.get("src/test/resources/redaction/" + FIXTURE).toString());
        byte[] redacted;
        try {
            Page page = document.getPageTree().getPage(0);
            page.init();
            // "repeated" spans x 20..68 at each placement; " text" follows at 68..98 and must
            // survive. One rectangle per placement, as a search over the rendered page would give.
            page.addAnnotation(RedactionFixtures.redactionOver(document, new Rectangle(20, 145, 45, 20)), true);
            page.addAnnotation(RedactionFixtures.redactionOver(document, new Rectangle(20, 55, 45, 20)), true);
            Redactor.configure(document, RedactionRequest.ofAnnotations());

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.saveToOutputStream(out, WriteMode.FULL_UPDATE);
            redacted = out.toByteArray();
        } finally {
            document.dispose();
        }

        Document written = new Document();
        written.setByteArray(redacted, 0, redacted.length, "redacted");
        try {
            Page page = written.getPageTree().getPage(0);
            page.init();
            return new String(form(page).getDecodedStreamBytes(), StandardCharsets.ISO_8859_1);
        } finally {
            written.dispose();
        }
    }

    private Form form(Page page) {
        Resources resources = page.getResources();
        for (Name name : resources.getXObjects().keySet()) {
            Object xObject = resources.getXObject(name);
            if (xObject instanceof Form) {
                return (Form) xObject;
            }
        }
        throw new IllegalStateException("the fixture is meant to have a form");
    }

    private int occurrences(String haystack, String needle) {
        int count = 0;
        for (int at = haystack.indexOf(needle); at >= 0; at = haystack.indexOf(needle, at + 1)) {
            count++;
        }
        return count;
    }
}
