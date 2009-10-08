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
package org.icepdf.ri.common.views;

import org.icepdf.core.pobjects.Document;
import org.icepdf.core.util.Defs;
import org.icepdf.core.views.DocumentView;
import org.icepdf.core.views.DocumentViewModel;
import org.icepdf.core.views.PageViewComponent;
import org.icepdf.core.views.swing.AbstractPageViewComponent;

import java.awt.*;
import java.util.List;
import java.util.logging.Logger;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

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

    protected List<AbstractPageViewComponent> pageComponents;

    // page view settings
    protected float userZoom = 1.0f, oldUserZoom = 1.0f;
    protected float userRotation, oldUserRotation;
    protected int currentPageIndex, oldPageIndex;

    // page tool settings
    protected int userToolModeFlag, oldUserToolModeFlag;

    protected static ThreadPoolExecutor pageInitilizationThreadPool;
    protected static ThreadPoolExecutor pagePainterThreadPool;

    // 10 pages doesn't take to long to look at, any more and people will notice
    // the rest of the page sizes will be figured out later.
    protected static final int MAX_PAGE_SIZE_READ_AHEAD = 10;

    protected static int maxPainterThreads;
    protected static int maxPageInitThreads;

    private static final long KEEP_ALIVE_TIME = 3;

    static {
        try {
            maxPainterThreads =
                    Defs.intProperty("org.icepdf.core.views.painterthreads", 2);
            if (maxPainterThreads < 1) {
                maxPainterThreads = 1;
            }
        } catch (NumberFormatException e) {
            log.warning("Error reading buffered scale factor");
        }

        try {
            maxPageInitThreads =
                    Defs.intProperty("org.icepdf.core.views.pageinitthreads", 2);
            if (maxPageInitThreads < 1) {
                maxPageInitThreads = 1;
            }
        } catch (NumberFormatException e) {
            log.warning("Error reading buffered scale factor");
        }

        // build a Thread pool
        pageInitilizationThreadPool = new ThreadPoolExecutor(
                1, maxPageInitThreads, KEEP_ALIVE_TIME, TimeUnit.SECONDS,
                new LinkedBlockingQueue<Runnable>());
//        pageInitilizationThreadPool.getThreadFactory();
        // set a lower thread priority
        pageInitilizationThreadPool.setThreadFactory(new ThreadFactory() {
            public Thread newThread(java.lang.Runnable command) {
                Thread newThread = new Thread(command);
                newThread.setPriority(Thread.NORM_PRIORITY);
                return newThread;
            }
        });

        pagePainterThreadPool = new ThreadPoolExecutor(
                1, maxPainterThreads, KEEP_ALIVE_TIME, TimeUnit.SECONDS,
                new LinkedBlockingQueue<Runnable>());
//        pagePainterThreadPool.getThreadFactory();
        // set a lower thread priority
        pagePainterThreadPool.setThreadFactory(new ThreadFactory() {
            public Thread newThread(java.lang.Runnable command) {
                Thread newThread = new Thread(command);
                newThread.setPriority(Thread.NORM_PRIORITY);
                return newThread;
            }
        });
    }

    public AbstractDocumentViewModel(Document currentDocument) {
        this.currentDocument = currentDocument;
    }

    public Document getDocument() {
        return currentDocument;
    }

    public void executePageInitialization(Runnable runnable) throws InterruptedException {
        pageInitilizationThreadPool.execute(runnable);
    }

    public void executePagePainter(Runnable runnable) throws InterruptedException {
        pagePainterThreadPool.execute(runnable);
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
            for (AbstractPageViewComponent pageViewComponent : pageComponents){
                if (pageViewComponent != null){
                    pageViewComponent.invalidate();
                }
            }
        }
        return changed;
    }

    /**
     * Invalidate the underlying Document Page models.
     */
    public void invalidate(){
        for (AbstractPageViewComponent pageViewComponent : pageComponents){
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
            for (PageViewComponent pageViewComponent : pageComponents){
                if (pageViewComponent != null){
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

    public Rectangle getPageBounds(int pageIndex) {
        Rectangle pageBounds = new Rectangle();
        if (pageComponents != null && pageIndex < pageComponents.size()) {
            Component pageViewComponentImpl;
            Object tmp = pageComponents.get(pageIndex);
            if (tmp != null && tmp instanceof Component) {
                pageViewComponentImpl = (Component) tmp;
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
            for (PageViewComponent pageViewComponent : pageComponents){
                if (pageViewComponent != null){
                    pageViewComponent.dispose();
                }
            }
            pageComponents.clear();
        }

        // do a little clean up.
        pageInitilizationThreadPool.purge();
        pagePainterThreadPool.purge();
    }

}
