/*
 * Version: MPL 1.1/GPL 2.0/LGPL 2.1
 *
 * "The contents of this file are subject to the Mozilla Public License
 * Version 1.1 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
 * License for the specific language governing rights and limitations under
 * the License.
 *
 * The Original Code is ICEpdf 3.0 open source software code, released
 * May 1st, 2009. The Initial Developer of the Original Code is ICEsoft
 * Technologies Canada, Corp. Portions created by ICEsoft are Copyright (C)
 * 2004-2009 ICEsoft Technologies Canada, Corp. All Rights Reserved.
 *
 * Contributor(s): _____________________.
 *
 * Alternatively, the contents of this file may be used under the terms of
 * the GNU Lesser General Public License Version 2.1 or later (the "LGPL"
 * License), in which case the provisions of the LGPL License are
 * applicable instead of those above. If you wish to allow use of your
 * version of this file only under the terms of the LGPL License and not to
 * allow others to use your version of this file under the MPL, indicate
 * your decision by deleting the provisions above and replace them with
 * the notice and other provisions required by the LGPL License. If you do
 * not delete the provisions above, a recipient may use your version of
 * this file under either the MPL or the LGPL License."
 *
 */
package org.icepdf.core.util;

import java.io.*;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * The <code>ByteCache</code> class is designed to cache a byte array input
 * to file when a specified size has been reached (Cached).  This cached byte
 * stream can be read from file when the byte array is needed by a program.
 * <p/>
 * The <code>ByteCache</code> creates a temporary file in the users temporary
 * directory and the file is deleted when the application is closed or when
 * the deleteFileCache is called.
 * <p/>
 * File caching can be forced by calling the forceByteCaching() method, but
 * caching is normally done automatically when the byteArray reaches a
 * specified length.  The length is specified by the system property
 * org.icepdf.core.util.byteFileCachingSize where the associated value is byte
 * array length in which the byte array will be cached to a temporary file.
 * <p/>
 * Every time a new bye cache is created the full path to the file
 * is recored in the CacheManager class which allows the CacheManager to delete
 * a documents cached bytes when the document is closed.
 *
 * @since 1.1
 */
public class ByteCache {

    private static final Logger logger =
            Logger.getLogger(ByteCache.class.toString());

    // length of byte array that is in ByteCache
    private int length = 0;

    // temp file that is created for the cache
    private File tempFile = null;

    // is caching enabled or disabled
    private boolean isCached = false;

    // auto caching kick in size
    private static int fileCachingSize;

    // There are cases where, when we are low in memory,
    //  that we need to write to file, even when our data
    //  size is less than fileCachingSize
    private static int fileCachingFallbackSize = 512 * 1024;

    // disable/inable file cahcing, overrides fileCachingSize.
    private static boolean isCachingEnabled;

    // stream used for reading from file
    private FileOutputStream fileOutputStream = null;

    // stream used for writing to file
    private FileInputStream fileInputStream = null;

    // byte array used for reading bytes
    private ByteArrayInputStream byteArrayInputStream = null;

    // byte array used for writing bytes
    private ByteArrayOutputStream byteArrayOutputStream = null;

    // cache manager reference
    private CacheManager cacheManager = null;

    static {
        // set initial caching size at 1MB for now
        fileCachingSize =
                Defs.sysPropertyInt("org.icepdf.core.streamcache.thresholdSize",
                        1000000);

        if (fileCachingFallbackSize > fileCachingSize)
            fileCachingFallbackSize = fileCachingSize;

        // sets if file caching is enabled or disabled.
        isCachingEnabled =
                Defs.sysPropertyBoolean("org.icepdf.core.streamcache.enabled",
                        true);
    }


    /**
     * Creates a new instance of <code>ByteCache</code> object.
     *
     * @param bytes bytes that will be stored in ByteCache .
     */
    public ByteCache(byte[] bytes, Library library) {
        // get pointer to cache manager.
        cacheManager = library.getCacheManager();
        try {
            int numNewBytes = (bytes == null) ? 0 : bytes.length;
            if (numNewBytes <= 0)
                return;
            calcIfFileCachingAndPotentiallyForce(numNewBytes);
            OutputStream out = getCorrectOutputStream(numNewBytes);
            out.write(bytes);
            length = numNewBytes;
        } catch (IOException e) {
            logger.log(Level.FINE, "Error creating ByteCache temporary file.", e);
        }

    }

    /**
     * Creates a new instance of <code>ByteCache</code> object.  This is an empty
     * place holder for a byte stream.  The length value will be used to pre
     * determine if a temp file will be created even though no data has been
     * cached.
     *
     * @param numNewBytes estimated length of a byte array that will be read at a
     *                    later instnace.
     */
    public ByteCache(int numNewBytes, Library library) {
        // get pointer to cache manager.
        cacheManager = library.getCacheManager();
        try {
            if (numNewBytes <= 0)
                return;
            calcIfFileCachingAndPotentiallyForce(numNewBytes);
            // Force creation of output stream, even though we don't use it right away
            getCorrectOutputStream(numNewBytes);
        }
        catch (IOException e) {
            logger.log(Level.FINE,
                    "Error creating ByteCache temporary file.", e);
        }
    }

    /**
     * Writes <code>length</code> bytes from the specified byte array
     * starting at offset <code>offset</code> to this byte cache.
     *
     * @param in          InputStream which holds the data.
     * @param numNewBytes the number of bytes to write.
     */
    public void writeBytes(InputStream in, int numNewBytes) {
        try {
            if (numNewBytes <= 0)
                return;
            calcIfFileCachingAndPotentiallyForce(numNewBytes);
            OutputStream out = getCorrectOutputStream(numNewBytes);

            byte[] buffer = new byte[Math.min(numNewBytes, 4096)];
            int totalRead = 0;
            while (totalRead < numNewBytes) {
                int currNumToRead = Math.min(buffer.length, numNewBytes - totalRead);
                int currRead = in.read(buffer, 0, currNumToRead);
                if (currRead <= 0)
                    break;
                out.write(buffer, 0, currRead);
                totalRead += currRead;
            }

            length += totalRead;
        }
        catch (IOException e) {
            logger.log(Level.FINE, "Error writing to temporary file ", e);
        }
    }

    /**
     * Writes <code>length</code> bytes from the specified byte array
     * starting at offset <code>offset</code> to this byte cache.
     *
     * @param bytes       the data.
     * @param offset      the start offset in the data.
     * @param numNewBytes the number of bytes to write.
     */
    public void writeBytes(byte[] bytes, int offset, int numNewBytes) {
        try {
            // if in cached mode write the tmp file
            if (numNewBytes <= 0)
                return;
            calcIfFileCachingAndPotentiallyForce(numNewBytes);
            OutputStream out = getCorrectOutputStream(Math.max(numNewBytes, 256));
            out.write(bytes, offset, numNewBytes);
            length += numNewBytes;
        }
        catch (IOException e) {
            logger.log(Level.FINE, "Error writing to temporary file.", e);
        }
    }

    /**
     * Writes <code>bytes.length</code> bytes from the specified byte array
     * to this byte cache..
     *
     * @param bytes the data.
     */
    public void writeBytes(byte[] bytes) {
        try {
            int numNewBytes = (bytes == null) ? 0 : bytes.length;
            if (numNewBytes <= 0)
                return;
            calcIfFileCachingAndPotentiallyForce(numNewBytes);
            OutputStream out = getCorrectOutputStream(Math.max(numNewBytes, 256));
            out.write(bytes);
            length += numNewBytes;
        }
        catch (IOException e) {
            logger.log(Level.FINE, "Error writing to temporary file ", e);
        }
    }

    /**
     * Writes the specified byte to this byte cache.
     *
     * @param bytes the byte to be written.
     */
    public void writeBytes(int bytes) {
        try {
            int numNewBytes = 1;
            calcIfFileCachingAndPotentiallyForce(numNewBytes);
            OutputStream out = getCorrectOutputStream(256);
            out.write(bytes);
            length += numNewBytes;
        }
        catch (IOException e) {
            logger.log(Level.FINE, "Error writing to temporary file ", e);
        }
    }

    /**
     * Reads up to <code>length</code> bytes of data from this byte cache
     * into an array of bytes. This method blocks until some input is
     * available.
     *
     * @param bytes  the buffer into which the data is read.
     * @param offset the start offset of the data.
     * @param length the maximum number of bytes read.
     * @return the total number of bytes read into the buffer, or
     *         <code>-1</code> if there is no more data because the end of
     *         the file has been reached.
     */
    public int readBytes(byte[] bytes, int offset, int length) {
        // default return value
        int returnValue = -1;
        try {
            InputStream in = getCorrectInputStream();
            if (in != null)
                returnValue = in.read(bytes, offset, length);
        }
        catch (IOException e) {
            logger.log(Level.FINE, "Error reading from temporary file ", e);
        }
        return returnValue;
    }

    /**
     * Reads up to <code>bytes.length</code> bytes of data from this input
     * stream into an array of bytes. This method blocks until some input
     * is available.
     *
     * @param bytes the buffer into which the data is read.
     * @return the total number of bytes read into the buffer, or
     *         <code>-1</code> if there is no more data because the end of
     *         the file has been reached.
     */
    public int readBytes(byte[] bytes) {
        int returnValue = -1;

        try {
            InputStream in = getCorrectInputStream();
            if (in != null)
                returnValue = in.read(bytes);
        }
        catch (IOException e) {
            logger.log(Level.FINE, "Error reading from temporary file ", e);
        }
        return returnValue;
    }

    /**
     * Reads a byte of data from this input stream. This method blocks
     * if no input is yet available.
     *
     * @return the next byte of data, or <code>-1</code> if the end of the
     *         file is reached.
     */
    public int readByte() {
        int returnValue = -1;

        try {
            InputStream in = getCorrectInputStream();
            if (in != null)
                returnValue = in.read();
        }
        catch (IOException e) {
            logger.log(Level.FINE, "Error reading from temporary file ", e);
        }
        return returnValue;
    }

    /**
     * Forces the caching of byte array to a temp file.  If the system property
     * corg.icepdf.core.util.bytecache.caching is set to false this method will
     * not write to file.
     */
    public void forceByteCaching() {
        if (isCachingEnabled && !isCached) {
            try {
                // Create a writable file channel
                if (tempFile == null) {
                    createTempFile();
                }
                // create output stream in needed
                if (fileOutputStream == null) {
                    fileOutputStream = new FileOutputStream(tempFile);
                }
                // write the bytes to the cache.
                if (byteArrayOutputStream != null)
                    fileOutputStream.write(byteArrayOutputStream.toByteArray());

                isCached = true;

                // clear byte arrays to free up some memory.
                byteArrayOutputStream = null;
                byteArrayInputStream = null;
            } catch (IOException e) {
                logger.log(Level.FINE, "Error creating the temp file.", e);
            }
        }
    }

    /**
     * Dispose this objects internal references and free up any available memory.
     *
     * @param cache Whether to make it so we can continue to use this object later
     */
    public void dispose(boolean cache) {
        try {
            // If not saved to file, but we'll need it later, and it's worth putting to file, then save to file
            if (!isCached && cache && length > fileCachingFallbackSize) {
                forceByteCaching();
            }

            if (fileOutputStream != null) {
                fileOutputStream.flush();
                fileOutputStream.close();
                fileOutputStream = null;
            }
            if (fileInputStream != null) {
                fileInputStream.close();
                fileInputStream = null;
            }
            if (byteArrayInputStream != null) {
                byteArrayInputStream.close();
                byteArrayInputStream = null;
            }
            if (byteArrayOutputStream != null) {
                byteArrayOutputStream.flush();
                byteArrayOutputStream.close();

                // If saved to file, or we don't need it later, then nuke our mem copy
                if (isCached || !cache) {
                    byteArrayOutputStream = null;
                }
            }

            if (!cache) {
                deleteFileCache();
            }
        } catch (IOException e) {
            logger.log(Level.FINE, "Error closing file streams ", e);
        }
    }

    /**
     * Creates a newly allocated byte array. Its size is the current
     * size of this output stream and the valid contents of the buffer
     * have been copied into it.
     *
     * @return the current contents of this byte cache, as a byte array.
     */
    public byte[] toByteArray() {
        byte[] returnValue = null;
        try {
            // if in cached mode read from the tmp file
            if (isCached) {
                if (tempFile != null) {
                    // Create a writable file channel, can't use instance as
                    // it will have funky side effects, for muliple calls.
                    FileInputStream fileInputStream = new FileInputStream(tempFile);
                    returnValue = new byte[length];
                    // copy the bytes
                    fileInputStream.read(returnValue);
                    fileInputStream.close();
                }
            } else {
                // copy origional data to output stream
                if (byteArrayOutputStream != null)
                    returnValue = byteArrayOutputStream.toByteArray();
            }
        }
        catch (IOException e) {
            logger.log(Level.FINE, "Error reading from temporary file.", e);
        }
        return returnValue;
    }

    /**
     * Dispose of temp file and bytes array.
     */
    public void deleteFileCache() {
        if (tempFile != null) {
            tempFile.delete();
        }
    }

    /**
     * Closes this file input stream and releases any system resources
     * associated with the stream.
     */
    public void close() {
        try {
            if (fileOutputStream != null) {
                fileOutputStream.close();
            }
            if (fileInputStream != null) {
                fileInputStream.close();
            }
            if (byteArrayInputStream != null) {
                byteArrayInputStream.close();
            }
            if (byteArrayOutputStream != null) {
                byteArrayOutputStream.close();
            }
        } catch (IOException e) {
            logger.log(Level.FINE, "Error closing file streams.", e);
        }
    }

    /**
     * Resets the buffer to the marked position.  The marked position
     * is 0 unless another position was marked.
     */
    public void reset() {
        try {
            // reseting non cached byte stream
            if (byteArrayInputStream != null) {
                byteArrayInputStream.reset();
            }
            // reseting cached byte stream
            if (fileInputStream != null) {
                fileInputStream.close();
                fileInputStream = null;
                fileInputStream = new FileInputStream(tempFile);
            }
        } catch (IOException e) {
            logger.log(Level.FINE, "Error closing file streams.", e);
        }
    }

    /**
     * Return if this byte cache has stored its byte data in a a temp file.
     *
     * @return true if the byte data has been cached to file; false otherwise.
     */
    public boolean isCached() {
        return isCached;
    }

    /**
     * Gets the length of the byte array stored in this byte cache.
     *
     * @return
     */
    public int getLength() {
        return length;
    }

    /**
     * @return true if all our bytes are in memory, so we won't have to go to disk for them
     */
    public boolean inMemory() {
        return byteArrayOutputStream != null;
    }

    /**
     * Utility method for creating tempfile
     */
    private void createTempFile() {
        try {
            // create tmp file and write bytes to it.
            tempFile = File.createTempFile("PDFByteStream" +
                    this.getClass().hashCode(),
                    ".tmp");

            // Delete temp file on exits, but dispose should do this too
            tempFile.deleteOnExit();

            // write file name to cache manager
            cacheManager.addCachedFile(tempFile.getAbsolutePath());

        } catch (IOException e) {
            logger.log(Level.FINE, "Error creating byte cache tmp file");
        }
    }

    private void calcIfFileCachingAndPotentiallyForce(int numNewBytes) {
        if (!isCached && (length + numNewBytes) > fileCachingSize)
            forceByteCaching();
    }

    private OutputStream getCorrectOutputStream(int optionalPresizing) throws IOException {
        if (isCached) {
            // create the tmp file if not initiated.
            if (tempFile == null) {
                createTempFile();
            }
            // create the new file stream
            if (fileOutputStream == null) {
                fileOutputStream = new FileOutputStream(tempFile.getAbsolutePath(), true);
            }
            return fileOutputStream;
        } else {
            // create the byte array if not initiated
            if (byteArrayOutputStream == null) {
                byteArrayOutputStream = new ByteArrayOutputStream(optionalPresizing);
            }
            return byteArrayOutputStream;
        }
    }

    private InputStream getCorrectInputStream() throws IOException {
        // if in cached mode use the tmp file
        if (isCached) {
            if (tempFile != null) {
                // initiate a new input stream in needed
                if (fileInputStream == null) {
                    fileInputStream = new FileInputStream(tempFile);
                }
                return fileInputStream;
            }
        } else {
            if (byteArrayOutputStream != null) {
                if (byteArrayInputStream == null) {
                    byteArrayInputStream =
                            new ByteArrayInputStream(
                                    byteArrayOutputStream.toByteArray());
                }
                return byteArrayInputStream;
            }
        }
        return null;
    }
}
