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
