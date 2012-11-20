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
package org.icepdf.core.io;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Mark Collette
 * @since 2.0
 */
public class SeekableByteArrayInputStream extends ByteArrayInputStream implements SeekableInput {

    private static final Logger log =
            Logger.getLogger(SeekableByteArrayInputStream.class.toString());

    private int m_iBeginningOffset;

    private Object m_oCurrentUser;

    public SeekableByteArrayInputStream(byte buf[]) {
        super(buf);
        m_iBeginningOffset = 0;

    }

    public SeekableByteArrayInputStream(byte buf[], int offset, int length) {
        super(buf, offset, length);
        m_iBeginningOffset = offset;

    }


    //
    // SeekableInput implementation
    //  (which are not already covered by InputStream overrides)
    //

    public void seekAbsolute(long absolutePosition) {
        int absPos = (int) (absolutePosition & 0xFFFFFFFF);
        pos = m_iBeginningOffset + absPos;
    }

    public void seekRelative(long relativeOffset) {
        int relOff = (int) (relativeOffset & 0xFFFFFFFF);
        int currPos = pos + relOff;
        if (currPos < m_iBeginningOffset)
            currPos = m_iBeginningOffset;
        pos = currPos;
    }

    public void seekEnd() {
        seekAbsolute(getLength());
    }

    public long getAbsolutePosition() {
        int absPos = pos - m_iBeginningOffset;
        return (((long) absPos) & 0xFFFFFFFF);
    }

    public long getLength() {
        int len = count - m_iBeginningOffset;
        return (((long) len) & 0xFFFFFFFF);
    }

    public InputStream getInputStream() {
        return this;
    }


    public synchronized void beginThreadAccess() {
        Object requestingUser = Thread.currentThread();
        while (true) {
            if (m_oCurrentUser == null) {
                m_oCurrentUser = requestingUser;
                break;
            } else if (m_oCurrentUser == requestingUser) {
                break;
            } else { // Some other Thread is currently using us
                try {
                    this.wait(100L);
                } catch (InterruptedException ie) {
                }
            }
        }
    }

    public synchronized void endThreadAccess() {
        Object requestingUser = Thread.currentThread();
        if (m_oCurrentUser == null) {
            this.notifyAll();
        } else if (m_oCurrentUser == requestingUser) {
            m_oCurrentUser = null;
            this.notifyAll();
        } else { // Some other Thread is currently using us
            if (log.isLoggable(Level.SEVERE)) {
                log.severe(
                        "ERROR:  Thread finished using SeekableInput, but it wasn't locked by that Thread\n" +
                                "        Thread: " + Thread.currentThread() + "\n" +
                                "        Locking Thread: " + m_oCurrentUser + "\n" +
                                "        SeekableInput: " + this);
            }
        }
    }
}
