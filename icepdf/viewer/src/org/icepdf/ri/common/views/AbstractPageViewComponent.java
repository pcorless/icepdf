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
package org.icepdf.ri.common.views;

import org.icepdf.core.pobjects.Page;
import org.icepdf.core.pobjects.annotations.Annotation;
import org.icepdf.ri.common.tools.*;
import org.icepdf.ri.common.views.annotations.AbstractAnnotationComponent;
import org.icepdf.ri.common.views.annotations.AnnotationComponentFactory;
import org.icepdf.ri.common.views.annotations.PopupAnnotationComponent;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Abstract PageViewComponent.
 */
public abstract class AbstractPageViewComponent
        extends JLayeredPane
        implements PageViewComponent {

    protected DocumentView parentDocumentView;
    protected DocumentViewModel documentViewModel;
    protected DocumentViewController documentViewController;

    // currently selected tool
    protected ToolHandler currentToolHandler;

    // annotations component for this pageViewComp.
    protected ArrayList<AnnotationComponent> annotationComponents;

    public abstract Page getPage();

    /**
     * Sets the tool mode for the current page component implementation.  When
     * a tool mode is assigned the respective tool handler is registered and
     * various event listeners are registered.
     *
     * @param viewToolMode view tool modes as defined in
     *                     DocumentViewMode.DISPLAY_TOOL_*
     */
    public void setToolMode(final int viewToolMode) {
        if (currentToolHandler != null) {
            currentToolHandler.uninstallTool();
            removeMouseListener(currentToolHandler);
            removeMouseMotionListener(currentToolHandler);
        }
        // assign the correct tool handler
        switch (viewToolMode) {
            case DocumentViewModel.DISPLAY_TOOL_ZOOM_IN:
                currentToolHandler = new ZoomInPageHandler(
                        documentViewController,
                        this,
                        documentViewModel);
                break;
            case DocumentViewModel.DISPLAY_TOOL_TEXT_SELECTION:
                currentToolHandler = new TextSelectionPageHandler(
                        documentViewController,
                        this,
                        documentViewModel);
                break;
            case DocumentViewModel.DISPLAY_TOOL_SELECTION:
                // no handler is needed for selection as it is handle by
                // each annotation.
                currentToolHandler = new AnnotationSelectionHandler(
                        documentViewController,
                        this,
                        documentViewModel);
                documentViewController.clearSelectedText();
                break;
            case DocumentViewModel.DISPLAY_TOOL_LINK_ANNOTATION:
                // handler is responsible for the initial creation of the annotation
                currentToolHandler = new LinkAnnotationHandler(
                        documentViewController,
                        this,
                        documentViewModel);
                documentViewController.clearSelectedText();
                break;
            case DocumentViewModel.DISPLAY_TOOL_HIGHLIGHT_ANNOTATION:
                // handler is responsible for the initial creation of the annotation
                currentToolHandler = new HighLightAnnotationHandler(
                        documentViewController,
                        this,
                        documentViewModel);
                ((HighLightAnnotationHandler) currentToolHandler).createTextMarkupAnnotation(null);
                documentViewController.clearSelectedText();
                break;
            case DocumentViewModel.DISPLAY_TOOL_STRIKEOUT_ANNOTATION:
                currentToolHandler = new StrikeOutAnnotationHandler(
                        documentViewController,
                        this,
                        documentViewModel);
                ((StrikeOutAnnotationHandler) currentToolHandler).createTextMarkupAnnotation(null);
                documentViewController.clearSelectedText();
                break;
            case DocumentViewModel.DISPLAY_TOOL_UNDERLINE_ANNOTATION:
                currentToolHandler = new UnderLineAnnotationHandler(
                        documentViewController,
                        this,
                        documentViewModel);
                ((UnderLineAnnotationHandler) currentToolHandler).createTextMarkupAnnotation(null);
                documentViewController.clearSelectedText();
                break;
            case DocumentViewModel.DISPLAY_TOOL_LINE_ANNOTATION:
                currentToolHandler = new LineAnnotationHandler(
                        documentViewController,
                        this,
                        documentViewModel);
                documentViewController.clearSelectedText();
                break;
            case DocumentViewModel.DISPLAY_TOOL_LINE_ARROW_ANNOTATION:
                currentToolHandler = new LineArrowAnnotationHandler(
                        documentViewController,
                        this,
                        documentViewModel);
                documentViewController.clearSelectedText();
                break;
            case DocumentViewModel.DISPLAY_TOOL_SQUARE_ANNOTATION:
                currentToolHandler = new SquareAnnotationHandler(
                        documentViewController,
                        this,
                        documentViewModel);
                documentViewController.clearSelectedText();
                break;
            case DocumentViewModel.DISPLAY_TOOL_CIRCLE_ANNOTATION:
                currentToolHandler = new CircleAnnotationHandler(
                        documentViewController,
                        this,
                        documentViewModel);
                documentViewController.clearSelectedText();
                break;
            case DocumentViewModel.DISPLAY_TOOL_INK_ANNOTATION:
                currentToolHandler = new InkAnnotationHandler(
                        documentViewController,
                        this,
                        documentViewModel);
                documentViewController.clearSelectedText();
                break;
            case DocumentViewModel.DISPLAY_TOOL_FREE_TEXT_ANNOTATION:
                currentToolHandler = new FreeTextAnnotationHandler(
                        documentViewController,
                        this,
                        documentViewModel);
                documentViewController.clearSelectedText();
                break;
            case DocumentViewModel.DISPLAY_TOOL_TEXT_ANNOTATION:
                currentToolHandler = new TextAnnotationHandler(
                        documentViewController,
                        this,
                        documentViewModel);
                documentViewController.clearSelectedText();
                break;
            default:
                currentToolHandler = null;
        }
        if (currentToolHandler != null) {
            currentToolHandler.installTool();
            addMouseListener(currentToolHandler);
            addMouseMotionListener(currentToolHandler);
        }
    }

    public void refreshAnnotationComponents(Page page) {
        if (page != null) {
            List<Annotation> annotations = page.getAnnotations();
            if (annotations != null && annotations.size() > 0) {
                // we don't want to re-initialize the component as we'll
                // get duplicates if the page has be gc'd
                if (annotationComponents == null) {
                    annotationComponents =
                            new ArrayList<AnnotationComponent>(annotations.size());
                    for (Annotation annotation : annotations) {
                        AbstractAnnotationComponent comp =
                                AnnotationComponentFactory.buildAnnotationComponent(
                                        annotation, documentViewController,
                                        this, documentViewModel);
                        if (comp != null) {
                            // add for painting
                            annotationComponents.add(comp);
                            // add to layout
                            if (comp instanceof PopupAnnotationComponent) {
                                this.add(comp, JLayeredPane.POPUP_LAYER);
                            } else {
                                this.add(comp, JLayeredPane.DEFAULT_LAYER);
                            }
                        }else{
                            // have test file with null value here.
                            // System.out.println();
                        }
                    }
                }
            }
        }
    }

    public ArrayList<AnnotationComponent> getAnnotationComponents() {
        return annotationComponents;
    }

    public static boolean isAnnotationTool(final int displayTool) {
        return displayTool == DocumentViewModel.DISPLAY_TOOL_SELECTION ||
                displayTool == DocumentViewModel.DISPLAY_TOOL_LINK_ANNOTATION ||
                displayTool == DocumentViewModel.DISPLAY_TOOL_HIGHLIGHT_ANNOTATION ||
                displayTool == DocumentViewModel.DISPLAY_TOOL_SQUIGGLY_ANNOTATION ||
                displayTool == DocumentViewModel.DISPLAY_TOOL_STRIKEOUT_ANNOTATION ||
                displayTool == DocumentViewModel.DISPLAY_TOOL_UNDERLINE_ANNOTATION;
    }

}
