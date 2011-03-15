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
 * 2004-2011 ICEsoft Technologies Canada, Corp. All Rights Reserved.
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

import java.io.IOException;
import java.io.InputStream;

/**
 * @author Mark Collette
 * @since 2.0
 */
public class SeekableInputConstrainedWrapper extends InputStream {
    private SeekableInput streamDataInput;
    private long filePositionOfStreamData;
    private long lengthOfStreamData;
    private long filePositionBeforeUse;
    private boolean takeOwnershipOfStreamDataInput;
    private boolean usedYet;

    public SeekableInputConstrainedWrapper(
            SeekableInput in, long offset, long length, boolean takeOwnership) {
        streamDataInput = in;
        filePositionOfStreamData = offset;
        lengthOfStreamData = length;
        filePositionBeforeUse = 0L;
        takeOwnershipOfStreamDataInput = takeOwnership;
        usedYet = false;
    }

    public void prepareForCurrentUse() {
        usedYet = false;
    }

    public void dispose() throws IOException {
        beginThreadAccess();
//System.out.println("SICW.endFinalUse()  About to ... " + this);
        if (takeOwnershipOfStreamDataInput) {
            if (streamDataInput != null) {
                streamDataInput.close();
                endThreadAccess();
                streamDataInput = null;
            }
        } else {
            endCurrentUse(); // In case using code forgot
        }
//System.out.println("SICW.endFinalUse()  ... Done     " + this);
    }

    private void ensureReadyOnFirstUse() throws IOException {
        beginThreadAccess();
        if (usedYet)
            return;
        usedYet = true;

//System.out.println("SICW.ensureReadyOnFirstUse()  About to ... " + this);
        filePositionBeforeUse = streamDataInput.getAbsolutePosition();
        streamDataInput.seekAbsolute(filePositionOfStreamData);
//System.out.println("SICW.ensureReadyOnFirstUse()  ... Done     " + this);
    }

    private void endCurrentUse() throws IOException {
//System.out.println("SICW.endCurrentUse()  About to ... " + this);
        if (usedYet) {
            streamDataInput.seekAbsolute(filePositionBeforeUse);
            usedYet = false;
        }
        endThreadAccess();
//System.out.println("SICW.endCurrentUse()  ... Done     " + this);
    }

    private long getBytesRemaining() throws IOException {
        long absPos = streamDataInput.getAbsolutePosition();
        if (absPos < filePositionOfStreamData)
            return -1;
        long end = filePositionOfStreamData + lengthOfStreamData;
        return end - absPos;
    }


    //
    // Methods from InputStream
    // Since Java does not have multiple inheritance, we have to
    //  explicitly expose InputStream's methods as part of our interface
    //
    public int read() throws IOException {
        ensureReadyOnFirstUse();
        long remain = getBytesRemaining();
//System.out.println("SICW.read()  remain: " + remain);
        if (remain <= 0)
            return -1;
        return streamDataInput.read();
    }

    public int read(byte[] buffer) throws IOException {
        return read(buffer, 0, buffer.length);
    }

    public int read(byte[] buffer, int offset, int length) throws IOException {
        ensureReadyOnFirstUse();
        long remain = getBytesRemaining();
//System.out.println("SICW.read( "+length+" )  remain: " + remain);
        if (remain <= 0)
            return -1;
        length = (int) Math.min(Math.min(remain, (long) length), (long) Integer.MAX_VALUE);
        return streamDataInput.read(buffer, offset, length);
    }

    public void close() throws IOException {
        // It's necessary for any InputStreams which are chained together
        //   to close themselves, and so release their resources, but
        //   this class, and the SeekableInput that we wrap, are
        //   intended to be reused, so close() should only end the current use
        beginThreadAccess();
        endCurrentUse();
    }

    public int available() {
        return 0;
    }

    public void mark(int readLimit) {
    }

    public boolean markSupported() {
        return false;
    }

    public void reset() throws IOException {
    }

    public long skip(long n) throws IOException {
        ensureReadyOnFirstUse();
        long remain = getBytesRemaining();
        if (remain <= 0)
            return -1;
        n = (int) Math.min(Math.min(remain, (long) n), (long) Integer.MAX_VALUE);
        return streamDataInput.skip(n);
    }


    //
    // Special methods that make this truly seekable
    //

    public void seekAbsolute(long absolutePosition) throws IOException {
        ensureReadyOnFirstUse();
        // The wrapper exists in a different coordinate system,
        //   where its beginning is location 0
        if (absolutePosition < 0L)
            throw new IOException("Attempt to absolutely seek to negative location: " + absolutePosition);
        // It's alright to seek beyond the end, it's just that read operations will fail
        absolutePosition += filePositionOfStreamData;
        streamDataInput.seekAbsolute(absolutePosition);
    }

    public void seekRelative(long relativeOffset) throws IOException {
        ensureReadyOnFirstUse();
        long pos = streamDataInput.getAbsolutePosition();
        pos += relativeOffset;
        if (pos < filePositionOfStreamData)
            pos = filePositionOfStreamData;
        // It's alright to seek beyond the end, it's just that read operations will fail
        streamDataInput.seekAbsolute(pos);
    }

    public void seekEnd() throws IOException {
        ensureReadyOnFirstUse();
        streamDataInput.seekAbsolute(filePositionOfStreamData + lengthOfStreamData);
    }

    public long getAbsolutePosition() throws IOException {
        ensureReadyOnFirstUse();
        long absolutePosition = getAbsolutePosition();
        absolutePosition -= filePositionOfStreamData;
        return absolutePosition;
    }

    public long getLength() {
        return lengthOfStreamData;
    }

    // To access InputStream methods, call this instead of casting
    // This InputStream has to support mark(), reset(), and obviously markSupported()
    public InputStream getInputStream() {
        return this;
    }


    public void beginThreadAccess() {
        if (streamDataInput != null)
            streamDataInput.beginThreadAccess();
    }

    public void endThreadAccess() {
        if (streamDataInput != null)
            streamDataInput.endThreadAccess();
    }


    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(super.toString());
        sb.append(" ( ");
        sb.append("pos=").append(filePositionOfStreamData).append(", ");
        sb.append("len=").append(lengthOfStreamData).append(", ");
        sb.append("posToRestore=").append(filePositionBeforeUse).append(", ");
        sb.append("own=").append(takeOwnershipOfStreamDataInput).append(", ");
        sb.append("usedYet=").append(usedYet);
        sb.append(" ) ");
        sb.append(": ");
        if (streamDataInput == null)
            sb.append("null ");
        else
            sb.append(streamDataInput.toString());
        return sb.toString();
    }
}
