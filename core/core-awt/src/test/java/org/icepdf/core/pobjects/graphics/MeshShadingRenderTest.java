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

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * GH-506 added mesh shadings (types 4-7).  They were validated against the QA corpus but had no
 * automated test, so a parsing or tessellation regression would only show up in the image compare.
 * Each case builds a one-page PDF whose only content is {@code /Sh0 sh} over a 200x200 page, renders it
 * and checks colours at known points.  Mesh data is 8 bits per coordinate/component/flag, with a
 * Decode that maps coordinates 1:1 onto page space.
 */
public class MeshShadingRenderTest {

    private static final int SIZE = 200;
    private static final String DECODE_RGB = "/Decode [0 255 0 255 0 1 0 1 0 1]";

    @DisplayName("type 4 free-form triangles: solid and interpolated triangles")
    @Test
    public void freeFormTriangleMesh() throws Exception {
        MeshData mesh = new MeshData();
        // lower-left half solid red
        mesh.vertex(0, 0, 0, 255, 0, 0).vertex(0, 200, 0, 255, 0, 0).vertex(0, 0, 200, 255, 0, 0);
        // upper-right half: red, green and blue corners
        mesh.vertex(0, 200, 200, 0, 0, 255).vertex(0, 200, 0, 0, 255, 0).vertex(0, 0, 200, 255, 0, 0);
        BufferedImage image = render(4, "/BitsPerFlag 8", mesh);

        assertColor(image, 30, 30, new Color(255, 0, 0));
        assertColor(image, 198, 198, new Color(0, 0, 255));
        // interpolated interior of the second triangle is a mix, not any one corner
        Color mid = pixel(image, 140, 140);
        assertTrue(mid.getRed() > 20 && mid.getGreen() > 20 && mid.getBlue() > 20, "not interpolated: " + mid);
    }

    @DisplayName("type 5 lattice: a 2x2 lattice fills the page")
    @Test
    public void latticeMesh() throws Exception {
        MeshData mesh = new MeshData();
        mesh.point(0, 0).rgb(0, 255, 0).point(200, 0).rgb(0, 255, 0)
                .point(0, 200).rgb(0, 255, 0).point(200, 200).rgb(0, 255, 0);
        BufferedImage image = render(5, "/VerticesPerRow 2", mesh);

        assertColor(image, 20, 20, new Color(0, 255, 0));
        assertColor(image, 180, 180, new Color(0, 255, 0));
        assertColor(image, 100, 100, new Color(0, 255, 0));
    }

    @DisplayName("type 6 Coons patch: a square patch fills the page with its corner colours")
    @Test
    public void coonsPatchMesh() throws Exception {
        MeshData mesh = new MeshData().flag(0);
        // boundary in spec order p00 p01 p02 p03 p13 p23 p33 p32 p31 p30 p20 p10
        squareBoundary(mesh);
        // corner colours: c00 c03 c33 c30
        mesh.rgb(255, 255, 0).rgb(255, 255, 0).rgb(255, 255, 0).rgb(255, 255, 0);
        BufferedImage image = render(6, "/BitsPerFlag 8", mesh);

        assertColor(image, 100, 100, new Color(255, 255, 0));
        assertColor(image, 10, 190, new Color(255, 255, 0));
        assertColor(image, 190, 10, new Color(255, 255, 0));
    }

    @DisplayName("type 7 tensor patch: interior control points are read, corner colours interpolate")
    @Test
    public void tensorPatchMesh() throws Exception {
        MeshData mesh = new MeshData().flag(0);
        squareBoundary(mesh);
        // interior p11 p12 p22 p21
        mesh.point(67, 67).point(67, 133).point(133, 133).point(133, 67);
        // c00 red, c03 red, c33 blue, c30 blue: left edge red, right edge blue
        mesh.rgb(255, 0, 0).rgb(255, 0, 0).rgb(0, 0, 255).rgb(0, 0, 255);
        BufferedImage image = render(7, "/BitsPerFlag 8", mesh);

        Color left = pixel(image, 5, 100);
        Color right = pixel(image, 195, 100);
        Color centre = pixel(image, 100, 100);
        assertTrue(left.getRed() > 200 && left.getBlue() < 60, "left edge should be red: " + left);
        assertTrue(right.getBlue() > 200 && right.getRed() < 60, "right edge should be blue: " + right);
        assertTrue(centre.getRed() > 60 && centre.getBlue() > 60, "centre should blend: " + centre);
    }

    private static void squareBoundary(MeshData mesh) {
        mesh.point(0, 0).point(0, 67).point(0, 133).point(0, 200)
                .point(67, 200).point(133, 200).point(200, 200)
                .point(200, 133).point(200, 67).point(200, 0)
                .point(133, 0).point(67, 0);
    }

    /** Mesh stream bytes, one byte per flag, coordinate and colour component. */
    private static final class MeshData {
        private final List<Integer> bytes = new ArrayList<>();

        MeshData flag(int flag) {
            bytes.add(flag);
            return this;
        }

        MeshData point(int x, int y) {
            bytes.add(x);
            bytes.add(y);
            return this;
        }

        MeshData rgb(int r, int g, int b) {
            bytes.add(r);
            bytes.add(g);
            bytes.add(b);
            return this;
        }

        MeshData vertex(int flag, int x, int y, int r, int g, int b) {
            return flag(flag).point(x, y).rgb(r, g, b);
        }

        String hex() {
            StringBuilder hex = new StringBuilder();
            for (int b : bytes) {
                hex.append(String.format("%02X", b));
            }
            return hex.append('>').toString();
        }
    }

    private static BufferedImage render(int shadingType, String typeEntries, MeshData mesh) throws Exception {
        String data = mesh.hex();
        // clipped first: a bare `sh` with no clip paints nothing at all (any shading type, 7.4.1
        // too), because consume_sh fills the current clip and the initial clip is null.
        String content = "0 0 200 200 re W n /Sh0 sh";
        List<String> objects = new ArrayList<>();
        objects.add("<< /Type /Catalog /Pages 2 0 R >>");
        objects.add("<< /Type /Pages /Kids [3 0 R] /Count 1 >>");
        objects.add("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 " + SIZE + " " + SIZE + "]"
                + " /Resources << /Shading << /Sh0 5 0 R >> >> /Contents 4 0 R >>");
        objects.add("<< /Length " + content.length() + " >>\nstream\n" + content + "\nendstream");
        objects.add("<< /ShadingType " + shadingType + " /ColorSpace /DeviceRGB /BitsPerCoordinate 8"
                + " /BitsPerComponent 8 " + typeEntries + " " + DECODE_RGB
                + " /Filter /ASCIIHexDecode /Length " + data.length() + " >>\nstream\n" + data + "\nendstream");

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
            document.setByteArray(bytes, 0, bytes.length, "mesh-type-" + shadingType + ".pdf");
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

    /** Pixel at PDF user-space (x, y); the image is y-down. */
    private static Color pixel(BufferedImage image, int x, int y) {
        return new Color(image.getRGB(Math.min(x, SIZE - 1), Math.min(SIZE - 1 - y, SIZE - 1)));
    }

    private static void assertColor(BufferedImage image, int x, int y, Color expected) {
        Color actual = pixel(image, x, y);
        int tolerance = 12;
        assertTrue(Math.abs(actual.getRed() - expected.getRed()) <= tolerance
                        && Math.abs(actual.getGreen() - expected.getGreen()) <= tolerance
                        && Math.abs(actual.getBlue() - expected.getBlue()) <= tolerance,
                "at (" + x + "," + y + ") expected " + expected + " but was " + actual);
    }
}
