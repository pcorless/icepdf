package org.icepdf.core.util.updater;


import org.icepdf.core.exceptions.PDFSecurityException;
import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.Page;
import org.icepdf.core.util.Library;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class ObjectUpdateTests {


    @DisplayName("xref table - delete object and write full document update")
    @Test
    public void testXrefTableFullUpdate() {
        try {
            Document document = new Document();
            InputStream fileUrl = ObjectUpdateTests.class.getResourceAsStream("/updater/R&D-05-Carbon.pdf");
            document.setInputStream(fileUrl, "R&D-05-Carbon.pdf");

            Page page = document.getPageTree().getPage(0);
            document.removePage(page);
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream(200000);
            document.saveToOutputStream(outputStream, WriteMode.FULL_UPDATE);

            // check file length
            outputStream.flush();
            outputStream.size();

            // make sure the following object are no longer present in file
            Library library = document.getCatalog().getLibrary();
            /// library.getObject();


        } catch (PDFSecurityException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @DisplayName("xrefStream - delete object and write full document update")
    @Test
    public void testXrefStreamFullUpdate() {

    }

    @DisplayName("linerized file - delete object and write full document update")
    @Test
    public void testLineraizedFileFullUpdate() {

    }
}
