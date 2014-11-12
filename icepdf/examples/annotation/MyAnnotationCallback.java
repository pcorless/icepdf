/*
 * Copyright 2006-2014 ICEsoft Technologies Inc.
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

import org.icepdf.core.pobjects.Page;
import org.icepdf.core.pobjects.actions.*;
import org.icepdf.core.pobjects.annotations.Annotation;
import org.icepdf.core.pobjects.annotations.BorderStyle;
import org.icepdf.core.pobjects.annotations.LinkAnnotation;
import org.icepdf.ri.common.views.AnnotationCallback;
import org.icepdf.ri.common.views.AnnotationComponent;
import org.icepdf.ri.common.views.DocumentViewController;
import org.icepdf.ri.common.views.PageViewComponent;
import org.icepdf.ri.util.BareBonesBrowserLaunch;

import java.awt.*;
import java.util.List;
import java.util.WeakHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * This class represents a basic implementation of the AnnotationCallback.  This
 * class also modifies the border of annotation for initial display, showing a
 * border around all annotation.  When an annotation is clicked on we then change
 * the colour of the annotation to an alternate colour to indicate that the link
 * has already been clicked.
 *
 * @author ICEsoft Technologies, Inc.
 * @since 2.7.1
 */
public class MyAnnotationCallback implements AnnotationCallback {

    private static final Logger logger =
            Logger.getLogger(MyAnnotationCallback.class.toString());

    private DocumentViewController documentViewController;

    // annotation History map similar to browser link history.  This is weak
    // hash map to avoid any potential memory issue for a large document.  As
    // this class lives for as long as the document is open.
    private WeakHashMap<String, AnnotationState> annotationHistory;

    private static final Color ANNOTATION = Color.red;
    private static final Color ANNOTATION_VISITED = Color.blue;

    public MyAnnotationCallback(DocumentViewController documentViewController) {
        this.documentViewController = documentViewController;
        // annotations click on history
        annotationHistory = new WeakHashMap<String, AnnotationState>();
    }

    /**
     * <p>Implemented Annotation Callback method.  When an annotation is
     * activated in a PageViewComponent it passes the annotation to this method
     * for processing.  The PageViewComponent take care of drawing the
     * annotation states but it up to this method to process the
     * annotation.</p>
     *
     * @param annotation annotation that was activated by a user via the
     *                   PageViewComponent.
     */
    public void processAnnotationAction(Annotation annotation) {
        if (logger.isLoggable(Level.INFO)) {
            logger.info("Annotation " + annotation.toString());
            if (annotation.getAction() != null) {
                logger.info("   Action: " + annotation.getAction().toString());
            }
        }

        if (annotation instanceof LinkAnnotation) {
            LinkAnnotation linkAnnotation = (LinkAnnotation) annotation;
            // look for an A entry,
            if (linkAnnotation.getAction() != null) {
                Action action =
                        linkAnnotation.getAction();
                // do instance of check to process actions correctly.
                if (action instanceof GoToAction &&
                        documentViewController != null) {
                    documentViewController.setDestinationTarget(
                            ((GoToAction) action).getDestination());
                } else if (action instanceof URIAction) {
                    BareBonesBrowserLaunch.openURL(
                            ((URIAction) action).getURI());
                } else if (action instanceof GoToRAction) {

                } else if (action instanceof LaunchAction) {

                }

            }
            // look for a Destination entry, only present if an action is not found
            else if (linkAnnotation.getDestination() != null &&
                    documentViewController != null) {
                // use this controller to navigate to the correct page
                documentViewController.setDestinationTarget(
                        linkAnnotation.getDestination());
            }
        }
        // catch any other annotation types and try and process their action.
        else {
            // look for the destination entry and navigate to it.
            if (annotation.getAction() != null) {
                Action action = annotation.getAction();
                if (action instanceof GoToAction) {
                    documentViewController.setDestinationTarget(
                            ((GoToAction) action).getDestination());
                } else if (action instanceof URIAction) {
                    BareBonesBrowserLaunch.openURL(
                            ((URIAction) action).getURI());
                }
            }
        }

        /**
         * Mark the annotation as visited.
         */
        // get our history cache reference if any
        AnnotationState annotationState =
                annotationHistory.get(annotation.toString());
        // if the weak reference is gone we need to put back a default state.
        if (annotationState != null) {
            annotationState.setColor(ANNOTATION_VISITED);
            // line width can also be set.
//            annotation.getBorderStyle().setStrokeWidth(1.0f);
            annotation.setColor(annotationState.getColor());
        }
        // if the annotation is in the cache then we change its appearance to
        // a visited state.
        else {
            annotation.setColor(ANNOTATION_VISITED);
            annotationHistory.put(annotation.toString(),
                    new AnnotationState(annotation.getColor(),
                            annotation.getBorderStyle()));
        }
    }

    /**
     * New annotation created with view tool.
     *
     * @param page page that annotation was added to.
     */
    public void newAnnotation(PageViewComponent page, AnnotationComponent annotation) {

    }

    public void updateAnnotation(AnnotationComponent annotationComponent) {

    }

    public void removeAnnotation(PageViewComponent pageComponent,
                                 AnnotationComponent annotationComponent) {

    }

    /**
     * <p>Implemented Annotation Callback method.  This method is called when a
     * pages annotations been initialized but before the page has been painted.
     * This method blocks the </p>
     *
     * @param page page that has been initialized.  The pages annotations are
     *             available via an accessor method.
     */
    public void pageAnnotationsInitialized(Page page) {

        List<Annotation> annotations = page.getAnnotations();
        // no annotation, no problem just return.
        if (annotations == null || annotations.size() == 0) {
            return;
        }
        // otherwise we loop though the annotation and add our default border
        Annotation annotation;
        for (int i = 0, max = annotations.size(); i < max; i++) {
            processNullAnnotationDecoration(annotations.get(i));
        }
    }

    /**
     * Utility method for changing the default state of a annotation border.
     * There is no guarantee that an annotation will actually have a border.  For
     * this example we add a default border and colour to annotations that
     * have a null border style.  If the annotation has a border we just store
     * the current state in the annotation history map.
     *
     * @param annotation annotation to decorate.
     */
    private void processNullAnnotationDecoration(Annotation annotation) {
        AnnotationState annotationState =
                annotationHistory.get(annotation.toString());
        BorderStyle borderStyle;
        // apply default style, freshly initialized page.
        if (annotationState == null) {
            // if there is a border already we paint it as it.
            if (annotation.getBorderStyle() != null) {
                annotationHistory.put(annotation.toString(),
                        new AnnotationState(annotation.getColor(),
                                annotation.getBorderStyle()));
            }
            // if no border we add our own custom border.
            else {
                borderStyle = new BorderStyle();
                // set default paint styles
                borderStyle.setBorderStyle(BorderStyle.BORDER_STYLE_DASHED);
                annotation.setBorderStyle(borderStyle);
                if (annotation.getColor() == null) {
                    annotation.setColor(ANNOTATION);
                }
                // add the state to the hash.
                annotationHistory.put(annotation.toString(),
                        new AnnotationState(
                                annotation.getColor(), borderStyle));
            }

        }
        // If the annotationState is not null then we are dealing with a page
        // that has been disposed of due to memory constraints.  In this case
        // we apply the previous border style and color.
        else {
            annotation.setBorderStyle(annotationState.getBorderStyle());
            annotation.setColor(annotationState.getColor());
        }
    }

    /**
     * Helper class for storing visited state information for annotations.
     */
    protected class AnnotationState {
        private Color color;
        private BorderStyle borderStyle;

        public AnnotationState(Color color, BorderStyle borderStyle) {
            this.color = color;
            this.borderStyle = borderStyle;
        }

        public Color getColor() {
            return color;
        }

        public void setColor(Color color) {
            this.color = color;
        }

        public BorderStyle getBorderStyle() {
            return borderStyle;
        }

        public void setBorderStyle(BorderStyle borderStyle) {
            this.borderStyle = borderStyle;
        }
    }
}
