/*
 * Version: MPL 1.1/GPL 2.0/LGPL 2.1
 *
 * "The contents of this file are subject to the Mozilla Public License
 * Version 1.1 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
 * License for the specific language governing rights and limitations under
 * the License.
 *
 * The Original Code is ICEpdf 3.0 open source software code, released
 * May 1st, 2009. The Initial Developer of the Original Code is ICEsoft
 * Technologies Canada, Corp. Portions created by ICEsoft are Copyright (C)
 * 2004-2011 ICEsoft Technologies Canada, Corp. All Rights Reserved.
 *
 * Contributor(s): _____________________.
 *
 * Alternatively, the contents of this file may be used under the terms of
 * the GNU Lesser General Public License Version 2.1 or later (the "LGPL"
 * License), in which case the provisions of the LGPL License are
 * applicable instead of those above. If you wish to allow use of your
 * version of this file only under the terms of the LGPL License and not to
 * allow others to use your version of this file under the MPL, indicate
 * your decision by deleting the provisions above and replace them with
 * the notice and other provisions required by the LGPL License. If you do
 * not delete the provisions above, a recipient may use your version of
 * this file under either the MPL or the LGPL License."
 *
 */
package org.icepdf.core.pobjects.graphics;

import org.icepdf.core.pobjects.Reference;
import org.icepdf.core.pobjects.Stream;
import org.icepdf.core.pobjects.StringObject;
import org.icepdf.core.util.Library;

import java.awt.*;
import java.util.Hashtable;
import java.util.Vector;

/**
 * The class represents an indexed colour space.
 */
public class Indexed extends PColorSpace {
    PColorSpace colorSpace;
    int hival;
    byte[] colors = {
            -1, -1, -1, 0, 0, 0
    };
    private boolean inited = false;
    private Color[] cols;

    /**
     * Contructs a new instance of the indexed colour space. Pares the indexed
     * colour pattern: [/Indexed base hival lookup] where base is an array or
     * name that identifies the base colour space. Hival parameter is an integer
     * that specifies the maximum valid index value. Lookup is the colour table
     * which must be of length m x (hival + 1) where m is the number of colour
     * components in base.
     *
     * @param library    document library.
     * @param entries    dictionary entries.
     * @param dictionary indexed colour dictionary.
     */
    Indexed(Library library, Hashtable entries, Vector dictionary) {
        super(library, entries);
        // get the base colour space
        colorSpace = getColorSpace(library, dictionary.elementAt(1));
        // get the hival
        hival = (((Number) (dictionary.elementAt(2))).intValue());
        // check for an instance of a lookup table.
        if (dictionary.elementAt(3) instanceof StringObject) {
            // peel and decrypt the literal string
            StringObject tmpText = (StringObject) dictionary.elementAt(3);
            String tmp = tmpText.getDecryptedLiteralString(library.securityManager);
            // build the colour lookup table.
            byte[] textBytes = new byte[colorSpace.getNumComponents() * (hival + 1)]; // m * (hival + 1)
            for (int i = 0; i < textBytes.length; i++) {
                textBytes[i] = (byte) tmp.charAt(i);
            }
            colors = textBytes;
        } else if (dictionary.elementAt(3) instanceof Reference) {
            Stream lookup = (Stream) (library.getObject((Reference) (dictionary.elementAt(3))));
            colors = lookup.getBytes();
        }
    }

    /**
     * Return the number of components in indexed colour.
     *
     * @return always returns 1.
     */
    public int getNumComponents() {
        return 1;
    }

    public String getDescription() {
        String desc = super.getDescription();
        if (colorSpace != null)
            desc = desc + ":" + colorSpace.getDescription();
        return desc;
    }

    /**
     * Initiate the Indexed Colour Object
     */
    public void init() {
        if (inited) {
            return;
        }
        inited = true;

        int numCSComps = colorSpace.getNumComponents();
        int b1[] = new int[numCSComps];
        float f1[] = new float[numCSComps];
        cols = new Color[hival + 1];
        for (int j = 0; j <= hival; j++) {
            for (int i = 0; i < numCSComps; i++) {
                b1[numCSComps - 1 - i] = 0xFF & ((int) colors[j * numCSComps + i]);
            }
            colorSpace.normaliseComponentsToFloats(b1, f1, 255.0f);
            cols[j] = colorSpace.getColor(f1);
        }
    }

    /**
     * Gets the colour for the array of float values
     *
     * @param f
     * @return
     */
    public Color getColor(float[] f) {
        init();
        int index = (int) (f[0] * (cols.length - 1));
        return cols[index];
    }

    public Color[] accessColorTable() {
        return cols;
    }
}
