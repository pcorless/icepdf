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
package org.icepdf.utils;

import org.verapdf.core.EncryptedPdfException;
import org.verapdf.core.ModelParsingException;
import org.verapdf.core.ValidationException;
import org.verapdf.gf.foundry.VeraGreenfieldFoundryProvider;
import org.verapdf.pdfa.Foundries;
import org.verapdf.pdfa.PDFAParser;
import org.verapdf.pdfa.PDFAValidator;
import org.verapdf.pdfa.VeraPDFFoundry;
import org.verapdf.pdfa.flavours.PDFAFlavour;
import org.verapdf.pdfa.results.ValidationResult;

import java.io.FileInputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class PDFValidator {

    public static void validatePDFA(FileInputStream fileStream) {
        VeraGreenfieldFoundryProvider.initialise();

        PDFAFlavour flavour = PDFAFlavour.PDFA_1_B;
        try (VeraPDFFoundry foundry = Foundries.defaultInstance()) {
            PDFAParser parser = foundry.createParser(fileStream, flavour);
            PDFAValidator validator = foundry.createValidator(flavour, false);
            ValidationResult result = validator.validate(parser);
            if (result.isCompliant()) {
                // File is a valid PDF/A 1b
                System.out.println("The document is a valid PDF/A-1b" + flavour.getLevel());
            } else {
                // it isn't
                System.out.println("The document is NOT a valid PDF/A-1b" + flavour.getLevel());
                result.getFailedChecks().forEach((check, line) -> {
                    System.out.printf("Failed check: %s, details: %s %s \n",
                            check.getClause(),
                            check.getSpecification().getDescription(),
                            check.getSpecification().getPdfSpecification().toString());
                });
            }
            assertTrue(result.isCompliant(), "The document is NOT a valid PDF/A-1b" + flavour.getLevel());
        } catch (IOException | ValidationException | ModelParsingException | EncryptedPdfException exception) {
            // Exception during validation
            System.out.printf("Exception during validation: %s%n", exception.getMessage());
            exception.printStackTrace();
        }
    }
}
