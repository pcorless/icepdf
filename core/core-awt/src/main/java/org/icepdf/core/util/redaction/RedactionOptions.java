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

import java.awt.*;
import java.util.EnumSet;
import java.util.Set;

/**
 * How a redaction should behave. Every value has a default that is safe for the common case, so a
 * caller who wants the usual thing can use {@link #defaults()} and stop reading.
 * <p>
 * Settings return {@code this} so they chain:
 * <pre>
 *     RedactionOptions options = RedactionOptions.defaults()
 *             .maskString("[removed]")
 *             .targets(EnumSet.of(RedactionTarget.PAGE_CONTENT, RedactionTarget.IMAGES));
 * </pre>
 *
 * @since 7.5.0
 */
public class RedactionOptions {

    /**
     * Any overlap at all flags a glyph. A redaction drawn snugly over a word does not fully contain
     * the glyph bounds - those carry ascender, descender and side-bearing slack - so requiring
     * containment leaves the text in the file with the annotation merely painted over it.
     */
    public static final float ANY_INTERSECTION = 0f;

    /**
     * The default: a glyph goes when the redaction covers a quarter of its height. A redaction drawn
     * over a word covers far more than that, while the sliver where a neighbouring line's glyph
     * bounds overlap it under tight leading (about a tenth) does not.
     */
    public static final float DEFAULT_GLYPH_COVERAGE = 0.25f;

    private float glyphCoverageThreshold = DEFAULT_GLYPH_COVERAGE;
    private String maskString = "****";
    private Color redactionColor = Color.BLACK;
    private Set<RedactionTarget> targets = EnumSet.allOf(RedactionTarget.class);
    private boolean verify = true;
    private boolean hashTermsInReport;
    private boolean removeAttachments = true;

    private RedactionOptions() {
    }

    /**
     * @return options that redact everything, mask with {@code ****}, and remove a glyph a quarter
     * covered (see {@link #glyphCoverageThreshold})
     */
    public static RedactionOptions defaults() {
        return new RedactionOptions();
    }

    /**
     * How much of a glyph a redaction must cover before the glyph is removed, as a fraction of the
     * glyph's height (where the two overlap across).
     * <p>
     * Glyph bounds carry the font's ascent and descent, so where lines are set tightly the bounds of
     * neighbouring lines overlap, and a redaction sized to a word reaches into the lines above and
     * below it. {@link #DEFAULT_GLYPH_COVERAGE}, the default, passes over that sliver.
     * {@link #ANY_INTERSECTION} removes a glyph the redaction touches at all, erring towards
     * over-redacting; a higher threshold risks leaving a partly covered glyph in the file.
     *
     * @param glyphCoverageThreshold fraction between 0 and 1
     * @return this
     */
    public RedactionOptions glyphCoverageThreshold(float glyphCoverageThreshold) {
        if (glyphCoverageThreshold < 0 || glyphCoverageThreshold > 1) {
            throw new IllegalArgumentException(
                    "glyph coverage threshold is a fraction of a glyph's height, got " + glyphCoverageThreshold);
        }
        this.glyphCoverageThreshold = glyphCoverageThreshold;
        return this;
    }

    public float getGlyphCoverageThreshold() {
        return glyphCoverageThreshold;
    }

    /**
     * What replaces a redacted string that has no position and so cannot be burned - an outline
     * title, a field value.
     * <p>
     * The default is a fixed {@code ****} regardless of how much was removed. A mask that matched
     * the length of what it replaced would leak the length of every redacted term, which for names,
     * account numbers and dates is a meaningful amount of information.
     *
     * @param maskString replacement text, or empty to remove the matched text outright
     * @return this
     */
    public RedactionOptions maskString(String maskString) {
        this.maskString = maskString != null ? maskString : "";
        return this;
    }

    public String getMaskString() {
        return maskString;
    }

    /**
     * Colour burned into images, and painted over redacted areas.
     *
     * @param redactionColor colour to burn, black by default
     * @return this
     */
    public RedactionOptions redactionColor(Color redactionColor) {
        this.redactionColor = redactionColor != null ? redactionColor : Color.BLACK;
        return this;
    }

    /**
     * The colour to burn where a redaction does not name one of its own.
     * <p>
     * A redaction annotation carries a colour, and that colour is what the viewer draws its marker
     * in; it wins, so the marker and the pixels underneath it cannot disagree. This covers the
     * annotations that carry no {@code /C} - which is what building them from search hits produces,
     * and so is the colour a headless workflow actually gets.
     *
     * @return the fallback redaction colour
     */
    public Color getRedactionColor() {
        return redactionColor;
    }


    /**
     * Which kinds of content to redact. All of them by default; narrowing this is how a caller opts
     * out of, say, rewriting metadata.
     *
     * @param targets targets to redact
     * @return this
     */
    public RedactionOptions targets(Set<RedactionTarget> targets) {
        this.targets = targets != null && !targets.isEmpty()
                ? EnumSet.copyOf(targets) : EnumSet.allOf(RedactionTarget.class);
        return this;
    }

    public Set<RedactionTarget> getTargets() {
        return EnumSet.copyOf(targets);
    }

    /**
     * Whether to check the written document afterwards and report what that established.
     * <p>
     * On by default: a redaction that silently did less than it claimed is the failure that matters,
     * and nothing else in the process would notice. It costs a re-open and a text extraction of the
     * result, so turn it off for a bulk job that verifies some other way.
     *
     * @param verify true to verify, the default
     * @return this
     */
    public RedactionOptions verify(boolean verify) {
        this.verify = verify;
        return this;
    }

    public boolean isVerify() {
        return verify;
    }

    /**
     * Whether the report identifies terms by a salted hash rather than by the term itself.
     * <p>
     * A report keyed by plaintext lists exactly what was worth removing, which makes it as sensitive
     * as the document it describes - awkward, since the point of a report is to be kept and shared.
     * Hashing lets it be filed anywhere while still telling you which term a count belongs to across
     * a batch.
     *
     * @param hashTermsInReport true to hash, false by default
     * @return this
     */
    public RedactionOptions hashTermsInReport(boolean hashTermsInReport) {
        this.hashTermsInReport = hashTermsInReport;
        return this;
    }

    public boolean isHashTermsInReport() {
        return hashTermsInReport;
    }

    /**
     * Whether attached files are removed from a redacted document.
     * <p>
     * On by default, and for the same reason a redacted page's thumbnail is dropped: an attachment
     * is an arbitrary file - a spreadsheet, another PDF, an image - and there is no general way to
     * find a term inside one, let alone mask it. It is usually the source of the document it is
     * attached to, so leaving it is the plainest possible leak, and any redaction it needed cannot
     * be done here. Removal is recorded in the report either way.
     * <p>
     * Turn it off when the attachments are known to be safe and worth keeping; the report then
     * carries an unverifiable region for them instead, since the pass cannot see inside.
     *
     * @param removeAttachments true to remove embedded files, the default
     * @return this
     */
    public RedactionOptions removeAttachments(boolean removeAttachments) {
        this.removeAttachments = removeAttachments;
        return this;
    }

    public boolean isRemoveAttachments() {
        return removeAttachments;
    }

    /**
     * @param target target to test
     * @return true when this target is in scope
     */
    public boolean redacts(RedactionTarget target) {
        return targets.contains(target);
    }
}
