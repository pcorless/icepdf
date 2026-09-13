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

import org.bouncycastle.cert.ocsp.OCSPException;
import org.bouncycastle.cert.ocsp.OCSPRespBuilder;
import org.icepdf.core.pobjects.acroform.signature.exceptions.RevokedCertificateException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.Date;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests checking a certificate's revocation against an OCSP responder.
 * <p>
 * OCSP is asked a live question and given a signed answer, so there are two things to get right:
 * reading the answer, and refusing one that cannot be trusted.  The second is the harder half and
 * the one worth the effort of a real responder - an answer signed by the wrong key, or echoing the
 * wrong nonce, or stale, has to be rejected rather than believed, and a verifier that believes
 * everything passes every test that only ever feeds it a valid response.
 * <p>
 * The responder here signs genuine responses with BouncyCastle and serves them over http, and can
 * be told to answer wrongly on purpose.  The requests it answers are the ones the code under test
 * built, which is what makes the nonce checks mean anything.
 */
public class OcspCheckingTest {

    /**
     * Runs {@code work} against a responder signed by an authority that also issued the
     * certificate being checked.
     */
    private interface ResponderWork {
        void run(OcspResponder responder, CertificateFixtures.Authority root,
                 X509Certificate subject) throws Exception;
    }

    /**
     * Sets up a root, an OCSP responder it trusts, and a certificate naming that responder.
     */
    private void withResponder(ResponderWork work) throws Exception {
        CertificateFixtures.Authority root = CertificateFixtures.rootAuthority("Test Root");
        CertificateFixtures.Authority responderAuthority =
                CertificateFixtures.ocspResponder("Test Responder", root);
        try (OcspResponder responder = new OcspResponder(responderAuthority)) {
            X509Certificate subject = CertificateFixtures.issuedBy("Test Leaf", root,
                    null, responder.getUrl(), BigInteger.valueOf(2001));
            work.run(responder, root, subject);
        }
    }

    /**
     * Asks the responder about {@code subject}, as the signature validator would.
     *
     * @param signDate when the signature was made, which decides whether a later revocation counts
     */
    private static void check(OcspResponder responder, CertificateFixtures.Authority root,
                              X509Certificate subject, Date signDate) throws Exception {
        Set<X509Certificate> chain = Collections.singleton(root.getCertificate());
        OcspHelper helper = new OcspHelper(subject, signDate, root.getCertificate(), chain,
                responder.getUrl());
        OCSPVerifier.verifyOCSP(helper, chain);
    }

    // ------------------------------------------------------------------
    // the answers a responder can give
    // ------------------------------------------------------------------

    @DisplayName("a certificate in good standing passes")
    @Test
    public void goodStanding() throws Exception {
        withResponder((responder, root, subject) -> {
            responder.answering(OcspResponder.Answer.GOOD);
            check(responder, root, subject, new Date());
        });
    }

    @DisplayName("a revoked certificate is reported as revoked, with the date it was revoked")
    @Test
    public void revoked() throws Exception {
        withResponder((responder, root, subject) -> {
            Date revokedOn = new Date(System.currentTimeMillis() - 48L * 60 * 60 * 1000);
            responder.revokedOn(revokedOn);

            RevokedCertificateException thrown = assertThrows(RevokedCertificateException.class,
                    () -> check(responder, root, subject, new Date()));
            assertNotNull(thrown.getRevocationTime(),
                    "the caller needs the date to say when the certificate stopped being good");
        });
    }

    @DisplayName("a certificate revoked after it signed is still good for that signature")
    @Test
    public void revokedAfterSigning() throws Exception {
        // The point of recording a revocation date rather than a flag: a signature made while the
        // certificate was valid stays valid afterwards.  Treating any revocation as fatal would
        // invalidate every signature whose certificate was later retired.
        withResponder((responder, root, subject) -> {
            Date signedOn = new Date(System.currentTimeMillis() - 72L * 60 * 60 * 1000);
            Date revokedOn = new Date(System.currentTimeMillis() - 24L * 60 * 60 * 1000);
            responder.revokedOn(revokedOn);

            check(responder, root, subject, signedOn);
        });
    }

    @DisplayName("a responder that does not know the certificate is not taken as approval")
    @Test
    public void unknownCertificate() throws Exception {
        // "I have never heard of it" is not "it is fine".
        withResponder((responder, root, subject) -> {
            responder.answering(OcspResponder.Answer.UNKNOWN);
            assertThrows(OCSPException.class, () -> check(responder, root, subject, new Date()));
        });
    }

    // ------------------------------------------------------------------
    // answers that cannot be trusted
    // ------------------------------------------------------------------

    @DisplayName("a response echoing the wrong nonce is refused")
    @Test
    public void wrongNonce() throws Exception {
        // The nonce ties an answer to the question that was asked.  Accepting one that does not
        // match lets an old "good" answer be replayed for a certificate revoked since, which is
        // the attack the nonce exists to stop.
        withResponder((responder, root, subject) -> {
            responder.withWrongNonce();
            assertThrows(OCSPException.class, () -> check(responder, root, subject, new Date()));
        });
    }

    @DisplayName("a response signed by someone other than the responder is refused")
    @Test
    public void wrongSigner() throws Exception {
        // Anyone can serve bytes at a url; the signature is the only thing that makes the answer
        // the authority's rather than the network's.
        CertificateFixtures.Authority root = CertificateFixtures.rootAuthority("Test Root");
        CertificateFixtures.Authority impostor = CertificateFixtures.rootAuthority("Impostor");
        CertificateFixtures.Authority responderAuthority =
                CertificateFixtures.ocspResponder("Test Responder", root);

        try (OcspResponder responder = new OcspResponder(responderAuthority)) {
            responder.signedBy(impostor);
            X509Certificate subject = CertificateFixtures.issuedBy("Test Leaf", root,
                    null, responder.getUrl(), BigInteger.valueOf(2002));
            assertThrows(Exception.class, () -> check(responder, root, subject, new Date()));
        }
    }

    @DisplayName("a stale response is refused when there is no nonce to vouch for it")
    @Test
    public void staleResponseWithoutANonce() throws Exception {
        // Without a nonce the only thing tying the answer to now is its update window, so the
        // window has to be enforced; otherwise a cached answer never expires.
        withResponder((responder, root, subject) -> {
            long hour = 60L * 60 * 1000;
            responder.withoutNonce().withValidity(
                    new Date(System.currentTimeMillis() - 48 * hour),
                    new Date(System.currentTimeMillis() - 24 * hour));
            assertThrows(OCSPException.class, () -> check(responder, root, subject, new Date()));
        });
    }

    @DisplayName("a response from the future is refused too")
    @Test
    public void futureResponseWithoutANonce() throws Exception {
        withResponder((responder, root, subject) -> {
            long hour = 60L * 60 * 1000;
            responder.withoutNonce().withValidity(
                    new Date(System.currentTimeMillis() + 24 * hour),
                    new Date(System.currentTimeMillis() + 48 * hour));
            assertThrows(OCSPException.class, () -> check(responder, root, subject, new Date()));
        });
    }

    @DisplayName("a fresh response with no nonce is accepted on its update window")
    @Test
    public void freshResponseWithoutANonce() throws Exception {
        // RFC 5019: a client that sent a nonce should not reject a response for lacking one, but
        // must then fall back to checking the time.  This is the accepting half of that rule, and
        // without it the two tests above would pass against a verifier that rejected everything.
        withResponder((responder, root, subject) -> {
            responder.withoutNonce();
            check(responder, root, subject, new Date());
        });
    }

    // ------------------------------------------------------------------
    // responders that will not answer
    // ------------------------------------------------------------------

    @DisplayName("a responder that refuses to answer is not taken as approval")
    @Test
    public void unsuccessfulResponseStatus() throws Exception {
        // Each of these is a responder declining to say anything about the certificate, and none
        // of them means the certificate is good.
        int[] statuses = {
                OCSPRespBuilder.MALFORMED_REQUEST,
                OCSPRespBuilder.INTERNAL_ERROR,
                OCSPRespBuilder.TRY_LATER,
                OCSPRespBuilder.SIG_REQUIRED,
                OCSPRespBuilder.UNAUTHORIZED,
        };
        for (int status : statuses) {
            withResponder((responder, root, subject) -> {
                responder.withResponseStatus(status);
                assertThrows(Exception.class, () -> check(responder, root, subject, new Date()),
                        "response status " + status + " should not pass");
            });
        }
    }

    @DisplayName("a responder that cannot be reached is not taken as approval")
    @Test
    public void unreachableResponder() throws Exception {
        CertificateFixtures.Authority root = CertificateFixtures.rootAuthority("Test Root");
        X509Certificate subject = CertificateFixtures.issuedBy("Test Leaf", root,
                null, "http://127.0.0.1:1/ocsp", BigInteger.valueOf(2003));
        Set<X509Certificate> chain = Collections.singleton(root.getCertificate());

        OcspHelper helper = new OcspHelper(subject, new Date(), root.getCertificate(), chain,
                "http://127.0.0.1:1/ocsp");
        assertThrows(Exception.class, () -> OCSPVerifier.verifyOCSP(helper, chain));
    }

    // ------------------------------------------------------------------
    // the responder's own certificate
    // ------------------------------------------------------------------

    @DisplayName("a responder certificate allowed to sign OCSP responses is accepted")
    @Test
    public void responderCertificateUsage() throws Exception {
        CertificateFixtures.Authority root = CertificateFixtures.rootAuthority("Test Root");
        CertificateFixtures.Authority responderAuthority =
                CertificateFixtures.ocspResponder("Test Responder", root);
        OcspHelper.checkResponderCertificateUsage(responderAuthority.getCertificate());
    }

    @DisplayName("the responder that answered is the one whose certificate is checked")
    @Test
    public void responderCertificateIsFound() throws Exception {
        // The response names its signer, and that name is what the client resolves to a
        // certificate before checking the signature against it.
        withResponder((responder, root, subject) -> {
            Set<X509Certificate> chain = Collections.singleton(root.getCertificate());
            OcspHelper helper = new OcspHelper(subject, new Date(), root.getCertificate(), chain,
                    responder.getUrl());
            helper.getResponseOcsp();

            assertNotNull(helper.getOcspResponderCertificate(),
                    "the responder's certificate should have been found in the response");
            assertEquals("CN=Test Responder",
                    helper.getOcspResponderCertificate().getSubjectX500Principal().getName());
            assertTrue(helper.getCertificateToCheck().equals(subject));
        });
    }
}
