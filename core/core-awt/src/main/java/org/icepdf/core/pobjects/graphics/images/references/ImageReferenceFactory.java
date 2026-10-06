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
package org.icepdf.core.pobjects.graphics.images.references;

import org.icepdf.core.pobjects.Name;
import org.icepdf.core.pobjects.Page;
import org.icepdf.core.pobjects.Resources;
import org.icepdf.core.pobjects.graphics.GraphicsState;
import org.icepdf.core.pobjects.graphics.images.ImageStream;
import org.icepdf.core.util.Defs;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/**
 * The ImageReferenceFactory determines which implementation of the
 * Image Reference should be created.  The ImageReference type can be specified
 * by the following system properties or alternatively by the registered type key.
 * <ul>
 * <li>org.icepdf.core.imageReference = default</li>
 * <li>org.icepdf.core.imageReference = scaled</li>
 * <li>org.icepdf.core.imageReference = mipmap</li>
 * <li>org.icepdf.core.imageReference = smoothScaled</li>
 * <li>org.icepdf.core.imageReference = blurred</li>
 * </ul>
 * The default value returns an unaltered image, scaled returns a scaled
 * image instance and MIP mapped returns/picks a scaled image that best fits
 * the current zoom level for a balance of render speed and quality.
 * <p>
 * New ImageReference implementations can be registered at runtime via
 * {@link #register(String, String, ImageReferenceCreator)}, allowing third-party
 * or application-level image reference types to be added without modifying this class.
 *
 * @see MipMappedImageReference
 * @see ImageStreamReference
 * @see ScaledImageReference
 * @since 5.0
 */
public class ImageReferenceFactory {

    private static final Logger logger = Logger.getLogger(ImageReferenceFactory.class.getName());

    /**
     * Functional interface for creating an {@link ImageReference} instance.
     * Implement this to register a custom image reference type.
     */
    @FunctionalInterface
    public interface ImageReferenceCreator {
        ImageReference create(ImageStream imageStream, Name xobjectName, GraphicsState graphicsState,
                              Resources resources, int imageIndex, Page page);
    }

    // Built-in type key constants
    public static final String TYPE_DEFAULT = "default";
    public static final String TYPE_SCALED = "scaled";
    public static final String TYPE_MIP_MAP = "mipmap";
    public static final String TYPE_SMOOTH_SCALED = "smoothScaled";
    public static final String TYPE_BLURRED = "blurred";

    /**
     * Registry entry pairing a display label with a creator.
     */
    public static final class RegistryEntry {
        private final String displayName;
        private final ImageReferenceCreator creator;

        public RegistryEntry(String displayName, ImageReferenceCreator creator) {
            this.displayName = displayName;
            this.creator = creator;
        }

        public String getDisplayName() {
            return displayName;
        }

        public ImageReferenceCreator getCreator() {
            return creator;
        }
    }

    /**
     * Thread-safe registry of all known image reference types. Insertion order
     * is preserved via the LinkedHashMap delegate so UI combo-boxes are stable.
     */
    private static final Map<String, RegistryEntry> orderedRegistry =
            Collections.synchronizedMap(new LinkedHashMap<>());

    /**
     * Currently active image reference type key.
     */
    public static String imageReferenceType;

    static {
        // Register the five built-in types
        registerBuiltin(TYPE_DEFAULT, "Default", ImageStreamReference::new);
        registerBuiltin(TYPE_SCALED, "Scaled", ScaledImageReference::new);
        registerBuiltin(TYPE_MIP_MAP, "MIP Map", MipMappedImageReference::new);
        registerBuiltin(TYPE_SMOOTH_SCALED, "Smooth Scaled", SmoothScaledImageReference::new);
        registerBuiltin(TYPE_BLURRED, "Blurred", BlurredImageReference::new);

        // Resolve active type from system property
        imageReferenceType = Defs.sysProperty("org.icepdf.core.imageReference", TYPE_DEFAULT);
        if (!orderedRegistry.containsKey(imageReferenceType)) {
            logger.warning("Unknown imageReference type '" + imageReferenceType + "', falling back to default.");
            imageReferenceType = TYPE_DEFAULT;
        }
    }

    private ImageReferenceFactory() {
    }

    /**
     * Registers a new image reference type.  Call this before the first page
     * is rendered, typically at application startup.
     *
     * @param key         unique, case-sensitive identifier (e.g. {@code "myCustomType"})
     * @param displayName human-readable label used in preference UIs
     * @param creator     factory lambda that constructs the ImageReference
     * @throws IllegalArgumentException if {@code key} is null or empty
     */
    public static void register(String key, String displayName, ImageReferenceCreator creator) {
        if (key == null || key.isEmpty()) {
            throw new IllegalArgumentException("ImageReference key must not be null or empty.");
        }
        orderedRegistry.put(key, new RegistryEntry(displayName != null ? displayName : key, creator));
    }

    /**
     * Returns an unmodifiable ordered view of all registered type keys and their entries.
     */
    public static Map<String, RegistryEntry> getRegisteredTypes() {
        synchronized (orderedRegistry) {
            return Collections.unmodifiableMap(new LinkedHashMap<>(orderedRegistry));
        }
    }

    /**
     * Returns an ordered set of all registered type keys.
     */
    public static Set<String> getRegisteredTypeKeys() {
        return getRegisteredTypes().keySet();
    }


    /**
     * Returns the currently active image reference type key.
     */
    public static String getImageReferenceType() {
        return imageReferenceType;
    }

    /**
     * Sets the active image reference type by key.
     *
     * @param key a registered type key; if unknown, falls back to {@link #TYPE_DEFAULT}.
     */
    public static void setImageReferenceType(String key) {
        if (orderedRegistry.containsKey(key)) {
            imageReferenceType = key;
        } else {
            logger.warning("Unknown imageReference type '" + key + "', falling back to default.");
            imageReferenceType = TYPE_DEFAULT;
        }
    }

    /**
     * Normalizes a property-string or legacy enum-name to a registered key.
     * Handles both the canonical keys ({@code "default"}, {@code "mipmap"}, …) and
     * the old enum names ({@code "DEFAULT"}, {@code "MIP_MAP"}, …) for backward
     * compatibility with stored preferences.
     *
     * @param name the raw string from a system property or preference store.
     * @return the matching registered key, or {@link #TYPE_DEFAULT} if not found.
     */
    public static String getImageReferenceType(String name) {
        if (name == null) return TYPE_DEFAULT;
        // Direct registry hit (handles canonical keys and any custom keys)
        if (orderedRegistry.containsKey(name)) return name;
        // Legacy enum-name aliases for backward compatibility
        switch (name.toUpperCase()) {
            case "SCALED":
                return TYPE_SCALED;
            case "MIP_MAP":
                return TYPE_MIP_MAP;
            case "SMOOTH_SCALED":
                return TYPE_SMOOTH_SCALED;
            case "BLURRED":
                return TYPE_BLURRED;
            default:
                return TYPE_DEFAULT;
        }
    }


    /**
     * Gets an instance of an ImageReference object for the given image data.
     * The ImageReference type is determined by {@link #imageReferenceType}.
     *
     * @param imageStream   image data
     * @param xobjectName   image name if specified via xObject reference, can be null
     * @param resources     parent resource object
     * @param graphicsState image graphic state
     * @param page          page that the image belongs to
     * @param imageIndex    image index number of total images for the page
     * @return newly created ImageReference
     */
    public static ImageReference getImageReference(
            ImageStream imageStream, Name xobjectName, Resources resources, GraphicsState graphicsState,
            Integer imageIndex, Page page) {
        RegistryEntry entry = orderedRegistry.get(imageReferenceType);
        if (entry == null) {
            entry = orderedRegistry.get(TYPE_DEFAULT);
        }
        return entry.getCreator().create(imageStream, xobjectName, graphicsState, resources, imageIndex, page);
    }


    private static void registerBuiltin(String key, String displayName, ImageReferenceCreator creator) {
        orderedRegistry.put(key, new RegistryEntry(displayName, creator));
    }
}
