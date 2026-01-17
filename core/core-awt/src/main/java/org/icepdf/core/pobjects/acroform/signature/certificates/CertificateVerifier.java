/*
 * Copyright 2006-2019 ICEsoft Technologies Canada Corp.
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

import org.bouncycastle.asn1.*;
import org.bouncycastle.asn1.x509.X509ObjectIdentifiers;
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils;
import org.bouncycastle.cert.ocsp.OCSPException;
import org.bouncycastle.jce.exception.ExtCertPathValidatorException;
import org.icepdf.core.pobjects.acroform.signature.exceptions.CertificateVerificationException;
import org.icepdf.core.pobjects.acroform.signature.exceptions.RevokedCertificateException;
import org.icepdf.core.pobjects.acroform.signature.exceptions.SelfSignedVerificationException;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.GeneralSecurityException;
import java.security.cert.*;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Class for building a certification chain for given certificate and verifying
 * it. Relies on a set of root CA certificates and intermediate certificates
 * that will be used for building the certification chain. The verification
 * process assumes that all self-signed certificates in the set are trusted
 * root CA certificates and all other certificates in the set are intermediate
 * certificates.
 *
 * @author Svetlin Nakov
 * <p>
 * Further updates from copied from
 * <a href="https://svn.apache.org/repos/asf/cxf/tags/cxf-2.4.9/distribution/src/main/release/samples/sts_issue_operation/src/main/java/demo/sts/provider/cert/">Apache CXF 2.4.9</a>
 */
public class CertificateVerifier {

    private static final Logger logger =
            Logger.getLogger(CertificateVerifier.class.toString());

    /**
     * Attempts to build a certification chain for given certificate and to verify
     * it. Relies on a set of root CA certificates and intermediate certificates
     * that will be used for building the certification chain. The verification
     * process assumes that all self-signed certificates in the set are trusted
     * root CA certificates and all other certificates in the set are intermediate
     * certificates.
     *
     * @param signerCert      signer certificate
     * @param additionalCerts - set of trusted root CA certificates that will be
     *                        used as "trust anchors" and intermediate CA certificates that will be
     *                        used as part of the certification chain. All self-signed certificates
     *                        are considered to be trusted root CA certificates. All the rest are
     *                        considered to be intermediate CA certificates.
     * @param signatureDate   date from signature dictionary.
     * @return the certification chain (if verification is successful)
     * @throws CertificateVerificationException - if the certification is not
     *                                          successful (e.g. certification path cannot be built or some
     *                                          certificate in the chain is expired or CRL checks are failed)
     * @throws CertificateVerificationException could not verify cert.
     * @throws CertificateExpiredException      cert is expired
     */
    public static PKIXCertPathBuilderResult verifyCertificate(X509Certificate signerCert,
                                                              Collection<X509Certificate> additionalCerts,
                                                              boolean verifySelfSignedCert,
                                                              Date signatureDate)
            throws CertificateVerificationException, GeneralSecurityException, SelfSignedVerificationException,
            RevokedCertificateException, OCSPException, IOException, URISyntaxException {
        try {
            // Check for self-signed root certificate
            if (verifySelfSignedCert && CertificateUtils.isSelfSigned(signerCert)) {
                throw new SelfSignedVerificationException("The certificate is self-signed.");
            }

            Set<X509Certificate> certSet = new HashSet<>(additionalCerts);

            // download missing intermediate certificates
            Set<X509Certificate> certsToTrySet = new HashSet<>();
            certsToTrySet.add(signerCert);
            certsToTrySet.addAll(additionalCerts);
            while (!certsToTrySet.isEmpty()) {
                Set<X509Certificate> nextCertsToTrySet = new HashSet<>();
                for (X509Certificate tryCert : certsToTrySet) {
                    Set<X509Certificate> downloadedExtraCertificatesSet =
                            CertificateVerifier.downloadExtraCertificates(tryCert);
                    for (X509Certificate downloadedCertificate : downloadedExtraCertificatesSet) {
                        if (!certSet.contains(downloadedCertificate)) {
                            nextCertsToTrySet.add(downloadedCertificate);
                            certSet.add(downloadedCertificate);
                        }
                    }
                }
                certsToTrySet = nextCertsToTrySet;
            }

            // Prepare a set of trusted root CA certificates
            // and a set of intermediate certificates
            Set<X509Certificate> trustedRootCerts = new HashSet<>();
            Set<X509Certificate> intermediateCerts = new HashSet<>();
            for (X509Certificate additionalCert : certSet) {
                if (CertificateUtils.isSelfSigned(additionalCert)) {
                    trustedRootCerts.add(additionalCert);
                } else {
                    intermediateCerts.add(additionalCert);
                }
            }
            if (trustedRootCerts.isEmpty()) {
                throw new CertificateVerificationException("No root certificate in the chain");
            }

            // Attempt to build the certification chain and verify it, first element should always be the signer cert.
            PKIXCertPathBuilderResult verifiedCertChain =
                    verifyCertificate(signerCert, trustedRootCerts, intermediateCerts);

            // Check whether the certificate is revoked by the CRL given in its CRL distribution point extension
            RevocationsVerifier.verifyRevocations(signerCert, certSet, signatureDate);

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
    private static PKIXCertPathBuilderResult verifyCertificate(X509Certificate cert,
                                                               Set<X509Certificate> trustedRootCerts,
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
        PKIXBuilderParameters pkixParams = new PKIXBuilderParameters(trustAnchors, selector);

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
     * Download extra certificates from the URI mentioned in id-ad-caIssuers in the "authority
     * information access" extension.
     *
     * @param x509Extension an X509 object that can have extensions.
     * @return a certificate set, never null.
     */
    public static Set<X509Certificate> downloadExtraCertificates(X509Extension x509Extension) {
        Set<X509Certificate> resultSet = new HashSet<>();
        byte[] authorityExtensionValue =
                x509Extension.getExtensionValue(org.bouncycastle.asn1.x509.Extension.authorityInfoAccess.getId());
        if (authorityExtensionValue == null) {
            return resultSet;
        }
        ASN1Primitive asn1Prim;
        try {
            asn1Prim = JcaX509ExtensionUtils.parseExtensionValue(authorityExtensionValue);
        } catch (IOException ex) {
            logger.log(Level.WARNING, ex.getMessage(), ex);
            return resultSet;
        }
        if (!(asn1Prim instanceof ASN1Sequence)) {
            logger.log(Level.WARNING, () -> "ASN1Sequence not found " + asn1Prim.getClass().getSimpleName());
            return resultSet;
        }
        ASN1Sequence asn1Seq = (ASN1Sequence) asn1Prim;
        Enumeration<?> objects = asn1Seq.getObjects();
        while (objects.hasMoreElements()) {
            // AccessDescription
            ASN1Sequence obj = (ASN1Sequence) objects.nextElement();
            ASN1Encodable oid = obj.getObjectAt(0);
            if (!X509ObjectIdentifiers.id_ad_caIssuers.equals(oid)) {
                continue;
            }
            ASN1TaggedObject location = (ASN1TaggedObject) obj.getObjectAt(1);
            ASN1OctetString uri = (ASN1OctetString) location.getBaseObject();
            String urlString = new String(uri.getOctets());
            logger.log(Level.FINE, () -> "CA issuers URL: " + urlString);
            try (InputStream in = openURL(urlString)) {
                CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
                Collection<? extends Certificate> altCerts = certFactory.generateCertificates(in);
                altCerts.forEach(altCert -> resultSet.add((X509Certificate) altCert));
                logger.log(Level.FINE, () -> "CA issuers URL: " + altCerts.size() + "certificate(s) downloaded");
            } catch (IOException | URISyntaxException ex) {
                logger.log(Level.WARNING, urlString + " failure: " + ex.getMessage(), ex);
            } catch (CertificateException ex) {
                logger.log(Level.WARNING, ex.getMessage(), ex);
            }
        }
        return resultSet;
    }

    /**
     * Open a URL connection, following redirections from http to https
     *
     * @param urlString url string
     * @author Tilman Hausherr from PDFBox SigUtils
     */
    private static InputStream openURL(String urlString) throws IOException, URISyntaxException {
        URL url = new URI(urlString).toURL();
        if (!urlString.startsWith("http")) {
            // so that ftp is still supported
            return url.openStream();
        }
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        int responseCode = con.getResponseCode();
        logger.log(Level.FINER, "URL response: " + responseCode + " " + con.getResponseMessage());
        if (responseCode == HttpURLConnection.HTTP_MOVED_TEMP ||
                responseCode == HttpURLConnection.HTTP_MOVED_PERM ||
                responseCode == HttpURLConnection.HTTP_SEE_OTHER) {
            String location = con.getHeaderField("Location");
            if (urlString.startsWith("http://") &&
                    location.startsWith("https://") &&
                    urlString.substring(7).equals(location.substring(8))) {
                // redirection from http:// to https://
                // change this code if you want to be more flexible (but think about security!)
                logger.log(Level.FINER, () -> "redirection to " + location + " followed");
                con.disconnect();
                con = (HttpURLConnection) new URI(location).toURL().openConnection();
            } else {
                logger.log(Level.FINER, () -> "redirection to " + location + " ignored");
            }
        }
        return con.getInputStream();
    }
}