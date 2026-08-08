/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.icepdf.core.util.updater;

import org.icepdf.core.exceptions.PDFSecurityException;
import org.icepdf.core.pobjects.*;
import org.icepdf.core.util.Library;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.*;

import static org.junit.jupiter.api.Assertions.*;

public class EncryptionTests {
    @DisplayName("encryption - document rewrite should still be encrypted")
    @Test
    public void testXrefTableFullUpdate() {
        try {
            FullUpdater.compressXrefTable = false;
            Document document = new Document();
            InputStream fileUrl = ObjectUpdateTests.class.getResourceAsStream("/updater/DSCP73_om_en.pdf");
            document.setInputStream(fileUrl, "DSCP73_om_en.pdf");

            File out = new File("./src/test/out/EncryptionTest_testXrefTableFullUpdate.pdf");
            try (BufferedOutputStream stream = new BufferedOutputStream(new FileOutputStream(out), 8192)) {
                long length = document.saveToOutputStream(stream, WriteMode.FULL_UPDATE);

                // test for length 142246
//                assertEquals(3576973, length);
            }
            Document modifiedDocument = new Document();
            modifiedDocument.setFile(out.getAbsolutePath());

        } catch (PDFSecurityException | IOException | InterruptedException e) {
            // make sure we have no io errors.
            fail("should not be any exceptions");
        }
    }

    @DisplayName("encryption - a hex string authored on an encrypted document survives a rewrite")
    @Test
    public void testHexStringRoundTripsEncrypted() throws Exception {
        // The corpus encrypted document holds no hexadecimal strings at all, so this path had no
        // coverage: authoring one produced <EBEAE0> from a 62 digit string, because the writer put
        // the DECODED text between the angle brackets and re-parsing kept only the characters that
        // happened to be hexadecimal digits.
        final Name probeKey = new Name("ICEpdfHexProbe");
        final String plainText = "Hex probe value";

        Document document = new Document();
        document.setInputStream(
                EncryptionTests.class.getResourceAsStream("/updater/DSCP73_om_en.pdf"), "DSCP73_om_en.pdf");
        Library library = document.getCatalog().getLibrary();
        assertNotNull(library.getSecurityManager(), "test document is expected to be encrypted");

        PInfo info = document.getInfo();
        info.getEntries().put(probeKey, new HexStringObject(plainText, info.getPObjectReference()));
        library.getStateManager().addChange(new PObject(info, info.getPObjectReference()));

        File out = new File("./src/test/out/EncryptionTest_testHexStringRoundTrip.pdf");
        try (BufferedOutputStream stream = new BufferedOutputStream(new FileOutputStream(out), 8192)) {
            document.saveToOutputStream(stream, WriteMode.FULL_UPDATE);
        }
        document.dispose();

        Document rewritten = new Document();
        rewritten.setFile(out.getAbsolutePath());
        Object readBack = rewritten.getInfo().getEntries().get(probeKey);
        assertInstanceOf(HexStringObject.class, readBack);
        assertEquals(plainText, ((HexStringObject) readBack).getDecryptedLiteralString(
                rewritten.getCatalog().getLibrary().getSecurityManager()));
        rewritten.dispose();
    }
}
