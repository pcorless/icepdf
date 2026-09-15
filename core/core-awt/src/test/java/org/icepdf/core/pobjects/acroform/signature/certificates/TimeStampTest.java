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

import org.bouncycastle.tsp.TimeStampToken;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests asking a timestamp authority to stamp a signature.
 * <p>
 * A timestamp is what lets a signature outlive its certificate: it says the signature existed at a
 * given moment, so a certificate that expires or is revoked later does not retroactively invalidate
 * what it signed.  That only holds if the token really is about this signature and really came from
 * the authority, which is what the client checks - the nonce it sent, and the hash it asked to have
 * stamped, both have to come back.
 * <p>
 * The authority here is real: it signs genuine RFC 3161 tokens with BouncyCastle and serves them
 * over http, and it can be told to answer wrongly, because a client that accepts any token gives a
 * signature a date that means nothing.
 */
public class TimeStampTest {

    private static final byte[] CONTENT =
            "the bytes of a signature".getBytes(StandardCharsets.ISO_8859_1);

    private interface AuthorityWork {
        void run(TsaServer server, CertificateFixtures.Authority authority) throws Exception;
    }

    /**
     * Starts an authority trusted by a fresh root and runs {@code work} against it.
     */
    private void withAuthority(AuthorityWork work) throws Exception {
        CertificateFixtures.Authority root = CertificateFixtures.rootAuthority("Test Root");
        CertificateFixtures.Authority tsa =
                CertificateFixtures.timeStampAuthority("Test TSA", root);
        try (TsaServer server = new TsaServer(tsa)) {
            work.run(server, tsa);
        }
    }

    private static TSAClient client(TsaServer server, String username, String password)
            throws Exception {
        return new TSAClient(new URI(server.getUrl()).toURL(), username, password,
                MessageDigest.getInstance("SHA-256"));
    }

    private static TimeStampToken stamp(TsaServer server) throws Exception {
        return client(server, null, null).getTimeStampToken(new ByteArrayInputStream(CONTENT));
    }

    // ------------------------------------------------------------------
    // a token that can be trusted
    // ------------------------------------------------------------------

    @DisplayName("an authority stamps the content it was asked about")
    @Test
    public void stampsTheContent() throws Exception {
        withAuthority((server, authority) -> {
            TimeStampToken token = stamp(server);
            assertNotNull(token);

            // the token has to be about this content and no other: the imprint it carries is the
            // digest of what was sent, and anything else would date somebody else's signature
            byte[] expected = MessageDigest.getInstance("SHA-256").digest(CONTENT);
            assertArrayEquals(expected,
                    token.getTimeStampInfo().getMessageImprintDigest(),
                    "the token should stamp the digest of the content that was sent");
        });
    }

    @DisplayName("the token carries the time the authority says it was made")
    @Test
    public void tokenCarriesItsTime() throws Exception {
        withAuthority((server, authority) -> {
            Date before = new Date(System.currentTimeMillis() - 1000);
            TimeStampToken token = stamp(server);
            Date after = new Date(System.currentTimeMillis() + 1000);

            Date genTime = token.getTimeStampInfo().getGenTime();
            assertTrue(!genTime.before(before) && !genTime.after(after),
                    "the token's time should be when it was made, was " + genTime);
        });
    }

    @DisplayName("the token names the authority that signed it")
    @Test
    public void tokenNamesItsAuthority() throws Exception {
        // The certificate comes back in the token because the client asked for it, which is what
        // lets a reader check later who vouched for the time.
        withAuthority((server, authority) -> {
            TimeStampToken token = stamp(server);
            assertNotNull(token.getCertificates());
            assertTrue(token.getCertificates().getMatches(token.getSID()).iterator().hasNext(),
                    "the signing certificate should travel with the token");
        });
    }

    @DisplayName("two requests for the same content get different tokens")
    @Test
    public void everyRequestIsItsOwn() throws Exception {
        // Each carries its own nonce and serial, so one cannot be mistaken for the other; a token
        // that repeated would be indistinguishable from a replayed one.
        withAuthority((server, authority) -> {
            TimeStampToken first = stamp(server);
            TimeStampToken second = stamp(server);
            assertTrue(!first.getTimeStampInfo().getSerialNumber()
                            .equals(second.getTimeStampInfo().getSerialNumber()),
                    "two tokens should not share a serial number");
        });
    }

    // ------------------------------------------------------------------
    // answers that cannot be trusted
    // ------------------------------------------------------------------

    @DisplayName("a token answering a different nonce is refused")
    @Test
    public void wrongNonce() throws Exception {
        // The nonce ties the token to the request that asked for it.  Accepting one that does not
        // match lets an old token be replayed, dating a signature to whenever that token was made
        // rather than to now.
        withAuthority((server, authority) -> {
            server.withWrongNonce();
            assertThrows(IOException.class, () -> stamp(server));
        });
    }

    @DisplayName("a token stamping different content is refused")
    @Test
    public void wrongImprint() throws Exception {
        // Otherwise an authority could hand back a stamp of anything at all and the signature
        // would carry a date that belongs to some other document.
        withAuthority((server, authority) -> {
            byte[] other = MessageDigest.getInstance("SHA-256")
                    .digest("something else entirely".getBytes(StandardCharsets.ISO_8859_1));
            server.withWrongImprint(other);
            assertThrows(IOException.class, () -> stamp(server));
        });
    }

    @DisplayName("an authority that refuses the request yields no token")
    @Test
    public void refusedRequest() throws Exception {
        // A rejection carries no token, and the failure has to say so rather than hand back null
        // for the caller to trip over.
        withAuthority((server, authority) -> {
            server.refusing(2);
            IOException thrown = assertThrows(IOException.class, () -> stamp(server));
            assertNotNull(thrown.getMessage());
        });
    }

    @DisplayName("an authority that cannot be reached yields no token")
    @Test
    public void unreachableAuthority() throws Exception {
        TSAClient client = new TSAClient(new URI("http://127.0.0.1:1/tsa").toURL(), null, null,
                MessageDigest.getInstance("SHA-256"));
        assertThrows(IOException.class, () ->
                client.getTimeStampToken(new ByteArrayInputStream(CONTENT)));
    }

    // ------------------------------------------------------------------
    // authorities that want a password
    // ------------------------------------------------------------------

    @DisplayName("credentials are sent when the authority is given some")
    @Test
    public void basicAuthentication() throws Exception {
        withAuthority((server, authority) -> {
            client(server, "user", "secret").getTimeStampToken(new ByteArrayInputStream(CONTENT));

            String authorization = server.getLastAuthorization();
            assertNotNull(authorization, "credentials should have been sent");
            assertTrue(authorization.startsWith("Basic "), authorization);
            assertEquals("user:secret", new String(
                    Base64.getDecoder().decode(authorization.substring("Basic ".length())),
                    StandardCharsets.UTF_8));
        });
    }

    @DisplayName("nothing is sent when there are no credentials to send")
    @Test
    public void noAuthentication() throws Exception {
        // An empty username is the same as none; sending "Basic Og==" would be a request for
        // anonymous access that some authorities refuse outright.
        withAuthority((server, authority) -> {
            stamp(server);
            assertNull(server.getLastAuthorization());
        });
        withAuthority((server, authority) -> {
            client(server, "", "").getTimeStampToken(new ByteArrayInputStream(CONTENT));
            assertNull(server.getLastAuthorization());
        });
    }

    // ------------------------------------------------------------------
    // the verifier that wraps the client
    // ------------------------------------------------------------------

    @DisplayName("a verifier with no authority url does not try to stamp")
    @Test
    public void verifierWithoutAnAuthority() throws Exception {
        // Timestamping is optional, and a signature without one still has to be produced.
        assertNotNull(new TimeStampVerifier(null));
    }

    @DisplayName("a verifier builds a client for the authority it was given")
    @Test
    public void verifierWithAnAuthority() throws Exception {
        withAuthority((server, authority) ->
                assertNotNull(new TimeStampVerifier(server.getUrl())));
    }
}
