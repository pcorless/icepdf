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
package org.icepdf.examples.redaction;

import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.Page;
import org.icepdf.core.pobjects.annotations.Annotation;
import org.icepdf.core.pobjects.annotations.AnnotationFactory;
import org.icepdf.core.pobjects.annotations.RedactionAnnotation;
import org.icepdf.core.pobjects.graphics.text.WordText;
import org.icepdf.core.search.DocumentSearchController;
import org.icepdf.core.util.redaction.RedactionConfidence;
import org.icepdf.core.util.redaction.RedactionOptions;
import org.icepdf.core.util.redaction.RedactionReport;
import org.icepdf.core.util.redaction.RedactionRequest;
import org.icepdf.core.util.redaction.Redactor;
import org.icepdf.core.util.updater.WriteMode;
import org.icepdf.ri.common.search.DocumentSearchControllerImpl;
import org.icepdf.ri.util.FontPropertiesManager;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The <code>RedactionHeadless</code> class is an example of a complete unattended search-and-redact:
 * a term goes in, a redacted document and a report come out, and the exit code says whether the
 * result can be released without someone looking at it.
 * <p>
 * A redaction has two halves, and using only the first is the usual way to ship a document that
 * still contains the word that was redacted:
 * <ul>
 *     <li><em>Geometric</em> - a search finds the word on the page, a redaction annotation is drawn
 *     over each hit, and the export burns them into the content streams. This is what removes the
 *     text you can see.</li>
 *     <li><em>Term-driven</em> - the same word also sits in the bookmark that points at the section,
 *     the comment somebody left on it, the form field that was filled in from it and the document
 *     title. None of that has a position on any page, so no rectangle reaches it. Handing the search
 *     terms to the redaction lets it rewrite those strings as well.</li>
 * </ul>
 * Both are requested here by {@link RedactionRequest#ofAnnotationsAndTerms}.
 * <p>
 * Redactions are only burned by {@link WriteMode#FULL_UPDATE}. An incremental update can just append
 * to the file, so by its nature it cannot take anything out.
 *
 * @since 7.2.0
 */
public class RedactionHeadless {

    /**
     * The weakest result this pipeline will release without a human. Anything below it exits
     * non-zero, which is what an automated pipeline can actually act on: hold the document and
     * queue it for review. {@link RedactionConfidence} is ordered worst to best, so the comparison
     * reads directly.
     */
    private static final RedactionConfidence MINIMUM_CONFIDENCE = RedactionConfidence.VERIFIED_WITH_WARNINGS;

    private static final String DEFAULT_SEARCH_TERM = "redaction";

    public static void main(String[] args) {

        FontPropertiesManager.getInstance().loadOrReadSystemFonts();

        // Get a file from the command line to open, and optionally the term to redact.
        String filePath = args[0];
        String searchTerm = args.length > 1 ? args[1] : DEFAULT_SEARCH_TERM;

        Document document = new Document();
        try {
            document.setFile(filePath);

            // Get the search controller and add the terms to redact.
            DocumentSearchController searchController = new DocumentSearchControllerImpl(document);
            searchController.addSearchTerm(searchTerm, false, false);

            // First axis: a redaction annotation over every hit on every page.
            int hits = markSearchHits(document, searchController);
            System.out.println("Marked " + hits + " occurrence(s) of \"" + searchTerm + "\" for redaction");

            // Second axis: the same terms, for the copies of the word that have no position on any
            // page - bookmark titles, comment text, form values, the document title. Without this
            // the burned document still answers to a search for the word that was redacted.
            RedactionOptions options = RedactionOptions.defaults()
                    // What positionless text is replaced with. The default is "****".
                    .maskString("****")
                    // Attachments cannot be masked - an attachment is an arbitrary file - so the
                    // only safe thing to do with one is drop it. On by default; the report says so.
                    .removeAttachments(true)
                    // Re-read the written file and check the terms really are gone. On by default;
                    // this is what produces the confidence below.
                    .verify(true);
            Redactor.configure(document,
                    RedactionRequest.ofAnnotationsAndTerms(searchController.getSearchTerms())
                            .with(options));

            // Burn the redactions by exporting the document. FULL_UPDATE is required.
            File redactedFile = new File("redacted_output.pdf");
            try (FileOutputStream fileOutputStream = new FileOutputStream(redactedFile);
                 BufferedOutputStream buf = new BufferedOutputStream(fileOutputStream, 8192)) {
                document.writeToOutputStream(buf, WriteMode.FULL_UPDATE);
            }

            // The report is only complete once the file has been written, because the verification
            // pass reads the bytes that were actually produced.
            RedactionReport report = document.getRedactionReport();
            document.dispose();

            System.exit(handleReport(report, redactedFile));

        } catch (Exception e) {
            e.printStackTrace();
            System.exit(2);
        }
    }

    /**
     * Draws a redaction annotation over every occurrence of the search term.
     *
     * @return how many were marked, across the whole document
     */
    private static int markSearchHits(Document document, DocumentSearchController searchController)
            throws InterruptedException {
        int marked = 0;
        for (int i = 0, max = document.getNumberOfPages(); i < max; i++) {
            Page page = document.getPageTree().getPage(i);
            page.init();

            List<WordText> foundWords = searchController.searchPage(i);
            if (foundWords == null || foundWords.isEmpty()) {
                // A page with no hits is ordinary, not a reason to stop: the term is very often on
                // only some of the pages, and the ones after it still need redacting.
                continue;
            }
            for (WordText wordText : foundWords) {
                Rectangle bounds = wordText.getBounds().getBounds();
                RedactionAnnotation redactionAnnotation = (RedactionAnnotation)
                        AnnotationFactory.buildAnnotation(
                                document.getPageTree().getLibrary(),
                                Annotation.SUBTYPE_REDACT,
                                bounds);
                if (redactionAnnotation != null) {
                    redactionAnnotation.setColor(Color.BLACK);
                    redactionAnnotation.setMarkupBounds(new ArrayList<>(Collections.singletonList(bounds)));
                    redactionAnnotation.setMarkupPath(new GeneralPath(bounds));
                    redactionAnnotation.setBBox(bounds);
                    redactionAnnotation.resetAppearanceStream(new AffineTransform());
                    page.addAnnotation(redactionAnnotation, true);
                    marked++;
                }
            }
        }
        return marked;
    }

    /**
     * Writes the report beside the redacted file and decides whether the document can be released.
     * <p>
     * The report is deliberately not written into the PDF: it names what was removed, so putting it
     * in the document would undo the redaction it is reporting on.
     *
     * @return the process exit code - 0 to release, 1 to hold for review
     */
    private static int handleReport(RedactionReport report, File redactedFile) throws Exception {
        Path reportFile = redactedFile.toPath().resolveSibling("redacted_output.report.json");
        Files.write(reportFile, report.toJson().getBytes(StandardCharsets.UTF_8));

        System.out.println("Wrote " + redactedFile + " and " + reportFile.getFileName());
        System.out.println("  glyphs removed:    " + report.getGlyphsRemoved());
        System.out.println("  images burned:     " + report.getImagesBurned());
        System.out.println("  strings rewritten: " + report.getStringsRewritten());
        System.out.println("  hits before:       " + report.getHitsBeforeByTerm());
        System.out.println("  hits after:        " + report.getHitsAfterByTerm());
        System.out.println("  confidence:        " + report.getConfidence() + " (score " + report.getScore() + ")");

        report.getWarnings().forEach(warning -> System.out.println("  warning: " + warning));
        report.getUnverifiableRegions().forEach(region -> System.out.println("  unverifiable: " + region));

        if (report.getConfidence().compareTo(MINIMUM_CONFIDENCE) < 0) {
            System.out.println("HOLD: confidence is below " + MINIMUM_CONFIDENCE + "; do not release this document.");
            return 1;
        }
        return 0;
    }
}
