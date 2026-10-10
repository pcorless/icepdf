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

import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.Page;
import org.icepdf.core.util.GraphicsRenderingHints;
import org.icepdf.core.util.updater.WriteMode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.BufferedOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Text and choice field appearances built from the field's value, laid out as Acrobat lays them out:
 * padding, the cap-height-centred baseline, wrapping, comb cells, auto-size, quadding, list box
 * highlight, {@code /MK} border and background, and the parts of an existing appearance kept.
 */
public class FieldAppearanceGeneratorTest {

    @TempDir
    Path temp;

    /** A one-page form whose fields are the given widget dictionaries (objects 5 on, as listed). */
    private Path form(String name, String... widgets) throws Exception {
        return form(name, List.of(widgets), List.of());
    }

    /** As {@link #form(String, String...)}, with other objects numbered after the widgets. */
    private Path form(String name, List<String> widgets, List<String> extras) throws Exception {
        List<String> objects = new ArrayList<>();
        StringBuilder kids = new StringBuilder();
        for (int i = 0; i < widgets.size(); i++) kids.append(i + 5).append(" 0 R ");
        objects.add("<< /Type /Catalog /Pages 2 0 R /AcroForm << /Fields [" + kids + "] "
                + "/DR << /Font << /Helv 4 0 R >> >> /DA (/Helv 0 Tf 0 g) >> >>");
        objects.add("<< /Type /Pages /Kids [3 0 R] /Count 1 >>");
        objects.add("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 400 400] /Annots [" + kids + "] >>");
        objects.add("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>");
        objects.addAll(widgets);
        objects.addAll(extras);
        StringBuilder out = new StringBuilder("%PDF-1.7\n");
        List<Integer> offsets = new ArrayList<>();
        for (int i = 0; i < objects.size(); i++) {
            offsets.add(out.length());
            out.append(i + 1).append(" 0 obj\n").append(objects.get(i)).append("\nendobj\n");
        }
        int xref = out.length();
        out.append("xref\n0 ").append(objects.size() + 1).append("\n0000000000 65535 f \n");
        for (int offset : offsets) out.append(String.format("%010d 00000 n \n", offset));
        out.append("trailer\n<< /Size ").append(objects.size() + 1).append(" /Root 1 0 R >>\nstartxref\n")
                .append(xref).append("\n%%EOF\n");
        Path file = temp.resolve(name + ".pdf");
        Files.write(file, out.toString().getBytes(StandardCharsets.ISO_8859_1));
        return file;
    }

    private static String text(String name, String rect, String extra) {
        return "<< /Type /Annot /Subtype /Widget /FT /Tx /T (" + name + ") /Rect [" + rect + "] /F 4 " + extra + " >>";
    }

    /** Opens the form, rebuilds widget {@code index}'s appearance and returns the content written. */
    private static String regenerate(Document document, int index) throws Exception {
        Page page = document.getPageTree().getPage(0);
        page.init();
        Annotation widget = page.getAnnotations().get(index);
        widget.resetAppearanceStream(new AffineTransform());
        byte[] bytes = widget.getAppearanceStream().getDecodedStreamBytes();
        return new String(bytes, StandardCharsets.ISO_8859_1);
    }

    private String generated(String... widgets) throws Exception {
        Document document = new Document();
        document.setFile(form("form", widgets).toString());
        try {
            return regenerate(document, 0);
        } finally {
            document.dispose();
        }
    }

    private static float[] numbersBefore(String content, String operator) {
        Matcher m = Pattern.compile("(-?[\\d.]+) (-?[\\d.]+) " + operator).matcher(content);
        assertTrue(m.find(), "no " + operator + " in\n" + content);
        return new float[]{Float.parseFloat(m.group(1)), Float.parseFloat(m.group(2))};
    }

    @DisplayName("single line: /DA font and size, 1pt padding clip, capitals centred on the baseline")
    @Test
    void singleLine() throws Exception {
        String content = generated(text("name", "10 10 210 30", "/DA (/Helv 12 Tf 0 0 1 rg) /V (Hello)"));
        assertTrue(content.contains("/Tx BMC"), content);
        assertTrue(content.contains("1 1 198 18 re W n"), "clip padded by 1:\n" + content);
        assertTrue(content.contains("/Helv 12 Tf 0 0 1 rg"), "the /DA as written:\n" + content);
        float[] td = numbersBefore(content, "Td");
        assertEquals(2, td[0], 0.01, "text padded by 1 more");
        // Helvetica cap height 718: (18 - 8.616) / 2 + 1
        assertEquals(5.692, td[1], 0.01, "baseline");
        assertTrue(content.contains("(Hello) Tj"), content);
    }

    @DisplayName("quadding centres and right-aligns a single line by its advance")
    @Test
    void quadding() throws Exception {
        // "Hello" in Helvetica 12 = (722+556+222+222+556) * 0.012 = 27.336
        float[] centred = numbersBefore(generated(text("c", "0 0 200 20", "/DA (/Helv 12 Tf 0 g) /Q 1 /V (Hello)")), "Td");
        assertEquals(2 + (196 - 27.336) / 2, centred[0], 0.05);
        float[] right = numbersBefore(generated(text("r", "0 0 200 20", "/DA (/Helv 12 Tf 0 g) /Q 2 /V (Hello)")), "Td");
        assertEquals(2 + 196 - 27.336, right[0], 0.05);
    }

    @DisplayName("multi-line text wraps at word breaks, one line per Tj, a font bbox apart")
    @Test
    void multiLineWraps() throws Exception {
        String content = generated(text("notes", "0 0 100 100",
                "/Ff 4096 /DA (/Helv 10 Tf 0 g) /V (The quick brown fox jumps over the lazy dog again and again)"));
        List<String> lines = new ArrayList<>();
        Matcher m = Pattern.compile("\\(([^)]*)\\) Tj").matcher(content);
        while (m.find()) lines.add(m.group(1));
        assertTrue(lines.size() >= 3, "wrapped: " + lines);
        assertEquals("The quick brown fox jumps over the lazy dog again and again", String.join("", lines).trim()
                .replaceAll("\\s+", " "), "no text lost: " + lines);
        // leading = Helvetica bbox height 1156 * 10 / 1000
        assertTrue(content.contains(" -11.56 Td"), "leading:\n" + content);
    }

    @DisplayName("a line break in a single-line field shows as a space")
    @Test
    void singleLineBreaks() throws Exception {
        String content = generated(text("one", "0 0 300 20", "/DA (/Helv 10 Tf 0 g) /V (first\\nsecond)"));
        assertTrue(content.contains("(first second) Tj"), content);
    }

    @DisplayName("a comb field puts one character centred in each MaxLen cell")
    @Test
    void comb() throws Exception {
        String content = generated(text("code", "0 0 100 20", "/Ff 16777216 /MaxLen 5 /DA (/Helv 10 Tf 0 g) /V (ABC)"));
        Matcher m = Pattern.compile("(-?[\\d.]+) (-?[\\d.]+) Td \\((.)\\) Tj").matcher(content);
        float x = 0;
        List<Float> xs = new ArrayList<>();
        while (m.find()) {
            x += Float.parseFloat(m.group(1));
            xs.add(x);
        }
        assertEquals(3, xs.size(), content);
        // cells 20 wide; A (667) and B (667) centred: 20 * i + (20 - 6.67) / 2
        assertEquals((20 - 6.67) / 2, xs.get(0), 0.05);
        assertEquals(20 + (20 - 6.67) / 2, xs.get(1), 0.05);
    }

    @DisplayName("font size 0 fits a single line to the box")
    @Test
    void autoSize() throws Exception {
        String content = generated(text("auto", "0 0 300 20", "/DA (/Helv 0 Tf 0 g) /V (Fits)"));
        Matcher m = Pattern.compile("/Helv ([\\d.]+) Tf").matcher(content);
        assertTrue(m.find(), content);
        float size = Float.parseFloat(m.group(1));
        // content height 16 / ((718 + 207) / 1000)
        assertEquals(16 / 0.925, size, 0.05, "height-limited auto size");
        String narrow = generated(text("auto", "0 0 40 20", "/DA (/Helv 0 Tf 0 g) /V (A long value)"));
        m = Pattern.compile("/Helv ([\\d.]+) Tf").matcher(narrow);
        assertTrue(m.find());
        // content width 36 over the value's advance at size 1 (5.504 em)
        assertEquals(36 / 5.504, Float.parseFloat(m.group(1)), 0.05, "width-limited");
    }

    @DisplayName("/MK background and border are drawn, and a beveled border's bands")
    @Test
    void appearanceCharacteristics() throws Exception {
        String content = generated(text("mk", "0 0 100 20",
                "/DA (/Helv 10 Tf 0 g) /V (x) /MK << /BG [0.9] /BC [1 0 0] >> /BS << /W 2 /S /B >>"));
        assertTrue(content.indexOf("0.9 g\n0 0 100 20 re f") >= 0, "background:\n" + content);
        assertTrue(content.contains("1 0 0 RG"), "border colour:\n" + content);
        assertTrue(content.contains("2 w"), "border width:\n" + content);
        assertTrue(content.contains("1 1 98 18 re s"), "border inset by half its width:\n" + content);
        assertTrue(content.contains("1 g\n"), "bevel light band:\n" + content);
        assertTrue(content.indexOf("re f") < content.indexOf("/Tx BMC"), "drawn before the text section");
        // beveled pads twice the border width: clip at 4
        assertTrue(content.contains("4 4 92 12 re W n"), "beveled padding:\n" + content);
    }

    @DisplayName("without /MK, what an existing appearance draws outside /Tx BMC ... EMC is kept")
    @Test
    void keepsExistingAppearance() throws Exception {
        String stream = "0.5 g 0 0 100 20 re f /Tx BMC BT /Helv 10 Tf (old) Tj ET EMC 0 G 0 0 m 100 0 l S";
        String ap = "<< /Type /XObject /Subtype /Form /BBox [0 0 100 20] /Resources << /Font << /Helv 4 0 R >> >> "
                + "/Length " + stream.length() + " >>\nstream\n" + stream + "\nendstream";
        Document document = new Document();
        document.setFile(form("keep", List.of(text("kept", "0 0 100 20", "/DA (/Helv 10 Tf 0 g) /V (new) /AP << /N 6 0 R >>")),
                List.of(ap)).toString());
        try {
            String content = regenerate(document, 0);
            assertTrue(content.startsWith("0.5 g 0 0 100 20 re f /Tx BMC"), content);
            assertTrue(content.contains("(new) Tj"), content);
            assertFalse(content.contains("(old)"), content);
            assertTrue(content.trim().endsWith("EMC 0 G 0 0 m 100 0 l S"), content);
        } finally {
            document.dispose();
        }
    }

    @DisplayName("a list box shows its options and highlights the selected one in Acrobat's colour")
    @Test
    void listBox() throws Exception {
        String content = generated("<< /Type /Annot /Subtype /Widget /FT /Ch /T (list) /Rect [0 0 100 60] /F 4 "
                + "/DA (/Helv 10 Tf 0 g) /Opt [(Apple) (Banana) (Cherry)] /V (Banana) >>");
        assertTrue(content.contains("0.6 0.7569 0.8431 rg"), "highlight colour:\n" + content);
        Matcher highlight = Pattern.compile("1 (-?[\\d.]+) 98 11.56 re f").matcher(content);
        assertTrue(highlight.find(), "one row high:\n" + content);
        // second row: top 59 - 2 * 11.56 + 2
        assertEquals(59 - 2 * 11.56 + 2, Float.parseFloat(highlight.group(1)), 0.01);
        assertTrue(content.contains("(Apple) Tj") && content.contains("(Banana) Tj") && content.contains("(Cherry) Tj"),
                content);
    }

    @DisplayName("a combo box shows its selected option's label")
    @Test
    void comboLabel() throws Exception {
        String content = generated("<< /Type /Annot /Subtype /Widget /FT /Ch /Ff 131072 /T (combo) /Rect [0 0 100 20] "
                + "/F 4 /DA (/Helv 10 Tf 0 g) /Opt [[(ca) (Canada)] [(us) (United States)]] /V (ca) >>");
        assertTrue(content.contains("(Canada) Tj"), content);
    }

    @DisplayName("/MK /R 90 swaps the box and rotates it with the form matrix")
    @Test
    void rotation() throws Exception {
        Document document = new Document();
        document.setFile(form("rot", text("r", "0 0 20 100", "/DA (/Helv 10 Tf 0 g) /V (up) /MK << /R 90 >>")).toString());
        try {
            regenerate(document, 0);
            Annotation widget = document.getPageTree().getPage(0).getAnnotations().get(0);
            org.icepdf.core.pobjects.Form form = (org.icepdf.core.pobjects.Form) widget.getAppearanceStream();
            assertEquals(100, form.getBBox().getWidth(), 0.01);
            assertEquals(20, form.getBBox().getHeight(), 0.01);
            AffineTransform matrix = form.getMatrix();
            assertEquals(0, matrix.getScaleX(), 1e-6);
            assertEquals(1, matrix.getShearY(), 1e-6);
        } finally {
            document.dispose();
        }
    }

    @DisplayName("the generated appearance renders the value and survives a save")
    @Test
    void rendersAndSaves() throws Exception {
        Document document = new Document();
        Path saved = temp.resolve("saved.pdf");
        document.setFile(form("render", text("r", "50 300 350 340", "/DA (/Helv 24 Tf 0 g) /V (IIIIII)")).toString());
        regenerate(document, 0);
        try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(saved))) {
            document.saveToOutputStream(out, WriteMode.INCREMENT_UPDATE);
        }
        document.dispose();

        Document reopened = new Document();
        reopened.setFile(saved.toString());
        try {
            Page page = reopened.getPageTree().getPage(0);
            page.init();
            BufferedImage image = new BufferedImage(400, 400, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = image.createGraphics();
            page.paint(g, GraphicsRenderingHints.PRINT, Page.BOUNDARY_CROPBOX, 0, 1f, true, false);
            g.dispose();
            int dark = 0;
            for (int y = 60; y < 100; y++) {
                for (int x = 50; x < 350; x++) {
                    if ((image.getRGB(x, y) & 0xFF) < 100) dark++;
                }
            }
            assertTrue(dark > 200, "the value is drawn: " + dark + " dark pixels");
        } finally {
            reopened.dispose();
        }
    }

    @DisplayName("a /DA font the document doesn't have falls back to Helvetica, with the resource added")
    @Test
    void missingFontFallsBack() throws Exception {
        Document document = new Document();
        document.setFile(form("missing", text("m", "0 0 200 20", "/DA (/TiBo 0 Tf 0 g) /V (Shown)")).toString());
        try {
            String content = regenerate(document, 0);
            assertTrue(content.matches("(?s).*/Helv [\\d.]+ Tf 0 g.*"), "Helvetica, size and colour kept:\n" + content);
            Annotation widget = document.getPageTree().getPage(0).getAnnotations().get(0);
            org.icepdf.core.pobjects.Form form = (org.icepdf.core.pobjects.Form) widget.getAppearanceStream();
            assertNotNull(form.getResources().getFont(new org.icepdf.core.pobjects.Name("Helv")),
                    "the appearance's resources hold the font it uses");
        } finally {
            document.dispose();
        }
    }

    @DisplayName("an existing appearance whose resources lack the /DA font gets its own copy with the font")
    @Test
    void addsFontToExistingResources() throws Exception {
        String stream = "/Tx BMC EMC";
        String ap = "<< /Type /XObject /Subtype /Form /BBox [0 0 100 20] /Resources 7 0 R /Length "
                + stream.length() + " >>\nstream\n" + stream + "\nendstream";
        Document document = new Document();
        document.setFile(form("shared", List.of(text("s", "0 0 100 20", "/DA (/Helv 10 Tf 0 g) /V (x) /AP << /N 6 0 R >>")),
                List.of(ap, "<< /ProcSet [/PDF] >>")).toString());
        try {
            regenerate(document, 0);
            Annotation widget = document.getPageTree().getPage(0).getAnnotations().get(0);
            org.icepdf.core.pobjects.Form form = (org.icepdf.core.pobjects.Form) widget.getAppearanceStream();
            assertNotNull(form.getResources().getFont(new org.icepdf.core.pobjects.Name("Helv")));
            Object shared = document.getCatalog().getLibrary().getObject(new org.icepdf.core.pobjects.Reference(7, 0));
            org.icepdf.core.pobjects.DictionaryEntries sharedEntries = shared instanceof org.icepdf.core.pobjects.Dictionary
                    ? ((org.icepdf.core.pobjects.Dictionary) shared).getEntries() : (org.icepdf.core.pobjects.DictionaryEntries) shared;
            assertFalse(sharedEntries.containsKey(org.icepdf.core.pobjects.Resources.FONT_KEY),
                    "the shared resources are left alone");
        } finally {
            document.dispose();
        }
    }
}
