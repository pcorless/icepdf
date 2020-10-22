package org.icepdf.core.pobjects.fonts.zfont;

import org.icepdf.core.pobjects.fonts.AFM;
import org.icepdf.core.pobjects.fonts.Font;
import org.icepdf.core.pobjects.fonts.FontDescriptor;
import org.icepdf.core.util.Library;

import java.util.HashMap;

import static org.icepdf.core.pobjects.fonts.ofont.Font.FONT_DESCRIPTOR_KEY;

public class SimpleFont extends Font {

    public SimpleFont(Library library, HashMap entries) {
        super(library, entries);
    }

    @Override
    public void init() {
        // flag for initiated fonts
        if (inited) {
            return;
        }

        // todo move out ot font
        boolean isEmbedded = false;

        // Assign the font descriptor
        Object of = library.getObject(entries, FONT_DESCRIPTOR_KEY);
        if (of instanceof FontDescriptor) {
            fontDescriptor = (FontDescriptor) of;
        }
        // some font descriptors are missing the type entry so we
        // try build the fontDescriptor from retrieved hashtable.
        else if (of instanceof HashMap) {
            fontDescriptor = new FontDescriptor(library, (HashMap) of);
        }
        if (fontDescriptor != null) {
            fontDescriptor.init();
            // get most types of embedded fonts from here
            if (fontDescriptor.getEmbeddedFont() != null) {
                font = fontDescriptor.getEmbeddedFont();
                isEmbedded = true;
                // make sure we mark this font as having a font file
                isFontSubstitution = false;
            }
        }

        // If there is no FontDescriptor then we most likely have a core afm
        // font and we should get the matrix so that we can derive the correct
        // font.
        if (fontDescriptor == null && basefont != null) {
            // see if the baseFont name matches one of the AFM names
            Object afm = AFM.AFMs.get(basefont.toLowerCase());
            //System.out.println("Looking for afm " + basefont);
            if (afm != null && afm instanceof AFM) {
                AFM fontMetrix = (AFM) afm;
                // finally create a fontDescriptor based on AFM data.
                //System.out.println("Initiating core 14 AFM font Descriptor");
                fontDescriptor = FontDescriptor.createDescriptor(library, fontMetrix);
                fontDescriptor.init();
            }
        }
    }
}
