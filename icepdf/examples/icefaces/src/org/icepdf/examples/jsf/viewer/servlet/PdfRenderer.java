/*
 * Copyright 2006-2012 ICEsoft Technologies Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an "AS
 * IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either * express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */
package org.icepdf.examples.jsf.viewer.servlet;

import org.icepdf.examples.jsf.viewer.view.BeanNames;
import org.icepdf.examples.jsf.viewer.view.DocumentManager;

import javax.imageio.ImageIO;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * PDF Rendering servlet responsible for rendering the current document state
 * image.  The document state keeps track of the current page, zoom and rotation
 * informaiton.  
 *
 * @since 3.0
 */
public class PdfRenderer extends HttpServlet {

    private static final Logger logger =
            Logger.getLogger(PdfRenderer.class.toString());

    /**
     * @param request  incoming request
     * @param response outgoing response
     * @throws javax.servlet.ServletException
     * @throws java.io.IOException
     */
    public void doGet(HttpServletRequest request,
                                   HttpServletResponse response)
            throws ServletException, IOException {
        BufferedImage bi = null;
        try {
            // get the document manager from the session map.
            DocumentManager documentManager = (DocumentManager)
                    request.getSession().getAttribute(BeanNames.DOCUMENT_MANAGER);

            if (documentManager != null) {
                // get the page image a write it out to the response stream
                bi = (BufferedImage)
                        documentManager.getCurrentPageImage();
                if (bi != null) {
                    response.setContentType("image/png");
                    OutputStream os1 = response.getOutputStream();
                    ImageIO.write(bi, "png", os1);
                    os1.close();
                    bi.flush();
                }
            }
        } catch (Throwable e) {
            logger.log(Level.WARNING, "Error writing image stream.", e);
            if (bi != null){
                bi.flush();
            }
        }
    }

}

