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
import org.icepdf.core.util.watermark.Watermark;
import org.icepdf.core.util.watermark.Watermarks;
import org.icepdf.core.util.watermark.WatermarksTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A watermark's appearance stream is shared between pages, and redaction burns the appearance of any
 * annotation it overlaps. Redacting one page mustn't punch the same hole in every page's watermark.
 */
public class WatermarkRedactionTest {

    @DisplayName("redacting over one page's watermark leaves the other pages' watermarks whole")
    @Test
    public void sharedWatermarkSurvivesOnOtherPages() throws Exception {
        Watermark footer = Watermark.of("SAMPLE WATERMARK").withPosition(Watermark.Position.FOOTER)
                .withColor(Color.BLACK).withOpacity(1f).withFontSize(14);
        byte[] watermarked = WatermarksTest.watermarked(WatermarksTest.TWO_PAGES, footer);

        byte[] redacted;
        Document document = WatermarksTest.open(watermarked);
        try {
            Page page = document.getPageTree().getPage(0);
            page.init();
            // across the whole footer of the first page only
            page.addAnnotation(RedactionFixtures.redactionOver(document, new Rectangle(0, 10, 300, 30)));
            Redactor.configure(document, RedactionRequest.ofAnnotations());
            redacted = WatermarksTest.save(document);
        } finally {
            document.dispose();
        }

        BufferedImage secondBefore = WatermarksTest.render(watermarked, 1);
        BufferedImage secondAfter = WatermarksTest.render(redacted, 1);
        assertEquals(0, WatermarksTest.difference(secondBefore, secondAfter)[0],
                "the second page's watermark is unchanged");

        int[] first = WatermarksTest.difference(WatermarksTest.render(watermarked, 0), WatermarksTest.render(redacted, 0));
        assertTrue(first[0] > 0, "the first page's footer was redacted");

        Document reopened = WatermarksTest.open(redacted);
        try {
            assertEquals(2, Watermarks.find(reopened).size(), "both pages still have their watermark annotation");
        } finally {
            reopened.dispose();
        }
    }
}
