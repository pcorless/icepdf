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
package org.icepdf.core.pobjects.security;

import org.icepdf.core.pobjects.DictionaryEntries;
import org.icepdf.core.pobjects.LiteralStringObject;
import org.icepdf.core.pobjects.Name;
import org.icepdf.core.pobjects.Reference;
import org.icepdf.core.util.Library;
import org.icepdf.core.util.Utils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Tests the crypt filters: the per-stream choice of which algorithm decrypts what.
 * <p>
 * A document from version 4 onwards does not encrypt everything the same way.  The {@code /CF}
 * dictionary names a set of filters, and {@code /StmF} and {@code /StrF} say which of them applies
 * to streams and to strings; a stream may also name one of its own, or name {@code /Identity} to
 * say it is not encrypted at all.  Choosing the wrong one is the quietest failure in the library:
 * the wrong per-object key produces plausible bytes that inflate to nothing, and the page simply
 * comes up missing its content rather than reporting an error.
 * <p>
 * The streaming path is covered alongside, because that is how a content stream is actually read -
 * the byte-array methods are used for strings.
 */
public class CryptFilterTest {

    private static final Name STD_CF = new Name("StdCF");
    private static final Name IDENTITY = new Name("Identity");
    private static final Name CFM_KEY = CryptFilterEntry.CFM_KEY;

    private static final byte[] PLAINTEXT =
            "A content stream, encrypted.".getBytes(StandardCharsets.ISO_8859_1);

    // ------------------------------------------------------------------
    // building a dictionary
    // ------------------------------------------------------------------

    /**
     * One crypt filter entry.
     *
     * @param method the /CFM algorithm name
     * @param length the /Length, as the file writes it
     * @return the entry's dictionary
     */
    private static DictionaryEntries filterEntry(String method, int length) {
        DictionaryEntries entries = new DictionaryEntries();
        entries.put(CFM_KEY, new Name(method));
        entries.put(CryptFilterEntry.AUTHEVENT_KEY, new Name("DocOpen"));
        entries.put(CryptFilterEntry.LENGTH_KEY, length);
        return entries;
    }

    /**
     * An encryption dictionary with a /CF holding {@code StdCF}, and the given /StmF and /StrF.
     *
     * @param method  the /CFM of StdCF
     * @param streams the /StmF filter name, or null to leave it out
     * @param strings the /StrF filter name, or null to leave it out
     * @return the dictionary
     */
    private static EncryptionDictionary dictionary(String method, Name streams, Name strings) {
        DictionaryEntries cryptFilters = new DictionaryEntries();
        cryptFilters.put(STD_CF, filterEntry(method, 16));

        DictionaryEntries entries = new DictionaryEntries();
        entries.put(EncryptionDictionary.FILTER_KEY, new Name("Standard"));
        entries.put(EncryptionDictionary.R_KEY, 4);
        entries.put(EncryptionDictionary.V_KEY, 4);
        entries.put(EncryptionDictionary.LENGTH_KEY, 128);
        entries.put(EncryptionDictionary.P_KEY, -1340);
        entries.put(EncryptionDictionary.CF_KEY, cryptFilters);
        if (streams != null) {
            entries.put(EncryptionDictionary.STMF_KEY, streams);
        }
        if (strings != null) {
            entries.put(EncryptionDictionary.STRF_KEY, strings);
        }
        entries.put(EncryptionDictionary.O_KEY,
                new LiteralStringObject(Utils.convertByteArrayToByteString(new byte[32])));
        entries.put(EncryptionDictionary.U_KEY,
                new LiteralStringObject(Utils.convertByteArrayToByteString(new byte[32])));

        List<Object> fileId = new ArrayList<>();
        fileId.add(new LiteralStringObject(Utils.convertByteArrayToByteString(new byte[16])));
        return new EncryptionDictionary(new Library(), entries, fileId);
    }

    private static StandardSecurityHandler handler(EncryptionDictionary dictionary) {
        StandardSecurityHandler handler = new StandardSecurityHandler(dictionary);
        handler.init();
        return handler;
    }

    private static byte[] keyFor(EncryptionDictionary dictionary) {
        return new StandardEncryption(dictionary).encryptionKeyAlgorithm("", 128);
    }

    private static byte[] readAll(InputStream in) throws IOException {
        return in.readAllBytes();
    }

    // ------------------------------------------------------------------
    // the /CF dictionary
    // ------------------------------------------------------------------

    @DisplayName("a filter is found by the name the document gave it")
    @Test
    public void lookupByName() {
        CryptFilter cryptFilter = dictionary("AESV2", STD_CF, STD_CF).getCryptFilter();
        CryptFilterEntry entry = cryptFilter.getCryptFilterByName(STD_CF);
        assertNotNull(entry);
        assertEquals(new Name("AESV2"), entry.getCryptFilterMethod());
    }

    @DisplayName("a name that is not in the dictionary finds nothing")
    @Test
    public void lookupMissingName() {
        CryptFilter cryptFilter = dictionary("AESV2", STD_CF, STD_CF).getCryptFilter();
        assertNull(cryptFilter.getCryptFilterByName(new Name("NoSuchFilter")));
        assertNull(cryptFilter.getCryptFilterByName(IDENTITY),
                "Identity is never a member of /CF");
    }

    @DisplayName("the filter set is built once and handed back the same each time")
    @Test
    public void filtersAreCached() {
        // The build used to publish an empty map before filling it, so a concurrent reader could
        // get null for a filter that was present and fall back to the wrong algorithm.
        CryptFilter cryptFilter = dictionary("AESV2", STD_CF, STD_CF).getCryptFilter();
        assertSame(cryptFilter.getCryptFilterByName(STD_CF),
                cryptFilter.getCryptFilterByName(STD_CF));
    }

    @DisplayName("an entry that is not a dictionary is skipped rather than stored")
    @Test
    public void nonDictionaryEntry() {
        DictionaryEntries cryptFilters = new DictionaryEntries();
        cryptFilters.put(STD_CF, filterEntry("V2", 16));
        cryptFilters.put(new Name("Junk"), 42);

        CryptFilter cryptFilter = new CryptFilter(new Library(), cryptFilters);
        assertNotNull(cryptFilter.getCryptFilterByName(STD_CF));
        assertNull(cryptFilter.getCryptFilterByName(new Name("Junk")));
    }

    @DisplayName("an empty /CF finds nothing")
    @Test
    public void emptyCryptFilterDictionary() {
        assertNull(new CryptFilter(new Library(), new DictionaryEntries())
                .getCryptFilterByName(STD_CF));
    }

    // ------------------------------------------------------------------
    // a filter entry
    // ------------------------------------------------------------------

    @DisplayName("an entry reports its method and its authentication event")
    @Test
    public void entryValues() {
        CryptFilterEntry entry = new CryptFilterEntry(new Library(), filterEntry("AESV3", 32));
        assertEquals(new Name("AESV3"), entry.getCryptFilterMethod());
        assertEquals(new Name("DocOpen"), entry.getAuthEvent());
        assertEquals(CryptFilterEntry.TYPE, entry.getType());
    }

    @DisplayName("an entry missing its keys reports nothing rather than throwing")
    @Test
    public void entryWithoutValues() {
        CryptFilterEntry entry = new CryptFilterEntry(new Library(), new DictionaryEntries());
        assertNull(entry.getCryptFilterMethod());
        assertNull(entry.getAuthEvent());
    }

    @DisplayName("getLength reads /Length as bytes and caps the result at 128 bits")
    @Test
    public void entryLength() {
        // Pinned rather than asserted as correct: /Length is the *bit* length by the specification
        // (Table 25), and this multiplies by eight as though it were bytes, then clamps to 128.  An
        // AES-256 filter therefore reports 128 whichever unit its file used.  Nothing in the
        // library calls this, so the two faults are latent.
        assertEquals(128, new CryptFilterEntry(new Library(), filterEntry("AESV2", 16)).getLength());
        assertEquals(128, new CryptFilterEntry(new Library(), filterEntry("AESV3", 32)).getLength());
        assertEquals(128, new CryptFilterEntry(new Library(), filterEntry("AESV3", 256)).getLength());
        assertEquals(40, new CryptFilterEntry(new Library(), filterEntry("V2", 5)).getLength());
    }

    // ------------------------------------------------------------------
    // choosing a filter for strings
    // ------------------------------------------------------------------

    @DisplayName("strings are decrypted with the algorithm /StrF names")
    @Test
    public void stringFilterSelectsTheAlgorithm() {
        // The proof that the right algorithm was chosen is that the round trip works: AES and RC4
        // produce different cipher text from the same key, so decrypting with the other one gives
        // back something else.
        for (String method : new String[]{"V2", "AESV2"}) {
            EncryptionDictionary dictionary = dictionary(method, STD_CF, STD_CF);
            StandardSecurityHandler handler = handler(dictionary);
            byte[] key = keyFor(dictionary);
            Reference reference = new Reference(4, 0);

            byte[] encrypted = handler.encrypt(reference, key, PLAINTEXT);
            assertFalse(Arrays.equals(PLAINTEXT, encrypted), method + " left the data alone");
            assertArrayEquals(PLAINTEXT, handler.decrypt(reference, key, encrypted),
                    method + " did not round trip");
        }
    }

    @DisplayName("a document with no /CF at all falls back to RC4")
    @Test
    public void noCryptFilterDictionary() {
        DictionaryEntries entries = new DictionaryEntries();
        entries.put(EncryptionDictionary.FILTER_KEY, new Name("Standard"));
        entries.put(EncryptionDictionary.R_KEY, 3);
        entries.put(EncryptionDictionary.V_KEY, 2);
        entries.put(EncryptionDictionary.LENGTH_KEY, 128);
        entries.put(EncryptionDictionary.P_KEY, -1340);
        entries.put(EncryptionDictionary.O_KEY,
                new LiteralStringObject(Utils.convertByteArrayToByteString(new byte[32])));
        entries.put(EncryptionDictionary.U_KEY,
                new LiteralStringObject(Utils.convertByteArrayToByteString(new byte[32])));
        List<Object> fileId = new ArrayList<>();
        fileId.add(new LiteralStringObject(Utils.convertByteArrayToByteString(new byte[16])));
        EncryptionDictionary dictionary = new EncryptionDictionary(new Library(), entries, fileId);

        StandardSecurityHandler handler = handler(dictionary);
        byte[] key = keyFor(dictionary);
        Reference reference = new Reference(4, 0);
        assertArrayEquals(PLAINTEXT,
                handler.decrypt(reference, key, handler.encrypt(reference, key, PLAINTEXT)));
    }

    @DisplayName("/StrF /Identity means the strings are not encrypted")
    @Test
    public void identityStringFilter() {
        // Identity is a legal value and is not a member of /CF, so looking it up finds nothing.
        // The strings are to be left exactly as they are.
        EncryptionDictionary dictionary = dictionary("AESV2", STD_CF, IDENTITY);
        StandardSecurityHandler handler = handler(dictionary);
        byte[] key = keyFor(dictionary);
        Reference reference = new Reference(4, 0);

        assertArrayEquals(PLAINTEXT, handler.encrypt(reference, key, PLAINTEXT));
        assertArrayEquals(PLAINTEXT, handler.decrypt(reference, key, PLAINTEXT));
    }

    @DisplayName("a /StrF naming a filter that is not there does not throw")
    @Test
    public void missingStringFilter() {
        // A damaged file can name a filter its /CF does not hold; the document still has to open.
        EncryptionDictionary dictionary = dictionary("AESV2", STD_CF, new Name("Absent"));
        StandardSecurityHandler handler = handler(dictionary);
        byte[] key = keyFor(dictionary);
        assertNotNull(handler.decrypt(new Reference(4, 0), key, PLAINTEXT));
    }

    @DisplayName("a filter entry with no /CFM does not throw")
    @Test
    public void filterWithoutMethod() {
        DictionaryEntries cryptFilters = new DictionaryEntries();
        DictionaryEntries noMethod = new DictionaryEntries();
        noMethod.put(CryptFilterEntry.LENGTH_KEY, 16);
        cryptFilters.put(STD_CF, noMethod);

        EncryptionDictionary dictionary = dictionary("AESV2", STD_CF, STD_CF);
        dictionary.getEntries().put(EncryptionDictionary.CF_KEY, cryptFilters);

        StandardSecurityHandler handler = handler(dictionary);
        byte[] key = keyFor(dictionary);
        assertNotNull(handler.decrypt(new Reference(4, 0), key, PLAINTEXT));
    }

    // ------------------------------------------------------------------
    // choosing a filter for streams
    // ------------------------------------------------------------------

    @DisplayName("streams round trip through the streaming path, for both algorithms")
    @Test
    public void streamRoundTrip() throws IOException {
        // Content streams are read through here rather than through the byte-array methods.
        for (String method : new String[]{"V2", "AESV2"}) {
            EncryptionDictionary dictionary = dictionary(method, STD_CF, STD_CF);
            StandardSecurityHandler handler = handler(dictionary);
            byte[] key = keyFor(dictionary);
            Reference reference = new Reference(4, 0);

            byte[] encrypted = readAll(handler.encryptInputStream(
                    reference, key, null, new ByteArrayInputStream(PLAINTEXT)));
            assertFalse(Arrays.equals(PLAINTEXT, encrypted), method + " left the stream alone");

            byte[] decrypted = readAll(handler.decryptInputStream(
                    reference, key, null, new ByteArrayInputStream(encrypted)));
            assertArrayEquals(PLAINTEXT, decrypted, method + " did not round trip");
        }
    }

    @DisplayName("a stream longer than one buffer decrypts whole")
    @Test
    public void longStreamRoundTrip() throws IOException {
        // The streaming path reads in chunks; a lost or repeated boundary shows only at length.
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < 4000; i++) {
            text.append("BT /F1 12 Tf (line ").append(i).append(") Tj ET\n");
        }
        byte[] plaintext = text.toString().getBytes(StandardCharsets.ISO_8859_1);

        EncryptionDictionary dictionary = dictionary("AESV2", STD_CF, STD_CF);
        StandardSecurityHandler handler = handler(dictionary);
        byte[] key = keyFor(dictionary);
        Reference reference = new Reference(4, 0);

        byte[] encrypted = readAll(handler.encryptInputStream(
                reference, key, null, new ByteArrayInputStream(plaintext)));
        assertArrayEquals(plaintext, readAll(handler.decryptInputStream(
                reference, key, null, new ByteArrayInputStream(encrypted))));
    }

    @DisplayName("a stream naming Identity in its decode parameters is left alone")
    @Test
    public void identityStreamFilter() throws IOException {
        // An embedded file or a metadata stream may opt out of encryption this way.
        EncryptionDictionary dictionary = dictionary("AESV2", STD_CF, STD_CF);
        StandardSecurityHandler handler = handler(dictionary);
        HashMap<Name, Object> decodeParams = new HashMap<>();
        decodeParams.put(StandardSecurityHandler.NAME_KEY, IDENTITY);

        byte[] result = readAll(handler.decryptInputStream(new Reference(4, 0),
                keyFor(dictionary), decodeParams, new ByteArrayInputStream(PLAINTEXT)));
        assertArrayEquals(PLAINTEXT, result);
    }

    @DisplayName("a stream naming its own filter uses that one")
    @Test
    public void namedStreamFilter() throws IOException {
        EncryptionDictionary dictionary = dictionary("AESV2", STD_CF, STD_CF);
        StandardSecurityHandler handler = handler(dictionary);
        byte[] key = keyFor(dictionary);
        Reference reference = new Reference(4, 0);

        HashMap<Name, Object> decodeParams = new HashMap<>();
        decodeParams.put(StandardSecurityHandler.NAME_KEY, STD_CF);

        byte[] encrypted = readAll(handler.encryptInputStream(
                reference, key, decodeParams, new ByteArrayInputStream(PLAINTEXT)));
        assertArrayEquals(PLAINTEXT, readAll(handler.decryptInputStream(
                reference, key, decodeParams, new ByteArrayInputStream(encrypted))));
    }

    @DisplayName("decode parameters with no filter name fall back to the stream default")
    @Test
    public void decodeParamsWithoutAFilterName() throws IOException {
        // Image streams carry decode parameters of their own that say nothing about encryption.
        EncryptionDictionary dictionary = dictionary("AESV2", STD_CF, STD_CF);
        StandardSecurityHandler handler = handler(dictionary);
        byte[] key = keyFor(dictionary);
        Reference reference = new Reference(4, 0);

        HashMap<Name, Object> decodeParams = new HashMap<>();
        decodeParams.put(new Name("Columns"), 1728);

        byte[] encrypted = readAll(handler.encryptInputStream(
                reference, key, decodeParams, new ByteArrayInputStream(PLAINTEXT)));
        assertArrayEquals(PLAINTEXT, readAll(handler.decryptInputStream(
                reference, key, decodeParams, new ByteArrayInputStream(encrypted))));
    }

    @DisplayName("an empty stream decrypts to nothing")
    @Test
    public void emptyStream() throws IOException {
        EncryptionDictionary dictionary = dictionary("AESV2", STD_CF, STD_CF);
        StandardSecurityHandler handler = handler(dictionary);
        byte[] key = keyFor(dictionary);
        Reference reference = new Reference(4, 0);

        byte[] encrypted = readAll(handler.encryptInputStream(
                reference, key, null, new ByteArrayInputStream(new byte[0])));
        assertEquals(0, readAll(handler.decryptInputStream(
                reference, key, null, new ByteArrayInputStream(encrypted))).length);
    }

    @DisplayName("/StmF /Identity means the streams are not encrypted either")
    @Test
    public void identityStreamFilterViaStmF() throws IOException {
        // The counterpart of /StrF /Identity, and the one that used to fail quietly: Identity is
        // not a member of /CF, so the lookup found nothing and the stream was decrypted with the
        // default RC4 anyway, turning content that was never encrypted into garbage.
        EncryptionDictionary dictionary = dictionary("AESV2", IDENTITY, STD_CF);
        StandardSecurityHandler handler = handler(dictionary);
        byte[] key = keyFor(dictionary);
        Reference reference = new Reference(4, 0);

        assertArrayEquals(PLAINTEXT, readAll(handler.encryptInputStream(
                reference, key, null, new ByteArrayInputStream(PLAINTEXT))));
        assertArrayEquals(PLAINTEXT, readAll(handler.decryptInputStream(
                reference, key, null, new ByteArrayInputStream(PLAINTEXT))));
    }

    @DisplayName("a /StmF naming a filter that is not there does not throw")
    @Test
    public void missingStreamFilter() throws IOException {
        EncryptionDictionary dictionary = dictionary("AESV2", new Name("Absent"), STD_CF);
        StandardSecurityHandler handler = handler(dictionary);
        assertNotNull(readAll(handler.decryptInputStream(new Reference(4, 0), keyFor(dictionary),
                null, new ByteArrayInputStream(PLAINTEXT))));
    }

    @DisplayName("a stream encrypted for one object does not decrypt as another")
    @Test
    public void streamObjectKeysAreNotInterchangeable() throws IOException {
        EncryptionDictionary dictionary = dictionary("V2", STD_CF, STD_CF);
        StandardSecurityHandler handler = handler(dictionary);
        byte[] key = keyFor(dictionary);

        byte[] encrypted = readAll(handler.encryptInputStream(new Reference(4, 0), key, null,
                new ByteArrayInputStream(PLAINTEXT)));
        byte[] wrongObject = readAll(handler.decryptInputStream(new Reference(5, 0), key, null,
                new ByteArrayInputStream(encrypted)));
        assertFalse(Arrays.equals(PLAINTEXT, wrongObject));
    }
}
