/*
 * Copyright 2026 Patrick Corless
 *
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
package org.icepdf.output;

import org.icepdf.core.exceptions.PDFSecurityException;
import org.icepdf.core.pobjects.Document;
import org.icepdf.core.util.updater.WriteMode;
import org.icepdf.ri.util.FontPropertiesManager;
import org.icepdf.utils.PDFValidator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.*;

import static org.junit.jupiter.api.Assertions.*;


public class IncrementalUpdateTest {
    @BeforeAll
    public static void init() {
        FontPropertiesManager.getInstance().loadOrReadSystemFonts();
    }

    @DisplayName("incremental update - should write the same file if no changes are made")
    @Test
    public void testEmptyIncrementalUpdateWritesTheSameFile() {
        try {
            Document document = new Document();
            // Open stream, measure length, then re-open for document
            String sampleFilePath = "/annotation/hello_pdfa1.pdf";
            String ouputFilePath = "./src/test/out/Incremental_update_write.pdf";
            try (InputStream fileUrl = IncrementalUpdateTest.class.getResourceAsStream(sampleFilePath)) {
                assertNotNull(fileUrl, "the sample file should be found in the test resources");
                int documentLength = (int) getInputStreamLength(fileUrl);
                // Re-open stream for document
                try (InputStream fileUrlForDoc = IncrementalUpdateTest.class.getResourceAsStream(sampleFilePath)) {
                    document.setInputStream(fileUrlForDoc, sampleFilePath);

                    File outputFile = new File(ouputFilePath);
                    try (BufferedOutputStream stream = new BufferedOutputStream(new FileOutputStream(outputFile),
                            64 * 1024)) {
                        document.saveToOutputStream(stream, WriteMode.INCREMENT_UPDATE);
                    }
                    Document modifiedDocument = new Document();
                    modifiedDocument.setFile(outputFile.getAbsolutePath());

                    try (InputStream fileUrl2 = new FileInputStream(outputFile)) {
                        int modifiedDocumentLength = (int) getInputStreamLength(fileUrl2);
                        assertEquals(documentLength, modifiedDocumentLength, "the file should be the same length as " +
                                "the original since we haven't made any changes.");
                    }

                    PDFValidator.validatePDFA(new FileInputStream(outputFile));
                }
            }
        } catch (PDFSecurityException | IOException | InterruptedException e) {
            fail("should not be any exceptions");
        }
    }

    public static long getInputStreamLength(InputStream inputStream) throws IOException {
        long length = 0;
        byte[] buffer = new byte[8192];
        int bytesRead;
        while ((bytesRead = inputStream.read(buffer)) != -1) {
            length += bytesRead;
        }
        return length;
    }
}
