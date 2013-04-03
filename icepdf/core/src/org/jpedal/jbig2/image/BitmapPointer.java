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
package org.jpedal.jbig2.image;

public class BitmapPointer {
	private int x, y, width, height, bits, count;
	private JBIG2Bitmap bitmap;

	public BitmapPointer(JBIG2Bitmap bitmap) {
		this.bitmap = bitmap;
		this.height = bitmap.getHeight();
		this.width = bitmap.getWidth();
	}

	public void setPointer(int x, int y) {
		this.x = x;
		this.y = y;
		count = 0;
	}

	public int nextPixel() {

		// fairly certain the byte can be cached here - seems to work fine. only
		// problem would be if cached pixel was modified, and the modified
		// version needed.
//		if (y < 0 || y >= height || x >= width) {
//			return 0;
//		} else if (x < 0) {
//			x++;
//			return 0;
//		}
//
//		if (count == 0 && width - x >= 8) {
//			bits = bitmap.getPixelByte(x, y);
//			count = 8;
//		} else {
//			count = 0;
//		}
//
//		if (count > 0) {
//			int b = bits & 0x01;
//			count--;
//			bits >>= 1;
//			x++;
//			return b;
//		}
//
//		int pixel = bitmap.getPixel(x, y);
//		x++;
//
//		return pixel;
		
		if (y < 0 || y >= height || x >= width) {
			return 0;
		} else if (x < 0) {
			x++;
			return 0;
		}

		int pixel = bitmap.getPixel(x, y);

		x++;

		return pixel;
	}
}
