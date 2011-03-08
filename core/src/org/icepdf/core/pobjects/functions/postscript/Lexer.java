/*
 * Version: MPL 1.1/GPL 2.0/LGPL 2.1
 *
 * "The contents of this file are subject to the Mozilla Public License
 * Version 1.1 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
 * License for the specific language governing rights and limitations under
 * the License.
 *
 * The Original Code is ICEpdf 4.1 open source software code, released
 * May 1st, 2009. The Initial Developer of the Original Code is ICEsoft
 * Technologies Canada, Corp. Portions created by ICEsoft are Copyright (C)
 * 2004-2010 ICEsoft Technologies Canada, Corp. All Rights Reserved.
 *
 * Contributor(s): _____________________.
 *
 * Alternatively, the contents of this file may be used under the terms of
 * the GNU Lesser General Public License Version 2.1 or later (the "LGPL"
 * License), in which case the provisions of the LGPL License are
 * applicable instead of those above. If you wish to allow use of your
 * version of this file only under the terms of the LGPL License and not to
 * allow others to use your version of this file under the MPL, indicate
 * your decision by deleting the provisions above and replace them with
 * the notice and other provisions required by the LGPL License. If you do
 * not delete the provisions above, a recipient may use your version of
 * this file under either the MPL or the LGPL License."
 *
 */
package org.icepdf.core.pobjects.functions.postscript;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Stack;

/**
 * A state machine used to parse valid type 4 functions tokens in a input
 * stream.  As the tokens are parsed found operands are executed to mananipulate
 * the stack.
 *
 * @author ICEsoft Technologies Inc.
 * @since 4.2
 */
public class Lexer {

    // stream reader pointers.
    private Reader reader;
    private char[] buf = new char[2056];
    private int pos = 0, numRead = 0, startTokenPos = 0;
    private int tokenType = 0;

    // expression depth count used to properly differ if and elseif operands.
    private int expressionDepth;

    // lexer states
    private static final int
            TOKEN_NUMBER = 1,
            TOKEN_OPERAND = 2,
            TOKEN_EXPRESSION = 3,
            TOKEN_BOOLEAN = 5;

    // stack to hold operads.
    private Stack<Object> stack;

    public Lexer() {
        stack = new Stack<Object>();
    }

    /**
     * Type 4 function input stream to pars.e
     *
     * @param in type 4 function input stream.
     */
    public void setInputStream(InputStream in) {
        setReader(new InputStreamReader(in));
    }

    protected void setReader(Reader reader) {
        this.reader = reader;
    }

    /**
     * Parse the input stream associated with this instance.
     *
     * @param input array of 1 or more numbers to be pushed onto the stack before
     *              the type 4 function is executed.
     * @throws IOException if the input stream is null or incomplete.
     */
    public void parse(float[] input) throws IOException {

        if (reader == null) {
            throw new IOException("Type 4 function, null input stream reader.");
        }

        // push input values on the stack
        for (Number num : input) {
            stack.push(num);
        }

        tokenType = TOKEN_EXPRESSION;

        boolean done = false;
        while (!done) {

            // Did we reach the end of the buffer, if so copy the next block
            // of data into the buffer.
            if (pos == buf.length) {
                // Copy the start of the token to the beginning
                System.arraycopy(buf, startTokenPos, buf, 0, pos - startTokenPos);
                pos = buf.length - startTokenPos;
                startTokenPos = 0;
                numRead = pos;
            }
            // Read at pos position
            int n = reader.read(buf, pos, buf.length - pos);
            if (n <= 0) break;
            numRead += n;

            // Scan to the numRead
            while (pos < numRead) {
                if (tokenType == TOKEN_NUMBER) {
                    numberStart();
                } else if (tokenType == TOKEN_OPERAND) {
                    operandStart();
                } else if (tokenType == TOKEN_BOOLEAN) {
                    booleanStart();
                } else if (tokenType == TOKEN_EXPRESSION) {
                    expressionStart();
                }
            }
        }
    }

    /**
     * Gets the stack associated with this lexer.  Once parse has successfully
     * executed the stack will contain n numbers which represent the type 4
     * function output.
     *
     * @return stack containing the output of the type 4 function.  If #parse()
     *         was not called the stack will be empty
     */
    public Stack getStack() {
        return stack;
    }

    /**
     * Utility to find the next token state.
     */
    private void parseNextState() {
        while (pos < numRead) {
            if (!(buf[pos] == ' ' || buf[pos] == '\t' ||
                    buf[pos] == '\n' || buf[pos] == '\r')) {
                break;
            }
            pos++;
        }

        // We found the end
        if (pos < numRead) {
            startTokenPos = pos;
            // look for number tokens.
            if (buf[pos] < 'A') {
                tokenType = TOKEN_NUMBER;
            }
            // else we have a boolean or operand.
            else {
                // look for a boolean
                if ((buf[pos] == 'f' && buf[pos + 1] == 'a') ||
                        (buf[pos] == 't' && buf[pos + 3] == 'e')) {
                    tokenType = TOKEN_BOOLEAN;
                }
                // otherwise we have an operand.
                else if (buf[pos] < '{') {
                    tokenType = TOKEN_OPERAND;
                }
                // special expression or procedure definition
                else if (buf[pos] == '{' || buf[pos] == '}') {
                    tokenType = TOKEN_EXPRESSION;
                } else {
                    parseNextState();
                }
            }
        }
    }

    /**
     * Utility to find an expression {some opps}.  We always ignore the first
     * as it is the start of the function but all other will be assoicated with
     * a if or elseif operand and as a result we don't eval the containing
     * operands until the if or elseif operand is encountered.
     */
    private void expressionStart() {
        while (pos < numRead) {
            if (!(buf[pos] == '{' || buf[pos] == '}')) {
                break;
            }
            pos++;
        }
        if (pos < numRead) {
            Operator operand = OperatorFactory.getOperator(buf,
                    startTokenPos, pos - startTokenPos);
            // found a start
            if (operand.getType() == OperatorNames.OP_EXP_START) {
                expressionDepth++;
            }
            // if not the first '{' we defer execution by putting it on the stack.
            if (expressionDepth > 1) {
                stack.push(operand);
            }
            // found '}' so we decrement our depth count.
            if (operand.getType() == OperatorNames.OP_EXP_END) {
                expressionDepth--;
            }
        }
        // go baby go!
        parseNextState();
    }

    /**
     * Utility for processing the operand state.
     */
    private void operandStart() {
        startTokenPos = pos;
        while (pos < numRead) {
            if (isDelimiter(buf[pos])) {
                break;
            }
            pos++;
        }
        if (pos < numRead && pos > startTokenPos) {
            Operator operand = OperatorFactory.getOperator(buf, startTokenPos, pos - startTokenPos);
            // execute differed execution by looking at expression depth.
            if (expressionDepth > 1) {
                stack.push(operand);
            } else {
                // execute the operand
                operand.eval(stack);
            }
        }
        parseNextState();
    }

    /**
     * Utility of processing a number state.
     */
    private void numberStart() {
        startTokenPos = pos;
        while (pos < numRead) {
            if (isDelimiter(buf[pos])) {
                break;
            }
            pos++;
        }
        if (pos < numRead) {
            // push the number
            stack.push(Float.parseFloat(new String(buf, startTokenPos, pos - startTokenPos)));
        }
        parseNextState();
    }

    /**
     * Utility for processing boolean
     */
    private void booleanStart() {
        while (pos < numRead) {
            if (isDelimiter(buf[pos])) {
                break;
            }
            pos++;
        }
        if (pos < numRead) {
            stack.push(Boolean.valueOf(new String(buf, startTokenPos, pos - startTokenPos)));
        }
        parseNextState();
    }

    /**
     * Utility for finding token delimiter in a type 4 function stream.
     * @param c character to compare against known delimiters.
     * @return true if c is a delimiter otherwise, false.
     */
    private static boolean isDelimiter(char c){
        return c == ' ' || c == '\t' ||
                c == '\n' || c == '\r' ||
                c == '{' || c == '}';
    }
}
