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
 * 2004-2010 ICEsoft Technologies Canada, Corp. All Rights Reserved.
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
 * When decoding CCITTFaxDecode images via JAI, we sometimes have
 * to zero pad the input data, otherwise JAI will fail instead
 * of it gracefully assuming zero data itself.
 * As well, with some inline images, we can have some trailing
 * whitespace that should be removed.
 * It's typical to have to remove the whitespace and add the zeros.
 *
 * @author Mark Collette
 * @since 2.2
 */

public class ZeroPaddedInputStream extends InputStream {
    private InputStream in;

    public ZeroPaddedInputStream(InputStream in) {
        this.in = in;
    }

    //
    // Methods from InputStream
    //
    public int read() throws IOException {
        int r = in.read();
//System.out.println("ZPIS.read()  r: " + r);
        if (r < 0)
            return 0;
        return r;
    }

    public int read(byte[] buffer) throws IOException {
        return read(buffer, 0, buffer.length);
    }

    public int read(byte[] buffer, int offset, int length) throws IOException {
        int readIn = in.read(buffer, offset, length);
//System.out.println("ZPIS.read( "+length+" )  readIn: " + readIn);
        if (readIn < length) {
//String str = new String( buffer, offset, Math.max(0, readIn) );
//System.out.println( str );
//System.out.println("-----------");
//System.out.println( org.icepdf.core.util.Utils.convertByteArrayToHexString(buffer, offset, Math.max(0, readIn), true, 0, '0') );
            if (readIn > 0) {
                while ((buffer[offset + readIn - 1] == 0x0A || buffer[offset + readIn - 1] == 0x0D || buffer[offset + readIn - 1] == 0x20) && readIn > 0)
                    readIn--;
                return readIn;
            }
            int end = offset + length;
            for (int current = offset + Math.max(0, readIn); current < end; current++)
                buffer[current] = 0;
        }
        return length;
    }

    public void close() throws IOException {
        in.close();
    }

    public int available() throws IOException {
        int a = in.available();
//System.out.println("ZPIS.available() : " + a);
        if (a <= 0)
            a = 1;
        return a;
    }

    public void mark(int readLimit) {
        in.mark(readLimit);
    }

    public boolean markSupported() {
        return in.markSupported();
    }

    public void reset() throws IOException {
        in.reset();
    }

    public long skip(long n) throws IOException {
        long s = in.skip(n);
//System.out.println("ZPIS.skip( " + n + " ) : " + s);
        return s;
    }
}
