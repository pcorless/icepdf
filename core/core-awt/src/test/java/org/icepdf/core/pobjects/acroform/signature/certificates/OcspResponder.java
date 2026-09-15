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
import org.bouncycastle.asn1.ocsp.OCSPObjectIdentifiers;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.Extensions;
import org.bouncycastle.cert.jcajce.JcaX509CertificateHolder;
import org.bouncycastle.cert.ocsp.BasicOCSPRespBuilder;
import org.bouncycastle.cert.ocsp.CertificateStatus;
import org.bouncycastle.cert.ocsp.OCSPReq;
import org.bouncycastle.cert.ocsp.OCSPResp;
import org.bouncycastle.cert.ocsp.OCSPRespBuilder;
import org.bouncycastle.cert.ocsp.Req;
import org.bouncycastle.cert.ocsp.RespID;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.security.cert.X509Certificate;
import java.util.Date;

/**
 * A real OCSP responder, over http, for the tests to ask.
 * <p>
 * Mocking the answer would leave the half that matters untested: the request is built by the code
 * under test, signed responses are parsed by it, and the nonce that guards against a replayed
 * answer only means anything if a real request and a real response are compared.  This builds and
 * signs a genuine response with BouncyCastle and serves it the way a responder would.
 * <p>
 * It can also answer wrongly on purpose - a stale response, one signed by the wrong key, one
 * echoing the wrong nonce - because a verifier is only as good as what it refuses.
 */
public final class OcspResponder implements AutoCloseable {

    /** What the responder says about the certificate it is asked about. */
    public enum Answer {
        /** The certificate is in good standing. */
        GOOD,
        /** The certificate has been revoked. */
        REVOKED,
        /** The responder does not know this certificate. */
        UNKNOWN
    }

    private final HttpServer server;
    private final String url;

    private Answer answer = Answer.GOOD;
    private Date revocationDate = new Date();
    private int responseStatus = OCSPRespBuilder.SUCCESSFUL;
    private boolean echoNonce = true;
    private byte[] wrongNonce;
    private Date thisUpdate;
    private Date nextUpdate;
    private CertificateFixtures.Authority signer;

    /**
     * Starts a responder signing with {@code signer}.
     *
     * @param signer the responder's own certificate and key
     */
    public OcspResponder(CertificateFixtures.Authority signer) throws Exception {
        this.signer = signer;
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/ocsp", exchange -> {
            try {
                byte[] response = respondTo(readAll(exchange.getRequestBody()));
                exchange.getResponseHeaders().add("Content-Type", "application/ocsp-response");
                exchange.sendResponseHeaders(200, response.length);
                exchange.getResponseBody().write(response);
            } catch (Exception e) {
                exchange.sendResponseHeaders(500, -1);
            } finally {
                exchange.close();
            }
        });
        server.start();
        url = "http://127.0.0.1:" + server.getAddress().getPort() + "/ocsp";
    }

    /** @return the url a certificate should name to be checked here */
    public String getUrl() {
        return url;
    }

    public OcspResponder answering(Answer answer) {
        this.answer = answer;
        return this;
    }

    public OcspResponder revokedOn(Date revocationDate) {
        this.answer = Answer.REVOKED;
        this.revocationDate = revocationDate;
        return this;
    }

    /** Answers with a status other than successful, as a busy or unhappy responder would. */
    public OcspResponder withResponseStatus(int responseStatus) {
        this.responseStatus = responseStatus;
        return this;
    }

    /** Leaves the nonce out of the response, which sends the client to the freshness check. */
    public OcspResponder withoutNonce() {
        this.echoNonce = false;
        return this;
    }

    /** Echoes a nonce of its own rather than the one asked with, as a replayed answer would. */
    public OcspResponder withWrongNonce() {
        this.wrongNonce = new byte[]{9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9};
        return this;
    }

    /** Answers with an update window that does not contain now. */
    public OcspResponder withValidity(Date thisUpdate, Date nextUpdate) {
        this.thisUpdate = thisUpdate;
        this.nextUpdate = nextUpdate;
        return this;
    }

    /** Signs with a key that is not the responder's, as an impostor would. */
    public OcspResponder signedBy(CertificateFixtures.Authority other) {
        this.signer = other;
        return this;
    }

    private byte[] respondTo(byte[] encodedRequest) throws Exception {
        OCSPReq request = new OCSPReq(encodedRequest);
        if (responseStatus != OCSPRespBuilder.SUCCESSFUL) {
            return new OCSPRespBuilder().build(responseStatus, null).getEncoded();
        }

        X509Certificate responderCertificate = signer.getCertificate();
        BasicOCSPRespBuilder builder = new BasicOCSPRespBuilder(
                new RespID(new JcaX509CertificateHolder(responderCertificate).getSubject()));

        CertificateStatus status;
        switch (answer) {
            case REVOKED:
                status = new org.bouncycastle.cert.ocsp.RevokedStatus(revocationDate,
                        org.bouncycastle.asn1.x509.CRLReason.privilegeWithdrawn);
                break;
            case UNKNOWN:
                status = new org.bouncycastle.cert.ocsp.UnknownStatus();
                break;
            case GOOD:
            default:
                status = CertificateStatus.GOOD;
                break;
        }

        Date from = thisUpdate != null ? thisUpdate
                : new Date(System.currentTimeMillis() - 60_000);
        Date to = nextUpdate != null ? nextUpdate
                : new Date(System.currentTimeMillis() + 60 * 60_000);
        for (Req req : request.getRequestList()) {
            builder.addResponse(req.getCertID(), status, from, to, null);
        }

        // The nonce ties this response to the request that asked for it; echoing the one that was
        // asked with is what stops an old answer being replayed.
        byte[] nonce = wrongNonce;
        if (nonce == null && echoNonce) {
            Extension requested = request.getExtension(OCSPObjectIdentifiers.id_pkix_ocsp_nonce);
            if (requested != null) {
                nonce = requested.getExtnValue().getOctets();
            }
        }
        if (nonce != null) {
            builder.setResponseExtensions(new Extensions(new Extension[]{
                    new Extension(OCSPObjectIdentifiers.id_pkix_ocsp_nonce, false, nonce)}));
        }

        ContentSigner contentSigner = new JcaContentSignerBuilder("SHA256withRSA")
                .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                .build(signer.getPrivateKey());
        return new OCSPRespBuilder().build(OCSPRespBuilder.SUCCESSFUL,
                builder.build(contentSigner,
                        new org.bouncycastle.cert.X509CertificateHolder[]{
                                new JcaX509CertificateHolder(responderCertificate)},
                        new Date())).getEncoded();
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
