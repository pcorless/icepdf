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
package org.jpedal.jbig2.util;

public class BinaryOperation {

	public static final int LEFT_SHIFT = 0;
	public static final int RIGHT_SHIFT = 1;

	public static int getInt32(short[] number) {
		return (number[0] << 24) | (number[1] << 16) | (number[2] << 8) | number[3];
	}

	public static int getInt16(short[] number) {
		return (number[0] << 8) | number[1];
	}

	public static long bit32Shift(long number, int shift, int direction) {
		if (direction == LEFT_SHIFT)
			number <<= shift;
		else
			number >>= shift;

		long mask = 0xffffffffl; // 1111 1111 1111 1111 1111 1111 1111 1111
		return (number & mask);
	}

	public static int bit8Shift(int number, int shift, int direction) {
		if (direction == LEFT_SHIFT)
			number <<= shift;
		else
			number >>= shift;

		int mask = 0xff; // 1111 1111
		return (number & mask);
	}

	public static int getInt32(byte[] number) {
		return (number[0] << 24) | (number[1] << 16) | (number[2] << 8) | number[3];
	}
}
