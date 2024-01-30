/*
 * Copyright 2006-2019 ICEsoft Technologies Canada Corp.
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

import org.icepdf.core.Memento;
import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.Page;
import org.icepdf.core.pobjects.PageTree;
import org.icepdf.core.pobjects.Reference;
import org.icepdf.ri.common.UndoCaretaker;
import org.icepdf.ri.common.views.annotations.AbstractAnnotationComponent;
import org.icepdf.ri.common.views.annotations.MarkupGlueComponent;
import org.icepdf.ri.common.views.annotations.PageViewAnnotationComponent;
import org.icepdf.ri.common.views.annotations.PopupAnnotationComponent;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

/**
 * <p>The AbstractDocumentViewModel is responsible for keeping the state of the
 * document view.  The AbstractDocumentViewModel also stores an list of
 * PageViewComponents who's state is update as the model changes.  The
 * AbstractDocumentViewModel can be swapped into different page views quickly
 * and efficiently.</p>
 *
 * @see org.icepdf.ri.common.views.DocumentViewModelImpl
 * @since 2.5
 */
public abstract class AbstractDocumentViewModel implements DocumentViewModel {

    private static final Logger log =
            Logger.getLogger(AbstractDocumentViewModel.class.toString());

    // document that model is associated.
    protected final Document currentDocument;

    protected DocumentView documentView;

    // Pages that have selected text.
    private HashMap<Integer, AbstractPageViewComponent> selectedPageText;
    // select all state flag, optimization for painting select all state lazily
    private boolean selectAll;
    protected List<AbstractPageViewComponent> pageComponents;
    protected HashMap<AbstractPageViewComponent, ArrayList<PageViewAnnotationComponent>> documentViewAnnotationComponents;
    // scroll pane used to contain the view
    protected JScrollPane documentViewScrollPane;
    // annotation memento caretaker
    protected final UndoCaretaker undoCaretaker;
    // currently selected annotation
    protected AnnotationComponent currentAnnotation;
    // page view settings
    protected float userZoom = 1.0f, oldUserZoom = 1.0f;
    protected float userRotation, oldUserRotation;
    protected int currentPageIndex, oldPageIndex;
    protected int pageBoundary = Page.BOUNDARY_CROPBOX;
    // page tool settings
    protected int userToolModeFlag, oldUserToolModeFlag;

    // 10 pages doesn't take to long to look at, anymore and people will notice
    // the rest of the page sizes will be figured out later.
    protected static final int MAX_PAGE_SIZE_READ_AHEAD = 10;

    public AbstractDocumentViewModel(Document currentDocument) {
        this.currentDocument = currentDocument;
        // create new instance of the undoCaretaker
        undoCaretaker = new UndoCaretaker();
        documentViewAnnotationComponents = new HashMap<>();
    }

    protected abstract AbstractPageViewComponent buildPageViewComponent(DocumentViewModel documentViewModel,
                                                                        PageTree pageTree, final int pageIndex,
                                                                        int width, int height);

    public Document getDocument() {
        return currentDocument;
    }

    public List<AbstractPageViewComponent> getPageComponents() {
        return pageComponents;
    }


    public HashMap<AbstractPageViewComponent, ArrayList<PageViewAnnotationComponent>> getDocumentViewAnnotationComponents() {
        return documentViewAnnotationComponents;
    }
    @Override
    public ArrayList<PageViewAnnotationComponent> getDocumentViewAnnotationComponents(AbstractPageViewComponent pageViewComponent) {
        return documentViewAnnotationComponents.get(pageViewComponent);
    }

    @Override
    public void addDocumentViewAnnotationComponent(AbstractPageViewComponent pageViewComponent, PageViewAnnotationComponent annotationComponent) {
        if (!documentViewAnnotationComponents.containsKey(pageViewComponent)) {
            documentViewAnnotationComponents.put(pageViewComponent, new ArrayList<>());
        }
        List<PageViewAnnotationComponent> components = documentViewAnnotationComponents.get(pageViewComponent);
        components.add(annotationComponent);
    }

    @Override
    public void removeDocumentViewAnnotationComponent(DocumentView parentDocumentView,
                                                      AbstractPageViewComponent pageViewComponent,
                                                      PageViewAnnotationComponent annotationComponent) {
        if (documentViewAnnotationComponents.containsKey(pageViewComponent)) {
            List<PageViewAnnotationComponent> components = documentViewAnnotationComponents.get(pageViewComponent);
            components.remove(annotationComponent);
            // find and remove glue component
            if (annotationComponent instanceof PopupAnnotationComponent) {
                PageViewAnnotationComponent markForRemoval = null;
                for (PageViewAnnotationComponent pageViewAnnotationComponent : components) {
                    if (pageViewAnnotationComponent instanceof MarkupGlueComponent) {
                        MarkupGlueComponent glueComponent = (MarkupGlueComponent) pageViewAnnotationComponent;
                        PopupAnnotationComponent popupannotationComponent =
                                (PopupAnnotationComponent) annotationComponent;
                        Reference glueParentReference =
                                glueComponent.getPopupAnnotationComponent().getAnnotation().getPObjectReference();
                        Reference parentReference =
                                popupannotationComponent.getAnnotation().getPObjectReference();
                        if (glueParentReference.equals(parentReference)) {
                            markForRemoval = pageViewAnnotationComponent;
                            break;
                        }
                    }
                }
                if (markForRemoval != null) {
                    components.remove(markForRemoval);
                    parentDocumentView.remove((Component) markForRemoval);
                }
            }
        }
    }

    public void removeAllFloatingAnnotationComponent(AbstractPageViewComponent pageViewComponent) {
        if (!documentViewAnnotationComponents.containsKey(pageViewComponent)) {
            documentViewAnnotationComponents.remove(pageViewComponent);
        }
    }

    //todo clean up name and why this isn't called.
    public void removeFloatingAnnotationComponent(AbstractAnnotationComponent annotationComponent) {
        documentViewAnnotationComponents.remove(annotationComponent);
    }

    public boolean setViewCurrentPageIndex(int pageIndex) {
        boolean changed = pageIndex != currentPageIndex;
        oldPageIndex = currentPageIndex;
        currentPageIndex = pageIndex;
        return changed;
    }

    public int getViewCurrentPageIndex() {
        return currentPageIndex;
    }

    /**
     * Gets the list of components that have a selected state.  The
     * WeakReference must be checked to make sure the page was not disposed of
     * for some reason by the memory manager.
     *
     * @return list of pages that are in a selected state.
     */
    public ArrayList<AbstractPageViewComponent> getSelectedPageText() {
        if (selectedPageText != null) {
            Set<Integer> keySet = selectedPageText.keySet();
            ArrayList<AbstractPageViewComponent> selectedPages = new ArrayList<>(keySet.size());
            for (Integer pageIndex : keySet) {
                selectedPages.add(selectedPageText.get(pageIndex));
            }
            return selectedPages;
        } else {
            return null;
        }
    }

    /**
     * Gets the selected all state of the document pages view.
     *
     * @return true if all pages are ina  selected state, false otherwise.
     */
    public boolean isSelectAll() {
        return selectAll;
    }

    /**
     * Sets the select all state of the text in the document.  If true the
     * document text is all selected; otherwise, false. This is only a flag
     * and must be interpreted by the pages and page view components.
     *
     * @param selectAll to specify all text is selected, false to specify
     *                  no text is selected
     */
    public void setSelectAll(boolean selectAll) {
        this.selectAll = selectAll;
    }

    /**
     * Adds the specified page to selected page cache.  No checking is done
     * to make sure of selected text.  The caches are used as an optimization
     * to make sure selected text can be cleared quickly.
     *
     * @param pageComponent pageView component to add to list.
     */
    public void addSelectedPageText(AbstractPageViewComponent pageComponent) {
        if (selectedPageText == null) {
            selectedPageText = new HashMap<>();
        }
        selectedPageText.put(pageComponent.getPageIndex(), pageComponent);
    }

    /**
     * Removes the specified page to selected page cache.  No checking is done
     * to make sure of selected text.  The caches are used as an optimization
     * to make sure selected text can be cleared quickly.
     *
     * @param pageComponent pageView component to add to list.
     */
    public void removeSelectedPageText(AbstractPageViewComponent pageComponent) {
        if (selectedPageText == null) {
            selectedPageText = new HashMap<>();
        }
        selectedPageText.remove(pageComponent.getPageIndex());
    }

    /**
     * Clears cache used to store which pages have selected state.
     */
    public void clearSelectedPageText() {
        if (selectedPageText != null) {
            selectedPageText.clear();
        }
        selectAll = false;
    }

    /**
     * Sets the zoom factor of the page visualization. A zoom factor of 1.0f
     * is equal to 100% or actual size.  A zoom factor of 0.5f is equal to 50%
     * of the original size.
     *
     * @param viewZoom zoom factor
     * @return if zoom actually changed
     */
    public boolean setViewZoom(float viewZoom) {
        boolean changed = userZoom != viewZoom;
        if (changed) {
            // apply the change
            oldUserZoom = userZoom;
            userZoom = viewZoom;
        }
        return changed;
    }

    public float getViewZoom() {
        return userZoom;
    }

    public boolean setViewRotation(float viewRotation) {
        boolean changed = userRotation != viewRotation;
        if (changed) {
            // apply the change
            oldUserRotation = userRotation;
            userRotation = viewRotation;
        }
        return changed;
    }

    /**
     * Returns the zoom factor of the page visualization.  A zoom factor of 1.0f
     * is equal to 100% or actual size.  A zoom factor of 0.5f is equal to 50%
     * of the original size.
     *
     * @return zoom factor
     */
    public float getViewRotation() {
        return userRotation;
    }

    public boolean setViewToolMode(final int viewToolMode) {
        boolean changed = viewToolMode != userToolModeFlag;
        if (changed) {
            // apply the change
            oldUserToolModeFlag = userToolModeFlag;
            userToolModeFlag = viewToolMode;
        }
        return changed;
    }

    public int getViewToolMode() {
        return userToolModeFlag;
    }

    public boolean isViewToolModeSelected(final int viewToolMode) {
        return userToolModeFlag == viewToolMode;
    }

    /**
     * Sets the page boundary used to paint a page.
     *
     * @param pageBoundary page bounds
     */
    public void setPageBoundary(int pageBoundary) {
        this.pageBoundary = pageBoundary;
    }

    public int getPageBoundary() {
        return pageBoundary;
    }

    public Rectangle getPageBounds(int pageIndex) {
        if (pageComponents != null && pageIndex < pageComponents.size()) {
            Component pageViewComponentImpl = pageComponents.get(pageIndex);
            if (pageViewComponentImpl != null && pageViewComponentImpl.getParent() != null) {
                Rectangle pageBounds = pageViewComponentImpl.getParent().getBounds();
                return pageBounds;
            }
        }
        return new Rectangle();
    }

    public void dispose() {

        if (pageComponents != null) {
            for (AbstractPageViewComponent pageComponent : pageComponents) {
                if (pageComponent != null) {
                    pageComponent.dispose();
                }
            }
            pageComponents.clear();
        }
        if (documentViewAnnotationComponents != null) {
            documentViewAnnotationComponents.clear();
        }
    }

    /**
     * Gets the currently selected annotation in the document model.
     *
     * @return currently selected annotation, null if there is none.
     */
    public AnnotationComponent getCurrentAnnotation() {
        return currentAnnotation;
    }

    /**
     * Sets the current annotation.  This is mainly called by the UI tools
     * when editing and selecting page annotations.
     *
     * @param currentAnnotation annotation to make current.
     */
    public void setCurrentAnnotation(AnnotationComponent currentAnnotation) {
        // clear the previously selected state.
        if (this.currentAnnotation != null) {
            this.currentAnnotation.setSelected(false);
            this.currentAnnotation.repaint();
        }
        this.currentAnnotation = currentAnnotation;
        // select the new selection if valid
        if (this.currentAnnotation != null) {
            this.currentAnnotation.setSelected(true);
        }
    }

    public DocumentView getDocumentView() {
        return documentView;
    }

    public void setDocumentView(DocumentView documentView) {
        this.documentView = documentView;
    }

    /**
     * Get the scroll pane used to contain the page view components.
     *
     * @return parent page view scroll pane.
     */
    public JScrollPane getDocumentViewScrollPane() {
        return documentViewScrollPane;
    }

    /**
     * Sets the parent scroll pane used by this view.
     *
     * @param documentViewScrollPane parent scroll pane
     */
    public void setDocumentViewScrollPane(JScrollPane documentViewScrollPane) {
        this.documentViewScrollPane = documentViewScrollPane;
    }

    /**
     * Gets annotation caretaker responsible for saving states as defined
     * by the memento pattern.
     *
     * @return document level annotation care taker.
     */
    public UndoCaretaker getAnnotationCareTaker() {
        return undoCaretaker;
    }

    public void addMemento(Memento oldMementoState, Memento newMementoState) {
        undoCaretaker.addState(oldMementoState, newMementoState);
    }
}
