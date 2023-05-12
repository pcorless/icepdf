package org.icepdf.core.util.parser.object;


import java.nio.ByteBuffer;

/**
 * Object lexer to pick up on key operands when parsing out document objects.
 */
public class OperandNames {

    public static int[] getType(ByteBuffer ch, int offset, int length) {
        byte c1;
        byte c = ch.get(offset);

        /*
          quickly switch though possible operands to find matching operands
          as quickly as possible.  A few assumptions:
          - tokens should be separated by spaces so the length should
            match the assumptions of the look ahead.
          - if the length doesn't match then we likely have malformed
            stream and we tweak the offset so the next token can be found
         */
        switch (c) {
            case 'o':
                if (length == 3) return new int[]{OP_obj, 0};
                else { // correct offset for missing white space;
                    return new int[]{OP_obj, length - 3};
                }
            case 's':
                if (length == 4) return new int[]{OP_stream, 0};
                else { // correct offset for missing white space;
                    return new int[]{OP_stream, length - 6};
                }
            case 'e':
                c1 = ch.get(offset + 3);
                switch (c1) {
                    case 'o':
                        if (length == 6) return new int[]{OP_endobj, 0};
                        else return new int[]{OP_endobj, length - 6};
                    case 's':
                        if (length == 9) return new int[]{OP_endstream, 0};
                        else return new int[]{OP_endstream, length - 9};
                }
            case 'r':
                if (length == 1) return new int[]{OP_r, 0};
                else return new int[]{OP_r, length - 1};
            case 'n':
                if (length == 1) return new int[]{OP_n, 0};
                else return new int[]{OP_n, length - 1};
            case 'f':
                if (length == 1) return new int[]{OP_f, 0};
                else return new int[]{OP_f, length - 1};
        }
        return new int[]{NO_OP, 0};
    }

    /**
     * Postscript subset of operations used in a PDF content streams
     */
    public static final int
            NO_OP = 0,
    // object
    OP_obj = 1,
            OP_endobj = 2,
    // stream
    OP_stream = 3,
            OP_endstream = 4,
    // reference
    OP_r = 5,
    // xref table entries.
    OP_f = 6,
            OP_n = 7;
}
