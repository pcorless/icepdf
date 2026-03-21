/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.icepdf.ri.images;

import javax.swing.*;
import java.net.URL;

/**
 * Icon pack definition for the default colour icons shipping with ICEpdf
 * <p/>
 * This icon pack supports all icon variants. Typical icon sizes are:
 * <ul>
 *     <li>{@code HUGE}: 57&times;48 px</li>
 *     <li>{@code LARGE}: 32&times;32 px</li>
 *     <li>{@code SMALL}: 24&times;24 px</li>
 *     <li>{@code MINI}: 20&times;20 px</li>
 *     <li>{@code TINY}: 16&times;16 px</li>
 * </ul>
 * All icons in this pack are returned as {@link ImageIcon}s created from PNG files
 *
 * @author Alexander Leithner
 */
public class DefaultIconPack extends IconPack {

    @Override
    public VariantPool getProvidedVariants() {
        return new VariantPool(Variant.DISABLED, Variant.ROLLOVER, Variant.PRESSED, Variant.SELECTED);
    }

    @Override
    public Icon getIcon(String name, Variant variant, Images.IconSize size) throws RuntimeException {
        String iconSize;
        switch (size) {
            case HUGE:
                iconSize = "_lg";
                break;
            case LARGE:
                iconSize = "_32";
                break;
            case SMALL:
                iconSize = "_24";
                break;
            case MINI:
                iconSize = "_20";
                break;
            default:
                iconSize = "_16";
                break;
        }

        String iconVariant;
        switch (variant) {
            case NORMAL:
                iconVariant = "_a";
                break;
            case PRESSED:
            case DISABLED:
                iconVariant = "_i";
                break;
            case ROLLOVER:
                iconVariant = "_r";
                break;
            case SELECTED:
                iconVariant = "_selected_a";
                break;
            case NONE:
            default:
                iconVariant = "";
                break;
        }

        URL url = Images.class.getResource(name + iconVariant + iconSize + ".png");
        if (url == null) {
            throw new NullPointerException("Icon " + name + " not found with variant " + variant + ", size " + size + " on classpath; NULL URL returned");
        }

        return new ImageIcon(url);
    }
}
