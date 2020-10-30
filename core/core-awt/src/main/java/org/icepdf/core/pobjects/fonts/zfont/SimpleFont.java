package org.icepdf.core.pobjects.fonts.zfont;

import org.icepdf.core.util.Library;

import java.util.HashMap;

public class SimpleFont extends Font {


    public SimpleFont(Library library, HashMap entries) {
        super(library, entries);

    }

    @Override
    public void init() {
        super.init();

        if (widths != null) {
            // Assigns the First character code defined in the font's Widths array
            Object o = library.getObject(entries, FIRST_CHAR_KEY);
            if (o != null) {
                firstchar = ((Number) o).intValue();
            }
        }
    }
}
