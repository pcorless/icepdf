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
package org.icepdf.core.io;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.logging.Logger;
import java.util.logging.Level;

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
                }
                catch (InterruptedException ie) {
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
