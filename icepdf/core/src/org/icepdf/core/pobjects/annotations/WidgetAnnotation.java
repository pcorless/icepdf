/*
 * Copyright 2006-2015 ICEsoft Technologies Inc.
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

import org.icepdf.core.pobjects.Dictionary;
import org.icepdf.core.pobjects.Name;
import org.icepdf.core.pobjects.PRectangle;
import org.icepdf.core.pobjects.StateManager;
import org.icepdf.core.pobjects.acroform.FieldDictionary;
import org.icepdf.core.util.Library;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.util.HashMap;
import java.util.logging.Logger;

/**
 * Interactive forms (see 12.7, “Interactive Forms”) use widget annotations (PDF 1.2)
 * to represent the appearance of fields and to manage user interactions. As a
 * convenience, when a field has only a single associated widget annotation, the
 * contents of the field dictionary (12.7.3, “Field Dictionaries”) and the
 * annotation dictionary may be merged into a single dictionary containing
 * entries that pertain to both a field and an annotation.
 *
 * @since 5.0
 */
public class WidgetAnnotation extends Annotation {

    private static final Logger logger =
            Logger.getLogger(TextMarkupAnnotation.class.toString());

    /**
     * Indicates that the annotation has no highlight effect.
     */
    public static final Name HIGHLIGHT_NONE = new Name("N");

    protected FieldDictionary fieldDictionary;

    protected Name highlightMode;

    public WidgetAnnotation(Library l, HashMap h) {
        super(l, h);
    }

    public void init() {
        super.init();
        fieldDictionary = new FieldDictionary(library, entries);
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

    /**
     * Gets an instance of a PopupAnnotation that has valid Object Reference.
     *
     * @param library document library
     * @param rect    bounding rectangle in user space
     * @return new PopupAnnotation Instance.
     */
    public static WidgetAnnotation getInstance(Library library,
                                               Rectangle rect) {
        // state manager
        StateManager stateManager = library.getStateManager();

        // create a new entries to hold the annotation properties
        HashMap<Name, Object> entries = new HashMap<Name, Object>();
        // set default link annotation values.
        entries.put(Dictionary.TYPE_KEY, Annotation.TYPE_VALUE);
        entries.put(Dictionary.SUBTYPE_KEY, Annotation.SUBTYPE_WIDGET);
        // coordinates
        if (rect != null) {
            entries.put(Annotation.RECTANGLE_KEY,
                    PRectangle.getPRectangleVector(rect));
        } else {
            entries.put(Annotation.RECTANGLE_KEY, new Rectangle(10, 10, 50, 100));
        }

        // create the new instance
        WidgetAnnotation annotation = new WidgetAnnotation(library, entries);
        annotation.init();
        annotation.setPObjectReference(stateManager.getNewReferencNumber());
        annotation.setNew(true);

        // set default flags.
        annotation.setFlag(Annotation.FLAG_READ_ONLY, false);
        annotation.setFlag(Annotation.FLAG_NO_ROTATE, false);
        annotation.setFlag(Annotation.FLAG_NO_ZOOM, false);
        annotation.setFlag(Annotation.FLAG_PRINT, true);

        return annotation;
    }

    @Override
    public void resetAppearanceStream(double dx, double dy, AffineTransform pageSpace) {

    }

    @Override
    protected void renderAppearanceStream(Graphics2D g) {
        if (shapes != null) {
            super.renderAppearanceStream(g);
        } else {

        }
    }

}
