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
package org.icepdf.examples.jsf.viewer.util;

import javax.servlet.http.HttpSessionEvent;
import javax.servlet.http.HttpSessionListener;
import java.io.File;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * <p>The InputFileSessionCleaner is responsible for cleaning up any files
 * that may have been uploaded by a session.  The InputFile component by
 * default stores files in an upload folder in the root directory of the
 * deployed application in a child folder named after session id.</p>
 * <p>In most deployment scenarios there is no need to keep files that
 * where uploaded during the session.  This class implements
 * HttpSessionListener and will clean up any uploaded files when the
 * #sessionDestroyed method is called be the Servlet container </p>
 * <p>Make sure that this been is properly register as a listener in the
 * application web.xml descriptor file.</p>
 *
 * @since 3.0
 */
public class InputFileSessionCleaner implements HttpSessionListener {

    private static final Logger logger =
            Logger.getLogger(InputFileSessionCleaner.class.toString());

    public static final String FILE_UPLOAD_DIRECTORY = "upload";

    /**
     * This method is called by the servlet container when the session
     * is about to expire. This method will attempt to delete all files that
     * where uploaded into the folder which has the same name as the session
     * id.
     *
     * @param event JSF session event.
     */
    public void sessionDestroyed(HttpSessionEvent event) {
        // get the session id, so we know which folder to remove
        String sessionId = event.getSession().getId();

        String applicationPath = event.getSession().getServletContext().getRealPath(
                event.getSession().getServletContext().getServletContextName());

        String sessionFileUploadPath =
                applicationPath + File.separatorChar +
                        FILE_UPLOAD_DIRECTORY +
                        File.separatorChar + sessionId;

        File sessionfileUploadDirectory = new File(sessionFileUploadPath);

        if (sessionfileUploadDirectory.isDirectory()) {
            boolean delete;
            try {
                delete = sessionfileUploadDirectory.delete();
            }
            catch (SecurityException e) {
                logger.log(Level.FINE, "Error deleting file upload directory: ", e);
            }
            logger.log(Level.INFO, "Deleted Uploaded sessino files");
        }

    }

    public void sessionCreated(HttpSessionEvent event) {

    }

}
