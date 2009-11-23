/*
 * Version: MPL 1.1/GPL 2.0/LGPL 2.1
 *
 * "The contents of this file are subject to the Mozilla Public License
 * Version 1.1 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
 * License for the specific language governing rights and limitations under
 * the License.
 *
 * The Original Code is ICEpdf 3.0 open source software code, released
 * May 1st, 2009. The Initial Developer of the Original Code is ICEsoft
 * Technologies Canada, Corp. Portions created by ICEsoft are Copyright (C)
 * 2004-2009 ICEsoft Technologies Canada, Corp. All Rights Reserved.
 *
 * Contributor(s): _____________________.
 *
 * Alternatively, the contents of this file may be used under the terms of
 * the GNU Lesser General Public License Version 2.1 or later (the "LGPL"
 * License), in which case the provisions of the LGPL License are
 * applicable instead of those above. If you wish to allow use of your
 * version of this file only under the terms of the LGPL License and not to
 * allow others to use your version of this file under the MPL, indicate
 * your decision by deleting the provisions above and replace them with
 * the notice and other provisions required by the LGPL License. If you do
 * not delete the provisions above, a recipient may use your version of
 * this file under either the MPL or the LGPL License."
 *
 */
package org.icepdf.core.pobjects.annotations;

import org.icepdf.core.pobjects.StateManager;
import org.icepdf.core.util.Library;

import java.awt.*;
import java.util.Hashtable;
import java.util.Vector;

/**
 * Factory for build annotations
 *
 * @since 4.0
 */
public class AnnotationFactory {

    public static final int LINK_ANNOTATION = 1;

    /**
     * Creates a new Annotation object using properties from the annotationState
     * paramater.  If no annotaitonState is provided a LinkAnnotation is returned
     * with with a black border.  The rect specifies where the annotation should
     * be located in user space.
     * <p/>
     * This call adds the new Annotation object to the document library as well
     * as the document StateManager.
     *
     * @param library         library to register annotation with
     * @param type            type of annotation to create
     * @param rect            bounds of new annotation specified in user space.
     * @param annotationState annotation state to copy state rom.
     * @return new annotation object with the same properties as the one
     *         specified in annotaiton state.
     */
    public static Annotation buildAnnotation(Library library,
                                             int type,
                                             Rectangle rect,
                                             AnnotationState annotationState) {
        // state manager 
        StateManager stateManager = library.getStateManager();

        // create a new entries to hold the annotation properties
        Hashtable<String, Object> entries = new Hashtable<String, Object>();
        // set default link annotation values. 
        entries.put(Annotation.TYPE, Annotation.TYPE_VALUE);
        entries.put(Annotation.SUBTYPE, Annotation.SUBTYPE_LINK);
        // copy over properties
        if (annotationState != null) {

        }
        // some defaults just for display purposes.
        else {
            entries.put(Annotation.SUBTYPE, Annotation.SUBTYPE_LINK);

            // /C [ 1 0 0 ]
            Vector<Number> properties = new Vector<Number>();
            properties.add(1);
            properties.add(0);
            properties.add(0);
            entries.put(Annotation.COLOR, properties);
            // /Border [ 0 0 1 ]
            properties = new Vector<Number>();
            properties.add(0);
            properties.add(0);
            properties.add(1);
            entries.put(Annotation.BORDER, properties);
        }
        // coordinates
        if (rect != null) {
            entries.put(Annotation.RECTANGLE, rect);
        } else {
            entries.put(Annotation.RECTANGLE, new Rectangle(10, 10, 50, 100));
        }

        // we only support one type of annotation creation for now
        LinkAnnotation linkAnnotation = new LinkAnnotation(library, entries);
        linkAnnotation.setPObjectReference(stateManager.getNewReferencNumber());
        linkAnnotation.setNew(true);
        return linkAnnotation;

    }
}
