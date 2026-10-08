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
package org.icepdf.fx.signature;

import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.PDate;
import org.icepdf.core.pobjects.Page;
import org.icepdf.core.pobjects.acroform.FieldDictionaryFactory;
import org.icepdf.core.pobjects.acroform.InteractiveForm;
import org.icepdf.core.pobjects.acroform.SignatureDictionary;
import org.icepdf.core.pobjects.acroform.signature.appearance.SignatureType;
import org.icepdf.core.pobjects.acroform.signature.handlers.SignerHandler;
import org.icepdf.core.pobjects.annotations.AnnotationFactory;
import org.icepdf.core.pobjects.annotations.SignatureWidgetAnnotation;
import org.icepdf.core.util.Library;
import org.icepdf.core.util.SignatureManager;
import org.icepdf.core.util.updater.WriteMode;

import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * Signing a document with core, without any UI: add or pick a signature field, prepare the
 * signature (who, why, how it looks), then save; core computes and embeds the signature while it
 * writes an incremental update.  No JavaFX types; {@link SignDialog} is one way to collect the
 * details.
 * <pre>{@code
 * SignerHandler signer = new Pkcs12SignerHandler(tsaUrl, keystoreFile, alias,
 *         new SimplePasswordCallbackHandler(password));
 * SignatureWidgetAnnotation field = DocumentSigning.addSignatureField(document, 0, rect);
 * DocumentSigning.prepare(field, signer, request, appearance);
 * DocumentSigning.saveSigned(document, Path.of("signed.pdf"));
 * }</pre>
 * One signature per save: core signs the single signature prepared since the last save.
 */
public final class DocumentSigning {

    private DocumentSigning() {
    }

    /** A certificate in a keystore that has a private key to sign with. */
    public record KeyEntry(String alias, X509Certificate certificate) {
        public String commonName() {
            String cn = SignatureVerifier.part(certificate, "CN");
            return cn != null ? cn : certificate.getSubjectX500Principal().getName();
        }
    }

    /** What a signature says about itself. */
    public record Request(SignatureType type, String name, String contact, String location, String reason) {
        public Request {
            type = type != null ? type : SignatureType.SIGNER;
        }
    }

    /**
     * The DocMDP permission of the document's certification signature (PDF 32000-1 12.8.2.2): 0 when
     * the document isn't certified, else 1 (no changes), 2 (form fill-in and signing) or 3 (also
     * annotations).
     */
    public static int certificationPermission(Document document) {
        Library library = document.getCatalog().getLibrary();
        return library.getSignatureDictionaries().existingCertificationPermission(library);
    }

    /** Whether a new signature may be added: the document isn't certified "no changes". */
    public static boolean canSign(Document document) {
        return document.getCatalog().getLibrary().getSignatureDictionaries().hasPermissionToSignDocument();
    }

    /** Whether a certification signature may be added: the document isn't certified already. */
    public static boolean canCertify(Document document) {
        return certificationPermission(document) == 0;
    }

    /**
     * The signing certificates of a keystore (entries holding a private key).  Opening the keystore
     * asks the handler's password callback.
     */
    public static List<KeyEntry> keyEntries(SignerHandler signer) throws KeyStoreException {
        KeyStore keyStore = signer.buildKeyStore();
        List<KeyEntry> out = new ArrayList<>();
        for (String alias : Collections.list(keyStore.aliases())) {
            if (keyStore.isKeyEntry(alias) && keyStore.getCertificate(alias) instanceof X509Certificate c) {
                out.add(new KeyEntry(alias, c));
            }
        }
        return out;
    }

    /** The name, e-mail and place a certificate names, for pre-filling a {@link Request}. */
    public static Request requestFrom(X509Certificate certificate, SignatureType type) {
        String state = SignatureVerifier.part(certificate, "ST");
        String country = SignatureVerifier.part(certificate, "C");
        String place = state != null && country != null ? state + ", " + country : state != null ? state : country;
        return new Request(type, SignatureVerifier.part(certificate, "CN"),
                SignatureVerifier.part(certificate, "EMAILADDRESS", "E", "1.2.840.113549.1.9.1"), place, null);
    }

    /**
     * Adds an empty signature field to a page and the document's form.
     *
     * @param bounds in the page's user space (PDF points, origin bottom-left)
     */
    public static SignatureWidgetAnnotation addSignatureField(Document document, int pageIndex,
                                                              Rectangle2D bounds) {
        Library library = document.getCatalog().getLibrary();
        SignatureWidgetAnnotation field = (SignatureWidgetAnnotation) AnnotationFactory.buildWidgetAnnotation(
                library, FieldDictionaryFactory.TYPE_SIGNATURE, bounds.getBounds());
        InteractiveForm form = document.getCatalog().getOrCreateInteractiveForm();
        // a field needs a name (/T): signature panels and validators list it by name.
        java.util.Set<String> taken = new java.util.HashSet<>();
        for (SignatureWidgetAnnotation existing : form.getSignatureFields()) {
            taken.add(existing.getFieldDictionary().getFullyQualifiedFieldName());
        }
        int n = 1;
        while (taken.contains("Signature" + n)) n++;
        field.getEntries().put(org.icepdf.core.pobjects.acroform.FieldDictionary.T_KEY,
                new org.icepdf.core.pobjects.LiteralStringObject("Signature" + n));
        Page page = document.getPageTree().getPage(pageIndex);
        page.addAnnotation(field, true);
        form.addField(field);
        return field;
    }

    /**
     * Rebuilds the field's appearance from {@code appearance} without preparing a signature, for a
     * live preview while the signer chooses.  Undo with {@link #cancel}.
     */
    public static void previewAppearance(SignatureWidgetAnnotation field, SignatureAppearance appearance) {
        SignatureAppearanceBuilder builder = builderOf(field);
        builder.setSignatureAppearanceModel(appearance);
        field.setAppearanceCallback(builder);
        field.resetAppearanceStream(new AffineTransform());
    }

    /**
     * Prepares the field to be signed by {@code signer} on the next save: the signature
     * dictionary, its details and the field's appearance.
     *
     * @throws IllegalStateException the document forbids signing, or is already certified and
     *                               {@code request} certifies
     */
    public static void prepare(SignatureWidgetAnnotation field, SignerHandler signer, Request request,
                               SignatureAppearance appearance) {
        Library library = field.getLibrary();
        SignatureManager manager = library.getSignatureDictionaries();
        if (!manager.hasPermissionToSignDocument()) {
            throw new IllegalStateException("The document's certification doesn't permit more signatures.");
        }
        if (request.type() == SignatureType.CERTIFIER && manager.hasExistingCertifier(library)) {
            throw new IllegalStateException("The document is already certified; it can only be approved.");
        }
        SignatureDictionary dictionary = SignatureDictionary.getInstance(field, request.type());
        dictionary.setSignerHandler(signer);
        manager.addSignature(dictionary, field);
        if (request.name() != null) dictionary.setName(request.name());
        if (request.contact() != null) dictionary.setContactInfo(request.contact());
        if (request.location() != null) dictionary.setLocation(request.location());
        dictionary.setReason(request.reason() != null && !request.reason().isBlank() ? request.reason()
                : request.type().toString().toLowerCase());
        dictionary.setDate(PDate.formatDateTime(new Date()));

        appearance.setSignatureType(request.type());
        appearance.setName(request.name());
        appearance.setContact(request.contact());
        appearance.setLocation(request.location());
        previewAppearance(field, appearance);
        field.saveAppearanceStream();
    }

    /** Undoes {@link #previewAppearance} or {@link #prepare}: the field is left empty and unsigned. */
    public static void cancel(SignatureWidgetAnnotation field) {
        SignatureAppearanceBuilder builder = builderOf(field);
        if (builder.signatureAppearanceModel != null) {
            builder.removeAppearanceStream(field, new AffineTransform());
        } else {
            field.getLibrary().getSignatureDictionaries().clearSignatures();
        }
        field.setAppearanceCallback(null);
        BUILDERS.remove(field);
    }

    /**
     * Saves the document with its changes and the prepared signature, as an incremental update:
     * core signs while it writes.  Writes to a temporary file first, so a failed signing never
     * leaves a half-written file behind.
     */
    public static void saveSigned(Document document, Path target) throws IOException {
        Path temp = Files.createTempFile(target.toAbsolutePath().getParent(), ".signing", ".pdf");
        try {
            try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(temp), 8192)) {
                document.saveToOutputStream(out, WriteMode.INCREMENT_UPDATE);
            }
            Files.move(temp, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            Files.deleteIfExists(temp);
            throw e instanceof IOException io ? io : new IOException("Signing failed: " + e.getMessage(), e);
        }
    }

    // one builder per field, so a preview rebuild discards the objects the previous one wrote.
    private static final java.util.Map<SignatureWidgetAnnotation, SignatureAppearanceBuilder> BUILDERS =
            Collections.synchronizedMap(new java.util.WeakHashMap<>());

    private static SignatureAppearanceBuilder builderOf(SignatureWidgetAnnotation field) {
        return BUILDERS.computeIfAbsent(field, f -> new SignatureAppearanceBuilder());
    }
}
