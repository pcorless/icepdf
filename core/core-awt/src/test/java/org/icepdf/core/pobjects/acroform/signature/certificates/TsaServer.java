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

import com.sun.net.httpserver.HttpServer;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.cert.jcajce.JcaCertStore;
import org.bouncycastle.cert.jcajce.JcaX509CertificateHolder;
import org.bouncycastle.cms.SignerInfoGenerator;
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoGeneratorBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.DigestCalculator;
import org.bouncycastle.operator.bc.BcDigestCalculatorProvider;
import org.bouncycastle.tsp.TimeStampRequest;
import org.bouncycastle.tsp.TimeStampRequestGenerator;
import org.bouncycastle.tsp.TimeStampResponse;
import org.bouncycastle.tsp.TSPAlgorithms;
import org.bouncycastle.tsp.TimeStampResponseGenerator;
import org.bouncycastle.tsp.TimeStampTokenGenerator;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.Date;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A real RFC 3161 timestamp authority, over http, for the tests to ask.
 * <p>
 * A timestamp says a signature existed at a point in time, and the client checks the answer against
 * the question it asked - the nonce it sent and the hash it wanted stamped.  Mocking the response
 * would skip exactly that comparison, so this signs genuine tokens with BouncyCastle and can be
 * told to answer wrongly: with someone else's nonce, a stamp of a different hash, or a refusal.
 */
public final class TsaServer implements AutoCloseable {

    /** SHA-256, which is what the client asks to be stamped with. */
    private static final ASN1ObjectIdentifier SHA256 =
            new ASN1ObjectIdentifier("2.16.840.1.101.3.4.2.1");
    /** An arbitrary policy oid; a real authority publishes its own. */
    private static final ASN1ObjectIdentifier POLICY = new ASN1ObjectIdentifier("1.2.3.4.1");

    private final HttpServer server;
    private final String url;
    private final CertificateFixtures.Authority authority;

    private int status = 0;                       // granted
    private BigInteger overrideNonce;
    private byte[] overrideImprint;
    private Date genTime;
    private final AtomicReference<String> lastAuthorization = new AtomicReference<>();

    public TsaServer(CertificateFixtures.Authority authority) throws Exception {
        this.authority = authority;
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/tsa", exchange -> {
            try {
                lastAuthorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
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

    public String getUrl() {
        return url;
    }

    /** The Authorization header of the last request, or null if there was none. */
    public String getLastAuthorization() {
        return lastAuthorization.get();
    }

    /** Refuses the request with the given PKIStatus, 2 being rejection. */
    public TsaServer refusing(int status) {
        this.status = status;
        return this;
    }

    /** Answers with a nonce other than the one asked with, as a replayed token would. */
    public TsaServer withWrongNonce() {
        this.overrideNonce = BigInteger.valueOf(987654321L);
        return this;
    }

    /** Stamps a hash other than the one asked about. */
    public TsaServer withWrongImprint(byte[] imprint) {
        this.overrideImprint = imprint;
        return this;
    }

    /** Dates the token at a time of its choosing. */
    public TsaServer at(Date genTime) {
        this.genTime = genTime;
        return this;
    }

    private byte[] respondTo(byte[] encodedRequest) throws Exception {
        TimeStampRequest request = new TimeStampRequest(encodedRequest);

        DigestCalculator digestCalculator = new BcDigestCalculatorProvider()
                .get(new org.bouncycastle.asn1.x509.AlgorithmIdentifier(SHA256));
        SignerInfoGenerator signerInfoGenerator = new JcaSimpleSignerInfoGeneratorBuilder()
                .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                .build("SHA256withRSA", authority.getPrivateKey(), authority.getCertificate());

        TimeStampTokenGenerator tokenGenerator =
                new TimeStampTokenGenerator(signerInfoGenerator, digestCalculator, POLICY);
        tokenGenerator.addCertificates(new JcaCertStore(
                Collections.singletonList(authority.getCertificate())));

        TimeStampResponseGenerator responseGenerator =
                new TimeStampResponseGenerator(tokenGenerator, TSPAlgorithms.ALLOWED);

        if (status != 0) {
            return responseGenerator.generateRejectedResponse(
                    new Exception("refused by the authority")).getEncoded();
        }

        // Rebuild the request when the answer is meant to be wrong, so the token is signed over
        // something other than what was asked.
        TimeStampRequest answered = request;
        if (overrideNonce != null || overrideImprint != null) {
            TimeStampRequestGenerator generator = new TimeStampRequestGenerator();
            generator.setCertReq(true);
            answered = generator.generate(SHA256,
                    overrideImprint != null ? overrideImprint : request.getMessageImprintDigest(),
                    overrideNonce != null ? overrideNonce : request.getNonce());
        }

        TimeStampResponse response = responseGenerator.generate(answered,
                BigInteger.valueOf(System.nanoTime()),
                genTime != null ? genTime : new Date());
        return response.getEncoded();
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
