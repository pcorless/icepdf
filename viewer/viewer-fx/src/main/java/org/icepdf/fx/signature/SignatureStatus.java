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
package org.icepdf.fx.signature;

import org.icepdf.core.pobjects.annotations.SignatureWidgetAnnotation;

import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.List;

/**
 * The result of checking one signature field: a {@link Verdict} plus the facts it rests on, the
 * signer and the certificate chain.  Immutable; made by {@link SignatureVerifier}.  No JavaFX types.
 *
 * @param widget              the signature field
 * @param pageIndex           zero-based page the field is on, or -1 if unknown
 * @param fieldName           fully qualified field name
 * @param verdict             overall result
 * @param signerName          the signer: the certificate's common name, else the signature's /Name
 * @param signerEmail         from the certificate, or null
 * @param signerOrganization  from the certificate, or null
 * @param reason              /Reason, or null
 * @param location            /Location, or null
 * @param contact             /ContactInfo, or null
 * @param signingTime         the time the signature claims (/M), or null
 * @param timestamped         a timestamp authority's token is embedded
 * @param certification       a certification (DocMDP) signature, not an approval signature
 * @param signedDataModified  the bytes the signature covers have changed: the signature is broken
 * @param modifiedAfterSigning the file has later revisions (form fill-in, more signatures, edits)
 * @param identityTrusted     the signer's certificate chains to a trusted root (the JRE's cacerts)
 * @param selfSigned          the signer's certificate is self-signed
 * @param revoked             the certificate is revoked
 * @param certificateDateValid the certificate was valid at the signing time
 * @param chain               the certificate chain, signer first; empty if unsigned
 * @param problem             why the signature couldn't be checked ({@link Verdict#ERROR}), else null
 */
public record SignatureStatus(SignatureWidgetAnnotation widget, int pageIndex, String fieldName, Verdict verdict,
                              String signerName, String signerEmail, String signerOrganization,
                              String reason, String location, String contact, Date signingTime,
                              boolean timestamped, boolean certification,
                              boolean signedDataModified, boolean modifiedAfterSigning,
                              boolean identityTrusted, boolean selfSigned, boolean revoked,
                              boolean certificateDateValid, List<X509Certificate> chain, String problem) {

    /** Overall result, as Acrobat's signature panel summarises it. */
    public enum Verdict {
        /** Unchanged since signing and the signer's identity is trusted. */
        VALID,
        /** Unchanged since signing, but the signer's identity can't be verified (untrusted, self-signed, expired). */
        UNKNOWN,
        /** The signed content was changed, or the certificate was revoked. */
        INVALID,
        /** An empty signature field, waiting to be signed. */
        UNSIGNED,
        /** The signature couldn't be checked (malformed, unsupported). */
        ERROR
    }

    public SignatureStatus {
        chain = chain != null ? List.copyOf(chain) : List.of();
    }

    public boolean isSigned() {
        return verdict != Verdict.UNSIGNED;
    }

    /** One sentence for the verdict, e.g. for a tooltip or a list cell. */
    public String summary() {
        return switch (verdict) {
            case VALID -> modifiedAfterSigning ? "Signature is valid; the document has changed since it was signed"
                    : "Signature is valid";
            case UNKNOWN -> "Signature validity is unknown: the signer's identity can't be verified";
            case INVALID -> revoked && !signedDataModified ? "Signature is invalid: the certificate has been revoked"
                    : "Signature is invalid: the document was altered after it was signed";
            case UNSIGNED -> "Unsigned signature field";
            case ERROR -> "Signature couldn't be checked" + (problem != null ? ": " + problem : "");
        };
    }
}
