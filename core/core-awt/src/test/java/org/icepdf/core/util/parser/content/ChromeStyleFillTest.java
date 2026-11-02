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
package org.icepdf.core.util.parser.content;

import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.Page;
import org.icepdf.core.util.GraphicsRenderingHints;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * GH-665: constructs Chrome/Skia write into every browser-printed PDF.  Each case is a 200 x 100 page rendered at
 * 1:1 onto white; image y = 100 - page y.
 */
public class ChromeStyleFillTest {

    @TempDir
    Path temp;

    private static final String PAGE = "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 200 100] /Contents 4 0 R"
            + " /Resources << %s >> >>";

    private Path pdf(String name, String resources, String content, String... extra) throws Exception {
        List<String> objects = new ArrayList<>();
        objects.add("<< /Type /Catalog /Pages 2 0 R >>");
        objects.add("<< /Type /Pages /Kids [3 0 R] /Count 1 >>");
        objects.add(String.format(PAGE, resources));
        objects.add(stream("", content));
        objects.addAll(Arrays.asList(extra));
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

    private static String stream(String dictionary, String data) {
        return "<< " + dictionary + " /Length " + data.length() + " >>\nstream\n" + data + "\nendstream";
    }

    private static BufferedImage render(Path file) throws Exception {
        Document document = new Document();
        document.setFile(file.toString());
        try {
            Page page = document.getPageTree().getPage(0);
            page.init();
            BufferedImage image = new BufferedImage(200, 100, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = image.createGraphics();
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, 200, 100);
            page.paint(g, GraphicsRenderingHints.SCREEN, Page.BOUNDARY_CROPBOX, 0f, 1f, true, false);
            g.dispose();
            return image;
        } finally {
            document.dispose();
        }
    }

    private static Color at(BufferedImage image, int x, int y) {
        return new Color(image.getRGB(x, y));
    }

    private static boolean dark(BufferedImage image, int x, int y) {
        Color c = at(image, x, y);
        return c.getRed() < 80 && c.getGreen() < 80 && c.getBlue() < 80;
    }

    private static boolean white(BufferedImage image, int x, int y) {
        Color c = at(image, x, y);
        return c.getRed() > 245 && c.getGreen() > 245 && c.getBlue() > 245;
    }

    private static boolean red(BufferedImage image, int x, int y) {
        Color c = at(image, x, y);
        return c.getRed() > 150 && c.getGreen() < 80 && c.getBlue() < 80;
    }

    private static boolean blue(BufferedImage image, int x, int y) {
        Color c = at(image, x, y);
        return c.getBlue() > 150 && c.getRed() < 80 && c.getGreen() < 80;
    }

    // a red 200 x 100 cell (red top half, blue bottom half when split) whose step is far larger than the
    // cell, so it paints as a single stamp
    private static String stampPattern(String cell) {
        return stream("/Type /Pattern /PatternType 1 /PaintType 1 /TilingType 1 /BBox [0 0 200 100]"
                + " /XStep 400 /YStep 400 /Resources << >>", cell);
    }

    @Test
    @DisplayName("Line width set only through an ExtGState is applied to the stroke")
    public void lineWidthFromExtGState() throws Exception {
        BufferedImage image = render(pdf("lw", "/ExtGState << /G0 << /LW 12 >> >>",
                "0 G /G0 gs 20 50 m 180 50 l S"));
        // a 12-unit line at y 50 covers image rows 44..56; a 1-unit line would leave these white
        assertTrue(dark(image, 100, 46), "upper edge of the 12-unit line");
        assertTrue(dark(image, 100, 54), "lower edge of the 12-unit line");
        assertTrue(white(image, 100, 40), "outside the line");
    }

    @Test
    @DisplayName("A shading-pattern fill through a luminosity soft mask fades instead of vanishing")
    public void shadingPatternFillThroughSoftMask() throws Exception {
        // colour: constant blue axial shading; mask: luminosity 0 at the bottom of the page to 1 at the top
        String colour = "<< /PatternType 2 /Shading << /ShadingType 2 /ColorSpace /DeviceRGB /Coords [0 0 0 100]"
                + " /Function << /FunctionType 2 /Domain [0 1] /C0 [0 0 1] /C1 [0 0 1] /N 1 >> >> >>";
        String ramp = "<< /PatternType 2 /Shading << /ShadingType 2 /ColorSpace /DeviceRGB /Coords [0 0 0 100]"
                + " /Extend [true true] /Function << /FunctionType 2 /Domain [0 1] /C0 [0 0 0] /C1 [1 1 1] /N 1 >> >> >>";
        String maskGroup = stream("/Type /XObject /Subtype /Form /BBox [0 0 200 100]"
                + " /Group << /S /Transparency /CS /DeviceRGB /I true >> /Resources << /Pattern << /R 6 0 R >> >>",
                "/Pattern cs /R scn 0 0 200 100 re f");
        BufferedImage image = render(pdf("smask",
                "/ExtGState << /M << /SMask << /Type /Mask /S /Luminosity /G 7 0 R >> >> >> /Pattern << /C 5 0 R >>",
                "/M gs /Pattern cs /C scn 20 10 160 80 re f",
                colour, ramp, maskGroup));
        Color top = at(image, 100, 15);     // page y 85: mostly opaque blue
        Color bottom = at(image, 100, 85);  // page y 15: mostly transparent
        assertTrue(top.getBlue() > 200 && top.getRed() < 90, "top of the fill is blue: " + top);
        assertTrue(bottom.getRed() > 190, "bottom of the fill has faded towards white: " + bottom);
        assertTrue(top.getRed() < bottom.getRed(), "the fill fades from top to bottom");
    }

    @Test
    @DisplayName("A single-stamp tiling pattern under a y-flipped CTM is painted, the right way up")
    public void singleStampUnderFlippedCtm() throws Exception {
        // pattern space is the page's default space: red over its top half, blue over its bottom half
        BufferedImage image = render(pdf("flip", "/Pattern << /T 5 0 R >>",
                "1 0 0 -1 0 100 cm /Pattern cs /T scn 20 10 160 80 re f",
                stampPattern("0.8 0.1 0.1 rg 0 50 200 50 re f 0.1 0.1 0.8 rg 0 0 200 50 re f")));
        assertTrue(red(image, 100, 30), "top half red: " + at(image, 100, 30));
        assertTrue(blue(image, 100, 70), "bottom half blue: " + at(image, 100, 70));
        assertTrue(white(image, 10, 50), "outside the fill");
    }

    @Test
    @DisplayName("A single-stamp tiling pattern fills the path, not the stamp's rectangle")
    public void singleStampFollowsThePath() throws Exception {
        BufferedImage image = render(pdf("triangle", "/Pattern << /T 5 0 R >>",
                "/Pattern cs /T scn 20 10 m 180 10 l 100 90 l h f",
                stampPattern("0.8 0.1 0.1 rg 0 0 200 100 re f")));
        assertTrue(red(image, 100, 60), "inside the triangle: " + at(image, 100, 60));
        assertTrue(white(image, 30, 20), "inside the stamp but outside the triangle: " + at(image, 30, 20));
    }
}
