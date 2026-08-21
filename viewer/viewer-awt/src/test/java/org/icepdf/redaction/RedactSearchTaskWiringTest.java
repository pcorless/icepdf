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
package org.icepdf.redaction;

import org.icepdf.core.pobjects.Document;
import org.icepdf.core.search.SearchTerm;
import org.icepdf.core.util.redaction.RedactionRequest;
import org.icepdf.core.util.redaction.Redactor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The contract between the viewer's search-and-redact task and the core redaction.
 * <p>
 * The task drags a rectangle over every hit, which covers what is drawn on a page and nothing else.
 * The same words are usually also in the bookmark pointing at that page, in comments, in field
 * values and in the document title, where no rectangle reaches. What connects the two is the task
 * recording its search terms on the document, which is what these check - without going near Swing,
 * which is not what is being tested.
 */
public class RedactSearchTaskWiringTest {

    @DisplayName("recording terms leaves a request that covers both axes")
    @Test
    public void recordedTermsCoverBothAxes() throws Exception {
        Document document = new Document();
        document.setFile("src/test/resources/redact/test_print.pdf");
        try {
            Redactor.configure(document, RedactionRequest.ofAnnotationsAndTerms(terms("que")));

            RedactionRequest request = document.getRedactionRequest();
            assertNotNull(request, "the task should leave a request behind for the export to use");
            assertTrue(request.hasTerms(), "and it should carry the terms the search used");
            assertEquals("que", request.getTerms().get(0).getTerm());
        } finally {
            document.dispose();
        }
    }

    @DisplayName("a search with no terms leaves the document unconfigured")
    @Test
    public void noTermsLeavesNoRequest() throws Exception {
        Document document = new Document();
        document.setFile("src/test/resources/redact/test_print.pdf");
        try {
            // Nothing was searched for, so there is nothing to remove by term and the export should
            // behave exactly as it always has.
            assertNull(document.getRedactionRequest());
        } finally {
            document.dispose();
        }
    }

    private List<SearchTerm> terms(String word) {
        ArrayList<String> words = new ArrayList<>();
        words.add(word);
        return Collections.singletonList(new SearchTerm(word, words, false, false, false));
    }
}
