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
package org.jpedal.jbig2.segment.pattern;

import org.jpedal.jbig2.decoders.JBIG2StreamDecoder;
import org.jpedal.jbig2.segment.Flags;

public class PatternDictionaryFlags extends Flags {

    public static String HD_MMR = "HD_MMR";
    public static String HD_TEMPLATE = "HD_TEMPLATE";

    public void setFlags(int flagsAsInt) {
        this.flagsAsInt = flagsAsInt;

        /** extract HD_MMR */
        flags.put(HD_MMR, new Integer(flagsAsInt & 1));

        /** extract HD_TEMPLATE */
        flags.put(HD_TEMPLATE, new Integer((flagsAsInt >> 1) & 3));

        if (JBIG2StreamDecoder.debug)
            System.out.println(flags);
    }
}
