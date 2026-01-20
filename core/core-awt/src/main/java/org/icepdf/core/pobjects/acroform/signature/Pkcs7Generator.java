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
