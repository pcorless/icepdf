/*
 * Copyright 2026 Patrick Corless
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
package org.icepdf.core.pobjects.graphics.text;

/**
 * An immutable page-local caret: a character {@code offset} into a {@link TextSequence}
 * plus a {@link Bias} used to disambiguate boundary positions.  The owning viewer is
 * responsible for pairing this with a page index for document-level selection.
 *
 * @see TextSequence
 * @since 7.5
 */
public final class Caret {

    private final int offset;
    private final Bias bias;

    public Caret(int offset, Bias bias) {
        this.offset = offset;
        this.bias = bias;
    }

    public int getOffset() {
        return offset;
    }

    public Bias getBias() {
        return bias;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Caret)) return false;
        Caret caret = (Caret) o;
        return offset == caret.offset && bias == caret.bias;
    }

    @Override
    public int hashCode() {
        return 31 * offset + (bias == null ? 0 : bias.hashCode());
    }

    @Override
    public String toString() {
        return "Caret[" + offset + ", " + bias + ']';
    }
}
