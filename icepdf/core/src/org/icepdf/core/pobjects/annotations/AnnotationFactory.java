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
