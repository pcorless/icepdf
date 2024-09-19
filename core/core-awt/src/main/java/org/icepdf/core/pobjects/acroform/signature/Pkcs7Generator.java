package org.icepdf.core.pobjects.acroform.signature;

import org.bouncycastle.cert.jcajce.JcaCertStore;
import org.bouncycastle.cms.CMSException;
import org.bouncycastle.cms.CMSSignedDataGenerator;
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;

import java.security.PrivateKey;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.Arrays;

/**
 * Pkcs7Generator is a utility class for creating a PKCS7 signature.
 */
public class Pkcs7Generator {

    private CMSSignedDataGenerator signedDataGenerator;

    public Pkcs7Generator() {
    }

    public CMSSignedDataGenerator createSignedDataGenerator(String algorithmName, X509Certificate[] certs,
                                                            PrivateKey privateKey) throws CertificateEncodingException, OperatorCreationException, CMSException {
        signedDataGenerator = new CMSSignedDataGenerator();
        X509Certificate cert = certs[0];
        ContentSigner sha1Signer = new JcaContentSignerBuilder(algorithmName).build(privateKey);
        signedDataGenerator.addSignerInfoGenerator(new JcaSignerInfoGeneratorBuilder(new JcaDigestCalculatorProviderBuilder().build()).build(sha1Signer, cert));
        signedDataGenerator.addCertificates(new JcaCertStore(Arrays.asList(certs)));
        return signedDataGenerator;
    }
}
