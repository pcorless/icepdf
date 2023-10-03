package org.icepdf.redaction;

import org.icepdf.core.exceptions.PDFSecurityException;
import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.Page;
import org.icepdf.core.pobjects.annotations.Annotation;
import org.icepdf.core.pobjects.annotations.AnnotationFactory;
import org.icepdf.core.pobjects.annotations.RedactionAnnotation;
import org.icepdf.core.pobjects.graphics.text.WordText;
import org.icepdf.core.search.DocumentSearchController;
import org.icepdf.core.util.updater.WriteMode;
import org.icepdf.ri.common.SwingController;
import org.icepdf.ri.common.SwingViewBuilder;
import org.icepdf.ri.util.FontPropertiesManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

public class RedactionTests {

    @BeforeAll
    public static void init() {
        FontPropertiesManager.getInstance().loadOrReadSystemFonts();
    }

    @DisplayName("redact simple text and export")
    @Test
//    @Disabled
    public void testSimpleLayoutFullUpdate() {
        try {

            // search
            Document document = searchAndRedact(
                    "/redact/test_print.pdf",
                    new String[]{"que"},
                    0,
                    1);
            File out = new File("./src/test/out/RedactionTests_testSimpleLayoutFullUpdate.pdf");
            try (BufferedOutputStream stream = new BufferedOutputStream(new FileOutputStream(out), 64 * 1024)) {
                document.saveToOutputStream(stream, WriteMode.FULL_UPDATE);
            }
            Document modifiedDocument = new Document();
            modifiedDocument.setFile(out.getAbsolutePath());

            // make sure page still has an annotation
            Page page = modifiedDocument.getPageTree().getPage(0);
            assertEquals(4, page.getAnnotations().size());
        } catch (PDFSecurityException | IOException | InterruptedException | InvocationTargetException e) {
            // make sure we have no io errors.
            fail("should not be any exceptions");
        }
    }

    @DisplayName("redact complex layout text and export")
    @Test
//    @Disabled
    public void testComplexLayoutFullUpdate() {
        try {
            // search
            Document document = searchAndRedact(
                    "/redact/pdf_reference_addendum_redaction.pdf",
                    new String[]{"Redaction"},
                    1,
                    2);
            File out = new File("./src/test/out/RedactionTests_testComplexLayoutFullUpdate.pdf");
            try (BufferedOutputStream stream = new BufferedOutputStream(new FileOutputStream(out), 64 * 1024)) {
                document.saveToOutputStream(stream, WriteMode.FULL_UPDATE);
            }
            Document modifiedDocument = new Document();
            modifiedDocument.setFile(out.getAbsolutePath());

            // make sure page still has an annotation
            Page page = modifiedDocument.getPageTree().getPage(1);
            assertEquals(15, page.getAnnotations().size());
        } catch (PDFSecurityException | IOException | InterruptedException | InvocationTargetException e) {
            // make sure we have no io errors.
            fail("should not be any exceptions");
        }
    }

    private Document searchAndRedact(String path, String[] terms, int startIndex, int endIndex) throws InterruptedException,
            InvocationTargetException {

        InputStream fileUrl = RedactionTests.class.getResourceAsStream(path);

        final SwingController controller = new SwingController();

        SwingUtilities.invokeAndWait(() -> {
            SwingViewBuilder factory = new SwingViewBuilder(controller);
            JPanel viewerComponentPanel = factory.buildViewerPanel();
            JFrame applicationFrame = new JFrame();
            applicationFrame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
            applicationFrame.getContentPane().add(viewerComponentPanel);

            // add interactive mouse link annotation support via callback
            controller.getDocumentViewController().setAnnotationCallback(
                    new org.icepdf.ri.common.MyAnnotationCallback(
                            controller.getDocumentViewController()));

            // Now that the GUI is all in place, we can try opening the PDF
            controller.openDocument(fileUrl, "redact_test", "redact_test");

            DocumentSearchController searchController =
                    controller.getDocumentSearchController();
            for (String term : terms) {
                searchController.addSearchTerm(term, false, false);
            }

            Document document = controller.getDocument();
            // list of founds words to print out
            ArrayList<WordText> foundWords;
            for (int pageIndex = startIndex; pageIndex < endIndex; pageIndex++) {
                // get the search results for this page
                foundWords = searchController.searchPage(pageIndex);
                if (foundWords != null) {
                    // get the current page lock and start adding the annotations
                    Page page = document.getPageTree().getPage(pageIndex);

                    for (WordText wordText : foundWords) {

                        Rectangle tBbox = wordText.getBounds().getBounds();

                        RedactionAnnotation redactionAnnotation = (RedactionAnnotation)
                                AnnotationFactory.buildAnnotation(
                                        document.getPageTree().getLibrary(),
                                        Annotation.SUBTYPE_REDACT,
                                        tBbox);

                        if (redactionAnnotation != null) {
                            ArrayList<Shape> markupBounds = new ArrayList<>();
                            markupBounds.add(tBbox);
                            redactionAnnotation.setColor(Color.BLACK);
                            redactionAnnotation.setMarkupBounds(markupBounds);
                            redactionAnnotation.setMarkupPath(new GeneralPath(tBbox));
                            redactionAnnotation.setBBox(tBbox);
                            redactionAnnotation.resetAppearanceStream(new AffineTransform());

                            page.addAnnotation(redactionAnnotation, true);
                        }
                    }
                }
            }
        });
        return controller.getDocument();
    }
}
