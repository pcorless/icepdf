package org.icepdf.core.util.updater;


import org.icepdf.core.exceptions.PDFSecurityException;
import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.Page;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.FileOutputStream;
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
            FileOutputStream fileOutputStream = new FileOutputStream("./src/test/out/ObjectUpdateTest.pdf");
            document.saveToOutputStream(fileOutputStream, WriteMode.FULL_UPDATE);

            // check file length
            fileOutputStream.close();

            // open the output and check for the missing objcts


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
