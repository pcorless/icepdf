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
import org.icepdf.core.pobjects.Reference;
import org.icepdf.core.util.Library;
import org.icepdf.core.util.Utils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the standard security handler's algorithms: how a password becomes a key, how that key
 * becomes a per-object key, and how that key encrypts and decrypts.
 * <p>
 * Encryption has no partial credit and no useful symptom.  A key derived one byte differently
 * decrypts a document into bytes that are not a PDF, and the first thing that notices is usually
 * the content-stream lexer, thousands of lines away.  The assertions here are therefore about the
 * properties the algorithms must have rather than about literal key bytes: the same inputs give the
 * same key, different inputs give different keys, and anything encrypted comes back out.
 * <p>
 * Each test builds the encryption dictionary it needs, so the revision, the key length and the
 * algorithm are all visible next to what they are expected to produce.
 */
public class StandardEncryptionTest {

    private static final byte[] FILE_ID = {
            0x01, 0x23, 0x45, 0x67, (byte) 0x89, (byte) 0xAB, (byte) 0xCD, (byte) 0xEF,
            0x01, 0x23, 0x45, 0x67, (byte) 0x89, (byte) 0xAB, (byte) 0xCD, (byte) 0xEF};

    /**
     * A standard security handler dictionary.  /O and /U hold arbitrary but fixed bytes; the key
     * derivation reads them, and the tests that need a real /U compute one and put it back.
     *
     * @param revision  the /R revision number
     * @param version   the /V algorithm version
     * @param keyLength key length in bits
     * @return the dictionary
     */
    private static EncryptionDictionary dictionary(int revision, int version, int keyLength) {
        DictionaryEntries entries = new DictionaryEntries();
        entries.put(EncryptionDictionary.FILTER_KEY,
                new org.icepdf.core.pobjects.Name("Standard"));
        entries.put(EncryptionDictionary.R_KEY, revision);
        entries.put(EncryptionDictionary.V_KEY, version);
        entries.put(EncryptionDictionary.LENGTH_KEY, keyLength);
        entries.put(EncryptionDictionary.P_KEY, -1340);
        entries.put(EncryptionDictionary.O_KEY,
                new LiteralStringObject(Utils.convertByteArrayToByteString(new byte[32])));
        entries.put(EncryptionDictionary.U_KEY,
                new LiteralStringObject(Utils.convertByteArrayToByteString(new byte[32])));
        entries.put(EncryptionDictionary.ENCRYPT_METADATA_KEY, Boolean.TRUE);

        List<Object> fileId = new ArrayList<>();
        fileId.add(new LiteralStringObject(Utils.convertByteArrayToByteString(FILE_ID)));
        fileId.add(new LiteralStringObject(Utils.convertByteArrayToByteString(FILE_ID)));
        return new EncryptionDictionary(new Library(), entries, fileId);
    }

    private static StandardEncryption encryption(int revision, int version, int keyLength) {
        return new StandardEncryption(dictionary(revision, version, keyLength));
    }

    // ------------------------------------------------------------------
    // the document key (algorithm 2)
    // ------------------------------------------------------------------

    @DisplayName("the key is the length the dictionary asks for")
    @Test
    public void keyLength() {
        // Revision 2 is always 40 bits; later revisions take the length from /Length.
        assertEquals(5, encryption(2, 1, 40).encryptionKeyAlgorithm("", 40).length);
        assertEquals(16, encryption(3, 2, 128).encryptionKeyAlgorithm("", 128).length);
    }

    @DisplayName("the same password and dictionary always give the same key")
    @Test
    public void keyIsDeterministic() {
        // The key is recomputed on every open; a key that varies cannot decrypt what it wrote.
        StandardEncryption encryption = encryption(3, 2, 128);
        assertArrayEquals(encryption.encryptionKeyAlgorithm("secret", 128),
                encryption.encryptionKeyAlgorithm("secret", 128));
    }

    @DisplayName("a different password gives a different key")
    @Test
    public void keyDependsOnPassword() {
        StandardEncryption encryption = encryption(3, 2, 128);
        byte[] one = encryption.encryptionKeyAlgorithm("secret", 128);
        byte[] other = encryption.encryptionKeyAlgorithm("secrets", 128);
        assertFalse(Arrays.equals(one, other), "two passwords must not derive the same key");
    }

    @DisplayName("an empty password derives a key rather than refusing")
    @Test
    public void emptyPassword() {
        // Most encrypted documents have no user password at all; the padding string stands in.
        assertNotNull(encryption(3, 2, 128).encryptionKeyAlgorithm("", 128));
    }

    @DisplayName("the key depends on the permissions, the file id and the owner entry")
    @Test
    public void keyDependsOnTheDictionary() {
        // Every one of these is fed into the hash, and leaving one out would let a document be
        // opened after its permissions had been edited.
        byte[] baseline = encryption(3, 2, 128).encryptionKeyAlgorithm("", 128);

        EncryptionDictionary changedPermissions = dictionary(3, 2, 128);
        changedPermissions.getEntries().put(EncryptionDictionary.P_KEY, -44);
        assertFalse(Arrays.equals(baseline,
                        new StandardEncryption(changedPermissions).encryptionKeyAlgorithm("", 128)),
                "the permissions have to be part of the key");

        EncryptionDictionary changedOwner = dictionary(3, 2, 128);
        byte[] owner = new byte[32];
        Arrays.fill(owner, (byte) 0x42);
        changedOwner.getEntries().put(EncryptionDictionary.O_KEY,
                new LiteralStringObject(Utils.convertByteArrayToByteString(owner)));
        assertFalse(Arrays.equals(baseline,
                        new StandardEncryption(changedOwner).encryptionKeyAlgorithm("", 128)),
                "the owner entry has to be part of the key");
    }

    // ------------------------------------------------------------------
    // the per-object key (algorithm 1)
    // ------------------------------------------------------------------

    /**
     * Gives the encryption a document key to work from, as opening a document does.
     */
    private static StandardEncryption withKey(int revision, int version, int keyLength) {
        StandardEncryption encryption = encryption(revision, version, keyLength);
        encryption.encryptionKeyAlgorithm("", keyLength);
        return encryption;
    }

    @DisplayName("every object gets a key of its own")
    @Test
    public void perObjectKeysDiffer() {
        // Reusing one key across objects is what the object and generation numbers are mixed in to
        // prevent; two objects sharing a key leak each other's plaintext under a known-plaintext
        // attack.
        StandardEncryption encryption = withKey(3, 2, 128);
        byte[] first = encryption.resetObjectReference(new Reference(1, 0), true);
        byte[] second = encryption.resetObjectReference(new Reference(2, 0), true);
        byte[] laterGeneration = encryption.resetObjectReference(new Reference(1, 1), true);

        assertFalse(Arrays.equals(first, second), "the object number has to change the key");
        assertFalse(Arrays.equals(first, laterGeneration),
                "the generation number has to change the key");
    }

    @DisplayName("the same object always gets the same key")
    @Test
    public void perObjectKeyIsDeterministic() {
        StandardEncryption encryption = withKey(3, 2, 128);
        assertArrayEquals(encryption.resetObjectReference(new Reference(7, 0), true),
                encryption.resetObjectReference(new Reference(7, 0), true));
    }

    @DisplayName("the AES key differs from the RC4 key for the same object")
    @Test
    public void aesKeyDiffersFromRc4Key() {
        // AES mixes four extra bytes into the hash; skipping them would make an AES document
        // decrypt with an RC4 key, which produces plausible-looking garbage.
        StandardEncryption encryption = withKey(4, 4, 128);
        assertFalse(Arrays.equals(encryption.resetObjectReference(new Reference(1, 0), true),
                        encryption.resetObjectReference(new Reference(1, 0), false)),
                "the salt has to change the key");
    }

    @DisplayName("the key handed to the algorithm is the key it uses")
    @Test
    public void theSuppliedKeyIsTheKeyUsed() {
        // generalEncryptionAlgorithm takes the document key as an argument, but derived the
        // per-object key from whatever key had last been computed on this instance instead.  A
        // caller that supplied its own key was quietly ignored, and an instance that had never
        // computed one threw.  Two instances given the same key must now agree, and one given a
        // different key must not.
        byte[] key = encryption(3, 2, 128).encryptionKeyAlgorithm("", 128);
        StandardEncryption fresh = encryption(3, 2, 128);
        Reference reference = new Reference(5, 0);

        byte[] encrypted = fresh.generalEncryptionAlgorithm(reference, key,
                StandardEncryption.ENCRYPTION_TYPE_V2, PLAINTEXT, true);
        assertNotNull(encrypted, "an instance that never derived a key still has to use the one given");

        byte[] decrypted = encryption(3, 2, 128).generalEncryptionAlgorithm(reference, key,
                StandardEncryption.ENCRYPTION_TYPE_V2, encrypted, false);
        assertArrayEquals(PLAINTEXT, decrypted);

        byte[] otherKey = encryption(3, 2, 128).encryptionKeyAlgorithm("different", 128);
        byte[] wrongKey = encryption(3, 2, 128).generalEncryptionAlgorithm(reference, otherKey,
                StandardEncryption.ENCRYPTION_TYPE_V2, encrypted, false);
        assertFalse(Arrays.equals(PLAINTEXT, wrongKey));
    }

    @DisplayName("the per-object key can be derived from a key that is passed in")
    @Test
    public void perObjectKeyFromASuppliedKey() {
        byte[] key = encryption(3, 2, 128).encryptionKeyAlgorithm("", 128);
        StandardEncryption fresh = encryption(3, 2, 128);
        assertArrayEquals(withKey(3, 2, 128).resetObjectReference(new Reference(1, 0), true),
                fresh.resetObjectReference(new Reference(1, 0), true, key));
    }

    @DisplayName("an object number beyond three bytes still yields a key")
    @Test
    public void largeObjectNumber() {
        // Only the low three bytes go into the hash, so a large document must not overflow here.
        StandardEncryption encryption = withKey(3, 2, 128);
        assertEquals(16, encryption.resetObjectReference(new Reference(16_777_300, 0), true).length);
    }

    // ------------------------------------------------------------------
    // encrypting and decrypting (algorithm 1)
    // ------------------------------------------------------------------

    private static final byte[] PLAINTEXT =
            "The quick brown fox jumps over the lazy dog.".getBytes(StandardCharsets.ISO_8859_1);

    @DisplayName("RC4 - what is encrypted comes back out")
    @Test
    public void rc4RoundTrip() {
        StandardEncryption encryption = encryption(3, 2, 128);
        byte[] key = encryption.encryptionKeyAlgorithm("", 128);
        Reference reference = new Reference(5, 0);

        byte[] encrypted = encryption.generalEncryptionAlgorithm(reference, key,
                StandardEncryption.ENCRYPTION_TYPE_V2, PLAINTEXT, true);
        assertFalse(Arrays.equals(PLAINTEXT, encrypted), "the data should not have been left alone");

        byte[] decrypted = encryption.generalEncryptionAlgorithm(reference, key,
                StandardEncryption.ENCRYPTION_TYPE_V2, encrypted, false);
        assertArrayEquals(PLAINTEXT, decrypted);
    }

    @DisplayName("AES - what is encrypted comes back out")
    @Test
    public void aesRoundTrip() {
        StandardEncryption encryption = encryption(4, 4, 128);
        byte[] key = encryption.encryptionKeyAlgorithm("", 128);
        Reference reference = new Reference(5, 0);

        byte[] encrypted = encryption.generalEncryptionAlgorithm(reference, key,
                StandardEncryption.ENCRYPTION_TYPE_AES_V2, PLAINTEXT, true);
        assertFalse(Arrays.equals(PLAINTEXT, encrypted));

        byte[] decrypted = encryption.generalEncryptionAlgorithm(reference, key,
                StandardEncryption.ENCRYPTION_TYPE_AES_V2, encrypted, false);
        assertArrayEquals(PLAINTEXT, decrypted);
    }

    @DisplayName("AES - the same plaintext encrypts differently each time")
    @Test
    public void aesUsesAFreshInitialisationVector() {
        // CBC mode prepends a random initialisation vector; without one, identical strings in a
        // document encrypt identically and the structure of the plaintext shows through.
        StandardEncryption encryption = encryption(4, 4, 128);
        byte[] key = encryption.encryptionKeyAlgorithm("", 128);
        Reference reference = new Reference(5, 0);

        byte[] once = encryption.generalEncryptionAlgorithm(reference, key,
                StandardEncryption.ENCRYPTION_TYPE_AES_V2, PLAINTEXT, true);
        byte[] twice = encryption.generalEncryptionAlgorithm(reference, key,
                StandardEncryption.ENCRYPTION_TYPE_AES_V2, PLAINTEXT, true);
        assertFalse(Arrays.equals(once, twice));

        // ... and both still decrypt to the same thing
        assertArrayEquals(PLAINTEXT, encryption.generalEncryptionAlgorithm(reference, key,
                StandardEncryption.ENCRYPTION_TYPE_AES_V2, once, false));
        assertArrayEquals(PLAINTEXT, encryption.generalEncryptionAlgorithm(reference, key,
                StandardEncryption.ENCRYPTION_TYPE_AES_V2, twice, false));
    }

    @DisplayName("data encrypted for one object does not decrypt as another")
    @Test
    public void objectKeysAreNotInterchangeable() {
        StandardEncryption encryption = encryption(3, 2, 128);
        byte[] key = encryption.encryptionKeyAlgorithm("", 128);

        byte[] encrypted = encryption.generalEncryptionAlgorithm(new Reference(5, 0), key,
                StandardEncryption.ENCRYPTION_TYPE_V2, PLAINTEXT, true);
        byte[] wrongObject = encryption.generalEncryptionAlgorithm(new Reference(6, 0), key,
                StandardEncryption.ENCRYPTION_TYPE_V2, encrypted, false);
        assertFalse(Arrays.equals(PLAINTEXT, wrongObject));
    }

    @DisplayName("an empty input encrypts and decrypts to nothing")
    @Test
    public void emptyInput() {
        StandardEncryption encryption = encryption(3, 2, 128);
        byte[] key = encryption.encryptionKeyAlgorithm("", 128);
        byte[] encrypted = encryption.generalEncryptionAlgorithm(new Reference(5, 0), key,
                StandardEncryption.ENCRYPTION_TYPE_V2, new byte[0], true);
        assertEquals(0, encryption.generalEncryptionAlgorithm(new Reference(5, 0), key,
                StandardEncryption.ENCRYPTION_TYPE_V2, encrypted, false).length);
    }

    @DisplayName("a missing reference, key or input is refused rather than half-encrypted")
    @Test
    public void missingArguments() {
        StandardEncryption encryption = encryption(3, 2, 128);
        byte[] key = encryption.encryptionKeyAlgorithm("", 128);
        assertNull(encryption.generalEncryptionAlgorithm(null, key,
                StandardEncryption.ENCRYPTION_TYPE_V2, PLAINTEXT, true));
        assertNull(encryption.generalEncryptionAlgorithm(new Reference(1, 0), null,
                StandardEncryption.ENCRYPTION_TYPE_V2, PLAINTEXT, true));
        assertNull(encryption.generalEncryptionAlgorithm(new Reference(1, 0), key,
                StandardEncryption.ENCRYPTION_TYPE_V2, null, true));
    }

    // ------------------------------------------------------------------
    // authenticating a password (algorithms 4, 5 and 6)
    // ------------------------------------------------------------------

    /**
     * Builds a dictionary whose /U is the value {@code password} actually computes, which is what a
     * writer would have stored, then returns an encryption over it.
     */
    private static StandardEncryption authenticatable(int revision, int version, int keyLength,
                                                      String password) {
        EncryptionDictionary dictionary = dictionary(revision, version, keyLength);
        byte[] computed = new StandardEncryption(dictionary).calculateUserPassword(password);
        dictionary.getEntries().put(EncryptionDictionary.U_KEY,
                new LiteralStringObject(Utils.convertByteArrayToByteString(computed)));
        return new StandardEncryption(dictionary);
    }

    @DisplayName("revision 2 - the right user password authenticates and a wrong one does not")
    @Test
    public void authenticateRevision2() {
        StandardEncryption encryption = authenticatable(2, 1, 40, "secret");
        assertTrue(encryption.authenticateUserPassword("secret"));
        assertFalse(encryption.authenticateUserPassword("wrong"));
        assertFalse(encryption.authenticateUserPassword(""));
    }

    @DisplayName("revision 3 - the right user password authenticates and a wrong one does not")
    @Test
    public void authenticateRevision3() {
        // Revision 3 compares only the first sixteen bytes of /U, the rest being arbitrary.
        StandardEncryption encryption = authenticatable(3, 2, 128, "secret");
        assertTrue(encryption.authenticateUserPassword("secret"));
        assertFalse(encryption.authenticateUserPassword("wrong"));
    }

    @DisplayName("an empty user password authenticates when that is what the document was given")
    @Test
    public void authenticateEmptyPassword() {
        // The common case: a document encrypted to restrict permissions, openable by anyone.
        StandardEncryption encryption = authenticatable(3, 2, 128, "");
        assertTrue(encryption.authenticateUserPassword(""));
        assertFalse(encryption.authenticateUserPassword("secret"));
    }

    @DisplayName("the authenticated password is remembered for the key derivation that follows")
    @Test
    public void authenticationRemembersThePassword() {
        StandardEncryption encryption = authenticatable(3, 2, 128, "secret");
        assertTrue(encryption.authenticateUserPassword("secret"));
        assertEquals("secret", encryption.getUserPassword());
    }

    @DisplayName("revisions 5 and 6 are not authenticated by the pre-AES-256 algorithm")
    @Test
    public void authenticateRevision5() {
        // AES-256 replaced algorithms 4 and 5 entirely; the security handler routes those
        // revisions elsewhere, and this method has to decline rather than answer wrongly.
        assertFalse(encryption(5, 5, 256).authenticateUserPassword(""));
        assertFalse(encryption(6, 5, 256).authenticateUserPassword(""));
    }

    @DisplayName("an AES-256 entry too short to hold its salts fails the password, it does not throw")
    @Test
    public void shortRevision56Entry() {
        // A revision 5 or 6 /U is 48 bytes: a hash and two salts.  A damaged or hostile file can
        // carry fewer, and this is reached straight from opening a document, so it has to answer
        // "wrong password" rather than throwing out of the security handler.
        SecurityHandler handler = new StandardSecurityHandler(dictionary(6, 5, 256));
        handler.init();
        assertFalse(handler.isAuthorized(""));
        assertFalse(handler.isAuthorized("secret"));
    }

    @DisplayName("the owner password authenticates against the value it computes")
    @Test
    public void authenticateOwnerPassword() {
        EncryptionDictionary dictionary = dictionary(3, 2, 128);
        byte[] owner = new StandardEncryption(dictionary)
                .calculateOwnerPassword("owner", "user", false);
        dictionary.getEntries().put(EncryptionDictionary.O_KEY,
                new LiteralStringObject(Utils.convertByteArrayToByteString(owner)));

        // /U has to match the user password for the owner check, which decrypts /O to recover it
        StandardEncryption encryption = new StandardEncryption(dictionary);
        byte[] user = encryption.calculateUserPassword("user");
        dictionary.getEntries().put(EncryptionDictionary.U_KEY,
                new LiteralStringObject(Utils.convertByteArrayToByteString(user)));

        assertTrue(new StandardEncryption(dictionary).authenticateOwnerPassword("owner"));
        assertFalse(new StandardEncryption(dictionary).authenticateOwnerPassword("not the owner"));
    }
}
