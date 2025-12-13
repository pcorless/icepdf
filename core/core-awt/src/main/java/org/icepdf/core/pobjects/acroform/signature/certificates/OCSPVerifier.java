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

import org.bouncycastle.asn1.ocsp.OCSPObjectIdentifiers;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.ocsp.BasicOCSPResp;
import org.bouncycastle.cert.ocsp.OCSPException;
import org.bouncycastle.cert.ocsp.OCSPResp;
import org.icepdf.core.pobjects.acroform.signature.exceptions.CertificateVerificationException;
import org.icepdf.core.pobjects.acroform.signature.exceptions.RevokedCertificateException;
import org.icepdf.core.pobjects.acroform.signature.exceptions.SelfSignedVerificationException;

import java.io.IOException;
import java.net.URISyntaxException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateExpiredException;
import java.security.cert.X509Certificate;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Main logic copied from Apache CXF 2.4.9, initial version:
 * https://svn.apache.org/repos/asf/cxf/tags/cxf-2.4
 * .9/distribution/src/main/release/samples/sts_issue_operation/src/main/java/demo/sts/provider/cert/
 *
 */
public class OCSPVerifier {

    private static final Logger logger = Logger.getLogger(OCSPVerifier.class.toString());

    /**
     * Verify whether the certificate has been revoked at signing date, and verify whether
     * the certificate of the responder has been revoked now.
     *
     * @param ocspHelper      the OCSP helper.
     * @param additionalCerts
     * @throws RevokedCertificateException
     * @throws IOException
     * @throws URISyntaxException
     * @throws OCSPException
     * @throws CertificateVerificationException
     */
    public static void verifyOCSP(OcspHelper ocspHelper, Set<X509Certificate> additionalCerts)
            throws RevokedCertificateException, IOException, OCSPException,
            CertificateVerificationException, URISyntaxException, CertificateExpiredException,
            SelfSignedVerificationException {
        Date now = Calendar.getInstance().getTime();
        OCSPResp ocspResponse;
        ocspResponse = ocspHelper.getResponseOcsp();
        if (ocspResponse.getStatus() != OCSPResp.SUCCESSFUL) {
            throw new CertificateVerificationException("OCSP check not successful, status: "
                    + ocspResponse.getStatus());
        }
        logger.finer("OCSP check successful");

        BasicOCSPResp basicResponse = (BasicOCSPResp) ocspResponse.getResponseObject();
        X509Certificate ocspResponderCertificate = ocspHelper.getOcspResponderCertificate();
        if (ocspResponderCertificate.getExtensionValue(OCSPObjectIdentifiers.id_pkix_ocsp_nocheck.getId()) != null) {
            // https://tools.ietf.org/html/rfc6960#section-4.2.2.2.1
            // A CA may specify that an OCSP client can trust a responder for the
            // lifetime of the responder's certificate.  The CA does so by
            // including the extension id-pkix-ocsp-nocheck.
            logger.finer("Revocation check of OCSP responder certificate skipped (id-pkix-ocsp-nocheck is set)");
            return;
        }

        if (ocspHelper.getCertificateToCheck().equals(ocspResponderCertificate)) {
            logger.finer("OCSP responder certificate is identical to certificate to check");
            return;
        }

        logger.finer("Check of OCSP responder certificate");
        Set<X509Certificate> additionalCerts2 = new HashSet<>(additionalCerts);
        JcaX509CertificateConverter certificateConverter = new JcaX509CertificateConverter();
        for (X509CertificateHolder certHolder : basicResponse.getCerts()) {
            try {
                X509Certificate cert = certificateConverter.getCertificate(certHolder);
                if (!ocspResponderCertificate.equals(cert)) {
                    additionalCerts2.add(cert);
                }
            } catch (CertificateException ex) {
                // unlikely to happen because the certificate existed as an object
                logger.log(Level.WARNING, "Could not convert OCSP responder certificate holder to X509Certificate", ex);
            }
        }
        CertificateVerifier.verifyCertificate(ocspResponderCertificate, additionalCerts2, true, now);
        logger.finer("Check of OCSP responder certificate done");
    }
}
