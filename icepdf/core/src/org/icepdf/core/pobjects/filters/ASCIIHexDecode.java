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
 * The Original Code is ICEpdf 4.1 open source software code, released
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
package org.icepdf.core.pobjects.filters;

import org.icepdf.core.util.Parser;

import java.io.IOException;
import java.io.InputStream;

/**
 * @author Mark Collette
 * @since 2.0
 */
public class ASCIIHexDecode extends ChunkingInputStream {
    public ASCIIHexDecode(InputStream input) {
        super();

        setInputStream(input);
        setBufferSize(4096);
    }

    protected int fillInternalBuffer() throws IOException {
        int numRead = 0;

        for (int i = 0; i < buffer.length; i++) {
            byte val = 0;
            int hi;
            int lo;
            do {
                hi = in.read();
            } while (Parser.isWhitespace((char) hi));
            if (hi < 0)
                break;
            do {
                lo = in.read();
            } while (Parser.isWhitespace((char) lo));

            if (hi >= '0' && hi <= '9') {
                hi -= '0';
                val |= ((byte) ((hi << 4) & 0xF0));
            } else if (hi >= 'a' && hi <= 'z') {
                hi = hi - 'a' + 10;
                val |= ((byte) ((hi << 4) & 0xF0));
            } else if (hi >= 'A' && hi <= 'Z') {
                hi = hi - 'A' + 10;
                val |= ((byte) ((hi << 4) & 0xF0));
            }

            if (lo >= 0) {
                if (lo >= '0' && lo <= '9') {
                    lo -= '0';
                    val |= ((byte) (lo & 0x0F));
                } else if (lo >= 'a' && lo <= 'z') {
                    lo = lo - 'a' + 10;
                    val |= ((byte) (lo & 0x0F));
                } else if (lo >= 'A' && lo <= 'Z') {
                    lo = lo - 'A' + 10;
                    val |= ((byte) (lo & 0x0F));
                }
            }
            buffer[numRead++] = val;
        }

        if (numRead == 0)
            return -1;
        return numRead;
    }
}
