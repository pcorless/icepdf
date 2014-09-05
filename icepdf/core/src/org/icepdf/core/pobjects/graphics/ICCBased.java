/*
 * Copyright 2006-2014 ICEsoft Technologies Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an "AS
 * IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */
package org.icepdf.core.pobjects.graphics;

import org.icepdf.core.pobjects.Name;
import org.icepdf.core.pobjects.Stream;
import org.icepdf.core.util.Library;
import org.icepdf.core.util.Utils;

import java.awt.*;
import java.awt.color.ColorSpace;
import java.awt.color.ICC_ColorSpace;
import java.awt.color.ICC_Profile;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * put your documentation comment here
 */
public class ICCBased extends PColorSpace {

    private static final Logger logger =
            Logger.getLogger(ICCBased.class.toString());

    public static final Name ICCBASED_KEY = new Name("ICCBased");
    public static final Name N_KEY = new Name("N");

    int numcomp;
    PColorSpace alternate;
    Stream stream;
    ColorSpace colorSpace;

    // basic cache to speed up the lookup.
    private static ConcurrentHashMap<String, Color> iccColorCache;

    // setting up an ICC colour look up is expensive, so if we get a failure
    // we just fallback to the alternative space to safe cpu time.
    private boolean failed;

    public ICCBased(Library l, Stream h) {
        super(l, h.getEntries());
        iccColorCache = new ConcurrentHashMap<String, Color>();
        numcomp = h.getInt(N_KEY);
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

        byte[] in;
        try {
            stream.init();
            in = stream.getDecodedStreamBytes(0);
            if (logger.isLoggable(Level.FINEST)) {
                String content = Utils.convertByteArrayToByteString(in);
                logger.finest("Content = " + content);
            }
            if (in != null) {
                ICC_Profile profile = ICC_Profile.getInstance(in);
                colorSpace = new ICC_ColorSpace(profile);
            }
        } catch (Exception e) {
            logger.log(Level.FINE, "Error Processing ICCBased Colour Profile", e);
        }
        inited = true;
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

    public Color getColor(float[] f, boolean fillAndStroke) {
        init();
        if (colorSpace != null && !failed) {
            try {
                // cache the colour
                String key = String.valueOf(f[0]) + f[1] + f[2];
                if (f.length == 4) key += f[3];

                if (iccColorCache.containsKey(key)) {
                    return iccColorCache.get(key);
                } else {
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
                    iccColorCache.put(key, c);
                    return c;
                }
            } catch (Exception e) {
                logger.log(Level.FINE, "Error getting ICCBased colour", e);
                failed = true;
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
