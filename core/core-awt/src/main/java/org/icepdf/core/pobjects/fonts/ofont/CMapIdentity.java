package org.icepdf.core.pobjects.fonts.ofont;

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

    public String fromSelector(String str) {
        return str;
    }

    public char fromSelector(char ech) {
        return ech;
    }

    public String toUnicode(char ech) {
        return String.valueOf(ech);
    }
}