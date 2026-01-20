/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.icepdf.core.pobjects.acroform.signature.certificates;

import org.bouncycastle.cert.ocsp.OCSPException;
import org.icepdf.core.pobjects.acroform.signature.exceptions.CertificateVerificationException;
import org.icepdf.core.pobjects.acroform.signature.exceptions.RevokedCertificateException;
import org.icepdf.core.pobjects.acroform.signature.exceptions.SelfSignedVerificationException;

import java.io.IOException;
import java.net.URISyntaxException;
import java.security.GeneralSecurityException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * Revocations verifier class.
 * Main logic copied from Apache CXF 2.4.9, initial version:
 * <a href="https://svn.apache.org/repos/asf/cxf/tags/cxf-2.4.9/distribution/src/main/release/samples/sts_issue_operation/src/main/java/demo/sts/provider/cert/">Apache CXF 2.4.9</a>
 *
 */
public class RevocationsVerifier {

    private static final Logger logger = Logger.getLogger(RevocationsVerifier.class.toString());

    public static void verifyRevocations(X509Certificate cert,
                                         Set<X509Certificate> additionalCerts,
                                         Date signDate) throws CertificateException, NoSuchAlgorithmException,
            NoSuchProviderException, RevokedCertificateException, CertificateVerificationException, OCSPException,
            IOException, URISyntaxException, SelfSignedVerificationException {
        if (CertificateUtils.isSelfSigned(cert)) {
            // root, we're done
            return;
        }
        for (X509Certificate additionalCert : additionalCerts) {
            try {
                cert.verify(additionalCert.getPublicKey());
                checkRevocationsWithIssuer(cert, additionalCert, additionalCerts, signDate);
                // there can be several issuers
            } catch (GeneralSecurityException ex) {
                // not the issuer
            }
        }
    }

    private static void checkRevocationsWithIssuer(X509Certificate cert, X509Certificate issuerCert,
                                                   Set<X509Certificate> additionalCerts, Date signDate)
            throws OCSPException, CertificateVerificationException, RevokedCertificateException,
            GeneralSecurityException, IOException, URISyntaxException, SelfSignedVerificationException {
        // Try checking the certificate through OCSP (faster than CRL)
        String ocspURL = CertificateUtils.extractOCSPURL(cert);
        if (ocspURL != null) {
            OcspHelper ocspHelper = new OcspHelper(cert, signDate, issuerCert, additionalCerts, ocspURL);
            try {
                OCSPVerifier.verifyOCSP(ocspHelper, additionalCerts);
            } catch (IOException | OCSPException ex) {
                // IOException happens with 021496.pdf because OCSP responder no longer exists
                // OCSPException happens with QV_RCA1_RCA3_CPCPS_V4_11.pdf
                logger.log(Level.WARNING, "Exception trying OCSP, will try CRL", ex);
                logger.log(Level.WARNING, "Certificate# to check: " + cert.getSerialNumber().toString(16));
                CRLVerifier.verifyCRL(cert);//, signDate, additionalCerts);
            }
        } else {
            logger.finer("OCSP not available, will try CRL");

            // Check whether the certificate is revoked by the CRL
            // given in its CRL distribution point extension
            CRLVerifier.verifyCRL(cert);//, signDate, additionalCerts);
        }

        // now check the issuer
        verifyRevocations(issuerCert, additionalCerts, signDate);
    }


}
