/*
 * Copyright 2006-2018 ICEsoft Technologies Canada Corp.
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
import org.icepdf.core.pobjects.graphics.Indexed;
import org.icepdf.core.pobjects.graphics.PColorSpace;
import org.icepdf.core.util.Library;

import java.awt.*;
import java.util.HashMap;
import java.util.List;

/**
 * An image XObject defining an image mask to be applied to this image ("Explicit Masking"), or an array specifying a
 * range of colours to be applied to it as a colour key mask ("Colour Key Masking").
 */
public class ColorKeyMask extends Dictionary {

    private int maskMinIndex, maskMaxIndex;
    private int[] maskMinRGB, maskMaxRGB;


    public ColorKeyMask(Library library, HashMap entries, ImageParams imageParams) {
        super(library, entries);
        Object maskObj = library.getObject(entries, ImageParams.MASK_KEY);
        if (maskObj instanceof List) {
            PColorSpace colourSpace = imageParams.getColourSpace();
            int colorSpaceCompCount = colourSpace.getNumComponents();
            int bitsPerComponent = imageParams.getBitsPerComponent();

            List maskVector = (List) maskObj;
            int[] maskMinOrigCompsInt = new int[colorSpaceCompCount];
            int[] maskMaxOrigCompsInt = new int[colorSpaceCompCount];
            for (int i = 0; i < colorSpaceCompCount; i++) {
                if ((i * 2) < maskVector.size())
                    maskMinOrigCompsInt[i] = ((Number) maskVector.get(i * 2)).intValue();
                if ((i * 2 + 1) < maskVector.size())
                    maskMaxOrigCompsInt[i] = ((Number) maskVector.get(i * 2 + 1)).intValue();
            }
            if (colourSpace instanceof Indexed) {
                Indexed icolourSpace = (Indexed) colourSpace;
                Color[] colors = icolourSpace.accessColorTable();
                if (colors != null &&
                        maskMinOrigCompsInt.length >= 1 &&
                        maskMaxOrigCompsInt.length >= 1) {
                    maskMinIndex = maskMinOrigCompsInt[0];
                    maskMaxIndex = maskMaxOrigCompsInt[0];
                    if (maskMinIndex >= 0 && maskMinIndex < colors.length &&
                            maskMaxIndex >= 0 && maskMaxIndex < colors.length) {
                        Color minColor = colors[maskMinOrigCompsInt[0]];
                        Color maxColor = colors[maskMaxOrigCompsInt[0]];
                        maskMinRGB = new int[]{minColor.getRed(), minColor.getGreen(), minColor.getBlue()};
                        maskMaxRGB = new int[]{maxColor.getRed(), maxColor.getGreen(), maxColor.getBlue()};
                    }
                }
            } else {
                PColorSpace.reverseInPlace(maskMinOrigCompsInt);
                PColorSpace.reverseInPlace(maskMaxOrigCompsInt);
                float[] maskMinOrigComps = new float[colorSpaceCompCount];
                float[] maskMaxOrigComps = new float[colorSpaceCompCount];
                colourSpace.normaliseComponentsToFloats(maskMinOrigCompsInt, maskMinOrigComps, (1 << bitsPerComponent) - 1);
                colourSpace.normaliseComponentsToFloats(maskMaxOrigCompsInt, maskMaxOrigComps, (1 << bitsPerComponent) - 1);

                Color minColor = colourSpace.getColor(maskMinOrigComps);
                Color maxColor = colourSpace.getColor(maskMaxOrigComps);
                PColorSpace.reverseInPlace(maskMinOrigComps);
                PColorSpace.reverseInPlace(maskMaxOrigComps);
                maskMinRGB = new int[]{minColor.getRed(), minColor.getGreen(), minColor.getBlue()};
                maskMaxRGB = new int[]{maxColor.getRed(), maxColor.getGreen(), maxColor.getBlue()};
            }
        }
    }

    public int getMaskMinIndex() {
        return maskMinIndex;
    }

    public int getMaskMaxIndex() {
        return maskMaxIndex;
    }

    public int[] getMaskMinRGB() {
        return maskMinRGB;
    }

    public int[] getMaskMaxRGB() {
        return maskMaxRGB;
    }
}
