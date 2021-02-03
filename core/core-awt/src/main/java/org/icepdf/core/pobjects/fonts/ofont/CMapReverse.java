package org.icepdf.core.pobjects.fonts.ofont;

public class CMapReverse extends CMap {

    public CMapReverse(int[] cidToGidMap) {
        super(cidToGidMap);
    }

    @Override
    public char toSelector(char charMap) {
        if (charMap < codeSpaceRange[0].length) {
            return (char) codeSpaceRange[0][charMap];
        }
        return charMap;
    }
}
