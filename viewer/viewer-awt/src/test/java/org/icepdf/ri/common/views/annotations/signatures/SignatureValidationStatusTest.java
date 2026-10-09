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
package org.icepdf.ri.common.views.annotations.signatures;

import org.icepdf.core.pobjects.acroform.signature.SignatureValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.ResourceBundle;

import static org.icepdf.ri.common.views.annotations.signatures.SignatureValidationStatus.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The validity, document and identity lines (and so the icon) the Swing signature dialogs and panel
 * show.  Validity used to depend on whether later signatures cover the rest of the file, so an
 * intact signature followed by form fill-in or another signature showed a red cross under the
 * words "validity is unknown"; and a broken signature was described as "unaltered but subsequent
 * changes have been made".
 */
public class SignatureValidationStatusTest {

    private static SignatureValidator validator(boolean signedDataModified, boolean documentModified,
                                                boolean trusted, boolean revoked, boolean coversFile) {
        SignatureValidator v = mock(SignatureValidator.class);
        when(v.isSignedDataModified()).thenReturn(signedDataModified);
        when(v.isDocumentDataModified()).thenReturn(documentModified);
        when(v.isCertificateChainTrusted()).thenReturn(trusted);
        when(v.isRevocation()).thenReturn(revoked);
        when(v.isSignaturesCoverDocumentLength()).thenReturn(coversFile);
        return v;
    }

    @DisplayName("an intact, trusted signature is valid")
    @Test
    void validTrusted() {
        SignatureValidator v = validator(false, false, true, false, true);
        assertEquals(Validity.VALID, validityOf(v));
        assertEquals(DocumentState.UNMODIFIED, documentStateOf(v));
        assertEquals(Identity.VALID, identityOf(v));
    }

    @DisplayName("later revisions don't break an earlier signature: not invalid, 'subsequent changes'")
    @Test
    void laterRevisions() {
        // not covering the whole file is what any signature followed by a later revision looks like.
        SignatureValidator trusted = validator(false, true, true, false, false);
        assertEquals(Validity.VALID, validityOf(trusted));
        assertEquals(DocumentState.SUBSEQUENT_CHANGES, documentStateOf(trusted));
        SignatureValidator untrusted = validator(false, true, false, false, false);
        assertEquals(Validity.UNKNOWN, validityOf(untrusted), "was a red cross");
    }

    @DisplayName("an intact signature by an untrusted signer is unknown")
    @Test
    void untrusted() {
        SignatureValidator v = validator(false, false, false, false, true);
        assertEquals(Validity.UNKNOWN, validityOf(v));
        assertEquals(Identity.UNKNOWN, identityOf(v));
    }

    @DisplayName("altered signed content is invalid and described as altered, covering the file or not")
    @Test
    void altered() {
        for (boolean covers : new boolean[]{true, false}) {
            SignatureValidator v = validator(true, true, false, false, covers);
            assertEquals(Validity.INVALID, validityOf(v));
            assertEquals(DocumentState.ALTERED, documentStateOf(v), "was 'unaltered' when not covering the file");
        }
    }

    @DisplayName("a revoked certificate is invalid and says so")
    @Test
    void revoked() {
        SignatureValidator v = validator(false, false, false, true, true);
        assertEquals(Validity.INVALID, validityOf(v));
        assertEquals(Identity.REVOKED, identityOf(v));
    }

    @DisplayName("every key the rules build exists, and valid / unknown say what they mean")
    @Test
    void messageKeys() {
        ResourceBundle bundle = ResourceBundle.getBundle("org.icepdf.ri.resources.MessageBundle", Locale.ENGLISH);
        for (String prefix : new String[]{"viewer.annotation.signature.validation.common.",
                "viewer.utilityPane.signatures.tab.certTree.cert."}) {
            for (Validity validity : Validity.values()) bundle.getString(prefix + validity.key + ".label");
            assertEquals("Signature is valid:", bundle.getString(prefix + "valid.label"));
            assertEquals("Signature validity is unknown:", bundle.getString(prefix + "unknown.label"));
        }
        for (DocumentState state : DocumentState.values()) {
            bundle.getString("viewer.annotation.signature.validation.common.doc." + state.key + ".label");
            bundle.getString("viewer.utilityPane.signatures.tab.certTree.doc." + state.key + ".label");
        }
        for (Identity identity : Identity.values()) {
            bundle.getString("viewer.annotation.signature.validation.common.identity." + identity.key + ".label");
            bundle.getString("viewer.utilityPane.signatures.tab.certTree.signature.identity." + identity.key + ".label");
        }
    }

    @DisplayName("every translation keeps valid and unknown apart the same way as English")
    @Test
    void translationsAgree() {
        for (String language : new String[]{"da", "de", "es", "fi", "fr", "it", "nl", "no", "pt", "sv"}) {
            ResourceBundle bundle = ResourceBundle.getBundle("org.icepdf.ri.resources.MessageBundle",
                    new Locale(language));
            String valid = bundle.getString("viewer.annotation.signature.validation.common.valid.label");
            String unknown = bundle.getString("viewer.annotation.signature.validation.common.unknown.label");
            // the "unknown" sentence is the longer one in every language here ("... validity is unknown").
            assertEquals(true, unknown.length() > valid.length(), language + ": " + valid + " / " + unknown);
        }
    }
}
