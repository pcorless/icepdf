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
package org.icepdf.core.pobjects.annotations.utils;

import org.icepdf.core.pobjects.fonts.FontFile;
import org.icepdf.core.pobjects.fonts.FontManager;
import org.icepdf.core.pobjects.fonts.builders.TrueTypeFontEmbedder;
import org.icepdf.core.pobjects.graphics.Shapes;
import org.icepdf.core.pobjects.graphics.commands.PostScriptEncoder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.*;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Several runs of text laid out into one font.
 * <p>
 * A signature appearance does exactly this: reason, contact, signer and location are four separate
 * calls that end up sharing a single font. Which kind of font that is depends on all four together -
 * one line outside WinAnsiEncoding means the font has to be a composite one, and then every line has
 * to be written in its two-byte codes.
 * <p>
 * Deciding it per run, which is what the first version did, writes the Latin lines as one-byte codes
 * and the other as two-byte CIDs into a single font that can only be read one of those ways. Nothing
 * errors; the Latin lines are simply drawn as half as many wrong glyphs.
 * <p>
 * This is tested here rather than through a real signature appearance because whether a signature
 * draws any text at all is a viewer preference, shared by every test in the JVM and seeded from the
 * viewer's defaults - which made the same assertion pass or fail for reasons having nothing to do
 * with encoding.
 */
public class MultiRunEncodingTest {

    /**
     * Greek alpha, outside anything a one-byte WinAnsi code can reach. Escaped rather than written as
     * itself: the build pins no source encoding.
     */
    private static final String OUTSIDE_WIN_ANSI = "\u03B1";

    @BeforeAll
    public static void init() {
        FontManager.getInstance().initialize();
    }

    /**
     * The Latin run is laid out first and the run that forces the composite font comes second, which
     * is the order that got it wrong: by the time the second arrives, the first is already written.
     */
    @DisplayName("runs sharing a font are all written in that font's encoding")
    @Test
    public void everyRunUsesTheFontsEncoding() {
        TrueTypeFontEmbedder embedder = embedder();
        Shapes shapes = new Shapes();

        // What a caller laying out more than one run has to do: declare all of it first.
        embedder.addToSubset("Reason: Approval");
        embedder.addToSubset(OUTSIDE_WIN_ANSI);

        ContentWriterUtils.addTextSpritesToShapes(embedder, 0, 0, shapes, 12, 2, Color.BLACK,
                "Reason: Approval");
        ContentWriterUtils.addTextSpritesToShapes(embedder, 0, 20, shapes, 12, 2, Color.BLACK,
                OUTSIDE_WIN_ANSI);

        String contentStream = new String(PostScriptEncoder.generatePostScript(shapes.getShapes()),
                StandardCharsets.ISO_8859_1);

        assertTrue(embedder.requiresCompositeFont(), "one run is outside WinAnsiEncoding");
        assertFalse(contentStream.contains("[("),
                "no run may be written as one-byte codes:\n" + contentStream);
        assertTrue(contentStream.contains("[<"),
                "every run should be a hex CID string:\n" + contentStream);
    }

    /**
     * The control. If every run became a hex string regardless, the assertion above would hold for a
     * document that had no need of a composite font and had been made bigger for nothing.
     */
    @DisplayName("runs that need nothing special stay simple")
    @Test
    public void winAnsiRunsStaySimple() {
        TrueTypeFontEmbedder embedder = embedder();
        Shapes shapes = new Shapes();

        embedder.addToSubset("Reason: Approval");
        ContentWriterUtils.addTextSpritesToShapes(embedder, 0, 0, shapes, 12, 2, Color.BLACK,
                "Reason: Approval");

        String contentStream = new String(PostScriptEncoder.generatePostScript(shapes.getShapes()),
                StandardCharsets.ISO_8859_1);

        assertFalse(embedder.requiresCompositeFont(), "this text fits a simple font");
        assertTrue(contentStream.contains("[("),
                "and should be written as one-byte codes:\n" + contentStream);
    }

    private TrueTypeFontEmbedder embedder() {
        FontFile fontFile = FontManager.getInstance().getInstance("Helvetica", 0);
        assumeTrue(fontFile != null, "no font available to lay text out with");
        return new TrueTypeFontEmbedder(fontFile.deriveFont(12f));
    }
}
