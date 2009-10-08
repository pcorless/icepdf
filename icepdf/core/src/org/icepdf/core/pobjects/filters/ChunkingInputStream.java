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
package org.icepdf.core.pobjects.filters;

import java.io.IOException;
import java.io.InputStream;

/**
 * Most of the filters have to read in a chunk of data from their input stream,
 * and do some processing on it, before they can make it available to others
 *
 * @author Mark Collette
 * @since 2.0
 */
public abstract class ChunkingInputStream extends InputStream {
    protected InputStream in;
    protected byte[] buffer;
    private int bufferPosition;
    private int bufferAvailable;

    public ChunkingInputStream() {
        in = null;
        buffer = null;
        bufferPosition = 0;
        bufferAvailable = 0;
    }

    protected void setInputStream(InputStream input) {
        in = input;
    }

    protected void setBufferSize(int size) {
        buffer = new byte[size];
    }

    /**
     * For some reason, when reading from InflaterInputStream, if we ask to
     * fill a buffer, it will instead only give us a chunk at a time,
     * even though more data is available, and buffer.length has bee requested
     *
     * @throws IOException
     */
    protected int fillBufferFromInputStream() throws IOException {
        return fillBufferFromInputStream(0, buffer.length);
    }

    protected int fillBufferFromInputStream(int offset, int length) throws IOException {
        int read = 0;
        while (read < length) {
            int currRead = in.read(buffer, offset + read, length - read);
            if (currRead < 0 && read == 0)
                return currRead;
            if (currRead <= 0)
                break;
            read += currRead;
        }
        return read;
    }

    /**
     * This is only called if bufferAvailable is 0.
     * Implementations should read in more data, and return how many bytes are now available
     */
    protected abstract int fillInternalBuffer() throws IOException;


    private int ensureDataAvailable() throws IOException {
        if (bufferAvailable > 0)
            return bufferAvailable;
        bufferPosition = 0;
        bufferAvailable = 0;
        int avail = fillInternalBuffer();
        if (avail > 0)
            bufferAvailable = avail;
        return bufferAvailable;
    }


    //
    // InputStream overrides
    //

    public boolean markSupported() {
        return false;
    }

    public void mark(int readlimit) {
    }

    public void reset() throws IOException {
    }

    public int read() throws IOException {
        int avail = ensureDataAvailable();
        if (avail <= 0)
            return -1;
        byte b = buffer[bufferPosition];
        bufferPosition++;
        bufferAvailable--;
        return (((int) b) & 0xFF);
    }

    public int read(byte[] b) throws IOException {
        return read(b, 0, b.length);
    }

    public int read(byte[] b, int off, int length) throws IOException {
        int read = 0;
        while (read < length) {
            int avail = ensureDataAvailable();
            if (avail <= 0) {
                if (read > 0)
                    return read;
                else
                    return -1;
            }

            int toRead = Math.min(length - read, avail);
            int srcIdx = bufferPosition;
            int dstIdx = off + read;
            for (int i = 0; i < toRead; i++)
                b[dstIdx++] = buffer[srcIdx++];
            bufferPosition += toRead;
            bufferAvailable -= toRead;
            read += toRead;
        }
        return read;
    }

    public long skip(long n) throws IOException {
        long skipped = 0L;
        while (skipped < n) {
            int avail = ensureDataAvailable();
            if (avail <= 0) {
                if (skipped > 0L)
                    return skipped;
                else
                    return -1;
            }

            long toSkip = Math.min(n - skipped, avail);
            bufferPosition += toSkip;
            bufferAvailable -= toSkip;
            skipped += toSkip;
        }
        return skipped;
    }

    public int available() throws IOException {
        return bufferAvailable;
    }

    public void close() throws IOException {
        if (in != null) {
            in.close();
            in = null;
        }
    }


    public String toString() {
        StringBuffer sb = new StringBuffer();
        sb.append(super.toString());
        sb.append(": ");
        if (in == null)
            sb.append("null");
        else
            sb.append(in.toString());
        return sb.toString();
    }
}
