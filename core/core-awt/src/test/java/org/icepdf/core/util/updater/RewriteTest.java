package org.icepdf.core.util.updater;

import org.icepdf.core.exceptions.PDFSecurityException;
import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.Page;
import org.icepdf.core.pobjects.PageTree;
import org.icepdf.core.pobjects.annotations.Annotation;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

public class RewriteTest {

    @DisplayName("xrefStream - write full document, dropping incremental update")
    @Test
    public void testFullUpdate() {
        try {
            Document document = new Document();
            InputStream fileUrl = ObjectUpdateTests.class.getResourceAsStream("/updater/annotation_popup.pdf");
            document.setInputStream(fileUrl, "annotation_popup.pdf");

            File out = new File("./src/test/out/RewriteTest_testFullUpdate.pdf");


            try (BufferedOutputStream stream = new BufferedOutputStream(new FileOutputStream(out), 8192)) {
                long length = document.saveToOutputStream(stream, WriteMode.FULL_UPDATE);
//                assertEquals(134643, length);
            }
            Document modifiedDocument = new Document();
            modifiedDocument.setFile(out.getAbsolutePath());

            // check pages length
            assertEquals(2, modifiedDocument.getNumberOfPages());

            // make sure page still has an annotation
            Page page = document.getPageTree().getPage(0);
            // annot, popup and glue
            assertEquals(3, page.getAnnotations().size());
        } catch (PDFSecurityException | IOException | InterruptedException e) {
            // make sure we have no io errors.
            fail("should not be any exceptions");
        }
    }

    @DisplayName("xrefStream - write full document, dropping double incremental update")
    @Test
    public void testDoubleIncrementFullUpdate() {
        try {
            Document document = new Document();
            InputStream fileUrl = ObjectUpdateTests.class.getResourceAsStream("/updater/annotation_popup_edit.pdf");
            document.setInputStream(fileUrl, "annotation_popup_edit.pdf");

            File out = new File("./src/test/out/RewriteTest_testDoubleIncrementFullUpdate.pdf");


            try (BufferedOutputStream stream = new BufferedOutputStream(new FileOutputStream(out), 8192)) {
                long length = document.saveToOutputStream(stream, WriteMode.FULL_UPDATE);
//                assertEquals(134628, length);
            }
            Document modifiedDocument = new Document();
            modifiedDocument.setFile(out.getAbsolutePath());

            // check pages length
            assertEquals(2, modifiedDocument.getNumberOfPages());

            // make sure page still has an annotation
            Page page = document.getPageTree().getPage(0);
            // annot, popup and glue
            List<Annotation> annotations = page.getAnnotations();
            assertEquals(3, annotations.size());

            // check annotation for edit
            assertEquals("This should not be in file if deleted - edited", annotations.get(0).getContents());

        } catch (PDFSecurityException | IOException | InterruptedException e) {
            // make sure we have no io errors.
            fail("should not be any exceptions");
        }
    }

    @DisplayName("xrefStream - write full document, after annotation deletion")
    @Test
    public void testAnnotationDeleteFullUpdate() {
        try {
            Document document = new Document();
            InputStream fileUrl = ObjectUpdateTests.class.getResourceAsStream("/updater/annotation_popup_full.pdf");
            document.setInputStream(fileUrl, "annotation_popup_full.pdf");

            File out = new File("./src/test/out/RewriteTest_testAnnotationDeleteFullUpdate.pdf");

            Page page = document.getPageTree().getPage(0);
            page.deleteAnnotation(page.getAnnotations().get(0));

            try (BufferedOutputStream stream = new BufferedOutputStream(new FileOutputStream(out), 8192)) {
                long length = document.saveToOutputStream(stream, WriteMode.FULL_UPDATE);

                // test for length 142246
//                assertEquals(133529, length);
            }
            Document modifiedDocument = new Document();
            modifiedDocument.setFile(out.getAbsolutePath());

            // check pages length
            assertEquals(2, modifiedDocument.getNumberOfPages());

            // make sure page still has an annotation
            page = document.getPageTree().getPage(0);
            // annot, popup and glue
            assertEquals(0, page.getAnnotations().size());
        } catch (PDFSecurityException | IOException | InterruptedException e) {
            // make sure we have no io errors.
            fail("should not be any exceptions");
        }
    }

    @DisplayName("full write - write and open and fail if there is an exception loading the new file")
    @Test
    @Disabled
    public void testBatchFullUpdate() {
        String testDirectory = "/test-suite-path/";
        Path contentPath = Paths.get(testDirectory);
        if (Files.isDirectory(contentPath)) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(contentPath)) {
                int count = 0;
                for (Path entry : stream) {
                    if (entry.getFileName().toString().toLowerCase().endsWith(".pdf")) {
                        System.out.println("rewriting: " + entry.toString() + " " + count++);
                        Document document = new Document();
                        document.setFile(entry.toString());

                        File out = new File("./src/test/out/test.pdf");

                        try (BufferedOutputStream fileStream = new BufferedOutputStream(new FileOutputStream(out)
                                , 8192)) {
                            document.saveToOutputStream(fileStream, WriteMode.FULL_UPDATE);
                        }
                        document.dispose();

                        // open the document, check for any xref loading corruption issue.
                        Document modifiedDocument = new Document();
                        modifiedDocument.setFile(out.getAbsolutePath());
                        PageTree pageTree = document.getPageTree();
                        pageTree.init();
                        Page page = pageTree.getPage(0);
                        modifiedDocument.dispose();
                    }
                }
            } catch (IOException | PDFSecurityException | InterruptedException e) {
                // make sure we have no io errors.
                fail("should not be any exceptions", e);
            }
        }
    }
}
