package org.icepdf.os.examples.extraction;
/*
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
import org.icepdf.core.pobjects.PInfo;
import org.icepdf.ri.util.FontPropertiesManager;

import java.io.FileNotFoundException;
import java.io.IOException;

/**
 * The <code>PageMetaDataExtraction</code> class is an example of how to extract
 * meta-data from a PDF document.   A file specified at the command line is opened
 * and the document's information is displayed on the command line.
 *
 * @since 2.0
 */
public class PageMetaDataExtraction {
    public static void main(String[] args) {

        // Get a file from the command line to open
        String filePath = args[0];

        // read/store the font cache.
        FontPropertiesManager.getInstance().loadOrReadSystemFonts();

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

        // data to collect from document information entry
        String title = "";
        String author = "";
        String subject = "";
        String keyWords = "";
        String creator = "";
        String producer = "";
        String creationDate = "";
        String modDate = "";

        // get document information values if available
        PInfo documentInfo = document.getInfo();
        if (documentInfo != null) {
            title = documentInfo.getTitle();
            author = documentInfo.getAuthor();
            subject = documentInfo.getSubject();
            keyWords = documentInfo.getKeywords();
            creator = documentInfo.getCreator() != null ?
                    documentInfo.getCreator() : "Not Available";
            producer = documentInfo.getProducer() != null ?
                    documentInfo.getProducer() : "Not Available";
            creationDate = documentInfo.getCreationDate() != null ?
                    documentInfo.getCreationDate().toString() : "Not Available";
            modDate = documentInfo.getModDate() != null ?
                    documentInfo.getModDate().toString() : "Not Available";
        }

        // Output the captured document information
        System.out.println("Title:    " + title);
        System.out.println("Subject:  " + subject);
        System.out.println("Author:   " + author);
        System.out.println("Keywords: " + keyWords);
        System.out.println("Creator:  " + creator);
        System.out.println("Producer: " + producer);
        System.out.println("Created:  " + creationDate);
        System.out.println("Modified: " + modDate);

        // clean up resources
        document.dispose();
    }
}
