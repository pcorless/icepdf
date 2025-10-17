package org.icepdf.core.pobjects.structure;

import org.icepdf.core.exceptions.PDFSecurityException;
import org.icepdf.core.pobjects.Document;
import org.icepdf.core.util.updater.FullUpdater;
import org.icepdf.core.util.updater.ObjectUpdateTests;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

public class IndexerTest {
    @DisplayName("should assert exception on bad encoding")
    @Test
    public void testIndexerBadEncoding() {
        assertThrows(IllegalStateException.class, () -> {
            FullUpdater.compressXrefTable = false;
            Document document = new Document();
            InputStream fileUrl = ObjectUpdateTests.class.getResourceAsStream("/structure/bad_encoding.pdf");
            document.setInputStream(fileUrl, "bad_encoding.pdf");
        });
    }

    @DisplayName("should assert exception on partial xref table")
    @Test
    public void testIndexerBadXref() {
        assertThrows(IllegalStateException.class, () -> {
            FullUpdater.compressXrefTable = false;
            Document document = new Document();
            InputStream fileUrl = ObjectUpdateTests.class.getResourceAsStream("/structure/bad_missing_xref.pdf");
            document.setInputStream(fileUrl, "bad_missing_xref.pdf");
        });
    }

    @DisplayName("should throw exception on partial xref table")
    @Test
    public void testIndexerParcialXref() {
        assertThrows(IllegalStateException.class, () -> {
            FullUpdater.compressXrefTable = false;
            Document document = new Document();
            InputStream fileUrl = ObjectUpdateTests.class.getResourceAsStream("/structure/bad_partial_xref.pdf");
            document.setInputStream(fileUrl, "bad_partial_xref.pdf");
        });
    }

    @DisplayName("should open document with bad object offsets")
    @Test
    public void testXrefTableFullUpdate() {
        try {
            FullUpdater.compressXrefTable = false;
            Document document = new Document();
            InputStream fileUrl = ObjectUpdateTests.class.getResourceAsStream("/structure/bad_object_offset.pdf");
            document.setInputStream(fileUrl, "bad_object_offset.pdf");
        } catch (PDFSecurityException | IOException e) {
            // make sure we have no io errors.
            fail("should not be any exceptions");
        }
    }
}
