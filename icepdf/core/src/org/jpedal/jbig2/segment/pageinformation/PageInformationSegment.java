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

import org.jpedal.jbig2.JBIG2Exception;
import org.jpedal.jbig2.decoders.JBIG2StreamDecoder;
import org.jpedal.jbig2.image.JBIG2Bitmap;
import org.jpedal.jbig2.segment.Segment;
import org.jpedal.jbig2.util.BinaryOperation;

import java.io.IOException;

public class PageInformationSegment extends Segment {

    private int pageBitmapHeight, pageBitmapWidth;
    private int yResolution, xResolution;

    PageInformationFlags pageInformationFlags = new PageInformationFlags();
    private int pageStriping;

    private JBIG2Bitmap pageBitmap;

    public PageInformationSegment(JBIG2StreamDecoder streamDecoder) {
        super(streamDecoder);
    }

    public PageInformationFlags getPageInformationFlags() {
        return pageInformationFlags;
    }

    public JBIG2Bitmap getPageBitmap() {
        return pageBitmap;
    }

    public void readSegment() throws IOException, JBIG2Exception {

        if (JBIG2StreamDecoder.debug)
            System.out.println("==== Reading Page Information Dictionary ====");

        short[] buff = new short[4];
        decoder.readByte(buff);
        pageBitmapWidth = BinaryOperation.getInt32(buff);

        buff = new short[4];
        decoder.readByte(buff);
        pageBitmapHeight = BinaryOperation.getInt32(buff);

        if (JBIG2StreamDecoder.debug)
            System.out.println("Bitmap size = " + pageBitmapWidth + 'x' + pageBitmapHeight);

        buff = new short[4];
        decoder.readByte(buff);
        xResolution = BinaryOperation.getInt32(buff);

        buff = new short[4];
        decoder.readByte(buff);
        yResolution = BinaryOperation.getInt32(buff);

        if (JBIG2StreamDecoder.debug)
            System.out.println("Resolution = " + xResolution + 'x' + yResolution);

        /** extract page information flags */
        short pageInformationFlagsField = decoder.readByte();

        pageInformationFlags.setFlags(pageInformationFlagsField);

        if (JBIG2StreamDecoder.debug)
            System.out.println("symbolDictionaryFlags = " + pageInformationFlagsField);

        buff = new short[2];
        decoder.readByte(buff);
        pageStriping = BinaryOperation.getInt16(buff);

        if (JBIG2StreamDecoder.debug)
            System.out.println("Page Striping = " + pageStriping);

        int defPix = pageInformationFlags.getFlagValue(PageInformationFlags.DEFAULT_PIXEL_VALUE);

        int height;

        if (pageBitmapHeight == -1) {
            height = pageStriping & 0x7fff;
        } else {
            height = pageBitmapHeight;
        }

        pageBitmap = new JBIG2Bitmap(pageBitmapWidth, height, arithmeticDecoder, huffmanDecoder, mmrDecoder);
        pageBitmap.clear(defPix);
    }

    public int getPageBitmapHeight() {
        return pageBitmapHeight;
    }
}
