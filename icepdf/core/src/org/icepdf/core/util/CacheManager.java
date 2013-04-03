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
package org.icepdf.core.util;

import java.io.File;
import java.util.Vector;
import java.util.logging.Logger;
import java.util.logging.Level;


/**
 * <p>The <code>CacheManager</code> class has one very simple task which is to ensure
 * that when a document is closed there are no temporary <code>ByteCache</code>
 * or <code>ImageCache</code> objects left in the user's temporary folder.</p>
 * <p/>
 * <p>Whenever a new <code>ByteCache</code> or <code>ImageCache</code> object
 * is created, the corresponding cached file name is registered with the
 * <code>CacheManger</code>.  When the document is closed and the
 * <code>CacheManager</code> <code>despose()</code> method is
 * called, all of the registered files are deleted from the file system.</p>
 *
 * @since 1.1
 */
public class CacheManager {

    private static final Logger logger =
            Logger.getLogger(CacheManager.class.toString());

    private int fileCount = 0;
    private Vector cachedFiles;

    /**
     * Create a new instance of a CacheManager.
     */
    public CacheManager() {
        cachedFiles = new Vector(128);
    }

    /**
     * Add the file name of either a ByteCache or ImageCache object cached file.
     *
     * @param filePath the absolulte path to a temporary file.
     */
    public synchronized void addCachedFile(String filePath) {
        // increment file count
        fileCount++;
        cachedFiles.add(filePath);
    }

    /**
     * Remove all files specified by the addCachedFile method along with the
     * temporary file created by this class.
     */
    public void dispose() {
        for (Object cachedFile : cachedFiles) {
            String fileName = (String) cachedFile;
            try {
                boolean success = (new File(fileName)).delete();
                if (!success && logger.isLoggable(Level.FINE)) {
                    logger.fine("Error deleting cached file " + fileName);
                }
            }
            catch (SecurityException e) {
                logger.log(Level.FINE,
                        "Security error removing cached file " + fileName, e);
            }
        }
    }
}

