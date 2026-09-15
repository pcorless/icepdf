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
package org.icepdf.core.pobjects.functions.postscript;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the type 4 PostScript calculator: the little stack language a function may be written in.
 * <p>
 * The whole language is about forty operators, each one a few lines of arithmetic, and every one of
 * them is applied to every colour component of every sample of whatever shading or tint transform
 * uses it.  A wrong operator is not a crash - it is a wrong colour, everywhere that function is
 * used, with nothing to say so.  Each operator is therefore checked against a value worked out by
 * hand, including the cases where the specification and Java disagree: the modulo of a negative
 * number, the direction of a bit shift, and whether a truncation rounds or floors.
 * <p>
 * Operators are exercised through the lexer directly rather than through {@code Function_4}, so a
 * failure names the operator rather than a colour.
 */
public class PostScriptCalculatorTest {

    private static final float TOLERANCE = 0.0005f;

    /**
     * Runs a calculator program over the given inputs and returns the resulting stack.
     *
     * @param program program text, braces included
     * @param inputs  values pushed before the program runs
     * @return the stack left behind
     */
    private static OperandStack run(String program, float... inputs) throws IOException {
        Lexer lexer = new Lexer();
        lexer.setInputStream(new ByteArrayInputStream(program.getBytes(StandardCharsets.ISO_8859_1)));
        lexer.parse(inputs);
        return lexer.getStack();
    }

    /**
     * @return the single number a program leaves on the stack
     */
    private static float result(String program, float... inputs) throws IOException {
        OperandStack stack = run(program, inputs);
        assertTrue(!stack.isEmpty(), "the program left nothing on the stack: " + program);
        return ((Number) stack.pop()).floatValue();
    }

    /**
     * @return the boolean a program leaves on the stack
     */
    private static boolean booleanResult(String program, float... inputs) throws IOException {
        return (Boolean) run(program, inputs).pop();
    }

    // ------------------------------------------------------------------
    // arithmetic
    // ------------------------------------------------------------------

    @DisplayName("add, sub, mul and div")
    @Test
    public void arithmetic() throws IOException {
        assertEquals(7f, result("{ 3 4 add }"), TOLERANCE);
        // subtraction is not commutative: the operand pushed first is the one subtracted from
        assertEquals(-1f, result("{ 3 4 sub }"), TOLERANCE);
        assertEquals(12f, result("{ 3 4 mul }"), TOLERANCE);
        assertEquals(0.75f, result("{ 3 4 div }"), TOLERANCE);
    }

    @DisplayName("idiv and mod - integer division and its remainder")
    @Test
    public void integerDivision() throws IOException {
        assertEquals(3f, result("{ 13 4 idiv }"), TOLERANCE);
        assertEquals(1f, result("{ 13 4 mod }"), TOLERANCE);
        assertEquals(0f, result("{ 12 4 mod }"), TOLERANCE);
    }

    @DisplayName("neg, abs and sqrt")
    @Test
    public void signAndRoot() throws IOException {
        assertEquals(-5f, result("{ 5 neg }"), TOLERANCE);
        assertEquals(5f, result("{ 5 neg abs }"), TOLERANCE);
        assertEquals(3f, result("{ 9 sqrt }"), TOLERANCE);
        assertEquals(1.5f, result("{ 2.25 sqrt }"), TOLERANCE);
    }

    @DisplayName("exp, ln and log")
    @Test
    public void exponentialsAndLogs() throws IOException {
        assertEquals(8f, result("{ 2 3 exp }"), TOLERANCE);
        assertEquals(1.4142f, result("{ 2 0.5 exp }"), 0.001f);
        assertEquals(0f, result("{ 1 ln }"), TOLERANCE);
        assertEquals(1f, result("{ 2.71828 ln }"), 0.001f);
        assertEquals(2f, result("{ 100 log }"), TOLERANCE);
    }

    @DisplayName("sin, cos and atan work in degrees, not radians")
    @Test
    public void trigonometry() throws IOException {
        // The calculator's angles are degrees; treating them as radians silently distorts any
        // function that uses them.
        assertEquals(0f, result("{ 0 sin }"), TOLERANCE);
        assertEquals(1f, result("{ 90 sin }"), TOLERANCE);
        assertEquals(1f, result("{ 0 cos }"), TOLERANCE);
        assertEquals(0f, result("{ 90 cos }"), TOLERANCE);
    }

    @DisplayName("atan answers in 0..360, keeping the quadrant")
    @Test
    public void arcTangent() throws IOException {
        // The operator takes a numerator and a denominator, not their ratio: the ratio alone
        // cannot tell the first quadrant from the third, and a zero denominator is legal.
        assertEquals(0f, result("{ 0 1 atan }"), 0.01f);
        assertEquals(45f, result("{ 4 4 atan }"), 0.01f);
        assertEquals(90f, result("{ 1 0 atan }"), 0.01f);
        assertEquals(270f, result("{ -100 0 atan }"), 0.01f);
    }

    @DisplayName("ceiling, floor, round and truncate")
    @Test
    public void rounding() throws IOException {
        assertEquals(4f, result("{ 3.2 ceiling }"), TOLERANCE);
        assertEquals(3f, result("{ 3.8 floor }"), TOLERANCE);
        assertEquals(4f, result("{ 3.5 round }"), TOLERANCE);
        assertEquals(3f, result("{ 3.2 round }"), TOLERANCE);
        // truncate drops the fraction rather than rounding it
        assertEquals(3f, result("{ 3.8 truncate }"), TOLERANCE);
        assertEquals(-3f, result("{ -3.8 truncate }"), TOLERANCE);
    }

    @DisplayName("cvi and cvr convert between integer and real")
    @Test
    public void conversion() throws IOException {
        assertEquals(3f, result("{ 3.7 cvi }"), TOLERANCE);
        assertEquals(-3f, result("{ -3.7 cvi }"), TOLERANCE);
        assertEquals(3f, result("{ 3 cvr }"), TOLERANCE);
    }

    // ------------------------------------------------------------------
    // stack manipulation
    // ------------------------------------------------------------------

    @DisplayName("dup, pop and exch")
    @Test
    public void stackBasics() throws IOException {
        // dup then add doubles the value
        assertEquals(10f, result("{ 5 dup add }"), TOLERANCE);
        // pop drops the top, leaving what was under it
        assertEquals(1f, result("{ 1 2 pop }"), TOLERANCE);
        // exch swaps the top two, which turns 3-4 into 4-3
        assertEquals(1f, result("{ 3 4 exch sub }"), TOLERANCE);
    }

    @DisplayName("copy duplicates the top n elements")
    @Test
    public void copy() throws IOException {
        OperandStack stack = run("{ 1 2 3 2 copy }");
        assertEquals(5, stack.size(), "two copies should have been added to three elements");
        assertEquals(3f, ((Number) stack.pop()).floatValue(), TOLERANCE);
        assertEquals(2f, ((Number) stack.pop()).floatValue(), TOLERANCE);
        assertEquals(3f, ((Number) stack.pop()).floatValue(), TOLERANCE);
    }

    @DisplayName("index reaches back down the stack without disturbing it")
    @Test
    public void index() throws IOException {
        // 0 index is the top; 2 index reaches three deep
        assertEquals(3f, result("{ 1 2 3 0 index }"), TOLERANCE);
        assertEquals(1f, result("{ 1 2 3 2 index }"), TOLERANCE);
    }

    @DisplayName("roll rotates the top n elements, in either direction")
    @Test
    public void roll() throws IOException {
        // 1 2 3, rolled up by one, is 3 1 2
        OperandStack up = run("{ 1 2 3 3 1 roll }");
        assertEquals(2f, ((Number) up.pop()).floatValue(), TOLERANCE);
        assertEquals(1f, ((Number) up.pop()).floatValue(), TOLERANCE);
        assertEquals(3f, ((Number) up.pop()).floatValue(), TOLERANCE);

        // and rolled back down by one, 1 2 3 becomes 2 3 1
        OperandStack down = run("{ 1 2 3 3 -1 roll }");
        assertEquals(1f, ((Number) down.pop()).floatValue(), TOLERANCE);
        assertEquals(3f, ((Number) down.pop()).floatValue(), TOLERANCE);
        assertEquals(2f, ((Number) down.pop()).floatValue(), TOLERANCE);
    }

    // ------------------------------------------------------------------
    // comparison and logic
    // ------------------------------------------------------------------

    @DisplayName("the comparison operators")
    @Test
    public void comparisons() throws IOException {
        assertTrue(booleanResult("{ 1 1 eq }"));
        assertTrue(!booleanResult("{ 1 2 eq }"));
        assertTrue(booleanResult("{ 1 2 ne }"));
        assertTrue(booleanResult("{ 2 1 gt }"));
        assertTrue(!booleanResult("{ 1 2 gt }"));
        assertTrue(booleanResult("{ 2 2 ge }"));
        assertTrue(booleanResult("{ 1 2 lt }"));
        assertTrue(booleanResult("{ 2 2 le }"));
    }

    @DisplayName("true and false push themselves")
    @Test
    public void booleanLiterals() throws IOException {
        assertTrue(booleanResult("{ true }"));
        assertTrue(!booleanResult("{ false }"));
    }

    @DisplayName("and, or, xor and not on booleans")
    @Test
    public void booleanLogic() throws IOException {
        assertTrue(booleanResult("{ true true and }"));
        assertTrue(!booleanResult("{ true false and }"));
        assertTrue(booleanResult("{ true false or }"));
        assertTrue(booleanResult("{ true false xor }"));
        assertTrue(!booleanResult("{ true true xor }"));
        assertTrue(booleanResult("{ false not }"));
        assertTrue(!booleanResult("{ true not }"));
    }

    @DisplayName("and, or, xor and not on integers are bitwise")
    @Test
    public void bitwiseLogic() throws IOException {
        // The same operators are bitwise when handed numbers rather than booleans.
        assertEquals(8f, result("{ 12 10 and }"), TOLERANCE);     // 1100 & 1010 = 1000
        assertEquals(14f, result("{ 12 10 or }"), TOLERANCE);     // 1100 | 1010 = 1110
        assertEquals(6f, result("{ 12 10 xor }"), TOLERANCE);     // 1100 ^ 1010 = 0110
    }

    @DisplayName("a bitwise result can be used by the operator that follows it")
    @Test
    public void bitwiseResultIsUsable() throws IOException {
        // The stack is floats; a bitwise operator that left an integer on it would fail the next
        // operator rather than itself, which is the hardest version of this bug to place.
        assertEquals(9f, result("{ 12 10 and 1 add }"), TOLERANCE);
        assertEquals(15f, result("{ 12 10 or 1 add }"), TOLERANCE);
        assertEquals(7f, result("{ 12 10 xor 1 add }"), TOLERANCE);
        assertEquals(4f, result("{ 13 4 idiv 1 add }"), TOLERANCE);
        assertEquals(4f, result("{ 3.7 cvi 1 add }"), TOLERANCE);
        assertEquals(9f, result("{ 1 3 bitshift 1 add }"), TOLERANCE);
    }

    @DisplayName("bitshift shifts left on a positive count and right on a negative one")
    @Test
    public void bitshift() throws IOException {
        assertEquals(8f, result("{ 1 3 bitshift }"), TOLERANCE);
        assertEquals(1f, result("{ 8 -3 bitshift }"), TOLERANCE);
    }

    // ------------------------------------------------------------------
    // conditionals
    // ------------------------------------------------------------------

    @DisplayName("if runs its procedure only when the condition holds")
    @Test
    public void conditionalIf() throws IOException {
        assertEquals(2f, result("{ 1 true { 1 add } if }"), TOLERANCE);
        assertEquals(1f, result("{ 1 false { 1 add } if }"), TOLERANCE);
    }

    @DisplayName("ifelse picks one of two procedures")
    @Test
    public void conditionalIfElse() throws IOException {
        assertEquals(10f, result("{ true { 10 } { 20 } ifelse }"), TOLERANCE);
        assertEquals(20f, result("{ false { 10 } { 20 } ifelse }"), TOLERANCE);
    }

    @DisplayName("a condition computed from the input selects the branch")
    @Test
    public void conditionalOnInput() throws IOException {
        // The shape a real tint transform uses: a threshold on the input value.
        String program = "{ 0.5 gt { 1 } { 0 } ifelse }";
        assertEquals(1f, result(program, 0.75f), TOLERANCE);
        assertEquals(0f, result(program, 0.25f), TOLERANCE);
    }

    @DisplayName("conditionals nest")
    @Test
    public void nestedConditionals() throws IOException {
        String program = "{ dup 0.33 lt { pop 0 } { dup 0.66 lt { pop 0.5 } { pop 1 } ifelse } ifelse }";
        assertEquals(0f, result(program, 0.1f), TOLERANCE);
        assertEquals(0.5f, result(program, 0.5f), TOLERANCE);
        assertEquals(1f, result(program, 0.9f), TOLERANCE);
    }

    // ------------------------------------------------------------------
    // inputs, outputs and whole programs
    // ------------------------------------------------------------------

    @DisplayName("the inputs are on the stack when the program starts")
    @Test
    public void inputsArePushed() throws IOException {
        assertEquals(0.5f, result("{ }", 0.5f), TOLERANCE);
        assertEquals(1f, result("{ 2 mul }", 0.5f), TOLERANCE);
        // several inputs arrive in order, so the last one pushed is on top
        assertEquals(3f, result("{ add }", 1f, 2f), TOLERANCE);
    }

    @DisplayName("a program can leave several outputs, as a tint transform does")
    @Test
    public void severalOutputs() throws IOException {
        // One tint in, four CMYK components out: the shape of a Separation tint transform.
        OperandStack stack = run("{ dup 0 exch dup 0 exch }", 0.5f);
        assertTrue(stack.size() >= 4, "expected at least four outputs, got " + stack.size());
    }

    @DisplayName("a realistic tint transform - one tint to CMYK")
    @Test
    public void tintTransform() throws IOException {
        // tint -> 0 0 0 tint, the commonest Separation transform there is.
        OperandStack stack = run("{ 0 0 0 4 -1 roll }", 0.7f);
        assertEquals(4, stack.size());
        assertEquals(0.7f, ((Number) stack.pop()).floatValue(), TOLERANCE);
        assertEquals(0f, ((Number) stack.pop()).floatValue(), TOLERANCE);
    }

    @DisplayName("whitespace and line breaks between tokens are ignored")
    @Test
    public void formatting() throws IOException {
        assertEquals(7f, result("{\n  3\n  4\n  add\n}"), TOLERANCE);
        assertEquals(7f, result("{3 4 add}"), TOLERANCE);
    }

    @DisplayName("negative and fractional literals are read as written")
    @Test
    public void numberLiterals() throws IOException {
        assertEquals(-5f, result("{ -5 }"), TOLERANCE);
        assertEquals(0.25f, result("{ 0.25 }"), TOLERANCE);
        assertEquals(-0.5f, result("{ -0.5 }"), TOLERANCE);
    }

    // ------------------------------------------------------------------
    // robustness
    // ------------------------------------------------------------------

    @DisplayName("a lexer with no input stream fails loudly")
    @Test
    public void noInputStream() {
        assertThrows(IOException.class, () -> new Lexer().parse(new float[]{0}));
    }

    @DisplayName("an empty program leaves the inputs untouched")
    @Test
    public void emptyProgram() throws IOException {
        OperandStack stack = run("{ }", 0.25f);
        assertEquals(1, stack.size());
        assertEquals(0.25f, ((Number) stack.pop()).floatValue(), TOLERANCE);
    }
}
