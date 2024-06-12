package org.icepdf.examples.redaction;

import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.Page;
import org.icepdf.core.pobjects.annotations.Annotation;
import org.icepdf.core.pobjects.annotations.AnnotationFactory;
import org.icepdf.core.pobjects.annotations.RedactionAnnotation;
import org.icepdf.core.pobjects.graphics.text.WordText;
import org.icepdf.core.search.DocumentSearchController;
import org.icepdf.core.util.updater.WriteMode;
import org.icepdf.ri.common.search.DocumentSearchControllerImpl;
import org.icepdf.ri.util.FontPropertiesManager;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Collections;

/**
 * The <code>RedactionHeadless</code> class is an example of how to use text search results
 * as inputs for the creation of redaction annotations. Once the annotations are created the
 * document is exported burning the redaction annotations into the PDFs content streams.
 * The resulting document will no longer have text where the Redaction annotations intersected.
 *
 * @since 7.2.0
 */
public class RedactionHeadless {
    public static void main(String[] args) {

        FontPropertiesManager.getInstance().loadOrReadSystemFonts();

        // Get a file from the command line to open
        String filePath = args[0];

        // save page captures to file.
        float scale = 1.0f;
        float rotation = 0f;

        // open the document
        Document document = new Document();
        try {
            document.setFile(filePath);

            // get the search controller
            DocumentSearchController searchController =
                    new DocumentSearchControllerImpl(document);
            // add a specified search terms.
            searchController.addSearchTerm("redaction", false, false);

            ArrayList<WordText> foundWords;
            RedactionAnnotation redactionAnnotation;

            // iterated over each page creating redaction from search terms
            for (int i = 0, max = document.getNumberOfPages(); i < max; i++) {
                Page page = document.getPageTree().getPage(i);
                page.init();

                // search the page
                foundWords = searchController.searchPage(i);
                if (foundWords == null) {
                    System.out.println("No Search terms found");
                    return;
                }
                for (WordText wordText : foundWords) {
                    final Rectangle tBbox = wordText.getBounds().getBounds();

                    redactionAnnotation = (RedactionAnnotation)
                            AnnotationFactory.buildAnnotation(
                                    document.getPageTree().getLibrary(),
                                    Annotation.SUBTYPE_REDACT,
                                    tBbox);

                    if (redactionAnnotation != null) {
                        redactionAnnotation.setColor(Color.BLACK);
                        redactionAnnotation.setMarkupBounds(new ArrayList<>(Collections.singletonList(tBbox)));
                        redactionAnnotation.setMarkupPath(new GeneralPath(tBbox));
                        redactionAnnotation.setBBox(tBbox);
                        redactionAnnotation.resetAppearanceStream(new AffineTransform());
                        page.addAnnotation(redactionAnnotation, true);
                    }
                }
            }

            // burn the redaction into the PDF by exporting the document.
            File file = new File("redacted_output.pdf");
            try (final FileOutputStream fileOutputStream = new FileOutputStream(file);
                 final BufferedOutputStream buf = new BufferedOutputStream(fileOutputStream, 8192)) {
                document.writeToOutputStream(buf, WriteMode.FULL_UPDATE);
            }

            // clean up resources
            document.dispose();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
