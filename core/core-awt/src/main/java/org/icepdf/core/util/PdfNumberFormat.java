/*
 * Copyright 2026 Patrick Corless
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.icepdf.core.util;

import java.math.BigDecimal;

/**
 * Writes numbers the way PDF requires them.
 * <p>
 * PDF 32000-1 7.3.3 gives a real no exponent form, which rules out the obvious ways of turning a
 * float into text:
 * <ul>
 * <li>{@code String.valueOf(1.0E-5f)} yields {@code 1.0E-5}, and {@code String.valueOf(1.0E7f)}
 *     yields {@code 1.0E7}. A conforming reader rejects or truncates either.</li>
 * <li>{@code BigDecimal.valueOf(float)} widens through {@code double} first, so {@code 0.1f} becomes
 *     {@code 0.10000000149011612} - seventeen digits of binary noise written into every file - and
 *     large values still come out as {@code 1.0E+7}.</li>
 * </ul>
 * Going through the float's own shortest decimal representation and then demanding plain notation
 * avoids both.
 */
public final class PdfNumberFormat {

    private PdfNumberFormat() {
    }

    /**
     * @param value number to write into a PDF
     * @return plain decimal text, no exponent, no trailing zeros
     */
    public static String format(float value) {
        return toPlainString(Float.toString(value));
    }

    /**
     * @param value number to write into a PDF
     * @return plain decimal text, no exponent, no trailing zeros
     */
    public static String format(double value) {
        return toPlainString(Double.toString(value));
    }

    private static String toPlainString(String shortestDecimal) {
        BigDecimal decimal = new BigDecimal(shortestDecimal).stripTrailingZeros();
        // stripTrailingZeros turns 10000000 into 1E+7; toPlainString undoes that without
        // reintroducing the trailing zeros on values like 34.50.
        return decimal.toPlainString();
    }
}
