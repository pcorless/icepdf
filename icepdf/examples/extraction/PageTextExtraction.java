/*
 * Copyright 2006-2014 ICEsoft Technologies Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an "AS
 * IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */

import org.icepdf.core.exceptions.PDFException;
import org.icepdf.core.exceptions.PDFSecurityException;
import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.graphics.text.PageText;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;

/**
 * The <code>PageTextExtraction</code> class is an example of how to extract
 * text from a PDF document.  A file specified at the command line is opened
 * and any text in the first page's content is saved to a text file.
 *
 * @since 2.0
 */
public class PageTextExtraction {
    public static void main(String[] args) {

        // Get a file from the command line to open
        String filePath = args[0];

        // open the url
        Document document = new Document();
        try {
            document.setFile(filePath);
        } catch (PDFException ex) {
            System.out.println("Error parsing PDF document " + ex);
        } catch (PDFSecurityException ex) {
            System.out.println("Error encryption not supported " + ex);
        } catch (FileNotFoundException ex) {
            System.out.println("Error file not found " + ex);
        } catch (IOException ex) {
            System.out.println("Error handling PDF document " + ex);
        }

        try {
            // create a file to write the extracted text to
            File file = new File("extractedtext.txt");
            FileWriter fileWriter = new FileWriter(file);

            // Get text from the first page of the document, assuming that there
            // is text to extract.
            for (int pageNumber = 0, max = document.getNumberOfPages();
                 pageNumber < max; pageNumber++) {
                PageText pageText = document.getPageText(pageNumber);
                System.out.println("Extracting page text: " + pageNumber);
                if (pageText != null && pageText.getPageLines() != null) {
                    fileWriter.write(pageText.toString());
                }
            }

            // close the writer
            fileWriter.close();

        } catch (IOException ex) {
            System.out.println("Error writing to file " + ex);
        } catch (InterruptedException ex) {
            System.out.println("Error paring page " + ex);
        }

        // clean up resources
        document.dispose();
    }
}
