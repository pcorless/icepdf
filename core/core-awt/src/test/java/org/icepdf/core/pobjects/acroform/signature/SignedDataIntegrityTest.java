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
package org.icepdf.core.pobjects.acroform.signature;

import org.bouncycastle.cert.jcajce.JcaCertStore;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.CMSSignedDataGenerator;
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;
import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.acroform.signature.certificates.CertificateFixtures;
import org.icepdf.core.pobjects.annotations.SignatureWidgetAnnotation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Whether a signature's covered bytes were changed (isSignedDataModified), for each way a PKCS#7
 * signature can be laid out: adbe.pkcs7.detached and adbe.pkcs7.sha1 (the SHA-1 of the byte range
 * encapsulated as the signed content), each with and without signed attributes.  The documents are
 * built here, signed with Bouncy Castle by a generated self-signed key, so every case is exact.
 * <p>
 * Before the fix only detached-with-attributes worked: adbe.pkcs7.sha1 and attribute-less
 * signatures always read as modified, and the signature value itself was never verified, so a
 * document whose digests had been recomputed after an edit read as unmodified.
 */
public class SignedDataIntegrityTest {

    private static final String DETACHED = "adbe.pkcs7.detached";
    private static final String SHA1 = "adbe.pkcs7.sha1";
    private static final int CONTENTS_BYTES = 8192;

    private static CertificateFixtures.Authority signer;

    @BeforeAll
    static void signer() throws Exception {
        signer = CertificateFixtures.rootAuthority("Integrity Test Signer");
    }

    private enum Damage {NONE, CONTENT_CHANGED, SIGNATURE_FORGED}

    @DisplayName("adbe.pkcs7.detached with signed attributes: unmodified")
    @Test
    void detachedWithAttributes() throws Exception {
        assertFalse(signedDataModified(DETACHED, true, Damage.NONE));
    }

    @DisplayName("adbe.pkcs7.detached without signed attributes: unmodified")
    @Test
    void detachedWithoutAttributes() throws Exception {
        assertFalse(signedDataModified(DETACHED, false, Damage.NONE));
    }

    @DisplayName("adbe.pkcs7.sha1 with signed attributes: unmodified")
    @Test
    void sha1WithAttributes() throws Exception {
        assertFalse(signedDataModified(SHA1, true, Damage.NONE));
    }

    @DisplayName("adbe.pkcs7.sha1 without signed attributes: unmodified")
    @Test
    void sha1WithoutAttributes() throws Exception {
        assertFalse(signedDataModified(SHA1, false, Damage.NONE));
    }

    @DisplayName("a byte changed inside the signed range is caught, every layout")
    @Test
    void contentChanged() throws Exception {
        assertTrue(signedDataModified(DETACHED, true, Damage.CONTENT_CHANGED));
        assertTrue(signedDataModified(DETACHED, false, Damage.CONTENT_CHANGED));
        assertTrue(signedDataModified(SHA1, true, Damage.CONTENT_CHANGED));
        assertTrue(signedDataModified(SHA1, false, Damage.CONTENT_CHANGED));
    }

    @DisplayName("matching digests with a broken signature value are caught, every layout")
    @Test
    void signatureForged() throws Exception {
        assertTrue(signedDataModified(DETACHED, true, Damage.SIGNATURE_FORGED));
        assertTrue(signedDataModified(DETACHED, false, Damage.SIGNATURE_FORGED));
        assertTrue(signedDataModified(SHA1, true, Damage.SIGNATURE_FORGED));
        assertTrue(signedDataModified(SHA1, false, Damage.SIGNATURE_FORGED));
    }

    private boolean signedDataModified(String subFilter, boolean signedAttributes, Damage damage) throws Exception {
        Path file = Files.createTempFile("signed-" + subFilter, ".pdf");
        try {
            Files.write(file, signedPdf(subFilter, signedAttributes, damage));
            Document document = new Document();
            document.setFile(file.toString());
            try {
                SignatureWidgetAnnotation field =
                        document.getCatalog().getInteractiveForm().getSignatureFields().get(0);
                SignatureValidator validator = field.getSignatureValidator();
                validator.validate();
                return validator.isSignedDataModified();
            } finally {
                document.dispose();
            }
        } finally {
            Files.deleteIfExists(file);
        }
    }

    /** A one-page document with one signature field, signed as asked. */
    private static byte[] signedPdf(String subFilter, boolean signedAttributes, Damage damage) throws Exception {
        String placeholder = "[0 0000000000 0000000000 0000000000]";
        List<String> objects = new ArrayList<>();
        objects.add("<< /Type /Catalog /Pages 2 0 R /AcroForm << /Fields [4 0 R] /SigFlags 3 >> >>");
        objects.add("<< /Type /Pages /Kids [3 0 R] /Count 1 >>");
        objects.add("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 200 200] /Annots [4 0 R] >>");
        objects.add("<< /Type /Annot /Subtype /Widget /FT /Sig /T (Signature1) /Rect [0 0 0 0] /F 132 /P 3 0 R"
                + " /V 5 0 R >>");
        objects.add("<< /Type /Sig /Filter /Adobe.PPKLite /SubFilter /" + subFilter + " /M (D:20260101000000Z)"
                + " /ByteRange " + placeholder + " /Contents <" + "0".repeat(CONTENTS_BYTES * 2) + "> >>");
        StringBuilder pdf = new StringBuilder("%PDF-1.7\n");
        List<Integer> offsets = new ArrayList<>();
        for (int i = 0; i < objects.size(); i++) {
            offsets.add(pdf.length());
            pdf.append(i + 1).append(" 0 obj\n").append(objects.get(i)).append("\nendobj\n");
        }
        int xref = pdf.length();
        pdf.append("xref\n0 ").append(objects.size() + 1).append("\n0000000000 65535 f \n");
        for (int offset : offsets) pdf.append(String.format("%010d 00000 n \n", offset));
        pdf.append("trailer\n<< /Size ").append(objects.size() + 1).append(" /Root 1 0 R >>\nstartxref\n")
                .append(xref).append("\n%%EOF\n");

        byte[] bytes = pdf.toString().getBytes(StandardCharsets.ISO_8859_1);
        String text = pdf.toString();
        int contentsStart = text.indexOf("/Contents <") + "/Contents ".length();
        int contentsEnd = text.indexOf('>', contentsStart) + 1;
        String range = String.format("[0 %010d %010d %010d]", contentsStart, contentsEnd, bytes.length - contentsEnd);
        int rangeAt = text.indexOf(placeholder);
        System.arraycopy(range.getBytes(StandardCharsets.ISO_8859_1), 0, bytes, rangeAt, range.length());

        ByteArrayOutputStream signedRange = new ByteArrayOutputStream();
        signedRange.write(bytes, 0, contentsStart);
        signedRange.write(bytes, contentsEnd, bytes.length - contentsEnd);
        byte[] cms = sign(signedRange.toByteArray(), subFilter, signedAttributes);
        if (damage == Damage.SIGNATURE_FORGED) {
            // break the signature value itself, leaving every digest as signed.
            byte[] value = new CMSSignedData(cms).getSignerInfos().getSigners().iterator().next().getSignature();
            int at = indexOf(cms, value);
            cms[at + value.length / 2] ^= 0x01;
        }
        byte[] hex = toHex(cms).getBytes(StandardCharsets.ISO_8859_1);
        System.arraycopy(hex, 0, bytes, contentsStart + 1, hex.length);
        if (damage == Damage.CONTENT_CHANGED) {
            // the media box 200 -> 201: inside the first signed section, after signing.
            int at = text.indexOf("/MediaBox [0 0 200 200]") + "/MediaBox [0 0 20".length();
            bytes[at] = '1';
        }
        return bytes;
    }

    private static byte[] sign(byte[] signedRange, String subFilter, boolean signedAttributes) throws Exception {
        boolean sha1 = SHA1.equals(subFilter);
        ContentSigner contentSigner = new JcaContentSignerBuilder(sha1 ? "SHA1withRSA" : "SHA256withRSA")
                .build(signer.getPrivateKey());
        CMSSignedDataGenerator generator = new CMSSignedDataGenerator();
        generator.addSignerInfoGenerator(new JcaSignerInfoGeneratorBuilder(
                new JcaDigestCalculatorProviderBuilder().build())
                .setDirectSignature(!signedAttributes)
                .build(contentSigner, signer.getCertificate()));
        generator.addCertificates(new JcaCertStore(List.of(signer.getCertificate())));
        CMSSignedData signed;
        if (sha1) {
            // adbe.pkcs7.sha1: the byte range's SHA-1 digest is the encapsulated content.
            byte[] digest = MessageDigest.getInstance("SHA-1").digest(signedRange);
            signed = generator.generate(new CMSProcessableByteArray(digest), true);
        } else {
            signed = generator.generate(new CMSProcessableByteArray(signedRange), false);
        }
        return signed.getEncoded();
    }

    private static int indexOf(byte[] data, byte[] part) {
        outer:
        for (int i = 0; i <= data.length - part.length; i++) {
            for (int j = 0; j < part.length; j++) {
                if (data[i + j] != part[j]) continue outer;
            }
            return i;
        }
        throw new AssertionError("signature value not found in the encoding");
    }

    private static String toHex(byte[] data) {
        StringBuilder out = new StringBuilder(data.length * 2);
        for (byte b : data) out.append(String.format("%02X", b & 0xFF));
        return out.toString();
    }
}
