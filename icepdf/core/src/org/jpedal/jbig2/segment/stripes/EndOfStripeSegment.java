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
package org.jpedal.jbig2.segment.stripes;

import org.jpedal.jbig2.JBIG2Exception;
import org.jpedal.jbig2.decoders.JBIG2StreamDecoder;
import org.jpedal.jbig2.segment.Segment;

import java.io.IOException;

public class EndOfStripeSegment extends Segment {

    public EndOfStripeSegment(JBIG2StreamDecoder streamDecoder) {
        super(streamDecoder);
    }

    public void readSegment() throws IOException, JBIG2Exception {
        for (int i = 0; i < this.getSegmentHeader().getSegmentDataLength(); i++) {
            decoder.readByte();
        }
    }
}
