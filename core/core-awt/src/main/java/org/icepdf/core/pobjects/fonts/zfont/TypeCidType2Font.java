package org.icepdf.core.pobjects.fonts.zfont;

import org.icepdf.core.pobjects.Name;
import org.icepdf.core.pobjects.Stream;
import org.icepdf.core.pobjects.fonts.ofont.CMap;
import org.icepdf.core.pobjects.fonts.zfont.fontFiles.ZFontType2;
import org.icepdf.core.util.Library;

import java.util.HashMap;
import java.util.logging.Logger;

public class TypeCidType2Font extends CompositeFont {

    private static final Logger logger =
            Logger.getLogger(TypeCidType2Font.class.toString());

    public TypeCidType2Font(Library library, HashMap entries) {
        super(library, entries);
    }

    @Override
    public void init() {
        super.init();
        parseCidToGidMap();
        inited = true;
    }

    protected void parseWidths() {
        super.parseWidths();
        if (widths != null || defaultWidth > -1) {
            font = ((ZFontType2) font).deriveFont(defaultWidth, widths);
        } else {
            font = ((ZFontType2) font).deriveFont(1000, null);
        }
    }

    protected void parseCidToGidMap() {
        Object gidMap = library.getObject(entries, CID_TO_GID_MAP_KEY);

        // ordering != null && ordering.startsWith("Identity")) || ((gidMap != null || !isFontSubstitution)
        if (true) {
//            CMap subfontToUnicodeCMap = toUnicodeCMap != null ? toUnicodeCMap : CMap.IDENTITY;
            if (gidMap == null) {
//                throw new Exception("null CID_TO_GID_MAP_KEY " + gidMap);
//                font = ((ZFontTrueType) font).deriveFont(CMap.IDENTITY, null);// subfontToUnicodeCMap);
            }
            if (gidMap instanceof Name) {
//                throw new Exception("gidMap name " + gidMap);
//                String mappingName = null;
//                if (gidMap != null) {
//                    mappingName = gidMap.toString();
//                }
//                if (toUnicodeCMap instanceof CMapIdentityH) {
//                    mappingName = toUnicodeCMap.toString();
//                }
//                // mapping name will be null only in a few corner cases, but
//                // identity will be applied otherwise.
//                if (mappingName == null || mappingName.equals("Identity")) {
//                    font = ((ZFontTrueType) font).deriveFont(CMap.IDENTITY, subfontToUnicodeCMap);
//                }
            } else if (gidMap instanceof Stream) {
//                try {
                CMap cidGidMap = new CMap((Stream) gidMap);
                cidGidMap.init();
//                    System.out.println();
//                font = ((ZFontTrueType) font).deriveFont(cidGidMap, null);//toUnicodeCMap != null ? toUnicodeCMap : CMap.IDENTITY);
//                    int test = cidGidMap.toSelector(39);

//                    ByteArrayInputStream cidStream = ((Stream) gidMap).getDecodedByteArrayInputStream();
//                    int character = 0;
//                    int i = 0;
//                    int length = cidStream.available() / 2;
//                    char[] cidToGid = new char[length];
//                    // parse the cidToGid stream out, arranging the high bit,
//                    // each character position that has a value > 0 is a valid
//                    // entry in the CFF.
//                    while (character != -1 && i < length) {
//                        character = cidStream.read();
//                        character = (char) ((character << 8) | cidStream.read());
//                        cidToGid[i] = (char) character;
//                        i++;
//                    }
//                    cidStream.close();
//                    System.out.println(cidToGid);
                // apply the cidToGid mapping, but try figure out how many bytes are going to be in
                // in each character, we use the toUnicode mapping if present.
//                    CMap cidGidMap = CMap.getInstance(library, CID_TO_GID_MAP_KEY);
//                    if (toUnicodeCMap != null) {
//                        cidGidMap.applyBytes(toUnicodeCMap);
//                    }
//                    font = ((ZFontTrueType) font).deriveFont(
//                            cidGidMap.reverse(), toUnicodeCMap != null ? toUnicodeCMap : CMap.IDENTITY);
//                } catch (IOException e) {
//                    logger.log(Level.FINE, "Error reading CIDToGIDMap Stream.", e);
//                }
            }
        }
    }
}