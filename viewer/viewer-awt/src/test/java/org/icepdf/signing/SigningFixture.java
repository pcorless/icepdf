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

import org.icepdf.core.pobjects.Document;
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
import org.icepdf.ri.common.views.annotations.signing.BasicSignatureAppearanceCallback;
import org.icepdf.ri.common.views.annotations.signing.SignatureAppearanceModelImpl;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Date;
import java.util.Locale;

/**
 * Signs a document, the way the tests here need it signed.
 * <p>
 * Three tests wanted the same twenty-odd lines with a couple of details different, and had three
 * copies of them. What each test is actually about - a level A document staying conformant, an
 * indirect {@code /AcroForm} being written through, a signature validating - is the part that was
 * hardest to see for all the setup around it.
 */
public class SigningFixture {

    private static final String KEYSTORE = "src/test/resources/signing/certificate.pfx";
    private static final String PASSWORD = "changeit";
    private static final String ALIAS = "senderKeyPair";
    private static final String TIME_STAMP_AUTHORITY = "http://time.certum.pl";

    private final File source;
    private SignatureType signatureType = SignatureType.CERTIFIER;
    private String reason = "Certification";
    private boolean appearance;
    private BufferedImage signatureImage;

    private SigningFixture(File source) {
        this.source = source;
    }

    public static SigningFixture of(File source) {
        return new SigningFixture(source);
    }

    public SigningFixture signatureType(SignatureType signatureType) {
        this.signatureType = signatureType;
        return this;
    }

    public SigningFixture reason(String reason) {
        this.reason = reason;
        return this;
    }

    /**
     * Draws the signature's appearance. Off by default: it pulls in the font machinery, which a test
     * about where a dictionary entry lands does not need.
     */
    public SigningFixture withAppearance() {
        this.appearance = true;
        return this;
    }

    public SigningFixture withAppearance(BufferedImage signatureImage) {
        this.signatureImage = signatureImage;
        return withAppearance();
    }

    /**
     * @param outputFile where to write the signed document
     * @return the file written, so a caller can go straight on to reading it
     */
    public File signTo(File outputFile) throws Exception {
        JceProvider.loadProvider();
        PfxGenerator.createPfx(KEYSTORE, PASSWORD, ALIAS);
        Pkcs12SignerHandler signerHandler = new Pkcs12SignerHandler(TIME_STAMP_AUTHORITY,
                new File(KEYSTORE), ALIAS, new SimplePasswordCallbackHandler(PASSWORD));

        Document document = new Document();
        try (InputStream stream = new FileInputStream(source)) {
            document.setInputStream(stream, source.getName());
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
                SignatureDictionary.getInstance(signatureAnnotation, signatureType);
        signatureDictionary.setSignerHandler(signerHandler);
        signatureDictionary.setReason(reason);
        signatureDictionary.setDate(PDate.formatDateTime(new Date()));
        signatureManager.addSignature(signatureDictionary, signatureAnnotation);
        SignatureUtilities.updateSignatureDictionary(signatureDictionary, signerHandler.getCertificate());

        if (appearance) {
            SignatureAppearanceModelImpl appearanceModel = new SignatureAppearanceModelImpl(library);
            appearanceModel.setLocale(Locale.ENGLISH);
            appearanceModel.setName(signatureDictionary.getName());
            appearanceModel.setContact(signatureDictionary.getContactInfo());
            appearanceModel.setLocation(signatureDictionary.getLocation());
            appearanceModel.setSignatureType(signatureType);
            if (signatureImage != null) {
                appearanceModel.setSignatureImage(signatureImage);
            }

            BasicSignatureAppearanceCallback appearanceCallback = new BasicSignatureAppearanceCallback();
            appearanceCallback.setSignatureAppearanceModel(appearanceModel);
            signatureAnnotation.setAppearanceCallback(appearanceCallback);
            signatureAnnotation.resetAppearanceStream(new AffineTransform());
            signatureAnnotation.saveAppearanceStream();
        }

        outputFile.getParentFile().mkdirs();
        try (BufferedOutputStream stream = new BufferedOutputStream(new FileOutputStream(outputFile), 8192)) {
            document.saveToOutputStream(stream, WriteMode.INCREMENT_UPDATE);
        }
        document.dispose();
        return outputFile;
    }
}
