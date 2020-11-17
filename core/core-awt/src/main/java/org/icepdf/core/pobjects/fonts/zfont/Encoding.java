package org.icepdf.core.pobjects.fonts.zfont;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.logging.Logger;

/**
 * put your documentation comment here
 */
public class Encoding implements org.icepdf.core.pobjects.fonts.Encoding {

    protected static final Logger logger =
            Logger.getLogger(Encoding.class.toString());

    // Latin-text encoding names
    public static final String STANDARD_ENCODING_NAME = "StandardEncoding";
    public static final String MAC_ROMAN_ENCODING_NAME = "MacRomanEncoding";
    public static final String WIN_ANSI_ENCODING_NAME = "WinAnsiEncoding";
    public static final String PDF_DOC_ENCODING_NAME = "PDFDocEncoding;";
    // MacExpertEncoding
    public static final String MAC_EXPERT_ENCODING_NAME = "MacExpertEncoding";
    // Symbol
    public static final String SYMBOL_ENCODING_NAME = "SYMBOL";
    // ZAPF_DINGBATS
    public static final String ZAPF_DINGBATS_ENCODING_NAME = "ZAPF_DINGBATS";

    /**
     * Adobe standard Latin-text encoding. This is the built-in encoding defined
     * in Type 1 Latin-text font programs (but generally not in TrueType font
     * programs). Conforming readers shall not have a predefined encoding
     * named StandardEncoding. However, it is necessary to describe this
     * encoding, since a font’s built-in encoding can be used as the base
     * encoding from which differences may be specified in an encoding
     * dictionary.
     */
    public static Encoding standardEncoding;

    /**
     * Mac OS standard encoding for Latin text in Western writing systems.
     * Conforming readers shall have a predefined encoding named
     * MacRomanEncoding that may be used with both Type 1 and TrueType
     * fonts.
     */
    public static Encoding macRomanEncoding;

    /**
     * Windows Code Page 1252, often called the “Windows ANSI” encoding.
     * This is the standard Windows encoding for Latin text in Western writing
     * systems. Conforming readers shall have a predefined encoding named
     * WinAnsiEncoding that may be used with both Type 1 and TrueType
     * fonts.
     */
    public static Encoding winAnsiEncoding;

    /**
     * Encoding for text strings in a PDF document outside the document’s
     * content streams. This is one of two encodings (the other being Unicode)
     * that may be used to represent text strings; see 7.9.2.2, "Text String
     * Type". PDF does not have a predefined encoding named
     * PDFDocEncoding; it is not customary to use this encoding to show text
     * from fonts.
     */
    public static Encoding pdfDocEncoding;

    /**
     * An encoding for use with expert fonts—ones containing the expert
     * character set. Conforming readers shall have a predefined encoding
     * named MacExpertEncoding. Despite its name, it is not a platform-
     * specific encoding; however, only certain fonts have the appropriate
     * character set for use with this encoding. No such fonts are among the
     * standard 14 predefined fonts.
     */
    public static Encoding macExpertEncoding;

    public static Encoding symbolEncoding;
    public static Encoding zapfDingBats;

    static {
        initializeLatinEncodings();
        initializeOddBallEncodings();
    }

    private String name;
    private String[] encodingMap;

    private Encoding(String name, String[] encodingMap) {
        this.name = name;
        this.encodingMap = encodingMap;
    }

    public Encoding(Encoding base, String[] diff) {
        this.name = "diff";
        String[] cMap = base.encodingMap.clone();
        String name;
        for (int code = 0, max = diff.length; code < max; code++) {
            cMap[code] = diff[code];
        }
        encodingMap = cMap;
    }

    public Encoding(org.apache.fontbox.encoding.Encoding encoding) {
        Map<Integer, String> fontBoxEncoding = encoding.getCodeToNameMap();
        this.name = "embedded-font";
        // this is just a guess,  will need to see an example if a simple font really only has 256 max.
        String[] cMap = new String[256];
        String name;
        for (int code = 0, max = cMap.length; code < max; code++) {
            cMap[code] = fontBoxEncoding.get(code);
        }
        encodingMap = cMap;
    }

    public String getName() {
        return name;
    }

    public String getName(int code) {
        if (code >= 0 && code < encodingMap.length) {
            return encodingMap[code];
        }
        return ".notdef";
    }

    public char getChar(String name) {
        char ch = 0;
        // simple check to see if we have a /Euro or /euro
        if (name.equalsIgnoreCase("euro")) {
            name = "Euro";
        }
        boolean isEuro = name.equalsIgnoreCase("euro");
        for (int i = 0, max = encodingMap.length; i < max; i++) {
            if (name.equals(encodingMap[i])) {
                ch = (char) i;
                break;
            }
        }
        return ch;
    }

    /**
     * PDF encoding name to return an instance of.
     *
     * @param name one of the standard encoding names.
     * @return named encoding type, identity if something out of the ordinary happened
     */
    public static Encoding getInstance(String name) {

        if (STANDARD_ENCODING_NAME.equals(name)) {
            return standardEncoding;
        } else if (MAC_ROMAN_ENCODING_NAME.equals(name)) {
            return macRomanEncoding;
        } else if (WIN_ANSI_ENCODING_NAME.equals(name)) {
            return winAnsiEncoding;
        } else if (PDF_DOC_ENCODING_NAME.equals(name)) {
            return pdfDocEncoding;
        } else if (MAC_EXPERT_ENCODING_NAME.equals(name)) {
            return macExpertEncoding;
        } else if (ZAPF_DINGBATS_ENCODING_NAME.equals(name)) {
            return zapfDingBats;
        } else if (SYMBOL_ENCODING_NAME.equals(name)) {
            return symbolEncoding;
        } else {
            // todo return identity
            return null;
        }
    }

    private static void initializeLatinEncodings() {
        EncodingReader encodingReader = new EncodingReader();
        String[][] mappings = encodingReader.readEncoding("EncodingLatin.txt", 4);
        standardEncoding = new Encoding(STANDARD_ENCODING_NAME, mappings[0]);
        macRomanEncoding = new Encoding(MAC_ROMAN_ENCODING_NAME, mappings[1]);
        winAnsiEncoding = new Encoding(WIN_ANSI_ENCODING_NAME, mappings[2]);
        pdfDocEncoding = new Encoding(PDF_DOC_ENCODING_NAME, mappings[3]);
    }

    private static void initializeOddBallEncodings() {
        EncodingReader encodingReader = new EncodingReader();
        String[][] mappings = encodingReader.readEncoding("EncodingSymbol.txt", 1);
        symbolEncoding = new Encoding(SYMBOL_ENCODING_NAME, mappings[0]);

        mappings = encodingReader.readEncoding("EncodingZapf.txt", 1);
        zapfDingBats = new Encoding(ZAPF_DINGBATS_ENCODING_NAME, mappings[0]);

        mappings = encodingReader.readEncoding("EncodingExpert.txt", 1);
        macExpertEncoding = new Encoding(MAC_EXPERT_ENCODING_NAME, mappings[0]);
    }

    private static class EncodingReader {

        public String[][] readEncoding(String fileName, int width) {
            String[][] mappings = new String[width][256];
            try (InputStream inputStream = Encoding.class.getResourceAsStream(
                    "/org/icepdf/core/pobjects/fonts/encoding/" + fileName)) {
                BufferedReader encodingBuffer = new BufferedReader(new InputStreamReader(inputStream));
                String currentLine, name, nextCode;
                int code;
                StringTokenizer toker;
                while ((currentLine = encodingBuffer.readLine()) != null) {
                    if (!currentLine.startsWith("#")) {
                        toker = new StringTokenizer(currentLine, " ");
                        name = toker.nextToken();
                        for (int i = 0; i < width; i++) {
                            nextCode = toker.nextToken();
                            if (!nextCode.startsWith("-")) {
                                code = Integer.parseInt(nextCode, 8);
                                mappings[i][code] = name;
                            }
                        }
                    }
                }
                return mappings;
            } catch (IOException e) {
                logger.warning("Failed to read encoding " + fileName);
            }
            return null;
        }

    }
}
