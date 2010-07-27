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
 * The Original Code is ICEpdf 4.1 open source software code, released
 * May 1st, 2009. The Initial Developer of the Original Code is ICEsoft
 * Technologies Canada, Corp. Portions created by ICEsoft are Copyright (C)
 * 2004-2010 ICEsoft Technologies Canada, Corp. All Rights Reserved.
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

import org.icepdf.core.pobjects.Name;
import org.icepdf.core.pobjects.functions.Function;
import org.icepdf.core.util.ColorUtil;
import org.icepdf.core.util.Library;

import java.awt.*;
import java.util.Hashtable;

/**
 * <p>Separation Color Space background:</p>
 * <ul>
 * <p>Color output devices produce full color by combining primary or process
 * colorants in varying amounts. On an additive color device such as a display,
 * the primary colorants consist of red, green, and blue phosphors; on a
 * subtractive device such as a printer, they typically consist of cyan, magenta,
 * yellow, and sometimes black inks. In addition, some devices can apply special
 * colorants, often called spot colorants, to produce effects that cannot be
 * achieved with the standard process colorants alone. Examples include metallic
 * and fluorescent colors and special textures.</p>
 * </ul>
 * <p>A Separation color space (PDF 1.2) provides a means for specifying the use
 * of additional colorants or for isolating the control of individual color
 * components of a device color space for a subtractive device. When such a space
 * is the current color space, the current color is a single-component value,
 * called a tint, that controls the application of the given colorant or color
 * components only.</p>
 * <p>A Separation color space is defined as follows:<br />
 * [/Separation name alternateSpace tintTransform]
 * </p>
 * <ul>
 * <li>The <i>alternateSpace</i> parameter must be an array or name object that
 * identifies the alternate color space, which can be any device or
 * CIE-based color space but not another special color space (Pattern,
 * Indexed, Separation, or DeviceN).</li>
 * <li>The <i>tintTransform</i> parameter must be a function (currently only suppports
 * type 0 and type2 ). During subsequent painting operations, an application
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

    // named colour reference if valid conversion took place
    protected Color namedColor;
    // alternative colour space, named colour can not be resolved.
    protected PColorSpace alternate;
    // transform for colour tint, named function type
    protected Function tintTransform;

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
    protected Separation(Library l, Hashtable h, Object name, Object alternateSpace, Object tintTransform) {
        super(l, h);
        alternate = getColorSpace(l, alternateSpace);
        this.tintTransform = Function.getFunction(l, l.getObject(tintTransform));
        // see if name can be converted to a known colour.
        if (name instanceof Name) {
            String colorName = ((Name) name).getName();

            // get colour value if any
            int colorVaue = ColorUtil.convertNamedColor(colorName.toLowerCase());
            if (colorVaue != -1) {
                namedColor = new Color(colorVaue);
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
     * Gets the colour in RGB represened by the array of colour components
     *
     * @param components array of component colour data
     * @return new RGB colour composed from the components array.
     */
    public Color getColor(float[] components) {
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
        if (alternate != null){
            float y[] = tintTransform.calculate(components);
            return alternate.getColor(reverse(y));
        }
        // return the named colour if it was resolved, otherwise assemble the
        // alternative colour.
        // -- Only applies to subtractive devices, screens are additive but I'm
        // leaving this in encase something goes horribly wrong.
        return namedColor;
    }
}
