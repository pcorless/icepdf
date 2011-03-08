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
package org.icepdf.core.pobjects.functions;

import org.icepdf.core.io.SeekableInput;
import org.icepdf.core.pobjects.Dictionary;
import org.icepdf.core.pobjects.Stream;
import org.icepdf.core.pobjects.functions.postscript.Lexer;
import org.icepdf.core.util.Utils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Stack;
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
 * @author ICEsoft Technologies Inc.
 * @since 4.2
 */
public class Function_4 extends Function {

    private static final Logger logger =
            Logger.getLogger(Function_4.class.toString());

    // decoded content that makes up the type 4 functions.
    private String functionContent;

    public Function_4(Dictionary d) {
        super(d);
        // decode the stream for parsing.
        if (d instanceof Stream) {
            Stream functionStream = (Stream) d;
            InputStream input = functionStream.getInputStreamForDecodedStreamBytes();
            if (input instanceof SeekableInput) {
                functionContent = Utils.getContentFromSeekableInput((SeekableInput) input, false);
            } else {
                InputStream[] inArray = new InputStream[]{input};
                functionContent = Utils.getContentAndReplaceInputStream(inArray, false);
            }
            logger.finest("Function 4: " + functionContent);
        } else {
            logger.warning("Type 4 function operands could not be found.");
        }
    }

    /**
     * <p>Puts the value x thought the function type 4 algorithm.
     *
     * @param x input values m
     * @return output values n
     */
    public float[] calculate(float[] x) {

        InputStream content = new ByteArrayInputStream(functionContent.getBytes());

        Lexer lex = new Lexer();
        lex.setInputStream(content);

        // parse/evaluate the type 4 functions with the input value(s) x.
        try {
            lex.parse(x);
        } catch (IOException e) {
            logger.log(Level.WARNING, "Error Processing Type 4 definition", e);
        }

        // get the remaining number on the stack which are the return values.
        Stack stack = lex.getStack();

        // length of output array
        int n = range.length / 2;
        // ready output array
        float y[] = new float[n];

        // pop remaining items off the stack and apply the range bounds.
        for (int i = 0; i < n; i++) {
            y[i] = Math.min(Math.max((Float) stack.elementAt(i),
                    range[2 * i]), range[2 * i + 1]);
        }
        return y;
    }
}
