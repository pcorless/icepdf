package org.icepdf.core.pobjects;

import java.util.LinkedHashMap;

public class DictionaryEntries extends LinkedHashMap<Name, Object> {
    public DictionaryEntries(int initialCapacity) {
        super(initialCapacity);
    }

    public DictionaryEntries() {
    }
}
