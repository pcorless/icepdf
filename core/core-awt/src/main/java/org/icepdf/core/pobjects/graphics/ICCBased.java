/*
 * Copyright 2006-2019 ICEsoft Technologies Canada Corp.
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
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * put your documentation comment here
 */
public class ICCBased extends PColorSpace {

    private static final Logger logger =
            Logger.getLogger(ICCBased.class.getName());

    // guards profile parsing only (JDK-8033238); converting a colour takes no lock
    private static final Object lock = new Object();

    // 16384 slots: the colours a page's fills, strokes and shadings use; images remember their own (GH-617)
    private static final int CACHE_BITS = 14;

    public static final Name ICCBASED_KEY = new Name("ICCBased");
    public static final Name N_KEY = new Name("N");
    public static final Name RANGE_KEY = new Name("Range");

    private final int numcomp;
    private PColorSpace alternate;
    private final Stream stream;
    private ColorSpace colorSpace;
    // each component's minimum and 255 / (max - min), read once from the profile for the cache key
    private float[] keyMin;
    private float[] keyScale;

    // converted colours keyed by components quantised to 8 bits, for profiles of up to 4 components;
    // made with the colour space, so a profile that never converts a colour costs no table
    private ColorCache colorCache;

    // setting up an ICC colour look up is expensive, so if we get a failure
    // we just fallback to the alternative space to safe cpu time.
    private volatile boolean failed;

    public ICCBased(Library l, Stream h) {
        super(l, h.getEntries());
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
        synchronized (lock) {
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
                    ICC_ColorSpace iccColorSpace = new ICC_ColorSpace(profile);
                    // the first toRGB builds the transform and component scaling, and JDK 11 does so
                    // unsynchronized; build them here, under the lock and before inited publishes the space,
                    // so getColor can convert without one
                    int n = iccColorSpace.getNumComponents();
                    iccColorSpace.toRGB(new float[n]);
                    keyMin = new float[n];
                    keyScale = new float[n];
                    for (int i = 0; i < n; i++) {
                        keyMin[i] = iccColorSpace.getMinValue(i);
                        keyScale[i] = 255 / (iccColorSpace.getMaxValue(i) - keyMin[i]);
                    }
                    colorCache = new ColorCache(CACHE_BITS);
                    colorSpace = iccColorSpace;
                }
            } catch (Exception e) {
                logger.log(Level.FINE, "Error Processing ICCBased Colour Profile, falling back on alternative.");
            }
            inited = true;
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
     * Packs the components into a cache key, each quantised to 8 bits across its range; a missing component
     * counts as 0, as in {@link #calculateColor(float[], ColorSpace)}.  For the usual 0..1 range this is the
     * {@code (int) (f * 255)} the cache always keyed on.
     */
    private int generateKey(float[] f) {
        int key = 0;
        for (int i = 0, n = keyMin.length; i < n; i++) {
            float value = i < f.length ? f[i] : 0f;
            int q = (int) ((value - keyMin[i]) * keyScale[i]);
            key = key << 8 | Math.max(0, Math.min(255, q));
        }
        return key;
    }

    /**
     * The components a cache key stands for.  The cached colour is converted from these rather than from the
     * caller's values, so it depends on the key alone and threads sharing the cache always agree.
     */
    private float[] keyComponents(int key) {
        int n = keyMin.length;
        float[] f = new float[n];
        for (int i = 0; i < n; i++) {
            int q = key >>> (8 * (n - 1 - i)) & 0xff;
            f[i] = keyMin[i] + q / keyScale[i];
        }
        return f;
    }

    public Color getColor(float[] f, boolean fillAndStroke) {
        if (!inited) {
            init();
        }
        // init() wrote colorSpace before the volatile inited and already made its first conversion, so no lock
        // is needed from here on (GH-617: a static lock serialised every colour of every document)
        ColorSpace colorSpace = this.colorSpace;
        if (colorSpace != null && !failed) {
            try {
                if (colorSpace.getNumComponents() > 4) {
                    return new Color(calculateColor(f, colorSpace));
                }
                int key = generateKey(f);
                Color color = colorCache.get(key);
                if (color == null) {
                    color = new Color(calculateColor(keyComponents(key), colorSpace));
                    colorCache.put(key, color);
                }
                return color;
            } catch (Exception e) {
                logger.log(Level.FINE, "Error getting ICCBased colour", e);
                failed = true;
            }
        }
        return alternate.getColor(f);
    }

    private static int calculateColor(float[] f, ColorSpace colorSpace) {
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
            float curr = f[i];
            if (curr < colorSpace.getMinValue(i)) {
                curr = 0.0f;
            } else if (curr > colorSpace.getMaxValue(i)) {
                curr = 1.0f;
            }
            fvalue[i] = curr;
        }
        float[] frgbvalue = colorSpace.toRGB(fvalue);
        return (0xFF000000) |
                ((((int) (frgbvalue[0] * 255)) & 0xFF) << 16) |
                ((((int) (frgbvalue[1] * 255)) & 0xFF) << 8) |
                ((((int) (frgbvalue[2] * 255)) & 0xFF));
    }

    /**
     * The profile dictionary's Range, {@code [0 1]} for each component without one (Table 90).
     */
    @Override
    public float[] getDefaultDecode(int bitsPerComponent) {
        float[] decode = super.getDefaultDecode(bitsPerComponent);
        Object range = library.getObject(entries, RANGE_KEY);
        if (range instanceof java.util.List) {
            java.util.List<?> values = (java.util.List<?>) range;
            for (int i = 0; i < decode.length && i < values.size(); i++) {
                if (values.get(i) instanceof Number) {
                    decode[i] = ((Number) values.get(i)).floatValue();
                }
            }
        }
        return decode;
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
