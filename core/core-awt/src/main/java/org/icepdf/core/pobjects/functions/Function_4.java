/*
 * Copyright 2006-2019 ICEsoft Technologies Canada Corp.
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
package org.icepdf.core.pobjects.functions;

import org.icepdf.core.pobjects.Dictionary;
import org.icepdf.core.pobjects.Stream;
import org.icepdf.core.pobjects.functions.postscript.Lexer;
import org.icepdf.core.util.Utils;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Stack;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * <p>Type 4 Function (PDF 1.3), also called a PostScript calculator function,
 * shall be represented as a stream containing code written n a small subset of
 * the PostScript language. </p>
 * <p>Type 4 functions offer greater flexibility and potentially greater
 * accuracy then exponential functions (type 2 functions).  Type 4 functions
 * also make it possible to include a wide variety of halftone spots functions
 * without the loss of accuracy that comes from sampling, and without adding to
 * the list a predefined spot function (10.5.3 spot functions).  All of the
 * predefined spot functions can be written as type 4 functions. </p>
 *
 * @since 4.2
 */
public class Function_4 extends Function {

    private static final Logger logger =
            Logger.getLogger(Function_4.class.getName());

    // decoded content that makes up the type 4 functions.
    private byte[] functionContent;

    // cache for calculated colour values, keyed on the exact input components
    private final ConcurrentHashMap<InputKey, float[]> resultCache;

    // a malformed type 4 program fails deterministically on every sample of a
    // shading; only log the parse failure once per function rather than once
    // per colour lookup.
    private volatile boolean evaluationFailureLogged = false;

    public Function_4(Dictionary d) {
        super(d);
        // decode the stream for parsing.
        if (d instanceof Stream) {
            Stream functionStream = (Stream) d;
            functionContent = functionStream.getDecodedStreamBytes(0);
            if (logger.isLoggable(Level.FINER)) {
                logger.finer("Function 4: " + Utils.convertByteArrayToByteString(functionContent));
            }

        } else {
            logger.finer("Type 4 function operands could not be found.");
        }
        // cache for type 4 function results.
        resultCache = new ConcurrentHashMap<>();
    }

    /**
     * <p>Puts the value x thought the function type 4 algorithm.
     *
     * @param x input values m
     * @return output values n
     */
    public float[] calculate(float[] x) {

        // check the cache in case we've already made the calculation.  The key
        // copies the inputs and compares them by value so distinct inputs never
        // collide (a hashCode-based key could silently return another input's
        // colour).
        InputKey colourKey = new InputKey(x);
        float[] result = resultCache.get(colourKey);
        if (result != null) {
            // hand back a copy; callers (e.g. Function_3.validateAgainstRange)
            // clamp the result in place, which would otherwise corrupt the
            // cached array.
            return result.clone();
        }

        // setup the lexer stream
        InputStream content = new ByteArrayInputStream(functionContent);
        Lexer lex = new Lexer();
        lex.setInputStream(content);

        // parse/evaluate the type 4 functions with the input value(s) x.
        try {
            lex.parse(x);
        } catch (Exception e) {
            if (!evaluationFailureLogged) {
                evaluationFailureLogged = true;
                logger.log(Level.WARNING, "Error Processing Type 4 definition", e);
            }
        }

        // get the remaining numbers on the stack which are the return values.
        Stack stack = lex.getStack();

        // length of output array
        int n = range.length / 2;
        // ready output array
        float[] y = new float[n];

        // The n output values are the top-most n items left on the stack.  If
        // evaluation failed or underflowed the stack may hold fewer than n
        // items; fall back to the lower range bound rather than throwing and
        // aborting the entire shading.
        int available = stack.size();
        if (available < n) {
            for (int i = 0; i < n; i++) {
                y[i] = range[2 * i];
            }
            return y;
        }
        // read the top n items (a well-behaved function leaves exactly n), and
        // clamp each to its range; tolerate non-Float numbers defensively.
        int base = available - n;
        for (int i = 0; i < n; i++) {
            Object value = stack.elementAt(base + i);
            float f = value instanceof Number ? ((Number) value).floatValue() : range[2 * i];
            y[i] = Math.min(Math.max(f, range[2 * i]), range[2 * i + 1]);
        }
        // add the new value to the cache and return a private copy so the
        // cached array stays pristine if the caller mutates the result.
        resultCache.put(colourKey, y);
        return y.clone();
    }

    /**
     * Value-based cache key wrapping a copy of a function's input components.
     * Using the component values directly (rather than a hashCode) guarantees
     * that two different inputs can never map to the same cached result.
     */
    private static final class InputKey {
        private final float[] values;
        private final int hash;

        InputKey(float[] input) {
            this.values = input.clone();
            this.hash = Arrays.hashCode(this.values);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            return o instanceof InputKey && Arrays.equals(values, ((InputKey) o).values);
        }

        @Override
        public int hashCode() {
            return hash;
        }
    }
}
