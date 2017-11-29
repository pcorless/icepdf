/*
 * Copyright 2006-2017 ICEsoft Technologies Canada Corp.
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
package org.icepdf.ri.util.qa;

import org.icepdf.core.exceptions.PDFException;
import org.icepdf.core.exceptions.PDFSecurityException;
import org.icepdf.core.pobjects.Document;
import org.icepdf.ri.util.FontPropertiesManager;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;

public class PageCapture implements CaptureTest<BufferedImage> {

    protected Document document;

    @Override
    public int load(Path filePath) {

        // initiate font Cache manager, reads system if necessary,  load the cache otherwise.
        FontPropertiesManager.getInstance().loadOrReadSystemFonts();

        try {
            document = new Document();
            document.setFile(filePath.toAbsolutePath().toString());
            return document.getNumberOfPages();
        } catch (PDFException e) {
            e.printStackTrace();
        } catch (PDFSecurityException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return 0;
    }

    @Override
    public BufferedImage capture(int pageNumber, int renderHintType, int boundary, float userRotation, float userZoom) {
        try {
            return (BufferedImage) document.getPageImage(pageNumber, renderHintType, boundary, userRotation, userZoom);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // capture the page.
        return null;
    }

    @Override
    public void dispose() {
        document.dispose();
    }
}
