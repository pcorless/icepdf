/*
 * Copyright 2026 Patrick Corless
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package org.icepdf.core.pobjects.graphics;

import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.Page;
import org.icepdf.core.util.GraphicsRenderingHints;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Type 1 (function-based) shadings were a stub: the 2-in function was called with one input (a Type 4 function then
 * threw EmptyStackException) and getPaint() returned null, so the area filled black (PDF-1123-CIPC3.pdf buttons).
 * <p>
 * The function here, {@code {0}}, maps (x, y) to RGB (x, y, 0), so the colour shows exactly where in the domain each
 * pixel landed.
 */
public class ShadingType1RenderTest {

    private static final int SIZE = 100;

    @DisplayName("a type 1 shading evaluates its 2-in function across the domain")
    @Test
    public void functionIsEvaluatedOverTheDomain() throws Exception {
        // domain [0 1 0 1] scaled by /Matrix to the whole 100x100 page
        BufferedImage image = render("{0}", "[100 0 0 100 0 0]");
        Color lowerLeft = pixel(image, 5, 5);
        Color lowerRight = pixel(image, 95, 5);
        Color upperLeft = pixel(image, 5, 95);
        assertTrue(lowerLeft.getRed() < 30 && lowerLeft.getGreen() < 30, "x,y near 0: " + lowerLeft);
        assertTrue(lowerRight.getRed() > 220 && lowerRight.getGreen() < 30, "x near 1: " + lowerRight);
        assertTrue(upperLeft.getRed() < 30 && upperLeft.getGreen() > 220, "y near 1: " + upperLeft);
        assertTrue(lowerRight.getBlue() < 10 && upperLeft.getBlue() < 10, "third output is 0");
    }

    @DisplayName("outside the domain nothing is painted, and /Matrix places the domain")
    @Test
    public void paintsOnlyTheDomain() throws Exception {
        // domain mapped to the lower-left 50x50
        BufferedImage image = render("{0}", "[50 0 0 50 0 0]");
        assertTrue(pixel(image, 45, 5).getRed() > 200, "inside, x near 1: " + pixel(image, 45, 5));
        assertEquals(Color.WHITE, pixel(image, 75, 75), "outside the domain stays unpainted");
    }

    @DisplayName("a function that fails to evaluate doesn't abort the rest of the page")
    @Test
    public void brokenFunctionDoesNotAbortThePage() throws Exception {
        // pops more than it has: every evaluation fails; the blue square drawn after the shading must still appear
        BufferedImage image = render("{pop pop pop pop}", "[50 0 0 50 0 0]", " Q q 0 0 1 rg 60 60 30 30 re f");
        assertEquals(new Color(0, 0, 255), pixel(image, 75, 75));
    }

    private static Color pixel(BufferedImage image, int x, int y) {
        return new Color(image.getRGB(x, SIZE - 1 - y));
    }

    private static BufferedImage render(String function, String matrix) throws Exception {
        return render(function, matrix, "");
    }

    private static BufferedImage render(String function, String matrix, String after) throws Exception {
        String content = "q 0 0 " + SIZE + " " + SIZE + " re W n /Sh0 sh" + (after.isEmpty() ? " Q" : after);
        List<String> objects = new ArrayList<>();
        objects.add("<< /Type /Catalog /Pages 2 0 R >>");
        objects.add("<< /Type /Pages /Kids [3 0 R] /Count 1 >>");
        objects.add("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 " + SIZE + " " + SIZE + "]"
                + " /Resources << /Shading << /Sh0 5 0 R >> >> /Contents 4 0 R >>");
        objects.add("<< /Length " + content.length() + " >>\nstream\n" + content + "\nendstream");
        objects.add("<< /ShadingType 1 /ColorSpace /DeviceRGB /Domain [0 1 0 1] /Matrix " + matrix
                + " /Function 6 0 R >>");
        objects.add("<< /FunctionType 4 /Domain [0 1 0 1] /Range [0 1 0 1 0 1] /Length " + function.length()
                + " >>\nstream\n" + function + "\nendstream");
        StringBuilder pdf = new StringBuilder("%PDF-1.4\n");
        List<Integer> offsets = new ArrayList<>();
        for (int i = 0; i < objects.size(); i++) {
            offsets.add(pdf.length());
            pdf.append(i + 1).append(" 0 obj\n").append(objects.get(i)).append("\nendobj\n");
        }
        int xref = pdf.length();
        pdf.append("xref\n0 ").append(objects.size() + 1).append("\n0000000000 65535 f \n");
        for (int offset : offsets) {
            pdf.append(String.format("%010d 00000 n \n", offset));
        }
        pdf.append("trailer\n<< /Root 1 0 R /Size ").append(objects.size() + 1)
                .append(" >>\nstartxref\n").append(xref).append("\n%%EOF\n");
        byte[] bytes = pdf.toString().getBytes(StandardCharsets.ISO_8859_1);
        Document document = new Document();
        try {
            document.setByteArray(bytes, 0, bytes.length, "type1-shading.pdf");
            Page page = document.getPageTree().getPage(0);
            page.init();
            BufferedImage image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = image.createGraphics();
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, SIZE, SIZE);
            page.paint(g, GraphicsRenderingHints.PRINT, Page.BOUNDARY_CROPBOX, 0f, 1f);
            g.dispose();
            return image;
        } finally {
            document.dispose();
        }
    }
}
