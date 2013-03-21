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
package org.jpedal.jbig2.segment.symboldictionary;

import org.jpedal.jbig2.decoders.JBIG2StreamDecoder;
import org.jpedal.jbig2.segment.Flags;

public class SymbolDictionaryFlags extends Flags {

    public static String SD_HUFF = "SD_HUFF";
    public static String SD_REF_AGG = "SD_REF_AGG";
    public static String SD_HUFF_DH = "SD_HUFF_DH";
    public static String SD_HUFF_DW = "SD_HUFF_DW";
    public static String SD_HUFF_BM_SIZE = "SD_HUFF_BM_SIZE";
    public static String SD_HUFF_AGG_INST = "SD_HUFF_AGG_INST";
    public static String BITMAP_CC_USED = "BITMAP_CC_USED";
    public static String BITMAP_CC_RETAINED = "BITMAP_CC_RETAINED";
    public static String SD_TEMPLATE = "SD_TEMPLATE";
    public static String SD_R_TEMPLATE = "SD_R_TEMPLATE";

    public void setFlags(int flagsAsInt) {
        this.flagsAsInt = flagsAsInt;

        /** extract SD_HUFF */
        flags.put(SD_HUFF, new Integer(flagsAsInt & 1));

        /** extract SD_REF_AGG */
        flags.put(SD_REF_AGG, new Integer((flagsAsInt >> 1) & 1));

        /** extract SD_HUFF_DH */
        flags.put(SD_HUFF_DH, new Integer((flagsAsInt >> 2) & 3));

        /** extract SD_HUFF_DW */
        flags.put(SD_HUFF_DW, new Integer((flagsAsInt >> 4) & 3));

        /** extract SD_HUFF_BM_SIZE */
        flags.put(SD_HUFF_BM_SIZE, new Integer((flagsAsInt >> 6) & 1));

        /** extract SD_HUFF_AGG_INST */
        flags.put(SD_HUFF_AGG_INST, new Integer((flagsAsInt >> 7) & 1));

        /** extract BITMAP_CC_USED */
        flags.put(BITMAP_CC_USED, new Integer((flagsAsInt >> 8) & 1));

        /** extract BITMAP_CC_RETAINED */
        flags.put(BITMAP_CC_RETAINED, new Integer((flagsAsInt >> 9) & 1));

        /** extract SD_TEMPLATE */
        flags.put(SD_TEMPLATE, new Integer((flagsAsInt >> 10) & 3));

        /** extract SD_R_TEMPLATE */
        flags.put(SD_R_TEMPLATE, new Integer((flagsAsInt >> 12) & 1));

        if (JBIG2StreamDecoder.debug)
            System.out.println(flags);
    }
}
