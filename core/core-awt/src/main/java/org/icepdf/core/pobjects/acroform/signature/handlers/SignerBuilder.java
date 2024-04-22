package org.icepdf.core.pobjects.acroform.signature.handlers;

import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.cms.CMSObjectIdentifiers;
import org.bouncycastle.cms.CMSException;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.CMSSignedDataGenerator;
import org.bouncycastle.operator.OperatorCreationException;
import org.icepdf.core.pobjects.acroform.signature.Pkcs7Generator;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;

public class SignerBuilder {

    private X509Certificate signingCertificate;
    private PrivateKey signingKey;

    private String signatureAlgorithm = "SHA256withRSA";

    public SignerBuilder setSigningCertificate(X509Certificate signingCertificate) {
        this.signingCertificate = signingCertificate;
        return this;
    }

    public SignerBuilder setSigningKey(PrivateKey signingKey) {
        this.signingKey = signingKey;
        return this;
    }

    public SignerBuilder setSignatureAlgorithm(String signatureAlgorithm) {
        this.signatureAlgorithm = signatureAlgorithm;
        return this;
    }

    public byte[] sign(byte[] data) throws NoSuchAlgorithmException, CertificateEncodingException,
            OperatorCreationException, CMSException,
            IOException {

        CMSSignedDataGenerator signedDataGenerator = new Pkcs7Generator()
                .createSignedDataGenerator(signatureAlgorithm, new X509Certificate[]{signingCertificate}, signingKey);

        CMSProcessableByteArray message =
                new CMSProcessableByteArray(new ASN1ObjectIdentifier(CMSObjectIdentifiers.data.getId()), data);

        CMSSignedData signedData = signedDataGenerator.generate(message, false);
        return signedData.getEncoded();
    }
}