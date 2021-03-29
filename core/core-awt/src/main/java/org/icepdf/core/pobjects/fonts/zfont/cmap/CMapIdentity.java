package org.icepdf.core.pobjects.fonts.zfont.cmap;

class CMapIdentity extends CMap {

    public String toSelector(String str) {
        return str;
    }

    public char toSelector(char ech) {
        return ech;
    }

    public char toSelector(char ech, boolean isCFF) {
        return ech;
    }

    public char fromSelector(char ech) {
        return ech;
    }

    public String toUnicode(char ech) {
        return String.valueOf(ech);
    }
}