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

import org.verapdf.gf.foundry.VeraGreenfieldFoundryProvider;
import org.verapdf.pdfa.Foundries;
import org.verapdf.pdfa.PDFAParser;
import org.verapdf.pdfa.PDFAValidator;
import org.verapdf.pdfa.VeraPDFFoundry;
import org.verapdf.pdfa.flavours.PDFAFlavour;
import org.verapdf.pdfa.results.ValidationResult;

import java.io.FileInputStream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Checks a written document against a PDF/A conformance level with veraPDF.
 */
public class PDFValidator {

    /**
     * Validates against PDF/A-1b, the level most of these tests target.
     */
    public static void validatePDFA(FileInputStream fileStream) {
        validatePDFA(fileStream, PDFAFlavour.PDFA_1_B);
    }

    /**
     * Validates against a given conformance level.
     * <p>
     * A validator that cannot read the document fails the test rather than passing it. It used to
     * catch the parse and validation exceptions, print them and return, which made "could not be
     * checked" and "checked and found compliant" the same outcome to a caller - the one distinction a
     * validator exists to make.
     *
     * @param fileStream document to check, which this closes
     * @param flavour    conformance level to check against
     */
    public static void validatePDFA(FileInputStream fileStream, PDFAFlavour flavour) {
        VeraGreenfieldFoundryProvider.initialise();
        try (VeraPDFFoundry foundry = Foundries.defaultInstance();
             FileInputStream stream = fileStream) {
            PDFAParser parser = foundry.createParser(stream, flavour);
            PDFAValidator validator = foundry.createValidator(flavour, false);
            ValidationResult result = validator.validate(parser);
            if (!result.isCompliant()) {
                StringBuilder failures = new StringBuilder();
                result.getFailedChecks().forEach((check, count) ->
                        failures.append(String.format("%n  %s (x%s): %s", check.getClause(), count,
                                check.getSpecification().getDescription())));
                fail("Not valid PDF/A-" + flavour.getId() + failures);
            }
        } catch (Exception exception) {
            fail("PDF/A-" + flavour.getId() + " validation could not be run: " + exception, exception);
        }
    }

    /**
     * @return true when the document conforms, without failing the test either way - for a check that
     * is being characterised rather than asserted
     */
    public static boolean isCompliant(FileInputStream fileStream, PDFAFlavour flavour) {
        VeraGreenfieldFoundryProvider.initialise();
        try (VeraPDFFoundry foundry = Foundries.defaultInstance();
             FileInputStream stream = fileStream) {
            PDFAParser parser = foundry.createParser(stream, flavour);
            return foundry.createValidator(flavour, false).validate(parser).isCompliant();
        } catch (Exception exception) {
            throw new IllegalStateException("PDF/A validation could not be run", exception);
        }
    }
}
