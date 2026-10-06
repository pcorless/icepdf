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

import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.acroform.SignatureDictionary;
import org.icepdf.core.pobjects.annotations.Annotation;
import org.icepdf.core.pobjects.annotations.SignatureWidgetAnnotation;
import org.icepdf.ri.util.FontPropertiesManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Signing a document that is encrypted.
 * <p>
 * A signature dictionary's /Reason, /Location, /Name and /ContactInfo are ordinary text strings, so
 * in an encrypted document they are encrypted like any other.  Written in plain text they still
 * parse, and the file still opens and still validates - the reader simply decrypts them on the way
 * back in and shows the signer four fields of scrambled characters.  Nothing reports an error at any
 * point, which is why this is asserted rather than left to be noticed.
 * <p>
 * The source is borrowed from the font fixtures for want of a smaller encrypted document; what
 * matters about it here is only that it has a security handler.
 */
public class SigningEncryptedTest {

    private static final File SOURCE =
            new File("src/test/resources/fonts/gh-521-identity-h-cid.pdf");

    @BeforeAll
    public static void init() {
        FontPropertiesManager.getInstance().loadOrReadSystemFonts();
    }

    private static SignatureDictionary signatureOf(Document document) throws Exception {
        for (Annotation annotation : document.getPageTree().getPage(0).getAnnotations()) {
            if (annotation instanceof SignatureWidgetAnnotation) {
                return ((SignatureWidgetAnnotation) annotation).getSignatureDictionary();
            }
        }
        return null;
    }

    @DisplayName("a signature's details survive being written to an encrypted document")
    @Test
    public void signatureStringsRoundTripThroughEncryption() throws Exception {
        Document source = new Document();
        source.setFile(SOURCE.getAbsolutePath());
        assertNotNull(source.getCatalog().getLibrary().getSecurityManager(),
                "the fixture has to be encrypted for this to be testing anything");
        source.dispose();

        File signed = SigningFixture.of(SOURCE).reason("Certification")
                .signTo(new File("./src/test/out/SigningEncryptedTest_signed.pdf"));

        Document document = new Document();
        document.setFile(signed.getAbsolutePath());
        try {
            SignatureDictionary signature = signatureOf(document);
            assertNotNull(signature, "the signed document should carry a signature dictionary");

            // Read back through the security manager: a string written in plain text comes back
            // from here as whatever decrypting plain text produces.
            assertEquals("Certification", signature.getReason());
            assertNotNull(signature.getName());
            assertFalse(signature.getName().isEmpty());
        } finally {
            document.dispose();
        }
    }

    @DisplayName("the signature's details are not left in the file as plain text")
    @Test
    public void signatureStringsAreEncryptedOnDisk() throws Exception {
        File signed = SigningFixture.of(SOURCE).reason("Certification")
                .signTo(new File("./src/test/out/SigningEncryptedTest_plaintext.pdf"));

        // The round trip above says the value survives; this says it was not simply written in the
        // clear, which is the thing that was actually wrong and which a round trip through a
        // document that happened not to be encrypted would not catch.
        String raw = new String(Files.readAllBytes(signed.toPath()), StandardCharsets.ISO_8859_1);
        assertFalse(raw.contains("/Reason (Certification)"),
                "the reason was written unencrypted into an encrypted document");
    }
}
