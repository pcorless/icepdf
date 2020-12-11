package org.icepdf.core.pobjects.fonts.zfont;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.StringTokenizer;
import java.util.logging.Logger;

public class GlyphList {

    protected static final Logger logger =
            Logger.getLogger(GlyphList.class.toString());

    public static GlyphList adobeGlyphList;
    public static GlyphList zapfDingBatsGlyphList;

    static {
        initializeAdobeGlyphList();
        initializeZapfDingbatsGlyphList();
    }

    private final HashMap<String, String> nameToUnicode;

    private GlyphList(String fileName, int size) {
        nameToUnicode = GlyphListReader.readGlyphList(fileName, size);
    }

    public String toUnicode(String name) {
        String unicode = nameToUnicode.get(name);
        return unicode;
    }

    public static GlyphList getAdobeGlyphList() {
        return adobeGlyphList;
    }

    public static GlyphList getZapfDingBatsGlyphList() {
        return zapfDingBatsGlyphList;
    }

    private static void initializeAdobeGlyphList() {
        adobeGlyphList = new GlyphList("glyphlist.txt", 4281);
    }

    private static void initializeZapfDingbatsGlyphList() {
        zapfDingBatsGlyphList = new GlyphList("zapfdingbats.txt", 200);
    }

    private static class GlyphListReader {

        public static HashMap<String, String> readGlyphList(String fileName, int size) {
            HashMap<String, String> mappings = new HashMap<>(size);
            try (InputStream inputStream = Encoding.class.getResourceAsStream(
                    "/org/icepdf/core/pobjects/fonts/glyphlist/" + fileName)) {
                BufferedReader encodingBuffer = new BufferedReader(new InputStreamReader(inputStream));
                String currentLine, name;
                String[] codes;
                int[] codePoints;
                StringTokenizer toker;
                while ((currentLine = encodingBuffer.readLine()) != null) {
                    if (!currentLine.startsWith("#")) {
                        toker = new StringTokenizer(currentLine, ";");
                        name = toker.nextToken();
                        codes = toker.nextToken().split(" ");
                        codePoints = new int[codes.length];
                        for (int i = 0, max = codes.length; i < max; i++) {
                            codePoints[i] = Integer.parseInt(codes[i], 16);
                        }
                        mappings.put(name, new String(codePoints, 0, codePoints.length));
                    }
                }
                return mappings;
            } catch (IOException e) {
                logger.warning("Failed to read glyph list " + fileName);
            }
            return null;
        }

    }
}
