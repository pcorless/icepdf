package org.icepdf.core.pobjects.acroform.signature.handlers;

import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.cms.CMSObjectIdentifiers;
import org.bouncycastle.cms.CMSException;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.CMSSignedDataGenerator;
import org.bouncycastle.operator.OperatorCreationException;
import org.icepdf.core.pobjects.acroform.signature.Pkcs7Generator;
import org.icepdf.core.pobjects.acroform.signature.certificates.TimeStampVerifier;

import java.io.IOException;
import java.net.URISyntaxException;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Signer handles the setup and signing work to generate a PKCS7 signed hash for the given data. Implementing
 * classes must implement the abstract methods to create a keystore with access to a private key used for signing.
 */
public abstract class SignerHandler {

    private static final Logger logger = Logger.getLogger(SignerHandler.class.toString());

    protected static final String algorithm = "SHA256WithRSA";

    protected String certAlias;
    protected String tsaUrl;
    protected KeyStore keystore;
    protected PasswordCallbackHandler callbackHandler;

    public SignerHandler(String timeStampAuthorityUrl, String certAlias, PasswordCallbackHandler callbackHandler) {
        this.certAlias = certAlias;
        this.tsaUrl = timeStampAuthorityUrl;
        // todo username and password support, not sure it's needed yet.
        this.callbackHandler = callbackHandler;
    }

    public void setCertAlias(String certAlias) {
        this.certAlias = certAlias;
    }

    public abstract KeyStore buildKeyStore() throws KeyStoreException;

    protected abstract PrivateKey getPrivateKey() throws KeyStoreException, UnrecoverableKeyException,
            NoSuchAlgorithmException;

    public X509Certificate getCertificate() throws KeyStoreException {
        if (keystore == null) {
            keystore = buildKeyStore();
        }
        return (X509Certificate) keystore.getCertificate(certAlias);
    }

    public X509Certificate getCertificate(String alias) throws KeyStoreException {
        if (keystore == null) {
            keystore = buildKeyStore();
        }
        return (X509Certificate) keystore.getCertificate(alias);
    }

    public byte[] signData(byte[] data) throws KeyStoreException, UnrecoverableKeyException, NoSuchAlgorithmException,
            CertificateException, OperatorCreationException, CMSException, IOException {

        if (keystore == null) {
            keystore = buildKeyStore();
        }
        PrivateKey privateKey = getPrivateKey();

        X509Certificate certificate = (X509Certificate) keystore.getCertificate(certAlias);

        CMSSignedDataGenerator signedDataGenerator = new Pkcs7Generator()
                .createSignedDataGenerator(algorithm, new X509Certificate[]{certificate}, privateKey);

        CMSProcessableByteArray message =
                new CMSProcessableByteArray(new ASN1ObjectIdentifier(CMSObjectIdentifiers.data.getId()), data);
        CMSSignedData signedData = signedDataGenerator.generate(message, false);
        if (tsaUrl != null && !tsaUrl.isEmpty()) {
            try {
                TimeStampVerifier timeStampHandler = new TimeStampVerifier(tsaUrl);
                signedData = timeStampHandler.addSignedTimeStamp(signedData);
            } catch (URISyntaxException e) {
                throw new RuntimeException(e);
            }
        } else {
            logger.log(Level.WARNING, "No TSA URL provided, skipping timestamping.");
        }
        return signedData.getEncoded();
    }
}
