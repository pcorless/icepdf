package org.icepdf.ri.images;

import javax.swing.*;

import static org.icepdf.ri.images.Images.IconSize;

/**
 * Abstract class providing logic for fetching icons from the classpath
 * <p/>
 * Ultimately, this should only be called by the {@link Images} utility class
 *
 * @author Alexander Leithner
 */
public abstract class IconPack {

    /**
     * Enum describing the different available variants of any icon
     */
    public enum Variant {
        /**
         * "None" variant, i.e. either no variant is preferred or the source icon is known not to have variants.
         * <p/>
         * An icon pack may choose to load a different variant than "none" if it deems it appropriate to do so.
         */
        NONE,

        /**
         * "Normal" variant, i.e. the icon which should be displayed in a button's default state.
         */
        NORMAL,

        /**
         * "Disabled" variant to be displayed whenever a button/component is marked as disabled.
         */
        DISABLED,

        /**
         * "Rollover" variant to be displayed when the mouse cursors hovers a component/button.
         */
        ROLLOVER,

        /**
         * "Pressed" variant to be displayed when a button is pressed.
         */
        PRESSED,

        /**
         * "Selected" variant to be displayed when a toggleable component is marked as selected.
         */
        SELECTED
    }

    /**
     * Data class advertising the different (optional) variants an icon pack provides
     */
    public static class VariantPool {

        private final boolean providesDisabled;
        private final boolean providesRollover;
        private final boolean providesPressed;
        private final boolean providesSelected;

        protected VariantPool(Variant... variants) {
            boolean providesDisabled = false;
            boolean providesRollover = false;
            boolean providesPressed = false;
            boolean providesSelected = false;
            for (Variant variant : variants) {
                switch (variant) {
                    case DISABLED:
                        providesDisabled = true;
                        break;
                    case ROLLOVER:
                        providesRollover = true;
                        break;
                    case PRESSED:
                        providesPressed = true;
                        break;
                    case SELECTED:
                        providesSelected = true;
                        break;
                }
            }

            this.providesDisabled = providesDisabled;
            this.providesRollover = providesRollover;
            this.providesPressed = providesPressed;
            this.providesSelected = providesSelected;
        }

        /**
         * Whether this icon pack provides the {@link Variant#DISABLED} variant.
         *
         * @return If {@link Variant#DISABLED} is available
         */
        public boolean disabledProvided() {
            return this.providesDisabled;
        }

        /**
         * Whether this icon pack provides the {@link Variant#ROLLOVER} variant.
         *
         * @return If {@link Variant#ROLLOVER} is available
         */
        public boolean rolloverProvided() {
            return this.providesRollover;
        }

        /**
         * Whether this icon pack provides the {@link Variant#PRESSED} variant.
         *
         * @return If {@link Variant#PRESSED} is available
         */
        public boolean pressedProvided() {
            return this.providesPressed;
        }

        /**
         * Whether this icon pack provides the {@link Variant#SELECTED} variant.
         *
         * @return If {@link Variant#SELECTED} is available
         */
        public boolean selectedProvided() {
            return this.providesSelected;
        }

    }

    /**
     * Gets all variants this icon pack provides for at least one icon.
     * <p/>
     * This method is called by the {@link Images} utility class when deciding what icons to register to a given
     * component. No guarantee regarding further usage is made.
     *
     * @return Information about what variant this icon pack provides.
     */
    public abstract VariantPool getProvidedVariants();

    /**
     * Gets a single icon with the given name in the given variant and size from the classpath.
     * <p/>
     * Note that the {@code size} parameter is to be regarded as a hint. If the icon pack deems it necessary to return
     * another size for any given icon, the icon will be returned in this size instead. No guarantee is made that any
     * icon return by this method is square
     *
     * @param name    The name of the icon to retrieve
     * @param variant The variant to retrieve
     * @param size    The size to retrieve
     * @return The icon requested in the given variant, either in the wanted size or a similar size
     * @throws RuntimeException If the icon could not be retrieved from the classpath or any other error occurs
     */
    public abstract Icon getIcon(String name, Variant variant, IconSize size) throws RuntimeException;

}
