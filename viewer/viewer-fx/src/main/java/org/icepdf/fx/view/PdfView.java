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
package org.icepdf.fx.view;

import javafx.beans.property.*;
import javafx.scene.control.Control;
import javafx.scene.control.Skin;
import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.Page;
import org.icepdf.core.pobjects.graphics.text.DocumentSelection;

import java.util.Optional;

/**
 * A JavaFX control that displays a PDF {@link Document} rendered by the ICEpdf core.
 * <p>
 * Drop it into any scene graph and drive it through properties:
 * <pre>{@code
 * Document document = new Document();
 * document.setFile("report.pdf");
 * PdfView view = new PdfView();
 * view.setDocument(document);
 * view.setFitMode(FitMode.WIDTH);
 * }</pre>
 * Pages are rendered off the FX thread by Java2D into tiles sized to the screen, not the zoom, so
 * memory stays bounded at any zoom up to {@link #MAX_ZOOM}.  Only visible pages have nodes.  The
 * control does not own the document: the caller disposes it after removing it from the view.
 * <p>
 * Styleable with the {@code pdf-view} style class.
 */
public class PdfView extends Control {

    public static final double MIN_ZOOM = 0.05;
    public static final double MAX_ZOOM = 64;
    private static final double[] ZOOM_STEPS = {0.05, 0.1, 0.25, 0.5, 0.75, 1, 1.25, 1.5, 2, 3, 4, 6, 8, 12, 16,
            24, 32, 40, 48, 64};

    private final ObjectProperty<Document> document = new SimpleObjectProperty<>(this, "document");
    private final IntegerProperty currentPageIndex = new SimpleIntegerProperty(this, "currentPageIndex", 0) {
        @Override
        public void set(int value) {
            super.set(Math.max(0, Math.min(value, Math.max(0, getPageCount() - 1))));
        }
    };
    private final DoubleProperty zoom = new SimpleDoubleProperty(this, "zoom", 1) {
        @Override
        public void set(double value) {
            super.set(Double.isNaN(value) ? 1 : Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, value)));
        }
    };
    private final DoubleProperty rotation = new SimpleDoubleProperty(this, "rotation", 0) {
        @Override
        public void set(double value) {
            double normalised = value % 360;
            super.set(normalised < 0 ? normalised + 360 : normalised);
        }
    };
    private final ObjectProperty<ViewMode> viewMode =
            new SimpleObjectProperty<>(this, "viewMode", ViewMode.CONTINUOUS);
    private final BooleanProperty coverPage = new SimpleBooleanProperty(this, "coverPage", false);
    private final ObjectProperty<FitMode> fitMode = new SimpleObjectProperty<>(this, "fitMode", FitMode.NONE);
    private final DoubleProperty pageGap = new SimpleDoubleProperty(this, "pageGap", 8);
    private final IntegerProperty pageBoundary =
            new SimpleIntegerProperty(this, "pageBoundary", Page.BOUNDARY_CROPBOX);
    private final BooleanProperty paintAnnotations = new SimpleBooleanProperty(this, "paintAnnotations", true);
    private final ObjectProperty<PageOverlayFactory> pageOverlayFactory =
            new SimpleObjectProperty<>(this, "pageOverlayFactory");
    private final ReadOnlyIntegerWrapper pageCount = new ReadOnlyIntegerWrapper(this, "pageCount", 0);
    private final ReadOnlyBooleanWrapper rendering = new ReadOnlyBooleanWrapper(this, "rendering", false);
    private final ObjectProperty<DocumentSelection> textSelection =
            new SimpleObjectProperty<>(this, "textSelection");
    private final ObjectProperty<ToolMode> toolMode = new SimpleObjectProperty<>(this, "toolMode",
            ToolMode.TEXT_SELECT) {
        @Override
        public void set(ToolMode value) {
            super.set(value == null ? ToolMode.TEXT_SELECT : value);
        }
    };

    public PdfView() {
        getStyleClass().add("pdf-view");
        setFocusTraversable(true);
        document.addListener((obs, old, doc) -> {
            setTextSelection(null);
            pageCount.set(doc != null ? doc.getNumberOfPages() : 0);
            setCurrentPageIndex(0);
        });
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new PdfViewSkin(this);
    }

    @Override
    public String getUserAgentStylesheet() {
        return PdfView.class.getResource("pdf-view.css").toExternalForm();
    }

    // ---- actions --------------------------------------------------------------------------

    /** Next step up the zoom ladder; leaves fit mode. */
    public void zoomIn() {
        setFitMode(FitMode.NONE);
        double z = getZoom();
        for (double step : ZOOM_STEPS) {
            if (step > z + 1e-6) {
                setZoom(step);
                return;
            }
        }
        setZoom(MAX_ZOOM);
    }

    /** Next step down the zoom ladder; leaves fit mode. */
    public void zoomOut() {
        setFitMode(FitMode.NONE);
        double z = getZoom();
        for (int i = ZOOM_STEPS.length - 1; i >= 0; i--) {
            if (ZOOM_STEPS[i] < z - 1e-6) {
                setZoom(ZOOM_STEPS[i]);
                return;
            }
        }
        setZoom(MIN_ZOOM);
    }

    public void rotateClockwise() {
        setRotation(getRotation() + 90);
    }

    public void rotateCounterClockwise() {
        setRotation(getRotation() - 90);
    }

    /**
     * Scrolls the viewport by logical px (positive = right/down), clamped to the document.  Does
     * nothing before the control has a skin.
     */
    public void scrollBy(double dx, double dy) {
        if (getSkin() instanceof PdfViewSkin skin) skin.scrollBy(dx, dy);
    }

    /**
     * The page and PDF user-space point under a point in this control's coordinates (for example a
     * mouse event's {@code getX()/getY()}), or empty over the gaps between pages or before the
     * control is shown.
     */
    public Optional<PagePoint> pageAt(double x, double y) {
        return getSkin() instanceof PdfViewSkin skin ? Optional.ofNullable(skin.pageAt(x, y)) : Optional.empty();
    }

    /**
     * Scrolls the least distance needed to bring a page point comfortably into view (e.g. a caret or
     * search hit).  In single-page and facing modes the point's page must be the one shown; set
     * {@link #currentPageIndexProperty()} first.
     */
    public void ensureVisible(PagePoint point) {
        if (point != null && getSkin() instanceof PdfViewSkin skin) skin.ensureVisible(point, 48);
    }

    /** Selects every page's text; no-op without a document. */
    public void selectAll() {
        if (getPageCount() > 0) setTextSelection(DocumentSelection.all(getPageCount()));
    }

    public void clearSelection() {
        setTextSelection(null);
    }

    public void nextPage() {
        setCurrentPageIndex(getCurrentPageIndex() + (getViewMode().isFacing() ? 2 : 1));
    }

    public void previousPage() {
        setCurrentPageIndex(getCurrentPageIndex() - (getViewMode().isFacing() ? 2 : 1));
    }

    // ---- properties -----------------------------------------------------------------------

    /** The document shown; not disposed by the view. */
    public final ObjectProperty<Document> documentProperty() {
        return document;
    }

    public final Document getDocument() {
        return document.get();
    }

    public final void setDocument(Document value) {
        document.set(value);
    }

    /** Zero-based page the user is looking at; setting it scrolls that page into view. */
    public final IntegerProperty currentPageIndexProperty() {
        return currentPageIndex;
    }

    public final int getCurrentPageIndex() {
        return currentPageIndex.get();
    }

    public final void setCurrentPageIndex(int value) {
        currentPageIndex.set(value);
    }

    /** 1.0 = 100% (one PDF point per logical pixel), clamped to [MIN_ZOOM, MAX_ZOOM]. */
    public final DoubleProperty zoomProperty() {
        return zoom;
    }

    public final double getZoom() {
        return zoom.get();
    }

    public final void setZoom(double value) {
        zoom.set(value);
    }

    /** Clockwise user rotation in degrees, normalised to [0, 360); applied on top of the page's /Rotate. */
    public final DoubleProperty rotationProperty() {
        return rotation;
    }

    public final double getRotation() {
        return rotation.get();
    }

    public final void setRotation(double value) {
        rotation.set(value);
    }

    public final ObjectProperty<ViewMode> viewModeProperty() {
        return viewMode;
    }

    public final ViewMode getViewMode() {
        return viewMode.get();
    }

    public final void setViewMode(ViewMode value) {
        viewMode.set(value == null ? ViewMode.CONTINUOUS : value);
    }

    /** In facing modes, show the first page alone on the right, like a book cover. */
    public final BooleanProperty coverPageProperty() {
        return coverPage;
    }

    public final boolean isCoverPage() {
        return coverPage.get();
    }

    public final void setCoverPage(boolean value) {
        coverPage.set(value);
    }

    public final ObjectProperty<FitMode> fitModeProperty() {
        return fitMode;
    }

    public final FitMode getFitMode() {
        return fitMode.get();
    }

    public final void setFitMode(FitMode value) {
        fitMode.set(value == null ? FitMode.NONE : value);
    }

    /** Space between pages and around the document, logical px. */
    public final DoubleProperty pageGapProperty() {
        return pageGap;
    }

    public final double getPageGap() {
        return pageGap.get();
    }

    public final void setPageGap(double value) {
        pageGap.set(value);
    }

    /** Page box to display, one of the {@code Page.BOUNDARY_*} constants; crop box by default. */
    public final IntegerProperty pageBoundaryProperty() {
        return pageBoundary;
    }

    public final int getPageBoundary() {
        return pageBoundary.get();
    }

    public final void setPageBoundary(int value) {
        pageBoundary.set(value);
    }

    /** Whether annotation appearances are part of the page raster. */
    public final BooleanProperty paintAnnotationsProperty() {
        return paintAnnotations;
    }

    public final boolean isPaintAnnotations() {
        return paintAnnotations.get();
    }

    public final void setPaintAnnotations(boolean value) {
        paintAnnotations.set(value);
    }

    /**
     * The text selection, null for none.  Offsets index each page's reading-order
     * {@code TextSequence}; set it to select programmatically (e.g. a search hit).
     */
    public final ObjectProperty<DocumentSelection> textSelectionProperty() {
        return textSelection;
    }

    public final DocumentSelection getTextSelection() {
        return textSelection.get();
    }

    public final void setTextSelection(DocumentSelection value) {
        textSelection.set(value);
    }

    /** What a primary-button drag does; text selection by default. */
    public final ObjectProperty<ToolMode> toolModeProperty() {
        return toolMode;
    }

    public final ToolMode getToolMode() {
        return toolMode.get();
    }

    public final void setToolMode(ToolMode value) {
        toolMode.set(value);
    }

    /** Supplies native overlay content per visible page; see {@link PageOverlayFactory}. */
    public final ObjectProperty<PageOverlayFactory> pageOverlayFactoryProperty() {
        return pageOverlayFactory;
    }

    public final PageOverlayFactory getPageOverlayFactory() {
        return pageOverlayFactory.get();
    }

    public final void setPageOverlayFactory(PageOverlayFactory value) {
        pageOverlayFactory.set(value);
    }

    public final ReadOnlyIntegerProperty pageCountProperty() {
        return pageCount.getReadOnlyProperty();
    }

    public final int getPageCount() {
        return pageCount.get();
    }

    /**
     * True while visible tiles are still being rendered (or a zoom is settling); false once the
     * viewport is fully painted at the current zoom.  Bind a busy indicator to it.
     */
    public final ReadOnlyBooleanProperty renderingProperty() {
        return rendering.getReadOnlyProperty();
    }

    public final boolean isRendering() {
        return rendering.get();
    }

    void setRendering(boolean value) {
        rendering.set(value);
    }
}
