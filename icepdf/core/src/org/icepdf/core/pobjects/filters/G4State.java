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

/**
 * This is a utility class that aids the decoding of CCITT 4 2D encoded data.
 * CCITTFax is the parent class that uses this class to keep track of the
 * locations of the B & W bit locations with in the stream
 */
class G4State {
    int[] ref;
    int[] cur;
    boolean white = true; // colour reference
    int a0; // The reference element on the coding line
    int b1; // The next changing element on the reference line to the right of a0 and of opppsite color of a0
    int refIndex; // the previous scan line
    int curIndex; // the current scan line
    int runLength;
    int width;
    int longrun;

    /**
     * Greate a new instance of a G4State.
     *
     * @param w width of the line being looked at.
     */
    G4State(int w) {
        width = w;
        ref = new int[width + 1];
        cur = new int[width + 1];
        a0 = 0;
        b1 = width;
        ref[0] = width;
        ref[1] = 0;
        runLength = 0;
        longrun = 0;
        refIndex = 1;
        curIndex = 0;
    }
}
