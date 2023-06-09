package org.icepdf.core.util.updater;


import org.icepdf.core.exceptions.PDFSecurityException;
import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.Page;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.*;

public class ObjectUpdateTests {


    @DisplayName("xrefStream - delete object and write full document update")
    @Test
    public void testXrefTableFullUpdate() {
        try {
            Document document = new Document();
            InputStream fileUrl = ObjectUpdateTests.class.getResourceAsStream("/updater/R&D-05-Carbon.pdf");
            document.setInputStream(fileUrl, "R&D-05-Carbon.pdf");

            Page page = document.getPageTree().getPage(2);
            document.removePage(page);

            File out = new File("./src/test/out/ObjectUpdateTest.pdf");

            try (BufferedOutputStream stream = new BufferedOutputStream(new FileOutputStream(out), 8192)) {
                long length = document.saveToOutputStream(stream, WriteMode.FULL_UPDATE);

                // test for length 142246

                // check pages length

                // open the document and make sure page is no longer found.

                // make sure there were no exceptoin
            }
        } catch (PDFSecurityException | IOException e) {
            // make sure we have no io errors.
        }
    }

    @DisplayName("xref table - add annotation object and write full document update")
    @Test
    public void testXrefStreamFullUpdate() {

    }

}
