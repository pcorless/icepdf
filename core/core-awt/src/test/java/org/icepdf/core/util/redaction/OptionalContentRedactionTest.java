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
import org.icepdf.core.pobjects.Form;
import org.icepdf.core.pobjects.Name;
import org.icepdf.core.pobjects.Page;
import org.icepdf.core.pobjects.Resources;
import org.icepdf.core.pobjects.Stream;
import org.icepdf.core.util.updater.WriteMode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.*;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Redacting a form that sits on an optional-content layer which is turned off.
 * <p>
 * The parser stops at the visibility check before it descends into the form, which is right for
 * painting and wrong for a rewrite: not drawing a stream does not take it out of the file. The text
 * stays there behind a flag anyone can turn back on, and text extraction never needed the flag on in
 * the first place.
 */
public class OptionalContentRedactionTest {

    private static final String FIXTURE = "hidden_layer.pdf";

    /**
     * The hidden form draws "hidden layer secret" over 20,140 to 320,190; the page's own text is
     * lower down and says something else.
     */
    @DisplayName("a redaction reaches text on a layer that is switched off")
    @Test
    public void redactionReachesAHiddenLayer() throws Exception {
        String streams = allStreams(redact(new Rectangle(20, 140, 180, 40)));

        assertFalse(streams.contains("hidden layer secret"),
                "the hidden form should have been redacted:\n" + streams);
        assertTrue(streams.contains("page says alpha"),
                "and the page's own text left alone:\n" + streams);
    }

    /**
     * The control the assertion above needs. Without it "the text is gone" is equally well explained
     * by the check never having been able to see the text at all.
     */
    @DisplayName("a hidden layer clear of the redaction keeps its text")
    @Test
    public void hiddenLayerOutsideTheRedactionSurvives() throws Exception {
        String streams = allStreams(redact(new Rectangle(0, 0, 20, 20)));

        assertTrue(streams.contains("hidden layer secret"),
                "nothing covered it, so it should still be there:\n" + streams);
    }

    // -- helpers ---------------------------------------------------------------------------------

    private byte[] redact(Rectangle area) throws Exception {
        Document document = new Document();
        document.setFile(Paths.get("src/test/resources/redaction/" + FIXTURE).toString());
        try {
            Page page = document.getPageTree().getPage(0);
            page.init();
            page.addAnnotation(RedactionFixtures.redactionOver(document, area), true);
            Redactor.configure(document, RedactionRequest.ofAnnotations());

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.saveToOutputStream(out, WriteMode.FULL_UPDATE);
            return out.toByteArray();
        } finally {
            document.dispose();
        }
    }

    /**
     * The page's content streams and the decoded stream of every form XObject it names.
     * <p>
     * The hidden form's text never reaches the page's text - it is not parsed for rendering at all -
     * so the usual extraction check would report this document clean whatever happened to it.
     */
    private String allStreams(byte[] pdf) throws Exception {
        Document document = new Document();
        document.setByteArray(pdf, 0, pdf.length, "redacted");
        try {
            Page page = document.getPageTree().getPage(0);
            page.init();
            StringBuilder streams = new StringBuilder();
            for (Stream stream : page.getContentStreams()) {
                streams.append(new String(stream.getDecodedStreamBytes(), StandardCharsets.ISO_8859_1));
            }
            Resources resources = page.getResources();
            if (resources != null) {
                for (Name name : resources.getXObjects().keySet()) {
                    Object xObject = resources.getXObject(name);
                    if (xObject instanceof Form) {
                        streams.append(new String(((Form) xObject).getDecodedStreamBytes(),
                                StandardCharsets.ISO_8859_1));
                    }
                }
            }
            return streams.toString();
        } finally {
            document.dispose();
        }
    }
}
