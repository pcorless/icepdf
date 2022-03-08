package org.icepdf.core.util.parser.object;

import java.nio.ByteBuffer;

/**
 *
 */
public class OperatorFactory {
    @SuppressWarnings(value = "unchecked")
    public static int[] getOperator(ByteBuffer ch, int offset, int length) {

        // get the operator int value.
        try {
            return OperandNames.getType(ch, offset, length);
        } catch (Throwable e) {
            return new int[]{OperandNames.NO_OP, 0};
        }
    }
}
