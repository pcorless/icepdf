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

import org.icepdf.core.io.SeekableInput;
import org.icepdf.core.pobjects.Stream;
import org.icepdf.core.util.Library;
import org.icepdf.core.util.Utils;

import java.awt.*;
import java.awt.color.ColorSpace;
import java.awt.color.ICC_ColorSpace;
import java.awt.color.ICC_Profile;
import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * put your documentation comment here
 */
public class ICCBased extends PColorSpace {

    private static final Logger logger =
            Logger.getLogger(ICCBased.class.toString());

    int numcomp;
    PColorSpace alternate;
    Stream stream;
    ColorSpace colorSpace;

    private float[] lastInput;
    private Color lastOutput;

    /**
     * @param l
     * @param h
     */
    public ICCBased(Library l, Stream h) {
        super(l, h.getEntries());
        numcomp = h.getInt("N");
        switch (numcomp) {
            case 1:
                alternate = new DeviceGray(l, null);
                break;
            case 3:
                alternate = new DeviceRGB(l, null);
                break;
            case 4:
                alternate = new DeviceCMYK(l, null);
                break;
        }
        stream = h;
    }

    /**
     *
     */
    public synchronized void init() {
        if (inited) {
            return;
        }
        inited = true;
        InputStream in = null;
        try {
            stream.init();
            in = stream.getInputStreamForDecodedStreamBytes();
            if (logger.isLoggable(Level.FINEST)) {
                String content;
                if (in instanceof SeekableInput) {
                    content = Utils.getContentFromSeekableInput((SeekableInput) in, false);
                } else {
                    InputStream[] inArray = new InputStream[]{in};
                    content = Utils.getContentAndReplaceInputStream(inArray, false);
                    in = inArray[0];
                }
                logger.finest("Content = " + content);
            }
            if (in != null) {
                ICC_Profile profile = ICC_Profile.getInstance(in);
                colorSpace = new ICC_ColorSpace(profile);
            }
        } catch (Exception e) {
            logger.log(Level.FINE, "Error Processing ICCBased Colour Profile", e);
        }
        finally {
            try {
                if (in != null) in.close();
            }
            catch (IOException e) {
            }
        }
    }

    /**
     * Get the alternative colour specified by the N dictionary entry.  DeviceGray,
     * DeviceRGB, or DeviceCMYK, depending on whether the value of N is  1, 3
     * or 4, respectively.
     *
     * @return PDF colour space represented by the N (number of components)key.
     */
    public PColorSpace getAlternate() {
        return alternate;
    }

    /**
     * @param f
     * @return
     */
    public Color getColor(float[] f) {
        init();
        if (colorSpace != null) {
            try {
                synchronized (this) {
                    // We cache the previous inputs and output, since images
                    //   tend to have long runs of the same color
                    if (lastOutput != null && lastInput != null &&
                            f != null && lastInput.length == f.length) {
                        boolean matches = true;
                        int num = lastInput.length;
                        for (int i = num - 1; i >= 0; i--) {
                            if (f[i] != lastInput[i]) {
                                matches = false;
                                break;
                            }
                        }
                        if (matches)
                            return lastOutput;
                    }

                    int n = colorSpace.getNumComponents();
                    // Get the reverse of f, and only take n values
                    // Might as well limit the bounds while we're at it
                    float[] fvalue = new float[n];
                    int toCopy = n;
                    int fLength = f.length;
                    if (fLength < toCopy) {
                        toCopy = fLength;
                    }
                    for (int i = 0; i < toCopy; i++) {
                        float curr = f[fLength - 1 - i];
                        if (curr < 0.0f)
                            curr = 0.0f;
                        else if (curr > 1.0f)
                            curr = 1.0f;
                        fvalue[i] = curr;
                    }
                    float[] frgbvalue = colorSpace.toRGB(fvalue);
                    int value = (0xFF000000) |
                            ((((int) (frgbvalue[0] * 255)) & 0xFF) << 16) |
                            ((((int) (frgbvalue[1] * 255)) & 0xFF) << 8) |
                            ((((int) (frgbvalue[2] * 255)) & 0xFF));
                    Color c = new Color(value);

                    // Update the cache
                    if (lastInput == null || lastInput.length != fLength)
                        lastInput = new float[fLength];
                    for (int i = fLength - 1; i >= 0; i--)
                        lastInput[i] = f[i];
                    lastOutput = c;

                    return c;
                }
                /*
                Color c = new Color( colorSpace,reverse(f), 1 );
                return new Color(
                    ColorSpace_CS_sRGB,
                    c.getRGBComponents(null),
                    1);
                */
            } catch (Exception e) {
                logger.log(Level.FINE, "Error getting ICCBased colour", e);
            }
        }
        return alternate.getColor(f);
    }

    public ColorSpace getColorSpace() {
        return colorSpace;
    }

    /**
     * Gets the number of components specified by the N entry.
     *
     * @return number of colour components in color space
     */
    public int getNumComponents() {
        return numcomp;
    }
}
