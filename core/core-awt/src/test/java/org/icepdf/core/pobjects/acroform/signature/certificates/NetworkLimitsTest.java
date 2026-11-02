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

import org.icepdf.core.pobjects.acroform.signature.SignatureValidationOptions;
import org.icepdf.core.pobjects.acroform.signature.exceptions.RevokedCertificateException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * GH-643: revocation checking must not hang on a server that accepts a connection and never
 * answers, must not touch the network offline, and must report a status it couldn't fetch as
 * unchecked rather than as a broken chain.
 */
public class NetworkLimitsTest {

    private static final SignatureValidationOptions FAST =
            SignatureValidationOptions.defaults().withOnline(true).withTimeouts(500, 500);

    /**
     * A server that accepts every connection, reads nothing, writes nothing and holds it open:
     * the case a connect timeout alone doesn't cover.
     */
    private static final class SilentServer implements AutoCloseable {
        private final ServerSocket server;
        private final List<Socket> held = Collections.synchronizedList(new ArrayList<>());
        private final AtomicInteger accepted = new AtomicInteger();

        SilentServer() throws IOException {
            server = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
            Thread acceptor = new Thread(() -> {
                while (!server.isClosed()) {
                    try {
                        held.add(server.accept());
                        accepted.incrementAndGet();
                    } catch (IOException e) {
                        return;
                    }
                }
            }, "silent-server");
            acceptor.setDaemon(true);
            acceptor.start();
        }

        String url(String path) {
            return "http://127.0.0.1:" + server.getLocalPort() + path;
        }

        int accepted() {
            return accepted.get();
        }

        @Override
        public void close() throws IOException {
            server.close();
            synchronized (held) {
                for (Socket socket : held) socket.close();
            }
        }
    }

    @DisplayName("a revocation list server that never answers gives up within the read timeout")
    @Test
    public void silentCrlServer() throws Exception {
        CertificateFixtures.Authority root = CertificateFixtures.rootAuthority("Test Root");
        try (SilentServer server = new SilentServer()) {
            X509Certificate leaf = CertificateFixtures.issuedBy("Test Leaf", root,
                    server.url("/test.crl"), null, BigInteger.valueOf(3001));
            RevocationStatus status = new RevocationStatus();
            assertTimeoutPreemptively(Duration.ofSeconds(10),
                    () -> CRLVerifier.verifyCRL(leaf, FAST, status));
            assertFalse(status.isComplete(), "an unanswered list is unchecked");
            assertEquals(1, server.accepted());
        }
    }

    @DisplayName("an OCSP responder and revocation list that never answer leave the chain unchecked, not failed")
    @Test
    public void silentOcspThenCrl() throws Exception {
        CertificateFixtures.Authority root = CertificateFixtures.rootAuthority("Test Root");
        try (SilentServer server = new SilentServer()) {
            X509Certificate leaf = CertificateFixtures.issuedBy("Test Leaf", root,
                    server.url("/test.crl"), server.url("/ocsp"), BigInteger.valueOf(3002));
            Set<X509Certificate> chain = Collections.singleton(root.getCertificate());
            RevocationStatus status = new RevocationStatus();
            // OCSP times out, falls back to the list, which times out too.
            assertTimeoutPreemptively(Duration.ofSeconds(10),
                    () -> RevocationsVerifier.verifyRevocations(leaf, chain, new Date(), FAST, status));
            assertFalse(status.isComplete());
            assertEquals(2, server.accepted(), "asked the responder, then the list");
        }
    }

    @DisplayName("the whole chain check against silent servers finishes and trusts the chain")
    @Test
    public void silentServersWholeChain() throws Exception {
        CertificateFixtures.Authority root = CertificateFixtures.rootAuthority("Test Root");
        try (SilentServer server = new SilentServer()) {
            X509Certificate leaf = CertificateFixtures.issuedBy("Test Leaf", root,
                    server.url("/test.crl"), server.url("/ocsp"), BigInteger.valueOf(3003));
            RevocationStatus status = new RevocationStatus();
            assertTimeoutPreemptively(Duration.ofSeconds(10),
                    () -> CertificateVerifier.verifyCertificate(leaf,
                            Collections.singleton(root.getCertificate()), false, new Date(), FAST, status));
            assertFalse(status.isComplete());
        }
    }

    @DisplayName("offline, nothing is fetched and the certificate is unchecked")
    @Test
    public void offline() throws Exception {
        CertificateFixtures.Authority root = CertificateFixtures.rootAuthority("Test Root");
        try (SilentServer server = new SilentServer()) {
            X509Certificate leaf = CertificateFixtures.issuedBy("Test Leaf", root,
                    server.url("/test.crl"), server.url("/ocsp"), BigInteger.valueOf(3004));
            RevocationStatus status = new RevocationStatus();
            CertificateVerifier.verifyCertificate(leaf, Collections.singleton(root.getCertificate()), false,
                    new Date(), FAST.withOnline(false), status);
            assertFalse(status.isComplete());
            assertEquals(0, server.accepted(), "offline must not connect");
        }
    }

    @DisplayName("a certificate naming no OCSP or CRL url is unchecked")
    @Test
    public void nowhereToAsk() throws Exception {
        CertificateFixtures.Authority root = CertificateFixtures.rootAuthority("Test Root");
        X509Certificate leaf = CertificateFixtures.issuedBy("Test Leaf", root);
        RevocationStatus status = new RevocationStatus();
        RevocationsVerifier.verifyRevocations(leaf, Collections.singleton(root.getCertificate()), new Date(),
                FAST, status);
        assertFalse(status.isComplete());
    }

    @DisplayName("a reachable list that doesn't name the certificate completes the check")
    @Test
    public void reachableList() throws Exception {
        CertificateFixtures.Authority root = CertificateFixtures.rootAuthority("Test Root");
        byte[] crl = CertificateFixtures.revocationList(root, BigInteger.valueOf(9999)).getEncoded();
        com.sun.net.httpserver.HttpServer server = com.sun.net.httpserver.HttpServer.create(
                new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/test.crl", exchange -> {
            exchange.sendResponseHeaders(200, crl.length);
            exchange.getResponseBody().write(crl);
            exchange.close();
        });
        server.start();
        try {
            String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/test.crl";
            X509Certificate good = CertificateFixtures.issuedBy("Good", root, url, null, BigInteger.valueOf(3005));
            RevocationStatus status = new RevocationStatus();
            RevocationsVerifier.verifyRevocations(good, Collections.singleton(root.getCertificate()), new Date(),
                    FAST, status);
            assertTrue(status.isComplete(), String.valueOf(status.getUnchecked()));

            X509Certificate revoked = CertificateFixtures.issuedBy("Revoked", root, url, null,
                    BigInteger.valueOf(9999));
            assertThrows(RevokedCertificateException.class, () -> RevocationsVerifier.verifyRevocations(
                    revoked, Collections.singleton(root.getCertificate()), new Date(), FAST, new RevocationStatus()));
        } finally {
            server.stop(0);
        }
    }

    @DisplayName("the system properties set the defaults; withers leave the original alone")
    @Test
    public void options() {
        SignatureValidationOptions defaults = SignatureValidationOptions.defaults();
        SignatureValidationOptions offline = defaults.withOnline(false);
        assertFalse(offline.isOnline());
        assertEquals(defaults.getConnectTimeout(), offline.getConnectTimeout());
        assertEquals(0, defaults.withTimeouts(-5, 0).getConnectTimeout(), "negative clamps to no limit");
        assertEquals(offline, defaults.withOnline(false));
    }
}
