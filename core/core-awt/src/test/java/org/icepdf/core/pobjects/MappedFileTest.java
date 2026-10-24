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
package org.icepdf.core.pobjects;

import org.icepdf.core.util.updater.WriteMode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * {@link Document#MAP_FILE_PROPERTY}: a big enough file is mapped rather than copied, reads and
 * saves the same as a copy, and lets go of the mapping on dispose.
 */
public class MappedFileTest {

    private static final Path SOURCE = Paths.get("src/test/resources/acroform/all_fields.pdf");

    @TempDir
    Path dir;

    @BeforeEach
    public void mapEverything() {
        System.setProperty(Document.MAP_FILE_PROPERTY, "true");
        System.setProperty(Document.MAP_FILE_THRESHOLD_PROPERTY, "0");
    }

    @AfterEach
    public void restore() {
        System.clearProperty(Document.MAP_FILE_PROPERTY);
        System.clearProperty(Document.MAP_FILE_THRESHOLD_PROPERTY);
    }

    private Document open(Path file) throws Exception {
        Document document = new Document();
        document.setFile(file.toString());
        return document;
    }

    private static byte[] save(Document document) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        document.writeToOutputStream(out, WriteMode.FULL_UPDATE);
        return out.toByteArray();
    }

    @Test
    public void mappedReadsAndSavesAsCopied() throws Exception {
        Path file = Files.copy(SOURCE, dir.resolve("mapped.pdf"));
        Document mapped = open(file);
        assertTrue(mapped.isFileMapped());
        int pages = mapped.getNumberOfPages();
        String text = mapped.getPageText(0).toString();
        byte[] mappedSave = save(mapped);
        mapped.dispose();

        System.setProperty(Document.MAP_FILE_PROPERTY, "false");
        Document copied = open(file);
        assertFalse(copied.isFileMapped());
        assertEquals(pages, copied.getNumberOfPages());
        assertEquals(text, copied.getPageText(0).toString());
        assertArrayEquals(save(copied), mappedSave, "a mapped document saves the same bytes as a copied one");
        copied.dispose();
    }

    @Test
    public void belowThresholdIsCopied() throws Exception {
        System.setProperty(Document.MAP_FILE_THRESHOLD_PROPERTY, "64");
        Document document = open(Files.copy(SOURCE, dir.resolve("small.pdf")));
        assertFalse(document.isFileMapped());
        document.dispose();
    }

    @Test
    public void disposeLetsGoOfTheMapping() throws Exception {
        Path maps = Paths.get("/proc/self/maps");
        assumeTrue(Files.isReadable(maps), "needs /proc/self/maps (Linux)");
        Path file = Files.copy(SOURCE, dir.resolve("released.pdf"));
        String name = file.toRealPath().toString();
        Document document = open(file);
        document.getPageTree().getPage(0).init();
        assertTrue(Files.readString(maps).contains(name), "the file is mapped while open");

        document.dispose();
        document = null;
        boolean released = false;
        for (int i = 0; i < 20 && !released; i++) {
            System.gc();
            Thread.sleep(50);
            released = !Files.readString(maps).contains(name);
        }
        assertTrue(released, "the mapping is gone once the disposed document is collected");
        // and the file can be replaced, as a save does.
        Files.move(Files.copy(SOURCE, dir.resolve("saved.tmp")), file, StandardCopyOption.REPLACE_EXISTING);
    }
}
