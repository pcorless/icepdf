/*
 * Copyright 2006-2016 ICEsoft Technologies Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an "AS
 * IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */
package org.icepdf.core.pobjects.acroform.signature;

import org.icepdf.core.pobjects.acroform.signature.exceptions.SignatureIntegrityException;

import java.security.cert.X509Certificate;

/**
 * Interface for Digital Signature validation.  Singer certificate validity can be determined from this class.
 */
public interface Validator {

    void init() throws SignatureIntegrityException;

    /**
     * Checks integrity of the signature and will set the boolean property defining isDocumentModified.
     *
     * @throws SignatureIntegrityException occurs if there is an issue validating the public key against the cert.
     */
    void validate() throws SignatureIntegrityException;

    /**
     * General is valid call: !isDocumentModified and isCertificateTrusted and isSignerTimeValid and isValidationTimeValid.
     * This method may return invalid even if the document !isDocumentModified because of singer trust issues.
     *
     * @return true if valid, otherwise false.
     */
    boolean isValid();

    /**
     * Evaluation of the signature has yielded if the document has bee altered or not since it was singed.
     *
     * @return true true if the document has been modified since it was singed.
     */
    boolean isDocumentModified();

    /**
     * The certificate has been verified as trusted.
     *
     * @return true if the certificate is trusted, otherwise false.
     */
    boolean isCertificateTrusted();

    boolean isRevocationCheck();

    /**
     * The singer time stamp is valid.
     *
     * @return true if the signer time is valid.
     */
    boolean isSignerTimeValid();

    /**
     * Validation time is valid.
     *
     * @return true if the validation time is valid.
     */
    boolean isValidationTimeValid();

    /**
     * Gets the signers certificate.
     *
     * @return signers certificate.
     */
    X509Certificate getSignerCertificate();

}
