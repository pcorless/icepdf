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
package org.icepdf.fx.ri;

/**
 * Where the annotation board keeps the user's choices for how it looks.  None of it is written to
 * the document.  {@link #inMemory()} keeps them while the viewer is open; the viewer application keeps
 * them in its settings.
 */
public interface BoardStore {

    /** Text size used when none has been chosen: the board follows the window's font. */
    double DEFAULT_TEXT_SIZE = 0;

    /** Whether cards show one line each rather than everything. */
    boolean isCompact();

    void setCompact(boolean compact);

    /** The cards' text size in pixels, or {@link #DEFAULT_TEXT_SIZE}. */
    double getTextSize();

    void setTextSize(double size);

    /** A store that lives only as long as the viewer, for when the application doesn't supply one. */
    static BoardStore inMemory() {
        return new BoardStore() {
            private boolean compact;
            private double textSize = DEFAULT_TEXT_SIZE;

            @Override
            public boolean isCompact() {
                return compact;
            }

            @Override
            public void setCompact(boolean compact) {
                this.compact = compact;
            }

            @Override
            public double getTextSize() {
                return textSize;
            }

            @Override
            public void setTextSize(double size) {
                this.textSize = size;
            }
        };
    }
}
