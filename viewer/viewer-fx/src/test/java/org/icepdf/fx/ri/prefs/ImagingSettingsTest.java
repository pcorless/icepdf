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
package org.icepdf.fx.ri.prefs;

import org.icepdf.core.pobjects.graphics.images.references.ImageDownsampler;
import org.icepdf.core.pobjects.graphics.images.references.ImageReferenceFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The Imaging settings reach core, and settings never saved leave core's own (command line)
 * values alone.
 */
public class ImagingSettingsTest {

    @AfterEach
    public void restoreCore() {
        // both are JVM-wide, so put them back for the next test
        ImageReferenceFactory.setImageReferenceType(ImageReferenceFactory.TYPE_DEFAULT);
        ImageDownsampler.setEnabled(true);
    }

    @Test
    public void savedSettingsApply() {
        ViewerPreferences preferences = ViewerPreferences.inMemory();
        preferences.put(ViewerPreferences.IMAGE_REFERENCE, ImageReferenceFactory.TYPE_SMOOTH_SCALED);
        preferences.putBoolean(ViewerPreferences.IMAGE_DOWNSAMPLE, false);
        ImagingSettings.apply(preferences);
        assertEquals(ImageReferenceFactory.TYPE_SMOOTH_SCALED, ImageReferenceFactory.getImageReferenceType());
        assertFalse(ImageDownsampler.isEnabled());
    }

    @Test
    public void unsavedSettingsLeaveCoreAlone() {
        ImageReferenceFactory.setImageReferenceType(ImageReferenceFactory.TYPE_BLURRED);
        ImageDownsampler.setEnabled(false);
        ImagingSettings.apply(ViewerPreferences.inMemory());
        assertEquals(ImageReferenceFactory.TYPE_BLURRED, ImageReferenceFactory.getImageReferenceType());
        assertFalse(ImageDownsampler.isEnabled());
    }

    @Test
    public void legacyTypeNamesAreUnderstood() {
        // the Swing viewer stored the old enum names; a hand-copied value should still work
        ViewerPreferences preferences = ViewerPreferences.inMemory();
        preferences.put(ViewerPreferences.IMAGE_REFERENCE, "MIP_MAP");
        ImagingSettings.apply(preferences);
        assertEquals(ImageReferenceFactory.TYPE_MIP_MAP, ImageReferenceFactory.getImageReferenceType());
    }
}
