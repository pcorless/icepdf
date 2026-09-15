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
package org.icepdf.core.pobjects.functions.postscript;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Type 4 (PostScript calculator) function tests.  Each function is taken from a
 * document that exercised a particular corner of the calculator grammar and the
 * expected output was derived from the PostScript operator semantics described
 * in the PDF specification, section 7.10.5.
 */
class LexerTest {

    private static final float TOLERANCE = 0.000001f;

    @DisplayName("type 4 function - roll, index, mul and add")
    @Test
    public void tint_transform_with_roll_and_index() throws IOException {
        String function =
                "{1.000000 3 1 roll 1.000000 3 1 roll 1.000000 3 1 roll 5 -1 roll \n" +
                        "2 index -0.874500 mul 1.000000 add mul 1 index -0.098000 mul 1.000000 add mul 5 \n" +
                        "1 roll 4 -1 roll 2 index -0.796100 mul 1.000000 add mul 1 index -0.247100 \n" +
                        "mul 1.000000 add mul 4 1 roll 3 -1 roll 2 index -0.647100 mul 1.000000 \n" +
                        "add mul 1 index -0.878400 mul 1.000000 add mul 3 1 roll pop pop }";

        assertOutput(function, new float[]{1.0f, 1.0f},
                new float[]{0.11320102f, 0.1535163f, 0.042912632f});
    }

    // ficha--3--para+impresion.pdf page 1
    @DisplayName("type 4 function - cvr, exch and sub invert twice")
    @Test
    public void rgb_to_cmyk_double_inversion() throws IOException {
        String function =
                "{2 index 1.000000 cvr exch sub 4 1 roll 1 index 1.000000 cvr exch sub \n" +
                        "4 1 roll 0 index 1.000000 cvr exch sub 4 1 roll 1.000000 4 1 \n" +
                        "roll 7 -1 roll 1.000000 cvr exch sub 7 1 roll 6 -1 roll 1.000000 \n" +
                        "cvr exch sub 6 1 roll 5 -1 roll 1.000000 cvr exch sub 5 1 \n" +
                        "roll 4 -1 roll 1.000000 cvr exch sub 4 1 roll pop pop pop }";

        // two inversions of each component leave the input untouched, with a
        // zero black component appended.
        assertOutput(function, new float[]{0.360779f, 0.094238274f, 0.00392151f},
                new float[]{0.360779f, 0.09423828f, 0.003921509f, 0.0f});
    }

    // ficha--3--para+impresion.pdf page 2
    @DisplayName("type 4 function - separation to cmyk")
    @Test
    public void separation_tint_to_cmyk() throws IOException {
        String function =
                "{1.000000 2 1 roll 1.000000 2 1 roll 1.000000 2 1 roll 0 index 1.000000 \n" +
                        "cvr exch sub 2 1 roll 5 -1 roll 1.000000 cvr exch sub 5 1 \n" +
                        "roll 4 -1 roll 1.000000 cvr exch sub 4 1 roll 3 -1 roll 1.000000 \n" +
                        "cvr exch sub 3 1 roll 2 -1 roll 1.000000 cvr exch sub 2 1 \n" +
                        "roll pop }";

        assertOutput(function, new float[]{0.300003f},
                new float[]{0.0f, 0.0f, 0.0f, 0.300003f});
    }

    // 9560_test.pdf page 2
    @DisplayName("type 4 function - if clamps each component")
    @Test
    public void conditional_if_clamps_components() throws IOException {
        String function =
                "{0 0 0 0 5 4 roll 0 index 3 -1 roll add 2 1 roll pop dup 1 gt " +
                        "{pop 1} if 4 1 roll dup 1 gt {pop 1} if 4 1 roll dup 1 gt " +
                        "{pop 1} if 4 1 roll dup 1 gt {pop 1} if 4 1 roll}";

        assertOutput(function, new float[]{1f}, new float[]{0.0f, 0.0f, 0.0f, 1.0f});
    }

    @DisplayName("type 4 function - ifelse and exp clamp to domain")
    @Test
    public void conditional_ifelse_clamps_to_domain() throws IOException {
        String function =
                "{dup 0 lt {pop 0 }{dup 1 gt {pop 1 } if } ifelse 0 index 1 exp 1 mul 0 add " +
                        "dup 0 lt {pop 0 }{dup 1 gt {pop 1 } if } ifelse 1 index 1 exp 0 mul 0 add " +
                        "dup 0 lt {pop 0 }{dup 1 gt {pop 1 } if } ifelse 2 index 1 exp 0 mul 0 add " +
                        "dup 0 lt {pop 0 }{dup 1 gt {pop 1 } if } ifelse 3 index 1 exp 0 mul 0 add " +
                        "dup 0 lt {pop 0 }{dup 1 gt {pop 1 } if } ifelse 5 4 roll pop }";

        assertOutput(function, new float[]{1.0f}, new float[]{1.0f, 0.0f, 0.0f, 0.0f});
    }

    @DisplayName("type 4 function - input values are pushed in order")
    @Test
    public void input_is_pushed_on_the_stack_in_order() throws IOException {
        assertOutput("{ }", new float[]{0.25f, 0.5f, 0.75f},
                new float[]{0.25f, 0.5f, 0.75f});
    }

    @DisplayName("type 4 function - stack is empty when parse is not called")
    @Test
    public void stack_is_empty_before_parse() {
        assertEquals(0, new Lexer().getStack().size());
    }

    @DisplayName("type 4 function - null input stream is an error")
    @Test
    public void parse_without_an_input_stream_throws() {
        assertThrows(IOException.class, () -> new Lexer().parse(new float[]{1.0f}));
    }

    /**
     * Parses the given type 4 function with the given input values and compares
     * the resulting stack, bottom to top, with the expected output.
     */
    private static void assertOutput(String function, float[] input, float[] expected)
            throws IOException {
        Lexer lexer = new Lexer();
        lexer.setInputStream(new ByteArrayInputStream(function.getBytes(StandardCharsets.UTF_8)));
        lexer.parse(input);

        OperandStack stack = lexer.getStack();
        assertEquals(expected.length, stack.size(), "unexpected stack depth: " + stack);
        float[] actual = new float[stack.size()];
        for (int i = 0; i < actual.length; i++) {
            actual[i] = ((Number) stack.elementAt(i)).floatValue();
        }
        assertArrayEquals(expected, actual, TOLERANCE, "unexpected output: " + stack);
    }
}
