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

import org.icepdf.core.pobjects.Dictionary;
import org.icepdf.core.pobjects.Name;
import org.icepdf.core.pobjects.Reference;
import org.icepdf.core.util.Library;

import java.awt.*;
import java.util.Hashtable;
import java.util.Vector;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * put your documentation comment here
 */
public abstract class PColorSpace extends Dictionary {

    private static final Logger logger =
            Logger.getLogger(PColorSpace.class.toString());

    /**
     * @return
     */
    public abstract int getNumComponents();


    public String getDescription() {
        String name = getClass().getName();
        int index = name.lastIndexOf('.');
        return name.substring(index + 1);
    }
    
    /**
     * @param l
     * @param h
     */
    PColorSpace(Library l, Hashtable h) {
        super(l, h);
    }

    /**
     * @param library
     * @param o
     * @return
     */
    public static PColorSpace getColorSpace(Library library, Object o) {
        if (o != null) {
            if (o instanceof Reference) {
                o = library.getObject((Reference) o);
            }
            if (o instanceof Name) {
                if (o.equals("DeviceGray") || o.equals("G")) {
                    return new DeviceGray(library, null);
                } else if (o.equals("DeviceRGB") || o.equals("RGB")) {
                    return new DeviceRGB(library, null);
                } else if (o.equals("DeviceCMYK") || o.equals("CMYK")) {
                    return new DeviceCMYK(library, null);
                } else if (o.equals("Pattern")) {
                    return new PatternColor(library, null);
                }
            } else if (o instanceof Vector) {
                Vector v = (Vector) o;
                if (v.elementAt(0).equals("Indexed")
                        || v.elementAt(0).equals("I")) {
                    return new Indexed(library, null, v);
                } else if (v.elementAt(0).equals("CalRGB")) {
                    return new CalRGB(library, (Hashtable) v.elementAt(1));
                } else if (v.elementAt(0).equals("Lab")) {
                    return new Lab(library, (Hashtable) v.elementAt(1));
                } else if (v.elementAt(0).equals("Separation")) {
                    return new Separation(
                            library,
                            null,
                            v.elementAt(1),
                            v.elementAt(2),
                            v.elementAt(3));
                } else if (v.elementAt(0).equals("DeviceN")) {
                    return new DeviceN(
                            library,
                            null,
                            v.elementAt(1),
                            v.elementAt(2),
                            v.elementAt(3),
                            v.size() > 4 ? v.elementAt(4) : null);
                } else if (v.elementAt(0).equals("ICCBased")) {
                    /*Stream st = (Stream)library.getObject((Reference)v.elementAt(1));
                     return  PColorSpace.getColorSpace(library, library.getObject(st.getEntries(),
                     "Alternate"));*/
                    return library.getICCBased((Reference) v.elementAt(1));
                } else if (v.elementAt(0).equals("DeviceRGB")) {
                    return new DeviceRGB(library, null);
                } else if (v.elementAt(0).equals("DeviceCMYK")) {
                    return new DeviceCMYK(library, null);
                } else if (v.elementAt(0).equals("DeviceGray")) {
                    return new DeviceRGB(library, null);
                } else if (v.elementAt(0).equals("Pattern")) {
                    PatternColor patternColour = new PatternColor(library, null);
                    if (v.size() > 1) {
                        patternColour.setPColorSpace(getColorSpace(library, v.elementAt(1)));
                    }
                    return patternColour;
                }
            } else if (o instanceof Hashtable) {
                return new PatternColor(library, (Hashtable) o);
            }
            if (logger.isLoggable(Level.FINE)) {
                logger.fine("Unsupported Colorspace: " + o);
            }
        }
        return new DeviceGray(library, null);
    }

    /**
     * Gets the color space for the given n value. If n == 3 then the new color
     * space with be RGB,  if n == 4 then CMYK and finally if n == 1 then
     * Gray.
     *
     * @param library hash of all library objects
     * @param n       number of colours in colour space
     * @return a new PColorSpace given the value of n
     */
    public static PColorSpace getColorSpace(Library library, float n) {
        if (n == 3) {
            return new DeviceRGB(library, null);
        } else if (n == 4) {
            return new DeviceCMYK(library, null);
        } else {
            return new DeviceGray(library, null);
        }
    }

    /**
     * @param f
     * @return
     */
    public abstract Color getColor(float[] f);

    public void normaliseComponentsToFloats(int[] in, float[] out, float maxval) {
        int count = getNumComponents();
        for (int i = 0; i < count; i++)
            out[i] = (((float) in[i]) / maxval);
    }

    /**
     * @param f
     * @return
     */
    public static float[] reverse(float f[]) {
        float n[] = new float[f.length];
        //System.out.print("R ");
        for (int i = 0; i < f.length; i++) {
            n[i] = f[f.length - i - 1];
            //System.out.print( n[i] + ",");
        }
        //System.out.println();
        return n;
    }

    public static void reverseInPlace(float[] f) {
        int num = f.length / 2;
        for (int i = 0; i < num; i++) {
            float tmp = f[i];
            f[i] = f[f.length - 1 - i];
            f[f.length - 1 - i] = tmp;
        }
    }

    public static void reverseInPlace(int[] f) {
        int num = f.length / 2;
        for (int i = 0; i < num; i++) {
            int tmp = f[i];
            f[i] = f[f.length - 1 - i];
            f[f.length - 1 - i] = tmp;
        }
    }
}
