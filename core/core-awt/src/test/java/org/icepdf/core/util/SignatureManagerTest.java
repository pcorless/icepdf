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
import org.icepdf.core.pobjects.acroform.SignatureReferenceDictionary;
import org.icepdf.core.pobjects.acroform.signature.appearance.SignatureType;
import org.icepdf.core.pobjects.acroform.signature.certificates.CertificateFixtures;
import org.icepdf.core.pobjects.acroform.signature.handlers.Pkcs12SignerHandler;
import org.icepdf.core.pobjects.acroform.signature.handlers.SimplePasswordCallbackHandler;
import org.icepdf.core.pobjects.annotations.AnnotationFactory;
import org.icepdf.core.pobjects.annotations.SignatureWidgetAnnotation;
import org.icepdf.core.util.updater.WriteMode;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SignatureManager's checks against the signatures already in a file: an approval signature (no
 * /Reference) used to NPE hasExistingCertifier, and a certification forbidding changes was ignored by
 * hasPermissionToSignDocument, which only saw the signature being added in the session.  The
 * documents are signed here with a generated key.
 */
public class SignatureManagerTest {

    private static final char[] PASSWORD = "secret".toCharArray();
    private static Path keystore;

    @TempDir
    static Path temp;

    @BeforeAll
    static void keystore() throws Exception {
        CertificateFixtures.Authority signer = CertificateFixtures.rootAuthority("Permission Test Signer");
        KeyStore store = KeyStore.getInstance("PKCS12");
        store.load(null, null);
        store.setKeyEntry("signer", signer.getPrivateKey(), PASSWORD, new Certificate[]{signer.getCertificate()});
        keystore = temp.resolve("signer.p12");
        try (OutputStream out = Files.newOutputStream(keystore)) {
            store.store(out, PASSWORD);
        }
    }

    /** all_fields.pdf signed once as asked, saved as an incremental update. */
    private static Path signed(String name, SignatureType type, int permission) throws Exception {
        Path source = Paths.get("src/test/resources/acroform/all_fields.pdf");
        Path target = temp.resolve(name + ".pdf");
        Document document = new Document();
        document.setFile(source.toString());
        Library library = document.getCatalog().getLibrary();
        SignatureWidgetAnnotation field = (SignatureWidgetAnnotation) AnnotationFactory.buildWidgetAnnotation(
                library, FieldDictionaryFactory.TYPE_SIGNATURE, new Rectangle(300, 40, 200, 60));
        document.getPageTree().getPage(0).addAnnotation(field, true);
        document.getCatalog().getOrCreateInteractiveForm().addField(field);
        SignatureDictionary dictionary = SignatureDictionary.getInstance(field, type, permission);
        dictionary.setSignerHandler(new Pkcs12SignerHandler(null, keystore.toFile(), "signer",
                new SimplePasswordCallbackHandler(new String(PASSWORD))));
        library.getSignatureDictionaries().addSignature(dictionary, field);
        dictionary.setName("Permission Test Signer");
        try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(target))) {
            document.saveToOutputStream(out, WriteMode.INCREMENT_UPDATE);
        }
        document.dispose();
        return target;
    }

    private static Document open(Path file) throws Exception {
        Document document = new Document();
        document.setFile(file.toString());
        return document;
    }

    @DisplayName("an unsigned document: no certifier, signing allowed")
    @Test
    public void unsigned() throws Exception {
        Document document = open(Paths.get("src/test/resources/acroform/all_fields.pdf"));
        try {
            Library library = document.getCatalog().getLibrary();
            SignatureManager manager = library.getSignatureDictionaries();
            assertFalse(manager.hasExistingCertifier(library));
            assertTrue(manager.hasPermissionToSignDocument());
        } finally {
            document.dispose();
        }
    }

    @DisplayName("an approval signature (no /Reference): no certifier, signing allowed, no NPE")
    @Test
    public void approvalSigned() throws Exception {
        Document document = open(signed("approval", SignatureType.SIGNER,
                DocMDPTransferParam.PERMISSION_VALUE_FORMS_SIGNING));
        try {
            Library library = document.getCatalog().getLibrary();
            SignatureManager manager = library.getSignatureDictionaries();
            assertFalse(manager.hasExistingCertifier(library));
            assertTrue(manager.hasPermissionToSignDocument());
            SignatureDictionary signature = library.getCatalog().getInteractiveForm().getSignatureFields().get(0)
                    .getFieldDictionary().getSignatureDictionary();
            assertTrue(signature.getReferences().isEmpty(), "no references, not null");
        } finally {
            document.dispose();
        }
    }

    @DisplayName("certified 'no changes': a certifier, and no more signatures")
    @Test
    public void certifiedNoChanges() throws Exception {
        Document document = open(signed("certified-p1", SignatureType.CERTIFIER,
                DocMDPTransferParam.PERMISSION_VALUE_NO_CHANGES));
        try {
            Library library = document.getCatalog().getLibrary();
            SignatureManager manager = library.getSignatureDictionaries();
            assertTrue(manager.hasExistingCertifier(library));
            assertEquals(1, manager.existingCertificationPermission(library));
            assertFalse(manager.hasPermissionToSignDocument(), "the certification forbids changes");
        } finally {
            document.dispose();
        }
    }

    @DisplayName("certified 'form fill-in and signing': a certifier, approval signatures allowed")
    @Test
    public void certifiedFormsSigning() throws Exception {
        Document document = open(signed("certified-p2", SignatureType.CERTIFIER,
                DocMDPTransferParam.PERMISSION_VALUE_FORMS_SIGNING));
        try {
            Library library = document.getCatalog().getLibrary();
            SignatureManager manager = library.getSignatureDictionaries();
            assertTrue(manager.hasExistingCertifier(library));
            assertEquals(2, manager.existingCertificationPermission(library));
            assertTrue(manager.hasPermissionToSignDocument());
        } finally {
            document.dispose();
        }
    }

    @DisplayName("references read from a file are typed: no ClassCastException")
    @Test
    public void referencesAreTyped() throws Exception {
        Document document = open(signed("certified-refs", SignatureType.CERTIFIER,
                DocMDPTransferParam.PERMISSION_VALUE_FORMS_SIGNING));
        try {
            SignatureDictionary signature = document.getCatalog().getInteractiveForm().getSignatureFields().get(0)
                    .getFieldDictionary().getSignatureDictionary();
            List<SignatureReferenceDictionary> references = signature.getReferences();
            assertEquals(1, references.size());
            assertEquals(SignatureReferenceDictionary.TransformMethods.DocMDP, references.get(0).getTransformMethod());
        } finally {
            document.dispose();
        }
    }
}
