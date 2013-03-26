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
package org.jpedal.jbig2.examples.pdf;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class PDFSegment {

    private ByteArrayOutputStream header = new ByteArrayOutputStream();
    private ByteArrayOutputStream data = new ByteArrayOutputStream();
    private int segmentDataLength;

    public void writeToHeader(short bite) {
        header.write(bite);
    }

    public void writeToHeader(short[] bites) throws IOException {
        for (int i = 0; i < bites.length; i++)
            header.write(bites[i]);
    }

    public void writeToData(short bite) {
        data.write(bite);
    }

    public ByteArrayOutputStream getHeader() {
        return header;
    }

    public ByteArrayOutputStream getData() {
        return data;
    }

    public void setDataLength(int segmentDataLength) {
        this.segmentDataLength = segmentDataLength;

    }

    public int getSegmentDataLength() {
        return segmentDataLength;
    }
}
