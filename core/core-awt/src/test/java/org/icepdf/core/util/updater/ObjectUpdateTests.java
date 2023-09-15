package org.icepdf.core.util.updater;


import org.icepdf.core.exceptions.PDFSecurityException;
import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.Page;
import org.icepdf.core.pobjects.Reference;
import org.icepdf.core.pobjects.annotations.Annotation;
import org.icepdf.core.pobjects.annotations.AnnotationFactory;
import org.icepdf.core.pobjects.annotations.TextAnnotation;
import org.icepdf.core.util.Library;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.*;
import java.io.*;

import static org.junit.jupiter.api.Assertions.*;

public class ObjectUpdateTests {
    @DisplayName("xrefStream - delete object and write full document update")
    @Test
    public void testXrefTableFullUpdate() {
        try {
            Document document = new Document();
            InputStream fileUrl = ObjectUpdateTests.class.getResourceAsStream("/updater/R&D-05-Carbon.pdf");
            document.setInputStream(fileUrl, "R&D-05-Carbon.pdf");

            Page page = document.getPageTree().getPage(2);
            Reference deletedPageReference = page.getPObjectReference();
            document.deletePage(page);

            File out = new File("./src/test/out/ObjectUpdateTest_testXrefTableFullUpdate.pdf");

            try (BufferedOutputStream stream = new BufferedOutputStream(new FileOutputStream(out), 8192)) {
                long length = document.saveToOutputStream(stream, WriteMode.FULL_UPDATE);

                // test for length 142246
                assertEquals(146775, length);
            }
            Document modifiedDocument = new Document();
            modifiedDocument.setFile(out.getAbsolutePath());

            // check pages length
            assertEquals(2, modifiedDocument.getNumberOfPages());

            // make sure page is no longer found.
            Library library = document.getCatalog().getLibrary();
            Object deletedPage = library.getObject(deletedPageReference);
            assertNull(deletedPage);
        } catch (PDFSecurityException | IOException | InterruptedException e) {
            // make sure we have no io errors.
            fail("should not be any exceptions");
        }
    }

    @DisplayName("xref table - add annotation object and write full document update")
    @Test
    public void testXrefStreamFullUpdate() {
        try {
            FullUpdater.compressXrefTable = false;

            Document document = new Document();
            InputStream fileUrl = ObjectUpdateTests.class.getResourceAsStream("/updater/CineplexMagazine-2020.pdf");
            document.setInputStream(fileUrl, "CineplexMagazine-2020.pdf");
            Page page = document.getPageTree().getPage(0);

            TextAnnotation textAnnotation = (TextAnnotation)
                    AnnotationFactory.buildAnnotation(
                            document.getPageTree().getLibrary(),
                            Annotation.SUBTYPE_TEXT,
                            new Rectangle(10, 10, 50, 50));

            page.addAnnotation(textAnnotation, true);

            File out = new File("./src/test/out/ObjectUpdateTest_testXrefStreamFullUpdate.pdf");
            try (BufferedOutputStream stream = new BufferedOutputStream(new FileOutputStream(out), 8192)) {
                long length = document.saveToOutputStream(stream, WriteMode.FULL_UPDATE);
                // test for length 142246
                assertEquals(8812459, length);
            }
            Document modifiedDocument = new Document();
            modifiedDocument.setFile(out.getAbsolutePath());

            // check for annotation entry
            Page modifiedPage = document.getPageTree().getPage(0);
            assertEquals(1, modifiedPage.getAnnotations().size());

        } catch (PDFSecurityException | IOException | InterruptedException e) {
            // make sure we have no io errors.
            fail("should not be any exceptions");
        }
    }

}
