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
import org.icepdf.core.pobjects.Stream;
import org.icepdf.core.util.Library;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

/**
 * Builds PDF function dictionaries by hand, so a function can be evaluated without a document.
 * <p>
 * A function is pure arithmetic - numbers in, numbers out - and every one of the four types is
 * described entirely by its dictionary.  Building that dictionary directly is the whole setup a
 * test needs, and it keeps the sample table, the bounds or the calculator program visible next to
 * the values they are expected to produce.
 */
public final class FunctionFixtures {

    private FunctionFixtures() {
    }

    /**
     * @return a library with no document behind it, enough to resolve direct dictionary values
     */
    public static Library library() {
        return new Library();
    }

    /**
     * Entries common to every function type.
     *
     * @param type   the /FunctionType
     * @param domain input domain pairs
     * @param range  output range pairs, or null to leave /Range out
     * @return the entries
     */
    public static DictionaryEntries entries(int type, float[] domain, float[] range) {
        DictionaryEntries entries = new DictionaryEntries();
        entries.put(Function.FUNCTIONTYPE_NAME, type);
        entries.put(Function.DOMAIN_NAME, numbers(domain));
        if (range != null) {
            entries.put(Function.RANGE_NAME, numbers(range));
        }
        return entries;
    }

    /**
     * A type 2 exponential function, the simplest one to use as a component of types 3 and 4 tests.
     *
     * @param c0 output at 0
     * @param c1 output at 1
     * @param n  interpolation exponent
     * @return the function
     */
    public static Function exponential(float[] c0, float[] c1, float n) {
        return Function.getFunction(library(), new Dictionary(library(), exponentialEntries(c0, c1, n)));
    }

    /**
     * The dictionary of a type 2 function, for use as a sub-function of a stitching function.
     * <p>
     * A type 3's /Functions array holds dictionaries, not built functions - the array is resolved
     * as it is parsed - so a sub-function has to be supplied in the form the parser expects.
     *
     * @param c0 output at 0
     * @param c1 output at 1
     * @param n  interpolation exponent
     * @return the entries
     */
    public static DictionaryEntries exponentialEntries(float[] c0, float[] c1, float n) {
        DictionaryEntries entries = entries(2, new float[]{0, 1}, null);
        entries.put(new Name("C0"), numbers(c0));
        entries.put(new Name("C1"), numbers(c1));
        entries.put(new Name("N"), n);
        return entries;
    }

    /**
     * A type 0 sampled function.  The samples are given as whole numbers in the range a sample of
     * {@code bitsPerSample} bits can hold, which is how they are stored in the stream.
     *
     * @param domain        input domain pairs
     * @param range         output range pairs
     * @param size          samples per input dimension
     * @param bitsPerSample bits per stored sample
     * @param samples       the sample table, in stream order
     * @return the function
     */
    public static Function sampled(float[] domain, float[] range, int[] size, int bitsPerSample,
                                   int... samples) {
        DictionaryEntries entries = entries(0, domain, range);
        entries.put(Function_0.SIZE_KEY, integers(size));
        entries.put(Function_0.BITSPERSAMPLE_KEY, bitsPerSample);
        byte[] streamBytes = packSamples(bitsPerSample, samples);
        entries.put(Dictionary.LENGTH_KEY, streamBytes.length);
        Stream stream = new Stream(library(), entries, ByteBuffer.wrap(streamBytes));
        return Function.getFunction(library(), stream);
    }

    /**
     * A type 4 PostScript calculator function.
     *
     * @param domain  input domain pairs
     * @param range   output range pairs
     * @param program the calculator program, braces included
     * @return the function
     */
    public static Function calculator(float[] domain, float[] range, String program) {
        DictionaryEntries entries = entries(4, domain, range);
        byte[] streamBytes = program.getBytes(StandardCharsets.ISO_8859_1);
        entries.put(Dictionary.LENGTH_KEY, streamBytes.length);
        Stream stream = new Stream(library(), entries, ByteBuffer.wrap(streamBytes));
        return Function.getFunction(library(), stream);
    }

    /**
     * Packs whole-number samples into the big-endian bit stream a type 0 function reads.
     *
     * @param bitsPerSample bits each sample occupies
     * @param samples       the samples
     * @return the packed bytes
     */
    public static byte[] packSamples(int bitsPerSample, int... samples) {
        long totalBits = (long) bitsPerSample * samples.length;
        byte[] packed = new byte[(int) ((totalBits + 7) / 8)];
        int bitPosition = 0;
        for (int sample : samples) {
            for (int bit = bitsPerSample - 1; bit >= 0; bit--) {
                if (((sample >> bit) & 1) != 0) {
                    packed[bitPosition >> 3] |= (byte) (0x80 >> (bitPosition & 7));
                }
                bitPosition++;
            }
        }
        return packed;
    }

    public static List<Object> numbers(float... values) {
        Object[] boxed = new Object[values.length];
        for (int i = 0; i < values.length; i++) {
            boxed[i] = values[i];
        }
        return Arrays.asList(boxed);
    }

    public static List<Object> integers(int... values) {
        Object[] boxed = new Object[values.length];
        for (int i = 0; i < values.length; i++) {
            boxed[i] = values[i];
        }
        return Arrays.asList(boxed);
    }
}
