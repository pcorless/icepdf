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

import org.icepdf.core.pobjects.DictionaryEntries;
import org.icepdf.core.pobjects.Name;
import org.icepdf.core.pobjects.functions.Function;
import org.icepdf.core.util.Library;

import java.awt.*;

/**
 * <p>Separation Color Space background:</p>
 * <ul>
 * <li>Color output devices produce full color by combining primary or process
 * colorants in varying amounts. On an additive color device such as a display,
 * the primary colorants consist of red, green, and blue phosphors; on a
 * subtractive device such as a printer, they typically consist of cyan, magenta,
 * yellow, and sometimes black inks. In addition, some devices can apply special
 * colorants, often called spot colorants, to produce effects that cannot be
 * achieved with the standard process colorants alone. Examples include metallic
 * and fluorescent colors and special textures.</li>
 * </ul>
 * <p>A Separation color space (PDF 1.2) provides a means for specifying the use
 * of additional colorants or for isolating the control of individual color
 * components of a device color space for a subtractive device. When such a space
 * is the current color space, the current color is a single-component value,
 * called a tint, that controls the application of the given colorant or color
 * components only.</p>
 * <p>A Separation color space is defined as follows:<br>
 * [/Separation name alternateSpace tintTransform]
 * </p>
 * <ul>
 * <li>The <i>alternateSpace</i> parameter must be an array or name object that
 * identifies the alternate color space, which can be any device or
 * CIE-based color space but not another special color space (Pattern,
 * Indexed, Separation, or DeviceN).</li>
 * <li>The <i>tintTransform</i> parameter must be a function.
 * During subsequent painting operations, an application
 * calls this function to transform a tint value into color component values
 * in the alternate color space. The function is called with the tint value
 * and must return the corresponding color component values. That is, the
 * number of components and the interpretation of their values depend on the
 * alternate color space.</li>
 * </ul>
 *
 * @since 1.0
 */
public class Separation extends PColorSpace {

    public static final Name SEPARATION_KEY = new Name("Separation");

    // alternative colour space, named colour can not be resolved.
    protected final PColorSpace alternate;
    // transform for colour tint, named function type
    protected final Function tintTransform;
    public static final String COLORANT_ALL = "all";
    // The special colorant name None shall not produce any visible output.
    // Painting operations in a Separationspace with this colorant name shall
    // have no effect on the current page.
    private boolean isNone;
    public static final String COLORANT_NONE = "none";
    private volatile float tint = 1.0f;
    // converted colours keyed by the tint's exact float bits, so a colour depends on its tint alone and every
    // rendering thread can share them; 1024 slots, so an image's 256 tints rarely collide (GH-617)
    private final ColorCache colorCache = new ColorCache(10);

    /**
     * Create a new Seperation colour space.  Separation is specified using
     * [/Seperation name alternateSpace tintTransform]
     *
     * @param l              library
     * @param h              dictionary entries
     * @param name           name of colourspace, always seperation
     * @param alternateSpace name of alternative colour space
     * @param tintTransform  function which defines the tint transform
     */
    protected Separation(Library l, DictionaryEntries h, Object name, Object alternateSpace, Object tintTransform) {
        super(l, h);
        alternate = getColorSpace(l, alternateSpace);

        this.tintTransform = Function.getFunction(l, l.getObject(tintTransform));
        // every colorant, Black included, paints through its tint transform and alternate, as a CMYK alternate
        // is the ink and a screen colour isn't (GH-648, GH-647)
        if (name instanceof Name) {
            String colorName = ((Name) name).getName().toLowerCase();
            if (colorName.equals(COLORANT_NONE)) {
                isNone = true;
            }
        }
    }

    /**
     * Returns the number of components in this colour space.
     *
     * @return number of components
     */
    public int getNumComponents() {
        return 1;
    }

    /**
     * A Separation no longer paints its colorant's screen colour: every colorant goes through the tint transform
     * and alternate space (GH-647, GH-648).
     *
     * @return false
     * @deprecated always false; to be removed
     */
    @Deprecated
    public boolean isNamedColor() {
        return false;
    }

    /**
     * Gets the colour in RGB represented by the array of colour components
     *
     * @param components    array of component colour data
     * @param fillAndStroke true indicates a fill or stroke operation, so we
     *                      will try to used the named colour and tint. This
     *                      is generally not do for images.
     * @return new RGB colour composed from the components array.
     */
    public Color getColor(float[] components, boolean fillAndStroke) {
        // the function couldn't be initiated then use the alternative colour
        // space.  The alternate colour space can be any device or CIE-based
        // colour space. However Separation is usually specified using only one
        // component so we must generate the output colour
        if (tintTransform == null) {
            float colour = components[0];
            // copy the colour values into the needed length of the alternate colour
            float[] alternateColour = new float[alternate.getNumComponents()];
            for (int i = 0, max = alternate.getNumComponents(); i < max; i++) {
                alternateColour[i] = colour;
            }
            return alternate.getColor(alternateColour);
        }
        if (alternate != null && !isNone) {
            // a Separation has one component, the tint, so the cache keys on it; a function may clip its
            // input in place, so it gets a copy
            if (components.length != 1) {
                return alternate.getColor(tintTransform.calculate(components.clone()));
            }
            // a tint is 0..1 by definition; a shading function that overshoots must not drive the tint
            // transform outside its domain into an out-of-range Color (which throws and drops the shading)
            float tint = Math.max(0f, Math.min(1f, components[0]));
            int key = Float.floatToIntBits(tint);
            Color color = colorCache.get(key);
            if (color == null) {
                color = alternate.getColor(tintTransform.calculate(new float[]{tint}));
                colorCache.put(key, color);
            }
            return color;
        }
        if (isNone) {
            return new Color(0, 0, 0, 0);
        }
        // return the named colour if it was resolved, otherwise assemble the
        // alternative colour.
        // -- Only applies to subtractive devices, screens are additive but I'm
        // leaving this in encase something goes horribly wrong.
        return null;
    }

    public float getTint() {
        return tint;
    }
}
