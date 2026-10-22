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
package org.icepdf.fx.view.internal;

import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.Page;
import org.icepdf.core.pobjects.annotations.Annotation;
import org.icepdf.core.pobjects.graphics.text.TextSequence;
import org.icepdf.fx.signature.SignatureStatus;
import org.icepdf.fx.view.PagePoint;
import org.icepdf.fx.view.PdfView;
import org.icepdf.fx.view.internal.annot.AnnotationEdits;
import org.icepdf.fx.view.internal.annot.AnnotationUiLayer;

import java.awt.geom.AffineTransform;
import java.util.Collection;

/**
 * What the view's skin offers the controllers behind it (annotations, form fields, signatures)
 * and the tools: the document, the layout's zoom and page transforms, the per-page UI layers, and
 * the few skin actions they need.  Points are in viewport coordinates.
 */
public interface ViewHost {

    /** The control, for its public API. */
    PdfView view();

    /** The open document, or null. */
    Document document();

    /** Adds a checked signature field to the control's signature list. */
    void addSignatureStatus(SignatureStatus status);

    /** Records an edit on the view's undo history (null is ignored). */
    void recordEdit(AnnotationEdits.Edit edit);

    /** The zoom the current layout was built for. */
    double zoom();

    /** Page view space (at the layout's zoom and rotation) to page user space. */
    AffineTransform toPageSpace(Page page);

    /** Page user space to page view space. */
    AffineTransform pageToView(Page page);

    /** Serialises annotation mutations with the renderer. */
    AnnotationEdits.Locker annotationLocker();

    /** The UI layer of a visible page, or null. */
    AnnotationUiLayer uiLayer(int pageIndex);

    Collection<AnnotationUiLayer> uiLayers();

    /** The page and user-space point under a viewport point, or null between or outside pages. */
    PagePoint pageAtViewport(double vx, double vy);

    /** The page's loaded text, or null if it isn't loaded (or has none). */
    TextSequence textSequence(int pageIndex);

    /**
     * Leaves an annotation out of the page's annotation tiles (a live node or editor shows it
     * instead), or puts it back; either way the page's annotations re-render.
     */
    void setExcluded(int pageIndex, Annotation annotation, boolean excluded);

    /** Scrolls a page point into view, switching page first in the non-continuous modes. */
    void reveal(PagePoint point);

    /** Re-applies hover, selection and field focus to the UI layers. */
    void refreshAnnotationChrome();

    void scheduleRefresh();
}
