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
import java.util.List;
import java.util.Map;

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
 *
 * @since 7.5.0
 */
public class RedactionReport {

    private int glyphsRemoved;
    private int imagesBurned;
    private int stringsRewritten;
    private final Map<RedactionTarget, Integer> countsByTarget = new EnumMap<>(RedactionTarget.class);
    private final List<RedactionWarning> warnings = new ArrayList<>();

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
        json.append("  \"stringsRewritten\": ").append(stringsRewritten).append(",\n");
        json.append("  \"countsByTarget\": {");
        boolean first = true;
        for (Map.Entry<RedactionTarget, Integer> entry : countsByTarget.entrySet()) {
            json.append(first ? "\n" : ",\n");
            json.append("    \"").append(entry.getKey()).append("\": ").append(entry.getValue());
            first = false;
        }
        json.append(first ? "}" : "\n  }").append(",\n");
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
        return "RedactionReport{glyphsRemoved=" + glyphsRemoved + ", imagesBurned=" + imagesBurned
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

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }
}
