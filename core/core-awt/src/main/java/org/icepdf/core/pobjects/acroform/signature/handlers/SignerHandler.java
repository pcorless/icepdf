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
import java.security.*;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

/**
 * Signer handles the setup and singing work to generate a PKCS7 signed hash for the given data. Implementing
 * classes must implement the abstract methods to create a keystore with access to a private key used for signing.
 */
public abstract class SignerHandler {

    protected static final String algorithm = "SHA256WithRSA";

    protected String certAlias;
    protected PasswordCallbackHandler callbackHandler;

    public SignerHandler(String certAlias, PasswordCallbackHandler callbackHandler) {
        this.certAlias = certAlias;
        this.callbackHandler = callbackHandler;
    }

    protected abstract KeyStore buildKeyStore() throws KeyStoreException;

    protected abstract PrivateKey getPrivateKey(KeyStore keyStore) throws KeyStoreException, UnrecoverableKeyException,
            NoSuchAlgorithmException;

    public byte[] signData(byte[] data) throws KeyStoreException, UnrecoverableKeyException, NoSuchAlgorithmException,
            CertificateException, OperatorCreationException, CMSException, IOException {

        KeyStore keystore = buildKeyStore();
        PrivateKey privateKey = getPrivateKey(keystore);

        X509Certificate certificate = (X509Certificate) keystore.getCertificate(certAlias);

        CMSSignedDataGenerator signedDataGenerator = new Pkcs7Generator()
                .createSignedDataGenerator(algorithm, new X509Certificate[]{certificate}, privateKey);

        CMSProcessableByteArray message =
                new CMSProcessableByteArray(new ASN1ObjectIdentifier(CMSObjectIdentifiers.data.getId()), data);
        CMSSignedData signedData = signedDataGenerator.generate(message, false);
        return signedData.getEncoded();
    }
}
