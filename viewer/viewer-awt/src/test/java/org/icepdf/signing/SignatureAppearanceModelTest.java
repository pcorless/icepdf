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
package org.icepdf.signing;

import org.icepdf.core.pobjects.StateManager;
import org.icepdf.core.pobjects.structure.CrossReferenceRoot;
import org.icepdf.core.util.Library;
import org.icepdf.ri.common.views.annotations.signing.SignatureAppearanceLayout;
import org.icepdf.ri.common.views.annotations.signing.SignatureAppearanceModelImpl;
import org.icepdf.ri.util.ViewerPropertiesManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.prefs.Preferences;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The layout settings the signature appearance reads.
 * <p>
 * These live in {@link Preferences}, which is a store the user can edit and which carries whatever
 * was last written to it on that machine.  A value in it that makes no sense - a maximum below the
 * minimum, a negative padding, a floor above the font size it is a floor for - has to come back as
 * something the layout can still be built from, because the alternative is an appearance that is
 * silently wrong on one machine and right on every other.
 */
public class SignatureAppearanceModelTest {

    private static final String[] KEYS = {
            ViewerPropertiesManager.PROPERTY_SIGNATURE_PADDING,
            ViewerPropertiesManager.PROPERTY_SIGNATURE_IMAGE_WIDTH_MAX,
            ViewerPropertiesManager.PROPERTY_SIGNATURE_LINE_LEADING,
            ViewerPropertiesManager.PROPERTY_SIGNATURE_LAYOUT,
            ViewerPropertiesManager.PROPERTY_SIGNATURE_IMAGE_SCALE,
            ViewerPropertiesManager.PROPERTY_SIGNATURE_FONT_SIZE};

    private Preferences preferences;
    private SignatureAppearanceModelImpl model;

    @BeforeEach
    public void setUp() {
        preferences = ViewerPropertiesManager.getInstance().getPreferences();
        // These are the signer's real stored settings, not the test's; put back what was there.
        for (String key : KEYS) {
            preferences.remove(key);
        }
        Library library = new Library();
        library.setStateManager(new StateManager(new CrossReferenceRoot(library)));
        model = new SignatureAppearanceModelImpl(library);
    }

    @AfterEach
    public void tearDown() {
        for (String key : KEYS) {
            preferences.remove(key);
        }
    }

    @DisplayName("the layout settings have defaults, so an unconfigured signer gets a sane appearance")
    @Test
    public void defaultsAreUsedWhenNothingIsStored() {
        assertEquals(SignatureAppearanceModelImpl.DEFAULT_PADDING, model.getAppearancePadding());
        assertEquals(SignatureAppearanceModelImpl.DEFAULT_IMAGE_WIDTH_MAX,
                model.getImageMaxWidthPercentage());
        assertEquals(SignatureAppearanceModelImpl.DEFAULT_LINE_LEADING, model.getLineLeadingPercentage());
        assertEquals(SignatureAppearanceModelImpl.DEFAULT_LAYOUT, model.getLayout());
    }

    @DisplayName("a stored layout setting is read back")
    @Test
    public void storedSettingsAreRead() {
        model.setAppearancePadding(8);
        model.setImageMaxWidthPercentage(70);
        model.setLineLeadingPercentage(50);

        assertEquals(8, model.getAppearancePadding());
        assertEquals(70, model.getImageMaxWidthPercentage());
        assertEquals(50, model.getLineLeadingPercentage());
    }

    @DisplayName("the chosen layout is remembered")
    @Test
    public void layoutIsStored() {
        model.setLayout(SignatureAppearanceLayout.OVERLAY);
        assertEquals(SignatureAppearanceLayout.OVERLAY, model.getLayout());
    }

    @DisplayName("a layout name that means nothing falls back rather than failing to sign")
    @Test
    public void unknownLayoutFallsBack() {
        // A name written by a version that had a layout this one does not, or simply mistyped into
        // the preferences file.  Throwing here would take the signing dialog with it.
        preferences.put(ViewerPropertiesManager.PROPERTY_SIGNATURE_LAYOUT, "DIAGONAL");
        assertEquals(SignatureAppearanceModelImpl.DEFAULT_LAYOUT, model.getLayout());
    }

    @DisplayName("nonsense percentages are clamped to a usable range")
    @Test
    public void percentagesAreClamped() {
        model.setImageMaxWidthPercentage(-40);
        model.setImageScale(400);
        model.setAppearancePadding(-5);

        assertEquals(0, model.getImageMaxWidthPercentage());
        assertEquals(100, model.getImageScale(), "the image never fills more than the room it has");
        assertEquals(0, model.getAppearancePadding());
    }
}
