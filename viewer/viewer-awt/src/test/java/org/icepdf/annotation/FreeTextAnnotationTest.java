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
package org.icepdf.annotation;

import org.icepdf.core.exceptions.PDFSecurityException;
import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.PDate;
import org.icepdf.core.pobjects.Page;
import org.icepdf.core.pobjects.annotations.Annotation;
import org.icepdf.core.pobjects.annotations.AnnotationFactory;
import org.icepdf.core.pobjects.annotations.FreeTextAnnotation;
import org.icepdf.core.util.Library;
import org.icepdf.core.util.SystemProperties;
import org.icepdf.core.util.updater.WriteMode;
import org.icepdf.ri.util.FontPropertiesManager;
import org.icepdf.signing.SigningTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.io.*;
import java.util.Date;

import static org.icepdf.core.pobjects.annotations.FreeTextAnnotation.INSETS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

public class FreeTextAnnotationTest {

    @BeforeAll
    public static void init() {
        FontPropertiesManager.getInstance().loadOrReadSystemFonts();
    }

    @DisplayName("redact simple text and export")
    @Test
    public void testCreateFreeTextAndSave() {
        try {

            Document document = new Document();
            InputStream fileUrl = SigningTest.class.getResourceAsStream("/annotation/hello_pdfa1.pdf");
            document.setInputStream(fileUrl, "test_print.pdf");
            Library library = document.getCatalog().getLibrary();

            Page page = document.getPageTree().getPage(0);

            Rectangle rect = new Rectangle(250, 200, 400, 50);
            Rectangle tBbox = page.convertToPageSpace(rect, Page.BOUNDARY_CROPBOX, 0f, 1.0f);
            tBbox.setLocation(tBbox.x - INSETS, tBbox.y - tBbox.height - INSETS);

            // create annotations types that are rectangle based;
            // which is actually just link annotations
            FreeTextAnnotation annotation = (FreeTextAnnotation)
                    AnnotationFactory.buildAnnotation(
                            library,
                            Annotation.SUBTYPE_FREE_TEXT,
                            tBbox);
            annotation.setCreationDate(PDate.formatDateTime(new Date()));
            annotation.setTitleText(SystemProperties.USER_NAME);
            annotation.setContents("Hello World");

            AffineTransform pageTransform = page.getToPageSpaceTransform(Page.BOUNDARY_CROPBOX, 0f, 1.0f);
            annotation.resetAppearanceStream(pageTransform);

            page.addAnnotation(annotation, true);
            annotation.saveAppearanceStream();

            File out = new File("./src/test/out/FreeText_annotation_write.pdf");
            try (BufferedOutputStream stream = new BufferedOutputStream(new FileOutputStream(out), 64 * 1024)) {
                document.saveToOutputStream(stream, WriteMode.INCREMENT_UPDATE);
            }
            Document modifiedDocument = new Document();
            modifiedDocument.setFile(out.getAbsolutePath());

            // make sure page still has an annotation
            page = modifiedDocument.getPageTree().getPage(0);
            assertEquals(1, page.getAnnotations().size());

            // todo validate PDF/A-1b compliance of the output file.

        } catch (PDFSecurityException | IOException | InterruptedException e) {
            // make sure we have no io errors.
            fail("should not be any exceptions");
        }
    }

}
