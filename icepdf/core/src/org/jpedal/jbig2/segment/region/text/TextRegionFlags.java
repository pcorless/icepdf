/*
 * Copyright 2006-2013 ICEsoft Technologies Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an "AS
 * IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */
package org.jpedal.jbig2.segment.region.text;

import org.jpedal.jbig2.decoders.JBIG2StreamDecoder;
import org.jpedal.jbig2.segment.Flags;

public class TextRegionFlags extends Flags {

    public static String SB_HUFF = "SB_HUFF";
    public static String SB_REFINE = "SB_REFINE";
    public static String LOG_SB_STRIPES = "LOG_SB_STRIPES";
    public static String REF_CORNER = "REF_CORNER";
    public static String TRANSPOSED = "TRANSPOSED";
    public static String SB_COMB_OP = "SB_COMB_OP";
    public static String SB_DEF_PIXEL = "SB_DEF_PIXEL";
    public static String SB_DS_OFFSET = "SB_DS_OFFSET";
    public static String SB_R_TEMPLATE = "SB_R_TEMPLATE";

    public void setFlags(int flagsAsInt) {
        this.flagsAsInt = flagsAsInt;

        /** extract SB_HUFF */
        flags.put(SB_HUFF, new Integer(flagsAsInt & 1));

        /** extract SB_REFINE */
        flags.put(SB_REFINE, new Integer((flagsAsInt >> 1) & 1));

        /** extract LOG_SB_STRIPES */
        flags.put(LOG_SB_STRIPES, new Integer((flagsAsInt >> 2) & 3));

        /** extract REF_CORNER */
        flags.put(REF_CORNER, new Integer((flagsAsInt >> 4) & 3));

        /** extract TRANSPOSED */
        flags.put(TRANSPOSED, new Integer((flagsAsInt >> 6) & 1));

        /** extract SB_COMB_OP */
        flags.put(SB_COMB_OP, new Integer((flagsAsInt >> 7) & 3));

        /** extract SB_DEF_PIXEL */
        flags.put(SB_DEF_PIXEL, new Integer((flagsAsInt >> 9) & 1));

        int sOffset = (flagsAsInt >> 10) & 0x1f;
        if ((sOffset & 0x10) != 0) {
            sOffset |= -1 - 0x0f;
        }
        flags.put(SB_DS_OFFSET, new Integer(sOffset));

        /** extract SB_R_TEMPLATE */
        flags.put(SB_R_TEMPLATE, new Integer((flagsAsInt >> 15) & 1));

        if (JBIG2StreamDecoder.debug)
            System.out.println(flags);
    }
}
