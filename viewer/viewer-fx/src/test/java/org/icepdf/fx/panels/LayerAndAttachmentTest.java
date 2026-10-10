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
package org.icepdf.fx.panels;

import org.icepdf.core.pobjects.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** The layer and attachment models behind the side panels, on core's small redaction fixtures. */
class LayerAndAttachmentTest {

    private static final Path FIXTURES = Paths.get("../../core/core-awt/src/test/resources/redaction");

    private static Document open(String name) throws Exception {
        Path file = FIXTURES.resolve(name);
        assumeTrue(Files.isRegularFile(file), "core fixture not found: " + file);
        Document document = new Document();
        document.setFile(file.toString());
        return document;
    }

    @DisplayName("a document without /Order lists its groups flat; /D /OFF starts hidden; toggling shows")
    @Test
    void layers() throws Exception {
        Document document = open("hidden_layer.pdf");
        try {
            List<LayerNode> layers = LayerNode.of(document);
            assertEquals(1, layers.size());
            LayerNode layer = layers.get(0);
            assertEquals("hidden layer", layer.name());
            assertFalse(layer.isLabel());
            assertFalse(layer.isVisible());
            layer.setVisible(true);
            assertTrue(layer.isVisible());
            assertTrue(layer.group().isVisible());
        } finally {
            document.dispose();
        }
        Document plain = open("hidden_copies.pdf");
        try {
            assertTrue(LayerNode.of(plain).isEmpty());
        } finally {
            plain.dispose();
        }
    }

    @DisplayName("an embedded file: name, description, content")
    @Test
    void attachments() throws Exception {
        Document document = open("hidden_copies.pdf");
        try {
            List<Attachment> attachments = Attachment.of(document);
            assertEquals(1, attachments.size());
            Attachment attachment = attachments.get(0);
            assertEquals("bravo source.txt", attachment.name());
            assertEquals("notes about bravo", attachment.description());
            assertTrue(attachment.hasContent());
            assertFalse(attachment.isPdf());
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            attachment.writeTo(out);
            assertEquals(22, out.size(), new String(out.toByteArray(), StandardCharsets.ISO_8859_1));
        } finally {
            document.dispose();
        }
        Document none = open("hidden_layer.pdf");
        try {
            assertTrue(Attachment.of(none).isEmpty());
        } finally {
            none.dispose();
        }
    }

    @DisplayName("sizes read as people write them")
    @Test
    void sizes() {
        assertEquals("", AttachmentPanel.formatSize(-1));
        assertEquals("512 B", AttachmentPanel.formatSize(512));
        assertTrue(AttachmentPanel.formatSize(2048).matches("2[.,]0 KB"));
        assertTrue(AttachmentPanel.formatSize(3 * 1024 * 1024).matches("3[.,]0 MB"));
    }
}
