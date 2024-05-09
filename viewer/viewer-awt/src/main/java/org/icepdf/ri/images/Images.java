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
package org.icepdf.ri.images;

import org.icepdf.ri.util.ViewerPropertiesManager;

import javax.swing.*;
import java.net.URL;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;

import static org.icepdf.ri.images.IconPack.Variant;

/**
 * Utility class providing a unified interface to all application icons.
 * <p/>
 * For a one-off, directly accessing the default icon pack, call {@link #get(String)}. When loading icons which
 * are meant to be customizable by applications embedding ICEpdf, call:
 * <ul>
 *     <li>for a single icon {@link #getSingleIcon(String, Variant, IconSize)}</li>
 *     <li>for a button without different styles for its states,
 *     {@link #applyIcon(AbstractButton, String, Variant, IconSize)}</li>
 *     <li>for a button with different styles per state, {@link #applyIcons(AbstractButton, String, IconSize)}</li>
 * </ul>
 * Icons for the different states will be loaded and registered on the button if the current icon pack provides
 * these styles. Note that all types of buttons, including {@code JMenu}s and {@code JMenuItem}s are supported
 * by {@link #applyIcons(AbstractButton, String, IconSize)} and
 * {@link #applyIcon(AbstractButton, String, Variant, IconSize)}.
 *
 * @author Mark Collette
 * @author Alexander Leithner
 * @since 2.0
 */
public class Images {

    /**
     * Enum specifying the wanted size of the icon
     * <p/>
     * Note that icon packs may choose to ignore icon sizes
     */
    public enum IconSize {
        /**
         * "Huge" size, typically aimed to be 48x48 px
         */
        HUGE,
        /**
         * "Large" size, typically aimed to be 32x32 px
         */
        LARGE,
        /**
         * "Small" size, typically aimed to be 24x24 px
         */
        SMALL,
        /**
         * "Mini" size, typically aimed to be 20x20 px
         */
        MINI,
        /**
         * "Tiny" size, typically aimed to be 16x16 px
         */
        TINY
    }

    private static final Logger LOGGER = Logger.getLogger(Images.class.getName());

    private static final IconPack ICON_PACK;
    private static final IconPack.VariantPool AVAILABLE_VARIANTS;

    static {
        Object defaultIconPackProp = UIManager.get("org.icepdf.ri.iconpack");
        if (!(defaultIconPackProp instanceof IconPack)) {
            LOGGER.fine("No user-defined icon pack was registered or registered one was invalid; using default icon " +
                    "pack");
            ICON_PACK = new DefaultIconPack();
        } else {
            ICON_PACK = (IconPack) defaultIconPackProp;
        }

        AVAILABLE_VARIANTS = ICON_PACK.getProvidedVariants();
    }

    /**
     * Legacy string, kept for compatibility reasons when reading old preferences files.
     * <p/>
     * Any new usage should instead switch to {@link IconSize#LARGE}.
     */
    public static final String SIZE_LARGE = "_32";

    /**
     * Legacy string, kept for compatibility reasons when reading old preferences files.
     * <p/>
     * Any new usage should instead switch to {@link IconSize#SMALL}.
     */
    public static final String SIZE_SMALL = "_24";

    /**
     * Get a single icon from the classpath (must be PNG or GIF); no icon packs involved
     *
     * @param name The file to retrieve
     * @return A URL referring to the requested icon on the classpath.
     */
    public static URL get(String name) {
        return Images.class.getResource(name);
    }

    /**
     * Get the current setting for the icon size or the given "else" value if preference is unset or is invalid
     * <p/>
     * This method is equivalent to calling {@code getDefaultIconSizeOr(propertiesManager.getPreferences(), elseValue)}
     *
     * @param propertiesManager The Properties Manager to retrieve the preferences from
     * @param elseValue         The value to return if preference is unset or is invalid
     * @return The current preference for the icon size or the given default value
     */
    public static IconSize getDefaultIconSizeOr(ViewerPropertiesManager propertiesManager, IconSize elseValue) {
        return getDefaultIconSizeOr(propertiesManager.getPreferences(), elseValue);
    }

    /**
     * Get the current setting for the icon size or the given "else" value if preference is unset or is invalid
     *
     * @param preferences The preference store to retrieve the preference from
     * @param elseValue   The value to return if preference is unset or is invalid
     * @return The current preference for the icon size or the given default value
     */
    public static IconSize getDefaultIconSizeOr(Preferences preferences, IconSize elseValue) {
        IconSize iconSize;

        String defaultSizeStr = preferences.get(ViewerPropertiesManager.PROPERTY_ICON_DEFAULT_SIZE, Images.SIZE_LARGE);
        if (defaultSizeStr == null || !(defaultSizeStr.equals(Images.SIZE_SMALL) || defaultSizeStr.equals(Images.SIZE_LARGE))) {
            try {
                iconSize = IconSize.valueOf(defaultSizeStr);
            } catch (IllegalArgumentException e) {
                iconSize = elseValue;
            }
        } else if (defaultSizeStr.equals(Images.SIZE_SMALL)) iconSize = Images.IconSize.SMALL;
        else iconSize = Images.IconSize.LARGE;

        return iconSize;
    }

    /**
     * Ask the current icon pack what its default height value (in pixels) is for the given icon size
     *
     * @param iconSize The icon size to query
     * @return The height value for the given icon size of the current icon pack
     */
    public static int getHeightValueForIconSize(IconSize iconSize) {
        return ICON_PACK.getHeightValueForSize(iconSize);
    }

    /**
     * Apply normal, pressed, rollover and disabled variants of the given icon in the given size to the given button
     * <p/>
     * Pressed, rollover and disabled icons are only registered if the current icon pack supports those variants
     *
     * @param button   The button to register the icons to
     * @param iconName The icon to register
     * @param size     The size of the icon to register
     */
    public static void applyIcons(AbstractButton button, String iconName, IconSize size) {
        applyIcon(button::setIcon, iconName, Variant.NORMAL, size);
        if (AVAILABLE_VARIANTS.pressedProvided()) applyIcon(button::setPressedIcon, iconName, Variant.PRESSED, size);
        if (AVAILABLE_VARIANTS.rolloverProvided()) applyIcon(button::setRolloverIcon, iconName, Variant.ROLLOVER, size);
        if (AVAILABLE_VARIANTS.disabledProvided()) applyIcon(button::setDisabledIcon, iconName, Variant.DISABLED, size);
    }

    /**
     * Apply the given icon in the given variant and size to the given button
     * <p/>
     * No check is made whether the current icon pack provides the variant asked for. If it doesn't, calling this
     * method may either fail with a runtime exception or a corrupted icon being displayed by the button.
     *
     * @param button   The button to register the icon to
     * @param iconName The icon to register
     * @param variant  The wanted variant of the given icon
     * @param size     The wanted size
     */
    public static void applyIcon(AbstractButton button, String iconName, Variant variant, IconSize size) {
        applyIcon(button::setIcon, iconName, variant, size);
    }

    /**
     * Get a single icon in the given variant and size from the current icon pack
     * <p/>
     * If the icon pack encounters an error trying to create the icon, it may throw a {@code RuntimeException},
     * which will not be caught by this method but passed to the callee.
     *
     * @param iconName The icon to retrieve from the current icon pack
     * @param variant  The variant of the icon to retrieve
     * @param size     The size in which to retrieve the icon
     * @return The icon as fetched by the icon pack
     * @throws RuntimeException If the icon pack could not find or could not load the requested icon
     */
    public static Icon getSingleIcon(String iconName, Variant variant, IconSize size) throws RuntimeException {
        return ICON_PACK.getIcon(iconName, variant, size);
    }

    /**
     * Retrieve the given icon and apply it using the given consumer (possibly to an {@code AbstractButton}).
     *
     * @param applyFunction The consumer which will take care the retrieved icon will be applied
     * @param name          The name of the icon to fetch
     * @param variant       The variant of the icon to fetch
     * @param size          The wanted size of the icon
     */
    private static void applyIcon(Consumer<Icon> applyFunction, String name, Variant variant, IconSize size) {
        try {
            applyFunction.accept(ICON_PACK.getIcon(name, variant, size));
        } catch (RuntimeException e) {
            LOGGER.log(Level.WARNING, "Couldn't get icon " + name + ", size " + size + ", variant " + variant + " " +
                    "from icon pack", e);
        }
    }

}
