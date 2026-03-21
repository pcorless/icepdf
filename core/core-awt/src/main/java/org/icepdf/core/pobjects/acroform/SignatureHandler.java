/*
 * Copyright 2006-2019 ICEsoft Technologies Canada Corp.
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
package org.icepdf.core.pobjects.acroform;

import org.icepdf.core.pobjects.acroform.signature.DigitalSignatureFactory;
import org.icepdf.core.pobjects.acroform.signature.SignatureValidator;
import org.icepdf.core.pobjects.acroform.signature.exceptions.SignatureIntegrityException;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The signature handler is responsible for returning validation results for a given Digital signature's
 * signature field dictionary.  The returned Validation objected can be interrogated to see which properties
 * are considered valid.
 */
public class SignatureHandler {

    private static final Logger logger =
            Logger.getLogger(SignatureHandler.class.toString());

    public SignatureHandler() {
    }

    /**
     * Validates the given SignatureFieldDictionary.
     *
     * @param signatureFieldDictionary signature to validate
     * @return SignatureValidator object if cert and public key verified, null otherwise.
     */
    public SignatureValidator validateSignature(SignatureFieldDictionary signatureFieldDictionary) {

        SignatureDictionary signatureDictionary = signatureFieldDictionary.getSignatureDictionary();
        if (signatureDictionary != null) {
            // Generate the correct validator and try to validate the signature.
            try {
                return DigitalSignatureFactory.getInstance().getValidatorInstance(signatureFieldDictionary);
            } catch (SignatureIntegrityException e) {
                logger.log(Level.WARNING, "Signature certificate could not be initialized.", e);
            } catch (Exception e) {
                logger.log(Level.WARNING, "Signature validation was unsuccessful.", e);
            }
        }
        return null;
    }
}
