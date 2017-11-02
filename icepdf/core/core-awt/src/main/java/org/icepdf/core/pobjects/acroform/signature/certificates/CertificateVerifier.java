/*
 * Copyright 2006-2017 ICEsoft Technologies Canada Corp.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an "AS
 * IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */
package org.icepdf.core.pobjects.acroform.signature.certificates;

import org.bouncycastle.jce.exception.ExtCertPathValidatorException;
import org.icepdf.core.pobjects.acroform.signature.exceptions.CertificateVerificationException;
import org.icepdf.core.pobjects.acroform.signature.exceptions.RevocationVerificationException;
import org.icepdf.core.pobjects.acroform.signature.exceptions.SelfSignedVerificationException;

import java.security.*;
import java.security.cert.*;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Class for building a certification chain for given certificate and verifying
 * it. Relies on a set of root CA certificates and intermediate certificates
 * that will be used for building the certification chain. The verification
 * process assumes that all self-signed certificates in the set are trusted
 * root CA certificates and all other certificates in the set are intermediate
 * certificates.
 *
 * @author Svetlin Nakov
 */
public class CertificateVerifier {

    /**
     * Attempts to build a certification chain for given certificate and to verify
     * it. Relies on a set of root CA certificates and intermediate certificates
     * that will be used for building the certification chain. The verification
     * process assumes that all self-signed certificates in the set are trusted
     * root CA certificates and all other certificates in the set are intermediate
     * certificates.
     *
     * @param signerCert      signer certificate
     * @param cert            - certificate for validation
     * @param additionalCerts - set of trusted root CA certificates that will be
     *                        used as "trust anchors" and intermediate CA certificates that will be
     *                        used as part of the certification chain. All self-signed certificates
     *                        are considered to be trusted root CA certificates. All the rest are
     *                        considered to be intermediate CA certificates.
     * @return the certification chain (if verification is successful)
     * @throws CertificateVerificationException - if the certification is not
     *                                          successful (e.g. certification path cannot be built or some
     *                                          certificate in the chain is expired or CRL checks are failed)
     * @throws CertificateVerificationException could not verify cert.
     * @throws CertificateExpiredException      cert is expired
     * @throws SelfSignedVerificationException  self signed cert.
     * @throws RevocationVerificationException revocation verifcation error.
     */
    public static PKIXCertPathBuilderResult verifyCertificate(X509Certificate signerCert, X509Certificate[] cert,
                                                              Collection<X509Certificate> additionalCerts)
            throws CertificateVerificationException, CertificateExpiredException, SelfSignedVerificationException,
            RevocationVerificationException {
        try {
            // Check for self-signed root certificate
            if (isSelfSigned(signerCert)) {
                throw new SelfSignedVerificationException("The certificate is self-signed.");
            }
            // Prepare a set of trusted root CA certificates
            // and a set of intermediate certificates
            Set<X509Certificate> trustedRootCerts = new HashSet<>();
            Set<X509Certificate> intermediateCerts = new HashSet<>();
            for (X509Certificate additionalCert : additionalCerts) {
                if (isSelfSigned(additionalCert)) {
                    trustedRootCerts.add(additionalCert);
                } else {
                    intermediateCerts.add(additionalCert);
                }
            }
            // add in any specified intermediate certificates
            intermediateCerts.addAll(Arrays.asList(cert));

            // Attempt to build the certification chain and verify it, first element should always be the signer cert.
            PKIXCertPathBuilderResult verifiedCertChain =
                    verifyCertificate(cert[0], trustedRootCerts, intermediateCerts);

            // Check whether the certificate is revoked by the CRL
            // given in its CRL distribution point extension
            CRLVerifier.verifyCertificateCRLs(cert[0]);

            // The chain is built and verified. Return it as a result
            return verifiedCertChain;
        } catch (CertPathBuilderException certPathEx) {
            if (certPathEx.getCause() instanceof ExtCertPathValidatorException) {
                if (certPathEx.getCause().getCause() instanceof CertificateExpiredException) {
                    throw (CertificateExpiredException) certPathEx.getCause().getCause();
                }
            }
            throw new CertificateVerificationException(
                    "Error building certification path: " + signerCert.getSubjectX500Principal(), certPathEx);
        } catch (CertificateVerificationException | RevocationVerificationException cvex) {
            throw cvex;
        } catch (Exception ex) {
            throw new CertificateVerificationException(
                    "Error verifying the certificate: " + signerCert.getSubjectX500Principal(), ex);
        }
    }

    /**
     * Attempts to build a certification chain for given certificate and to verify
     * it. Relies on a set of root CA certificates (trust anchors) and a set of
     * intermediate certificates (to be used as part of the chain).
     *
     * @param cert              - certificate for validation
     * @param trustedRootCerts  - set of trusted root CA certificates
     * @param intermediateCerts - set of intermediate certificates
     * @return the certification chain (if verification is successful)
     * @throws GeneralSecurityException - if the verification is not successful
     *                                  (e.g. certification path cannot be built or some certificate in the
     *                                  chain is expired)
     */
    private static PKIXCertPathBuilderResult verifyCertificate(X509Certificate cert, Set<X509Certificate> trustedRootCerts,
                                                               Set<X509Certificate> intermediateCerts)
            throws GeneralSecurityException {

        // Create the selector that specifies the starting certificate
        X509CertSelector selector = new X509CertSelector();
        selector.setCertificate(cert);

        // Create the trust anchors (set of root CA certificates)
        Set<TrustAnchor> trustAnchors = new HashSet<>();
        for (X509Certificate trustedRootCert : trustedRootCerts) {
            trustAnchors.add(new TrustAnchor(trustedRootCert, null));
        }

        // Configure the PKIX certificate builder algorithm parameters
        PKIXBuilderParameters pkixParams =
                new PKIXBuilderParameters(trustAnchors, selector);

        // Disable CRL checks (this is done manually as additional step)
        pkixParams.setRevocationEnabled(false);

        // Specify a list of intermediate certificates
        CertStore intermediateCertStore = CertStore.getInstance("Collection",
                new CollectionCertStoreParameters(intermediateCerts), "BC");
        pkixParams.addCertStore(intermediateCertStore);

        // Build and verify the certification chain
        CertPathBuilder builder = CertPathBuilder.getInstance("PKIX", "BC");
        return (PKIXCertPathBuilderResult) builder.build(pkixParams);
    }

    /**
     * Checks whether given X.509 certificate is self-signed.
     *
     * @param cert cert to test for self signing.
     * @return true if self signed, otherwise false.
     * @throws CertificateException     certificate exception.
     * @throws NoSuchAlgorithmException not such algorithm exception.
     * @throws NoSuchProviderException no such provider exception.
     */
    public static boolean isSelfSigned(X509Certificate cert)
            throws CertificateException, NoSuchAlgorithmException,
            NoSuchProviderException {
        try {
            // Try to verify certificate signature with its own public key
            PublicKey key = cert.getPublicKey();
            cert.verify(key);
            return true;
        } catch (SignatureException | InvalidKeyException sigEx) {
            // Invalid signature, not self-signed
            return false;
        }
    }
}