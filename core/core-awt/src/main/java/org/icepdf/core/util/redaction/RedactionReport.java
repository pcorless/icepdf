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

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What a redaction removed, and anything about it the caller should know.
 * <p>
 * Redaction is the one operation here where quiet partial success is a failure - a caller told
 * nothing assumes everything went - so this exists to be read, not merely to exist. It is a plain
 * data object: keep it, hand it to an audit system, or write it beside the redacted file with
 * {@link #toJson()}.
 * <p>
 * <b>Do not write it into the redacted PDF.</b> It names what was removed, so putting it in
 * {@code /Info}, XMP or an embedded file puts the content straight back into the document that is
 * supposed to be rid of it.
 * <p>
 * Read {@link RedactionConfidence#VERIFIED} before treating a confidence as a guarantee: it sets out
 * precisely what the verification pass checked, and the one case - a rectangle over a long span
 * whose individual words recur elsewhere - where a clean result is narrower than it looks.
 *
 * @since 7.5.0
 */
public class RedactionReport {

    private int glyphsRemoved;
    private int imagesBurned;
    private int imagesReEncoded;
    private int stringsRewritten;
    private final Map<RedactionTarget, Integer> countsByTarget = new EnumMap<>(RedactionTarget.class);
    private final List<RedactionWarning> warnings = new ArrayList<>();

    // verification, filled in after the document has been written
    private RedactionConfidence confidence;
    private final Map<String, Integer> hitsBeforeByTerm = new LinkedHashMap<>();
    private final Map<String, Integer> hitsAfterByTerm = new LinkedHashMap<>();
    private int rawByteMatchesAfter;
    private final List<UnverifiableRegion> unverifiableRegions = new ArrayList<>();
    private float score;

    // What the burn actually took out, used to give the verification something to search for when
    // the redaction was driven by annotations and there are no terms. Deliberately not exposed:
    // it is the redacted content, and a report is meant to be safe to keep.
    private final Set<String> removedText = new LinkedHashSet<>();

    /**
     * @return glyphs removed from content streams
     */
    public int getGlyphsRemoved() {
        return glyphsRemoved;
    }

    /**
     * @return images that had a redaction burned into their pixels
     */
    public int getImagesBurned() {
        return imagesBurned;
    }

    /**
     * How many burned images came back out with a different filter than they arrived with.
     * <p>
     * A burn re-encodes whatever it touches and the encoder follows the original filter rather than
     * preserving it, so a JPEG returns as Flate RGB and a JBIG2 as CCITT. The redaction is unaffected
     * - the removed area is gone and the rest of the image is intact - which is why this is a count
     * and not a warning: it does not lower the confidence, and it should not, or a document could
     * never come back {@code VERIFIED} for redacting a photograph.
     * <p>
     * It is reported because it explains a real change to the document. An image arriving as JPEG
     * and leaving several times larger is a surprise worth having accounted for when someone
     * compares the file before and after.
     *
     * @return burned images whose filter changed, of {@link #getImagesBurned()}
     */
    public int getImagesReEncoded() {
        return imagesReEncoded;
    }

    /**
     * @return positionless strings replaced with the mask - outline titles, field values and the like
     */
    public int getStringsRewritten() {
        return stringsRewritten;
    }

    /**
     * @return how much was removed from each kind of content
     */
    public Map<RedactionTarget, Integer> getCountsByTarget() {
        return Collections.unmodifiableMap(countsByTarget);
    }

    /**
     * @return everything that degraded rather than failing, in the order it happened
     */
    public List<RedactionWarning> getWarnings() {
        return Collections.unmodifiableList(warnings);
    }

    /**
     * How much the verification pass could establish. Null when verification did not run, either
     * because it was switched off or because the document was never written.
     *
     * @return the confidence level, or null
     */
    public RedactionConfidence getConfidence() {
        return confidence;
    }

    /**
     * How many times each term appeared before the redaction. Keyed by the term itself, or by a
     * salted hash of it when {@link RedactionOptions#isHashTermsInReport()} is set - a report keyed
     * by plaintext is as sensitive as the document it describes.
     *
     * @return occurrences per term before redacting
     */
    public Map<String, Integer> getHitsBeforeByTerm() {
        return Collections.unmodifiableMap(hitsBeforeByTerm);
    }

    /**
     * How many times each term could still be found afterwards. Anything above zero is a leak and
     * forces {@link RedactionConfidence#FAILED}.
     *
     * @return occurrences per term after redacting
     */
    public Map<String, Integer> getHitsAfterByTerm() {
        return Collections.unmodifiableMap(hitsAfterByTerm);
    }

    /**
     * Occurrences found by scanning the written bytes rather than by searching the document.
     * <p>
     * This is the check that catches what extraction cannot see: a string left in a content stream
     * without its operator is never shown and never extracted, but it is still in the file.
     *
     * @return byte-level occurrences after redacting
     */
    public int getRawByteMatchesAfter() {
        return rawByteMatchesAfter;
    }

    /**
     * @return parts of the document the verification could not check
     */
    public List<UnverifiableRegion> getUnverifiableRegions() {
        return Collections.unmodifiableList(unverifiableRegions);
    }

    /**
     * A number for sorting a batch, not a probability: 1 for a clean verified result, lower as
     * warnings and unverifiable regions accumulate, 0 for a failure.
     * <p>
     * Threshold on it to triage a corpus, but read {@link #getConfidence()} to decide about a
     * document. A failure is never reachable by a low score - it is set by a concrete surviving
     * match - so no threshold can hide one.
     *
     * @return score between 0 and 1
     */
    public float getScore() {
        return score;
    }

    /**
     * @return true when the redaction removed nothing at all, which is worth a second look if the
     * caller expected otherwise
     */
    public boolean isEmpty() {
        return glyphsRemoved == 0 && imagesBurned == 0 && stringsRewritten == 0;
    }

    /**
     * The report as JSON, for writing beside the redacted file or handing to an audit trail.
     * <p>
     * Hand-built rather than pulled from a JSON library: core has no such dependency, and the shape
     * here is a handful of numbers and strings.
     *
     * @return a JSON object
     */
    public String toJson() {
        StringBuilder json = new StringBuilder(256);
        json.append("{\n");
        json.append("  \"glyphsRemoved\": ").append(glyphsRemoved).append(",\n");
        json.append("  \"imagesBurned\": ").append(imagesBurned).append(",\n");
        json.append("  \"imagesReEncoded\": ").append(imagesReEncoded).append(",\n");
        json.append("  \"stringsRewritten\": ").append(stringsRewritten).append(",\n");
        json.append("  \"countsByTarget\": {");
        boolean first = true;
        for (Map.Entry<RedactionTarget, Integer> entry : countsByTarget.entrySet()) {
            json.append(first ? "\n" : ",\n");
            json.append("    \"").append(entry.getKey()).append("\": ").append(entry.getValue());
            first = false;
        }
        json.append(first ? "}" : "\n  }").append(",\n");
        json.append("  \"confidence\": ").append(confidence == null ? "null" : "\"" + confidence + "\"")
                .append(",\n");
        json.append("  \"score\": ").append(confidence == null ? "null" : String.valueOf(score)).append(",\n");
        json.append("  \"rawByteMatchesAfter\": ").append(rawByteMatchesAfter).append(",\n");
        json.append("  \"hitsBeforeByTerm\": ").append(termMapJson(hitsBeforeByTerm)).append(",\n");
        json.append("  \"hitsAfterByTerm\": ").append(termMapJson(hitsAfterByTerm)).append(",\n");
        json.append("  \"unverifiableRegions\": [");
        boolean firstRegion = true;
        for (UnverifiableRegion region : unverifiableRegions) {
            json.append(firstRegion ? "\n" : ",\n");
            json.append("    {\"reason\": \"").append(region.getReason()).append("\", \"detail\": \"")
                    .append(escape(region.getDetail())).append("\"}");
            firstRegion = false;
        }
        json.append(firstRegion ? "]" : "\n  ]").append(",\n");
        json.append("  \"warnings\": [");
        first = true;
        for (RedactionWarning warning : warnings) {
            json.append(first ? "\n" : ",\n");
            json.append("    {\"kind\": \"").append(warning.getKind()).append("\", \"detail\": \"")
                    .append(escape(warning.getDetail())).append("\"}");
            first = false;
        }
        json.append(first ? "]" : "\n  ]").append("\n}");
        return json.toString();
    }

    @Override
    public String toString() {
        return "RedactionReport{" + (confidence != null ? confidence + ", " : "")
                + "glyphsRemoved=" + glyphsRemoved + ", imagesBurned=" + imagesBurned
                + ", stringsRewritten=" + stringsRewritten + ", warnings=" + warnings.size() + "}";
    }

    // -- recording -----------------------------------------------------------------------------
    // Public only because the redaction runs across several packages and Java has no way to say
    // "these are for the implementation". A caller reads this object; it has no reason to write to
    // it, and writing to it only makes the report describe something that did not happen.

    /**
     * Records glyphs removed from a content stream. Called by the redaction implementation.
     *
     * @param count  glyphs removed
     * @param target what kind of content they came from
     */
    public void recordGlyphsRemoved(int count, RedactionTarget target) {
        glyphsRemoved += count;
        countsByTarget.merge(target, count, Integer::sum);
    }

    /**
     * Records an image that had a redaction burned into it. Called by the redaction implementation.
     *
     * @param target what kind of content it was
     */
    public void recordImageBurned(RedactionTarget target) {
        imagesBurned++;
        countsByTarget.merge(target, 1, Integer::sum);
    }

    /**
     * Records a burned image whose filter changed on the way out. Called by the redaction
     * implementation.
     */
    public void recordImageReEncoded() {
        imagesReEncoded++;
    }

    /**
     * Records a positionless string replaced with the mask. Called by the redaction implementation.
     *
     * @param target what kind of content it was
     */
    public void recordStringRewritten(RedactionTarget target) {
        stringsRewritten++;
        countsByTarget.merge(target, 1, Integer::sum);
    }

    /**
     * Records something that degraded rather than failing. Called by the redaction implementation.
     *
     * @param kind   what kind of thing went not-quite-right
     * @param detail what specifically happened
     */
    public void warn(RedactionWarning.Kind kind, String detail) {
        warnings.add(new RedactionWarning(kind, detail));
    }

    /**
     * Records text the burn took out, so the verification has something to look for when the
     * redaction was driven by annotations rather than terms. Called by the redaction implementation.
     *
     * @param text a run of removed characters
     */
    public void recordRemovedText(String text) {
        if (text != null && !text.trim().isEmpty()) {
            removedText.add(text.trim());
        }
    }

    /**
     * @return what the burn removed, for the verification pass to search for
     */
    public Set<String> getRemovedText() {
        return Collections.unmodifiableSet(removedText);
    }

    /**
     * Records what the verification pass established. Called by {@link RedactionVerifier}.
     */
    public void recordVerification(RedactionConfidence confidence, float score,
                                   Map<String, Integer> hitsBefore, Map<String, Integer> hitsAfter,
                                   int rawByteMatches, List<UnverifiableRegion> regions) {
        this.confidence = confidence;
        this.score = score;
        this.hitsBeforeByTerm.putAll(hitsBefore);
        this.hitsAfterByTerm.putAll(hitsAfter);
        this.rawByteMatchesAfter = rawByteMatches;
        this.unverifiableRegions.addAll(regions);
    }

    private static String termMapJson(Map<String, Integer> counts) {
        if (counts.isEmpty()) {
            return "{}";
        }
        StringBuilder json = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            json.append(first ? "\n" : ",\n");
            json.append("    \"").append(escape(entry.getKey())).append("\": ").append(entry.getValue());
            first = false;
        }
        return json.append("\n  }").toString();
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }
}
