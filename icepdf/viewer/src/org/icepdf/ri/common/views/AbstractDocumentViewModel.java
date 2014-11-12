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
package org.icepdf.ri.common.views;

import org.icepdf.core.Memento;
import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.Page;
import org.icepdf.core.util.Defs;
import org.icepdf.ri.common.UndoCaretaker;

import javax.swing.*;
import java.awt.*;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * <p>The AbstractDocumentViewModel is responsible for keeping the state of the
 * documetn view.  The AbstractDocumentViewModel also stores an list of
 * PageViewComponetnts who's state is update as the model changes.  The
 * AbstractDocumentViewModel can be swapped into different page views quickly
 * and efficently.</p>
 *
 * @see org.icepdf.ri.common.views.DocumentViewModelImpl
 * @since 2.5
 */
public abstract class AbstractDocumentViewModel implements DocumentViewModel {

    private static final Logger log =
            Logger.getLogger(AbstractDocumentViewModel.class.toString());

    // document that model is associated.
    protected Document currentDocument;

    // page dirty repaint timer
    private Timer isDirtyTimer;
    // dirty refresh timer call interval
    private static int dirtyTimerInterval = 5;

    static {
        try {
            dirtyTimerInterval =
                    Defs.intProperty("org.icepdf.core.views.dirtytimer.interval",
                            dirtyTimerInterval);
        } catch (NumberFormatException e) {
            log.log(Level.FINE, "Error reading dirty timer interval");
        }
    }

    // Pages that have selected text.
    private ArrayList<WeakReference<AbstractPageViewComponent>> selectedPageText;
    // select all state flag, optimization for painting select all state lazily
    private boolean selectAll;

    protected List<AbstractPageViewComponent> pageComponents;

    // annotation memento caretaker
    protected UndoCaretaker undoCaretaker;

    // currently selected annotation
    protected AnnotationComponent currentAnnotation;

    // page view settings
    protected float userZoom = 1.0f, oldUserZoom = 1.0f;
    protected float userRotation, oldUserRotation;
    protected int currentPageIndex, oldPageIndex;
    protected int pageBoundary = Page.BOUNDARY_CROPBOX;

    // page tool settings
    protected int userToolModeFlag, oldUserToolModeFlag;

    // 10 pages doesn't take to long to look at, any more and people will notice
    // the rest of the page sizes will be figured out later.
    protected static final int MAX_PAGE_SIZE_READ_AHEAD = 10;

    public AbstractDocumentViewModel(Document currentDocument) {
        this.currentDocument = currentDocument;
        // create new instance of the undoCaretaker
        undoCaretaker = new UndoCaretaker();

        // timer will dictate when buffer repaints can take place
        isDirtyTimer = new Timer(dirtyTimerInterval, null);
        isDirtyTimer.setInitialDelay(0);
        isDirtyTimer.start();
    }

    public Document getDocument() {
        return currentDocument;
    }

    public List<AbstractPageViewComponent> getPageComponents() {
        return pageComponents;
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
     * WeakReference must be checkt o make sure the page was not disposed of
     * for for some reason by the the memeory manager.
     *
     * @return list of pages that are in a selected state.
     */
    public ArrayList<WeakReference<AbstractPageViewComponent>> getSelectedPageText() {
        return selectedPageText;
    }

    /**
     * Gets the selected all state of the doucment pages view.
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
     * @param selectAll to to specify all text is selected, false to sepcify
     *                  no text is selected
     */
    public void setSelectAll(boolean selectAll) {
        this.selectAll = selectAll;
    }

    /**
     * Adds the specified page to selected page cache.  No checking is done
     * to make sure of selected text.  The caches is used as an optimization
     * to make sure selected text can be cleared quickly.
     *
     * @param pageViewComponent pageview component to add to list.
     */
    public void addSelectedPageText(AbstractPageViewComponent pageViewComponent) {
        if (selectedPageText == null) {
            selectedPageText =
                    new ArrayList<WeakReference<AbstractPageViewComponent>>();
        }
        selectedPageText.add(
                new WeakReference<AbstractPageViewComponent>(pageViewComponent));
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
            // propagate the changes to the sub components.
            for (AbstractPageViewComponent pageViewComponent : pageComponents) {
                if (pageViewComponent != null) {
                    pageViewComponent.invalidate();
                }
            }
        }
        return changed;
    }

    /**
     * Invalidate the underlying Document Page models.
     */
    public void invalidate() {
        for (AbstractPageViewComponent pageViewComponent : pageComponents) {
            pageViewComponent.invalidatePage();
        }
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
            // propagate the changes to the sub components.
            for (AbstractPageViewComponent pageViewComponent : pageComponents) {
                if (pageViewComponent != null) {
                    pageViewComponent.invalidate();
                }
            }
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
     * Sets the page boundtry used to paint a page.
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
        Rectangle pageBounds = new Rectangle();
        if (pageComponents != null && pageIndex < pageComponents.size()) {
            Component pageViewComponentImpl = pageComponents.get(pageIndex);
            if (pageViewComponentImpl != null) {
                Component parentComponent = pageViewComponentImpl;
                Dimension size = pageViewComponentImpl.getPreferredSize();
                pageBounds.setSize(size.width, size.height);
                while (parentComponent != null &&
                        !(parentComponent instanceof DocumentView)) {
                    pageBounds.x += parentComponent.getBounds().x;
                    pageBounds.y += parentComponent.getBounds().y;
                    parentComponent = parentComponent.getParent();
                }
            }
        }
        return pageBounds;
    }

    public void dispose() {

        if (pageComponents != null) {
            for (AbstractPageViewComponent pageViewComponent : pageComponents) {
                if (pageViewComponent != null) {
                    pageViewComponent.dispose();
                }
            }
            pageComponents.clear();

            // stop the timer
            if (isDirtyTimer != null && isDirtyTimer.isRunning()) {
                isDirtyTimer.stop();
            }
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
     * Sets the current annotation.  This is manily called by the UI tools
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

    /**
     * Gets annotation caretaker responsible for saving states as defined
     * by the momento pattern.
     *
     * @return document leve annotation care taker.
     */
    public UndoCaretaker getAnnotationCareTaker() {
        return undoCaretaker;
    }

    public void addMemento(Memento oldMementoState, Memento newMementoState) {
        undoCaretaker.addState(oldMementoState, newMementoState);
    }

    public Timer getDirtyTimer() {
        return isDirtyTimer;
    }
}
