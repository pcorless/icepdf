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
package org.icepdf.core.util.redaction;

import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.Page;
import org.icepdf.core.pobjects.Reference;
import org.icepdf.core.pobjects.Stream;
import org.icepdf.core.pobjects.structure.CrossReference;
import org.icepdf.core.pobjects.structure.CrossReferenceCompressedEntry;
import org.icepdf.core.pobjects.structure.CrossReferenceEntry;
import org.icepdf.core.search.SearchTerm;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Checks a redacted document and records what that established.
 * <p>
 * The point is not to reassure. A redaction that quietly did less than it claimed is the failure
 * that matters, and nothing else in the process notices: the burn reports what it believes it
 * removed, which is exactly the thing not to trust. So this reads the <em>written bytes</em> - not
 * the in-memory document, which would only re-report the state the burn just produced - and looks
 * for what should be gone.
 * <p>
 * Two checks, because they catch different things:
 * <ul>
 * <li><b>Searching the reopened document</b> proves the text is not <em>reachable</em>.</li>
 * <li><b>Scanning the bytes</b> proves it is not <em>present</em>. A string left in a content stream
 *     without its operator is never shown and never extracted, so a search reports it clean while it
 *     sits in the file for anyone who opens it in an editor.</li>
 * </ul>
 * What it cannot check is recorded too, as {@link UnverifiableRegion}s. A pass that folded "I could
 * not look here" into "I found nothing" would report a clean result for a document it never examined.
 * <p>
 * Note the question being asked: <em>is this term still in the document</em>, not <em>were the
 * targets I configured processed</em>. Narrowing the scope and leaving the word on a page is
 * therefore reported as a failure. That is deliberate - somebody reading this report wants to know
 * whether the file is safe to release, and "the parts you asked for went" does not answer that.
 *
 * @since 7.5.0
 */
public class RedactionVerifier {

    private static final Logger logger = Logger.getLogger(RedactionVerifier.class.getName());

    /**
     * Penalty per warning or unverifiable region, applied to the batch-triage score. Chosen so a
     * handful of them lands the document well below anything a reasonable threshold would pass.
     */
    private static final float PENALTY = 0.1f;

    private RedactionVerifier() {
    }

    /**
     * Verifies a written document and records the result on the report.
     *
     * @param writtenFile the redacted document as it was written
     * @param original    the document before redaction, for counting what was there to start with
     * @param request     what was asked for
     * @param report      report to record the verification on
     */
    public static void verify(Path writtenFile, Document original, RedactionRequest request,
                              RedactionReport report) {
        List<String> terms = namedTerms(request);
        List<String> searchFor = whatToLookFor(terms, report);
        Map<String, Integer> before = new LinkedHashMap<>();
        Map<String, Integer> after = new LinkedHashMap<>();
        List<UnverifiableRegion> regions = new ArrayList<>();
        int rawMatches = 0;
        int leaks = 0;

        String salt = request.getOptions().isHashTermsInReport() ? newSalt() : null;

        if (searchFor.isEmpty()) {
            regions.add(new UnverifiableRegion(UnverifiableRegion.Reason.NOTHING_TO_SEARCH_FOR,
                    "The redaction removed nothing that could be named, so there is nothing to " +
                            "search the result for"));
        }

        try {
            byte[] writtenBytes = Files.readAllBytes(writtenFile);
            String writtenText = allText(writtenBytes, regions);
            String writtenStreams = allStreams(writtenBytes, regions);
            String writtenObjectStreams = allObjectStreams(writtenBytes, regions);
            String originalText = textOf(original, regions);
            String originalStreams = null;

            for (String term : searchFor) {
                String key = salt != null ? hash(term, salt) : term;
                int had = countIn(originalText, term);
                int hits = countIn(writtenText, term);
                before.put(key, had);
                after.put(key, hits);
                if (terms.contains(term)) {
                    // named: it should be gone from the whole document.
                    int raw = countInSyntax(writtenStreams, term) + countRawBytes(writtenBytes, term)
                            + countRawBytes(writtenObjectStreams.getBytes(StandardCharsets.ISO_8859_1), term);
                    rawMatches += raw;
                    leaks += hits + raw;
                } else {
                    // only what a redaction took out somewhere: the same text elsewhere stays, so it
                    // leaked if there is as much left as there was. (The removed text is a set: one
                    // removal is assumed, which can only find a leak, never miss one.)
                    if (originalStreams == null) originalStreams = streamsOf(original, regions);
                    int streamLeak = Math.max(0, countInSyntax(writtenStreams, term)
                            - Math.max(0, countInSyntax(originalStreams, term) - 1));
                    rawMatches += streamLeak;
                    leaks += Math.max(0, hits - Math.max(0, had - 1)) + streamLeak;
                }
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Redaction verification could not read the written document", e);
            regions.add(new UnverifiableRegion(UnverifiableRegion.Reason.STREAM_NOT_PARSED,
                    "The written document could not be re-read: " + e));
        }

        // An image burn leaves nothing to search for: there was never any text in a raster.
        if (report.getImagesBurned() > 0) {
            regions.add(new UnverifiableRegion(UnverifiableRegion.Reason.RASTER_CONTENT,
                    report.getImagesBurned() + " image(s) were burned; pixels can be confirmed " +
                            "changed but not that the right pixels changed"));
        }

        RedactionConfidence confidence = judge(leaks, report.getWarnings().size(), regions.size());
        report.recordVerification(confidence, scoreFor(confidence, report.getWarnings().size(),
                regions.size()), before, after, rawMatches, regions);
    }

    /**
     * What the result should be searched for: the terms if there were any, otherwise whatever the
     * burn recorded taking out.
     */
    private static List<String> whatToLookFor(List<String> terms, RedactionReport report) {
        List<String> searchFor = new ArrayList<>(terms);
        // A redaction driven by annotations never named anything, so the only account of what should
        // be gone is what the burn took out.
        for (String removed : report.getRemovedText()) {
            if (!searchFor.contains(removed)) searchFor.add(removed);
        }
        return searchFor;
    }

    private static List<String> namedTerms(RedactionRequest request) {
        List<String> terms = new ArrayList<>();
        for (SearchTerm term : request.getTerms()) {
            if (term.getTerm() != null && !term.getTerm().isEmpty() && !terms.contains(term.getTerm())) {
                terms.add(term.getTerm());
            }
        }
        return terms;
    }

    private static RedactionConfidence judge(int leaks, int warnings, int unverifiable) {
        if (leaks > 0) {
            return RedactionConfidence.FAILED;
        }
        if (unverifiable > 0) {
            return RedactionConfidence.UNVERIFIED;
        }
        return warnings > 0 ? RedactionConfidence.VERIFIED_WITH_WARNINGS : RedactionConfidence.VERIFIED;
    }

    private static float scoreFor(RedactionConfidence confidence, int warnings, int unverifiable) {
        if (confidence == RedactionConfidence.FAILED) {
            return 0f;
        }
        return Math.max(0f, 1f - PENALTY * (warnings + unverifiable));
    }

    private static String textOf(Document document, List<UnverifiableRegion> regions) {
        StringBuilder text = new StringBuilder();
        for (int i = 0, max = document.getNumberOfPages(); i < max; i++) {
            try {
                Page page = document.getPageTree().getPage(i);
                page.init();
                text.append(page.getViewText().toString()).append('\n');
            } catch (Exception e) {
                regions.add(new UnverifiableRegion(UnverifiableRegion.Reason.STREAM_NOT_PARSED,
                        "Page " + i + " could not be read: " + e));
            }
        }
        return text.toString();
    }

    private static String allText(byte[] pdf, List<UnverifiableRegion> regions) throws Exception {
        Document document = new Document();
        document.setByteArray(pdf, 0, pdf.length, "verification");
        try {
            return textOf(document, regions);
        } finally {
            document.dispose();
        }
    }

    /**
     * Every content stream of the written document, decompressed. This is where a string that is
     * present but never shown hides: the filters mean a scan of the raw file cannot see it.
     */
    private static String allStreams(byte[] pdf, List<UnverifiableRegion> regions) throws Exception {
        Document document = new Document();
        document.setByteArray(pdf, 0, pdf.length, "verification");
        try {
            return streamsOf(document, regions);
        } finally {
            document.dispose();
        }
    }

    /**
     * Every object stream of the written document, decompressed. The objects a scan of the raw file
     * is there for - metadata, an annotation's strings, names - are compressed when the writer packs
     * them into object streams, and the raw scan can't see them there.
     */
    private static String allObjectStreams(byte[] pdf, List<UnverifiableRegion> regions) throws Exception {
        Document document = new Document();
        document.setByteArray(pdf, 0, pdf.length, "verification");
        try {
            Set<Reference> objectStreams = new LinkedHashSet<>();
            for (CrossReference crossReference :
                    document.getCatalog().getLibrary().getCrossReferenceRoot().getCrossReferences()) {
                for (CrossReferenceEntry entry : crossReference.getEntries().values()) {
                    if (entry instanceof CrossReferenceCompressedEntry) {
                        objectStreams.add(((CrossReferenceCompressedEntry) entry)
                                .getObjectNumberOfContainingObjectStream());
                    }
                }
            }
            StringBuilder objects = new StringBuilder();
            for (Reference reference : objectStreams) {
                Object stream = document.getCatalog().getLibrary().getObject(reference);
                if (stream instanceof Stream) {
                    objects.append(new String(((Stream) stream).getDecodedStreamBytes(),
                            StandardCharsets.ISO_8859_1));
                } else {
                    regions.add(new UnverifiableRegion(UnverifiableRegion.Reason.STREAM_NOT_PARSED,
                            "Object stream " + reference + " could not be read"));
                }
            }
            return objects.toString();
        } finally {
            document.dispose();
        }
    }

    private static String streamsOf(Document document, List<UnverifiableRegion> regions) {
        StringBuilder streams = new StringBuilder();
        for (int i = 0, max = document.getNumberOfPages(); i < max; i++) {
            try {
                Page page = document.getPageTree().getPage(i);
                page.init();
                for (Stream stream : page.getContentStreams()) {
                    streams.append(new String(stream.getDecodedStreamBytes(),
                            StandardCharsets.ISO_8859_1));
                }
            } catch (Exception e) {
                regions.add(new UnverifiableRegion(UnverifiableRegion.Reason.STREAM_NOT_PARSED,
                        "Content stream of page " + i + " could not be decoded: " + e));
            }
        }
        return streams.toString();
    }

    /**
     * Scans the file as bytes, in both of the encodings PDF writes text in. Catches what is stored
     * uncompressed and outside any page: metadata, names, an annotation's own strings.
     */
    private static int countRawBytes(byte[] pdf, String term) {
        return countInSyntax(new String(pdf, StandardCharsets.ISO_8859_1), term)
                + countInSyntax(new String(pdf, StandardCharsets.UTF_16BE), term);
    }

    /**
     * {@link #countIn}, for PDF syntax rather than text: a match that is only part of a longer
     * number token isn't the term. A year or an amount is otherwise found inside the coordinates of
     * any drawing ("1949" in {@code 787.05 444.19495 l}) and in the cross-reference table, and every
     * redaction of a number fails. Only a bare token is passed over - delimited by white space or
     * syntax - so a number inside a string, {@code (19495)}, still counts.
     */
    static int countInSyntax(String haystack, String needle) {
        if (haystack == null || needle == null || needle.isEmpty()) {
            return 0;
        }
        boolean numeric = needle.chars().allMatch(RedactionVerifier::isNumberChar);
        int count = 0;
        for (int i = haystack.indexOf(needle); i >= 0; i = haystack.indexOf(needle, i + needle.length())) {
            if (!numeric || !insideNumberToken(haystack, i, i + needle.length())) {
                count++;
            }
        }
        return count;
    }

    /** True when [start, end) is part of a longer number token, delimited by white space or syntax. */
    private static boolean insideNumberToken(String s, int start, int end) {
        int from = start;
        while (from > 0 && isNumberChar(s.charAt(from - 1))) from--;
        if (from > 0 && (s.charAt(from - 1) == '-' || s.charAt(from - 1) == '+')) from--;
        int to = end;
        while (to < s.length() && isNumberChar(s.charAt(to))) to++;
        if (from == start && to == end) {
            return false; // the whole token: the term itself
        }
        return (from == 0 || isTokenEdge(s.charAt(from - 1))) && (to == s.length() || isTokenEdge(s.charAt(to)));
    }

    private static boolean isNumberChar(int c) {
        return c >= '0' && c <= '9' || c == '.';
    }

    // white space and the delimiters that end a number token; not ( and ), which would put it in a string.
    private static boolean isTokenEdge(char c) {
        return c == ' ' || c == '\n' || c == '\r' || c == '\t' || c == '\f' || c == 0
                || c == '[' || c == ']' || c == '/' || c == '<' || c == '>' || c == '{' || c == '}' || c == '%';
    }

    private static int countIn(String haystack, String needle) {
        if (haystack == null || needle == null || needle.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (int i = haystack.indexOf(needle); i >= 0; i = haystack.indexOf(needle, i + needle.length())) {
            count++;
        }
        return count;
    }

    private static String newSalt() {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        return Base64.getEncoder().encodeToString(salt);
    }

    /**
     * A salted hash, so a report can name which term a count belongs to without carrying the term.
     * The salt is per report: the same word in two documents hashes differently, which stops a
     * collection of reports being mined for the vocabulary of what people redact.
     */
    private static String hash(String term, String salt) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(salt.getBytes(StandardCharsets.UTF_8));
            byte[] hashed = digest.digest(term.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().withoutPadding().encodeToString(hashed).substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is required of every Java runtime; if it is missing something is very wrong.
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
