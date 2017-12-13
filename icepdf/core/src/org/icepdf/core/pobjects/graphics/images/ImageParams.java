/*
 * Copyright 2006-2017 ICEsoft Technologies Canada Corp.
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
package org.icepdf.core.pobjects.graphics.images;

import org.icepdf.core.pobjects.Dictionary;
import org.icepdf.core.pobjects.Name;
import org.icepdf.core.pobjects.Reference;
import org.icepdf.core.pobjects.Resources;
import org.icepdf.core.pobjects.graphics.DeviceGray;
import org.icepdf.core.pobjects.graphics.GraphicsState;
import org.icepdf.core.pobjects.graphics.PColorSpace;
import org.icepdf.core.util.Defs;
import org.icepdf.core.util.Library;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static org.icepdf.core.pobjects.graphics.images.FaxDecoder.K_KEY;

public class ImageParams extends Dictionary {

    // paper size for rare corner case when ccittfax is missing a dimension.
    private static double pageRatio;

    static {
        //  PDF-458 corner case/one off for trying to guess the width or height
        // of an CCITTfax image that is basically the same use as the page, we
        // use the page dimensions to try and determine the page size.
        // This will fail miserably if the image isn't full page.
        // define alternate page size ration w/h, default Legal.
        pageRatio =
                Defs.sysPropertyDouble("org.icepdf.core.pageRatio",
                        8.26 / 11.68);
    }

    public static final Name WIDTH_KEY = new Name("Width");
    public static final Name W_KEY = new Name("W");
    public static final Name HEIGHT_KEY = new Name("Height");
    public static final Name H_KEY = new Name("H");
    public static final Name IMAGE_MASK_KEY = new Name("ImageMask");
    public static final Name IM_KEY = new Name("IM");
    public static final Name COLORSPACE_KEY = new Name("ColorSpace");
    public static final Name CS_KEY = new Name("CS");
    public static final Name DECODE_PARAM_KEY = new Name("DecodeParms");
    public static final Name FILTER_KEY = new Name("Filter");
    public static final Name F_KEY = new Name("F");
    public static final Name INDEXED_KEY = new Name("Indexed");
    public static final Name I_KEY = new Name("I");
    // in line image flags.
    public static final Name BPC_KEY = new Name("BPC");
    public static final Name D_KEY = new Name("D");
    public static final Name DP_KEY = new Name("DP");

    public static final Name BLACKIS1_KEY = new Name("BlackIs1");

    public static final Name DECODE_KEY = new Name("Decode");
    public static final Name BITS_PER_COMPONENT_KEY = new Name("BitsPerComponent");
    public static final Name SMASK_KEY = new Name("SMask");
    public static final Name MASK_KEY = new Name("Mask");

    private Resources resources;


    public ImageParams(Library library, HashMap entries, Resources resources) {
        super(library, entries);
        this.resources = resources;
    }

    public int getWidth() {
        int width = library.getInt(entries, WIDTH_KEY);
        if (width == 0) {
            int height = library.getInt(entries, HEIGHT_KEY);
            width = (int) (pageRatio * height);
        }
        return width;
    }

    public int getHeight() {
        int height = library.getInt(entries, HEIGHT_KEY);
        if (height == 0) {
            int width = library.getInt(entries, WIDTH_KEY);
            height = (int) (pageRatio * width);
        }
        return height;
    }

    public HashMap getDecodeParams() {
        return getDecodeParams(library, entries);
    }

    public static HashMap getDecodeParams(Library library, HashMap entries) {
        HashMap decodeParams = library.getDictionary(entries, DECODE_PARAM_KEY);
        if (decodeParams != null &&
                (decodeParams.containsKey(K_KEY) || decodeParams.size() > 0)) {
            return decodeParams;
        } else {
            // malformed pdf where k value is store in an indirect reference.
            Object tmp = library.getObject(entries, ImageParams.DECODE_PARAM_KEY);
            if (tmp instanceof ArrayList) {
                ArrayList potential = (ArrayList) tmp;
                for (Object obj : potential) {
                    if (obj instanceof Reference) {
                        Object found = library.getObject((Reference) obj);
                        if (found instanceof HashMap) {
                            return (HashMap) found;
                        }
                    }
                }
            }
        }
        return null;
    }

    public Name getSubType() {
        return library.getName(entries, SUBTYPE_KEY);
    }

    public boolean isImageMask() {
        return library.getBoolean(entries, IMAGE_MASK_KEY);
    }

    public PColorSpace getColourSpace() {
        Object o = entries.get(COLORSPACE_KEY);
        PColorSpace colourSpace = null;
        if (resources != null && o != null) {
            colourSpace = resources.getColorSpace(o);
        }
        // assume b&w image is no colour space
        if (colourSpace == null) {
            colourSpace = new DeviceGray(library, null);
        }
        return colourSpace;
    }

    public int getBitsPerComponent() {
        int bitsPerComponent = library.getInt(entries, BITS_PER_COMPONENT_KEY);
        if (isImageMask() && bitsPerComponent == 0) {
            bitsPerComponent = 1;
        }
        return bitsPerComponent;
    }

    public int getColorSpaceCompCount() {
        return getColourSpace().getNumComponents();
    }

    public float[] getDecode() {
        int bitsPerComponent = getBitsPerComponent();
        int colorSpaceCompCount = getColorSpaceCompCount();
        int maxValue = ((int) Math.pow(2, bitsPerComponent)) - 1;
        float[] decode = new float[2 * colorSpaceCompCount];
        List<Number> decodeVec = (List<Number>) library.getObject(entries, DECODE_KEY);
        if (decodeVec == null) {
            // add a decode param for each colour channel.
            for (int i = 0, j = 0; i < colorSpaceCompCount; i++) {
                decode[j++] = 0.0f;
                decode[j++] = 1.0f / maxValue;
            }
        } else {
            for (int i = 0, j = 0; i < colorSpaceCompCount; i++) {
                float Dmin = decodeVec.get(j).floatValue();
                float Dmax = decodeVec.get(j + 1).floatValue();
                decode[j++] = Dmin;
                decode[j++] = (Dmax - Dmin) / maxValue;
            }
        }
        return decode;
    }

    public ColorKeyMask getColorKeyMask() {
        Object maskObj = library.getObject(entries, MASK_KEY);
        if (maskObj instanceof List) {
            return new ColorKeyMask(library, entries, this);
        }
        return null;
    }

    public boolean hasMask() {
        return library.getObject(entries, MASK_KEY) != null;
    }

    public ImageDecoder getMask(GraphicsState graphicsState) {
        Object maskObj = library.getObject(entries, MASK_KEY);
        if (maskObj instanceof ImageStream) {
            return ImageDecoderFactory.createDecoder((ImageStream) maskObj, graphicsState);
        }
        return null;
    }

    public boolean hasSMask() {
        return library.getObject(entries, SMASK_KEY) != null;
    }

    public ImageDecoder getSMask(GraphicsState graphicsState) {
        Object maskObj = library.getObject(entries, SMASK_KEY);
        if (maskObj instanceof ImageStream) {
            return ImageDecoderFactory.createDecoder((ImageStream) maskObj, graphicsState);
        }
        return null;
    }

    public HashMap getDictionary(Name key) {
        return library.getDictionary(entries, key);
    }

    public Object getObject(HashMap entries, Name key) {
        return library.getObject(entries, key);
    }

    public float getFloat(HashMap dictionaryEntries, Name key) {
        return library.getFloat(dictionaryEntries, key);
    }

    public int getInt(HashMap dictionaryEntries, Name key) {
        return library.getInt(dictionaryEntries, key);
    }

    public boolean getBlackIs1(HashMap decodeParmsDictionary) {
        Object blackIs1Obj = library.getObject(decodeParmsDictionary, BLACKIS1_KEY);
        if (blackIs1Obj != null) {
            if (blackIs1Obj instanceof Boolean) {
                return (Boolean) blackIs1Obj;
            } else if (blackIs1Obj instanceof String) {
                String blackIs1String = (String) blackIs1Obj;
                if (blackIs1String.equalsIgnoreCase("true"))
                    return true;
                else if (blackIs1String.equalsIgnoreCase("t"))
                    return true;
                else if (blackIs1String.equals("1"))
                    return true;
                else if (blackIs1String.equalsIgnoreCase("false"))
                    return false;
                else if (blackIs1String.equalsIgnoreCase("f"))
                    return false;
                else if (blackIs1String.equals("0"))
                    return false;
            }
        }
        return false;
    }

    public int getDataLength() {
        return getWidth() * getHeight()
                * getColorSpaceCompCount()
                * getBitsPerComponent() / 8;
    }

}
