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

import org.verapdf.pdfa.results.TestAssertion;
import org.verapdf.pdfa.validation.profiles.RuleId;

import java.io.File;
import java.io.FileInputStream;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.Map;

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
     * Asserts the document conforms to every level given, reporting all of them rather than stopping
     * at the first - which level a document reaches and which it misses is usually the question.
     *
     * @param file     document to check
     * @param flavours levels it is expected to meet
     */
    public static void assertConformsTo(File file, PDFAFlavour... flavours) {
        Map<PDFAFlavour, ValidationResult> results = validate(file, flavours);
        StringBuilder failures = new StringBuilder();
        results.forEach((flavour, result) -> {
            if (!result.isCompliant()) {
                failures.append(String.format("%n  PDF/A-%s:%s", flavour.getId(), failedAssertions(result)));
            }
        });
        if (failures.length() > 0) {
            fail(file.getName() + " does not conform:" + failures);
        }
    }

    /**
     * Runs the document against each level without asserting anything, for a test that is
     * characterising what a document is rather than requiring it to be something.
     */
    public static Map<PDFAFlavour, ValidationResult> validate(File file, PDFAFlavour... flavours) {
        VeraGreenfieldFoundryProvider.initialise();
        Map<PDFAFlavour, ValidationResult> results = new LinkedHashMap<>();
        for (PDFAFlavour flavour : flavours) {
            try (VeraPDFFoundry foundry = Foundries.defaultInstance();
                 FileInputStream stream = new FileInputStream(file)) {
                PDFAParser parser = foundry.createParser(stream, flavour);
                results.put(flavour, foundry.createValidator(flavour, false).validate(parser));
            } catch (Exception exception) {
                throw new IllegalStateException(
                        "PDF/A-" + flavour.getId() + " validation could not be run on " + file, exception);
            }
        }
        return results;
    }

    /**
     * Asserts that signing, or any other edit, introduced no conformance failure the source document
     * did not already have.
     * <p>
     * The useful question about an edit is not whether the result conforms - a document that went in
     * non-conforming will come out that way - but whether the edit made it worse. A PDF/A-1 file
     * checked against PDF/A-2 fails on its own identification metadata at every level, and that noise
     * would drown a real regression in an assertion that simply demanded conformance.
     *
     * @param before   the document as it was
     * @param after    the document after the edit
     * @param flavours levels to compare across
     */
    public static void assertNoNewFailures(File before, File after, PDFAFlavour... flavours) {
        Map<PDFAFlavour, ValidationResult> was = validate(before, flavours);
        Map<PDFAFlavour, ValidationResult> now = validate(after, flavours);
        StringBuilder introduced = new StringBuilder();
        for (PDFAFlavour flavour : flavours) {
            Map<String, Integer> beforeRules = failureCounts(was.get(flavour));
            for (Map.Entry<String, Integer> failure : failureCounts(now.get(flavour)).entrySet()) {
                String rule = failure.getKey();
                int wasCount = beforeRules.getOrDefault(rule, 0);
                if (wasCount == 0) {
                    introduced.append(String.format("%n  PDF/A-%s %s", flavour.getId(), rule));
                } else if (failure.getValue() > wasCount) {
                    // A rule the document already broke can be broken more times by an edit - a new
                    // page or annotation carrying the same fault - and comparing only the set of
                    // rules broken would call that no change.  It is the difference between an edit
                    // that inherits a problem and one that spreads it.
                    introduced.append(String.format("%n  PDF/A-%s %s, %d times where there were %d",
                            flavour.getId(), rule, failure.getValue(), wasCount));
                }
            }
        }
        if (introduced.length() > 0) {
            fail(after.getName() + " introduced conformance failures that " + before.getName()
                    + " did not have:" + introduced);
        }
    }

    /**
     * How many times each rule was broken, keyed by the rule's clause and test number.  veraPDF keys
     * its map by RuleId, and two RuleIds naming the same clause do not compare equal.
     */
    private static Map<String, Integer> failureCounts(ValidationResult result) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        result.getFailedChecks().forEach(
                (rule, count) -> counts.merge(ruleName(rule), count, Integer::sum));
        return counts;
    }

    private static String ruleName(RuleId rule) {
        return rule.getClause() + "-" + rule.getTestNumber();
    }

    /**
     * A line per level saying whether the document reaches it and, when it does not, why - the shape
     * of a document's conformance rather than a pass or a fail.
     */
    public static String report(File file, PDFAFlavour... flavours) {
        StringBuilder out = new StringBuilder(file.getName());
        validate(file, flavours).forEach((flavour, result) -> {
            out.append(String.format("%n  %-4s %s", flavour.getId(),
                    result.isCompliant() ? "conforms" : "does not conform"));
            out.append(failedAssertions(result));
        });
        return out.toString();
    }

    /**
     * The failed assertions, with what each one actually says.
     * <p>
     * A clause number identifies a rule but does not describe it, and looking one up means finding
     * the specification. veraPDF carries the rule's own text and the object it failed on; those are
     * what tell somebody what to fix.
     */
    private static String failedAssertions(ValidationResult result) {
        StringBuilder out = new StringBuilder();
        Set<String> seen = new LinkedHashSet<>();
        for (TestAssertion assertion : result.getTestAssertions()) {
            if (assertion.getStatus() != TestAssertion.Status.FAILED) {
                continue;
            }
            RuleId rule = assertion.getRuleId();
            String line = String.format("%n       %s-%d: %s", rule.getClause(), rule.getTestNumber(),
                    assertion.getMessage());
            // one line per distinct rule; a rule that fails on forty objects says the same thing forty
            // times and buries every other failure.
            if (seen.add(line)) {
                out.append(line);
                if (assertion.getLocation() != null) {
                    out.append(String.format("%n              at %s", assertion.getLocation().getContext()));
                }
            }
        }
        return out.toString();
    }
}
