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
package org.icepdf.core.pobjects.acroform.signature.certificates;

import org.bouncycastle.asn1.DERNull;
import org.bouncycastle.asn1.ocsp.OCSPObjectIdentifiers;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.AccessDescription;
import org.bouncycastle.asn1.x509.AuthorityInformationAccess;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.CRLDistPoint;
import org.bouncycastle.asn1.x509.DistributionPoint;
import org.bouncycastle.asn1.x509.DistributionPointName;
import org.bouncycastle.asn1.x509.ExtendedKeyUsage;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.KeyPurposeId;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.asn1.x509.X509ObjectIdentifiers;
import org.bouncycastle.cert.X509v2CRLBuilder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CRLConverter;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Security;
import java.security.cert.X509CRL;
import java.security.cert.X509Certificate;
import java.util.Date;

/**
 * Builds certificates and revocation lists for the tests, rather than committing any.
 * <p>
 * Everything here is generated in the test run, which keeps two things honest: a certificate that
 * expires cannot rot the suite, and the revocation tests can state which serial number is revoked
 * instead of hoping a checked-in list still says so.  Keys are 1024 bits, which is far too short
 * for anything real and is the point - these exist for a few milliseconds and the tests are about
 * revocation logic, not cryptographic strength.
 */
public final class CertificateFixtures {

    static {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    private static final String SIGNING_ALGORITHM = "SHA256withRSA";

    private CertificateFixtures() {
    }

    /**
     * A certificate authority: its certificate and the key it signs with.
     */
    public static final class Authority {
        private final X509Certificate certificate;
        private final PrivateKey privateKey;

        Authority(X509Certificate certificate, PrivateKey privateKey) {
            this.certificate = certificate;
            this.privateKey = privateKey;
        }

        public X509Certificate getCertificate() {
            return certificate;
        }

        public PrivateKey getPrivateKey() {
            return privateKey;
        }
    }

    private static KeyPair keyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(1024);
        return generator.generateKeyPair();
    }

    private static Date yesterday() {
        return new Date(System.currentTimeMillis() - 24L * 60 * 60 * 1000);
    }

    private static Date nextYear() {
        return new Date(System.currentTimeMillis() + 365L * 24 * 60 * 60 * 1000);
    }

    /**
     * A self-signed root certificate authority.
     *
     * @param commonName name to issue it under
     * @return the authority
     */
    public static Authority rootAuthority(String commonName) throws Exception {
        KeyPair keyPair = keyPair();
        X500Name name = new X500Name("CN=" + commonName);
        X509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                name, BigInteger.valueOf(System.nanoTime()), yesterday(), nextYear(),
                name, keyPair.getPublic());
        builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(true));

        ContentSigner signer = new JcaContentSignerBuilder(SIGNING_ALGORITHM)
                .build(keyPair.getPrivate());
        X509Certificate certificate = new JcaX509CertificateConverter()
                .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                .getCertificate(builder.build(signer));
        return new Authority(certificate, keyPair.getPrivate());
    }

    /**
     * A certificate issued by {@code issuer}, carrying no revocation extensions.
     *
     * @param commonName name to issue it under
     * @param issuer     authority that signs it
     * @return the certificate
     */
    public static X509Certificate issuedBy(String commonName, Authority issuer) throws Exception {
        return issuedBy(commonName, issuer, null, null, BigInteger.valueOf(System.nanoTime()));
    }

    /**
     * A certificate issued by {@code issuer}, optionally naming where its revocation list and its
     * OCSP responder live.
     *
     * @param commonName name to issue it under
     * @param issuer     authority that signs it
     * @param crlUrl     the /CRLDistributionPoints url, or null for none
     * @param ocspUrl    the authority information access OCSP url, or null for none
     * @param serial     serial number, which is what a revocation list names
     * @return the certificate
     */
    public static X509Certificate issuedBy(String commonName, Authority issuer, String crlUrl,
                                           String ocspUrl, BigInteger serial) throws Exception {
        KeyPair keyPair = keyPair();
        X509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                issuer.getCertificate(), serial, yesterday(), nextYear(),
                new X500Name("CN=" + commonName), keyPair.getPublic());
        builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(false));

        if (crlUrl != null) {
            DistributionPointName pointName = new DistributionPointName(new GeneralNames(
                    new GeneralName(GeneralName.uniformResourceIdentifier, crlUrl)));
            builder.addExtension(Extension.cRLDistributionPoints, false,
                    new CRLDistPoint(new DistributionPoint[]{
                            new DistributionPoint(pointName, null, null)}));
        }
        if (ocspUrl != null) {
            builder.addExtension(Extension.authorityInfoAccess, false,
                    new AuthorityInformationAccess(new AccessDescription(
                            X509ObjectIdentifiers.id_ad_ocsp,
                            new GeneralName(GeneralName.uniformResourceIdentifier, ocspUrl))));
        }

        ContentSigner signer = new JcaContentSignerBuilder(SIGNING_ALGORITHM)
                .build(issuer.getPrivateKey());
        return new JcaX509CertificateConverter()
                .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                .getCertificate(builder.build(signer));
    }

    /**
     * A certificate issued by {@code issuer} that is allowed to sign OCSP responses, which is what
     * the extended key usage of id-kp-OCSPSigning says.
     *
     * @param commonName name to issue it under
     * @param issuer     authority that signs it
     * @return the responder's certificate and its key
     */
    public static Authority ocspResponder(String commonName, Authority issuer) throws Exception {
        KeyPair keyPair = keyPair();
        X509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                issuer.getCertificate(), BigInteger.valueOf(System.nanoTime()),
                yesterday(), nextYear(), new X500Name("CN=" + commonName), keyPair.getPublic());
        builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(false));
        builder.addExtension(Extension.extendedKeyUsage, false,
                new ExtendedKeyUsage(KeyPurposeId.id_kp_OCSPSigning));
        // a responder trusted for the life of its own certificate needs no revocation check of its
        // own, which is what this extension says (RFC 6960 4.2.2.2.1)
        builder.addExtension(OCSPObjectIdentifiers.id_pkix_ocsp_nocheck, false,
                DERNull.INSTANCE);

        ContentSigner signer = new JcaContentSignerBuilder(SIGNING_ALGORITHM)
                .build(issuer.getPrivateKey());
        X509Certificate certificate = new JcaX509CertificateConverter()
                .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                .getCertificate(builder.build(signer));
        return new Authority(certificate, keyPair.getPrivate());
    }

    /**
     * A certificate issued by {@code issuer} that is allowed to sign timestamps.
     * <p>
     * RFC 3161 requires the timestamping purpose to be the only extended key usage and to be marked
     * critical, and BouncyCastle refuses to build a token with a certificate that says otherwise.
     *
     * @param commonName name to issue it under
     * @param issuer     authority that signs it
     * @return the authority's certificate and its key
     */
    public static Authority timeStampAuthority(String commonName, Authority issuer)
            throws Exception {
        KeyPair keyPair = keyPair();
        X509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                issuer.getCertificate(), BigInteger.valueOf(System.nanoTime()),
                yesterday(), nextYear(), new X500Name("CN=" + commonName), keyPair.getPublic());
        builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(false));
        builder.addExtension(Extension.extendedKeyUsage, true,
                new ExtendedKeyUsage(KeyPurposeId.id_kp_timeStamping));

        ContentSigner signer = new JcaContentSignerBuilder(SIGNING_ALGORITHM)
                .build(issuer.getPrivateKey());
        X509Certificate certificate = new JcaX509CertificateConverter()
                .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                .getCertificate(builder.build(signer));
        return new Authority(certificate, keyPair.getPrivate());
    }

    /**
     * A revocation list from {@code issuer} naming the given serial numbers as revoked.
     *
     * @param issuer          authority that signs the list
     * @param revokedSerials  serial numbers to list as revoked
     * @return the list
     */
    public static X509CRL revocationList(Authority issuer, BigInteger... revokedSerials)
            throws Exception {
        X509v2CRLBuilder builder = new X509v2CRLBuilder(
                new X500Name(issuer.getCertificate().getSubjectX500Principal().getName()),
                new Date());
        builder.setNextUpdate(nextYear());
        for (BigInteger serial : revokedSerials) {
            // 0 is "unspecified", which is what a list says when it gives no reason
            builder.addCRLEntry(serial, yesterday(), 0);
        }
        ContentSigner signer = new JcaContentSignerBuilder(SIGNING_ALGORITHM)
                .build(issuer.getPrivateKey());
        return new JcaX509CRLConverter()
                .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                .getCRL(builder.build(signer));
    }
}
