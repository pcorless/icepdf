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
import org.icepdf.core.pobjects.HexStringObject;
import org.icepdf.core.pobjects.LiteralStringObject;
import org.icepdf.core.pobjects.Name;
import org.icepdf.core.pobjects.PInfo;
import org.icepdf.core.pobjects.PObject;
import org.icepdf.core.pobjects.StringObject;
import org.icepdf.core.pobjects.security.SecurityManager;
import org.icepdf.core.util.Library;
import org.icepdf.core.util.Utils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Writing strings that were authored after the document was opened, to a document that is
 * encrypted, with both of the ciphers the standard security handler uses.
 * <p>
 * RC4 and AES are not interchangeable here even though the handler presents them the same way.  RC4
 * is its own inverse, so code that runs the decipher in both directions works; AES is not, and the
 * same code ran AES decryption over plain text, failed its padding check and returned null - so no
 * string authored on an AES document could be written at all.  Everything encrypted in the corpus
 * was RC4, so nothing noticed.
 * <p>
 * AES also picks a fresh initialisation vector per call, which means the same string does not
 * encrypt to the same bytes twice.  Anything that serializes an object more than once and expects
 * the results to agree - signing writes the signature dictionary once to find its offsets and again
 * to write it back over itself - depends on that being pinned down.
 * <p>
 * The matrix is both ciphers against both ways of writing a document, because the incremental
 * updater and the full updater reach the writers by different routes.
 */
public class EncryptedStringWriteTests {

    private static final Name LITERAL_PROBE = new Name("ICEpdfLiteralProbe");
    private static final Name HEX_PROBE = new Name("ICEpdfHexProbe");

    /** Plain ASCII, so that it can be looked for verbatim in the written file. */
    private static final String ASCII_VALUE = "Probe value (with parens) and a backslash \\";

    /** Beyond Latin-1, so the string is carried as UTF-16 and exercises that path too. */
    private static final String UNICODE_VALUE = "Grüße 中文 ☃";

    private static Document open(String fixture) throws Exception {
        Document document = new Document();
        try (InputStream stream =
                     EncryptedStringWriteTests.class.getResourceAsStream("/updater/" + fixture)) {
            assertNotNull(stream, "missing fixture " + fixture);
            document.setInputStream(stream, fixture);
        }
        assertNotNull(document.getCatalog().getLibrary().getSecurityManager(),
                fixture + " is expected to be encrypted");
        return document;
    }

    private static String readBack(Document document, Name key) {
        PInfo info = document.getInfo();
        Object value = info.getEntries().get(key);
        assertTrue(value instanceof StringObject, key + " should have been written as a string");
        SecurityManager securityManager = document.getCatalog().getLibrary().getSecurityManager();
        return Utils.decodeTextString(((StringObject) value).getDecryptedRawBytes(securityManager));
    }

    /**
     * Authors a literal and a hexadecimal string into the document's information dictionary, writes
     * it out the given way, and hands back the file.
     */
    private File authorAndWrite(String fixture, WriteMode writeMode, String value, String tag)
            throws Exception {
        Document document = open(fixture);
        Library library = document.getCatalog().getLibrary();
        PInfo info = document.getInfo();

        info.getEntries().put(LITERAL_PROBE,
                new LiteralStringObject(value, info.getPObjectReference()));
        info.getEntries().put(HEX_PROBE,
                new HexStringObject(value, info.getPObjectReference()));
        library.getStateManager().addChange(new PObject(info, info.getPObjectReference()));

        File out = new File("./src/test/out/EncryptedStringWriteTests_" + tag + ".pdf");
        out.getParentFile().mkdirs();
        try (BufferedOutputStream stream = new BufferedOutputStream(new FileOutputStream(out), 8192)) {
            document.saveToOutputStream(stream, writeMode);
        }
        document.dispose();
        return out;
    }

    @DisplayName("an authored string survives being written to an encrypted document")
    @ParameterizedTest(name = "{0} {1}")
    @CsvSource({
            "DSCP73_om_en.pdf, FULL_UPDATE",
            "DSCP73_om_en.pdf, INCREMENT_UPDATE",
            "encrypted-aes-v2.pdf, FULL_UPDATE",
            "encrypted-aes-v2.pdf, INCREMENT_UPDATE"})
    public void authoredStringsRoundTrip(String fixture, WriteMode writeMode) throws Exception {
        File out = authorAndWrite(fixture, writeMode, ASCII_VALUE,
                "ascii_" + fixture.charAt(0) + "_" + writeMode);

        Document rewritten = new Document();
        rewritten.setFile(out.getAbsolutePath());
        try {
            assertEquals(ASCII_VALUE, readBack(rewritten, LITERAL_PROBE));
            assertEquals(ASCII_VALUE, readBack(rewritten, HEX_PROBE));
        } finally {
            rewritten.dispose();
        }
    }

    @DisplayName("an authored string is enciphered on disk, not merely written")
    @ParameterizedTest(name = "{0} {1}")
    @CsvSource({
            "DSCP73_om_en.pdf, FULL_UPDATE",
            "DSCP73_om_en.pdf, INCREMENT_UPDATE",
            "encrypted-aes-v2.pdf, FULL_UPDATE",
            "encrypted-aes-v2.pdf, INCREMENT_UPDATE"})
    public void authoredStringsAreEnciphered(String fixture, WriteMode writeMode) throws Exception {
        // The round trip above passes either way if the string is written in the clear and read
        // back without being deciphered.  This is the half that says it was actually encrypted -
        // and it is what a reader sees, which for a plain text string is four fields of rubbish.
        File out = authorAndWrite(fixture, writeMode, ASCII_VALUE,
                "clear_" + fixture.charAt(0) + "_" + writeMode);

        String raw = new String(Files.readAllBytes(out.toPath()), StandardCharsets.ISO_8859_1);
        assertFalse(raw.contains("Probe value"),
                "the probe was written in plain text into an encrypted document");
    }

    @DisplayName("text beyond Latin-1 survives the round trip too")
    @ParameterizedTest(name = "{0} {1}")
    @CsvSource({
            "DSCP73_om_en.pdf, FULL_UPDATE",
            "encrypted-aes-v2.pdf, FULL_UPDATE"})
    public void unicodeStringsRoundTrip(String fixture, WriteMode writeMode) throws Exception {
        // Carried as UTF-16, so most of its bytes are above 0x7F - which is where a charset that is
        // not a byte for byte mapping turns the string into replacement characters.
        File out = authorAndWrite(fixture, writeMode, UNICODE_VALUE,
                "unicode_" + fixture.charAt(0) + "_" + writeMode);

        Document rewritten = new Document();
        rewritten.setFile(out.getAbsolutePath());
        try {
            assertEquals(UNICODE_VALUE, readBack(rewritten, LITERAL_PROBE));
            assertEquals(UNICODE_VALUE, readBack(rewritten, HEX_PROBE));
        } finally {
            rewritten.dispose();
        }
    }

    @DisplayName("enciphering the same string twice gives the same bytes, so a rewrite can rely on it")
    @ParameterizedTest(name = "{0}")
    @CsvSource({"DSCP73_om_en.pdf", "encrypted-aes-v2.pdf"})
    public void encipheringIsRepeatable(String fixture) throws Exception {
        // AES picks a fresh initialisation vector per call, so this is not true of the cipher
        // itself; it is true because the string remembers what it produced.  Signing serializes the
        // signature dictionary twice and writes the second over the first, so two different answers
        // of two different lengths either truncate the object or run into the one after it.
        Document document = open(fixture);
        try {
            SecurityManager securityManager = document.getCatalog().getLibrary().getSecurityManager();
            PInfo info = document.getInfo();
            LiteralStringObject probe = new LiteralStringObject(ASCII_VALUE, info.getPObjectReference());

            byte[] first = probe.getEncryptedRawBytes(info.getPObjectReference(), securityManager);
            byte[] second = probe.getEncryptedRawBytes(info.getPObjectReference(), securityManager);

            assertEquals(first.length, second.length, "the two encipherings differ in length");
            assertArrayEquals(first, second, "the two encipherings differ");
        } finally {
            document.dispose();
        }
    }
}
