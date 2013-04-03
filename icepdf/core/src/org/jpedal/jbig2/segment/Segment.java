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
package org.jpedal.jbig2.segment;

import java.io.IOException;

import org.jpedal.jbig2.JBIG2Exception;
import org.jpedal.jbig2.decoders.ArithmeticDecoder;
import org.jpedal.jbig2.decoders.HuffmanDecoder;
import org.jpedal.jbig2.decoders.JBIG2StreamDecoder;
import org.jpedal.jbig2.decoders.MMRDecoder;

public abstract class Segment {

	public static final int SYMBOL_DICTIONARY = 0;
	public static final int INTERMEDIATE_TEXT_REGION = 4;
	public static final int IMMEDIATE_TEXT_REGION = 6;
	public static final int IMMEDIATE_LOSSLESS_TEXT_REGION = 7;
	public static final int PATTERN_DICTIONARY = 16;
	public static final int INTERMEDIATE_HALFTONE_REGION = 20;
	public static final int IMMEDIATE_HALFTONE_REGION = 22;
	public static final int IMMEDIATE_LOSSLESS_HALFTONE_REGION = 23;
	public static final int INTERMEDIATE_GENERIC_REGION = 36;
	public static final int IMMEDIATE_GENERIC_REGION = 38;
	public static final int IMMEDIATE_LOSSLESS_GENERIC_REGION = 39;
	public static final int INTERMEDIATE_GENERIC_REFINEMENT_REGION = 40;
	public static final int IMMEDIATE_GENERIC_REFINEMENT_REGION = 42;
	public static final int IMMEDIATE_LOSSLESS_GENERIC_REFINEMENT_REGION = 43;
	public static final int PAGE_INFORMATION = 48;
	public static final int END_OF_PAGE = 49;
	public static final int END_OF_STRIPE = 50;
	public static final int END_OF_FILE = 51;
	public static final int PROFILES = 52;
	public static final int TABLES = 53;
	public static final int EXTENSION = 62;
	public static final int BITMAP = 70;

	protected SegmentHeader segmentHeader;

	protected HuffmanDecoder huffmanDecoder;

	protected ArithmeticDecoder arithmeticDecoder;

	protected MMRDecoder mmrDecoder;

	protected JBIG2StreamDecoder decoder;

	public Segment(JBIG2StreamDecoder streamDecoder) {
		this.decoder = streamDecoder;

//		try {
			//huffDecoder = HuffmanDecoder.getInstance();
//			arithmeticDecoder = ArithmeticDecoder.getInstance();
			
			huffmanDecoder = decoder.getHuffmanDecoder();
			arithmeticDecoder = decoder.getArithmeticDecoder();
			mmrDecoder = decoder.getMMRDecoder();
			
//		} catch (JBIG2Exception e) {
//			e.printStackTrace();
//		}
	}

	protected short readATValue() throws IOException {
		short atValue;
		short c0 = atValue = decoder.readByte();

		if ((c0 & 0x80) != 0) {
			atValue |= -1 - 0xff;
		}

		return atValue;
	}

	public SegmentHeader getSegmentHeader() {
		return segmentHeader;
	}

	public void setSegmentHeader(SegmentHeader segmentHeader) {
		this.segmentHeader = segmentHeader;
	}

	public abstract void readSegment() throws IOException, JBIG2Exception;
}
