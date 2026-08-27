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
package org.icepdf.signing;

import org.icepdf.core.pobjects.Catalog;
import org.icepdf.core.pobjects.Dictionary;
import org.icepdf.core.pobjects.DictionaryEntries;
import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.Name;
import org.icepdf.core.pobjects.PDate;
import org.icepdf.core.pobjects.acroform.FieldDictionaryFactory;
import org.icepdf.core.pobjects.acroform.InteractiveForm;
import org.icepdf.core.pobjects.acroform.SignatureDictionary;
import org.icepdf.core.pobjects.acroform.signature.appearance.SignatureType;
import org.icepdf.core.pobjects.acroform.signature.handlers.Pkcs12SignerHandler;
import org.icepdf.core.pobjects.acroform.signature.handlers.SimplePasswordCallbackHandler;
import org.icepdf.core.pobjects.acroform.signature.utils.SignatureUtilities;
import org.icepdf.core.pobjects.annotations.AnnotationFactory;
import org.icepdf.core.pobjects.annotations.SignatureWidgetAnnotation;
import org.icepdf.core.pobjects.security.JceProvider;
import org.icepdf.core.util.Library;
import org.icepdf.core.util.SignatureManager;
import org.icepdf.core.util.updater.WriteMode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.*;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Signing a document whose {@code /AcroForm} and {@code /Perms} are objects in their own right.
 * <p>
 * Both are usually written inline in the catalog, and every fixture this project had did it that way.
 * They are allowed to be indirect references and real documents make them so - and then changing them
 * does not change the catalog, which still holds the same reference. Code that mutated one and
 * registered the <em>catalog</em> as the changed object therefore wrote nothing at all: the signature
 * field never joined {@code /Fields}, {@code /SigFlags} was never declared, and asking for a
 * certification produced a signature with no {@code /Perms /DocMDP} behind it. All three failed
 * silently, and the save reported success.
 */
public class IndirectAcroFormSigningTest {

    /**
     * The fixture's catalog says {@code /AcroForm 4 0 R} and {@code /Perms 5 0 R}, and both objects
     * start empty. Everything asserted here lives in one of those two objects, so if the wrong object
     * is registered as changed, none of it is in the file.
     */
    @DisplayName("signing writes through an indirect /AcroForm and /Perms")
    @Test
    public void indirectAcroFormAndPermsArePersisted() throws Exception {
        File signed = sign(new File("src/test/resources/signing/indirect_acroform.pdf"),
                new File("./src/test/out/IndirectAcroFormSigningTest_signed.pdf"));

        Document document = new Document();
        try (InputStream stream = new java.io.FileInputStream(signed)) {
            document.setInputStream(stream, signed.getName());
        }
        try {
            Catalog catalog = document.getCatalog();
            Library library = catalog.getLibrary();

            DictionaryEntries acroForm = library.getDictionary(catalog.getEntries(), Catalog.ACRO_FORM_KEY);
            assertNotNull(acroForm, "the fixture's /AcroForm should still resolve");
            assertEquals(3, library.getInt(acroForm, new Name("SigFlags")),
                    "SignaturesExist and AppendOnly, written into the referenced object");
            assertEquals(1, ((List<?>) library.getObject(acroForm, new Name("Fields"))).size(),
                    "the signature field should have joined /Fields");

            DictionaryEntries perms = library.getDictionary(catalog.getEntries(), Catalog.PERMS_KEY);
            assertNotNull(perms, "the fixture's /Perms should still resolve");
            assertNotNull(perms.get(new Name("DocMDP")),
                    "a certification signature has to be recorded in /Perms to certify anything");
        } finally {
            document.dispose();
        }
    }

    // -- helpers ---------------------------------------------------------------------------------

    /**
     * The signing flow, without an appearance stream - what is under test is which object the changes
     * land in, and an appearance would only add font machinery to the fixture.
     */
    private File sign(File source, File outputFile) throws Exception {
        JceProvider.loadProvider();
        String keystorePath = "src/test/resources/signing/certificate.pfx";
        PfxGenerator.createPfx(keystorePath, "changeit", "senderKeyPair");
        Pkcs12SignerHandler signerHandler = new Pkcs12SignerHandler(
                "http://time.certum.pl", new File(keystorePath), "senderKeyPair",
                new SimplePasswordCallbackHandler("changeit"));

        Document document = new Document();
        try (InputStream fileUrl = new java.io.FileInputStream(source)) {
            document.setInputStream(fileUrl, source.getName());
        }
        Library library = document.getCatalog().getLibrary();
        SignatureManager signatureManager = library.getSignatureDictionaries();

        SignatureWidgetAnnotation signatureAnnotation =
                (SignatureWidgetAnnotation) AnnotationFactory.buildWidgetAnnotation(
                        library, FieldDictionaryFactory.TYPE_SIGNATURE,
                        new Rectangle(100, 250, 375, 150));
        document.getPageTree().getPage(0).addAnnotation(signatureAnnotation, true);

        InteractiveForm interactiveForm = document.getCatalog().getOrCreateInteractiveForm();
        interactiveForm.addField(signatureAnnotation);

        SignatureDictionary signatureDictionary =
                SignatureDictionary.getInstance(signatureAnnotation, SignatureType.CERTIFIER);
        signatureDictionary.setSignerHandler(signerHandler);
        signatureDictionary.setReason("Certification");
        signatureDictionary.setDate(PDate.formatDateTime(new Date()));
        signatureManager.addSignature(signatureDictionary, signatureAnnotation);
        SignatureUtilities.updateSignatureDictionary(signatureDictionary, signerHandler.getCertificate());

        outputFile.getParentFile().mkdirs();
        try (BufferedOutputStream stream = new BufferedOutputStream(new FileOutputStream(outputFile), 8192)) {
            document.saveToOutputStream(stream, WriteMode.INCREMENT_UPDATE);
        }
        document.dispose();
        return outputFile;
    }
}
