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
package org.jpedal.jbig2.segment.region;

import org.jpedal.jbig2.JBIG2Exception;
import org.jpedal.jbig2.decoders.JBIG2StreamDecoder;
import org.jpedal.jbig2.segment.Segment;
import org.jpedal.jbig2.util.BinaryOperation;

import java.io.IOException;

public abstract class RegionSegment extends Segment {
    protected int regionBitmapWidth, regionBitmapHeight;
    protected int regionBitmapXLocation, regionBitmapYLocation;

    protected RegionFlags regionFlags = new RegionFlags();

    public RegionSegment(JBIG2StreamDecoder streamDecoder) {
        super(streamDecoder);
    }

    public void readSegment() throws IOException, JBIG2Exception {
        short[] buff = new short[4];
        decoder.readByte(buff);
        regionBitmapWidth = BinaryOperation.getInt32(buff);

        buff = new short[4];
        decoder.readByte(buff);
        regionBitmapHeight = BinaryOperation.getInt32(buff);

        if (JBIG2StreamDecoder.debug)
            System.out.println("Bitmap size = " + regionBitmapWidth + 'x' + regionBitmapHeight);

        buff = new short[4];
        decoder.readByte(buff);
        regionBitmapXLocation = BinaryOperation.getInt32(buff);

        buff = new short[4];
        decoder.readByte(buff);
        regionBitmapYLocation = BinaryOperation.getInt32(buff);

        if (JBIG2StreamDecoder.debug)
            System.out.println("Bitmap location = " + regionBitmapXLocation + ',' + regionBitmapYLocation);

        /** extract region Segment flags */
        short regionFlagsField = decoder.readByte();

        regionFlags.setFlags(regionFlagsField);

        if (JBIG2StreamDecoder.debug)
            System.out.println("region Segment flags = " + regionFlagsField);
    }
}
