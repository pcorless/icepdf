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
package org.icepdf.ri.common.views;

import org.icepdf.core.pobjects.*;
import org.icepdf.core.pobjects.annotations.Annotation;
import org.icepdf.core.pobjects.annotations.ChoiceWidgetAnnotation;
import org.icepdf.core.pobjects.annotations.FreeTextAnnotation;
import org.icepdf.core.pobjects.annotations.TextWidgetAnnotation;
import org.icepdf.core.pobjects.graphics.Shapes;
import org.icepdf.core.pobjects.graphics.text.PageText;
import org.icepdf.core.search.DocumentSearchController;
import org.icepdf.core.util.GraphicsRenderingHints;
import org.icepdf.core.util.PropertyConstants;
import org.icepdf.ri.common.search.DocumentSearchControllerImpl;
import org.icepdf.ri.common.tools.*;
import org.icepdf.ri.common.utility.search.SearchHitComponent;
import org.icepdf.ri.common.views.annotations.*;
import org.icepdf.ri.common.views.destinations.DestinationComponent;

import javax.swing.*;
import java.awt.*;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.geom.AffineTransform;
import java.util.*;
import java.util.List;
import java.util.logging.Logger;

/**
 * @since 6.2 heavily modified behaviour for cpu and memory enhancements.
 */
@SuppressWarnings("serial")
public class PageViewComponentImpl extends AbstractPageViewComponent implements FocusListener {

    private static final Logger logger =
            Logger.getLogger(PageViewComponentImpl.class.getName());

    // currently selected tool
    protected ToolHandler currentToolHandler;

    // we always keep around a page selection tool, it's only called from the parent view
    // component, this allows for multiple page selection.
    protected final TextSelectionPageHandler textSelectionPageHandler;

    // annotations component for this pageViewComp.
    // All access to annotationComponents, annotationToComponent, destinationComponents and the
    // alreadyDisposing flag is guarded by annotationComponentsLock. This is a dedicated lock,
    // intentionally NOT the component monitor (synchronized(this)) — locking on a Swing component
    // contends with AWT internals (add/remove/layout) and risks deadlock with the EDT.
    protected final Object annotationComponentsLock = new Object();
    protected ArrayList<AbstractAnnotationComponent> annotationComponents;
    protected Map<Reference, AnnotationComponent> annotationToComponent;
    protected ArrayList<DestinationComponent> destinationComponents;
    // popup -> glue lookup, mirrors the MarkupGlueComponents tracked in the documentViewModel so
    // getGlue() is O(1) instead of a linear scan (avoids O(n^2) during page init).
    protected final Map<PopupAnnotationComponent, MarkupGlueComponent> popupToGlue = new HashMap<>();
    private Set<SearchHitComponent> searchHitComponents = new HashSet<>();
    private boolean alreadyDisposing = false;

    public PageViewComponentImpl(DocumentViewModel documentViewModel, PageTree pageTree,
                                 final int pageIndex, int width, int height) {
        super(documentViewModel, pageTree, pageIndex, width, height);
        setFocusable(true);
        addFocusListener(this);
        // text selection handler
        textSelectionPageHandler = new TextSelectionPageHandler(documentViewController, this);
        // fully dynamic view, so we need to make sure we don't paint annotations to the buffer.
        paintAnnotations = false;
    }

    public void setDocumentViewCallback(DocumentView parentDocumentView) {
        super.setDocumentViewCallback(parentDocumentView);
        textSelectionPageHandler.setDocumentViewController(documentViewController);
    }

    public void clearSearchHighlights() {
        searchHitComponents.forEach(this::remove);
        searchHitComponents.clear();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        Graphics2D g2d = (Graphics2D) g.create(0, 0, pageSize.width, pageSize.height);
        GraphicsRenderingHints grh = GraphicsRenderingHints.getDefault();
        g2d.setRenderingHints(grh.getRenderingHints(GraphicsRenderingHints.SCREEN));

        // paint the annotation components.
        paintAnnotationComponents(g2d);
        // paint selected and highlighted text.
        paintTextSelection(g2d);
        // paint destinations, if any
        paintDestinations(g2d);

        // paint annotation handler effect if any.
        if (currentToolHandler != null) {
            currentToolHandler.paintTool(g2d);
        }
        if (documentViewModel.getViewToolMode() == DocumentViewModel.DISPLAY_TOOL_TEXT_SELECTION) {
            textSelectionPageHandler.paintTool(g2d);
        }
        g2d.dispose();
    }

    public void dispose() {
        synchronized (annotationComponentsLock) {
            alreadyDisposing = true;
        }
        if (pageImageCaptureTask != null && !pageImageCaptureTask.isDone()) {
            pageImageCaptureTask.cancel(true);
        }
        // remove annotation listeners.
        removeMouseMotionListener(currentToolHandler);
        removeMouseListener(currentToolHandler);
        // remove focus listener
        removeFocusListener(this);
        // dispose annotations components
        if (annotationComponents != null) {
            synchronized (annotationComponentsLock) {
                for (AbstractAnnotationComponent annotationComponent : annotationComponents) {
                    annotationComponent.dispose();
                }
            }
        }
    }

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
            currentToolHandler = null;
        }
        // assign the correct tool handler
        switch (viewToolMode) {
            case DocumentViewModel.DISPLAY_TOOL_ZOOM_IN:
                currentToolHandler = new ZoomInPageHandler(
                        documentViewController,
                        this);
                break;
            case DocumentViewModel.DISPLAY_TOOL_SELECTION:
                // no handler is needed for selection as it is handled by each annotation.
                currentToolHandler = new AnnotationSelectionHandler(
                        documentViewController, this);
                documentViewController.clearSelectedText();
                break;
            case DocumentViewModel.DISPLAY_TOOL_LINK_ANNOTATION:
                currentToolHandler = new LinkAnnotationHandler(
                        documentViewController, this);
                documentViewController.clearSelectedText();
                break;
            case DocumentViewModel.DISPLAY_TOOL_HIGHLIGHT_ANNOTATION:
                currentToolHandler = new HighLightAnnotationHandler(
                        documentViewController, this);
                documentViewController.clearSelectedText();
                break;
            case DocumentViewModel.DISPLAY_TOOL_REDACTION_ANNOTATION:
                currentToolHandler = new RedactionAnnotationHandler(
                        documentViewController, this);
                documentViewController.clearSelectedText();
                break;
            case DocumentViewModel.DISPLAY_TOOL_SIGNATURE_ANNOTATION:
                currentToolHandler = new SignatureAnnotationHandler(
                        documentViewController, this);
                documentViewController.clearSelectedText();
                break;
            case DocumentViewModel.DISPLAY_TOOL_STRIKEOUT_ANNOTATION:
                currentToolHandler = new StrikeOutAnnotationHandler(
                        documentViewController, this);
                documentViewController.clearSelectedText();
                break;
            case DocumentViewModel.DISPLAY_TOOL_UNDERLINE_ANNOTATION:
                currentToolHandler = new UnderLineAnnotationHandler(
                        documentViewController, this);
                documentViewController.clearSelectedText();
                break;
            case DocumentViewModel.DISPLAY_TOOL_LINE_ANNOTATION:
                currentToolHandler = new LineAnnotationHandler(
                        documentViewController, this);
                documentViewController.clearSelectedText();
                break;
            case DocumentViewModel.DISPLAY_TOOL_LINE_ARROW_ANNOTATION:
                currentToolHandler = new LineArrowAnnotationHandler(
                        documentViewController, this);
                documentViewController.clearSelectedText();
                break;
            case DocumentViewModel.DISPLAY_TOOL_SQUARE_ANNOTATION:
                currentToolHandler = new SquareAnnotationHandler(
                        documentViewController, this);
                documentViewController.clearSelectedText();
                break;
            case DocumentViewModel.DISPLAY_TOOL_CIRCLE_ANNOTATION:
                currentToolHandler = new CircleAnnotationHandler(
                        documentViewController, this);
                documentViewController.clearSelectedText();
                break;
            case DocumentViewModel.DISPLAY_TOOL_INK_ANNOTATION:
                currentToolHandler = new InkAnnotationHandler(
                        documentViewController,
                        this);
                documentViewController.clearSelectedText();
                break;
            case DocumentViewModel.DISPLAY_TOOL_FREE_TEXT_ANNOTATION:
                currentToolHandler = new FreeTextAnnotationHandler(
                        documentViewController, this);
                documentViewController.clearSelectedText();
                break;
            case DocumentViewModel.DISPLAY_TOOL_TEXT_ANNOTATION:
                currentToolHandler = new TextAnnotationHandler(
                        documentViewController, this);
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

    /**
     * Gets a list of the annotation components used in this page view.
     *
     * @return list of annotation components, can be null.
     */
    public ArrayList<AbstractAnnotationComponent> getAnnotationComponents() {
        synchronized (annotationComponentsLock) {
            return annotationComponents;
        }
    }

    /**
     * Returns the annotation component linked to the given annotation.
     * <p>
     * If this page's annotation components have not been built yet, the first call lazily builds
     * them all (an O(n) construction of Swing components) so the lookup can succeed; subsequent
     * calls are a plain map lookup. Because that build creates Swing components it only runs on the
     * EDT &mdash; if this is called off the EDT before the components exist it performs a pure lookup
     * and may return null rather than building Swing components on a worker thread.
     *
     * @param annot The annotation
     * @return The annotation component, or null if there is no match
     */
    public AnnotationComponent getComponentFor(Annotation annot) {
        synchronized (annotationComponentsLock) {
            if (annotationToComponent == null && SwingUtilities.isEventDispatchThread()) {
                // initializeAnnotationsComponent re-acquires annotationComponentsLock (reentrant).
                initializeAnnotationsComponent(getPage());
            }
            if (annotationToComponent != null) {
                return annotationToComponent.get(annot.getPObjectReference());
            }
            return null;
        }
    }

    /**
     * Gets a list of the annotation components used in this page view.
     *
     * @return list of annotation components, can be null.
     */
    public ArrayList<DestinationComponent> getDestinationComponents() {
        synchronized (annotationComponentsLock) {
            return destinationComponents;
        }
    }

    /**
     * Gets the page components TextSelectionPageHandler.  Each page has one and it directly accessed by the
     * TextSelectionViewHandler.  All other tools are created/disposed as the tools are selected.
     *
     * @return page's instance of the text selection handler.
     */
    public TextSelectionPageHandler getTextSelectionPageHandler() {
        return textSelectionPageHandler;
    }

    public ToolHandler getCurrentToolHandler() {
        return currentToolHandler;
    }

    private void paintTextSelection(Graphics g) {
        // Lazy paint of highlight and select all text states.
        Page currentPage = getPage();
        // paint any highlighted words
        DocumentSearchController searchController =
                documentViewController.getParentController().getDocumentSearchController();
        if (currentPage != null && currentPage.isInitiated() &&
                // make sure we don't accidentally block the awt ui thread, but we still
                // want to paint search text and text selection if text selection tool is selected.
                (searchController.isSearchHighlightRefreshNeeded(pageIndex, null) ||
                        documentViewModel.isViewToolModeSelected(DocumentViewModel.DISPLAY_TOOL_TEXT_SELECTION) ||
                        documentViewModel.isViewToolModeSelected(DocumentViewModel.DISPLAY_TOOL_HIGHLIGHT_ANNOTATION) ||
                        documentViewModel.isViewToolModeSelected(DocumentViewModel.DISPLAY_TOOL_STRIKEOUT_ANNOTATION) ||
                        documentViewModel.isViewToolModeSelected(DocumentViewModel.DISPLAY_TOOL_UNDERLINE_ANNOTATION) ||
                        documentViewModel.isViewToolModeSelected(DocumentViewModel.DISPLAY_TOOL_REDACTION_ANNOTATION))
        ) {
            try {
                // Use the non-initializing accessor. The isInitiated() guard above means the page
                // text is normally already built, but the page can be flushed between that check and
                // here; getViewText() would then run a synchronous init() parse on the EDT, whereas
                // getShapes() never triggers init(). If shapes are gone we simply skip this paint.
                Shapes shapes = currentPage.getShapes();
                PageText pageText = shapes != null ? shapes.getPageText() : null;
                if (pageText != null) {
                    // paint any highlighted words
                    if (searchController.isSearchHighlightRefreshNeeded(pageIndex, pageText)) {
                        searchController.searchHighlightPage(pageIndex);
                    }
                    // if select all we'll want to paint the selected text.
                    if (documentViewModel.isSelectAll()) {
                        documentViewModel.addSelectedPageText(this);
                        pageText.selectAll();
                    }
                    // paint selected text.
                    TextSelection.paintSelectedText(g, this, documentViewModel);
                    if (searchController instanceof DocumentSearchControllerImpl) {
                        Set<SearchHitComponent> newSearchHitComponents = ((DocumentSearchControllerImpl) searchController).getComponentsFor(pageIndex);
                        if (!newSearchHitComponents.equals(searchHitComponents)) {
                            searchHitComponents.forEach(this::remove);
                            searchHitComponents = newSearchHitComponents;
                            //In front of annotations, behind popups
                            searchHitComponents.forEach(comp -> {
                                this.setLayer(comp, JLayeredPane.MODAL_LAYER);
                                this.add(comp);
                            });
                            validate();
                        }
                    }
                }
            } catch (InterruptedException e) {
                logger.fine("Interrupt exception during view text fetch.");
            }
        } else if (currentPage != null && !currentPage.isInitiated()) {
            // there is good chance a page has been disposed on a large document, but if we have search hit we need
            // to repaint the page, setting the buffer to dirty will reinitialize the page on the next paint cycle.
            if (searchController.isSearchHighlightRefreshNeeded(pageIndex, null)) {
                pageBufferStore.setDirty(true);
            }
        }
    }

    private void paintAnnotationComponents(Graphics g) {
        Page currentPage = getPage();
        if (currentPage != null && annotationComponents != null) {
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
            AnnotationComponent annotation;
            synchronized (annotationComponentsLock) {
                for (AbstractAnnotationComponent annotationComponent : annotationComponents) {
                    annotation = annotationComponent;
                    if (annotation != null && ((Component) annotation).isVisible() &&
                            !((annotation.getAnnotation() instanceof FreeTextAnnotation)
                                    && annotation.isActive()) &&
                            !(annotation.getAnnotation() instanceof TextWidgetAnnotation
                                    && annotation.isActive()) &&
                            !(annotation.getAnnotation() instanceof ChoiceWidgetAnnotation
                                    && annotation.isActive())) {
                        annotation.getAnnotation().render(gg2,
                                GraphicsRenderingHints.SCREEN,
                                documentViewModel.getViewRotation(),
                                documentViewModel.getViewZoom(),
                                annotation.hasFocus() && notSelectTool);
                    }
                }
            }
            // post paint clean up.
            gg2.setColor(oldColor);
            gg2.setStroke(oldStroke);
            gg2.setTransform(prePaintTransform);
        }
    }

    private void paintDestinations(Graphics g) {
        Page currentPage = getPage();
        if (currentPage != null &&
                documentViewController.getParentController().getViewModel().isAnnotationEditingMode()) {

            // make sure we have a name tree to try and paint
            if (destinationComponents != null && destinationComponents.size() > 0) {
                Graphics2D g2d = (Graphics2D) g;
                // save draw state.
                AffineTransform prePaintTransform = g2d.getTransform();
                Color oldColor = g2d.getColor();
                Stroke oldStroke = g2d.getStroke();
                // apply page transform.
                AffineTransform at = currentPage.getPageTransform(
                        documentViewModel.getPageBoundary(),
                        documentViewModel.getViewRotation(),
                        documentViewModel.getViewZoom());
                g2d.transform(at);
                g2d.setStroke(new BasicStroke(1f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_MITER));
                DestinationComponent destinationComponent;
                Destination dest;
                synchronized (annotationComponentsLock) {
                    for (DestinationComponent component : destinationComponents) {
                        destinationComponent = component;
                        dest = destinationComponent.getDestination();
                        if (dest.getLeft() != null && dest.getTop() != null) {
                            DestinationComponent.paintDestination(dest, g2d);
                        }
                    }
                }
                // post paint clean up.
                g2d.setColor(oldColor);
                g2d.setStroke(oldStroke);
                g2d.setTransform(prePaintTransform);
            }
        }
    }

    public void focusGained(FocusEvent e) {
        int oldCurrentPage = documentViewModel.getViewCurrentPageIndex();
        documentViewModel.setViewCurrentPageIndex(pageIndex);
        documentViewController.firePropertyChange(PropertyConstants.DOCUMENT_CURRENT_PAGE,
                oldCurrentPage,
                pageIndex);
    }

    public void focusLost(FocusEvent e) {
    }

    public void updateView(String propertyConstant, Object oldValue, Object newValue) {
        super.updateView(propertyConstant, oldValue, newValue);
        // revalidate the annotation components.
        if (PropertyConstants.DOCUMENT_VIEW_ROTATION_CHANGE.equals(propertyConstant) ||
                PropertyConstants.DOCUMENT_VIEW_ZOOM_CHANGE.equals(propertyConstant)) {
            if (annotationComponents != null) {
                synchronized (annotationComponentsLock) {
                    for (AbstractAnnotationComponent comp : annotationComponents) {
                        comp.validate();
                    }
                }
            }
            if (destinationComponents != null) {
                synchronized (annotationComponentsLock) {
                    for (DestinationComponent comp : destinationComponents) {
                        comp.validate();
                    }
                }
            }
        }
    }

    /**
     * Add a new annotation object to this page view component.
     *
     * @param annotation annotation to add.
     */
    public void addAnnotation(AnnotationComponent annotation) {
        // delegate to handler.
        synchronized (annotationComponentsLock) {
            if (annotationComponents == null) {
                annotationComponents = new ArrayList<>();
                annotationToComponent = new HashMap<>();
            }
            annotationComponents.add((AbstractAnnotationComponent) annotation);
            annotationToComponent.put(annotation.getAnnotation().getPObjectReference(), annotation);
            if (annotation instanceof PopupAnnotationComponent) {
                final PopupAnnotationComponent popupAnnotationComponent = (PopupAnnotationComponent) annotation;
                addPopupAnnotationComponent(popupAnnotationComponent);
                addPopupAnnotationComponentGlue(popupAnnotationComponent.getMarkupAnnotationComponent(), popupAnnotationComponent);
            } else if (annotation instanceof MarkupAnnotationComponent) {
                MarkupAnnotationComponent markupAnnotationComponent = (MarkupAnnotationComponent) annotation;
                PopupAnnotationComponent popupAnnotationComponent = markupAnnotationComponent.getPopupAnnotationComponent();
                addPopupAnnotationComponentGlue(markupAnnotationComponent, popupAnnotationComponent);
                this.setLayer((AbstractAnnotationComponent) annotation, JLayeredPane.PALETTE_LAYER);
                this.add((AbstractAnnotationComponent) annotation);
            } else {
                this.setLayer((AbstractAnnotationComponent) annotation, JLayeredPane.PALETTE_LAYER);
                this.add((AbstractAnnotationComponent) annotation);
            }
        }
    }

    /**
     * Remove the specified annotation from this page view.
     *
     * @param annotationComp annotation to be removed.
     */
    public void removeAnnotation(AnnotationComponent annotationComp) {
        synchronized (annotationComponentsLock) {
            annotationComponents.remove(annotationComp);
            if (annotationComp.getAnnotation() != null) {
                annotationToComponent.remove(annotationComp.getAnnotation().getPObjectReference());
            } else {
                annotationToComponent.entrySet().stream().filter(e -> e.getValue().equals(annotationComp)).findFirst()
                        .ifPresent(e -> annotationToComponent.remove(e.getKey()));
            }
            if (annotationComp instanceof PopupAnnotationComponent) {
                removePopupAnnotationComponent((PopupAnnotationComponent) annotationComp);
            } else {
                this.remove((AbstractAnnotationComponent) annotationComp);
            }
        }
    }

    public void removeDestination(DestinationComponent destinationComponent) {
        synchronized (annotationComponentsLock) {
            destinationComponents.remove(destinationComponent);
            this.remove(destinationComponent);
        }
    }


    public void pageInitializedCallback(Page page) {
        refreshAnnotationComponents(page);
        refreshDestinationComponents(page);
    }

    public void pageTeardownCallback() {
        SwingUtilities.invokeLater(() -> {
            // remove popups from layout, so we can cleanly re-initialize if viewed again.
            ArrayList<PageViewAnnotationComponent> components = documentViewModel.getDocumentViewAnnotationComponents(this);
            if (components != null) {
                for (PageViewAnnotationComponent component : components) {
                    parentDocumentView.remove((JComponent) component);
                    if (component instanceof MarkupGlueComponent) {
                        ((MarkupGlueComponent)component).dispose();
                    }
                }
            }
            documentViewModel.removeAllFloatingAnnotationComponent(this);

            synchronized (annotationComponentsLock) {
                annotationComponents = null;
                annotationToComponent = null;
                popupToGlue.clear();
            }
        });
    }

    public void refreshDestinationComponents(Page page) {
        refreshDestinationComponents(page, true);
    }

    public void refreshDestinationComponents(Page page, boolean invokeLater) {
        if (page != null) {
            if (invokeLater) {
                final Page finalPage = page;
                SwingUtilities.invokeLater(() -> initializeDestinationComponents(finalPage));
            } else {
                initializeDestinationComponents(page);
            }
        }
    }

    public void refreshAnnotationComponents(Page page) {
        refreshAnnotationComponents(page, true);
    }

    public void refreshAnnotationComponents(Page page, boolean invokeLater) {
        if (page != null) {
            if (invokeLater) {
                final Page finalPage = page;
                SwingUtilities.invokeLater(() -> initializeAnnotationsComponent(finalPage));
            } else {
                initializeAnnotationsComponent(page);
            }
        }
    }

    private void initializeAnnotationsComponent(Page page) {
        synchronized (annotationComponentsLock) {
            if (alreadyDisposing) {
                return;
            }
            initializeAnnotationsComponentInternal(page);
        }
    }

    private void initializeAnnotationsComponentInternal(Page page) {
        // check to make sure we have a page and document,  this method can be called from the page init callback
        // which is called from a worker thread so we need to be careful that the document hasn't been closed.
        if (documentViewController.getDocumentViewModel() == null) return;
        List<Annotation> annotations = page.getAnnotations();
        AbstractPageViewComponent parent = this;
        if (documentViewController.getAnnotationCallback() != null) {
            documentViewController.getAnnotationCallback().pageAnnotationsInitialized(page);
        }
        if (annotations != null && !annotations.isEmpty()) {
            // we don't want to re-initialize the component as we'll
            // get duplicates if the page has been gc'd
            if (annotationComponents == null) {
                annotationComponents = new ArrayList<>(annotations.size());
                annotationToComponent = new HashMap<>(annotations.size());
                Annotation annotation;
                // is possible that a Popup annotation is added to the page during initialization but the component
                // creation will be handled during that creation process.
                for (int i = 0; i < annotations.size(); i++) {
                    annotation = annotations.get(i);
                    // parser can sometimes return an empty array depending on the PDF syntax being used.
                    if (annotation != null && documentViewModel != null) {
                        final AbstractAnnotationComponent comp =
                                AnnotationComponentFactory.buildAnnotationComponent(
                                        annotation, documentViewController, parent);
                        if (comp != null) {
                            // add for painting
                            annotationComponents.add(comp);
                            annotationToComponent.put(annotation.getPObjectReference(), comp);
                            // add to layout
                            if (comp instanceof PopupAnnotationComponent) {
                                PopupAnnotationComponent popupAnnotationComponent = (PopupAnnotationComponent) comp;
                                // check if we have created the parent markup, if so add the glue
                                MarkupAnnotationComponent markupAnnotationComponent =
                                        popupAnnotationComponent.getMarkupAnnotationComponent();
                                if (markupAnnotationComponent != null) {
                                    addPopupAnnotationComponentGlue(markupAnnotationComponent, popupAnnotationComponent);
                                }
                                addPopupAnnotationComponent(popupAnnotationComponent);
                            } else if (comp instanceof MarkupAnnotationComponent) {
                                MarkupAnnotationComponent markupAnnotationComponent = (MarkupAnnotationComponent) comp;
                                // will create popup if not already created.
                                PopupAnnotationComponent popupAnnotationComponent =
                                        markupAnnotationComponent.getPopupAnnotationComponent();
                                // we may or may not have created the popup, if so we create the glue
                                if (popupAnnotationComponent != null) {
                                    addPopupAnnotationComponentGlue(markupAnnotationComponent, popupAnnotationComponent);
                                }
                                parent.setLayer(comp, JLayeredPane.PALETTE_LAYER);
                                parent.add(markupAnnotationComponent);
                            } else {
                                parent.setLayer(comp, JLayeredPane.PALETTE_LAYER);
                                parent.add(comp);
                            }
                            comp.revalidate();
                            comp.repaint();
                        }
                    }
                }
            }
        }
    }

    private void addPopupAnnotationComponent(PopupAnnotationComponent popupAnnotationComponent) {
        // assign parent so we can properly place the popup relative to its parent page.
        popupAnnotationComponent.setParentPageComponent(this);
        popupAnnotationComponent.refreshDirtyBounds();
        documentViewModel.addDocumentViewAnnotationComponent(this, popupAnnotationComponent);
        // won't show up on the right layer if layer isn't set first.
        ((JLayeredPane)parentDocumentView).setLayer(popupAnnotationComponent, JLayeredPane.POPUP_LAYER);
        parentDocumentView.add(popupAnnotationComponent);
    }

    private void addPopupAnnotationComponentGlue(MarkupAnnotationComponent markupAnnotationComponent,
                                                 PopupAnnotationComponent popupAnnotationComponent) {
        if (markupAnnotationComponent != null && popupAnnotationComponent != null && getGlue(popupAnnotationComponent) == null) {
            MarkupGlueComponent markupGlueComponent =
                    new MarkupGlueComponent(documentViewController,
                            markupAnnotationComponent, popupAnnotationComponent);
            // assign parent so we can properly place the popup relative to its parent page.
            markupGlueComponent.setParentPageComponent(this);
            markupGlueComponent.refreshDirtyBounds();
            // won't show up on the right layer if layer isn't set first.
            documentViewModel.addDocumentViewAnnotationComponent(this, markupGlueComponent);
            ((JLayeredPane) parentDocumentView).setLayer(markupGlueComponent, JLayeredPane.MODAL_LAYER);
            parentDocumentView.add(markupGlueComponent);
            popupToGlue.put(popupAnnotationComponent, markupGlueComponent);
        }
    }

    private void removePopupAnnotationComponent(PopupAnnotationComponent popupAnnotationComponent) {
        parentDocumentView.remove(popupAnnotationComponent);
        documentViewModel.removeDocumentViewAnnotationComponent(parentDocumentView, this, popupAnnotationComponent);
        //Don't forget to remove the glue
        final MarkupGlueComponent glue = popupToGlue.remove(popupAnnotationComponent);
        if (glue != null) {
            parentDocumentView.remove(glue);
            documentViewModel.removeDocumentViewAnnotationComponent(parentDocumentView, this, glue);
        }
    }

    private MarkupGlueComponent getGlue(final PopupAnnotationComponent popupAnnotationComponent) {
        return popupToGlue.get(popupAnnotationComponent);
    }

    private void initializeDestinationComponents(Page page) {
        // check to make sure we have a page and document,  this method can be called from the page init callback
        // which is called from a worker thread so we need to be careful that the document hasn't been closed.
        if (documentViewController.getDocumentViewModel() == null) return;
        if (page != null) {
            synchronized (annotationComponentsLock) {
                if (alreadyDisposing) {
                    return;
                }
                // make sure we have a name tree to try and paint
                Catalog catalog = documentViewController.getDocument().getCatalog();
                if (catalog.getNames() != null && catalog.getNames().getDestsNameTree() != null) {
                    NameTree nameTree = catalog.getNames().getDestsNameTree();
                    ArrayList<Destination> destinations = nameTree.findDestinations(page.getPObjectReference());
                    AbstractPageViewComponent parent = this;
                    if (destinations != null && !destinations.isEmpty()) {
                        destinationComponents = new ArrayList<>(destinations.size());
                        // create the destination
                        for (Destination dest : destinations) {
                            DestinationComponent comp = new DestinationComponent(dest, documentViewController, this);
                            parent.setLayer(comp, JLayeredPane.PALETTE_LAYER);
                            parent.add(comp);
                            destinationComponents.add(comp);
                            comp.revalidate();
                            comp.repaint();
                        }
                    }
                }
            }
        }
    }

}
