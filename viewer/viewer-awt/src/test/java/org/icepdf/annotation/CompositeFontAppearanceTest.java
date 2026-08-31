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
package org.icepdf.annotation;

import org.icepdf.core.pobjects.DictionaryEntries;
import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.Form;
import org.icepdf.core.pobjects.Name;
import org.icepdf.core.pobjects.PDate;
import org.icepdf.core.pobjects.Page;
import org.icepdf.core.pobjects.annotations.Annotation;
import org.icepdf.core.pobjects.annotations.AnnotationFactory;
import org.icepdf.core.pobjects.annotations.FreeTextAnnotation;
import org.icepdf.core.pobjects.fonts.FontFile;
import org.icepdf.core.pobjects.fonts.FontManager;
import org.icepdf.core.pobjects.graphics.text.LineText;
import org.icepdf.core.pobjects.graphics.text.WordText;
import org.icepdf.core.util.Library;
import org.icepdf.core.util.SystemProperties;
import org.icepdf.core.util.updater.WriteMode;
import org.icepdf.ri.util.FontPropertiesManager;
import org.icepdf.utils.PDFValidator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.verapdf.pdfa.flavours.PDFAFlavour;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Date;

import static org.icepdf.core.pobjects.annotations.FreeTextAnnotation.INSETS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Annotation text that a simple font cannot show.
 * <p>
 * A simple font's character codes are one byte and the encoding written for them is WinAnsiEncoding,
 * so the most such a font can ever draw is what Windows-1252 defines. Greek, Cyrillic, Polish, CJK -
 * none of it has a code. Text containing any of those needs a composite font, where a glyph is
 * addressed by a two-byte CID instead, and that is what the builder here now produces.
 * <p>
 * The characters are written as escapes rather than as themselves: the build pins no source encoding,
 * so a literal would compile to something else on a machine whose default is not UTF-8 - which is
 * exactly the class of bug this path had.
 */
public class CompositeFontAppearanceTest {

    /**
     * Greek alpha, Cyrillic Zhe and a Polish L-with-stroke. All three are in the fonts any machine
     * running this has, and none of the three is in Windows-1252.
     */
    private static final String OUTSIDE_WIN_ANSI = "\u03B1\u0416\u0141";

    /**
     * "Japanese", in Japanese. Escaped rather than written as itself, as above.
     */
    private static final String CJK = "\u65E5\u672C\u8A9E";

    /**
     * Ships in fonts-wqy-zenhei, and covers Latin as well as CJK. Droid Sans Fallback also has the
     * CJK glyphs but no ASCII at all, so anything Latin drawn with it becomes notdef.
     */
    private static final String CJK_FONT = "WenQuanYi Zen Hei";

    @BeforeAll
    public static void init() {
        FontPropertiesManager.getInstance().loadOrReadSystemFonts();
    }

    /**
     * The round trip is the assertion that matters: what comes back out is what went in. It passes
     * only if the CIDs written into the content stream, the /CIDToGIDMap that resolves them to
     * glyphs, and the /ToUnicode that maps them back all agree.
     */
    @DisplayName("text outside WinAnsiEncoding survives a save and reopen")
    @Test
    public void textOutsideWinAnsiRoundTrips() throws Exception {
        File written = freeTextDocument(OUTSIDE_WIN_ANSI,
                new File("./src/test/out/CompositeFontAppearanceTest_composite.pdf"));

        Document document = new Document();
        document.setFile(written.getAbsolutePath());
        try {
            Page page = document.getPageTree().getPage(0);
            page.init();
            assertEquals(OUTSIDE_WIN_ANSI, annotationText(page),
                    "the annotation's text should read back as it was written");
        } finally {
            document.dispose();
        }
    }

    /**
     * The dictionary a reader is handed. A Type 0 font is a different shape from a simple one - the
     * descendant owns the glyphs and the metrics, and the parent says only how codes are read - and
     * this used to write a simple TrueType dictionary with /DescendantFonts bolted onto it.
     */
    @DisplayName("the font written is a Type 0 with a CIDFontType2 descendant")
    @Test
    public void compositeFontIsWellFormed() throws Exception {
        File written = freeTextDocument(OUTSIDE_WIN_ANSI,
                new File("./src/test/out/CompositeFontAppearanceTest_structure.pdf"));

        Document document = new Document();
        document.setFile(written.getAbsolutePath());
        try {
            Library library = document.getCatalog().getLibrary();
            DictionaryEntries font = appearanceFont(document);

            assertEquals(new Name("Type0"), font.get(new Name("Subtype")), "the parent is a Type 0");
            assertEquals(new Name("Identity-H"), font.get(new Name("Encoding")),
                    "codes are CIDs, two bytes, horizontal");
            assertNotNull(font.get(new Name("ToUnicode")), "text extraction needs the CMap");

            java.util.List<?> descendants = (java.util.List<?>) library.getObject(font, new Name("DescendantFonts"));
            assertEquals(1, descendants.size(), "a Type 0 font has exactly one descendant");
            DictionaryEntries descendant = ((org.icepdf.core.pobjects.Dictionary)
                    library.getObject(descendants.get(0))).getEntries();

            assertEquals(new Name("CIDFontType2"), descendant.get(new Name("Subtype")),
                    "an embedded TrueType descendant");
            assertNotNull(descendant.get(new Name("W")), "the descendant owns the widths");
            assertNotNull(descendant.get(new Name("CIDToGIDMap")),
                    "a subset needs the map from CID to the glyph it ended up at");
            assertNotNull(descendant.get(new Name("FontDescriptor")), "and the descriptor");
        } finally {
            document.dispose();
        }
    }

    /**
     * The control. Text that WinAnsiEncoding covers must still take the simple path - otherwise
     * "composite fonts work" would be indistinguishable from "everything is a composite font now",
     * and every existing document would have changed shape.
     */
    @DisplayName("text WinAnsiEncoding covers still gets a simple font")
    @Test
    public void winAnsiTextStillGetsASimpleFont() throws Exception {
        // a left double quote and a euro sign are 0x93 and 0x80 in Windows-1252, so they are covered
        File written = freeTextDocument("Hello \u201Cworld\u201D \u20AC5",
                new File("./src/test/out/CompositeFontAppearanceTest_simple.pdf"));

        Document document = new Document();
        document.setFile(written.getAbsolutePath());
        try {
            DictionaryEntries font = appearanceFont(document);
            assertEquals(new Name("TrueType"), font.get(new Name("Subtype")),
                    "this text needs nothing a simple font cannot do");
            assertNotNull(font.get(new Name("Widths")), "and a simple font carries its own widths");
        } finally {
            document.dispose();
        }
    }

    /**
     * A composite font brings entries a simple one does not - a descendant, a CIDToGIDMap, a CIDSet -
     * and PDF/A has rules about all of them. Two of those streams were being written with the font
     * programme's bytes instead of their own contents, which is the kind of fault that draws a page
     * of glyphs from the wrong places and never raises an error.
     */
    @DisplayName("adding composite-font text introduces no conformance failure")
    @Test
    public void compositeFontDoesNotBreakConformance() throws Exception {
        File source = new File("src/test/resources/annotation/hello_pdfa1.pdf");
        File written = freeTextDocument(OUTSIDE_WIN_ANSI,
                new File("./src/test/out/CompositeFontAppearanceTest_conformance.pdf"));

        PDFValidator.assertNoNewFailures(source, written,
                PDFAFlavour.PDFA_1_A, PDFAFlavour.PDFA_1_B,
                PDFAFlavour.PDFA_2_A, PDFAFlavour.PDFA_2_B, PDFAFlavour.PDFA_2_U,
                PDFAFlavour.PDFA_3_B);
    }

    /**
     * The case the composite path exists for. CJK is the reason a one-byte code is not enough: a
     * simple font has 256 codes and Japanese needs thousands, so there is no arrangement of a simple
     * font that shows this text at all.
     * <p>
     * The font has to be named explicitly. The default face has no CJK glyphs, and asking for a
     * character a font does not have returns glyph 0 - so a test that let the default stand would
     * write three notdefs and, since /ToUnicode is built from the same lookup, still read them back
     * as the right characters. It would pass while drawing empty boxes.
     */
    @DisplayName("CJK text survives a save and reopen")
    @Test
    public void cjkTextRoundTrips() throws Exception {
        assumeTrue(isInstalled(CJK_FONT),
                CJK_FONT + " is not installed, so there is nothing to draw this with");

        File written = freeTextDocument(CJK, CJK_FONT,
                new File("./src/test/out/CompositeFontAppearanceTest_cjk.pdf"));

        Document document = new Document();
        document.setFile(written.getAbsolutePath());
        try {
            Page page = document.getPageTree().getPage(0);
            page.init();
            assertEquals(CJK, annotationText(page), "the Japanese should read back as it was written");

            // A font without the glyph returns glyph 0 for it, and /ToUnicode is built from the same
            // lookup - so notdefs would still read back as the right characters while drawing empty
            // boxes.  The codes themselves are what says real glyphs were used.
            String contentStream = appearanceStream(page);
            assertFalse(contentStream.contains("0000"),
                    "no CID should be notdef:\n" + contentStream);
        } finally {
            document.dispose();
        }
    }

    /**
     * Guards the test above against passing on notdefs, and is the reason it is a skip rather than a
     * failure on a machine without the font.
     */
    private boolean isInstalled(String fontName) {
        FontFile fontFile = FontManager.getInstance().getInstance(fontName, 0);
        // FontManager always returns something - a substitute when it has no match - so the question
        // is whether it returned the face that was asked for.
        return fontFile != null
                && fontFile.getName().replace(" ", "").equalsIgnoreCase(fontName.replace(" ", ""));
    }

    // -- helpers ---------------------------------------------------------------------------------

    /**
     * @return the raw content stream of the page's first annotation's appearance
     */
    private String appearanceStream(Page page) throws Exception {
        Form appearance = (Form) page.getAnnotations().get(0).getAppearanceStream();
        appearance.init();
        return new String(appearance.getDecodedStreamBytes(), java.nio.charset.StandardCharsets.ISO_8859_1);
    }

    /**
     * @return the text of the page's first annotation, as extracted from its appearance stream
     */
    private String annotationText(Page page) throws Exception {
        Form appearance = (Form) page.getAnnotations().get(0).getAppearanceStream();
        appearance.init();
        StringBuilder text = new StringBuilder();
        for (LineText line : appearance.getShapes().getPageText().getPageLines()) {
            for (WordText word : line.getWords()) {
                text.append(word.getText());
            }
        }
        return text.toString().trim();
    }

    /**
     * The font dictionary of the first annotation's appearance stream.
     */
    private DictionaryEntries appearanceFont(Document document) throws Exception {
        Page page = document.getPageTree().getPage(0);
        page.init();
        Form appearance = (Form) page.getAnnotations().get(0).getAppearanceStream();
        appearance.init();
        Library library = document.getCatalog().getLibrary();
        DictionaryEntries fonts = appearance.getResources().getFonts();
        assertTrue(fonts != null && !fonts.isEmpty(), "the appearance should reference a font");
        Object font = library.getObject(fonts, (Name) fonts.keySet().iterator().next());
        return ((org.icepdf.core.pobjects.Dictionary) font).getEntries();
    }

    /**
     * Writes a document with one FreeText annotation carrying the given text.
     */
    private File freeTextDocument(String content, File outputFile) throws Exception {
        return freeTextDocument(content, null, outputFile);
    }

    private File freeTextDocument(String content, String fontName, File outputFile) throws Exception {
        Document document = new Document();
        try (InputStream source = getClass().getResourceAsStream("/annotation/hello_pdfa1.pdf")) {
            document.setInputStream(source, "hello_pdfa1.pdf");
        }
        Library library = document.getCatalog().getLibrary();
        Page page = document.getPageTree().getPage(0);

        Rectangle rect = new Rectangle(250, 200, 400, 50);
        Rectangle tBbox = page.convertToPageSpace(rect, Page.BOUNDARY_CROPBOX, 0f, 1.0f);
        tBbox.setLocation(tBbox.x - INSETS, tBbox.y - tBbox.height - INSETS);

        FreeTextAnnotation annotation = (FreeTextAnnotation) AnnotationFactory.buildAnnotation(
                library, Annotation.SUBTYPE_FREE_TEXT, tBbox);
        annotation.setCreationDate(PDate.formatDateTime(new Date()));
        annotation.setTitleText(SystemProperties.USER_NAME);
        annotation.setContents(content);
        if (fontName != null) {
            annotation.setFontName(fontName);
        }

        annotation.resetAppearanceStream(page.getToPageSpaceTransform(Page.BOUNDARY_CROPBOX, 0f, 1.0f));
        page.addAnnotation(annotation, true);
        annotation.saveAppearanceStream();

        outputFile.getParentFile().mkdirs();
        try (BufferedOutputStream stream = new BufferedOutputStream(new FileOutputStream(outputFile), 64 * 1024)) {
            document.saveToOutputStream(stream, WriteMode.INCREMENT_UPDATE);
        }
        document.dispose();
        return outputFile;
    }
}
