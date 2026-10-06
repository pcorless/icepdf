/*
 * Copyright 2026 Patrick Corless
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
package org.icepdf.core.pobjects.graphics.text;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the Model-B reflow join rules in {@link ExtractionFormat} (de-hyphenation).
 */
public class ExtractionFormatTest {

    @DisplayName("endsWithWordHyphen detects a line-break hyphen after a letter")
    @Test
    public void endsWithWordHyphen() {
        assertTrue(ExtractionFormat.endsWithWordHyphen("acti-"));
        assertTrue(ExtractionFormat.endsWithWordHyphen("sequence-"));
        assertFalse(ExtractionFormat.endsWithWordHyphen("acti"));      // no hyphen
        assertFalse(ExtractionFormat.endsWithWordHyphen("- 1 -"));     // trailing text, not word+hyphen
        assertFalse(ExtractionFormat.endsWithWordHyphen("1-"));        // digit before hyphen
        assertFalse(ExtractionFormat.endsWithWordHyphen("-"));         // too short
    }

    @DisplayName("deHyphenate drops soft split hyphens but keeps real compound hyphens")
    @Test
    public void deHyphenate() {
        // soft split: lowercase continuation, no hyphen in the continuation token -> drop.
        assertTrue(ExtractionFormat.deHyphenate("acti-", "vates them or they call"));
        // compound: continuation token itself hyphenated -> keep.
        assertFalse(ExtractionFormat.deHyphenate("sequence-", "of-events logging mechanism"));
        // capitalised continuation -> keep (e.g. a proper compound like well-Known).
        assertFalse(ExtractionFormat.deHyphenate("well-", "Known author"));
        // not a word hyphen at all.
        assertFalse(ExtractionFormat.deHyphenate("no hyphen here", "next line"));
    }
}
