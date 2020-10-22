package org.icepdf.core.pobjects.fonts.zfont;

import org.icepdf.core.pobjects.fonts.Font;
import org.icepdf.core.util.Library;

import java.util.HashMap;

public class CompositeFont extends Font {

    public CompositeFont(Library library, HashMap entries) {
        super(library, entries);
    }

    @Override
    public void init() {

    }
}