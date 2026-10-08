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
package org.icepdf.core.util;

import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.acroform.DocMDPTransferParam;
import org.icepdf.core.pobjects.acroform.FieldDictionaryFactory;
import org.icepdf.core.pobjects.acroform.SignatureDictionary;
import org.icepdf.core.pobjects.acroform.signature.SignatureValidator;
import org.icepdf.core.pobjects.acroform.signature.appearance.SignatureType;
import org.icepdf.core.pobjects.acroform.signature.certificates.CertificateFixtures;
import org.icepdf.core.pobjects.acroform.signature.certificates.TsaServer;
import org.icepdf.core.pobjects.acroform.signature.handlers.Pkcs12SignerHandler;
import org.icepdf.core.pobjects.acroform.signature.handlers.SimplePasswordCallbackHandler;
import org.icepdf.core.pobjects.annotations.AnnotationFactory;
import org.icepdf.core.pobjects.annotations.SignatureWidgetAnnotation;
import org.icepdf.core.util.updater.WriteMode;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.Rectangle;
import java.io.BufferedOutputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

/**
 * GH-584: a new signature records its signing time.  Core never wrote /M, so viewers had no signing
 * time to show for signatures it made; now the updaters stamp it just before the signature
 * dictionary is written, and the CMS signingTime attribute carries the same instant.
 */
public class SigningTimeTest {

    private static final char[] PASSWORD = "secret".toCharArray();
    private static final Path ALL_FIELDS = Paths.get("src/test/resources/acroform/all_fields.pdf");
    private static final Path ENCRYPTED = Paths.get("src/test/resources/updater/encrypted-aes-v2.pdf");
    private static Path keystore;

    @TempDir
    static Path temp;

    @BeforeAll
    static void keystore() throws Exception {
        CertificateFixtures.Authority signer = CertificateFixtures.rootAuthority("Signing Time Test Signer");
        KeyStore store = KeyStore.getInstance("PKCS12");
        store.load(null, null);
        store.setKeyEntry("signer", signer.getPrivateKey(), PASSWORD, new Certificate[]{signer.getCertificate()});
        keystore = temp.resolve("signer.p12");
        try (OutputStream out = Files.newOutputStream(keystore)) {
            store.store(out, PASSWORD);
        }
    }

    /** Signs page 1 of {@code source}, optionally with a caller-set /M and a timestamp authority. */
    private static Path sign(Path source, String name, WriteMode mode, String date, String tsaUrl) throws Exception {
        Path target = temp.resolve(name + ".pdf");
        Document document = new Document();
        document.setFile(source.toString());
        Library library = document.getCatalog().getLibrary();
        SignatureWidgetAnnotation field = (SignatureWidgetAnnotation) AnnotationFactory.buildWidgetAnnotation(
                library, FieldDictionaryFactory.TYPE_SIGNATURE, new Rectangle(300, 40, 200, 60));
        document.getPageTree().getPage(0).addAnnotation(field, true);
        document.getCatalog().getOrCreateInteractiveForm().addField(field);
        SignatureDictionary dictionary = SignatureDictionary.getInstance(field, SignatureType.SIGNER,
                DocMDPTransferParam.PERMISSION_VALUE_FORMS_SIGNING);
        dictionary.setSignerHandler(new Pkcs12SignerHandler(tsaUrl, keystore.toFile(), "signer",
                new SimplePasswordCallbackHandler(new String(PASSWORD))));
        library.getSignatureDictionaries().addSignature(dictionary, field);
        dictionary.setName("Signing Time Test Signer");
        if (date != null) dictionary.setDate(date);
        try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(target))) {
            document.saveToOutputStream(out, mode);
        }
        document.dispose();
        return target;
    }

    /** The reopened signature's /M and its validator, validated. */
    private static final class Signed {
        private final String date;
        private final Date mDate;
        private final SignatureValidator validator;

        Signed(String date, Date mDate, SignatureValidator validator) {
            this.date = date;
            this.mDate = mDate;
            this.validator = validator;
        }

        String date() {
            return date;
        }

        Date mDate() {
            return mDate;
        }

        SignatureValidator validator() {
            return validator;
        }
    }

    private static Signed reopen(Path file) throws Exception {
        Document document = new Document();
        document.setFile(file.toString());
        SignatureWidgetAnnotation field = document.getCatalog().getInteractiveForm().getSignatureFields().get(0);
        SignatureDictionary dictionary = field.getFieldDictionary().getSignatureDictionary();
        SignatureValidator validator = field.getSignatureValidator();
        validator.validate();
        Signed signed = new Signed(dictionary.getDate(),
                dictionary.getPDate() != null ? dictionary.getPDate().asDateWithTimeZone() : null, validator);
        document.dispose();
        return signed;
    }

    @DisplayName("/M is written at save time and the CMS signingTime is the same instant")
    @ParameterizedTest
    @EnumSource(value = WriteMode.class, names = {"INCREMENT_UPDATE", "FULL_UPDATE"})
    void stampedOnSave(WriteMode mode) throws Exception {
        long before = System.currentTimeMillis() / 1000 * 1000;
        Signed signed = reopen(sign(ALL_FIELDS, "stamped-" + mode, mode, null, null));
        long after = System.currentTimeMillis();
        assertNotNull(signed.mDate(), "/M written: " + signed.date());
        assertTrue(signed.mDate().getTime() >= before && signed.mDate().getTime() <= after,
                "/M is the save time: " + signed.date());
        assertEquals(signed.mDate(), signed.validator().getSigningTime(), "CMS signingTime matches /M");
        assertFalse(signed.validator().isSignedDataModified());
    }

    @DisplayName("a /M the caller set is kept, and the CMS signingTime follows it")
    @Test
    void callerDateKept() throws Exception {
        String date = "D:20240423082733+02'00'";
        Signed signed = reopen(sign(ALL_FIELDS, "caller-date", WriteMode.INCREMENT_UPDATE, date, null));
        assertEquals(date, signed.date());
        assertEquals(signed.mDate(), signed.validator().getSigningTime());
        assertFalse(signed.validator().isSignedDataModified());
    }

    @DisplayName("an encrypted document: /M is encrypted like any string and reads back")
    @Test
    void encryptedDocument() throws Exception {
        long before = System.currentTimeMillis() / 1000 * 1000;
        Signed signed = reopen(sign(ENCRYPTED, "encrypted", WriteMode.INCREMENT_UPDATE, null, null));
        assertNotNull(signed.mDate(), "/M readable: " + signed.date());
        assertTrue(signed.mDate().getTime() >= before, signed.date());
        assertEquals(signed.mDate(), signed.validator().getSigningTime());
        assertFalse(signed.validator().isSignedDataModified());
    }

    @DisplayName("with a timestamp authority: getTimeStampTime is the authority's time")
    @Test
    void timestamped() throws Exception {
        CertificateFixtures.Authority root = CertificateFixtures.rootAuthority("Test Root");
        CertificateFixtures.Authority tsa = CertificateFixtures.timeStampAuthority("Test TSA", root);
        Date authorityTime = new Date(System.currentTimeMillis() / 1000 * 1000 - 5000);
        try (TsaServer server = new TsaServer(tsa).at(authorityTime)) {
            Signed signed = reopen(sign(ALL_FIELDS, "timestamped", WriteMode.INCREMENT_UPDATE, null, server.getUrl()));
            assertEquals(authorityTime, signed.validator().getTimeStampTime());
            assertNotNull(signed.validator().getSigningTime());
            assertFalse(signed.validator().isSignedDataModified());
        }
    }

    @DisplayName("no timestamp: getTimeStampTime is null")
    @Test
    void notTimestamped() throws Exception {
        Signed signed = reopen(sign(ALL_FIELDS, "no-tsa", WriteMode.INCREMENT_UPDATE, null, null));
        assertNull(signed.validator().getTimeStampTime());
    }
}
