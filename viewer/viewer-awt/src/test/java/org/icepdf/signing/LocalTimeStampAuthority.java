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
package org.icepdf.signing;

import com.sun.net.httpserver.HttpServer;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.ExtendedKeyUsage;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.KeyPurposeId;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaCertStore;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.cms.SignerInfoGenerator;
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoGeneratorBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.DigestCalculator;
import org.bouncycastle.operator.bc.BcDigestCalculatorProvider;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.tsp.TSPAlgorithms;
import org.bouncycastle.tsp.TimeStampRequest;
import org.bouncycastle.tsp.TimeStampResponseGenerator;
import org.bouncycastle.tsp.TimeStampTokenGenerator;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.Date;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * An RFC 3161 timestamp authority the signing tests can reach without leaving the machine.
 * <p>
 * A signature carries a timestamp to say when it was made, and the only way to get one is to ask an
 * authority for it.  These tests used to ask a public one on the internet, which made them fail
 * whenever it was slow, unreachable, or simply answered a size it had not answered before - and
 * meant a full build put four requests to somebody else's server for no reason other than testing.
 * <p>
 * So this serves the tokens instead.  It is a real authority, not a stub: it signs genuine tokens
 * with BouncyCastle over the imprint it was sent, which is what makes the answer survive both the
 * client's check of the response against its request and the validator's later check of the token
 * against the signature it covers.  Handing back a canned response would pass neither.
 * <p>
 * Core has a fuller one of these, with knobs for answering wrongly, used to test the verifier.  What
 * is wanted here is only a working authority, and core's lives in that module's test sources where
 * this cannot reach it without publishing a test artifact.
 */
public final class LocalTimeStampAuthority implements AutoCloseable {

    /** SHA-256, which is the digest the client asks to have stamped. */
    private static final ASN1ObjectIdentifier SHA256 =
            new ASN1ObjectIdentifier("2.16.840.1.101.3.4.2.1");
    /** An arbitrary policy oid; a real authority publishes its own. */
    private static final ASN1ObjectIdentifier POLICY = new ASN1ObjectIdentifier("1.2.3.4.1");

    private final HttpServer server;
    private final String url;
    private final X509Certificate certificate;
    private final PrivateKey privateKey;
    private final AtomicInteger requests = new AtomicInteger();

    public LocalTimeStampAuthority() throws Exception {
        KeyPair keyPair = keyPair();
        privateKey = keyPair.getPrivate();
        certificate = timeStampingCertificate(keyPair);

        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/tsa", exchange -> {
            try {
                requests.incrementAndGet();
                byte[] response = respondTo(readAll(exchange.getRequestBody()));
                exchange.getResponseHeaders().add("Content-Type", "application/timestamp-reply");
                exchange.sendResponseHeaders(200, response.length);
                exchange.getResponseBody().write(response);
            } catch (Exception e) {
                exchange.sendResponseHeaders(500, -1);
            } finally {
                exchange.close();
            }
        });
        server.start();
        url = "http://127.0.0.1:" + server.getAddress().getPort() + "/tsa";
    }

    /**
     * @return where to point a signer at this authority
     */
    public String getUrl() {
        return url;
    }

    /**
     * How many requests have arrived, so a test can say the timestamp in a signature came from here
     * rather than from somewhere else, or from nowhere at all.
     *
     * @return the number of requests served
     */
    public int getRequestCount() {
        return requests.get();
    }

    /**
     * A key allowed to sign timestamps.
     * <p>
     * RFC 3161 wants timestamping to be the certificate's only extended key usage and wants it
     * marked critical; BouncyCastle refuses to build a token with a certificate saying otherwise,
     * and the validator checks the same thing when it opens the token later.  Self signed is
     * enough - nothing here builds a chain for the authority.
     */
    private static X509Certificate timeStampingCertificate(KeyPair keyPair) throws Exception {
        X500Name name = new X500Name("CN=Local Test TSA");
        Date from = new Date(System.currentTimeMillis() - 24L * 60 * 60 * 1000);
        Date to = new Date(System.currentTimeMillis() + 365L * 24 * 60 * 60 * 1000);

        X509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                name, BigInteger.valueOf(System.nanoTime()), from, to, name, keyPair.getPublic());
        builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(false));
        builder.addExtension(Extension.extendedKeyUsage, true,
                new ExtendedKeyUsage(KeyPurposeId.id_kp_timeStamping));

        ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA")
                .build(keyPair.getPrivate());
        return new JcaX509CertificateConverter().setProvider("BC")
                .getCertificate(builder.build(signer));
    }

    private static KeyPair keyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    /**
     * Signs a token over whatever imprint and nonce the request carried.  Both have to come back
     * unchanged: the client compares the response against the request it sent, and the validator
     * later compares the stamped imprint against the signature it is meant to cover.
     */
    private byte[] respondTo(byte[] encodedRequest) throws Exception {
        TimeStampRequest request = new TimeStampRequest(encodedRequest);

        DigestCalculator digestCalculator = new BcDigestCalculatorProvider()
                .get(new org.bouncycastle.asn1.x509.AlgorithmIdentifier(SHA256));
        SignerInfoGenerator signerInfoGenerator = new JcaSimpleSignerInfoGeneratorBuilder()
                .setProvider("BC")
                .build("SHA256withRSA", privateKey, certificate);

        TimeStampTokenGenerator tokenGenerator =
                new TimeStampTokenGenerator(signerInfoGenerator, digestCalculator, POLICY);
        // The token has to carry the certificate that signed it, or the validator has nothing to
        // check the signature against and reports the timestamp as unverifiable.
        tokenGenerator.addCertificates(new JcaCertStore(Collections.singletonList(certificate)));

        return new TimeStampResponseGenerator(tokenGenerator, TSPAlgorithms.ALLOWED)
                .generate(request, BigInteger.valueOf(System.nanoTime()), new Date())
                .getEncoded();
    }

    private static byte[] readAll(InputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int read;
        while ((read = in.read(buffer)) > 0) {
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
