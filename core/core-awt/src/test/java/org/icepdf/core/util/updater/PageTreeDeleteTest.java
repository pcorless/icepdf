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

package org.icepdf.core.util.updater;

import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.Page;
import org.icepdf.core.pobjects.PageTree;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link Document#deletePage(Page)} against a page tree with intermediate nodes, checked twice: in the open
 * document, whose page tree caches its kids, and in the saved file reopened.
 * <p>
 * The fixture tree, with each page's MediaBox width (its identity here) in brackets:
 * <pre>
 *   root 2
 *     node 3:  page 5 [105], page 6 [106]
 *     page 8 [108]
 *     node 4:  /Kids 9 0 R (an indirect array) = page 7 [107]
 * </pre>
 * so the document reads 105, 106, 108, 107.
 */
public class PageTreeDeleteTest {

    @DisplayName("deleting a page inside a nested node shifts the live tree and decrements every /Count")
    @Test
    public void deleteFromNestedNode() throws Exception {
        assertDelete(1, List.of(105, 108, 107));
    }

    @DisplayName("deleting a page listed directly by the root")
    @Test
    public void deleteFromRoot() throws Exception {
        assertDelete(2, List.of(105, 106, 107));
    }

    @DisplayName("deleting the only page of a node whose /Kids is an indirect array")
    @Test
    public void deleteFromIndirectKids() throws Exception {
        assertDelete(3, List.of(105, 106, 108));
    }

    @DisplayName("deleting two pages in turn, the second looked up after the first delete")
    @Test
    public void deleteTwice() throws Exception {
        Document document = open(nestedTree());
        try {
            document.deletePage(document.getPageTree().getPage(0));
            document.deletePage(document.getPageTree().getPage(0));
            assertEquals(List.of(108, 107), widths(document));
        } finally {
            document.dispose();
        }
    }

    private static void assertDelete(int pageIndex, List<Integer> expected) throws Exception {
        for (WriteMode writeMode : WriteMode.values()) {
            byte[] saved;
            Document document = open(nestedTree());
            try {
                assertEquals(List.of(105, 106, 108, 107), widths(document));
                document.deletePage(document.getPageTree().getPage(pageIndex));
                assertEquals(expected, widths(document), "live tree");
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                document.saveToOutputStream(out, writeMode);
                saved = out.toByteArray();
            } finally {
                document.dispose();
            }
            Document reopened = open(saved);
            try {
                assertEquals(expected, widths(reopened), writeMode + " saved tree");
            } finally {
                reopened.dispose();
            }
        }
    }

    private static List<Integer> widths(Document document) {
        PageTree pageTree = document.getPageTree();
        List<Integer> widths = new ArrayList<>();
        for (int i = 0; i < pageTree.getNumberOfPages(); i++) {
            Page page = pageTree.getPage(i);
            widths.add(Math.round(page.getMediaBox().width));
        }
        return widths;
    }

    private static Document open(byte[] pdf) throws Exception {
        Document document = new Document();
        document.setInputStream(new ByteArrayInputStream(pdf), "page-tree-delete");
        return document;
    }

    private static byte[] nestedTree() {
        String[] objects = {
                "<< /Type /Catalog /Pages 2 0 R >>",
                "<< /Type /Pages /Kids [3 0 R 8 0 R 4 0 R] /Count 4 >>",
                "<< /Type /Pages /Parent 2 0 R /Kids [5 0 R 6 0 R] /Count 2 >>",
                "<< /Type /Pages /Parent 2 0 R /Kids 9 0 R /Count 1 >>",
                page(3, 105),
                page(3, 106),
                page(4, 107),
                page(2, 108),
                "[7 0 R]",
        };
        StringBuilder pdf = new StringBuilder("%PDF-1.7\n");
        int[] offsets = new int[objects.length];
        for (int i = 0; i < objects.length; i++) {
            offsets[i] = pdf.length();
            pdf.append(i + 1).append(" 0 obj\n").append(objects[i]).append("\nendobj\n");
        }
        int xref = pdf.length();
        pdf.append("xref\n0 ").append(objects.length + 1).append("\n0000000000 65535 f \n");
        for (int offset : offsets) {
            pdf.append(String.format("%010d 00000 n \n", offset));
        }
        pdf.append("trailer\n<< /Size ").append(objects.length + 1).append(" /Root 1 0 R >>\nstartxref\n")
                .append(xref).append("\n%%EOF\n");
        return pdf.toString().getBytes(StandardCharsets.US_ASCII);
    }

    private static String page(int parent, int width) {
        return "<< /Type /Page /Parent " + parent + " 0 R /MediaBox [0 0 " + width + " 200] >>";
    }
}
