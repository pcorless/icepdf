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
import org.icepdf.core.util.updater.WriteMode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.*;
import java.io.ByteArrayOutputStream;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Two pages pointing at one {@code /Contents} stream.
 * <p>
 * Burning it redacts both pages, because there is only one stream to burn. That is the intended
 * behaviour and the same trade taken for shared images and forms: pages drawing the same stream draw
 * the same content, and the same content is the same disclosure.
 * <p>
 * It stops holding when the pages do not draw the stream alike - a different {@code /Rotate},
 * {@code /Resources} or page box makes the same operators land somewhere else - and then the caller
 * needs to be told, because the redaction reaches content nobody drew a rectangle over.
 */
public class SharedContentStreamTest {

    @DisplayName("pages that draw a shared stream alike are redacted quietly")
    @Test
    public void sharingIsNotReportedWhenThePagesDrawAlike() throws Exception {
        RedactionReport report = redactFirstPage("shared_content_alike.pdf");

        assertEquals(List.of(), report.getWarnings(),
                "both pages draw the same content, so propagating removes the same thing twice");
    }

    /**
     * The control for the test above, and for the premise of the whole class: silence is only
     * meaningful if the two pages really do share a stream. Redacting page one has to change page
     * two, which nobody drew a rectangle on.
     */
    @DisplayName("redacting one page of a shared stream redacts the other")
    @Test
    public void redactionPropagatesToTheOtherPage() throws Exception {
        byte[] redacted = redactFirstPageBytes("shared_content_alike.pdf");

        Document document = new Document();
        document.setByteArray(redacted, 0, redacted.length, "redacted");
        try {
            Page second = document.getPageTree().getPage(1);
            second.init();
            String text = second.getViewText().toString();
            assertTrue(text.contains("charlie"), "the rest of the line should survive: " + text);
            assertTrue(!text.contains("repeated") && !text.contains("bravo"),
                    "the redacted word should be gone from the page nobody redacted: " + text);
        } finally {
            document.dispose();
        }
    }

    @DisplayName("a shared stream drawn differently is reported")
    @Test
    public void sharingIsReportedWhenThePagesDiffer() throws Exception {
        RedactionReport report = redactFirstPage("shared_content_rotated.pdf");

        assertEquals(1, report.getWarnings().size(),
                "the second page rotates the shared stream: " + report.getWarnings());
        RedactionWarning warning = report.getWarnings().get(0);
        assertEquals(RedactionWarning.Kind.SHARED_OBJECT_BURNED_IN_PLACE, warning.getKind());
        assertTrue(warning.getDetail().contains("/Rotate"),
                "the warning should say what differs: " + warning.getDetail());
        assertTrue(warning.getDetail().contains("1, 2"),
                "and which pages share it: " + warning.getDetail());
    }

    // -- helpers ---------------------------------------------------------------------------------

    private RedactionReport redactFirstPage(String fixture) throws Exception {
        return redact(fixture).report;
    }

    private byte[] redactFirstPageBytes(String fixture) throws Exception {
        return redact(fixture).bytes;
    }

    private static class Result {
        RedactionReport report;
        byte[] bytes;
    }

    private Result redact(String fixture) throws Exception {
        Document document = new Document();
        document.setFile(Paths.get("src/test/resources/redaction/" + fixture).toString());
        try {
            Page page = document.getPageTree().getPage(0);
            page.init();
            page.addAnnotation(RedactionFixtures.redactionOver(document, new Rectangle(20, 145, 45, 20)), true);
            Redactor.configure(document, RedactionRequest.ofAnnotations());

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.saveToOutputStream(out, WriteMode.FULL_UPDATE);
            Result result = new Result();
            result.report = document.getRedactionReport();
            result.bytes = out.toByteArray();
            return result;
        } finally {
            document.dispose();
        }
    }
}
