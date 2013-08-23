/*
 * Copyright 2006-2013 ICEsoft Technologies Inc.
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

package org.icepdf.core.pobjects.annotations;

import org.icepdf.core.pobjects.Name;
import org.icepdf.core.pobjects.acroform.FieldDictionary;
import org.icepdf.core.util.Library;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.util.HashMap;
import java.util.logging.Logger;

/**
 * Abstract base class for Widget annotations types, button, choice and text.
 *
 * @since 5.1
 */
public abstract class AbstractWidgetAnnotation extends Annotation {

    private static final Logger logger =
            Logger.getLogger(AbstractWidgetAnnotation.class.toString());

    /**
     * Indicates that the annotation has no highlight effect.
     */
    public static final Name HIGHLIGHT_NONE = new Name("N");

    protected FieldDictionary fieldDictionary;

    protected Name highlightMode;

    public AbstractWidgetAnnotation(Library l, HashMap h) {
        super(l, h);
        Object possibleName = getObject(LinkAnnotation.HIGHLIGHT_MODE_KEY);
        if (possibleName instanceof Name) {
            Name name = (Name) possibleName;
            if (HIGHLIGHT_NONE.equals(name.getName())) {
                highlightMode = HIGHLIGHT_NONE;
            } else if (LinkAnnotation.HIGHLIGHT_OUTLINE.equals(name.getName())) {
                highlightMode = LinkAnnotation.HIGHLIGHT_OUTLINE;
            } else if (LinkAnnotation.HIGHLIGHT_PUSH.equals(name.getName())) {
                highlightMode = LinkAnnotation.HIGHLIGHT_PUSH;
            }
        }
        highlightMode = LinkAnnotation.HIGHLIGHT_INVERT;

    }

    public abstract void reset();

    @Override
    public abstract void resetAppearanceStream(double dx, double dy, AffineTransform pageSpace);

    @Override
    protected void renderAppearanceStream(Graphics2D g) {

        Appearance appearance = appearances.get(currentAppearance);
        AppearanceState appearanceState = appearance.getSelectedAppearanceState();

        if (appearanceState != null &&
                appearanceState.getShapes() != null) {
            super.renderAppearanceStream(g);
        }
    }

    public FieldDictionary getFieldDictionary() {
        return fieldDictionary;
    }
}
