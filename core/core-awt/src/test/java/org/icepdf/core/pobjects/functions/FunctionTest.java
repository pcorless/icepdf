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
package org.icepdf.core.pobjects.functions;

import org.icepdf.core.pobjects.Dictionary;
import org.icepdf.core.pobjects.DictionaryEntries;
import org.icepdf.core.pobjects.Name;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.icepdf.core.pobjects.functions.FunctionFixtures.calculator;
import static org.icepdf.core.pobjects.functions.FunctionFixtures.entries;
import static org.icepdf.core.pobjects.functions.FunctionFixtures.exponential;
import static org.icepdf.core.pobjects.functions.FunctionFixtures.integers;
import static org.icepdf.core.pobjects.functions.FunctionFixtures.library;
import static org.icepdf.core.pobjects.functions.FunctionFixtures.numbers;
import static org.icepdf.core.pobjects.functions.FunctionFixtures.sampled;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the four PDF function types: numbers in, numbers out.
 * <p>
 * Functions are where a document's colour is decided.  A Separation or DeviceN colour space runs
 * every one of its tint values through one, a shading runs its colour ramp through one, and a
 * transfer function runs every component of every pixel through one.  Nothing about a wrong answer
 * is visible except that the output is the wrong colour, which is why these are asserted against
 * values worked out from the specification rather than against whatever the code currently
 * produces.
 *
 * @see FunctionFixtures for how each type's dictionary is built
 */
public class FunctionTest {

    private static final float TOLERANCE = 0.0005f;

    // ------------------------------------------------------------------
    // dispatch and the shared parts
    // ------------------------------------------------------------------

    @DisplayName("the factory builds the class named by /FunctionType")
    @Test
    public void factoryDispatch() {
        assertInstanceOf(Function_0.class,
                sampled(new float[]{0, 1}, new float[]{0, 1}, new int[]{2}, 8, 0, 255));
        assertInstanceOf(Function_2.class, exponential(new float[]{0}, new float[]{1}, 1));
        assertInstanceOf(Function_4.class,
                calculator(new float[]{0, 1}, new float[]{0, 1}, "{ }"));
    }

    @DisplayName("an unknown or missing function type yields no function")
    @Test
    public void unknownFunctionType() {
        // A malformed function must come back null so the caller can fall back, rather than
        // half-built and throwing on the first colour lookup.
        assertNull(Function.getFunction(library(),
                new Dictionary(library(), entries(9, new float[]{0, 1}, new float[]{0, 1}))));
        assertNull(Function.getFunction(library(), new Dictionary(library(), new DictionaryEntries())));
        assertNull(Function.getFunction(library(), "not a dictionary at all"));
    }

    @DisplayName("the domain and range are read back as given")
    @Test
    public void domainAndRange() {
        Function function = sampled(new float[]{0, 1}, new float[]{0, 1, 0, 1}, new int[]{2}, 8,
                0, 0, 255, 255);
        assertArrayEquals(new float[]{0, 1}, function.getDomain(), TOLERANCE);
        assertArrayEquals(new float[]{0, 1, 0, 1}, function.getRange(), TOLERANCE);
        assertEquals(0, function.getFunctionType());
    }

    @DisplayName("interpolate maps a value linearly from one interval onto another")
    @Test
    public void interpolate() {
        // Every type uses this to map an input onto its encode or decode interval.
        assertEquals(0.5f, Function.interpolate(0.5f, 0, 1, 0, 1), TOLERANCE);
        assertEquals(50f, Function.interpolate(0.5f, 0, 1, 0, 100), TOLERANCE);
        assertEquals(0f, Function.interpolate(0, 0, 1, 0, 100), TOLERANCE);
        assertEquals(100f, Function.interpolate(1, 0, 1, 0, 100), TOLERANCE);
        // a reversed target interval runs backwards
        assertEquals(75f, Function.interpolate(0.25f, 0, 1, 100, 0), TOLERANCE);
        // an offset source interval
        assertEquals(0.5f, Function.interpolate(15, 10, 20, 0, 1), TOLERANCE);
    }

    // ------------------------------------------------------------------
    // type 2 - exponential interpolation
    // ------------------------------------------------------------------

    @DisplayName("type 2 - a linear ramp interpolates between C0 and C1")
    @Test
    public void exponentialLinear() {
        Function function = exponential(new float[]{0}, new float[]{1}, 1);
        assertEquals(0f, function.calculate(new float[]{0})[0], TOLERANCE);
        assertEquals(0.25f, function.calculate(new float[]{0.25f})[0], TOLERANCE);
        assertEquals(0.5f, function.calculate(new float[]{0.5f})[0], TOLERANCE);
        assertEquals(1f, function.calculate(new float[]{1})[0], TOLERANCE);
    }

    @DisplayName("type 2 - the exponent curves the ramp")
    @Test
    public void exponentialCurved() {
        // C0 + x^N * (C1 - C0); at N = 2 the halfway input is a quarter of the way along.
        Function function = exponential(new float[]{0}, new float[]{1}, 2);
        assertEquals(0.25f, function.calculate(new float[]{0.5f})[0], TOLERANCE);
        assertEquals(1f, function.calculate(new float[]{1})[0], TOLERANCE);
    }

    @DisplayName("type 2 - every output component is interpolated independently")
    @Test
    public void exponentialMultipleComponents() {
        // An RGB ramp from red to blue: the components move in different directions at once.
        Function function = exponential(new float[]{1, 0, 0}, new float[]{0, 0, 1}, 1);
        float[] middle = function.calculate(new float[]{0.5f});
        assertEquals(3, middle.length);
        assertEquals(0.5f, middle[0], TOLERANCE);
        assertEquals(0f, middle[1], TOLERANCE);
        assertEquals(0.5f, middle[2], TOLERANCE);
    }

    @DisplayName("type 2 - defaults to 0 and 1 when C0 and C1 are left out")
    @Test
    public void exponentialDefaults() {
        DictionaryEntries dictionary = entries(2, new float[]{0, 1}, null);
        dictionary.put(new Name("N"), 1);
        Function function = Function.getFunction(library(), new Dictionary(library(), dictionary));
        assertEquals(0f, function.calculate(new float[]{0})[0], TOLERANCE);
        assertEquals(1f, function.calculate(new float[]{1})[0], TOLERANCE);
    }

    // ------------------------------------------------------------------
    // type 0 - sampled
    // ------------------------------------------------------------------

    @DisplayName("type 0 - the sample values are decoded onto the range")
    @Test
    public void sampledEndpoints() {
        // Two 8-bit samples, 0 and 255, decoded onto [0 1]: the ends are exactly the samples.
        Function function = sampled(new float[]{0, 1}, new float[]{0, 1}, new int[]{2}, 8, 0, 255);
        assertEquals(0f, function.calculate(new float[]{0})[0], TOLERANCE);
        assertEquals(1f, function.calculate(new float[]{1})[0], TOLERANCE);
    }

    @DisplayName("type 0 - values between samples are interpolated, not snapped")
    @Test
    public void sampledInterpolation() {
        Function function = sampled(new float[]{0, 1}, new float[]{0, 1}, new int[]{2}, 8, 0, 255);
        assertEquals(0.5f, function.calculate(new float[]{0.5f})[0], TOLERANCE);
        assertEquals(0.25f, function.calculate(new float[]{0.25f})[0], TOLERANCE);
    }

    @DisplayName("type 0 - an input outside the domain is clipped to it")
    @Test
    public void sampledClipsDomain() {
        // A shading samples slightly outside its domain at the edges; clipping is what keeps the
        // edge colour flat instead of running off the end of the sample table.
        Function function = sampled(new float[]{0, 1}, new float[]{0, 1}, new int[]{2}, 8, 0, 255);
        assertEquals(0f, function.calculate(new float[]{-5})[0], TOLERANCE);
        assertEquals(1f, function.calculate(new float[]{5})[0], TOLERANCE);
    }

    @DisplayName("type 0 - a three sample table interpolates within each half")
    @Test
    public void sampledThreeSamples() {
        // Samples 0, 255, 0 over [0 1]: a triangle peaking in the middle.
        Function function = sampled(new float[]{0, 1}, new float[]{0, 1}, new int[]{3}, 8,
                0, 255, 0);
        assertEquals(0f, function.calculate(new float[]{0})[0], TOLERANCE);
        assertEquals(0.5f, function.calculate(new float[]{0.25f})[0], TOLERANCE);
        assertEquals(1f, function.calculate(new float[]{0.5f})[0], TOLERANCE);
        assertEquals(0.5f, function.calculate(new float[]{0.75f})[0], TOLERANCE);
        assertEquals(0f, function.calculate(new float[]{1})[0], TOLERANCE);
    }

    @DisplayName("type 0 - several output components share one sample table")
    @Test
    public void sampledMultipleOutputs() {
        // An RGB table: each sample position stores three consecutive samples, so a two-entry
        // table of red then blue is six samples in stream order.
        Function function = sampled(new float[]{0, 1}, new float[]{0, 1, 0, 1, 0, 1},
                new int[]{2}, 8, 255, 0, 0, 0, 0, 255);
        float[] start = function.calculate(new float[]{0});
        assertEquals(3, start.length);
        assertEquals(1f, start[0], TOLERANCE);
        assertEquals(0f, start[2], TOLERANCE);

        float[] end = function.calculate(new float[]{1});
        assertEquals(0f, end[0], TOLERANCE);
        assertEquals(1f, end[2], TOLERANCE);
    }

    @DisplayName("type 0 - the sample width is honoured, not assumed to be eight bits")
    @Test
    public void sampledBitDepths() {
        // 1, 4 and 16 bit tables all have to decode to the same ramp; a wrong shift silently
        // rescales every colour the function produces.
        assertEquals(1f,
                sampled(new float[]{0, 1}, new float[]{0, 1}, new int[]{2}, 1, 0, 1)
                        .calculate(new float[]{1})[0], TOLERANCE);
        assertEquals(1f,
                sampled(new float[]{0, 1}, new float[]{0, 1}, new int[]{2}, 4, 0, 15)
                        .calculate(new float[]{1})[0], TOLERANCE);
        assertEquals(1f,
                sampled(new float[]{0, 1}, new float[]{0, 1}, new int[]{2}, 16, 0, 65535)
                        .calculate(new float[]{1})[0], TOLERANCE);
        assertEquals(0.5f,
                sampled(new float[]{0, 1}, new float[]{0, 1}, new int[]{2}, 16, 0, 65535)
                        .calculate(new float[]{0.5f})[0], TOLERANCE);
    }

    @DisplayName("type 0 - /Decode maps the samples onto an interval of its own")
    @Test
    public void sampledDecode() {
        // /Decode is how a table of 0-255 samples drives an output range that is not 0-1.
        DictionaryEntries dictionary = entries(0, new float[]{0, 1}, new float[]{0, 100});
        dictionary.put(Function_0.SIZE_KEY, integers(2));
        dictionary.put(Function_0.BITSPERSAMPLE_KEY, 8);
        dictionary.put(Function_0.DECODE_KEY, numbers(0, 100));
        byte[] samples = FunctionFixtures.packSamples(8, 0, 255);
        dictionary.put(Dictionary.LENGTH_KEY, samples.length);
        Function function = Function.getFunction(library(),
                new org.icepdf.core.pobjects.Stream(library(), dictionary,
                        java.nio.ByteBuffer.wrap(samples)));

        assertEquals(0f, function.calculate(new float[]{0})[0], 0.05f);
        assertEquals(100f, function.calculate(new float[]{1})[0], 0.05f);
        assertEquals(50f, function.calculate(new float[]{0.5f})[0], 0.05f);
    }

    @DisplayName("type 0 - /Encode selects which part of the table the domain maps onto")
    @Test
    public void sampledEncode() {
        // Encoding [1 1] pins every input to the second sample, which is the flat-colour case.
        DictionaryEntries dictionary = entries(0, new float[]{0, 1}, new float[]{0, 1});
        dictionary.put(Function_0.SIZE_KEY, integers(3));
        dictionary.put(Function_0.BITSPERSAMPLE_KEY, 8);
        dictionary.put(Function_0.ENCODE_KEY, numbers(1, 1));
        byte[] samples = FunctionFixtures.packSamples(8, 0, 128, 255);
        dictionary.put(Dictionary.LENGTH_KEY, samples.length);
        Function function = Function.getFunction(library(),
                new org.icepdf.core.pobjects.Stream(library(), dictionary,
                        java.nio.ByteBuffer.wrap(samples)));

        float expected = 128 / 255f;
        assertEquals(expected, function.calculate(new float[]{0})[0], 0.01f);
        assertEquals(expected, function.calculate(new float[]{1})[0], 0.01f);
    }

    // ------------------------------------------------------------------
    // type 3 - stitching
    // ------------------------------------------------------------------

    /**
     * A stitching function over {@code [0 1]} made of {@code functions}, split at {@code bounds},
     * each sub-function seeing the whole of its own {@code [0 1]} domain.
     */
    private static Function stitched(float[] bounds, DictionaryEntries... functions) {
        return Function.getFunction(library(),
                new Dictionary(library(), stitchedEntries(bounds, functions)));
    }

    private static DictionaryEntries stitchedEntries(float[] bounds, DictionaryEntries... functions) {
        DictionaryEntries dictionary = entries(3, new float[]{0, 1}, null);
        dictionary.put(Function_3.BOUNDS_KEY, numbers(bounds));
        float[] encode = new float[functions.length * 2];
        for (int i = 0; i < functions.length; i++) {
            encode[2 * i] = 0;
            encode[2 * i + 1] = 1;
        }
        dictionary.put(Function_3.ENCODE_KEY, numbers(encode));
        dictionary.put(Function_3.FUNCTIONS_KEY, java.util.Arrays.asList((Object[]) functions));
        return dictionary;
    }

    /** Shorthand for a constant-valued sub-function. */
    private static DictionaryEntries constant(float value) {
        return FunctionFixtures.exponentialEntries(new float[]{value}, new float[]{value}, 1);
    }

    /** Shorthand for a sub-function ramping from {@code from} to {@code to}. */
    private static DictionaryEntries ramp(float from, float to) {
        return FunctionFixtures.exponentialEntries(new float[]{from}, new float[]{to}, 1);
    }

    @DisplayName("type 3 - each sub-domain is handed to its own function")
    @Test
    public void stitchingSelectsByBound() {
        // Below the bound the first function answers, above it the second.  Getting this backwards
        // reverses a gradient without any other symptom.
        Function function = stitched(new float[]{0.5f}, constant(0), constant(1));

        assertEquals(0f, function.calculate(new float[]{0})[0], TOLERANCE);
        assertEquals(0f, function.calculate(new float[]{0.25f})[0], TOLERANCE);
        assertEquals(1f, function.calculate(new float[]{0.75f})[0], TOLERANCE);
        assertEquals(1f, function.calculate(new float[]{1})[0], TOLERANCE);
    }

    @DisplayName("type 3 - the bound itself belongs to the function above it")
    @Test
    public void stitchingBoundIsHalfOpen() {
        // The sub-domains are half open: Bounds(i-1) <= x < Bounds(i).
        Function function = stitched(new float[]{0.5f}, constant(0), constant(1));
        assertEquals(1f, function.calculate(new float[]{0.5f})[0], TOLERANCE);
    }

    @DisplayName("type 3 - three sub-functions are each selected in turn")
    @Test
    public void stitchingThreeWay() {
        Function function = stitched(new float[]{0.33f, 0.66f},
                constant(0), constant(0.5f), constant(1));

        assertEquals(0f, function.calculate(new float[]{0.1f})[0], TOLERANCE);
        assertEquals(0.5f, function.calculate(new float[]{0.5f})[0], TOLERANCE);
        assertEquals(1f, function.calculate(new float[]{0.9f})[0], TOLERANCE);
    }

    @DisplayName("type 3 - each sub-function sees its own encode interval, not the page's")
    @Test
    public void stitchingEncodesSubDomain() {
        // The second half of the domain is mapped onto the whole of the second function's [0 1],
        // so the ramp it produces reaches 1 by the end of the page rather than stopping halfway.
        Function function = stitched(new float[]{0.5f}, constant(0), ramp(0, 1));

        assertEquals(0f, function.calculate(new float[]{0.5f})[0], 0.01f);
        assertEquals(0.5f, function.calculate(new float[]{0.75f})[0], 0.01f);
        assertEquals(1f, function.calculate(new float[]{1})[0], 0.01f);
    }

    @DisplayName("type 3 - a single sub-function with no bounds covers the whole domain")
    @Test
    public void stitchingSingleFunction() {
        Function function = stitched(new float[]{}, ramp(0, 1));
        assertEquals(0f, function.calculate(new float[]{0})[0], TOLERANCE);
        assertEquals(1f, function.calculate(new float[]{1})[0], TOLERANCE);
    }

    @DisplayName("type 3 - nests, with a stitching function inside a stitching function")
    @Test
    public void nestedStitching() {
        // Real shadings do this; each level has to encode into the level below it.
        DictionaryEntries inner = stitchedEntries(new float[]{0.5f}, constant(0), constant(0.5f));
        Function outer = stitched(new float[]{0.5f}, inner, constant(1));

        assertEquals(0f, outer.calculate(new float[]{0.1f})[0], TOLERANCE);
        assertEquals(0.5f, outer.calculate(new float[]{0.4f})[0], TOLERANCE);
        assertEquals(1f, outer.calculate(new float[]{0.9f})[0], TOLERANCE);
    }

    @DisplayName("type 3 - the output is clamped to /Range when one is given")
    @Test
    public void stitchingClampsToRange() {
        DictionaryEntries dictionary = entries(3, new float[]{0, 1}, new float[]{0, 0.5f});
        dictionary.put(Function_3.BOUNDS_KEY, numbers());
        dictionary.put(Function_3.ENCODE_KEY, numbers(0, 1));
        dictionary.put(Function_3.FUNCTIONS_KEY, java.util.Arrays.asList((Object) ramp(0, 1)));
        Function function = Function.getFunction(library(), new Dictionary(library(), dictionary));

        float[] result = function.calculate(new float[]{1});
        assertNotNull(result);
        assertTrue(result[0] <= 0.5f + TOLERANCE,
                "a result above the range should have been clamped, was " + result[0]);
    }
}
