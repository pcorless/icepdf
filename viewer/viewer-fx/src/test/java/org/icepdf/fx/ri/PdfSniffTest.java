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
package org.icepdf.fx.ri;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** What a drop accepts as a PDF, no toolkit. */
class PdfSniffTest {

    @DisplayName("a .pdf name, or a %PDF- header in the first 1024 bytes; not a folder or other file")
    @Test
    void sniffs(@TempDir Path dir) throws Exception {
        Path named = Files.writeString(dir.resolve("named.PDF"), "anything");
        Path bare = Files.writeString(dir.resolve("download"), "%PDF-1.7\n...");
        Path junk = Files.writeString(dir.resolve("junk.bin"), "x".repeat(100) + "%PDF-1.4");
        Path late = Files.writeString(dir.resolve("late.bin"), "x".repeat(2000) + "%PDF-1.4");
        Path text = Files.writeString(dir.resolve("notes.txt"), "hello");
        assertTrue(PdfViewer.isPdf(named.toFile()));
        assertTrue(PdfViewer.isPdf(bare.toFile()));
        assertTrue(PdfViewer.isPdf(junk.toFile()));
        assertFalse(PdfViewer.isPdf(late.toFile()));
        assertFalse(PdfViewer.isPdf(text.toFile()));
        assertFalse(PdfViewer.isPdf(dir.toFile()));
    }
}
