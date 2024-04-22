package org.icepdf.core.pobjects.acroform.signature.handlers;

import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.cms.CMSObjectIdentifiers;
import org.bouncycastle.cms.CMSException;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.CMSSignedDataGenerator;
import org.bouncycastle.operator.OperatorCreationException;
import org.icepdf.core.pobjects.acroform.signature.Pkcs7Generator;

import java.io.File;
import java.io.IOException;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

public class Pkcs12SignerHandler implements SignerHandler {

    private static final String algorithm = "SHA256WithRSA";

    private final File keystoreFile;
    private final String certAlias;
    private final PasswordCallbackHandler callbackHandler;

    public Pkcs12SignerHandler(File keyStoreFile, String certAlias, PasswordCallbackHandler callbackHandler) {
        this.keystoreFile = keyStoreFile;
        this.certAlias = certAlias;
        this.callbackHandler = callbackHandler;
    }

    @Override
    public byte[] signData(byte[] data) throws KeyStoreException, UnrecoverableKeyException, NoSuchAlgorithmException
            , CertificateException, OperatorCreationException, CMSException, IOException {

        KeyStore.CallbackHandlerProtection chp = new KeyStore.CallbackHandlerProtection(callbackHandler);
        KeyStore.Builder builder = KeyStore.Builder.newInstance(keystoreFile, chp);
        KeyStore keystore = builder.getKeyStore();

        X509Certificate certificate = (X509Certificate) keystore.getCertificate(certAlias);
        PrivateKey privateKey = (PrivateKey) keystore.getKey(certAlias, callbackHandler.getPassword());

        CMSSignedDataGenerator signedDataGenerator = new Pkcs7Generator()
                .createSignedDataGenerator(algorithm, new X509Certificate[]{certificate}, privateKey);

        CMSProcessableByteArray message =
                new CMSProcessableByteArray(new ASN1ObjectIdentifier(CMSObjectIdentifiers.data.getId()), data);
        CMSSignedData signedData = signedDataGenerator.generate(message, false);
        return signedData.getEncoded();
    }
}
