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

import org.icepdf.core.pobjects.acroform.signature.exceptions.CertificateVerificationException;
import org.icepdf.core.pobjects.acroform.signature.exceptions.RevokedCertificateException;
import org.icepdf.core.pobjects.acroform.signature.exceptions.SelfSignedVerificationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.security.cert.X509CRL;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the parts of revocation checking that decide an answer, as opposed to the parts that fetch
 * one over the network.
 * <p>
 * Revocation is how a signature stops being trustworthy after the fact: the certificate was valid
 * when it signed, and the authority has since withdrawn it.  Everything here is about telling three
 * outcomes apart - not revoked, revoked, and could not tell - because the last two look the same to
 * a reader that lumps them together, and they mean very different things.  "Could not reach the
 * revocation list" is a reason to warn; "the authority has revoked this certificate" is a reason to
 * refuse.
 * <p>
 * Certificates and revocation lists are generated per test, so the tests can name the serial number
 * that is revoked rather than relying on a checked-in list still saying so.
 */
public class RevocationCheckingTest {

    // ------------------------------------------------------------------
    // self-signed certificates
    // ------------------------------------------------------------------

    @DisplayName("a self-signed certificate is recognised as one")
    @Test
    public void selfSigned() throws Exception {
        // A root signs itself, which is what makes it a root; anything issued by another is not.
        CertificateFixtures.Authority root = CertificateFixtures.rootAuthority("Test Root");
        assertTrue(CertificateUtils.isSelfSigned(root.getCertificate()));

        X509Certificate leaf = CertificateFixtures.issuedBy("Test Leaf", root);
        assertFalse(CertificateUtils.isSelfSigned(leaf));
    }

    @DisplayName("a self-signed certificate has nothing to revoke it")
    @Test
    public void revocationOfARoot() throws Exception {
        // A root is its own authority, so there is no one above it to withdraw it; the check has
        // to stop rather than look for a list that cannot exist.
        CertificateFixtures.Authority root = CertificateFixtures.rootAuthority("Test Root");
        RevocationsVerifier.verifyRevocations(root.getCertificate(),
                Collections.singleton(root.getCertificate()), new Date());
    }

    @DisplayName("verifying a self-signed certificate says so when asked to")
    @Test
    public void selfSignedVerificationIsRefused() throws Exception {
        CertificateFixtures.Authority root = CertificateFixtures.rootAuthority("Test Root");
        assertThrows(SelfSignedVerificationException.class, () ->
                CertificateVerifier.verifyCertificate(root.getCertificate(),
                        Collections.singleton(root.getCertificate()), true, new Date()));
    }

    // ------------------------------------------------------------------
    // finding where the revocation information lives
    // ------------------------------------------------------------------

    @DisplayName("the revocation list url is read out of the certificate")
    @Test
    public void crlDistributionPoints() throws Exception {
        CertificateFixtures.Authority root = CertificateFixtures.rootAuthority("Test Root");
        X509Certificate leaf = CertificateFixtures.issuedBy("Test Leaf", root,
                "http://example.com/test.crl", null, BigInteger.ONE);

        List<String> points = CRLVerifier.getCrlDistributionPoints(leaf);
        assertEquals(1, points.size());
        assertEquals("http://example.com/test.crl", points.get(0));
    }

    @DisplayName("a certificate naming no revocation list has none to name")
    @Test
    public void noCrlDistributionPoints() throws Exception {
        // An empty list, not null: the caller iterates it.
        CertificateFixtures.Authority root = CertificateFixtures.rootAuthority("Test Root");
        List<String> points = CRLVerifier.getCrlDistributionPoints(
                CertificateFixtures.issuedBy("Test Leaf", root));
        assertNotNull(points);
        assertTrue(points.isEmpty());
    }

    @DisplayName("the OCSP responder url is read out of the certificate")
    @Test
    public void ocspUrl() throws Exception {
        CertificateFixtures.Authority root = CertificateFixtures.rootAuthority("Test Root");
        X509Certificate leaf = CertificateFixtures.issuedBy("Test Leaf", root,
                null, "http://example.com/ocsp", BigInteger.ONE);
        assertEquals("http://example.com/ocsp", CertificateUtils.extractOCSPURL(leaf));
    }

    @DisplayName("a certificate naming no OCSP responder has none to name")
    @Test
    public void noOcspUrl() throws Exception {
        // Which is what sends the check to the revocation list instead.
        CertificateFixtures.Authority root = CertificateFixtures.rootAuthority("Test Root");
        assertNull(CertificateUtils.extractOCSPURL(
                CertificateFixtures.issuedBy("Test Leaf", root)));
    }

    // ------------------------------------------------------------------
    // reading a revocation list
    // ------------------------------------------------------------------

    @DisplayName("a revocation list names the certificates it revokes, and only those")
    @Test
    public void revocationListContents() throws Exception {
        // The fixture's own check: everything below rests on a list that really does revoke one
        // certificate and really does not revoke the other.
        CertificateFixtures.Authority root = CertificateFixtures.rootAuthority("Test Root");
        X509Certificate revoked = CertificateFixtures.issuedBy("Revoked", root,
                null, null, BigInteger.valueOf(1001));
        X509Certificate good = CertificateFixtures.issuedBy("Good", root,
                null, null, BigInteger.valueOf(1002));

        X509CRL crl = CertificateFixtures.revocationList(root, BigInteger.valueOf(1001));
        assertTrue(crl.isRevoked(revoked), "the list should revoke the certificate it names");
        assertFalse(crl.isRevoked(good), "and no other");
    }

    // ------------------------------------------------------------------
    // checking against a revocation list that can actually be fetched
    // ------------------------------------------------------------------

    /**
     * Serves one revocation list over http for the life of the call, so the verifier can fetch it
     * the way it fetches a real one.  A local server rather than a mock: the fetching and the
     * parsing are what is being exercised, not just the decision at the end.
     */
    private static void servingCrl(X509CRL crl, java.util.function.Consumer<String> work)
            throws Exception {
        byte[] encoded = crl.getEncoded();
        com.sun.net.httpserver.HttpServer server =
                com.sun.net.httpserver.HttpServer.create(
                        new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/test.crl", exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "application/pkix-crl");
            exchange.sendResponseHeaders(200, encoded.length);
            exchange.getResponseBody().write(encoded);
            exchange.close();
        });
        server.start();
        try {
            work.accept("http://127.0.0.1:" + server.getAddress().getPort() + "/test.crl");
        } finally {
            server.stop(0);
        }
    }

    @DisplayName("a certificate the list does not name passes the revocation check")
    @Test
    public void notRevoked() throws Exception {
        CertificateFixtures.Authority root = CertificateFixtures.rootAuthority("Test Root");
        X509CRL crl = CertificateFixtures.revocationList(root, BigInteger.valueOf(9999));

        servingCrl(crl, url -> {
            try {
                X509Certificate good = CertificateFixtures.issuedBy("Good", root, url, null,
                        BigInteger.valueOf(1002));
                CRLVerifier.verifyCRL(good);
            } catch (Exception e) {
                throw new AssertionError("a certificate that is not revoked should pass", e);
            }
        });
    }

    @DisplayName("a revoked certificate is reported as revoked, not as one that could not be checked")
    @Test
    public void revokedIsReportedAsRevoked() throws Exception {
        // The distinction this whole class is about.  A reader shows "could not check revocation"
        // as a warning and "revoked" as a refusal, and the validator keeps them in separate flags:
        // RevokedCertificateException sets isRevocation, while CertificateVerificationException
        // only clears the chain-trusted flag.  A revocation reported as the latter is a withdrawn
        // certificate presented to the user as merely untrusted.
        CertificateFixtures.Authority root = CertificateFixtures.rootAuthority("Test Root");
        X509CRL crl = CertificateFixtures.revocationList(root, BigInteger.valueOf(1001));

        servingCrl(crl, url -> {
            X509Certificate revoked;
            try {
                revoked = CertificateFixtures.issuedBy("Revoked", root, url, null,
                        BigInteger.valueOf(1001));
            } catch (Exception e) {
                throw new AssertionError(e);
            }
            RevokedCertificateException thrown = assertThrows(RevokedCertificateException.class,
                    () -> CRLVerifier.verifyCRL(revoked),
                    "a certificate named by the revocation list has to be reported as revoked");
            assertNotNull(thrown.getMessage());
        });
    }

    @DisplayName("a revocation list that cannot be fetched is reported as unchecked, not as revoked")
    @Test
    public void unreachableListIsNotARevocation() throws Exception {
        // The other side of the same distinction: failing to reach the list says nothing about
        // whether the certificate is good, and must not be reported as though it did.
        CertificateFixtures.Authority root = CertificateFixtures.rootAuthority("Test Root");
        X509Certificate leaf = CertificateFixtures.issuedBy("Unreachable", root,
                "http://127.0.0.1:1/nothing-here.crl", null, BigInteger.valueOf(1003));

        assertThrows(CertificateVerificationException.class, () -> CRLVerifier.verifyCRL(leaf));
    }

    // ------------------------------------------------------------------
    // building a chain
    // ------------------------------------------------------------------

    @DisplayName("a certificate chaining to a trusted root verifies")
    @Test
    public void chainToATrustedRoot() throws Exception {
        // No revocation extensions on the leaf, so nothing here goes to the network.
        CertificateFixtures.Authority root = CertificateFixtures.rootAuthority("Test Root");
        X509Certificate leaf = CertificateFixtures.issuedBy("Test Leaf", root);

        Set<X509Certificate> chain = new HashSet<>();
        chain.add(root.getCertificate());
        chain.add(leaf);

        assertNotNull(CertificateVerifier.verifyCertificate(leaf, chain, false, new Date()));
    }

    @DisplayName("a chain with no root in it cannot be verified")
    @Test
    public void chainWithoutARoot() throws Exception {
        // Without a self-signed certificate to anchor it there is nothing to trust, and saying so
        // is the right answer rather than trusting the chain on its own say-so.
        CertificateFixtures.Authority root = CertificateFixtures.rootAuthority("Test Root");
        X509Certificate leaf = CertificateFixtures.issuedBy("Test Leaf", root);

        assertThrows(Exception.class, () ->
                CertificateVerifier.verifyCertificate(leaf, Collections.singleton(leaf), false,
                        new Date()));
    }

    @DisplayName("a certificate signed by an authority that did not issue it does not verify")
    @Test
    public void chainToTheWrongRoot() throws Exception {
        CertificateFixtures.Authority realRoot = CertificateFixtures.rootAuthority("Real Root");
        CertificateFixtures.Authority otherRoot = CertificateFixtures.rootAuthority("Other Root");
        X509Certificate leaf = CertificateFixtures.issuedBy("Test Leaf", realRoot);

        Set<X509Certificate> chain = new HashSet<>();
        chain.add(otherRoot.getCertificate());
        chain.add(leaf);

        assertThrows(Exception.class, () ->
                CertificateVerifier.verifyCertificate(leaf, chain, false, new Date()));
    }
}
