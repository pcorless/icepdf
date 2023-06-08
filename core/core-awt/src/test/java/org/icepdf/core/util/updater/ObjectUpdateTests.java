package org.icepdf.core.util.updater;


import org.icepdf.core.exceptions.PDFSecurityException;
import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.Page;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.*;

public class ObjectUpdateTests {


    @DisplayName("xref table - delete object and write full document update")
    @Test
    public void testXrefTableFullUpdate() {
        try {
            Document document = new Document();
            InputStream fileUrl = ObjectUpdateTests.class.getResourceAsStream("/updater/R&D-05-Carbon.pdf");
            document.setInputStream(fileUrl, "R&D-05-Carbon.pdf");

            Page page = document.getPageTree().getPage(2);
            document.removePage(page);

            File out = new File("./src/test/out/ObjectUpdateTest.pdf");

            BufferedOutputStream stream = new BufferedOutputStream(new FileOutputStream(out), 8192);

            long length = document.saveToOutputStream(stream, WriteMode.FULL_UPDATE);

            System.out.println("length: " + length);

            // check file length
            stream.close();

            // open the output and check for the removed objects
            OutputStreamWriter test = new OutputStreamWriter(stream);


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
