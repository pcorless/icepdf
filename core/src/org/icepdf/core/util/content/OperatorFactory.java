package org.icepdf.core.util.content;


/**
 *
 */
public class OperatorFactory {

//    private static HashMap<Integer, Operator> operatorCache =
//            new HashMap<Integer, Operator>();

    @SuppressWarnings(value = "unchecked")
    public static int[] getOperator(byte ch[], int offset, int length) {

        // get the operator int value.
        return OperandNames.getType(ch, offset, length);
    }

}
