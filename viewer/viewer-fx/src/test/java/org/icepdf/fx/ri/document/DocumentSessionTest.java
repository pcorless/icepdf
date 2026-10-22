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
package org.icepdf.fx.ri.document;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The session's file naming, no toolkit. */
class DocumentSessionTest {

    @Test
    @DisplayName("withPdfExtension adds .pdf only when the name lacks it, in any case")
    void pdfExtension() {
        assertEquals(Path.of("/tmp/report.pdf"), DocumentSession.withPdfExtension(Path.of("/tmp/report")));
        assertEquals(Path.of("/tmp/report.PDF"), DocumentSession.withPdfExtension(Path.of("/tmp/report.PDF")));
        assertEquals(Path.of("/tmp/report.pdf"), DocumentSession.withPdfExtension(Path.of("/tmp/report.pdf")));
        assertEquals(Path.of("/tmp/report.pdf.bak.pdf"), DocumentSession.withPdfExtension(Path.of("/tmp/report.pdf.bak")));
        assertEquals(Path.of("relative.pdf"), DocumentSession.withPdfExtension(Path.of("relative")));
        assertEquals(Path.of("/"), DocumentSession.withPdfExtension(Path.of("/")));
    }
}
