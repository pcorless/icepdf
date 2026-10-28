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
package org.icepdf.core.util.updater.writeables;

import org.icepdf.core.io.CountingOutputStream;
import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.DictionaryEntries;
import org.icepdf.core.pobjects.LiteralStringObject;
import org.icepdf.core.pobjects.Name;
import org.icepdf.core.pobjects.PObject;
import org.icepdf.core.pobjects.Reference;
import org.icepdf.core.pobjects.security.SecurityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * GH-637: two saves in one JVM must not share writers.
 * <p>
 * The writers were static, and each save replaced them, so a save in progress wrote with whichever
 * save had started last - and the string and stream writers carry the document's security manager.
 * An unencrypted document saved alongside an encrypted one came out with its strings enciphered
 * under the other document's key: garbage dates that then failed to parse. Here the interleaving is
 * played out directly, with no threads, so it is the same every run.
 */
public class WriterIsolationTest {

    private static final String DATE = "D:20140910131836-06'00'";

    private static SecurityManager securityManagerOf(Document document) {
        SecurityManager securityManager = document.getCatalog().getLibrary().getSecurityManager();
        assertNotNull(securityManager, "the fixture is expected to be encrypted");
        return securityManager;
    }

    private static Document open(String fixture) throws Exception {
        Document document = new Document();
        try (InputStream stream = WriterIsolationTest.class.getResourceAsStream("/updater/" + fixture)) {
            assertNotNull(stream, "missing fixture " + fixture);
            document.setInputStream(stream, fixture);
        }
        return document;
    }

    private static String write(BaseWriter writer) throws Exception {
        DictionaryEntries annotation = new DictionaryEntries();
        // authored text: plain, so an encrypted save enciphers it and an unencrypted one doesn't
        annotation.put(new Name("M"), new LiteralStringObject(DATE, new Reference(12, 0)));
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        writer.writeValue(new PObject(annotation, new Reference(12, 0)), new CountingOutputStream(bytes));
        return bytes.toString(StandardCharsets.ISO_8859_1);
    }

    @DisplayName("an unencrypted save's strings stay plain when an encrypted save starts meanwhile")
    @Test
    void savesDoNotShareWriters() throws Exception {
        Document encrypted = open("encrypted-aes-v2.pdf");
        try {
            BaseWriter plain = new BaseWriter(null, null, new CountingOutputStream(new ByteArrayOutputStream()), 0);
            plain.initializeWriters();
            // the control: on its own the string is written as it is
            assertTrue(write(plain).contains(DATE), write(plain));

            // another save starts, for an encrypted document
            BaseWriter cipher = new BaseWriter(null, securityManagerOf(encrypted),
                    new CountingOutputStream(new ByteArrayOutputStream()), 0);
            cipher.initializeWriters();

            assertTrue(write(plain).contains(DATE), "the unencrypted save picked up the other's key: " + write(plain));
            assertFalse(write(cipher).contains(DATE), "and the encrypted one still encrypts: " + write(cipher));
        } finally {
            encrypted.dispose();
        }
    }

    /**
     * Content streams have no encryption of their own (the stream is encrypted as a whole), so the
     * dictionary writer used inside them must write strings as they are, whatever save is running.
     */
    @DisplayName("an inline dictionary in a content stream is written plain while an encrypted save runs")
    @Test
    void contentStreamDictionaryIsPlain() throws Exception {
        Document encrypted = open("encrypted-aes-v2.pdf");
        try {
            BaseWriter cipher = new BaseWriter(null, securityManagerOf(encrypted),
                    new CountingOutputStream(new ByteArrayOutputStream()), 0);
            cipher.initializeWriters();

            DictionaryEntries entries = new DictionaryEntries();
            entries.put(new Name("M"), new LiteralStringObject(DATE, new Reference(12, 0)));
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            org.icepdf.core.util.redaction.InlineImageWriter.dictionaryWriter.writeInline(entries,
                    new CountingOutputStream(bytes));
            assertTrue(bytes.toString(StandardCharsets.ISO_8859_1).contains(DATE), bytes.toString(StandardCharsets.ISO_8859_1));
        } finally {
            encrypted.dispose();
        }
    }
}
