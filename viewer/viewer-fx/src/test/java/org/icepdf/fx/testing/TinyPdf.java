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
package org.icepdf.fx.testing;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * One-page PDFs written by hand for tests: objects are numbered in the order added, the first
 * is the catalog, and the cross-reference table is worked out on {@link #write}.
 * <pre>{@code
 * TinyPdf pdf = TinyPdf.onePage("/Annots [4 0 R]", "");
 * pdf.add("<< /Type /Annot /Subtype /Link /Rect [0 0 50 50] /A << /S /URI /URI (http://x) >> >>");
 * pdf.write(path);
 * }</pre>
 */
public final class TinyPdf {

    private final List<byte[]> objects = new ArrayList<>();

    /**
     * Objects 1-3: the catalog (with {@code catalogExtra}), the page tree and one 200x200 page
     * (with {@code pageExtra}).  Further objects are numbered from 4.
     */
    public static TinyPdf onePage(String pageExtra, String catalogExtra) {
        TinyPdf pdf = new TinyPdf();
        pdf.add("<< /Type /Catalog /Pages 2 0 R " + catalogExtra + " >>");
        pdf.add("<< /Type /Pages /Kids [3 0 R] /Count 1 >>");
        pdf.add("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 200 200] " + pageExtra + " >>");
        return pdf;
    }

    /** A plain one-page document. */
    public static byte[] blank() {
        return onePage("", "").bytes();
    }

    /** Adds an object; @return its number */
    public int add(String body) {
        objects.add(body.getBytes(StandardCharsets.ISO_8859_1));
        return objects.size();
    }

    /** Adds a stream object with {@code dictionaryExtra} in its dictionary; @return its number */
    public int addStream(String dictionaryExtra, byte[] data) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(("<< " + dictionaryExtra + " /Length " + data.length + " >>\nstream\n").getBytes(StandardCharsets.ISO_8859_1));
        out.writeBytes(data);
        out.writeBytes("\nendstream".getBytes(StandardCharsets.ISO_8859_1));
        objects.add(out.toByteArray());
        return objects.size();
    }

    public byte[] bytes() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes("%PDF-1.7\n".getBytes(StandardCharsets.ISO_8859_1));
        long[] offsets = new long[objects.size()];
        for (int i = 0; i < objects.size(); i++) {
            offsets[i] = out.size();
            out.writeBytes(((i + 1) + " 0 obj\n").getBytes(StandardCharsets.ISO_8859_1));
            out.writeBytes(objects.get(i));
            out.writeBytes("\nendobj\n".getBytes(StandardCharsets.ISO_8859_1));
        }
        long xref = out.size();
        StringBuilder table = new StringBuilder("xref\n0 " + (objects.size() + 1) + "\n0000000000 65535 f \n");
        for (long offset : offsets) table.append(String.format("%010d 00000 n \n", offset));
        table.append("trailer\n<< /Size ").append(objects.size() + 1).append(" /Root 1 0 R >>\nstartxref\n")
                .append(xref).append("\n%%EOF\n");
        out.writeBytes(table.toString().getBytes(StandardCharsets.ISO_8859_1));
        return out.toByteArray();
    }

    public Path write(Path file) throws IOException {
        Files.write(file, bytes());
        return file;
    }
}
