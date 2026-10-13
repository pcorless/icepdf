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
import org.icepdf.core.pobjects.acroform.signature.appearance.SignatureType;
import org.icepdf.core.pobjects.acroform.signature.handlers.Pkcs12SignerHandler;
import org.icepdf.core.pobjects.acroform.signature.handlers.SimplePasswordCallbackHandler;
import org.icepdf.core.pobjects.annotations.SignatureWidgetAnnotation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.geom.Rectangle2D;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Signing end to end without a UI: a new field, a prepared signature, saved, reopened and checked by
 * {@link SignatureVerifier}.  The keystore is the project's self-signed test certificate, so the
 * verdict is UNKNOWN (identity not trusted) but the signed data must be intact.
 */
class DocumentSigningTest {

    private static final String PASSWORD = "changeit";

    @TempDir
    Path temp;

    private static Path resource(String name) throws Exception {
        return Paths.get(Objects.requireNonNull(DocumentSigningTest.class.getResource(name)).toURI());
    }

    private static Pkcs12SignerHandler signer(String alias) throws Exception {
        return new Pkcs12SignerHandler(null, resource("/signing/certificate.pfx").toFile(), alias,
                new SimplePasswordCallbackHandler(PASSWORD));
    }

    @DisplayName("the keystore lists its signing certificate")
    @Test
    void keyEntries() throws Exception {
        List<DocumentSigning.KeyEntry> entries = DocumentSigning.keyEntries(signer(null));
        assertFalse(entries.isEmpty());
        assertEquals("senderkeypair", entries.get(0).alias().toLowerCase());
    }

    @DisplayName("a new field, signed and saved, verifies intact after reopening")
    @Test
    void signsANewField() throws Exception {
        Path source = temp.resolve("source.pdf");
        Files.copy(resource("/forms/all_fields.pdf"), source);
        Path signed = temp.resolve("signed.pdf");

        Document document = new Document();
        document.setFile(source.toString());
        Pkcs12SignerHandler signer = signer(null);
        DocumentSigning.KeyEntry entry = DocumentSigning.keyEntries(signer).get(0);
        signer.setCertAlias(entry.alias());
        assertTrue(DocumentSigning.canSign(document));
        SignatureWidgetAnnotation field = DocumentSigning.addSignatureField(document, 0,
                new Rectangle2D.Double(300, 40, 200, 60));
        DocumentSigning.Request request = new DocumentSigning.Request(SignatureType.SIGNER, "Test Signer",
                "signer@example.com", "Calgary", null);
        SignatureAppearance appearance = new SignatureAppearance(document.getCatalog().getLibrary());
        DocumentSigning.prepare(field, signer, request, appearance);
        DocumentSigning.saveSigned(document, signed);
        document.dispose();

        Document reopened = new Document();
        reopened.setFile(signed.toString());
        try {
            List<SignatureStatus> statuses = SignatureVerifier.verifyAll(reopened);
            SignatureStatus status = statuses.stream().filter(SignatureStatus::isSigned).findFirst().orElseThrow();
            assertFalse(status.signedDataModified(), status.summary());
            assertFalse(status.modifiedAfterSigning(), "the signature covers the whole file");
            assertEquals(SignatureStatus.Verdict.UNKNOWN, status.verdict(), "self-signed test certificate");
            assertEquals("Calgary", status.location());
            assertNotNull(status.signingTime(), "/M is written");
            assertEquals(0, status.pageIndex());
            assertFalse(status.certification());
        } finally {
            reopened.dispose();
        }
        assertTrue(new File(signed.toString()).length() > Files.size(source), "an incremental update");
    }

    @DisplayName("a drawn signature is embedded as the field's image and the file still verifies")
    @Test
    void signsWithDrawnInk() throws Exception {
        Path source = temp.resolve("source.pdf");
        Files.copy(resource("/forms/all_fields.pdf"), source);
        Path signed = temp.resolve("signed.pdf");

        Document document = new Document();
        document.setFile(source.toString());
        Pkcs12SignerHandler signer = signer(null);
        signer.setCertAlias(DocumentSigning.keyEntries(signer).get(0).alias());
        SignatureWidgetAnnotation field = DocumentSigning.addSignatureField(document, 0,
                new Rectangle2D.Double(300, 40, 200, 60));
        Ink ink = new Ink();
        ink.parse("20,80 60,40 100,90 140,30 180,70\n200,60 260,62");
        SignatureAppearance appearance = new SignatureAppearance(document.getCatalog().getLibrary());
        appearance.setImage(ink.toImage(3));
        appearance.setImageVisible(true);
        DocumentSigning.prepare(field, signer, new DocumentSigning.Request(SignatureType.SIGNER, "Test Signer",
                null, null, null), appearance);
        assertNotNull(appearance.getImageXObjectReference(), "the drawn image is written");
        assertNotNull(appearance.getImageSoftMaskXObjectReference(), "with its transparency");
        DocumentSigning.saveSigned(document, signed);
        document.dispose();

        Document reopened = new Document();
        reopened.setFile(signed.toString());
        try {
            SignatureStatus status = SignatureVerifier.verifyAll(reopened).stream()
                    .filter(SignatureStatus::isSigned).findFirst().orElseThrow();
            assertFalse(status.signedDataModified(), status.summary());
            assertFalse(status.modifiedAfterSigning());
        } finally {
            reopened.dispose();
        }
    }

    @DisplayName("redrawing the signature updates the preview (not the first image, cached by object number)")
    @Test
    void previewFollowsANewImage() throws Exception {
        Document document = new Document();
        document.setFile(resource("/forms/all_fields.pdf").toString());
        try {
            SignatureWidgetAnnotation field = DocumentSigning.addSignatureField(document, 0,
                    new Rectangle2D.Double(300, 40, 200, 60));
            SignatureAppearance appearance = new SignatureAppearance(document.getCatalog().getLibrary());
            appearance.setTextVisible(false);
            appearance.setLayout(SignatureAppearance.Layout.OVERLAY);
            Ink ink = new Ink();
            ink.setWidth(6);
            ink.parse("0,0 100,0");
            appearance.setImage(ink.toImage(1));
            DocumentSigning.previewAppearance(field, appearance);
            render(field);
            org.icepdf.core.pobjects.Reference first = appearance.getImageXObjectReference();

            // now a tall stroke: a stale image would paint the flat one, squeezed into a narrow column.
            ink.parse("0,0 0,100");
            appearance.setImage(ink.toImage(1));
            DocumentSigning.previewAppearance(field, appearance);
            java.awt.image.BufferedImage second = render(field);
            assertNotEquals(first, appearance.getImageXObjectReference(), "a new image, a new object");
            int inked = 0;
            for (int y = 0; y < second.getHeight(); y++) {
                if ((second.getRGB(second.getWidth() / 2, y) & 0xFFFFFF) != 0xFFFFFF) inked++;
            }
            assertTrue(inked > second.getHeight() / 2, "the vertical stroke spans the field: " + inked);
        } finally {
            document.dispose();
        }
    }

    private static java.awt.image.BufferedImage render(SignatureWidgetAnnotation field) {
        Rectangle2D r = field.getUserSpaceRectangle();
        java.awt.image.BufferedImage image = new java.awt.image.BufferedImage((int) r.getWidth(), (int) r.getHeight(),
                java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = image.createGraphics();
        g.setColor(java.awt.Color.WHITE);
        g.fillRect(0, 0, image.getWidth(), image.getHeight());
        g.transform(new java.awt.geom.AffineTransform(1, 0, 0, -1, -r.getX(), r.getY() + r.getHeight()));
        field.render(g, org.icepdf.core.util.GraphicsRenderingHints.SCREEN, 0f, 1f, false);
        g.dispose();
        return image;
    }

    @DisplayName("an e-mail address in a certificate name comes out as text, not a byte array")
    @Test
    void derEncodedValue() {
        byte[] ia5 = {0x16, 0x0E, 'm', 'e', '@', 'e', 'x', 'a', 'm', 'p', 'l', 'e', '.', 'c', 'o', 'm'};
        assertEquals("me@example.com", SignatureVerifier.valueOf(ia5));
        assertEquals("plain", SignatureVerifier.valueOf("plain"));
    }

    @DisplayName("a certification signature can't be added twice")
    @Test
    void certifiesOnce() throws Exception {
        Path source = temp.resolve("source.pdf");
        Files.copy(resource("/forms/all_fields.pdf"), source);
        Path certified = temp.resolve("certified.pdf");

        Document document = new Document();
        document.setFile(source.toString());
        Pkcs12SignerHandler signer = signer(null);
        signer.setCertAlias(DocumentSigning.keyEntries(signer).get(0).alias());
        SignatureWidgetAnnotation field = DocumentSigning.addSignatureField(document, 0,
                new Rectangle2D.Double(300, 40, 200, 60));
        DocumentSigning.prepare(field, signer, new DocumentSigning.Request(SignatureType.CERTIFIER, "Certifier",
                null, null, null), new SignatureAppearance(document.getCatalog().getLibrary()));
        DocumentSigning.saveSigned(document, certified);
        document.dispose();

        Document reopened = new Document();
        reopened.setFile(certified.toString());
        try {
            SignatureStatus status = SignatureVerifier.verifyAll(reopened).stream()
                    .filter(SignatureStatus::isSigned).findFirst().orElseThrow();
            assertTrue(status.certification());
            assertFalse(status.signedDataModified());
            assertFalse(DocumentSigning.canCertify(reopened), "already certified");
        } finally {
            reopened.dispose();
        }
    }
}
