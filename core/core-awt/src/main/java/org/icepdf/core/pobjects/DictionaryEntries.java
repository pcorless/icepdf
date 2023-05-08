package org.icepdf.core.pobjects;

import java.util.HashMap;

public class DictionaryEntries extends HashMap<Name, Object> {
    public DictionaryEntries(int initialCapacity) {
        super(initialCapacity);
    }

    public DictionaryEntries() {
    }
}
