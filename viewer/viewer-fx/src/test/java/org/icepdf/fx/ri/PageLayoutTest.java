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
package org.icepdf.fx.ri;

import org.icepdf.core.pobjects.Document;
import org.icepdf.fx.view.ViewMode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PageLayoutTest {

    @DisplayName("each /PageLayout maps to a view mode; the Right layouts put page 1 alone")
    @Test
    void mapping() {
        assertEquals(new PageLayout(ViewMode.SINGLE_PAGE, false), PageLayout.of("SinglePage"));
        assertEquals(new PageLayout(ViewMode.CONTINUOUS, false), PageLayout.of("OneColumn"));
        assertEquals(new PageLayout(ViewMode.FACING_CONTINUOUS, false), PageLayout.of("TwoColumnLeft"));
        assertEquals(new PageLayout(ViewMode.FACING_CONTINUOUS, true), PageLayout.of("TwoColumnRight"));
        assertEquals(new PageLayout(ViewMode.FACING, false), PageLayout.of("TwoPageLeft"));
        assertEquals(new PageLayout(ViewMode.FACING, true), PageLayout.of("TwoPageRight"));
        assertNull(PageLayout.of("Sideways"));
        assertNull(PageLayout.of((String) null));
    }

    @DisplayName("read from the catalog; none when the document doesn't say")
    @Test
    void fromCatalog() throws Exception {
        Document document = new Document();
        document.setFile("src/test/resources/layers/nested_layers.pdf");
        try {
            assertEquals(new PageLayout(ViewMode.FACING, true), PageLayout.of(document.getCatalog()));
        } finally {
            document.dispose();
        }
        Document plain = new Document();
        plain.setFile("../../core/core-awt/src/test/resources/redaction/simple_tj.pdf");
        try {
            assertNull(PageLayout.of(plain.getCatalog()));
        } finally {
            plain.dispose();
        }
    }
}
