/*
 * Copyright 2026 Patrick Corless
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package org.icepdf.core.pobjects.fonts.zfont;

import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.Page;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * An embedded encoding CMap stream should carry /CMapName but not every producer writes it.  Type0Font read it
 * unchecked, so the font's init() threw a NullPointerException and the whole font fell back to substitution
 * (Aruba - RR Copy.pdf).
 */
public class EmbeddedCMapWithoutNameTest {

    @DisplayName("a Type0 font whose embedded CMap has no /CMapName initializes")
    @Test
    public void embeddedCMapWithoutNameLoads() throws Exception {
        List<LogRecord> warnings = new ArrayList<>();
        Handler capture = new Handler() {
            @Override
            public void publish(LogRecord record) {
                if (record.getLevel().intValue() >= Level.WARNING.intValue()) {
                    warnings.add(record);
                }
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        Logger resourcesLogger = Logger.getLogger("org.icepdf.core.pobjects.Resources");
        resourcesLogger.addHandler(capture);
        Document document = new Document();
        try {
            byte[] pdf = pdf().getBytes(StandardCharsets.ISO_8859_1);
            document.setByteArray(pdf, 0, pdf.length, "cmap-without-name.pdf");
            Page page = document.getPageTree().getPage(0);
            page.init();
            org.icepdf.core.pobjects.fonts.Font font =
                    page.getResources().getFont(new org.icepdf.core.pobjects.Name("F1"));
            assertInstanceOf(Type0Font.class, font);
        } finally {
            resourcesLogger.removeHandler(capture);
            document.dispose();
        }
        assertTrue(warnings.isEmpty(), "font init failed: " + (warnings.isEmpty() ? "" :
                warnings.get(0).getMessage() + " " + warnings.get(0).getThrown()));
    }

    private static String pdf() {
        String cmap = "/CIDInit /ProcSet findresource begin 12 dict begin begincmap\n"
                + "/CIDSystemInfo << /Registry (Adobe) /Ordering (Identity) /Supplement 0 >> def\n"
                + "/CMapName /Custom-H def /CMapType 1 def\n"
                + "1 begincodespacerange <0000> <FFFF> endcodespacerange\n"
                + "1 begincidrange <0000> <FFFF> 0 endcidrange\n"
                + "endcmap CMapName currentdict /CMap defineresource pop end end\n";
        String content = "BT /F1 12 Tf 10 10 Td <0041> Tj ET";
        List<String> objects = new ArrayList<>();
        objects.add("<< /Type /Catalog /Pages 2 0 R >>");
        objects.add("<< /Type /Pages /Kids [3 0 R] /Count 1 >>");
        objects.add("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 200 200] /Resources << /Font << /F1 5 0 R >> >>"
                + " /Contents 4 0 R >>");
        objects.add("<< /Length " + content.length() + " >>\nstream\n" + content + "\nendstream");
        objects.add("<< /Type /Font /Subtype /Type0 /BaseFont /TestFont /Encoding 6 0 R /DescendantFonts [7 0 R] >>");
        // the encoding CMap stream deliberately has no /CMapName
        objects.add("<< /Type /CMap /Length " + cmap.length() + " >>\nstream\n" + cmap + "\nendstream");
        objects.add("<< /Type /Font /Subtype /CIDFontType2 /BaseFont /TestFont"
                + " /CIDSystemInfo << /Registry (Adobe) /Ordering (Identity) /Supplement 0 >> /FontDescriptor 8 0 R >>");
        objects.add("<< /Type /FontDescriptor /FontName /TestFont /Flags 4 /FontBBox [0 -200 1000 800] /ItalicAngle 0"
                + " /Ascent 800 /Descent -200 /CapHeight 700 /StemV 80 >>");
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
        return pdf.toString();
    }
}
