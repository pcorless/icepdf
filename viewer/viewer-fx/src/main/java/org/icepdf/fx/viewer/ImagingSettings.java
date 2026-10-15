/*
 * Copyright 2026 Patrick Corless
 *
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
package org.icepdf.fx.viewer;

import org.icepdf.core.pobjects.graphics.images.references.ImageDownsampler;
import org.icepdf.core.pobjects.graphics.images.references.ImageReferenceFactory;

/**
 * How core draws images, from the viewer's settings: the image reference type
 * ({@link ImageReferenceFactory}) and zoom-aware downsampling ({@link ImageDownsampler}).
 * <p>
 * Both are JVM-wide core settings.  One that was never saved is left alone, so a
 * {@code -Dorg.icepdf.core.imageReference} or {@code -Dorg.icepdf.core.imageReference.downsample}
 * given on the command line still holds until the user picks something in Preferences.
 */
public final class ImagingSettings {

    private ImagingSettings() {
    }

    /** Hands the saved settings to core. */
    public static void apply(ViewerPreferences preferences) {
        String type = preferences.get(ViewerPreferences.IMAGE_REFERENCE, null);
        if (type != null) {
            ImageReferenceFactory.setImageReferenceType(ImageReferenceFactory.getImageReferenceType(type));
        }
        String downsample = preferences.get(ViewerPreferences.IMAGE_DOWNSAMPLE, null);
        if (downsample != null) {
            ImageDownsampler.setEnabled(Boolean.parseBoolean(downsample.trim()));
        }
    }
}
