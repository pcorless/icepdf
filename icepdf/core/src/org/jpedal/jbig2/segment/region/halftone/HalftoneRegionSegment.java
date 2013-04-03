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

import org.jpedal.jbig2.JBIG2Exception;
import org.jpedal.jbig2.decoders.JBIG2StreamDecoder;
import org.jpedal.jbig2.image.JBIG2Bitmap;
import org.jpedal.jbig2.segment.Segment;
import org.jpedal.jbig2.segment.pageinformation.PageInformationSegment;
import org.jpedal.jbig2.segment.pattern.PatternDictionarySegment;
import org.jpedal.jbig2.segment.region.RegionFlags;
import org.jpedal.jbig2.segment.region.RegionSegment;
import org.jpedal.jbig2.util.BinaryOperation;

import java.io.IOException;

public class HalftoneRegionSegment extends RegionSegment {
	private HalftoneRegionFlags halftoneRegionFlags = new HalftoneRegionFlags();

	private boolean inlineImage;

	public HalftoneRegionSegment(JBIG2StreamDecoder streamDecoder, boolean inlineImage) {
		super(streamDecoder);

		this.inlineImage = inlineImage;
	}

	public void readSegment() throws IOException, JBIG2Exception {
		super.readSegment();

		/** read text region Segment flags */
		readHalftoneRegionFlags();

		short[] buf = new short[4];
		decoder.readByte(buf);
		int gridWidth = BinaryOperation.getInt32(buf);

		buf = new short[4];
		decoder.readByte(buf);
		int gridHeight = BinaryOperation.getInt32(buf);

		buf = new short[4];
		decoder.readByte(buf);
		int gridX = BinaryOperation.getInt32(buf);

		buf = new short[4];
		decoder.readByte(buf);
		int gridY = BinaryOperation.getInt32(buf);

		if (JBIG2StreamDecoder.debug)
			System.out.println("grid pos and size = " + gridX + ',' + gridY + ' ' + gridWidth + ',' + gridHeight);

		buf = new short[2];
		decoder.readByte(buf);
		int stepX = BinaryOperation.getInt16(buf);

		buf = new short[2];
		decoder.readByte(buf);
		int stepY = BinaryOperation.getInt16(buf);

		if (JBIG2StreamDecoder.debug)
			System.out.println("step size = " + stepX + ',' + stepY);

		int[] referedToSegments = segmentHeader.getReferredToSegments();
		if (referedToSegments.length != 1) {
			System.out.println("Error in halftone Segment. refSegs should == 1");
		}

		Segment segment = decoder.findSegment(referedToSegments[0]);
		if (segment.getSegmentHeader().getSegmentType() != Segment.PATTERN_DICTIONARY) {
			if(JBIG2StreamDecoder.debug)
				System.out.println("Error in halftone Segment. bad symbol dictionary reference");
		}
		
		PatternDictionarySegment patternDictionarySegment = (PatternDictionarySegment) segment;

		int bitsPerValue = 0, i = 1;
		while (i < patternDictionarySegment.getSize()) {
			bitsPerValue++;
			i <<= 1;
		}
		
		JBIG2Bitmap bitmap = patternDictionarySegment.getBitmaps()[0];
		int patternWidth = bitmap.getWidth();
		int patternHeight = bitmap.getHeight();

		if (JBIG2StreamDecoder.debug)
			System.out.println("pattern size = " + patternWidth + ',' + patternHeight);

		boolean useMMR = halftoneRegionFlags.getFlagValue(HalftoneRegionFlags.H_MMR) != 0;
		int template = halftoneRegionFlags.getFlagValue(HalftoneRegionFlags.H_TEMPLATE);
		
		if (!useMMR) {
			arithmeticDecoder.resetGenericStats(template, null);
			arithmeticDecoder.start();
		}

		int halftoneDefaultPixel = halftoneRegionFlags.getFlagValue(HalftoneRegionFlags.H_DEF_PIXEL);
		bitmap = new JBIG2Bitmap(regionBitmapWidth, regionBitmapHeight, arithmeticDecoder, huffmanDecoder, mmrDecoder);
		bitmap.clear(halftoneDefaultPixel);

		boolean enableSkip = halftoneRegionFlags.getFlagValue(HalftoneRegionFlags.H_ENABLE_SKIP) != 0;
		
		JBIG2Bitmap skipBitmap = null;
		if (enableSkip) {
			skipBitmap = new JBIG2Bitmap(gridWidth, gridHeight, arithmeticDecoder, huffmanDecoder, mmrDecoder);
			skipBitmap.clear(0);
			for (int y = 0; y < gridHeight; y++) {
				for (int x = 0; x < gridWidth; x++) {
					int xx = gridX + y * stepY + x * stepX;
					int yy = gridY + y * stepX - x * stepY;
					
					if (((xx + patternWidth) >> 8) <= 0 || (xx >> 8) >= regionBitmapWidth || ((yy + patternHeight) >> 8) <= 0 || (yy >> 8) >= regionBitmapHeight) {
						skipBitmap.setPixel(y, x, 1);
					}
				}
			}
		}

		int[] grayScaleImage = new int[gridWidth * gridHeight];

		short[] genericBAdaptiveTemplateX = new short[4], genericBAdaptiveTemplateY = new short[4];

		genericBAdaptiveTemplateX[0] = (short) (template <= 1 ? 3 : 2);
		genericBAdaptiveTemplateY[0] = -1;
		genericBAdaptiveTemplateX[1] = -3;
		genericBAdaptiveTemplateY[1] = -1;
		genericBAdaptiveTemplateX[2] = 2;
		genericBAdaptiveTemplateY[2] = -2;
		genericBAdaptiveTemplateX[3] = -2;
		genericBAdaptiveTemplateY[3] = -2;

        JBIG2Bitmap grayBitmap ;

        for (int j = bitsPerValue - 1; j >= 0; --j) {
			grayBitmap = new JBIG2Bitmap(gridWidth, gridHeight, arithmeticDecoder, huffmanDecoder, mmrDecoder);

			grayBitmap.readBitmap(useMMR, template, false, enableSkip, skipBitmap, genericBAdaptiveTemplateX, genericBAdaptiveTemplateY, -1);

			i = 0;
			for (int row = 0; row < gridHeight; row++) {
				for (int col = 0; col < gridWidth; col++) {
					int bit = grayBitmap.getPixel(col, row) ^ (grayScaleImage[i] & 1);
					grayScaleImage[i] = (grayScaleImage[i] << 1) | bit;
					i++;
				}
			}
		}

		int combinationOperator = halftoneRegionFlags.getFlagValue(HalftoneRegionFlags.H_COMB_OP);

		i = 0;
		for (int col = 0; col < gridHeight; col++) {
			int xx = gridX + col * stepY;
			int yy = gridY + col * stepX;
			for (int row = 0; row < gridWidth; row++) {
				if (!(enableSkip && skipBitmap.getPixel(col, row) == 1)) {
					JBIG2Bitmap patternBitmap = patternDictionarySegment.getBitmaps()[grayScaleImage[i]];
					bitmap.combine(patternBitmap, xx >> 8, yy >> 8, combinationOperator);
				}
				
				xx += stepX;
				yy -= stepY;
				
				i++;
			}
		}

		if (inlineImage) {
			PageInformationSegment pageSegment = decoder.findPageSegement(segmentHeader.getPageAssociation());
			JBIG2Bitmap pageBitmap = pageSegment.getPageBitmap();

			int externalCombinationOperator = regionFlags.getFlagValue(RegionFlags.EXTERNAL_COMBINATION_OPERATOR);
			pageBitmap.combine(bitmap, regionBitmapXLocation, regionBitmapYLocation, externalCombinationOperator);
		} else {
			bitmap.setBitmapNumber(getSegmentHeader().getSegmentNumber());
			decoder.appendBitmap(bitmap);
		}

	}

	private void readHalftoneRegionFlags() throws IOException {
		/** extract text region Segment flags */
		short halftoneRegionFlagsField = decoder.readByte();

		halftoneRegionFlags.setFlags(halftoneRegionFlagsField);

		if (JBIG2StreamDecoder.debug)
			System.out.println("generic region Segment flags = " + halftoneRegionFlagsField);
	}

	public HalftoneRegionFlags getHalftoneRegionFlags() {
		return halftoneRegionFlags;
	}
}