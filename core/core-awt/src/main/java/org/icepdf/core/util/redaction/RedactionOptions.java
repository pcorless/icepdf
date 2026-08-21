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

    private float glyphCoverageThreshold = ANY_INTERSECTION;
    private String maskString = "****";
    private Color redactionColor = Color.BLACK;
    private boolean copyOnBurn = true;
    private Set<RedactionTarget> targets = EnumSet.allOf(RedactionTarget.class);
    private boolean verify = true;
    private boolean hashTermsInReport;
    private boolean removeAttachments = true;

    private RedactionOptions() {
    }

    /**
     * @return options that redact everything, mask with {@code ****}, and err towards removing a
     * glyph rather than leaving it
     */
    public static RedactionOptions defaults() {
        return new RedactionOptions();
    }

    /**
     * How much of a glyph a redaction must cover before the glyph is removed, as a fraction of the
     * glyph's area.
     * <p>
     * {@link #ANY_INTERSECTION}, the default, removes a glyph the redaction touches at all. That
     * errs towards over-redacting: where lines are set tightly enough that their glyph bounds
     * overlap, a redaction sized to a word on one line can also clip a glyph on the line above.
     * Raising the threshold trades that back, at the risk of leaving a partly covered glyph in the
     * file.
     *
     * @param glyphCoverageThreshold fraction between 0 and 1
     * @return this
     */
    public RedactionOptions glyphCoverageThreshold(float glyphCoverageThreshold) {
        if (glyphCoverageThreshold < 0 || glyphCoverageThreshold > 1) {
            throw new IllegalArgumentException(
                    "glyph coverage threshold is a fraction of a glyph's area, got " + glyphCoverageThreshold);
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

    public Color getRedactionColor() {
        return redactionColor;
    }

    /**
     * Whether a shared object that is redacted with geometry not applying to all of its uses is
     * copied first.
     * <p>
     * Applies to image XObjects and to content streams shared between pages. It deliberately does
     * <em>not</em> apply to form XObjects: a redaction on a form applies to every placement by
     * design, which is both what a reader intuitively expects and what the search-and-redact
     * workflow wants, since search finds every occurrence anyway.
     *
     * @param copyOnBurn true to copy before burning, the default
     * @return this
     */
    public RedactionOptions copyOnBurn(boolean copyOnBurn) {
        this.copyOnBurn = copyOnBurn;
        return this;
    }

    public boolean isCopyOnBurn() {
        return copyOnBurn;
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
