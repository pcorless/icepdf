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
	public VariantPool getProvidedVariants () {
		return new VariantPool (Variant.DISABLED, Variant.ROLLOVER, Variant.PRESSED, Variant.SELECTED);
	}

	@Override
	public Icon getIcon (String name, Variant variant, Images.IconSize size) throws RuntimeException {
		String iconSize;
		if (size == Images.IconSize.HUGE) iconSize = "_lg";
		else if (size == Images.IconSize.LARGE) iconSize = "_32";
		else if (size == Images.IconSize.SMALL) iconSize = "_24";
		else if (size == Images.IconSize.MINI) iconSize = "_20";
		else iconSize = "_16";

		String iconVariant;
		if (variant == Variant.PRESSED || variant == Variant.DISABLED) iconVariant = "_i";
		else if (variant == Variant.ROLLOVER) iconVariant = "_r";
		else if (variant == Variant.SELECTED) iconVariant = "_selected_a";
		else if (variant == Variant.NONE) iconVariant = "";
		else iconVariant = "_a";

		URL url = Images.class.getResource (name + iconVariant + iconSize + ".png");
		if (url == null) {
			throw new NullPointerException ("Icon " + name + " not found with variant " + variant + ", size " + size + " on classpath; NULL URL returned");
		}

		return new ImageIcon (url);
	}

	@Override
	public int getHeightValueForSize (Images.IconSize size) {
		switch (size) {
			case HUGE: return 57;
			case LARGE: return 32;
			case SMALL: return 24;
			case MINI: return 20;
			default: return 16;
		}
	}

}
