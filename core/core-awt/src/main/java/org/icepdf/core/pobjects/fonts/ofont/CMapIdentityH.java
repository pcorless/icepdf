package org.icepdf.core.pobjects.fonts.ofont;

public class CMapIdentityH extends CMapIdentity {

    public String fromSelector(String str) {
        int length = str.length();
        StringBuilder builder = new StringBuilder(length * 2);
        for (int i = 0; i < length; i++) {
            builder.append((char) (str.charAt(i) >> 8)).append((char) (str.charAt(i) & 0xff));
        }
        return String.valueOf(builder);
    }

    public String toSelector(String str) {
        int length = str.length();
        StringBuilder builder = new StringBuilder(length / 2);
        for (int i = 0; i < length; i += 2) {
            builder.append((char) (str.charAt(i) << 8 | str.charAt(i + 1)));
        }
        return String.valueOf(builder);
    }
}