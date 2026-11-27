/*
 * Copyright 2026 Patrick Corless
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an "AS
 * IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */
package org.icepdf.core.pobjects.graphics;

import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.Page;
import org.icepdf.core.util.GraphicsRenderingHints;
import org.junit.jupiter.api.Test;

import java.awt.color.ICC_ColorSpace;
import java.awt.color.ICC_Profile;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * GH-647: a document's CMYK output intent is the profile its DeviceCMYK means. The page paints magenta as a fill,
 * as a raw DeviceCMYK image, and as a Separation over DeviceCMYK, and black as {@code /Separation /Black}; each
 * must convert through the output intent's profile (a newsprint one, far from the default), and through the
 * default without one. Separation /Black paints as its alternate's ink, not as screen black.
 */
public class OutputIntentTest {

    private static final float[] MAGENTA = {0, 1, 0, 0};
    private static final float[] BLACK = {0, 0, 0, 1};

    private static byte[] newsprint() throws IOException {
        try (InputStream in = OutputIntentTest.class.getResourceAsStream("/cmyk/IFRA26S_2004_newsprint.icc")) {
            return in.readAllBytes();
        }
    }

    /**
     * A 100x100 page: magenta fill at x 0..25, a 1x1 magenta DeviceCMYK image at 25..50, a magenta Separation
     * at 50..75, and Separation /Black at 75..100.
     */
    private static byte[] pdf(byte[] profile, String subtype, int n) {
        String content = "0 1 0 0 k 0 0 25 100 re f\n"
                + "q 25 0 0 100 25 0 cm /Im1 Do Q\n"
                + "/CS0 cs 1 scn 50 0 25 100 re f\n"
                + "/CS1 cs 1 scn 75 0 25 100 re f\n";
        List<byte[]> objects = new ArrayList<>();
        String intents = profile == null ? "" : "/OutputIntents [<< /Type /OutputIntent /S /" + subtype
                + " /OutputConditionIdentifier (test) /DestOutputProfile 7 0 R >>]";
        objects.add(ascii("<< /Type /Catalog /Pages 2 0 R " + intents + " >>"));
        objects.add(ascii("<< /Type /Pages /Kids [3 0 R] /Count 1 >>"));
        objects.add(ascii("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 100 100] /Contents 4 0 R"
                + " /Resources << /XObject << /Im1 5 0 R >> /ColorSpace << /CS0 [/Separation /Magenta /DeviceCMYK 6 0 R]"
                + " /CS1 [/Separation /Black /DeviceCMYK 8 0 R] >> >> >>"));
        objects.add(stream("", content.getBytes(StandardCharsets.US_ASCII)));
        objects.add(stream("/Type /XObject /Subtype /Image /Width 1 /Height 1 /ColorSpace /DeviceCMYK"
                + " /BitsPerComponent 8", new byte[]{0, (byte) 255, 0, 0}));
        objects.add(ascii("<< /FunctionType 2 /Domain [0 1] /C0 [0 0 0 0] /C1 [0 1 0 0] /N 1 >>"));
        objects.add(profile == null ? ascii("null") : stream("/N " + n, profile));
        objects.add(ascii("<< /FunctionType 2 /Domain [0 1] /C0 [0 0 0 0] /C1 [0 0 0 1] /N 1 >>"));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        write(out, ascii("%PDF-1.4\n"));
        int[] offsets = new int[objects.size()];
        for (int i = 0; i < objects.size(); i++) {
            offsets[i] = out.size();
            write(out, ascii((i + 1) + " 0 obj\n"));
            write(out, objects.get(i));
            write(out, ascii("\nendobj\n"));
        }
        int xref = out.size();
        StringBuilder table = new StringBuilder("xref\n0 " + (objects.size() + 1) + "\n0000000000 65535 f \n");
        for (int offset : offsets) {
            table.append(String.format("%010d 00000 n \n", offset));
        }
        table.append("trailer\n<< /Size ").append(objects.size() + 1).append(" /Root 1 0 R >>\nstartxref\n")
                .append(xref).append("\n%%EOF\n");
        write(out, ascii(table.toString()));
        return out.toByteArray();
    }

    private static byte[] stream(String dictionary, byte[] data) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        write(out, ascii("<< " + dictionary + " /Length " + data.length + " >>\nstream\n"));
        write(out, data);
        write(out, ascii("\nendstream"));
        return out.toByteArray();
    }

    private static byte[] ascii(String s) {
        return s.getBytes(StandardCharsets.ISO_8859_1);
    }

    private static void write(ByteArrayOutputStream out, byte[] bytes) {
        out.write(bytes, 0, bytes.length);
    }

    /** The colours at the centre of the four strips. */
    private static int[] render(byte[] pdf) throws Exception {
        Document document = new Document();
        try {
            document.setInputStream(new ByteArrayInputStream(pdf), "output-intent.pdf");
            BufferedImage image = (BufferedImage) document.getPageImage(0, GraphicsRenderingHints.PRINT,
                    Page.BOUNDARY_CROPBOX, 0f, 1f);
            int[] colours = new int[4];
            for (int i = 0; i < 4; i++) {
                colours[i] = image.getRGB(12 + 25 * i, 50) & 0xffffff;
            }
            return colours;
        } finally {
            document.dispose();
        }
    }

    private static void assertColours(CmykColorConverter converter, int[] colours) {
        int magenta = converter.toArgb(MAGENTA.clone()) & 0xffffff;
        assertEquals(hex(magenta), hex(colours[0]), "fill");
        assertEquals(hex(magenta), hex(colours[1]), "image");
        assertEquals(hex(magenta), hex(colours[2]), "Separation over DeviceCMYK");
        assertEquals(hex(converter.toArgb(BLACK.clone()) & 0xffffff), hex(colours[3]), "Separation /Black");
    }

    private static String hex(int rgb) {
        return String.format("%06x", rgb);
    }

    @Test
    public void cmykOutputIntentIsTheDeviceCmykProfile() throws Exception {
        byte[] profile = newsprint();
        CmykColorConverter newsprint = new CmykColorConverter(new ICC_ColorSpace(ICC_Profile.getInstance(profile)));
        int[] colours = render(pdf(profile, "GTS_PDFX", 4));
        assertColours(newsprint, colours);
        // newsprint magenta is nowhere near coated magenta, so the intent really was used
        int coated = DeviceCMYK.getCmykColorConverter().toArgb(MAGENTA.clone()) & 0xffffff;
        int distance = 0;
        for (int shift = 0; shift < 24; shift += 8) {
            distance = Math.max(distance, Math.abs((coated >> shift & 255) - (colours[0] >> shift & 255)));
        }
        assertTrue(distance > 20, "newsprint " + hex(colours[0]) + " vs coated " + hex(coated));
    }

    @Test
    public void anyCmykIntentCountsNotOnlyPdfX() throws Exception {
        byte[] profile = newsprint();
        CmykColorConverter newsprint = new CmykColorConverter(new ICC_ColorSpace(ICC_Profile.getInstance(profile)));
        assertColours(newsprint, render(pdf(profile, "GTS_PDFA1", 4)));
    }

    @Test
    public void withoutAnIntentTheDefaultProfile() throws Exception {
        assertColours(DeviceCMYK.getCmykColorConverter(), render(pdf(null, null, 0)));
    }

    @Test
    public void anRgbIntentLeavesDeviceCmykAlone() throws Exception {
        byte[] srgb = ICC_Profile.getInstance(java.awt.color.ColorSpace.CS_sRGB).getData();
        assertColours(DeviceCMYK.getCmykColorConverter(), render(pdf(srgb, "GTS_PDFA1", 3)));
    }

    @Test
    public void aBrokenProfileFallsBackToTheDefault() throws Exception {
        assertColours(DeviceCMYK.getCmykColorConverter(),
                render(pdf("not a profile".getBytes(StandardCharsets.US_ASCII), "GTS_PDFX", 4)));
    }
}
