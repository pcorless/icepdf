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

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Vector;

/**
 * @author Mark Collette
 * @since 2.0
 */
public class SequenceInputStream extends InputStream {
    private Iterator<InputStream> m_itInputStreams;
    private InputStream m_isCurrent;

    public SequenceInputStream(InputStream in1, InputStream in2) {
        ArrayList<InputStream> lst = new ArrayList<InputStream>(2);
        lst.add(in1);
        lst.add(in2);
        m_itInputStreams = lst.iterator();

        try {
            useNextInputStream();
        }
        catch (IOException e) {
            throw new java.lang.IllegalStateException("Could not use first InputStream in SequenceInputStream(List) : " + e);
        }
    }

    public SequenceInputStream(Iterator<InputStream> inputStreams) {
        m_itInputStreams = inputStreams;

        try {
            useNextInputStream();
        }
        catch (IOException e) {
            throw new java.lang.IllegalStateException("Could not use first InputStream in SequenceInputStream(List) : " + e);
        }
    }

    private InputStream getCurrentInputStream() {
        return m_isCurrent;
    }

    private void useNextInputStream() throws IOException {
        closeCurrentInputStream();

        m_isCurrent = null;
        while (m_itInputStreams.hasNext()) {
            InputStream in = m_itInputStreams.next();
            if (in != null) {
                m_isCurrent = in;
                break;
            }
        }
    }

    private void closeCurrentInputStream() throws IOException {
        InputStream in = getCurrentInputStream();
        if (in != null)
            in.close();
    }


    public int available() throws IOException {
        InputStream in = getCurrentInputStream();
        if (in != null)
            return in.available();
        return 0;
    }

    public int read() throws IOException {
        while (true) {
            InputStream in = getCurrentInputStream();
            if (in == null) {
                useNextInputStream();
                in = getCurrentInputStream();
                if (in == null)
                    return -1;
            }

            int readByte = in.read();
            if (readByte >= 0)
                return readByte;
            useNextInputStream();
        }
    }

    public int read(byte buffer[], int off, int len) throws IOException {
        if (buffer == null) {
            throw new NullPointerException();
        } else if ((off < 0) || (off >= buffer.length) ||
                (len < 0) || ((off + len) > buffer.length) ||
                ((off + len) < 0)) {
            throw new IndexOutOfBoundsException("Offset: " + off + ", Length: " + len + ", Buffer length: " + buffer.length);
        } else if (len == 0)
            return 0;

        int totalRead = 0;
        while (totalRead < len) {
            InputStream in = getCurrentInputStream();
            if (in == null) {
                useNextInputStream();
                in = getCurrentInputStream();
                if (in == null) {
                    if (totalRead > 0) {
                        break;
                    }
                    return -1;
                }
            }

            int currRead = in.read(buffer, off + totalRead, len - totalRead);
            if (currRead > 0) {
                totalRead += currRead;
            } else {
                useNextInputStream();
            }
        }

        return totalRead;
    }

    public void close() throws IOException {
        do {
            useNextInputStream();
        } while (getCurrentInputStream() != null);
    }

    public boolean markSupported() {
        return super.markSupported();
    }

    public void mark(int readlimit) {
        super.mark(readlimit);
    }

    public void reset() throws IOException {
        super.reset();
    }


    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(getClass().getName());
        sb.append(": ");

        Vector<InputStream> inputStreams = new Vector<InputStream>();
        while (m_itInputStreams.hasNext()) {
            InputStream in = m_itInputStreams.next();
            sb.append("\n  ");
            sb.append(in.toString());
            sb.append(",");
            inputStreams.add(in);
        }
        m_itInputStreams = inputStreams.iterator();

        sb.append('\n');
        return sb.toString();
    }
}
