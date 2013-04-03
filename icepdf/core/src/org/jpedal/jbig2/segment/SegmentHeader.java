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

import org.jpedal.jbig2.decoders.JBIG2StreamDecoder;

public class SegmentHeader {

	private int segmentNumber;

	private int segmentType;
	private boolean pageAssociationSizeSet;
	private boolean deferredNonRetainSet;

	private int referredToSegmentCount;
	private short[] rententionFlags;

	private int[] referredToSegments;
	private int pageAssociation;
	private int dataLength;

	public void setSegmentNumber(int SegmentNumber) {
		this.segmentNumber = SegmentNumber;
	}

	public void setSegmentHeaderFlags(short SegmentHeaderFlags) {
		segmentType = SegmentHeaderFlags & 63; // 63 = 00111111
		pageAssociationSizeSet = (SegmentHeaderFlags & 64) == 64; // 64 = // 01000000
		deferredNonRetainSet = (SegmentHeaderFlags & 80) == 80; // 64 = 10000000

		if (JBIG2StreamDecoder.debug) {
			System.out.println("SegmentType = " + segmentType);
			System.out.println("pageAssociationSizeSet = " + pageAssociationSizeSet);
			System.out.println("deferredNonRetainSet = " + deferredNonRetainSet);
		}
	}

	public void setReferredToSegmentCount(int referredToSegmentCount) {
		this.referredToSegmentCount = referredToSegmentCount;
	}

	public void setRententionFlags(short[] rententionFlags) {
		this.rententionFlags = rententionFlags;
	}

	public void setReferredToSegments(int[] referredToSegments) {
		this.referredToSegments = referredToSegments;
	}

	public int[] getReferredToSegments() {
		return referredToSegments;
	}

	public int getSegmentType() {
		return segmentType;
	}

	public int getSegmentNumber() {
		return segmentNumber;
	}

	public boolean isPageAssociationSizeSet() {
		return pageAssociationSizeSet;
	}

	public boolean isDeferredNonRetainSet() {
		return deferredNonRetainSet;
	}

	public int getReferredToSegmentCount() {
		return referredToSegmentCount;
	}

	public short[] getRententionFlags() {
		return rententionFlags;
	}

	public int getPageAssociation() {
		return pageAssociation;
	}

	public void setPageAssociation(int pageAssociation) {
		this.pageAssociation = pageAssociation;
	}

	public void setDataLength(int dataLength) {
		this.dataLength = dataLength;
	}

	public void setSegmentType(int type) {
		this.segmentType = type;
	}

	public int getSegmentDataLength() {
		return dataLength;
	}
}
