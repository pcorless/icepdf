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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PDF 32000-1 7.3.3 gives a real no exponent form, and every number this library writes has to
 * respect that. The cases below are the ones the obvious implementations get wrong.
 */
public class PdfNumberFormatTest {

    @DisplayName("floats are written in plain decimal")
    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            // small magnitudes: String.valueOf gives 1.0E-5
            "0.00001, 0.00001",
            "0.0001,  0.0001",
            // large magnitudes: String.valueOf and BigDecimal.valueOf both give 1.0E7
            "10000000, 10000000",
            "12345678, 12345678",
            // binary noise: BigDecimal.valueOf(0.1f) gives 0.10000000149011612
            "0.1, 0.1",
            "-1523.4, -1523.4",
            // trailing zeros are not worth the bytes
            "34.50, 34.5",
            "66.0, 66",
            "0.0, 0",
    })
    public void floatsAreWrittenPlain(float value, String expected) {
        assertEquals(expected, PdfNumberFormat.format(value));
    }

    @DisplayName("no formatted value carries an exponent")
    @Test
    public void noValueCarriesAnExponent() {
        float[] awkward = {1e-7f, 1e-5f, 0.1f, 1f, 1e7f, 1e9f, -1e8f, Float.MIN_VALUE, Float.MAX_VALUE};
        for (float value : awkward) {
            String formatted = PdfNumberFormat.format(value);
            assertFalse(formatted.contains("e") || formatted.contains("E"),
                    "PDF reals have no exponent form, but " + value + " formatted as " + formatted);
        }
    }

    @DisplayName("a formatted value still parses back to the same number")
    @Test
    public void formattingRoundTrips() {
        float[] values = {0.1f, 34.5f, -1523.4f, 1e7f, 0.00001f, 0f};
        for (float value : values) {
            assertEquals(value, Float.parseFloat(PdfNumberFormat.format(value)), 0f,
                    "formatting must not change the value");
        }
    }

    @DisplayName("doubles get the same treatment")
    @Test
    public void doublesAreWrittenPlain() {
        assertEquals("0.1", PdfNumberFormat.format(0.1d));
        assertEquals("10000000", PdfNumberFormat.format(1e7d));
        assertFalse(PdfNumberFormat.format(1e-7d).contains("E"));
    }
}
