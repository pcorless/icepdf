/*
 * Copyright 2006-2012 ICEsoft Technologies Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an "AS
 * IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either * express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */
package org.icepdf.ri.common;

import org.icepdf.core.AnnotationCallback;
import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.Page;
import org.icepdf.core.pobjects.PageTree;
import org.icepdf.core.pobjects.actions.*;
import org.icepdf.core.pobjects.annotations.Annotation;
import org.icepdf.core.pobjects.annotations.AnnotationState;
import org.icepdf.core.pobjects.annotations.LinkAnnotation;
import org.icepdf.core.views.AnnotationComponent;
import org.icepdf.core.views.DocumentViewController;
import org.icepdf.core.views.DocumentViewModel;
import org.icepdf.core.views.PageViewComponent;
import org.icepdf.ri.common.views.annotations.AbstractAnnotationComponent;
import org.icepdf.ri.util.BareBonesBrowserLaunch;

import java.io.File;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * This class represents a basic implemenation of the AnnotationCallback
 *
 * @author ICEsoft Technologies, Inc.
 * @since 2.6
 */
public class MyAnnotationCallback implements AnnotationCallback {

    private static final Logger logger =
            Logger.getLogger(MyAnnotationCallback.class.toString());

    private DocumentViewController documentViewController;


    public MyAnnotationCallback(DocumentViewController documentViewController) {
        this.documentViewController = documentViewController;
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
        if (logger.isLoggable(Level.FINE)) {
            logger.fine("Annotation " + annotation.toString());
            if (annotation.getAction() != null) {
                logger.fine("   Action: " + annotation.getAction().toString());
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
                    LaunchAction launchAction = (LaunchAction) action;
                    String file = launchAction.getExternalFile();
                    String location =
                            documentViewController.getDocument().getDocumentLocation();
                    location = location.substring(0, location.lastIndexOf(File.separator) + 1);
                    BareBonesBrowserLaunch.openFile(location + file);
                }

            }
            // look for a Dest entry, only present if an action is not found
            // or vise versa.
            else if (linkAnnotation.getDestination() != null &&
                    documentViewController != null) {
                // use this controller to navigate to the correct page
                documentViewController.setDestinationTarget(
                        linkAnnotation.getDestination());
            }

        }
        // catch any other annotation types and try and process their action.
        else {
            // look for the dest entry and navigate to it.
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

    }

    /**
     * New annotation created with view tool.
     *
     * @param pageComponent       page that annotation was added to.
     * @param annotationComponent component that will be created.
     */
    public void newAnnotation(PageViewComponent pageComponent,
                              AnnotationComponent annotationComponent) {
        // do a bunch a work to get at the page object.
        Document document = documentViewController.getDocument();
        PageTree pageTree = document.getPageTree();
        Page page = pageTree.getPage(pageComponent.getPageIndex());
        page.addAnnotation(annotationComponent.getAnnotation());

        // no we have let the pageComponent now about it.
        pageComponent.addAnnotation((AbstractAnnotationComponent) annotationComponent);

        // create new state for memento and apply/restore to save state to
        // document data structures. 
        AnnotationState newAnnotationState = new AnnotationState(annotationComponent);
        // saves the state changes back to the document structure.
        newAnnotationState.apply(newAnnotationState);
        newAnnotationState.restore();

        // finally change the current tool to the annotation selection
        documentViewController.getParentController().setDocumentToolMode(
                DocumentViewModel.DISPLAY_TOOL_SELECTION);
    }
}
