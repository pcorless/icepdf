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
package org.icepdf.core.pobjects.filters;

import java.io.IOException;
import java.io.InputStream;

/**
 * @author Mark Collette
 * @since 2.0
 */

public class ASCII85Decode extends ChunkingInputStream {
    private boolean eof = false;

    public ASCII85Decode(InputStream input) {
        super();

        setInputStream(input);
        setBufferSize(4);
    }

    protected int fillInternalBuffer() throws IOException {
        if (eof)
            return -1;
        long value = 0;
        int count = 0;
        long c = 0;
        while (true) {
            c = in.read();
            if (c < 0) {
                eof = true;
                break;
            }
            if (c == 0x00 || c == 0x09 || c == 0x0a || c == 0x0c || c == 0x0d || c == 0x20)
                continue;
            if (c == 126) { // '~'
                eof = true;
                break;
            }
            if (c == 122) { // 'z'
                buffer[0] = 0;
                buffer[1] = 0;
                buffer[2] = 0;
                buffer[3] = 0;
                count = 0;
                return 4;
            }
            count++;
            value = value * 85 + (c - 33);
            if (count == 5) {
                buffer[0] = (byte) ((value >> 24) & 0xFF);
                buffer[1] = (byte) ((value >> 16) & 0xFF);
                buffer[2] = (byte) ((value >> 8) & 0xFF);
                buffer[3] = (byte) (value & 0xFF);
                value = 0;
                count = 0;
                return 4;
            }
        }
        if (count == 2) {
            value = value * (85L * 85 * 85) + 0xFFFFFF;
        } else if (count == 3) {
            value = value * (85L * 85) + 0xFFFF;
        } else if (count == 4) {
            value = value * (85L) + 0xFF;
        }
        if (count >= 2)
            buffer[0] = (byte) ((value >> 24) & 0xFF);
        if (count >= 3)
            buffer[1] = (byte) ((value >> 16) & 0xFF);
        if (count >= 4)
            buffer[2] = (byte) ((value >> 8) & 0xFF);
        return count - 1;
    }
}
