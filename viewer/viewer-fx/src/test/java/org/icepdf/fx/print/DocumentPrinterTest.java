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
package org.icepdf.fx.print;

import org.icepdf.core.pobjects.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.print.DocFlavor;
import javax.print.StreamPrintService;
import javax.print.StreamPrintServiceFactory;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

class DocumentPrinterTest {

    @DisplayName("page ranges parse as typed: lists, open ends, order kept, past the end dropped")
    @Test
    void pageRanges() {
        assertArrayEquals(new int[]{0, 1, 2, 4}, PrintSettings.parsePageRange("1-3, 5", 10));
        assertArrayEquals(new int[]{7, 8, 9}, PrintSettings.parsePageRange("8-", 10));
        assertArrayEquals(new int[]{0, 1}, PrintSettings.parsePageRange("-2", 10));
        assertArrayEquals(new int[]{4, 0}, PrintSettings.parsePageRange("5,1", 10));
        assertArrayEquals(new int[]{9}, PrintSettings.parsePageRange("10-14", 10));
        assertNull(PrintSettings.parsePageRange("", 10));
        assertNull(PrintSettings.parsePageRange("3-1", 10));
        assertNull(PrintSettings.parsePageRange("0", 10));
        assertNull(PrintSettings.parsePageRange("a", 10));
        assertNull(PrintSettings.parsePageRange("11-12", 10), "nothing in range");
    }

    @DisplayName("fit scales to the printable area, shrink only shrinks, actual size is 1:1; all centred")
    @Test
    void placement() {
        // a letter page on an area of 540 x 720 at (36, 36)
        DocumentPrinter.Placement fit = DocumentPrinter.place(612, 792, 36, 36, 540, 720, PrintSettings.Scaling.FIT);
        assertEquals(Math.min(540 / 612.0, 720 / 792.0), fit.scale(), 1e-9);
        assertEquals(36 + (540 - 612 * fit.scale()) / 2, fit.x(), 1e-9);
        assertEquals(36 + (720 - 792 * fit.scale()) / 2, fit.y(), 1e-9);
        // a small page: fit enlarges, shrink-large leaves it alone
        assertTrue(DocumentPrinter.place(200, 100, 0, 0, 540, 720, PrintSettings.Scaling.FIT).scale() > 1);
        DocumentPrinter.Placement small = DocumentPrinter.place(200, 100, 0, 0, 540, 720,
                PrintSettings.Scaling.SHRINK_LARGE);
        assertEquals(1, small.scale(), 1e-9);
        assertEquals(170, small.x(), 1e-9);
        assertEquals(310, small.y(), 1e-9);
        // actual size overflows (centred, cropped by the clip)
        DocumentPrinter.Placement actual = DocumentPrinter.place(1000, 720, 0, 0, 540, 720,
                PrintSettings.Scaling.ACTUAL_SIZE);
        assertEquals(1, actual.scale(), 1e-9);
        assertEquals(-230, actual.x(), 1e-9);
    }

    @DisplayName("auto orientation turns the sheet for pages whose shape doesn't match it")
    @Test
    void orientation() {
        PrintSettings.Orientation auto = PrintSettings.Orientation.AUTO;
        assertFalse(DocumentPrinter.landscape(612, 792, 612, 792, auto));
        assertTrue(DocumentPrinter.landscape(792, 612, 612, 792, auto));
        assertFalse(DocumentPrinter.landscape(792, 612, 792, 612, auto), "landscape paper already");
        assertTrue(DocumentPrinter.landscape(612, 792, 612, 792, PrintSettings.Orientation.LANDSCAPE));
        assertFalse(DocumentPrinter.landscape(792, 612, 612, 792, PrintSettings.Orientation.PORTRAIT));
    }

    @DisplayName("prints to a PostScript file: one page per selected page, copies, progress reported")
    @Test
    void printsToPostScript() throws Exception {
        StreamPrintServiceFactory[] factories = StreamPrintServiceFactory.lookupStreamPrintServiceFactories(
                DocFlavor.SERVICE_FORMATTED.PAGEABLE, "application/postscript");
        assertTrue(factories.length > 0, "the JDK's PostScript stream service");
        Document document = new Document();
        document.setFile(Paths.get(Objects.requireNonNull(
                DocumentPrinterTest.class.getResource("/forms/all_fields.pdf")).toURI()).toString());
        Path out = Files.createTempFile("print", ".ps");
        try (OutputStream stream = Files.newOutputStream(out)) {
            StreamPrintService service = factories[0].getPrintService(stream);
            PrintSettings settings = new PrintSettings();
            settings.setPrinter(service);
            settings.setPages(new int[]{0, 0, 0});
            List<String> progress = new ArrayList<>();
            new DocumentPrinter(document, settings).print((page, total) -> progress.add(page + "/" + total));
            assertEquals(List.of("1/3", "2/3", "3/3"), progress);
        } finally {
            document.dispose();
        }
        String ps = Files.readString(out, StandardCharsets.ISO_8859_1);
        Files.deleteIfExists(out);
        assertTrue(ps.startsWith("%!PS"), "PostScript");
        assertEquals(3, ps.split("%%Page:", -1).length - 1, "three pages");
    }
}
