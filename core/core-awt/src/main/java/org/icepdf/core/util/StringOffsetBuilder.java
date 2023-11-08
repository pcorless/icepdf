package org.icepdf.core.util;

import java.util.ArrayList;
import java.util.List;

// todo probably don't need this class anymore.   TextSprint/GlyphText
public class StringOffsetBuilder {
    private StringBuilder baseString;
    // todo probably could be smaller then int
    private List<Integer> offsets;


    public StringOffsetBuilder(StringBuilder stringData, int offset) {
        baseString = stringData;
        offsets = new ArrayList<>(baseString.length());
        for (int i = 0, max = baseString.length(); i < max; i++) {
            offsets.add(offset);
        }
    }

    public StringOffsetBuilder(int length) {
        offsets = new ArrayList<>(length);
        baseString = new StringBuilder(length);
    }

    public void append(char character, int offset) {
        baseString.append(character);
        offsets.add(offset);
    }

    public char charAt(int index) {
        return baseString.charAt(index);
    }

    public int offsetAt(int index) {
        return offsets.get(index);
    }

    public int length() {
        return baseString.length();
    }
}
