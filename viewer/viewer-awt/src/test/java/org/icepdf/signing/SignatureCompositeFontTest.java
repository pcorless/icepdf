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
package org.icepdf.signing;

import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.Form;
import org.icepdf.core.pobjects.Name;
import org.icepdf.core.pobjects.Page;
import org.icepdf.core.pobjects.fonts.FontFile;
import org.icepdf.core.pobjects.fonts.FontManager;
import org.icepdf.core.pobjects.graphics.text.LineText;
import org.icepdf.core.pobjects.graphics.text.WordText;
import org.icepdf.ri.util.FontPropertiesManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * A signature appearance whose text is not all in one script.
 * <p>
 * A signature appearance is four separate lines - reason, contact, signer, location - laid out one
 * after another into a single font. Which kind of font that is depends on all four together: a
 * Japanese signer name means the font has to be a composite one, and then the three Latin lines have
 * to be written in its two-byte codes as well.
 * <p>
 * Deciding that per line, which is the obvious thing to do and what was done at first, writes the
 * Latin lines as one-byte codes and the Japanese line as two-byte CIDs into one font that can only be
 * read one of those ways. Nothing errors; the Latin lines are simply drawn as half as many wrong
 * glyphs. This is the test that says otherwise.
 */
public class SignatureCompositeFontTest {

    /**
     * "Japanese" in Japanese, as the signer's name. Escaped rather than written as itself: the build
     * pins no source encoding.
     */
    private static final String CJK_NAME = "\u65E5\u672C\u8A9E";

    /**
     * Has to cover both scripts, because all four lines share this one font. Droid Sans Fallback
     * carries no ASCII at all - glyph 0 for 'A' - so the Latin lines came back as notdefs with it,
     * which is the same shape of failure this test exists to catch and would have been mistaken for
     * it.
     */
    private static final String CJK_FONT = "WenQuanYi Zen Hei";

    @BeforeAll
    public static void init() {
        FontPropertiesManager.getInstance().loadOrReadSystemFonts();
    }

    /**
     * The reason and location lines are plain ASCII and the signer's name is not, so the appearance
     * has to commit to the composite font for all of them.
     */
    @DisplayName("a signature with a name outside WinAnsiEncoding writes every line in one encoding")
    @Test
    public void mixedScriptSignatureUsesOneEncoding() throws Exception {
        assumeTrue(isInstalled(CJK_FONT), CJK_FONT + " is not installed");

        File signed = SigningFixture.of(new File("src/test/resources/annotation/hello_pdfa1.pdf"))
                .withAppearance()
                .signerName(CJK_NAME)
                .appearanceFont(CJK_FONT)
                .signTo(new File("./src/test/out/SignatureCompositeFontTest_mixed.pdf"));

        Document document = new Document();
        document.setFile(signed.getAbsolutePath());
        try {
            Page page = document.getPageTree().getPage(0);
            page.init();
            String appearance = appearanceStream(page);

            // Every show operator has to be a hex string of CIDs.  A literal string is a run that was
            // written as one-byte codes, which is the bug: it would be read two bytes at a time.
            assertFalse(appearance.contains("[("),
                    "no run should be written as one-byte codes:\n" + appearance);
            assertTrue(appearance.contains("[<"),
                    "the runs should be hex CID strings:\n" + appearance);

            String text = appearanceText(page);
            assertTrue(text.contains(CJK_NAME), "the signer's name should read back:\n" + text);
            // The Latin lines are the ones that were being corrupted, so they are what proves it.
            assertTrue(text.contains("Approval") || text.contains("Certification"),
                    "and so should the Latin lines sharing the font:\n" + text);
        } finally {
            document.dispose();
        }
    }

    // -- helpers ---------------------------------------------------------------------------------

    private boolean isInstalled(String fontName) {
        FontFile fontFile = FontManager.getInstance().getInstance(fontName, 0);
        return fontFile != null
                && fontFile.getName().replace(" ", "").equalsIgnoreCase(fontName.replace(" ", ""));
    }

    private Form signatureAppearance(Page page) throws Exception {
        Form appearance = (Form) page.getAnnotations().get(0).getAppearanceStream();
        appearance.init();
        return appearance;
    }

    private String appearanceStream(Page page) throws Exception {
        return new String(signatureAppearance(page).getDecodedStreamBytes(), StandardCharsets.ISO_8859_1);
    }

    private String appearanceText(Page page) throws Exception {
        StringBuilder text = new StringBuilder();
        for (LineText line : signatureAppearance(page).getShapes().getPageText().getPageLines()) {
            for (WordText word : line.getWords()) {
                text.append(word.getText()).append(' ');
            }
        }
        return text.toString();
    }
}
