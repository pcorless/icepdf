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

import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.PDate;
import org.icepdf.core.pobjects.acroform.InteractiveForm;
import org.icepdf.core.pobjects.acroform.SignatureDictionary;
import org.icepdf.core.pobjects.acroform.SignatureReferenceDictionary;
import org.icepdf.core.pobjects.acroform.signature.SignatureValidator;
import org.icepdf.core.pobjects.acroform.signature.exceptions.SignatureIntegrityException;
import org.icepdf.core.pobjects.annotations.SignatureWidgetAnnotation;

import javax.naming.InvalidNameException;
import javax.naming.ldap.LdapName;
import javax.naming.ldap.Rdn;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Checks a document's signatures with core's validators and sums each up as a
 * {@link SignatureStatus}.  Checking can reach the network (certificate revocation lists, OCSP), so
 * call it off the FX thread; {@code PdfView.verifySignatures()} does.  No JavaFX types.
 * <p>
 * Trust comes from the JRE's {@code cacerts}: a signature by a certificate that doesn't chain to one
 * of its roots is {@link SignatureStatus.Verdict#UNKNOWN}, as Acrobat reports an identity it can't
 * verify.
 */
public final class SignatureVerifier {

    private SignatureVerifier() {
    }

    /** Every signature field of the document, signed or not, in the form's order. */
    public static List<SignatureStatus> verifyAll(Document document) {
        List<SignatureStatus> out = new ArrayList<>();
        InteractiveForm form = document != null ? document.getCatalog().getInteractiveForm() : null;
        if (form == null) return out;
        List<SignatureWidgetAnnotation> fields = form.getSignatureFields();
        if (fields.isEmpty()) return out;
        // marks each validator with whether the document's signatures reach the end of the file.
        try {
            form.isSignaturesCoverDocumentLength();
        } catch (RuntimeException e) {
            // a malformed byte range shows up per signature below
        }
        for (SignatureWidgetAnnotation field : fields) {
            out.add(verify(field));
        }
        return out;
    }

    /** One signature field. */
    public static SignatureStatus verify(SignatureWidgetAnnotation field) {
        int pageIndex = pageIndexOf(field);
        String name = field.getFieldDictionary().getFullyQualifiedFieldName();
        if (name == null && field.getEntries().get(org.icepdf.core.pobjects.acroform.FieldDictionary.T_KEY) != null) {
            // a field added this session: its dictionary read /T before it was set.
            name = field.getLibrary().getString(field.getEntries(), org.icepdf.core.pobjects.acroform.FieldDictionary.T_KEY);
        }
        // the field's own /V, as the validator reads it: the widget's copy is only set once its page
        // has been initialised.
        SignatureDictionary dictionary = field.getFieldDictionary().getSignatureDictionary();
        if (dictionary == null || dictionary.getEntries() == null || dictionary.getEntries().isEmpty()
                || dictionary.getContents() == null) {
            return new SignatureStatus(field, pageIndex, name, SignatureStatus.Verdict.UNSIGNED, null, null, null,
                    null, null, null, null, false, false, false, false, false, false, false, false, List.of(), null);
        }
        String reason = dictionary.getReason();
        String location = dictionary.getLocation();
        String contact = dictionary.getContactInfo();
        Date signingTime = date(dictionary.getPDate());
        boolean certification = isCertification(dictionary);
        SignatureValidator validator;
        try {
            validator = field.getSignatureValidator();
            if (validator == null) throw new SignatureIntegrityException("unsupported or malformed signature");
            validator.validate();
        } catch (SignatureIntegrityException | RuntimeException e) {
            return new SignatureStatus(field, pageIndex, name, SignatureStatus.Verdict.ERROR, dictionary.getName(),
                    null, null, reason, location, contact, signingTime, false, certification, false, false, false,
                    false, false, false, List.of(), e.getMessage() != null ? e.getMessage() : e.toString());
        }

        X509Certificate signer = validator.getSignerCertificate();
        String commonName = signer != null ? part(signer, "CN") : null;
        List<X509Certificate> chain = new ArrayList<>();
        if (signer != null) chain.add(signer);
        if (validator.getCertificateChain() != null) {
            for (Certificate c : validator.getCertificateChain()) {
                if (c instanceof X509Certificate x && !chain.contains(x)) chain.add(x);
            }
        }
        boolean revoked = validator.isRevocation();
        boolean trusted = validator.isCertificateChainTrusted();
        // core only sets its date flag on a trusted chain; check the signer's certificate directly.
        boolean dateValid = signer != null && validAt(signer, signingTime != null ? signingTime : new Date());
        SignatureStatus.Verdict verdict = validator.isSignedDataModified() || revoked ? SignatureStatus.Verdict.INVALID
                : trusted && dateValid ? SignatureStatus.Verdict.VALID
                : SignatureStatus.Verdict.UNKNOWN;
        return new SignatureStatus(field, pageIndex, name, verdict,
                commonName != null ? commonName : dictionary.getName(),
                signer != null ? part(signer, "EMAILADDRESS", "E", "1.2.840.113549.1.9.1") : null,
                signer != null ? part(signer, "O") : null,
                reason, location, contact, signingTime, validator.isEmbeddedTimeStamp(), certification,
                validator.isSignedDataModified(), validator.isDocumentDataModified(), trusted,
                validator.isSelfSigned(), revoked, dateValid, chain, null);
    }

    private static boolean validAt(X509Certificate certificate, Date when) {
        try {
            certificate.checkValidity(when);
            return true;
        } catch (java.security.cert.CertificateException e) {
            return false;
        }
    }

    private static int pageIndexOf(SignatureWidgetAnnotation field) {
        try {
            return field.getPageIndex();
        } catch (RuntimeException e) {
            return -1;
        }
    }

    private static boolean isCertification(SignatureDictionary dictionary) {
        List<?> references;
        try {
            references = dictionary.getReferences();
        } catch (RuntimeException e) {
            return false;
        }
        if (references == null) return false;
        // core can hand back raw dictionaries here rather than SignatureReferenceDictionary.
        for (Object reference : references) {
            Object method = reference instanceof SignatureReferenceDictionary r ? r.getTransformMethod()
                    : reference instanceof org.icepdf.core.pobjects.DictionaryEntries d
                    ? d.get(SignatureReferenceDictionary.TRANSFORM_METHOD_KEY) : null;
            if (method != null && "DocMDP".equals(method.toString())) return true;
        }
        return false;
    }

    private static Date date(PDate date) {
        try {
            return date != null ? date.asDateWithTimeZone() : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** A part of the subject's distinguished name ("CN", "O", ...), or null. */
    static String part(X509Certificate certificate, String... types) {
        try {
            LdapName name = new LdapName(certificate.getSubjectX500Principal().getName());
            for (Rdn rdn : name.getRdns()) {
                for (String type : types) {
                    if (rdn.getType().equalsIgnoreCase(type)) return String.valueOf(rdn.getValue());
                }
            }
        } catch (InvalidNameException e) {
            // fall through
        }
        return null;
    }
}
