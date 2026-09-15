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
import org.icepdf.core.pobjects.acroform.InteractiveForm;
import org.icepdf.core.pobjects.acroform.signature.SignatureValidator;
import org.icepdf.core.pobjects.acroform.signature.appearance.SignatureType;
import org.icepdf.core.pobjects.annotations.SignatureWidgetAnnotation;
import org.icepdf.ri.util.FontPropertiesManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Signs a document, alters it, and checks that the alteration is reported.
 * <p>
 * The existing signing tests assert that a freshly signed document validates.  That is the half of
 * the contract a validator satisfies by accident: a method that always answered "not modified"
 * would pass every one of them.  The half that matters is here - a document changed after it was
 * signed has to be reported as changed, because a validator that misses that is worse than no
 * validator at all, having told the reader the document was genuine.
 * <p>
 * Each case alters the signed bytes in a way a reader would not notice on its own: the file stays
 * well formed, still opens, and still holds exactly one signature.  What changes is something
 * inside the range the signature covers.
 */
public class TamperedSignatureTest {

    private static final Path SOURCE = Path.of("src/test/resources/annotation/hello_pdfa1.pdf");
    private static final Path OUT = Path.of("./src/test/out");

    @BeforeAll
    public static void init() {
        FontPropertiesManager.getInstance().loadOrReadSystemFonts();
    }

    /**
     * Signs the fixture document, giving the signature the stated reason.
     *
     * @param name output file name
     * @return the signed file's bytes
     */
    private static byte[] sign(String name) throws Exception {
        File out = SigningFixture.of(SOURCE.toFile())
                .reason("Approval")
                .signatureType(SignatureType.CERTIFIER)
                .signTo(OUT.resolve(name).toFile());
        return Files.readAllBytes(out.toPath());
    }

    /**
     * Writes bytes to a file under the test output directory.
     *
     * @param name file name
     * @param pdf  bytes to write
     * @return the file
     */
    private static File write(String name, byte[] pdf) throws Exception {
        File file = OUT.resolve(name).toFile();
        Files.createDirectories(OUT);
        Files.write(file.toPath(), pdf);
        return file;
    }

    /**
     * Opens a document and hands back the validator for its one signature, already run.
     *
     * @param file document to validate
     * @param work what to assert about the validator
     */
    private static void validating(File file, ValidatorAssertions work) throws Exception {
        Document document = new Document();
        document.setFile(file.getAbsolutePath());
        try {
            InteractiveForm interactiveForm = document.getCatalog().getInteractiveForm();
            assertNotNull(interactiveForm, "the altered document should still hold its /AcroForm");
            ArrayList<SignatureWidgetAnnotation> fields = interactiveForm.getSignatureFields();
            assertEquals(1, fields.size(), "the alteration should have left the signature in place");

            SignatureValidator validator = fields.get(0).getSignatureValidator();
            validator.validate();
            work.check(validator);
        } finally {
            document.dispose();
        }
    }

    @FunctionalInterface
    private interface ValidatorAssertions {
        void check(SignatureValidator validator) throws Exception;
    }

    // ------------------------------------------------------------------
    // the control
    // ------------------------------------------------------------------

    @DisplayName("the document as signed reports no modification")
    @Test
    public void untamperedControl() throws Exception {
        // Without this the tests below prove nothing: a validator that reported everything as
        // modified would pass them all.
        File signed = write("TamperedSignatureTest_control.pdf", sign("TamperedSignatureTest_signed.pdf"));
        validating(signed, validator -> {
            assertTrue(!validator.isDocumentDataModified(), "nothing was altered");
            assertTrue(!validator.isSignedDataModified(), "nothing was altered");
            assertTrue(validator.isSignaturesCoverDocumentLength());
            assertTrue(validator.checkByteRange());
        });
    }

    // ------------------------------------------------------------------
    // altering what the signature covers
    // ------------------------------------------------------------------

    @DisplayName("altering the stated reason after signing is reported")
    @Test
    public void alteredReasonIsDetected() throws Exception {
        // /Reason sits inside the second byte range, so it is signed.  Swapping it for another
        // word of the same length leaves the file byte-for-byte the same length and perfectly well
        // formed: only the digest of what was signed changes.  Someone altering why a document was
        // signed is exactly the tamper a signature exists to catch.
        byte[] signed = sign("TamperedSignatureTest_reason_source.pdf");
        byte[] tampered = replaceOnce(signed, "Approval", "Rejected");

        File file = write("TamperedSignatureTest_reason.pdf", tampered);
        assertEquals(signed.length, tampered.length, "the alteration must not change the length");
        validating(file, validator ->
                assertTrue(validator.isDocumentDataModified() || validator.isSignedDataModified(),
                        "altering the signed /Reason has to be reported as a modification"));
    }

    @DisplayName("altering a byte of page content after signing is reported")
    @Test
    public void alteredContentIsDetected() throws Exception {
        // The other end of the file: the first byte range covers the document body, so changing
        // what the page says must be caught too.
        byte[] signed = sign("TamperedSignatureTest_content_source.pdf");
        byte[] tampered = signed.clone();
        int at = indexOf(tampered, "%PDF-1.".getBytes(StandardCharsets.ISO_8859_1)) + 7;
        tampered[at] = tampered[at] == '4' ? (byte) '5' : (byte) '4';

        File file = write("TamperedSignatureTest_content.pdf", tampered);
        validating(file, validator ->
                assertTrue(validator.isDocumentDataModified() || validator.isSignedDataModified(),
                        "altering the signed body has to be reported as a modification"));
    }

    // ------------------------------------------------------------------
    // adding to the document after it was signed
    // ------------------------------------------------------------------

    @DisplayName("bytes appended after the signed range are reported as not covered")
    @Test
    public void appendedBytesAreNotCovered() throws Exception {
        // An append does not change what was signed, so the digest still matches; what it changes
        // is how much of the file the signature speaks for.  A reader shown "signature valid" for
        // a document with unsigned content added to the end has been told something false.
        byte[] signed = sign("TamperedSignatureTest_append_source.pdf");
        byte[] appended = new byte[signed.length + 32];
        System.arraycopy(signed, 0, appended, 0, signed.length);
        System.arraycopy("\n% appended after signing\n".getBytes(StandardCharsets.ISO_8859_1), 0,
                appended, signed.length, 26);

        File file = write("TamperedSignatureTest_appended.pdf", appended);
        validating(file, validator ->
                assertTrue(!validator.isSignaturesCoverDocumentLength(),
                        "the signature no longer covers the whole document"));
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    /**
     * Replaces the first occurrence of {@code find} with {@code replace}, which must be the same
     * length so every offset in the file stays where it was.
     */
    private static byte[] replaceOnce(byte[] source, String find, String replace) {
        assertEquals(find.length(), replace.length(), "the replacement has to be the same length");
        byte[] needle = find.getBytes(StandardCharsets.ISO_8859_1);
        int at = indexOf(source, needle);
        assertTrue(at >= 0, "could not find " + find + " in the signed document");

        byte[] result = source.clone();
        System.arraycopy(replace.getBytes(StandardCharsets.ISO_8859_1), 0, result, at, needle.length);
        assertArrayEquals(replace.getBytes(StandardCharsets.ISO_8859_1),
                java.util.Arrays.copyOfRange(result, at, at + needle.length));
        return result;
    }

    private static int indexOf(byte[] haystack, byte[] needle) {
        outer:
        for (int i = 0; i <= haystack.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }
}
