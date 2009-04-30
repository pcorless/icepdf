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
 * 2004-2009 ICEsoft Technologies Canada, Corp. All Rights Reserved..
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
package org.icepdf.core.io;

import org.icepdf.core.util.MemoryManager;

import java.io.IOException;
import java.io.OutputStream;

/**
 * @author Mark Collette
 * @since 2.0
 */
public class ConservativeSizingByteArrayOutputStream extends OutputStream {
    protected MemoryManager memoryManager;
    protected byte buf[];
    protected int count;

    /**
     * Creates a new byte array output stream, with the given initial
     * buffer capacity
     *
     * @param capacity The initial capacity
     * @throws IllegalArgumentException if capacity is negative
     */
    public ConservativeSizingByteArrayOutputStream(int capacity, MemoryManager mm) {
        if (capacity < 0) {
            throw new IllegalArgumentException("Negative initial capacity: " + capacity);
        }
        memoryManager = mm;
        buf = allocateByteArray(capacity, false);
        count = 0;
    }

    /**
     * Creates a new byte array output stream, with the given initial
     * buffer
     *
     * @param buffer The initial buffer
     * @throws IllegalArgumentException if capacity is negative
     */
    public ConservativeSizingByteArrayOutputStream(byte[] buffer, MemoryManager mm) {
        if (buffer == null)
            throw new IllegalArgumentException("Initial buffer is null");
        else if (buffer.length == 0)
            throw new IllegalArgumentException("Initial buffer has zero length");
        memoryManager = mm;
        buf = buffer;
        count = 0;
    }

    public void setMemoryManager(MemoryManager mm) {
        memoryManager = mm;
    }

    public synchronized void write(int b) throws IOException {
        int newCount = count + 1;
        if (newCount > buf.length)
            resizeArrayToFit(newCount);
        buf[count] = (byte) b;
        count = newCount;
    }

    public synchronized void write(byte b[], int off, int len) throws IOException {
        if ((off < 0) || (off >= b.length) || (len < 0) ||
                ((off + len) > b.length) || ((off + len) < 0)) {
            throw new IndexOutOfBoundsException();
        }
        if (len == 0)
            return;
        int newCount = count + len;
        if (newCount > buf.length)
            resizeArrayToFit(newCount);
        System.arraycopy(b, off, buf, count, len);
        count = newCount;
    }

    public synchronized void reset() {
        count = 0;
    }

    /**
     * Creates a newly allocated byte array. Its length is equal to the
     * current count of bytes in this output stream. The data bytes are
     * then copied into it.
     *
     * @return The current contents of this output stream, as a byte array.
     */
    public synchronized byte[] toByteArray() {
        byte newBuf[] = allocateByteArray(count, false);
        System.arraycopy(buf, 0, newBuf, 0, count);
        return newBuf;
    }

    /**
     * Returns the current size of the buffer.
     *
     * @return The number of valid bytes in this output stream.
     */
    public int size() {
        return count;
    }

    /**
     * Allows the caller to take ownership of this output stream's
     * byte array. Note that this output stream will then make
     * a new small buffer for itself and reset its size information,
     * meaning that you should call size() before this.
     */
    public synchronized byte[] relinquishByteArray() {
        byte[] returnBuf = buf;
        buf = null;
        buf = new byte[64];
        count = 0;
        return returnBuf;
    }

    /**
     * @return true, if there was enough memory to trim buf; false otherwise
     */
    public boolean trim() {
        if (count == 0 && (buf == null || buf.length == 0))
            return true;
        if (count == buf.length)
            return true;

        byte newBuf[] = allocateByteArray(count, true);
        if (newBuf == null)
            return false;
        System.arraycopy(buf, 0, newBuf, 0, count);
        buf = null;
        buf = newBuf;
        return true;
    }

    protected void resizeArrayToFit(int newCount) {
        int steppedSize = buf.length;
        if (steppedSize == 0)
            steppedSize = 64;
        else if (steppedSize <= 1024)
            steppedSize *= 4;
        else if (steppedSize <= 4024)
            steppedSize *= 2;
        else if (steppedSize <= 2 * 1024 * 1024) {
            steppedSize *= 2;
            steppedSize &= (~0x0FFF);           // Fit on even 4KB pages
        } else if (steppedSize <= 4 * 1024 * 1024) {
            steppedSize = (steppedSize * 3) / 2;  // x 1.50
            steppedSize &= (~0x0FFF);           // Fit on even 4KB pages
        } else if (steppedSize <= 15 * 1024 * 1024) {
            steppedSize = (steppedSize * 5) / 4;  // x 1.25
            steppedSize &= (~0x0FFF);           // Fit on even 4KB pages
        } else {
            steppedSize = (steppedSize + (3 * 1024 * 1024));  // Go up in 3MB increments
            steppedSize &= (~0x0FFF);           // Fit on even 4KB pages
        }

        int newBufSize = Math.max(steppedSize, newCount);
        byte newBuf[] = allocateByteArray(newBufSize, false);
        System.arraycopy(buf, 0, newBuf, 0, count);
        buf = null;
        buf = newBuf;
    }

    protected byte[] allocateByteArray(int size, boolean returnNullIfNoMemory) {
        boolean canAlloc = true;
        if (memoryManager != null && size >= 512 * 1024)
            canAlloc = memoryManager.checkMemory(size);
        if (returnNullIfNoMemory && !canAlloc)
            return null;
        return new byte[size];
    }
}
