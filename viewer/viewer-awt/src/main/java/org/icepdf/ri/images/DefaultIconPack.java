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
