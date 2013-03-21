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
package org.jpedal.jbig2.segment.pageinformation;

import org.jpedal.jbig2.decoders.JBIG2StreamDecoder;
import org.jpedal.jbig2.segment.Flags;

public class PageInformationFlags extends Flags {

    public static String DEFAULT_PIXEL_VALUE = "DEFAULT_PIXEL_VALUE";
    public static String DEFAULT_COMBINATION_OPERATOR = "DEFAULT_COMBINATION_OPERATOR";

    public void setFlags(int flagsAsInt) {
        this.flagsAsInt = flagsAsInt;

        /** extract DEFAULT_PIXEL_VALUE */
        flags.put(DEFAULT_PIXEL_VALUE, new Integer((flagsAsInt >> 2) & 1));

        /** extract DEFAULT_COMBINATION_OPERATOR */
        flags.put(DEFAULT_COMBINATION_OPERATOR, new Integer((flagsAsInt >> 3) & 3));

        if (JBIG2StreamDecoder.debug)
            System.out.println(flags);
    }
}
