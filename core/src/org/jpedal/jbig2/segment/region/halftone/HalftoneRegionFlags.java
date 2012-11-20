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
package org.jpedal.jbig2.segment.region.halftone;

import org.jpedal.jbig2.decoders.JBIG2StreamDecoder;
import org.jpedal.jbig2.segment.Flags;

public class HalftoneRegionFlags extends Flags {

    public static String H_MMR = "H_MMR";
    public static String H_TEMPLATE = "H_TEMPLATE";
    public static String H_ENABLE_SKIP = "H_ENABLE_SKIP";
    public static String H_COMB_OP = "H_COMB_OP";
    public static String H_DEF_PIXEL = "H_DEF_PIXEL";

    public void setFlags(int flagsAsInt) {
        this.flagsAsInt = flagsAsInt;

        /** extract H_MMR */
        flags.put(H_MMR, new Integer(flagsAsInt & 1));

        /** extract H_TEMPLATE */
        flags.put(H_TEMPLATE, new Integer((flagsAsInt >> 1) & 3));

        /** extract H_ENABLE_SKIP */
        flags.put(H_ENABLE_SKIP, new Integer((flagsAsInt >> 3) & 1));

        /** extract H_COMB_OP */
        flags.put(H_COMB_OP, new Integer((flagsAsInt >> 4) & 7));

        /** extract H_DEF_PIXEL */
        flags.put(H_DEF_PIXEL, new Integer((flagsAsInt >> 7) & 1));


        if (JBIG2StreamDecoder.debug)
            System.out.println(flags);
    }
}
