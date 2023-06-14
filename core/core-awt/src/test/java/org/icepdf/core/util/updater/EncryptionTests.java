package org.icepdf.core.util.updater;

import org.icepdf.core.exceptions.PDFSecurityException;
import org.icepdf.core.pobjects.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

public class EncryptionTests {


    @DisplayName("encryption - document rewrite should still be encrypted")
    @Test
    public void testXrefTableFullUpdate() {
        try {
            FullUpdater.compressXrefTable = false;
            Document document = new Document();
            InputStream fileUrl = ObjectUpdateTests.class.getResourceAsStream("/updater/DSCP73_om_en.pdf");
            document.setInputStream(fileUrl, "DSCP73_om_en.pdf");

            File out = new File("./src/test/out/ObjectUpdateTest-3.pdf");
            try (BufferedOutputStream stream = new BufferedOutputStream(new FileOutputStream(out), 8192)) {
                long length = document.saveToOutputStream(stream, WriteMode.FULL_UPDATE);

                // test for length 142246
                assertEquals(3576973, length);
            }
            Document modifiedDocument = new Document();
            modifiedDocument.setFile(out.getAbsolutePath());

        } catch (PDFSecurityException | IOException e) {
            // make sure we have no io errors.
            fail("should not be any exceptions");
        }
    }
}
