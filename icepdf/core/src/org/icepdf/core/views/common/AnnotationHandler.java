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
 * The Original Code is ICEpdf 4.1 open source software code, released
 * May 1st, 2009. The Initial Developer of the Original Code is ICEsoft
 * Technologies Canada, Corp. Portions created by ICEsoft are Copyright (C)
 * 2004-2010 ICEsoft Technologies Canada, Corp. All Rights Reserved.
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
package org.icepdf.core.views.common;

import org.icepdf.core.pobjects.Page;
import org.icepdf.core.pobjects.annotations.Annotation;
import org.icepdf.core.util.GraphicsRenderingHints;
import org.icepdf.core.views.DocumentViewController;
import org.icepdf.core.views.DocumentViewModel;
import org.icepdf.core.views.AnnotationComponent;
import org.icepdf.core.views.swing.AbstractPageViewComponent;
import org.icepdf.core.views.swing.AnnotationComponentImpl;

import javax.swing.event.MouseInputListener;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.util.ArrayList;
import java.util.logging.Logger;

/**
 * <p>This classes purpose is to manage annotation selected state and the
 * broadcaset of resized and moved for multiple selected components.  The
 * other purpose of this class is to handle the drawing of a selection box
 * and handle the creation of new link annotation when the link annotation
 * tool is selected </p>
 *
 * @since 4.0
 */
public class AnnotationHandler extends SelectionBoxHandler
        implements MouseInputListener {

    private static final Logger logger =
            Logger.getLogger(AnnotationHandler.class.toString());


    // parent page component
    private AbstractPageViewComponent pageViewComponent;
    private DocumentViewController documentViewController;
    private DocumentViewModel documentViewModel;

    // flag to indicate a drag had occured.
    private boolean isDragged;

    // annotations component for pageViewComp.
    private ArrayList<AnnotationComponent> annotations;

    // selected annotations.
    // todo: implement multiple select,  should probably go in documentViewModel
    // instead. 
    private ArrayList<AnnotationComponentImpl> selectedAnnotations;

    public AnnotationHandler(AbstractPageViewComponent pageViewComponent,
                             DocumentViewModel documentViewModel) {
        this.pageViewComponent = pageViewComponent;
        this.documentViewModel = documentViewModel;
        selectedAnnotations = new ArrayList<AnnotationComponentImpl>();
        selectionBoxColour = Color.GRAY;
    }

    /**
     * DocumentController callback
     *
     * @param documentViewController document controller.
     */
    public void setDocumentViewController(
            DocumentViewController documentViewController) {
        this.documentViewController = documentViewController;
    }

    /**
     * Initializes the annotation components given the annotations collections.
     *
     * @param annotations annotations to wrap with annotations components.
     */
    public void initializeAnnotationComponents(ArrayList<Annotation> annotations){

        if (this.annotations == null && annotations != null){
            this.annotations = new ArrayList<AnnotationComponent>(annotations.size());
            AnnotationComponentImpl comp;
            for (Annotation annotation : annotations) {
                if (!(annotation.getFlagInvisible() ||
                        annotation.getFlagHidden())) {
                    comp = new AnnotationComponentImpl(annotation,
                            documentViewController,
                            pageViewComponent, documentViewModel);
                    // add them to the container, using absolute positioning.
                    pageViewComponent.add(comp);
                    // add the comp reference locally so we have easier access
                    this.annotations.add(comp);
                }
            }
        }
    }

    /**
     * Wraps the specified annotaiton with a new Annotation component and adds
     * it to the PageViewComponent as a child.
     * @param annotation new annotation to add to PageView.
     */
    public AnnotationComponent addAnnotationComponent(Annotation annotation){
        // initialize annotations
        if (annotations == null){
            annotations = new ArrayList<AnnotationComponent>();
        }
        // make sure we don't add the following types.
        if (!(annotation.getFlagInvisible() ||
                annotation.getFlagHidden())) {
            AnnotationComponentImpl comp = new AnnotationComponentImpl(annotation,
                    documentViewController,
                    pageViewComponent, documentViewModel);
            // add them to the container, using absolute positioning.
            pageViewComponent.add(comp);
            // add the comp reference locally so we have easier access
            this.annotations.add(comp);
            // set the new annotation as the selected one.
            documentViewController.clearSelectedAnnotations();
            return comp;
        }
        return null;
    }

    /**
     * Removes the specified annotation from the page view.  The component
     * is actually set to invisible.  We need to keep the component around
     * so that it can be made visible on an undo, as each state var keeps a
     * reference to the component it is 
     * @param annotation annotation component to removed. 
     */
    public void removeAnnotationComponent(AnnotationComponent annotation){
        // initialize annotations
        if (annotations == null){
            return;
        }
        ((Component)annotation).setVisible(false);
        // set the new annotation as the selected one.
        documentViewController.assignSelectedAnnotation(null);
    }

    /**
     * Creates a new link annotation when the link annotation creation tool is
     * selected.  The bounds of the annotation are defined by the current
     * selection box that has none zero bounds.  If the two previous
     * conditions are met then the annotation callback is fired and an
     * width a new annotation object which can be updated by the end user
     * using either the api or UI tools.
     */
    public void createNewLinkAnnotation() {
        if (documentViewModel.getViewToolMode() ==
                DocumentViewModel.DISPLAY_TOOL_LINK_ANNOTATION) {
            if (documentViewController.getAnnotationCallback() != null) {
                // convert the drawn rectangle to page space.
                documentViewController.getAnnotationCallback()
                        .newAnnotation(pageViewComponent, rectToDraw);
            }
        }
    }

    /**
     * Adds an Annotation component to the list of selected.  The list
     * of selected annotations is used do batch resize and moved commands.
     *
     * @param annotationComponent component to add to list of selected annotations
     */
    public void addSelectedAnnotation(AnnotationComponentImpl annotationComponent) {
        selectedAnnotations.add(annotationComponent);
    }

    /**
     * Adds an Annotation component to the list of selected.  The list
     * of selected annotations is used do batch resize and moved commands.
     *
     * @param annotationComponent remove the specified annotation from the
     *                            selection list
     */
    public void removeSelectedAnnotation(AnnotationComponentImpl annotationComponent) {
        selectedAnnotations.remove(annotationComponent);
    }

    /**
     * Clears the slected list of AnnotationComponent,  PageViewComponent
     * focus should be called after this method is called to insure deselection
     * of all AnnotationComponents.
     */
    public void clearSelectedList() {
        selectedAnnotations.clear();
        selectedAnnotations.trimToSize();
    }

    /**
     * Determines if there are more then one selected component.  If there is
     * more then one component that steps should be made to do batch move and
     * resize propigation.
     *
     * @return true if there are more then one AnnotationComponents in a selected
     *         state
     */
    public boolean isMultipleSelect() {
        return selectedAnnotations.size() > 1;
    }

    /**
     * Moves all selected annotation components by the x,y translation.
     *
     * @param x x-axis offset to be applied to all selected annotation.
     * @param y y-axis offset to be applied to all selected annotation.
     */
    public void moveSelectedAnnotations(int x, int y) {
        // todo implement

        // considerations to make sure annotation is not outside of page bounds
    }

    /**
     * Resizes all selected annotation components by the width and height
     * values.
     *
     * @param width  width offset to be applied to all selected annotation.
     * @param height height offset to be applied to all selected annotation.
     */
    public void resizeSelectedAnnotations(int width, int height) {
        // todo implement

        // considerations to make sure annotation is not outside of page bounds
    }


    public void mouseClicked(MouseEvent e) {

    }

    public void mousePressed(MouseEvent e) {

        clearSelectedList();

        // annotation selection box.
        if (documentViewModel.getViewToolMode() ==
                DocumentViewModel.DISPLAY_TOOL_SELECTION||
            documentViewModel.getViewToolMode() ==
                DocumentViewModel.DISPLAY_TOOL_LINK_ANNOTATION) {
            int x = e.getX();
            int y = e.getY();
            currentRect = new Rectangle(x, y, 0, 0);
            updateDrawableRect(pageViewComponent.getWidth(),
                    pageViewComponent.getHeight());
            pageViewComponent.repaint();
        }

    }

    public void mouseDragged(MouseEvent e) {
        isDragged = true;
        if (documentViewModel.getViewToolMode() ==
                DocumentViewModel.DISPLAY_TOOL_SELECTION ||
            documentViewModel.getViewToolMode() ==
                DocumentViewModel.DISPLAY_TOOL_LINK_ANNOTATION) {

            // rectangle select tool
            updateSelectionSize(e, pageViewComponent);
        }
    }

    public void mouseReleased(MouseEvent e) {
        // update selection rectangle
        updateSelectionSize(e, pageViewComponent);
        if (documentViewModel.getViewToolMode() ==
                DocumentViewModel.DISPLAY_TOOL_SELECTION) {
            // clear the rectangle
            clearRectangle(pageViewComponent);
            pageViewComponent.repaint();
        }
        // link annotations tool.
        else if (documentViewModel.getViewToolMode() ==
                DocumentViewModel.DISPLAY_TOOL_LINK_ANNOTATION &&
                isDragged){
            createNewLinkAnnotation();
            // clear the rectangle
            clearRectangle(pageViewComponent);
            pageViewComponent.repaint();
        }

        isDragged = false;
    }

    public void mouseMoved(MouseEvent e) {

    }

    public void mouseEntered(MouseEvent e) {

    }

    public void mouseExited(MouseEvent e) {

    }

    /**
     * Paints all annotation content for a given page view.  If any annotation
     * properties are changed then this method must be called to repaint the
     * page annotations.
     * <p/>
     * todo: as a future enhancement it would be great if each Annotation
     * component did its own painting,  this would take a little more time
     * to figure out the correct coordinate space.
     *
     * @param g parent PageViewComponent graphics context to paint annotations
     *          to.
     */
    public void paintAnnotations(Graphics g) {
        Page currentPage = pageViewComponent.getPageLock(this);
        if (currentPage != null && currentPage.isInitiated()) {
            if (annotations != null) {
                Graphics2D gg2 = (Graphics2D) g;
                // save draw state.
                AffineTransform prePaintTransform = gg2.getTransform();
                Color oldColor = gg2.getColor();
                Stroke oldStroke = gg2.getStroke();
                // apply page transform.
                AffineTransform at = currentPage.getPageTransform(
                        documentViewModel.getPageBoundary(),
                        documentViewModel.getViewRotation(),
                        documentViewModel.getViewZoom());
                gg2.transform(at);
                // get current tool state, we don't want to draw the highlight
                // state if the selection tool is selected.
                boolean notSelectTool =
                        documentViewModel.getViewToolMode() !=
                                DocumentViewModel.DISPLAY_TOOL_SELECTION;

                // paint all annotations on top of the content buffer
                for (AnnotationComponent annotation : annotations) {
                    if (((Component)annotation).isVisible()){
                        annotation.getAnnotation().render(gg2,
                                GraphicsRenderingHints.SCREEN,
                                documentViewModel.getViewRotation(),
                                documentViewModel.getViewZoom(),
                                annotation.hasFocus() && notSelectTool);
                    }
                }
                // post paint clean up.
                gg2.setColor(oldColor);
                gg2.setStroke(oldStroke);
                gg2.setTransform(prePaintTransform);
            }
        }
        pageViewComponent.releasePageLock(currentPage, this);

        // paint new link annotation bound box.
        if (documentViewModel.getViewToolMode() ==
                DocumentViewModel.DISPLAY_TOOL_LINK_ANNOTATION){
            paintSelectionBox(g);
        }
    }

    /**
     private void annotationMouseMoveHandler(Page currentPage,
     Point mouseLocation) {

     if (currentPage != null &&
     currentPage.isInitiated() &&
     isInteractiveAnnotationsEnabled) {
     ArrayList<Annotation> annotations = currentPage.getAnnotations();
     if (annotations != null) {
     Annotation annotation;
     Object tmp;
     AffineTransform at = currentPage.getPageTransform(
     documentViewModel.getPageBoundary(),
     documentViewModel.getViewRotation(),
     documentViewModel.getViewZoom());

     try {
     at.inverseTransform(mouseLocation, mouseLocation);
     } catch (NoninvertibleTransformException e1) {
     e1.printStackTrace();
     }

     for (Object annotation1 : annotations) {
     tmp = annotation1;
     if (tmp instanceof Annotation) {
     annotation = (Annotation) tmp;
     // repaint an annotation.
     if (annotation.getUserSpaceRectangle().contains(
     mouseLocation.getX(), mouseLocation.getY())) {
     currentAnnotation = annotation;
     documentViewController.setViewCursor(DocumentViewController.CURSOR_HAND_ANNOTATION);
     //                            repaint(annotation.getUserSpaceRectangle().getBounds());
     pageViewComponent.repaint();
     break;
     } else {
     currentAnnotation = null;
     }
     }
     }
     if (currentAnnotation == null) {
     int toolMode = documentViewModel.getViewToolMode();
     if (toolMode == DocumentViewModel.DISPLAY_TOOL_PAN) {
     documentViewController.setViewCursor(DocumentViewController.CURSOR_HAND_OPEN);
     } else if (toolMode == DocumentViewModel.DISPLAY_TOOL_ZOOM_IN) {
     documentViewController.setViewCursor(DocumentViewController.CURSOR_ZOOM_IN);
     } else if (toolMode == DocumentViewModel.DISPLAY_TOOL_ZOOM_OUT) {
     documentViewController.setViewCursor(DocumentViewController.CURSOR_ZOOM_OUT);
     } else if (toolMode == DocumentViewModel.DISPLAY_TOOL_TEXT_SELECTION) {
     documentViewController.setViewCursor(DocumentViewController.CURSOR_SELECT);
     }
     pageViewComponent.repaint();
     }
     }
     }
     }
     */
}
