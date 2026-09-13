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
package org.icepdf.core.pobjects.acroform;

import org.icepdf.core.pobjects.DictionaryEntries;
import org.icepdf.core.pobjects.HexStringObject;
import org.icepdf.core.pobjects.LiteralStringObject;
import org.icepdf.core.pobjects.Name;
import org.icepdf.core.pobjects.actions.Action;
import org.icepdf.core.util.Library;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the supporting dictionaries of an interactive form: the signature a field carries, the
 * constraints a form places on signing, and the actions a field or annotation fires.
 * <p>
 * These are almost all key lookups, and the interesting part is which key.  A constant naming the
 * wrong one is invisible to every caller - the entry is optional, so reading the wrong key looks
 * exactly like the file not having it - which is why the keys themselves are asserted here rather
 * than only the values behind them.
 * <p>
 * The flag words get the same treatment as elsewhere: each bit on its own, against a baseline of
 * nothing set, since the whole point of the word is that each bit means something different.
 */
public class AcroFormDictionaryTest {

    private final Library library = new Library();

    private static DictionaryEntries entries(Object... keysAndValues) {
        DictionaryEntries entries = new DictionaryEntries();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            entries.put((Name) keysAndValues[i], keysAndValues[i + 1]);
        }
        return entries;
    }

    // ------------------------------------------------------------------
    // the signature dictionary
    // ------------------------------------------------------------------

    private SignatureDictionary signature(Object... keysAndValues) {
        return new SignatureDictionary(library, entries(keysAndValues));
    }

    @DisplayName("signature - the handler and its sub filter")
    @Test
    public void signatureFilters() {
        SignatureDictionary dictionary = signature(
                SignatureDictionary.FILTER_KEY, new Name("Adobe.PPKLite"),
                SignatureDictionary.SUB_FILTER_KEY, new Name("adbe.pkcs7.detached"));
        assertEquals(new Name("Adobe.PPKLite"), dictionary.getFilter());
        assertEquals(new Name("adbe.pkcs7.detached"), dictionary.getSubFilter());
    }

    @DisplayName("signature - the descriptive entries a reader shows")
    @Test
    public void signatureDescription() {
        SignatureDictionary dictionary = signature(
                SignatureDictionary.NAME_KEY, new LiteralStringObject("A Signer"),
                SignatureDictionary.LOCATION_KEY, new LiteralStringObject("Somewhere"),
                SignatureDictionary.REASON_KEY, new LiteralStringObject("Approval"),
                SignatureDictionary.CONTACT_INFO_KEY, new LiteralStringObject("signer@example.com"));
        assertEquals("A Signer", dictionary.getName());
        assertEquals("Somewhere", dictionary.getLocation());
        assertEquals("Approval", dictionary.getReason());
        assertEquals("signer@example.com", dictionary.getContactInfo());
    }

    @DisplayName("signature - each descriptive entry is read from its own key")
    @Test
    public void signatureDescriptionKeysAreDistinct() {
        // Setting one has to leave the others empty; a shared or mistyped key would show up as one
        // value appearing under two names, which is otherwise indistinguishable from a file that
        // simply set both.
        SignatureDictionary dictionary = signature(
                SignatureDictionary.REASON_KEY, new LiteralStringObject("Approval"));
        assertEquals("Approval", dictionary.getReason());
        assertNull(dictionary.getName());
        assertNull(dictionary.getLocation());
        assertNull(dictionary.getContactInfo());
    }

    @DisplayName("signature - the signing date, as text and as a date")
    @Test
    public void signatureDate() {
        SignatureDictionary dictionary = signature(
                SignatureDictionary.M_KEY, new LiteralStringObject("D:20260912143005-05'00'"));
        assertEquals("D:20260912143005-05'00'", dictionary.getDate());
        assertNotNull(dictionary.getPDate());
        assertEquals("2026", dictionary.getPDate().getYear());
    }

    @DisplayName("signature - a dictionary with no date has no date, rather than an empty one")
    @Test
    public void signatureWithoutDate() {
        assertNull(signature().getDate());
        assertNull(signature().getPDate());
    }

    @DisplayName("signature - the signed bytes come back only when they are a hex string")
    @Test
    public void signatureContents() {
        // /Contents is always written as a hex string; a literal one means the file is wrong, and
        // handing it back would feed the validator bytes that are not the signature.
        SignatureDictionary hex = signature(
                SignatureDictionary.CONTENTS_KEY, new HexStringObject("ABCDEF"));
        assertNotNull(hex.getContents());

        SignatureDictionary literal = signature(
                SignatureDictionary.CONTENTS_KEY, new LiteralStringObject("ABCDEF"));
        assertNull(literal.getContents());
    }

    @DisplayName("signature - the certificate may be one string or an array of them")
    @Test
    public void signatureCertificate() {
        SignatureDictionary single = signature(
                SignatureDictionary.CERT_KEY, new LiteralStringObject("cert"));
        assertTrue(single.isCertString());
        assertFalse(single.isCertArray());
        assertNotNull(single.getCertString());
        assertNull(single.getCertArray());

        SignatureDictionary chain = signature(SignatureDictionary.CERT_KEY,
                new ArrayList<>(Arrays.asList(new LiteralStringObject("one"),
                        new LiteralStringObject("two"))));
        assertTrue(chain.isCertArray());
        assertFalse(chain.isCertString());
        assertEquals(2, chain.getCertArray().size());
        assertNull(chain.getCertString());
    }

    @DisplayName("signature - the two version numbers are separate entries")
    @Test
    public void signatureVersions() {
        // /R is the handler's version and /V the dictionary's; reading one for the other would
        // misreport which revision of the format the signature was written to.
        SignatureDictionary dictionary = signature(
                SignatureDictionary.R_KEY, 2,
                SignatureDictionary.V_KEY, 1);
        assertEquals(2, dictionary.getHandlerVersion());
        assertEquals(1, dictionary.getDictionaryVersion());
    }

    @DisplayName("signature - the entries a signer writes can be set and read back")
    @Test
    public void signatureRoundTrip() {
        SignatureDictionary dictionary = signature();
        dictionary.setName("A Signer");
        dictionary.setReason("Approval");
        dictionary.setLocation("Somewhere");
        dictionary.setContactInfo("signer@example.com");
        dictionary.setDate("D:20260912143005-05'00'");
        dictionary.setFilter(new Name("Adobe.PPKLite"));
        dictionary.setSubFilter(new Name("adbe.pkcs7.detached"));

        assertEquals("A Signer", dictionary.getName());
        assertEquals("Approval", dictionary.getReason());
        assertEquals("Somewhere", dictionary.getLocation());
        assertEquals("signer@example.com", dictionary.getContactInfo());
        assertEquals("D:20260912143005-05'00'", dictionary.getDate());
        assertEquals(new Name("Adobe.PPKLite"), dictionary.getFilter());
        assertEquals(new Name("adbe.pkcs7.detached"), dictionary.getSubFilter());
    }

    // ------------------------------------------------------------------
    // the seed value dictionary
    // ------------------------------------------------------------------

    private SeedValueDictionary seedValue(Object... keysAndValues) {
        return new SeedValueDictionary(library, entries(keysAndValues));
    }

    @DisplayName("seed value - the constraints a form places on signing")
    @Test
    public void seedValues() {
        SeedValueDictionary dictionary = seedValue(
                SeedValueDictionary.FILTER_KEY, new Name("Adobe.PPKLite"),
                SeedValueDictionary.V_KEY, 2,
                SeedValueDictionary.ADD_REV_INFO_KEY, Boolean.TRUE);
        assertEquals(new Name("Adobe.PPKLite"), dictionary.getFilterKey());
        assertEquals(2d, dictionary.getV(), 0.001);
        assertTrue(dictionary.getAddRevInfo());
    }

    @DisplayName("seed value - each flag bit marks its own entry as required")
    @Test
    public void seedValueFlags() {
        // A value of 1 means the matching entry is a constraint the signer must honour, so reading
        // the wrong bit either enforces something the form did not ask for or lets a signature
        // through that should have been refused.
        assertTrue(seedValue(SeedValueDictionary.Ff_KEY, 0x1).isFilter());
        assertTrue(seedValue(SeedValueDictionary.Ff_KEY, 0x2).isSubFilter());
        assertTrue(seedValue(SeedValueDictionary.Ff_KEY, 0x4).isV());
        assertTrue(seedValue(SeedValueDictionary.Ff_KEY, 0x8).isReasons());
        assertTrue(seedValue(SeedValueDictionary.Ff_KEY, 0x10).isLegalAttenstation());
        assertTrue(seedValue(SeedValueDictionary.Ff_KEY, 0x20).isAddRevInfo());
        assertTrue(seedValue(SeedValueDictionary.Ff_KEY, 0x40).isDigestMethod());

        // and only its own
        SeedValueDictionary onlyFilter = seedValue(SeedValueDictionary.Ff_KEY, 0x1);
        assertFalse(onlyFilter.isSubFilter());
        assertFalse(onlyFilter.isV());
        assertFalse(onlyFilter.isDigestMethod());
    }

    @DisplayName("seed value - no flags means nothing is required")
    @Test
    public void seedValueNoFlags() {
        SeedValueDictionary dictionary = seedValue();
        assertFalse(dictionary.isFilter());
        assertFalse(dictionary.isSubFilter());
        assertFalse(dictionary.isV());
        assertFalse(dictionary.isReasons());
        assertFalse(dictionary.isLegalAttenstation());
        assertFalse(dictionary.isAddRevInfo());
        assertFalse(dictionary.isDigestMethod());
    }

    // ------------------------------------------------------------------
    // the certificate seed value dictionary
    // ------------------------------------------------------------------

    private CertSeedValueDictionary certSeedValue(Object... keysAndValues) {
        return new CertSeedValueDictionary(library, entries(keysAndValues));
    }

    @DisplayName("certificate seed value - each flag bit marks its own entry as required")
    @Test
    public void certSeedValueFlags() {
        assertTrue(certSeedValue(CertSeedValueDictionary.Ff_KEY, 0x1).isSubject());
        assertTrue(certSeedValue(CertSeedValueDictionary.Ff_KEY, 0x2).isIssuer());
        assertTrue(certSeedValue(CertSeedValueDictionary.Ff_KEY, 0x4).isOid());
        assertTrue(certSeedValue(CertSeedValueDictionary.Ff_KEY, 0x8).isSubjectDn());
        assertTrue(certSeedValue(CertSeedValueDictionary.Ff_KEY, 0x10).isReserved());
        assertTrue(certSeedValue(CertSeedValueDictionary.Ff_KEY, 0x20).isKeyUsage());
        assertTrue(certSeedValue(CertSeedValueDictionary.Ff_KEY, 0x40).isUrl());

        CertSeedValueDictionary onlySubject = certSeedValue(CertSeedValueDictionary.Ff_KEY, 0x1);
        assertFalse(onlySubject.isIssuer());
        assertFalse(onlySubject.isUrl());
    }

    @DisplayName("certificate seed value - the url a signer can fetch a certificate from")
    @Test
    public void certSeedValueUrl() {
        CertSeedValueDictionary dictionary = certSeedValue(
                CertSeedValueDictionary.URL_KEY, new LiteralStringObject("https://example.com/ca"),
                CertSeedValueDictionary.URL_TYPE_KEY, new Name("Browser"));
        assertEquals("https://example.com/ca", dictionary.getUrl());
        assertEquals(new Name("Browser"), dictionary.getUrlType());
    }

    // ------------------------------------------------------------------
    // the additional actions dictionary
    // ------------------------------------------------------------------

    private static DictionaryEntries uriAction(String uri) {
        DictionaryEntries action = new DictionaryEntries();
        action.put(Action.ACTION_TYPE_KEY, Action.ACTION_TYPE_URI);
        action.put(new Name("URI"), new LiteralStringObject(uri));
        return action;
    }

    @DisplayName("additional actions - an action is found under the key it was filed with")
    @Test
    public void additionalActions() {
        AdditionalActionsDictionary dictionary = new AdditionalActionsDictionary(library, entries(
                AdditionalActionsDictionary.ANNOTATION_E_KEY, uriAction("https://example.com/enter"),
                AdditionalActionsDictionary.ANNOTATION_X_KEY, uriAction("https://example.com/exit")));

        assertTrue(dictionary.isAnnotationValue(AdditionalActionsDictionary.ANNOTATION_E_KEY));
        assertTrue(dictionary.isAnnotationValue(AdditionalActionsDictionary.ANNOTATION_X_KEY));
        assertFalse(dictionary.isAnnotationValue(AdditionalActionsDictionary.ANNOTATION_D_KEY));

        assertInstanceOf(Action.class,
                dictionary.getAction(AdditionalActionsDictionary.ANNOTATION_E_KEY));
        assertNull(dictionary.getAction(AdditionalActionsDictionary.ANNOTATION_D_KEY));
    }

    @DisplayName("additional actions - every trigger has a key of its own")
    @Test
    public void triggerKeysAreDistinct() {
        // These are the names written in the file, and two constants sharing a name means one
        // trigger's action fires for the other.  Nothing else would notice: the entries are all
        // optional, so reading the wrong key is indistinguishable from the file not having it.
        Name[] triggers = {
                AdditionalActionsDictionary.ANNOTATION_E_KEY,
                AdditionalActionsDictionary.ANNOTATION_X_KEY,
                AdditionalActionsDictionary.ANNOTATION_D_KEY,
                AdditionalActionsDictionary.ANNOTATION_U_KEY,
                AdditionalActionsDictionary.ANNOTATION_FO_KEY,
                AdditionalActionsDictionary.ANNOTATION_Bl_KEY,
                AdditionalActionsDictionary.ANNOTATION_PO_KEY,
                AdditionalActionsDictionary.ANNOTATION_PC_KEY,
                AdditionalActionsDictionary.ANNOTATION_PV_KEY,
                AdditionalActionsDictionary.ANNOTATION_PI_KEY,
        };
        Set<String> seen = new HashSet<>();
        for (Name trigger : triggers) {
            assertTrue(seen.add(trigger.getName()),
                    "two triggers are both filed under " + trigger.getName());
        }
    }

    @DisplayName("additional actions - the trigger keys are the names the specification gives")
    @Test
    public void triggerKeyNames() {
        // Asserted literally, because a constant naming the wrong entry reads perfectly well at
        // the call site: only the string it holds says which trigger it really is.
        assertEquals("E", AdditionalActionsDictionary.ANNOTATION_E_KEY.getName());
        assertEquals("X", AdditionalActionsDictionary.ANNOTATION_X_KEY.getName());
        assertEquals("D", AdditionalActionsDictionary.ANNOTATION_D_KEY.getName());
        assertEquals("U", AdditionalActionsDictionary.ANNOTATION_U_KEY.getName());
        assertEquals("Fo", AdditionalActionsDictionary.ANNOTATION_FO_KEY.getName());
        assertEquals("Bl", AdditionalActionsDictionary.ANNOTATION_Bl_KEY.getName());
        assertEquals("PO", AdditionalActionsDictionary.ANNOTATION_PO_KEY.getName());
        assertEquals("PC", AdditionalActionsDictionary.ANNOTATION_PC_KEY.getName());
        assertEquals("PV", AdditionalActionsDictionary.ANNOTATION_PV_KEY.getName());
        assertEquals("PI", AdditionalActionsDictionary.ANNOTATION_PI_KEY.getName());
    }

    @DisplayName("additional actions - the page and form triggers have their own names too")
    @Test
    public void pageAndFormTriggerNames() {
        assertEquals("O", AdditionalActionsDictionary.PAGE_0_KEY.getName());
        assertEquals("C", AdditionalActionsDictionary.PAGE_C_KEY.getName());
        assertEquals("K", AdditionalActionsDictionary.FORM_K_KEY.getName());
        assertEquals("F", AdditionalActionsDictionary.FORM_F_KEY.getName());
        assertEquals("V", AdditionalActionsDictionary.FORM_V_KEY.getName());
        assertEquals("C", AdditionalActionsDictionary.FORM_C_KEY.getName());
    }

    // ------------------------------------------------------------------
    // the usage rights transfer parameters
    // ------------------------------------------------------------------

    @DisplayName("usage rights - the rights granted in each category")
    @Test
    public void usageRights() {
        List<Object> documentRights = new ArrayList<>(Arrays.asList(new Name("FullSave")));
        List<Object> annotationRights = new ArrayList<>(Arrays.asList(
                new Name("Create"), new Name("Modify")));
        UR3TransferParam dictionary = new UR3TransferParam(library, entries(
                UR3TransferParam.DOCUMENT_KEY, documentRights,
                UR3TransferParam.ANNOTATION_KEY, annotationRights,
                UR3TransferParam.MSG_KEY, new LiteralStringObject("Rights enabled"),
                UR3TransferParam.PERMISSION_KEY, Boolean.TRUE));

        assertEquals(1, dictionary.getDocumentRights().size());
        assertEquals(2, dictionary.getAnnotationRights().size());
        assertEquals("Rights enabled", dictionary.getMsg());
        assertTrue(dictionary.getPermission());
    }

    @DisplayName("usage rights - a category with nothing granted grants nothing")
    @Test
    public void usageRightsAbsent() {
        UR3TransferParam dictionary = new UR3TransferParam(library, entries());
        assertNull(dictionary.getDocumentRights());
        assertNull(dictionary.getAnnotationRights());
        assertNull(dictionary.getFormRights());
        assertNull(dictionary.getSignatureRights());
        assertNull(dictionary.getEmbeddedFilesRights());
        assertNull(dictionary.getMsg());
        assertFalse(dictionary.getPermission());
    }

    @DisplayName("usage rights - each category is read from its own key")
    @Test
    public void usageRightsKeysAreDistinct() {
        // Granting annotation rights must not grant form or signature rights as a side effect.
        UR3TransferParam dictionary = new UR3TransferParam(library, entries(
                UR3TransferParam.ANNOTATION_KEY,
                new ArrayList<>(Arrays.asList(new Name("Create")))));
        assertEquals(1, dictionary.getAnnotationRights().size());
        assertNull(dictionary.getFormRights());
        assertNull(dictionary.getSignatureRights());
        assertNull(dictionary.getDocumentRights());
    }
}
