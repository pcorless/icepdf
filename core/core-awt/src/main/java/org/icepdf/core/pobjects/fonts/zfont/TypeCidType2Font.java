package org.icepdf.core.pobjects.fonts.zfont;

import org.icepdf.core.util.Library;

import java.util.HashMap;

public class TypeCidType2Font extends CompositeFont {
    public TypeCidType2Font(Library library, HashMap entries) {
        super(library, entries);
    }

    @Override
    public void init() {
        super.init();
    }

    protected void parseCidToGidMap() {
        Object gidMap = library.getObject(entries, CID_TO_GID_MAP_KEY);
        System.out.println();
//        if (subtype.equals("CIDFontType2") &&
//                ((ordering != null && ordering.startsWith("Identity")) || gidMap != null || !isFontSubstitution)) {
//            CMap subfontToUnicodeCMap = toUnicodeCMap != null ? toUnicodeCMap : CMap.IDENTITY;
//            if (gidMap == null || gidMap instanceof Name) {
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
//                    font = ((NFontTrueType) font).deriveFont(CMap.IDENTITY, subfontToUnicodeCMap);
//                }
//            } else if (gidMap instanceof Stream) {
//                try {
//                    ByteArrayInputStream cidStream =
//                            ((Stream) gidMap).getDecodedByteArrayInputStream();
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
//                    // apply the cidToGid mapping, but try figure out how many bytes are going to be in
//                    // in each character, we use the toUnicode mapping if present.
//                    CMap cidGidMap = new CMap(cidToGid);
//                    if (toUnicodeCMap != null) {
//                        cidGidMap.applyBytes(toUnicodeCMap);
//                    }
//                    font = ((NFontTrueType) font).deriveFont(
//                            cidGidMap.reverse(), toUnicodeCMap != null ? toUnicodeCMap : CMap.IDENTITY);
//                } catch (IOException e) {
//                    logger.log(Level.FINE, "Error reading CIDToGIDMap Stream.", e);
//                }
//            }
//        }
    }
}