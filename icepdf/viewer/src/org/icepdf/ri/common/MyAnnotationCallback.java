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
package org.icepdf.ri.common;

import org.icepdf.core.AnnotationCallback;
import org.icepdf.core.pobjects.Page;
import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.PageTree;
import org.icepdf.core.pobjects.actions.*;
import org.icepdf.core.pobjects.annotations.Annotation;
import org.icepdf.core.pobjects.annotations.LinkAnnotation;
import org.icepdf.ri.util.BareBonesBrowserLaunch;
import org.icepdf.core.views.DocumentViewController;
import org.icepdf.core.views.PageViewComponent;
import org.icepdf.core.views.DocumentViewModel;

import java.util.logging.Logger;
import java.util.logging.Level;
import java.awt.*;

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
    public void proccessAnnotationAction(Annotation annotation) {
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
     * @param pageComponent page that annotation was added to.
     * @param rect annotation bounds
     */
    public void newAnnotation(PageViewComponent pageComponent, Rectangle rect){
        // do a bunch a owrk to get at the page object.
        Document document = documentViewController.getDocument();
        PageTree pageTree = document.getPageTree();
        Page page = pageTree.getPage(pageComponent.getPageIndex(), this);
        Annotation annotation = page.createAnnotation(rect, null);
        // no we have let the pageComponent now about it.
        pageComponent.addAnnotation(annotation);
        // release the page
        pageTree.releasePage(pageComponent.getPageIndex(), this);

        // finally change the current tool to the annotation selection
        documentViewController.getParentController().setDocumentToolMode(
                DocumentViewModel.DISPLAY_TOOL_SELECTION);
    }
}
