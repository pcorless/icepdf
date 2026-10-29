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
package org.icepdf.fx.view.internal.annot;

import org.icepdf.core.pobjects.Name;
import org.icepdf.core.pobjects.Page;
import org.icepdf.core.pobjects.annotations.AbstractWidgetAnnotation;
import org.icepdf.core.pobjects.annotations.Annotation;
import org.icepdf.core.pobjects.annotations.FreeTextAnnotation;
import org.icepdf.core.pobjects.annotations.LinkAnnotation;
import org.icepdf.core.pobjects.annotations.MarkupAnnotation;
import org.icepdf.core.pobjects.annotations.MarkupGlueAnnotation;
import org.icepdf.core.pobjects.annotations.PopupAnnotation;
import org.icepdf.core.pobjects.annotations.RedactionAnnotation;
import org.icepdf.core.pobjects.annotations.TextAnnotation;
import org.icepdf.core.pobjects.annotations.TextMarkupAnnotation;
import org.icepdf.core.pobjects.graphics.text.DocumentSelection;
import org.icepdf.core.pobjects.graphics.text.OffsetRange;
import org.icepdf.core.pobjects.graphics.text.TextSequence;
import org.icepdf.fx.view.PagePoint;
import org.icepdf.fx.view.PdfView;
import org.icepdf.fx.view.ToolMode;
import org.icepdf.fx.view.internal.ViewHost;

import java.awt.Color;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Annotations in the view: what may be edited, hit testing and the hover/selection chrome, the
 * creation tools, free text and popup note editing, and text markup from a selection - every
 * change an undoable edit through {@link AnnotationEdits}.  Moving and resizing stay with the skin,
 * which renders the live node.
 */
public final class AnnotationController {

    private final ViewHost host;
    // the annotation under the pointer, for the hover outline.
    private AnnotationHit hovered;

    public AnnotationController(ViewHost host) {
        this.host = host;
    }

    // ---- what may be edited

    /** True if the annotation may be moved/resized by the user. */
    public static boolean isEditable(Annotation annotation) {
        return annotation != null && !(annotation instanceof LinkAnnotation)
                && annotation.allowAlterProperties() && !annotation.getFlagReadOnly();
    }

    /**
     * True for annotations drawn over text - highlight, underline, strike-out, squiggly and redaction:
     * their quads are the text they mark, so moving or resizing one would only detach it from that
     * text.  They can still be selected, recoloured and deleted; to change what they cover, delete
     * and mark again.
     */
    public static boolean isTextBound(Annotation annotation) {
        return annotation instanceof TextMarkupAnnotation || annotation instanceof RedactionAnnotation;
    }

    /** {@link #isEditable}, and the document permits annotating. */
    public boolean canEdit(Annotation annotation) {
        return host.view().isAnnotationEditingAllowed() && isEditable(annotation);
    }

    /** {@link #canEdit}, and not {@link #isTextBound}: it may be moved and resized. */
    public boolean canMove(Annotation annotation) {
        return canEdit(annotation) && !isTextBound(annotation);
    }

    /**
     * {@link #canMove}, and its size means something: a text box keeps its text as it is whatever
     * its size, and a note is an icon, so those two only move.
     */
    public boolean canResize(Annotation annotation) {
        return canMove(annotation) && !(annotation instanceof FreeTextAnnotation)
                && !(annotation instanceof org.icepdf.core.pobjects.annotations.TextAnnotation);
    }

    // ---- hit testing and chrome

    /**
     * The topmost annotation under a viewport point that the view lets you interact with: visible,
     * not a popup (popups are nodes) and not a form widget.  Only pages already initialised by
     * rendering are searched, so this never parses on the FX thread.
     */
    public AnnotationHit annotationAtViewport(double vx, double vy) {
        PagePoint point = host.pageAtViewport(vx, vy);
        if (point == null) return null;
        Page page = host.document().getPageTree().getPage(point.pageIndex());
        if (page == null || !page.isInitiated()) return null;
        List<Annotation> annotations = page.getAnnotations();
        if (annotations == null) return null;
        for (int i = annotations.size() - 1; i >= 0; i--) {
            Annotation a = annotations.get(i);
            // MarkupGlueAnnotation is core's synthetic popup connector for printing; its rect spans
            // the markup and its popup, so it would swallow clicks meant for the markup.
            if (a == null || a.isDeleted() || a instanceof PopupAnnotation || a instanceof AbstractWidgetAnnotation
                    || a instanceof MarkupGlueAnnotation) continue;
            if (!a.allowScreenNormalMode() || a.getFlagHidden()) continue;
            Rectangle2D.Float r = a.getUserSpaceRectangle();
            if (r != null && r.contains(point.x(), point.y())) return new AnnotationHit(point.pageIndex(), a);
        }
        return null;
    }

    /** Follows the link (or action) under a viewport point; true if there was one. */
    public boolean activateLinkAtViewport(double vx, double vy) {
        AnnotationHit hit = annotationAtViewport(vx, vy);
        if (hit == null || !hit.isActionable()) return false;
        host.view().performAnnotationAction(hit.annotation());
        return true;
    }

    private boolean isOnPage(Annotation annotation, int pageIndex) {
        Page page = host.document().getPageTree().getPage(pageIndex);
        List<Annotation> annotations = page != null && page.isInitiated() ? page.getAnnotations() : null;
        return annotations != null && !annotation.isDeleted() && annotations.contains(annotation);
    }

    /** The page an annotation is on (among visible pages), or null. */
    public AnnotationHit hitOf(Annotation annotation) {
        for (AnnotationUiLayer ui : host.uiLayers()) {
            if (isOnPage(annotation, ui.getPageIndex())) return new AnnotationHit(ui.getPageIndex(), annotation);
        }
        return null;
    }

    /**
     * The pages the annotations are on, -1 for one that is on none: a visible page, else the page
     * the annotation names (/P) when that page holds it, else whichever loaded page holds it (/P is
     * optional, many files leave it out).  Only the pages' annotation lists are read, not their
     * content streams.
     */
    public int[] pageIndexesOf(List<? extends Annotation> annotations) {
        int count = host.document().getPageTree().getNumberOfPages();
        int[] pages = new int[annotations.size()];
        java.util.Map<Annotation, Integer> loaded = null;
        for (int i = 0; i < pages.length; i++) {
            Annotation annotation = annotations.get(i);
            AnnotationHit hit = hitOf(annotation);
            int pageIndex = hit != null ? hit.pageIndex() : annotation.getPageIndex();
            if (hit == null && !(pageIndex >= 0 && pageIndex < count && holds(pageIndex, annotation))) {
                if (loaded == null) {
                    // one pass over the pages, for every annotation that needs it.
                    loaded = new java.util.IdentityHashMap<>();
                    for (int p = 0; p < count; p++) {
                        Page page = host.document().getPageTree().getPage(p);
                        List<Annotation> onPage = page != null ? page.getAnnotations() : null;
                        if (onPage != null) for (Annotation a : onPage) loaded.putIfAbsent(a, p);
                    }
                }
                Integer found = annotation.isDeleted() ? null : loaded.get(annotation);
                pageIndex = found != null ? found : -1;
            }
            pages[i] = pageIndex;
        }
        return pages;
    }

    /** The page an annotation is on, shown or not ({@link #pageIndexesOf}), or -1. */
    public int pageIndexOf(Annotation annotation) {
        return pageIndexesOf(List.of(annotation))[0];
    }

    /** True if the page's annotation list holds the annotation (the list is read, not the page's content). */
    private boolean holds(int pageIndex, Annotation annotation) {
        Page page = host.document().getPageTree().getPage(pageIndex);
        List<Annotation> annotations = page != null ? page.getAnnotations() : null;
        return annotations != null && !annotation.isDeleted() && annotations.contains(annotation);
    }

    /** The selected annotation and the (visible) page it is on, or null. */
    public AnnotationHit selectedHit() {
        Annotation selected = host.view().getSelectedAnnotation();
        return selected == null ? null : hitOf(selected);
    }

    /** Sets the hover outline (null clears). */
    public void setHovered(AnnotationHit hit) {
        if (Objects.equals(hit, hovered)) return;
        hovered = hit;
        updateChrome();
    }

    /** Applies hover, selection and field focus to every page's UI layer. */
    public void updateChrome() {
        host.uiLayers().forEach(this::updateChrome);
    }

    /** Applies hover, selection and field focus to a page's UI layer. */
    public void updateChrome(AnnotationUiLayer ui) {
        int index = ui.getPageIndex();
        PdfView view = host.view();
        AbstractWidgetAnnotation field = view.getFocusedField();
        ui.setFocusedField(field != null && isOnPage(field, index) ? field : null);
        ui.setHovered(hovered != null && hovered.pageIndex() == index ? hovered.annotation() : null);
        Annotation selected = view.getSelectedAnnotation();
        Annotation onPage = selected != null && isOnPage(selected, index) ? selected : null;
        ui.setSelected(onPage, canResize(onPage));
    }

    // ---- popups

    /** Popup note edits: drag and resize, text, minimise; each an undoable edit. */
    public final PopupNode.Listener popupListener = new PopupNode.Listener() {
        @Override
        public void reshaping(PopupNode node, double dx, double dy, double dw, double dh) {
            AnnotationUiLayer ui = uiLayerOf(node);
            if (ui != null) ui.setPopupReshape(node, dx, dy, dw, dh, host.zoom());
        }

        @Override
        public void reshaped(PopupNode node, double dx, double dy, double dw, double dh) {
            AnnotationUiLayer ui = uiLayerOf(node);
            if (ui == null) return;
            double zoom = host.zoom();
            ui.clearPopupReshape(zoom);
            if (Math.abs(dx) < 0.5 && Math.abs(dy) < 0.5 && Math.abs(dw) < 0.5 && Math.abs(dh) < 0.5) return;
            int pageIndex = ui.getPageIndex();
            Rectangle2D.Float rect = node.getPopup().getUserSpaceRectangle();
            double w = Math.max(PopupNode.MIN_WIDTH, rect.getWidth() + dw / zoom);
            double h = Math.max(PopupNode.MIN_HEIGHT, rect.getHeight() + dh / zoom);
            double[] anchor = ui.popupAnchor(node.getPopup());
            Page page = host.document().getPageTree().getPage(pageIndex);
            Rectangle2D target = placeUpright(page, anchor[0] + dx, anchor[1] + dy, w, h);
            host.recordEdit(AnnotationEdits.popupRect(host.annotationLocker(), page, pageIndex,
                    node.getPopup(), target));
        }

        @Override
        public void contentsEdited(PopupNode node, String contents) {
            AnnotationUiLayer ui = uiLayerOf(node);
            String current = node.getMarkup().getContents();
            if (ui == null || contents.equals(current == null ? "" : current)) return;
            Page page = host.document().getPageTree().getPage(ui.getPageIndex());
            host.recordEdit(AnnotationEdits.contents(host.annotationLocker(), page,
                    ui.getPageIndex(), node.getMarkup(), contents));
        }

        @Override
        public void minimised(PopupNode node) {
            host.view().setPopupOpen(node.getMarkup(), false);
        }

        @Override
        public void privacyChanged(PopupNode node, boolean priv) {
            host.view().setAnnotationsPrivate(List.of(node.getMarkup()), priv);
        }
    };

    private AnnotationUiLayer uiLayerOf(PopupNode node) {
        for (AnnotationUiLayer ui : host.uiLayers()) {
            if (ui.popupNodes().contains(node)) return ui;
        }
        return null;
    }

    /**
     * The user-space rectangle of size (w, h) points whose view-space bounds have their top-left at
     * (viewX, viewY) - for an upright popup at any page rotation.
     */
    private Rectangle2D placeUpright(Page page, double viewX, double viewY, double w, double h) {
        AffineTransform pageToView = host.pageToView(page);
        Rectangle2D origin = pageToView.createTransformedShape(new Rectangle2D.Double(0, 0, w, h)).getBounds2D();
        // shift the origin-placed rect so its view bounds start at (viewX, viewY), in user space.
        try {
            Point2D shift = pageToView.createInverse().deltaTransform(
                    new Point2D.Double(viewX - origin.getMinX(), viewY - origin.getMinY()), null);
            return new Rectangle2D.Double(shift.getX(), shift.getY(), w, h);
        } catch (NoninvertibleTransformException e) {
            return new Rectangle2D.Double(0, 0, w, h);
        }
    }

    // ---- creating

    private AnnotationCreator.Style style(ToolMode mode) {
        PdfView control = host.view();
        javafx.scene.paint.Color fx = control.toolColor(mode);
        Color color = new Color((float) fx.getRed(), (float) fx.getGreen(), (float) fx.getBlue());
        int opacity = mode == ToolMode.HIGHLIGHT ? TextMarkupAnnotation.HIGHLIGHT_ALPHA : 255;
        return new AnnotationCreator.Style(control.getAnnotationAuthor(), color, opacity, 2f);
    }

    /** Adds a built annotation (with a popup for markup) as one undoable edit and selects it. */
    private void addCreated(int pageIndex, Page page, Annotation annotation, boolean popupOpen) {
        PopupAnnotation popup = annotation instanceof MarkupAnnotation markup
                && !(annotation instanceof FreeTextAnnotation)
                && !(annotation instanceof RedactionAnnotation)
                ? AnnotationCreator.popup(page.getLibrary(), markup, popupOpen, host.toPageSpace(page)) : null;
        // redaction marks stay public: hiding one from other reviewers would hide what is to be removed.
        if (host.view().isNewAnnotationsPrivate() && annotation instanceof MarkupAnnotation markup
                && !(annotation instanceof RedactionAnnotation)) {
            markup.setFlag(Annotation.FLAG_PRIVATE_CONTENTS, true);
            if (popup != null) popup.setFlag(Annotation.FLAG_PRIVATE_CONTENTS, true);
        }
        host.recordEdit(AnnotationEdits.add(host.annotationLocker(), page, pageIndex, annotation, popup));
        host.view().selectAnnotation(annotation);
    }

    private Page page(int pageIndex) {
        return host.document().getPageTree().getPage(pageIndex);
    }

    public void createNote(int pageIndex, double viewX, double viewY) {
        Page page = page(pageIndex);
        TextAnnotation note = AnnotationCreator.note(page.getLibrary(), viewX, viewY, host.pageToView(page),
                host.toPageSpace(page), style(ToolMode.NOTE));
        addCreated(pageIndex, page, note, true);
        host.scheduleRefresh();
    }

    /**
     * Marks text on a page for redaction from its page-space rectangles (one match's lines), as the
     * redact tool does for a selection.  @return the edit, applied, for the caller to record
     */
    public AnnotationEdits.Edit redactPageRects(int pageIndex, List<Rectangle2D> pageRects) {
        Page page = page(pageIndex);
        AffineTransform pageToView = host.pageToView(page);
        List<Rectangle2D> viewRects = new ArrayList<>();
        for (Rectangle2D r : pageRects) viewRects.add(pageToView.createTransformedShape(r).getBounds2D());
        return AnnotationEdits.add(host.annotationLocker(), page, pageIndex, AnnotationCreator.redaction(
                page.getLibrary(), viewRects, host.toPageSpace(page), host.view().getAnnotationAuthor()), null);
    }

    /** The redaction tool's box: marks whatever is under it (an image, a drawing) for redaction. */
    public void createRedactionBox(int pageIndex, Rectangle2D viewRect) {
        Page page = page(pageIndex);
        addCreated(pageIndex, page, AnnotationCreator.redaction(page.getLibrary(), List.of(viewRect),
                host.toPageSpace(page), host.view().getAnnotationAuthor()), false);
    }

    public void createShape(int pageIndex, boolean ellipse, Rectangle2D viewRect) {
        Page page = page(pageIndex);
        addCreated(pageIndex, page, AnnotationCreator.shape(page.getLibrary(), ellipse, viewRect,
                host.toPageSpace(page), style(ellipse ? ToolMode.ELLIPSE : ToolMode.RECTANGLE)), false);
    }

    public void createLine(int pageIndex, javafx.geometry.Point2D start, javafx.geometry.Point2D end) {
        Page page = page(pageIndex);
        addCreated(pageIndex, page, AnnotationCreator.line(page.getLibrary(),
                new Point2D.Double(start.getX(), start.getY()), new Point2D.Double(end.getX(), end.getY()),
                host.toPageSpace(page), style(ToolMode.LINE)), false);
    }

    public void createInk(int pageIndex, GeneralPath viewPath) {
        Page page = page(pageIndex);
        addCreated(pageIndex, page, AnnotationCreator.ink(page.getLibrary(), viewPath, host.toPageSpace(page),
                style(ToolMode.INK)), false);
    }

    public void createFreeText(int pageIndex, double viewX, double viewY) {
        Page page = page(pageIndex);
        FreeTextAnnotation freeText = AnnotationCreator.freeText(page.getLibrary(), viewX, viewY, host.zoom(),
                host.toPageSpace(page), style(ToolMode.FREE_TEXT));
        addCreated(pageIndex, page, freeText, false);
        editFreeText(new AnnotationHit(pageIndex, freeText), true);
    }

    /** Opens the inline editor over a free text annotation; the text commits as an undoable edit. */
    public void editFreeText(AnnotationHit hit) {
        editFreeText(hit, false);
    }

    /** @param created the box was just made: cancelling it, or saving it empty, takes it away again */
    private void editFreeText(AnnotationHit hit, boolean created) {
        if (!(hit.annotation() instanceof FreeTextAnnotation freeText) || !canEdit(freeText)) return;
        AnnotationUiLayer ui = host.uiLayer(hit.pageIndex());
        if (ui == null) return;
        Page page = page(hit.pageIndex());
        // the box's creation is the last edit while its editor is open: undo it, leaving no trace.
        Runnable uncreate = () -> {
            if (created && host.view().isCanUndo()) host.view().undo();
        };
        ui.openTextEditor(ui.viewBounds(freeText), freeText.getContents(), host.zoom(), text -> {
            if (created && text.isBlank()) {
                uncreate.run();
                return;
            }
            String current = freeText.getContents() == null ? "" : freeText.getContents();
            if (text.equals(current)) return;
            host.recordEdit(AnnotationEdits.freeTextContents(host.annotationLocker(), page,
                    hit.pageIndex(), freeText, text, host.toPageSpace(page)));
        }, uncreate);
    }

    /**
     * Turns the current text selection into highlight/underline/strike-out/squiggly annotations, one per page
     * it spans (pages whose text isn't loaded are skipped), and clears the selection.
     *
     * @return the number created
     */
    public int markupSelection(Name subtype) {
        PdfView view = host.view();
        DocumentSelection selection = view.getTextSelection();
        if (selection == null || selection.isCollapsed() || host.document() == null
                || !view.isAnnotationEditingAllowed()) return 0;
        ToolMode mode = TextMarkupAnnotation.SUBTYPE_HIGHLIGHT.equals(subtype) ? ToolMode.HIGHLIGHT
                : TextMarkupAnnotation.SUBTYPE_UNDERLINE.equals(subtype) ? ToolMode.UNDERLINE
                : TextMarkupAnnotation.SUBTYPE_SQUIGGLY.equals(subtype) ? ToolMode.SQUIGGLY : ToolMode.STRIKE_OUT;
        int created = 0;
        Annotation last = null;
        for (int pageIndex = selection.startPage(); pageIndex <= selection.endPage(); pageIndex++) {
            TextSequence sequence = host.textSequence(pageIndex);
            OffsetRange range = selection.rangeForPage(pageIndex, sequence);
            if (range == null || range.isEmpty()) continue;
            Page page = page(pageIndex);
            AffineTransform pageToView = host.pageToView(page);
            List<Rectangle2D> viewRects = new ArrayList<>();
            for (Rectangle2D r : sequence.rectsFor(range)) {
                viewRects.add(pageToView.createTransformedShape(r).getBounds2D());
            }
            if (viewRects.isEmpty()) continue;
            Annotation markup = Annotation.SUBTYPE_REDACT.equals(subtype)
                    ? AnnotationCreator.redaction(page.getLibrary(), viewRects, host.toPageSpace(page),
                    view.getAnnotationAuthor())
                    : AnnotationCreator.textMarkup(page.getLibrary(), subtype, viewRects,
                    sequence.extractText(range), host.toPageSpace(page), style(mode));
            addCreated(pageIndex, page, markup, false);
            last = markup;
            created++;
        }
        view.setTextSelection(null);
        if (last != null) view.selectAnnotation(last);
        return created;
    }
}
