package org.icepdf.core.util.updater;

import org.icepdf.core.pobjects.Name;
import org.icepdf.core.pobjects.Reference;
import org.icepdf.core.pobjects.Resources;
import org.icepdf.core.pobjects.fonts.zfont.SimpleFont;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * EmbeddedFontCache is a simple cache for storing embedded font references
 * found in an existing PDF's resources specifically written by this library.
 * This cache is used when updating content streams to ensure we reuse existing
 * embedded fonts instead of embedding new copies.
 * <p>
 * As annotation appearances are parsed we search for any previously used ice fonts.
 * This can only be done at this time as it the only time we have access to the resources object
 * reference in a convenient way without doing expensive searches.
 *
 * @since 7.4.0
 */
public class EmbeddedFontCache extends HashMap<String, ArrayList<Reference>> {

    public static final String ICEPDF_EMBEDDED_FONT_SUFFIX = "+iceEmbeddedFont";

    public static final String ICEPDF_EMBEDDED_FONT_RESOURCE = "ice";


    public EmbeddedFontCache() {
        super(14);
    }

    public void clearCache() {
        clear();
    }

    public Reference getFontReference(String key) {
        ArrayList<Reference> references = get(key);
        if (references != null && !references.isEmpty()) {
            return references.get(0);
        }
        return null;
    }

    public void putFontReference(String key, Reference reference) {
        ArrayList<Reference> references = get(key);
        if (references == null) {
            references = new ArrayList<>();
        }
        references.add(reference);
        put(key, references);
    }

    public void removeReference(String key, Reference reference) {
        ArrayList<Reference> references = get(key);
        if (references != null && !references.isEmpty()) {
            references.remove(reference);
        }
    }

    public boolean hasReference(String key, Reference reference) {
        ArrayList<Reference> references = get(key);
        return references != null && !references.isEmpty();
    }

    /**
     * Checks the provided resources for any embedded fonts added by icepdf
     * and adds them to the cache for later reuse.
     *
     * @param resources
     */
    public void checkAndPutAnyIceFonts(Resources resources) {
        if (resources != null && resources.getFonts() != null) {
            String baseFontName;
            SimpleFont font;
            for (Name fontName : resources.getFonts().keySet()) {
                baseFontName = fontName.getName();
                if (baseFontName.startsWith(ICEPDF_EMBEDDED_FONT_RESOURCE)) {
                    font = (SimpleFont) resources.getFont(fontName);
                    if (font != null && !font.isFontSubstitution()) {
                        String baseFont = font.getBaseFont();
                        if (baseFont != null && baseFont.endsWith(ICEPDF_EMBEDDED_FONT_SUFFIX)) {
                            int fontSuffixLength = ICEPDF_EMBEDDED_FONT_SUFFIX.length();
                            baseFont = baseFontName.substring(0, baseFontName.length() - fontSuffixLength);
                            putFontReference(
                                    baseFont,
                                    font.getPObjectReference());
                        }
                    }
                }
            }
        }
    }
}
