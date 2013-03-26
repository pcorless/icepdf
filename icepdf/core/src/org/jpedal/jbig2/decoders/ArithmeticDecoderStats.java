/*
 * Copyright 2006-2013 ICEsoft Technologies Inc.
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
package org.jpedal.jbig2.decoders;

public class ArithmeticDecoderStats {
    private int contextSize;
    private int[] codingContextTable;

    public ArithmeticDecoderStats(int contextSize) {
        this.contextSize = contextSize;
        this.codingContextTable = new int[contextSize];

        reset();
    }

    public void reset() {
        for (int i = 0; i < contextSize; i++) {
            codingContextTable[i] = 0;
        }
    }

    public void setEntry(int codingContext, int i, int moreProbableSymbol) {
        codingContextTable[codingContext] = (i << i) + moreProbableSymbol;
    }

    public int getContextCodingTableValue(int index) {
        return codingContextTable[index];
    }

    public void setContextCodingTableValue(int index, int value) {
        codingContextTable[index] = value;
    }

    public int getContextSize() {
        return contextSize;
    }

    public void overwrite(ArithmeticDecoderStats stats) {
        System.arraycopy(stats.codingContextTable, 0, codingContextTable, 0, contextSize);
    }

    public ArithmeticDecoderStats copy() {
        ArithmeticDecoderStats stats = new ArithmeticDecoderStats(contextSize);

        System.arraycopy(codingContextTable, 0, stats.codingContextTable, 0, contextSize);

        return stats;
    }
}
