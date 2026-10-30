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

import javafx.collections.ListChangeListener;
import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.acroform.signature.SignatureValidationOptions;
import org.icepdf.core.pobjects.acroform.signature.handlers.Pkcs12SignerHandler;
import org.icepdf.core.pobjects.acroform.signature.handlers.SimplePasswordCallbackHandler;
import org.icepdf.core.pobjects.acroform.signature.appearance.SignatureType;
import org.icepdf.core.pobjects.annotations.SignatureWidgetAnnotation;
import org.icepdf.fx.testing.Fx;
import org.icepdf.fx.view.PdfView;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.geom.Rectangle2D;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * GH-643: the JavaFX view lists every signature at once, PENDING, and replaces each as its check
 * finishes; one signature can be checked again; changing the network settings checks again; and a
 * trusted signature whose revocation went unchecked says so.
 */
class SignatureCheckingTest {

    private static final SignatureValidationOptions OFFLINE = SignatureValidationOptions.defaults().withOnline(false);

    @TempDir
    Path temp;

    @BeforeAll
    static void fx() {
        Fx.start();
    }

    private static Path resource(String name) throws Exception {
        return Paths.get(Objects.requireNonNull(SignatureCheckingTest.class.getResource(name)).toURI());
    }

    /** A copy of the form fixture with one new field signed by the self-signed test certificate. */
    private Path signedDocument() throws Exception {
        Path source = temp.resolve("source.pdf");
        Files.copy(resource("/forms/all_fields.pdf"), source);
        Path signed = temp.resolve("signed.pdf");
        Document document = new Document();
        document.setFile(source.toString());
        Pkcs12SignerHandler signer = new Pkcs12SignerHandler(null, resource("/signing/certificate.pfx").toFile(),
                null, new SimplePasswordCallbackHandler("changeit"));
        signer.setCertAlias(DocumentSigning.keyEntries(signer).get(0).alias());
        SignatureWidgetAnnotation field = DocumentSigning.addSignatureField(document, 0,
                new Rectangle2D.Double(300, 40, 200, 60));
        DocumentSigning.prepare(field, signer, new DocumentSigning.Request(SignatureType.SIGNER, "Test Signer",
                "signer@example.com", "Calgary", null), new SignatureAppearance(document.getCatalog().getLibrary()));
        DocumentSigning.saveSigned(document, signed);
        document.dispose();
        return signed;
    }

    @DisplayName("the view lists a signed field as PENDING first, then its result; re-checks one and on new options")
    @Test
    void progressiveResults() throws Exception {
        Document document = new Document();
        document.setFile(signedDocument().toString());
        try {
            List<SignatureStatus.Verdict> seen = Collections.synchronizedList(new ArrayList<>());
            PdfView view = Fx.call(() -> {
                PdfView v = new PdfView();
                v.setSignatureValidationOptions(OFFLINE);
                v.getSignatures().addListener((ListChangeListener<SignatureStatus>) c -> {
                    for (SignatureStatus status : v.getSignatures()) {
                        if (status.isSigned()) seen.add(status.verdict());
                    }
                });
                v.setDocument(document);
                return v;
            });
            Fx.waitFor("the signature check", () -> !view.isVerifyingSignatures()
                    && view.getSignatures().stream().anyMatch(s -> s.isSigned() && !s.isPending()));
            assertEquals(SignatureStatus.Verdict.PENDING, seen.get(0), "listed before it was checked");
            SignatureStatus checked = Fx.call(() -> view.getSignatures().stream()
                    .filter(SignatureStatus::isSigned).findFirst().orElseThrow());
            assertEquals(SignatureStatus.Verdict.UNKNOWN, checked.verdict(), "self-signed test certificate");
            assertTrue(checked.selfSigned());
            assertFalse(checked.isRevocationUnchecked(), "self-signed: checking online can't help");

            // one signature again: PENDING while it runs, then the result.
            seen.clear();
            SignatureStatus again = Fx.call(() -> view.verifySignature(checked, null)).get(30, TimeUnit.SECONDS);
            assertEquals(SignatureStatus.Verdict.PENDING, seen.get(0));
            assertEquals(SignatureStatus.Verdict.UNKNOWN, again.verdict());
            assertTrue(Fx.call(() -> view.getSignatures().contains(again)), "the result replaced the entry");

            // new network settings check the document again.
            seen.clear();
            Fx.run(() -> view.setSignatureValidationOptions(OFFLINE.withTimeouts(1000, 1000)));
            Fx.waitFor("the second check", () -> !view.isVerifyingSignatures() && seen.size() > 1);
            assertEquals(SignatureStatus.Verdict.PENDING, seen.get(0));
        } finally {
            document.dispose();
        }
    }

    @DisplayName("a trusted signature whose revocation went unchecked says so and can be checked online")
    @Test
    void revocationUnchecked() {
        SignatureStatus status = new SignatureStatus(null, 0, "Signature1", SignatureStatus.Verdict.VALID,
                "Signer", null, null, null, null, null, null, false, false, false, false,
                true, false, false, false, true, List.of(), null);
        assertTrue(status.isRevocationUnchecked());
        assertTrue(SignaturePropertiesDialog.checkList(status).stream().anyMatch(c ->
                c.text().equals(org.icepdf.fx.i18n.Messages.get("signatureProperties.check.revocationUnchecked"))));

        SignatureStatus checked = new SignatureStatus(null, 0, "Signature1", SignatureStatus.Verdict.VALID,
                "Signer", null, null, null, null, null, null, false, false, false, false,
                true, false, false, true, true, List.of(), null);
        assertFalse(checked.isRevocationUnchecked());
        assertEquals(SignatureStatus.Verdict.PENDING, checked.asPending().verdict());
        assertTrue(checked.asPending().isSigned());
    }
}
