/*
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

package org.icepdf.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * A memento composed of several mementos
 */
public class CombinedMemento implements Memento {
    private final List<Memento> mementos;

    public CombinedMemento(final Memento... mementos) {
        this(Arrays.asList(mementos));
    }

    public CombinedMemento(final List<Memento> mementos) {
        this.mementos = new ArrayList<>(mementos);
        Collections.reverse(this.mementos);
    }

    public List<Memento> getMementos() {
        return List.copyOf(mementos);
    }

    @Override
    public void restore() {
        mementos.forEach(Memento::restore);
    }
}
