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

import org.icepdf.fx.view.internal.annot.AnnotationHit;
import org.icepdf.fx.view.internal.text.DocumentSearch;

import org.icepdf.fx.view.internal.form.FormController;

import org.icepdf.fx.view.internal.annot.AnnotationEdits;

import javafx.application.Platform;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.ObservableMap;
import javafx.concurrent.Task;
import javafx.event.EventHandler;
import javafx.scene.AccessibleRole;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Control;
import javafx.scene.control.Skin;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.paint.Color;
import org.icepdf.core.pobjects.Destination;
import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.Name;
import org.icepdf.core.pobjects.Page;
import org.icepdf.core.pobjects.acroform.ChoiceFieldDictionary;
import org.icepdf.core.pobjects.actions.Action;
import org.icepdf.core.pobjects.actions.GoToAction;
import org.icepdf.core.pobjects.actions.NamedAction;
import org.icepdf.core.pobjects.annotations.AbstractWidgetAnnotation;
import org.icepdf.core.pobjects.annotations.Annotation;
import org.icepdf.core.pobjects.annotations.ButtonWidgetAnnotation;
import org.icepdf.core.pobjects.annotations.ChoiceWidgetAnnotation;
import org.icepdf.core.pobjects.annotations.LinkAnnotation;
import org.icepdf.core.pobjects.annotations.MarkupAnnotation;
import org.icepdf.core.pobjects.annotations.PopupAnnotation;
import org.icepdf.core.pobjects.annotations.TextMarkupAnnotation;
import org.icepdf.core.pobjects.graphics.text.DocumentSelection;
import org.icepdf.core.pobjects.graphics.text.PageText;
import org.icepdf.core.pobjects.graphics.text.TextSequence;
import org.icepdf.core.pobjects.security.Permissions;
import org.icepdf.core.pobjects.security.SecurityManager;
import org.icepdf.core.search.SearchTerm;
import org.icepdf.core.util.redaction.RedactionRequest;
import org.icepdf.core.util.redaction.Redactor;
import org.icepdf.fx.i18n.Messages;
import org.icepdf.fx.print.DocumentPrinter;
import org.icepdf.fx.print.PrintSettings;
import org.icepdf.fx.signature.SignatureStatus;
import org.icepdf.fx.signature.SignatureVerifier;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

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
 * {@link #dispose()} the view itself when it is thrown away, to stop its render threads.
 * <p>
 * Styleable with the {@code pdf-view} style class.
 */
public class PdfView extends Control {

    private static final Logger logger = Logger.getLogger(PdfView.class.getName());

    public static final double MIN_ZOOM = 0.05;

    /**
     * The most pages {@link #copySelection} copies, 250 by default as in the Swing viewer: every
     * page's text is extracted for a copy, and a select all of a 50,000 page document is minutes of
     * work and a clipboard string of hundreds of MB.  A copy spanning more pages is refused with a
     * {@link PdfViewErrorEvent} saying so.  {@link #selectedTextAsync} itself is not limited.
     */
    public static final String MAX_COPY_PAGES_PROPERTY = "org.icepdf.fx.copy.maxPages";
    public static final int DEFAULT_MAX_COPY_PAGES = 250;
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
            // quarter turns only: page layout, hit-testing and the caret assume upright or sideways pages.
            double quarter = Double.isFinite(value) ? Math.round(value / 90) * 90.0 : 0;
            double normalised = quarter % 360;
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
    private final BooleanProperty showSearchHits = new SimpleBooleanProperty(this, "showSearchHits", true);
    private final BooleanProperty busy = new SimpleBooleanProperty(this, "busy", false);
    private final ObjectProperty<PageOverlayFactory> pageOverlayFactory =
            new SimpleObjectProperty<>(this, "pageOverlayFactory");
    private final ReadOnlyIntegerWrapper pageCount = new ReadOnlyIntegerWrapper(this, "pageCount", 0);
    private final ReadOnlyBooleanWrapper rendering = new ReadOnlyBooleanWrapper(this, "rendering", false);
    private final ReadOnlyIntegerWrapper contentVersion = new ReadOnlyIntegerWrapper(this, "contentVersion", 0);
    // search: hits arrive progressively from a worker; hitsByPage indexes them for drawing.
    private final ObservableList<SearchHit> searchHits = FXCollections.observableArrayList();
    private final ObservableList<SearchHit> searchHitsView = FXCollections.unmodifiableObservableList(searchHits);
    private final Map<Integer, List<SearchHit>> hitsByPage = new HashMap<>();
    private final ReadOnlyIntegerWrapper currentSearchHitIndex =
            new ReadOnlyIntegerWrapper(this, "currentSearchHitIndex", -1);
    private final ReadOnlyBooleanWrapper searching = new ReadOnlyBooleanWrapper(this, "searching", false);
    private final ReadOnlyDoubleWrapper searchProgress = new ReadOnlyDoubleWrapper(this, "searchProgress", 0);
    private Thread searchThread;
    private int searchGeneration;
    private boolean searchAutoSelect;
    private int searchStartPage;

    private final ReadOnlyObjectWrapper<Annotation> selectedAnnotation =
            new ReadOnlyObjectWrapper<>(this, "selectedAnnotation");
    private final ObjectProperty<EventHandler<? super AnnotationActionEvent>> onAnnotationAction = new ObjectPropertyBase<>() {
        @Override
        protected void invalidated() {
            setEventHandler(AnnotationActionEvent.ANNOTATION_ACTION, get());
        }

        @Override
        public Object getBean() {
            return PdfView.this;
        }

        @Override
        public String getName() {
            return "onAnnotationAction";
        }
    };
    // the terms of the last search, for search-and-redact.
    private List<SearchTerm> searchTerms = List.of();
    private final AnnotationEdits.History history = new AnnotationEdits.History();
    private final BooleanProperty formFieldsEditable = new SimpleBooleanProperty(this, "formFieldsEditable", true);
    private final BooleanProperty highlightFormFields = new SimpleBooleanProperty(this, "highlightFormFields", false);
    private final ObjectProperty<EventHandler<? super FormFieldEvent>> onFormFieldChanged = new ObjectPropertyBase<>() {
        @Override
        protected void invalidated() {
            setEventHandler(FormFieldEvent.FIELD_CHANGED, get());
        }

        @Override
        public Object getBean() {
            return PdfView.this;
        }

        @Override
        public String getName() {
            return "onFormFieldChanged";
        }
    };
    private final ReadOnlyObjectWrapper<AbstractWidgetAnnotation> focusedField =
            new ReadOnlyObjectWrapper<>(this, "focusedField");
    private final StringProperty annotationAuthor =
            new SimpleStringProperty(this, "annotationAuthor", System.getProperty("user.name", ""));
    private final BooleanProperty newAnnotationsPrivate =
            new SimpleBooleanProperty(this, "newAnnotationsPrivate", false);
    private final ObjectProperty<Color> annotationColor =
            new SimpleObjectProperty<>(this, "annotationColor");
    private final ObservableMap<ToolMode, Color> toolColors = FXCollections.observableHashMap();
    private final ReadOnlyBooleanWrapper canUndo = new ReadOnlyBooleanWrapper(this, "canUndo", false);
    private final ReadOnlyBooleanWrapper canRedo = new ReadOnlyBooleanWrapper(this, "canRedo", false);
    private final ObjectProperty<Function<Annotation, ContextMenu>>
            annotationMenuFactory = new SimpleObjectProperty<>(this, "annotationMenuFactory");
    private final ObjectProperty<EventHandler<? super PdfViewEvent>> onAnnotationsChanged = new ObjectPropertyBase<>() {
        @Override
        protected void invalidated() {
            setEventHandler(PdfViewEvent.ANNOTATIONS_CHANGED, get());
        }

        @Override
        public Object getBean() {
            return PdfView.this;
        }

        @Override
        public String getName() {
            return "onAnnotationsChanged";
        }
    };
    private final ReadOnlyBooleanWrapper modified = new ReadOnlyBooleanWrapper(this, "modified", false);
    // what the document's permissions allow; readOnly can take editing away on top of them.
    private boolean annotatePermitted = true;
    private boolean fillPermitted = true;
    private final BooleanProperty readOnly = new SimpleBooleanProperty(this, "readOnly", false) {
        @Override
        protected void invalidated() {
            if (get()) commitFieldEdit();
            applyEditing();
        }
    };
    private final ReadOnlyBooleanWrapper pageNotDrawn = new ReadOnlyBooleanWrapper(this, "pageNotDrawn", false);
    private final ReadOnlyBooleanWrapper lowMemory = new ReadOnlyBooleanWrapper(this, "lowMemory", false);
    // what an encrypted document's permissions (/P) allow the user; all true for an unencrypted one.
    private final ReadOnlyBooleanWrapper copyAllowed = new ReadOnlyBooleanWrapper(this, "copyAllowed", true);
    private final ReadOnlyBooleanWrapper annotationEditingAllowed =
            new ReadOnlyBooleanWrapper(this, "annotationEditingAllowed", true);
    private final ReadOnlyBooleanWrapper formFillingAllowed =
            new ReadOnlyBooleanWrapper(this, "formFillingAllowed", true);
    private final ReadOnlyBooleanWrapper printAllowed = new ReadOnlyBooleanWrapper(this, "printAllowed", false);
    // signatures: checked off the FX thread when a document opens (verifySignaturesOnOpen).
    private final ObservableList<SignatureStatus> signatures = FXCollections.observableArrayList();
    private final ObservableList<SignatureStatus> signaturesView =
            FXCollections.unmodifiableObservableList(signatures);
    private final ReadOnlyBooleanWrapper verifyingSignatures =
            new ReadOnlyBooleanWrapper(this, "verifyingSignatures", false);
    private final BooleanProperty verifySignaturesOnOpen =
            new SimpleBooleanProperty(this, "verifySignaturesOnOpen", true);
    private final ObjectProperty<EventHandler<? super SignatureEvent>> onSignatureClicked = new ObjectPropertyBase<>() {
        @Override
        protected void invalidated() {
            setEventHandler(SignatureEvent.SIGNATURE_CLICKED, get());
        }

        @Override
        public Object getBean() {
            return PdfView.this;
        }

        @Override
        public String getName() {
            return "onSignatureClicked";
        }
    };
    private int signatureGeneration;
    private final ReadOnlyBooleanWrapper highQualityPrintAllowed =
            new ReadOnlyBooleanWrapper(this, "highQualityPrintAllowed", false);

    private final ObjectProperty<DocumentSelection> textSelection =
            new SimpleObjectProperty<>(this, "textSelection");
    private final ObjectProperty<ToolMode> toolMode = new SimpleObjectProperty<>(this, "toolMode",
            ToolMode.TEXT_SELECT) {
        @Override
        public void set(ToolMode value) {
            // annotation tools are unavailable when the document doesn't permit annotating.
            super.set(value == null || !toolPermitted(value) ? ToolMode.TEXT_SELECT : value);
        }
    };

    public PdfView() {
        getStyleClass().add("pdf-view");
        setFocusTraversable(true);
        // a screen reader reads the view as a read-only text area over a page (see PdfViewAccessibility).
        setAccessibleRole(AccessibleRole.TEXT_AREA);
        setAccessibleRoleDescription(Messages.get("pdfView.a11y.role"));
        document.addListener((obs, old, doc) -> {
            clearSearch();
            selectedAnnotation.set(null);
            focusedField.set(null);
            history.clear();
            updateHistoryState();
            setTextSelection(null);
            pageCount.set(doc != null ? doc.getNumberOfPages() : 0);
            setCurrentPageIndex(0);
            updatePermissions(doc);
            signatureGeneration++;
            signatures.clear();
            verifyingSignatures.set(false);
            pageNotDrawn.set(false);
            lowMemory.set(false);
            if (doc != null && isVerifySignaturesOnOpen()) verifySignatures();
            refreshModified();
        });
    }

    /**
     * Stops the view's background work (rendering, text loading, search) and releases its rasters.
     * Call it once the view is out of the scene and no longer wanted.  The document is not touched:
     * the caller still owns it.  A disposed view that is shown again gets a new skin and works as
     * before.
     */
    public void dispose() {
        clearSearch();
        setSkin(null);
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

    // ---- annotations ----------------------------------------------------------------------

    /** The annotation selected for editing (outline and handles), or null. */
    public final ReadOnlyObjectProperty<Annotation> selectedAnnotationProperty() {
        return selectedAnnotation.getReadOnlyProperty();
    }

    public final Annotation getSelectedAnnotation() {
        return selectedAnnotation.get();
    }

    /** Selects an annotation as a click would; null clears.  Popups and form widgets aren't selectable. */
    public void selectAnnotation(Annotation annotation) {
        selectedAnnotation.set(annotation);
    }

    public void clearAnnotationSelection() {
        selectedAnnotation.set(null);
    }

    /**
     * The topmost visible annotation under a point in this control's coordinates (popups and form
     * widgets excluded), or empty.  Only pages already rendered are searched.
     */
    public Optional<Annotation> annotationAt(double x, double y) {
        if (!(getSkin() instanceof PdfViewSkin skin)) return Optional.empty();
        AnnotationHit hit = skin.annotationAt(x, y);
        return hit != null ? Optional.of(hit.annotation()) : Optional.empty();
    }

    /**
     * Opens or closes a markup annotation's popup note; the state is saved in the document (/Open).
     * No-op for an annotation without a popup, and while {@link #isReadOnly() read only}: it is a
     * change to the document like any other.
     */
    public void setPopupOpen(MarkupAnnotation markup, boolean open) {
        PopupAnnotation popup = markup != null ? markup.getPopupAnnotation() : null;
        if (popup == null || isReadOnly() || !(getSkin() instanceof PdfViewSkin skin)) return;
        AnnotationHit hit = skin.annotations().hitOf(markup);
        if (hit == null) return;
        Page page = getDocument().getPageTree().getPage(hit.pageIndex());
        skin.annotationLocker().withAnnotationLock(hit.pageIndex(), () -> {
            popup.setOpen(open);
            page.updateAnnotation(popup);
        });
        skin.refreshAnnotationChrome();
        skin.requestRefresh();
    }

    /** Author (/T) written on annotations the user creates; the system user name by default. */
    public final StringProperty annotationAuthorProperty() {
        return annotationAuthor;
    }

    public final String getAnnotationAuthor() {
        return annotationAuthor.get();
    }

    public final void setAnnotationAuthor(String author) {
        annotationAuthor.set(author);
    }

    /**
     * Whether markup annotations the user creates are marked private (the viewer's own
     * {@link Annotation#FLAG_PRIVATE_CONTENTS} /F bit, set on the annotation and its popup); false by
     * default.  Redaction marks are never made private.
     * <p>
     * Private annotations are a viewer convention, not protection: with
     * {@code org.icepdf.core.page.annotation.privateProperty.enabled} set, ICEpdf leaves out other
     * authors' private annotations when it loads a page, but their text is still in the file and
     * other viewers show them.  Core compares the author with {@code user.name}, so keep
     * {@link #annotationAuthorProperty()} at its default while using private annotations, or the
     * user's own private annotations are hidden on reopen.
     */
    public final BooleanProperty newAnnotationsPrivateProperty() {
        return newAnnotationsPrivate;
    }

    public final boolean isNewAnnotationsPrivate() {
        return newAnnotationsPrivate.get();
    }

    public final void setNewAnnotationsPrivate(boolean value) {
        newAnnotationsPrivate.set(value);
    }

    /** Colour for new annotations; null uses each tool's default (yellow highlights, red shapes, ...). */
    public final ObjectProperty<Color> annotationColorProperty() {
        return annotationColor;
    }

    public final Color getAnnotationColor() {
        return annotationColor.get();
    }

    public final void setAnnotationColor(Color color) {
        annotationColor.set(color);
    }

    /**
     * Colours for new annotations by tool, ahead of {@link #annotationColorProperty()}: a highlight
     * tool set to green makes green highlights whatever the general colour is.  Tools without an
     * entry use the general colour, or their {@linkplain #defaultToolColor default}.
     */
    public final ObservableMap<ToolMode, Color> getToolColors() {
        return toolColors;
    }

    /** The colour {@code mode} gives a new annotation now: its tool colour, the general colour, or its default. */
    public final Color toolColor(ToolMode mode) {
        Color color = toolColors.get(mode);
        if (color == null) color = getAnnotationColor();
        return color != null ? color : defaultToolColor(mode);
    }

    /** A tool's colour when nothing is set: yellow highlights and notes, blue underlines, black text, red otherwise. */
    public static Color defaultToolColor(ToolMode mode) {
        return switch (mode) {
            case HIGHLIGHT, NOTE -> Color.rgb(255, 255, 0);
            case UNDERLINE -> Color.rgb(0, 102, 255);
            case FREE_TEXT -> Color.BLACK;
            default -> Color.rgb(230, 0, 0);
        };
    }

    /** Highlights the selected text (one annotation per page), clearing the selection; undoable. */
    public int highlightSelection() {
        return getSkin() instanceof PdfViewSkin skin ? skin.annotations().markupSelection(TextMarkupAnnotation.SUBTYPE_HIGHLIGHT) : 0;
    }

    /** Underlines the selected text; see {@link #highlightSelection()}. */
    public int underlineSelection() {
        return getSkin() instanceof PdfViewSkin skin ? skin.annotations().markupSelection(TextMarkupAnnotation.SUBTYPE_UNDERLINE) : 0;
    }

    /** Strikes out the selected text; see {@link #highlightSelection()}. */
    public int strikeOutSelection() {
        return getSkin() instanceof PdfViewSkin skin ? skin.annotations().markupSelection(TextMarkupAnnotation.SUBTYPE_STRIKE_OUT) : 0;
    }

    /** Puts a wavy underline under the selected text; see {@link #highlightSelection()}. */
    public int squigglySelection() {
        return getSkin() instanceof PdfViewSkin skin ? skin.annotations().markupSelection(TextMarkupAnnotation.SUBTYPE_SQUIGGLY) : 0;
    }

    // ---- forms ----------------------------------------------------------------------------

    /** Whether form fields can be filled in (in the text-select and hand tools).  True by default. */
    public final BooleanProperty formFieldsEditableProperty() {
        return formFieldsEditable;
    }

    public final boolean isFormFieldsEditable() {
        return formFieldsEditable.get();
    }

    public final void setFormFieldsEditable(boolean editable) {
        formFieldsEditable.set(editable);
    }

    /** Tints fillable fields (as Acrobat's "highlight existing fields"); drawn over the page, never in it. */
    public final BooleanProperty highlightFormFieldsProperty() {
        return highlightFormFields;
    }

    public final boolean isHighlightFormFields() {
        return highlightFormFields.get();
    }

    public final void setHighlightFormFields(boolean highlight) {
        highlightFormFields.set(highlight);
    }

    /** The form field with input focus, or null. */
    public final ReadOnlyObjectProperty<AbstractWidgetAnnotation> focusedFieldProperty() {
        return focusedField.getReadOnlyProperty();
    }

    public final AbstractWidgetAnnotation getFocusedField() {
        return focusedField.get();
    }

    /** Gives a field input focus and scrolls it into view; null clears. */
    public void focusField(AbstractWidgetAnnotation widget) {
        // reveal first, so the field's page is laid out when its editor opens.
        if (widget != null && getSkin() instanceof PdfViewSkin skin) {
            skin.fields().revealField(widget);
            // already focused (an Esc closed its editor): focusing again reopens it, as a click does.
            if (widget == getFocusedField()) {
                skin.fields().reopenEditor(widget);
                return;
            }
        }
        focusedField.set(widget);
    }

    public void clearFieldFocus() {
        focusedField.set(null);
    }

    /** Moves focus to the next fillable field in tab order (across pages), wrapping. */
    public void focusNextField() {
        if (getSkin() instanceof PdfViewSkin skin) focusField(skin.fields().adjacentField(getFocusedField(), false));
    }

    /** Moves focus to the previous fillable field in tab order, wrapping. */
    public void focusPreviousField() {
        if (getSkin() instanceof PdfViewSkin skin) focusField(skin.fields().adjacentField(getFocusedField(), true));
    }

    // ---- form values ----------------------------------------------------------------------------

    /**
     * Called for every form field value change: typing, clicks and choices, resets,
     * {@link #setFieldValue}, undo and redo ({@link FormFieldEvent#FIELD_CHANGED}).  For
     * applications tracking dirty state or validating.
     */
    public final ObjectProperty<EventHandler<? super FormFieldEvent>> onFormFieldChangedProperty() {
        return onFormFieldChanged;
    }

    public final void setOnFormFieldChanged(EventHandler<? super FormFieldEvent> handler) {
        onFormFieldChanged.set(handler);
    }

    public final EventHandler<? super FormFieldEvent> getOnFormFieldChanged() {
        return onFormFieldChanged.get();
    }

    /**
     * A field's value by fully-qualified name: a text field's text; a check box's or radio group's
     * on-state name ("Off" when off); a choice's export value, or a List of them for a multi-select
     * list.  Null if there is no such field (or a choice has nothing chosen).
     */
    public Object getFieldValue(String name) {
        List<FormController.Located> widgets = fieldWidgets(name);
        return widgets.isEmpty() ? null : FormController.valueOf(widgets.get(0).widget());
    }

    /** The fully-qualified names of the document's form fields, in page and /Annots order. */
    public List<String> getFieldNames() {
        Set<String> names = new LinkedHashSet<>();
        forEachWidget(w -> names.add(FormController.fieldNameOf(w.widget())));
        return new ArrayList<>(names);
    }

    /**
     * Sets a field's value, as the user would - appearances regenerate, the change is undoable and
     * reported to {@link #onFormFieldChangedProperty()}.  Read-only fields can be set (it's the
     * application asking).
     *
     * @param value text field: any value (its toString); check box: a Boolean, or the on-state name /
     *              "Off"; radio group: the on-state name of the kid to select; combo or list: an
     *              export value or label, or a Collection of them for a multi-select list (an
     *              editable combo also takes free text)
     * @return false if there is no such field, or the value doesn't fit it (an unknown option, an
     * unknown radio state, a signature or push button)
     */
    public boolean setFieldValue(String name, Object value) {
        List<FormController.Located> widgets = fieldWidgets(name);
        if (widgets.isEmpty()) return false;
        FormController.Located first = widgets.get(0);
        PdfViewSkin skin = getSkin() instanceof PdfViewSkin s ? s : null;
        if (skin != null) skin.fields().closeEditor(false);
        FormController forms = skin != null ? skin.fields().forms() : new FormController((page, mutation) -> mutation.run());
        java.awt.geom.AffineTransform toPage = skin != null ? skin.toPageSpace(first.page())
                : first.page().getToPageSpaceTransform(getPageBoundary(), 0, 1f);
        AnnotationEdits.Edit edit;
        switch (FormController.kindOf(first.widget())) {
            case TEXT, PASSWORD -> edit = forms.setText(first, value == null ? "" : value.toString(), toPage);
            case CHECK -> {
                boolean on = value instanceof Boolean b ? b : value != null && !"Off".equals(value.toString());
                ButtonWidgetAnnotation box =
                        (ButtonWidgetAnnotation) first.widget();
                edit = box.isOn() == on ? null : forms.toggleCheck(first, toPage);
            }
            case RADIO -> {
                FormController.Located target = null;
                for (FormController.Located kid : widgets) {
                    Name onName = FormController.onNameOf(
                            (ButtonWidgetAnnotation) kid.widget());
                    if (onName != null && value != null && onName.getName().equals(value.toString())) target = kid;
                }
                if (target == null) return false;
                edit = ((ButtonWidgetAnnotation) target.widget()).isOn()
                        ? null : forms.selectRadio(target, widgets, toPage);
            }
            case COMBO, LIST -> {
                ChoiceFieldDictionary choice =
                        ((ChoiceWidgetAnnotation) first.widget()).getFieldDictionary();
                List<String> wanted = new ArrayList<>();
                if (value instanceof Collection<?> many) {
                    if (!choice.isMultiSelect() && many.size() > 1) return false;
                    for (Object o : many) wanted.add(String.valueOf(o));
                } else if (value != null) {
                    wanted.add(value.toString());
                }
                List<Integer> indexes = FormController.indexesOf(choice.getOptions(), wanted);
                if (indexes.size() == wanted.size()) {
                    edit = forms.chooseIndexes(first, indexes, toPage);
                } else if (wanted.size() == 1 && choice.getChoiceFieldType()
                        == ChoiceFieldDictionary.ChoiceFieldType.CHOICE_EDITABLE_COMBO) {
                    edit = forms.choose(first, wanted.get(0), toPage);
                } else {
                    return false;
                }
            }
            default -> {
                return false;
            }
        }
        recordEdit(edit);
        return true;
    }

    /** Resets every fillable form field to its default value (/DV), as one undoable edit. */
    public void resetForm() {
        if (getSkin() instanceof PdfViewSkin skin) {
            skin.fields().resetFields(null);
            return;
        }
        List<FormController.Located> fields = new ArrayList<>();
        forEachWidget(w -> {
            FormController.FieldKind kind = FormController.kindOf(w.widget());
            if (FormController.isFillable(w.widget()) && kind != FormController.FieldKind.PUSH) fields.add(w);
        });
        if (fields.isEmpty()) return;
        recordEdit(new FormController((page, mutation) -> mutation.run()).reset(fields,
                fields.get(0).page().getToPageSpaceTransform(getPageBoundary(), 0, 1f)));
    }

    /** Every widget of a field, by fully-qualified name (read-only and hidden ones too). */
    private List<FormController.Located> fieldWidgets(String name) {
        List<FormController.Located> out = new ArrayList<>();
        if (name == null) return out;
        forEachWidget(w -> {
            if (name.equals(FormController.fieldNameOf(w.widget()))) out.add(w);
        });
        return out;
    }

    private void forEachWidget(Consumer<FormController.Located> action) {
        Document document = getDocument();
        if (document == null || document.getCatalog().getInteractiveForm() == null) return;
        for (int i = 0; i < document.getNumberOfPages(); i++) {
            Page page = document.getPageTree().getPage(i);
            List<Annotation> annotations = page.getAnnotations();
            if (annotations == null) continue;
            for (Annotation a : annotations) {
                if (a instanceof AbstractWidgetAnnotation w && !w.isDeleted()) {
                    action.accept(new FormController.Located(i, page, w));
                }
            }
        }
    }

    private void fireFieldChanges(AnnotationEdits.Edit edit, boolean undone) {
        if (!(edit instanceof FormController.FieldEdit fieldEdit)) return;
        for (FormController.FieldChange c : fieldEdit.changes()) {
            fireEvent(undone ? new FormFieldEvent(this, this, c.widget(), c.name(), c.newValue(), c.oldValue())
                    : new FormFieldEvent(this, this, c.widget(), c.name(), c.oldValue(), c.newValue()));
        }
    }

    /**
     * Deletes the selected annotation (and its popup); undoable.  No-op if none, it's locked, or the
     * document doesn't permit annotating.
     */
    public void deleteSelectedAnnotation() {
        if (!(getSkin() instanceof PdfViewSkin skin)) return;
        AnnotationHit hit = skin.annotations().selectedHit();
        if (hit == null || !skin.annotations().canEdit(hit.annotation())) return;
        Page page = getDocument().getPageTree().getPage(hit.pageIndex());
        recordEdit(AnnotationEdits.delete(skin.annotationLocker(), page, hit.pageIndex(), hit.annotation()));
        clearAnnotationSelection();
    }

    /**
     * Deletes an annotation (and its popup), on any page, shown or not; undoable.  No-op if it isn't
     * on a page of this document, it's locked, or the document doesn't permit annotating.
     */
    public void deleteAnnotation(Annotation annotation) {
        if (annotation == null || !(getSkin() instanceof PdfViewSkin skin) || !skin.annotations().canEdit(annotation)) return;
        int pageIndex = skin.annotations().pageIndexOf(annotation);
        if (pageIndex < 0) return;
        Page page = getDocument().getPageTree().getPage(pageIndex);
        recordEdit(AnnotationEdits.delete(skin.annotationLocker(), page, pageIndex, annotation));
        if (getSelectedAnnotation() == annotation) clearAnnotationSelection();
    }

    /**
     * Changes properties of an annotation as one undoable edit; it may be on any page, shown or not.
     * No-op if the annotation isn't on a page of this document, it's locked, the document doesn't
     * permit annotating, or its type has no editable properties ({@link AnnotationProperties#supports}).
     */
    public void applyAnnotationProperties(Annotation annotation, AnnotationProperties change) {
        if (change == null || !AnnotationProperties.supports(annotation)) return;
        if (!(getSkin() instanceof PdfViewSkin skin) || !skin.annotations().canEdit(annotation)) return;
        int pageIndex = skin.annotations().pageIndexOf(annotation);
        if (pageIndex < 0) return;
        Page page = getDocument().getPageTree().getPage(pageIndex);
        recordEdit(AnnotationEdits.properties(skin.annotationLocker(), page, pageIndex, annotation, change,
                skin.toPageSpace(page)));
    }

    /**
     * Replaces a comment's text (/Contents; a text box's text, redrawn) as one undoable edit, on any
     * page, shown or not.  No-op when unchanged, or as {@link #applyAnnotationProperties}.
     */
    public void setAnnotationContents(MarkupAnnotation annotation, String contents) {
        if (annotation == null || contents == null) return;
        if (!(getSkin() instanceof PdfViewSkin skin) || !skin.annotations().canEdit(annotation)) return;
        if (contents.equals(annotation.getContents() != null ? annotation.getContents() : "")) return;
        int pageIndex = skin.annotations().pageIndexOf(annotation);
        if (pageIndex < 0) return;
        Page page = getDocument().getPageTree().getPage(pageIndex);
        recordEdit(annotation instanceof org.icepdf.core.pobjects.annotations.FreeTextAnnotation freeText
                ? AnnotationEdits.freeTextContents(skin.annotationLocker(), page, pageIndex, freeText, contents,
                skin.toPageSpace(page))
                : AnnotationEdits.contents(skin.annotationLocker(), page, pageIndex, annotation, contents));
    }

    /**
     * Marks markup annotations private or public (see {@link #newAnnotationsPrivateProperty()}), as
     * one undoable edit.  Annotations already so, locked, or not on a page of this document are
     * skipped; they may be on any page, shown or not.  No-op when the document doesn't permit
     * annotating.
     *
     * @return how many annotations changed
     */
    public int setAnnotationsPrivate(Collection<? extends MarkupAnnotation> annotations, boolean priv) {
        if (annotations == null || !(getSkin() instanceof PdfViewSkin skin)) return 0;
        List<MarkupAnnotation> changing = new ArrayList<>();
        for (MarkupAnnotation markup : new LinkedHashSet<>(annotations)) {
            if (markup != null && markup.getFlagPrivateContents() != priv && skin.annotations().canEdit(markup)) {
                changing.add(markup);
            }
        }
        int[] pages = skin.annotations().pageIndexesOf(changing);
        List<AnnotationEdits.Edit> edits = new ArrayList<>();
        for (int i = 0; i < pages.length; i++) {
            if (pages[i] < 0) continue;
            Page page = getDocument().getPageTree().getPage(pages[i]);
            edits.add(AnnotationEdits.privacy(skin.annotationLocker(), page, pages[i], changing.get(i), priv));
        }
        if (edits.isEmpty()) return 0;
        recordEdit(edits.size() == 1 ? edits.get(0) : AnnotationEdits.group(edits, null, null));
        return edits.size();
    }

    /**
     * Builds the context menu shown when the user right-clicks an editable annotation (it is selected
     * first).  Return null for no menu.  With no factory set right-click does nothing.
     */
    public final ObjectProperty<Function<Annotation, ContextMenu>>
            annotationMenuFactoryProperty() {
        return annotationMenuFactory;
    }

    public final void setAnnotationMenuFactory(
            Function<Annotation, ContextMenu> factory) {
        annotationMenuFactory.set(factory);
    }

    public final Function<Annotation, ContextMenu> getAnnotationMenuFactory() {
        return annotationMenuFactory.get();
    }

    /**
     * Undoes the last annotation edit (move, resize, delete, add).  If it fails part way, the undo
     * history is cleared (it no longer matches the document) and a {@link PdfViewErrorEvent} says so.
     */
    public void undo() {
        if (isReadOnly()) return;
        AnnotationEdits.Edit edit;
        try {
            edit = history.undo();
        } catch (AnnotationEdits.StepFailedException e) {
            historyFailed(e, "pdfView.undoFailed");
            return;
        }
        afterHistory(edit);
        fireFieldChanges(edit, true);
    }

    /** Redoes the last undone annotation edit; a failure is handled as {@link #undo()}'s is. */
    public void redo() {
        if (isReadOnly()) return;
        AnnotationEdits.Edit edit;
        try {
            edit = history.redo();
        } catch (AnnotationEdits.StepFailedException e) {
            historyFailed(e, "pdfView.redoFailed");
            return;
        }
        afterHistory(edit);
        fireFieldChanges(edit, false);
    }

    /** An undo or redo threw: the rest of the history can't be trusted against the document any more. */
    private void historyFailed(AnnotationEdits.StepFailedException e, String key) {
        logger.log(Level.WARNING, "Undo/redo failed", e.getCause());
        history.clear();
        // repaint what the edit touched, whatever state it was left in.
        afterHistory(e.edit());
        fireEvent(new PdfViewErrorEvent(this, this, Messages.format(key, Messages.reason(e.getCause())), e.getCause()));
    }

    public final ReadOnlyBooleanProperty canUndoProperty() {
        return canUndo.getReadOnlyProperty();
    }

    public final boolean isCanUndo() {
        return canUndo.get();
    }

    /**
     * Called when an annotation or form field is added, removed or edited through the view
     * (including undo and redo) - {@link PdfViewEvent#ANNOTATIONS_CHANGED} - so a list of the
     * document's annotations knows to refresh.  Edits made straight on the core objects aren't seen.
     */
    public final ObjectProperty<EventHandler<? super PdfViewEvent>> onAnnotationsChangedProperty() {
        return onAnnotationsChanged;
    }

    public final void setOnAnnotationsChanged(EventHandler<? super PdfViewEvent> handler) {
        onAnnotationsChanged.set(handler);
    }

    public final EventHandler<? super PdfViewEvent> getOnAnnotationsChanged() {
        return onAnnotationsChanged.get();
    }

    /**
     * True while the document has changes not yet saved (core's
     * {@code StateManager.hasUnsavedUserChanges()}), or a form field holds typed text not yet applied
     * ({@link #hasPendingFieldEdit()}).  Updated after every edit made through the view, undo and
     * redo, while a field is typed in, and when the document changes; after changing core objects
     * directly, or after saving in place, call {@link #refreshModified()}.
     */
    public final ReadOnlyBooleanProperty modifiedProperty() {
        return modified.getReadOnlyProperty();
    }

    public final boolean isModified() {
        return modified.get();
    }

    /** Reads {@link #modifiedProperty()} again from the document. */
    public void refreshModified() {
        Document doc = getDocument();
        modified.set(doc != null && (doc.getStateManager().hasUnsavedUserChanges() || hasPendingFieldEdit()));
    }

    /**
     * True while a form field's editor holds a value the user changed that isn't in the document
     * yet: it is applied on Enter, Tab or leaving the field, or by {@link #commitFieldEdit()}.
     */
    public boolean hasPendingFieldEdit() {
        return getSkin() instanceof PdfViewSkin skin && skin.fields().isEditPending();
    }

    /**
     * Applies what is being typed in a form field to the document now, as Tab would, and closes
     * the editor.  Call it before the document is written, printed or signed - a shortcut such as
     * Ctrl+S doesn't take the focus out of the field, so nothing else commits it.
     */
    public void commitFieldEdit() {
        if (getSkin() instanceof PdfViewSkin skin) skin.fields().closeEditor(true);
    }

    /**
     * True once part of a page of this document couldn't be drawn (a broken content stream, say):
     * it shows blank.  The cause is logged.  Reset when the document changes.
     */
    public final ReadOnlyBooleanProperty pageNotDrawnProperty() {
        return pageNotDrawn.getReadOnlyProperty();
    }

    /**
     * True once memory ran short drawing this document: pages are drawn one at a time and may
     * take longer, and a page that still doesn't fit is left blank.  Reset when the document changes.
     */
    public final ReadOnlyBooleanProperty lowMemoryProperty() {
        return lowMemory.getReadOnlyProperty();
    }

    /** A tile or preview failed (the skin's renderer, FX thread). */
    void renderTrouble(boolean outOfMemory) {
        if (outOfMemory) lowMemory.set(true);
        else pageNotDrawn.set(true);
    }

    public final ReadOnlyBooleanProperty canRedoProperty() {
        return canRedo.getReadOnlyProperty();
    }

    public final boolean isCanRedo() {
        return canRedo.get();
    }

    /** Records an edit already applied; re-renders that page's annotation layers. */
    void recordEdit(AnnotationEdits.Edit edit) {
        if (edit == null) return;
        history.push(edit);
        afterHistory(edit);
        fireFieldChanges(edit, false);
    }

    private void afterHistory(AnnotationEdits.Edit edit) {
        if (edit != null && getSkin() instanceof PdfViewSkin skin) {
            edit.pages().forEach(skin::bumpAnnotationGeneration);
            skin.refreshAnnotationChrome();
        }
        updateHistoryState();
        if (edit != null) {
            refreshModified();
            fireEvent(new PdfViewEvent(this, this, PdfViewEvent.ANNOTATIONS_CHANGED));
        }
    }

    private void updateHistoryState() {
        canUndo.set(history.canUndo() && !isReadOnly());
        canRedo.set(history.canRedo() && !isReadOnly());
    }

    /**
     * Called with annotation actions the view doesn't perform itself (URI, launch, remote GoTo,
     * JavaScript, ...) - {@link AnnotationActionEvent#ANNOTATION_ACTION}.  In-document navigation is
     * handled by the view.
     */
    public final ObjectProperty<EventHandler<? super AnnotationActionEvent>> onAnnotationActionProperty() {
        return onAnnotationAction;
    }

    public final void setOnAnnotationAction(EventHandler<? super AnnotationActionEvent> handler) {
        onAnnotationAction.set(handler);
    }

    public final EventHandler<? super AnnotationActionEvent> getOnAnnotationAction() {
        return onAnnotationAction.get();
    }

    /**
     * Performs an annotation's action, as a click on it does: GoTo destinations and the page named
     * actions navigate; anything else goes to {@link #onAnnotationActionProperty()}.  A link with a
     * /Dest and no action navigates to it.
     */
    public void performAnnotationAction(Annotation annotation) {
        if (annotation == null) return;
        performAction(annotation, annotation.getAction());
    }

    /**
     * Performs an action that isn't an annotation's - a bookmark's, say - as
     * {@link #performAnnotationAction} would: GoTo and the page named actions navigate, anything
     * else goes to {@link #onAnnotationActionProperty()} with no annotation.
     */
    public void performAction(Action action) {
        performAction(null, action);
    }

    private void performAction(Annotation annotation, Action action) {
        if (action instanceof GoToAction goTo) {
            navigateTo(goTo.getDestination());
        } else if (action instanceof NamedAction named && named.getNamedAction() != null) {
            Name name = named.getNamedAction();
            if (NamedAction.FIRST_PAGE_KEY.equals(name)) setCurrentPageIndex(0);
            else if (NamedAction.LAST_PAGE_KEY.equals(name)) setCurrentPageIndex(getPageCount() - 1);
            else if (NamedAction.NEXT_PAGE_KEY.equals(name)) setCurrentPageIndex(getCurrentPageIndex() + 1);
            else if (NamedAction.PREV_PAGE_KEY.equals(name)) setCurrentPageIndex(getCurrentPageIndex() - 1);
            else dispatchAction(annotation, action);
        } else if (action != null) {
            dispatchAction(annotation, action);
        } else if (annotation instanceof LinkAnnotation link && link.getDestination() != null) {
            navigateTo(link.getDestination());
        }
    }

    private void dispatchAction(Annotation annotation, Action action) {
        fireEvent(new AnnotationActionEvent(this, this, annotation, action));
    }

    /**
     * Shows a destination: its page, and its top/left when the destination gives them (otherwise the
     * page's top).  Unresolvable destinations are ignored.
     */
    public void navigateTo(Destination destination) {
        Document doc = getDocument();
        if (destination == null || doc == null || destination.getPageReference() == null) return;
        int page = doc.getPageTree().getPageNumber(destination.getPageReference());
        if (page < 0 || page >= getPageCount()) return;
        setCurrentPageIndex(page);
        if (destination.getTop() != null && getSkin() instanceof PdfViewSkin skin) {
            Float left = destination.getLeft();
            skin.alignTop(new PagePoint(page, left != null ? left : 0, destination.getTop()));
        }
    }

    // ---- search ---------------------------------------------------------------------------

    /** Case-insensitive search for a phrase; see {@link #search(SearchTerm...)}. */
    public void search(String text) {
        search(new SearchTerm(text, null, false, false, false));
    }

    /**
     * Searches the whole document on a background thread, replacing any previous search.  Hits
     * appear in {@link #getSearchHits()} page by page as they are found and are highlighted on the
     * pages; the first hit at or after the current page is selected and scrolled into view as soon
     * as it is found.  Every page is loaded to be searched, so a long document takes a while; watch
     * {@link #searchingProperty()} and {@link #searchProgressProperty()}.
     */
    public void search(SearchTerm... terms) {
        clearSearch();
        searchTerms = List.of();
        Document doc = getDocument();
        List<SearchTerm> list = new ArrayList<>();
        for (SearchTerm term : terms) {
            if (term != null && term.getTerm() != null && !term.getTerm().isEmpty()) list.add(term);
        }
        if (doc == null || list.isEmpty()) return;
        searchTerms = List.copyOf(list);
        int generation = searchGeneration;
        int pages = getPageCount();
        searchAutoSelect = true;
        searchStartPage = getCurrentPageIndex();
        searching.set(true);
        searchThread = startWorker("icepdf-fx-search", () -> {
            try {
                DocumentSearch.run(page -> {
                    try {
                        PageText text = doc.getPageViewText(page);
                        return text != null ? text.getTextSequence() : null;
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return null;
                    } catch (RuntimeException e) {
                        // a page whose text can't be read has no hits; the rest are still searched.
                        logger.log(Level.WARNING, "Could not read the text of page " + (page + 1) + " to search it", e);
                        return null;
                    }
                }, list, pages, () -> Thread.currentThread().isInterrupted(), (page, hits, done, total) ->
                        Platform.runLater(() -> {
                            if (generation != searchGeneration) return;
                            if (!hits.isEmpty()) {
                                hitsByPage.put(page, List.copyOf(hits));
                                searchHits.addAll(hits);
                            }
                            searchProgress.set(done / (double) total);
                            if (done == total) searching.set(false);
                            autoSelectSearchHit(done == total);
                        }));
            } catch (RuntimeException e) {
                logger.log(Level.WARNING, "Search failed", e);
            } finally {
                // however the worker ends, a search that is still the current one is over.
                Platform.runLater(() -> {
                    if (generation == searchGeneration && searching.get()) {
                        searching.set(false);
                        autoSelectSearchHit(true);
                    }
                });
            }
        });
    }

    /** Selects the first hit at or after the page the search started from, once one is known. */
    private void autoSelectSearchHit(boolean finished) {
        if (!searchAutoSelect || searchHits.isEmpty()) return;
        for (int i = 0; i < searchHits.size(); i++) {
            if (searchHits.get(i).pageIndex() >= searchStartPage) {
                searchAutoSelect = false;
                selectSearchHit(i);
                return;
            }
        }
        if (finished) {
            searchAutoSelect = false;
            selectSearchHit(0);
        }
    }

    /**
     * Marks every search hit for redaction, one redaction per match (covering each line of it), as
     * one undoable edit; and hands the search terms to the export, so the same words are also
     * masked where they have no place on a page - bookmarks, comments, form values, metadata
     * ({@code RedactionRequest.ofAnnotationsAndTerms}).  Nothing is removed until the document is
     * exported with {@code WriteMode.FULL_UPDATE}.  With no hits in the page text (the terms only
     * match comments, form fields, bookmarks...) only the terms are recorded, still undoably.  The
     * text is read on a worker.
     *
     * @return completes on the FX thread with the number of redactions made - 0 when only the terms
     * were recorded, or nothing was done (a search running, no search, the document changed, or one
     * that doesn't permit annotating); see {@link #hasTermRedaction()}
     */
    public CompletableFuture<Integer> redactSearchHits() {
        Document doc = getDocument();
        List<SearchHit> hits = List.copyOf(searchHits);
        List<SearchTerm> terms = searchTerms;
        if (doc == null || terms.isEmpty() || isSearching() || !isAnnotationEditingAllowed()
                || !(getSkin() instanceof PdfViewSkin)) {
            return CompletableFuture.completedFuture(0);
        }
        CompletableFuture<Integer> result = new CompletableFuture<>();
        startWorker("icepdf-fx-redact-search", () -> {
            // each match's line rectangles, page space, from the same text the search ran on.
            Map<SearchHit, List<java.awt.geom.Rectangle2D>> rects = new LinkedHashMap<>();
            try {
                for (SearchHit hit : hits) {
                    PageText text = doc.getPageViewText(hit.pageIndex());
                    TextSequence sequence = text != null ? text.getTextSequence() : null;
                    if (sequence == null) continue;
                    rects.put(hit, new ArrayList<>(sequence.rectsFor(hit.range())));
                }
            } catch (InterruptedException e) {
                Platform.runLater(() -> result.complete(0));
                return;
            } catch (RuntimeException e) {
                Platform.runLater(() -> redactFailed(result, e, List.of(), null));
                return;
            }
            Platform.runLater(() -> {
                if (getDocument() != doc || !(getSkin() instanceof PdfViewSkin skin)) {
                    result.complete(0);
                    return;
                }
                List<AnnotationEdits.Edit> edits = new ArrayList<>();
                RedactionRequest before = doc.getRedactionRequest();
                try {
                    rects.forEach((hit, r) -> {
                        if (!r.isEmpty()) edits.add(skin.annotations().redactPageRects(hit.pageIndex(), r));
                    });
                    Runnable withTerms = () -> Redactor.configure(doc, RedactionRequest.ofAnnotationsAndTerms(terms));
                    Runnable restore = () -> Redactor.configure(doc, before);
                    withTerms.run();
                    recordEdit(AnnotationEdits.group(edits, restore, withTerms));
                    result.complete(edits.size());
                } catch (RuntimeException e) {
                    redactFailed(result, e, edits, () -> Redactor.configure(doc, before));
                }
            });
        });
        return result;
    }

    /**
     * Marking search hits failed: the redactions made so far are taken out again (all or nothing),
     * the user is told ({@link PdfViewErrorEvent}), and the future fails.
     */
    private void redactFailed(CompletableFuture<Integer> result, RuntimeException e, List<AnnotationEdits.Edit> made,
                              Runnable restoreRequest) {
        logger.log(Level.WARNING, "Marking search hits for redaction failed", e);
        for (int i = made.size() - 1; i >= 0; i--) {
            try {
                made.get(i).undo();
            } catch (RuntimeException ignored) {
                // already failing; the history below is refreshed either way.
            }
        }
        if (restoreRequest != null) restoreRequest.run();
        if (!made.isEmpty()) afterHistory(AnnotationEdits.group(made, () -> { }, () -> { }));
        fireEvent(new PdfViewErrorEvent(this, this, Messages.format("pdfView.redactHitsFailed", Messages.reason(e)), e));
        result.completeExceptionally(e);
    }

    /** Starts a one-off background job: a daemon thread below normal priority. */
    private static Thread startWorker(String name, Runnable job) {
        Thread worker = new Thread(job, name);
        worker.setDaemon(true);
        worker.setPriority(Thread.NORM_PRIORITY - 1);
        worker.start();
        return worker;
    }

    /**
     * True when the document has search terms recorded for redaction ({@link #redactSearchHits}):
     * the export will mask them where they have no place on a page, even with no redaction
     * annotations at all.
     */
    public boolean hasTermRedaction() {
        Document doc = getDocument();
        return doc != null && doc.getRedactionRequest() != null && doc.getRedactionRequest().hasTerms();
    }

    /** Stops any running search and removes all hits; the text selection is left alone. */
    public void clearSearch() {
        searchGeneration++;
        if (searchThread != null) {
            searchThread.interrupt();
            searchThread = null;
        }
        searchAutoSelect = false;
        hitsByPage.clear();
        searchHits.clear();
        currentSearchHitIndex.set(-1);
        searching.set(false);
        searchProgress.set(0);
    }

    /** Selects and reveals the next hit, wrapping; the first hit from the current page if none is current. */
    public void nextSearchHit() {
        searchAutoSelect = false;
        selectSearchHit(DocumentSearch.next(searchHits, getCurrentSearchHitIndex(), getCurrentPageIndex()));
    }

    /** Selects and reveals the previous hit, wrapping. */
    public void previousSearchHit() {
        searchAutoSelect = false;
        selectSearchHit(DocumentSearch.previous(searchHits, getCurrentSearchHitIndex(), getCurrentPageIndex()));
    }

    /**
     * Makes hit {@code index} current: it becomes the text selection (so it can be copied) and is
     * scrolled into view, switching page first in the non-continuous modes.
     */
    public void selectSearchHit(int index) {
        if (index < 0 || index >= searchHits.size()) return;
        SearchHit hit = searchHits.get(index);
        currentSearchHitIndex.set(index);
        setTextSelection(DocumentSelection.of(hit.pageIndex(), hit.range().getStart(),
                hit.pageIndex(), hit.range().getEnd()));
        if (!getViewMode().isContinuous()) setCurrentPageIndex(hit.pageIndex());
        ensureVisible(hit.centre());
    }

    /** All hits of the current search in document order; grows while {@link #isSearching()}. */
    public final ObservableList<SearchHit> getSearchHits() {
        return searchHitsView;
    }

    /** Hits on one page, for drawing. */
    List<SearchHit> searchHitsOnPage(int pageIndex) {
        return hitsByPage.getOrDefault(pageIndex, List.of());
    }

    /** Index into {@link #getSearchHits()} of the current hit, or -1. */
    public final ReadOnlyIntegerProperty currentSearchHitIndexProperty() {
        return currentSearchHitIndex.getReadOnlyProperty();
    }

    public final int getCurrentSearchHitIndex() {
        return currentSearchHitIndex.get();
    }

    public final ReadOnlyBooleanProperty searchingProperty() {
        return searching.getReadOnlyProperty();
    }

    public final boolean isSearching() {
        return searching.get();
    }

    /** Fraction of pages searched, 0 to 1. */
    public final ReadOnlyDoubleProperty searchProgressProperty() {
        return searchProgress.getReadOnlyProperty();
    }

    public final double getSearchProgress() {
        return searchProgress.get();
    }

    // ---- selection ------------------------------------------------------------------------

    /** Selects every page's text; no-op without a document. */
    public void selectAll() {
        if (getPageCount() > 0) setTextSelection(DocumentSelection.all(getPageCount()));
    }

    public void clearSelection() {
        setTextSelection(null);
    }

    /**
     * The selected text, paragraph-formatted, extracted off the FX thread (pages the selection
     * spans are loaded as needed).  Completes with "" when nothing is selected or before the control
     * is shown; complete on a background thread.
     */
    public CompletableFuture<String> selectedTextAsync() {
        DocumentSelection selection = getTextSelection();
        if (selection == null || selection.isCollapsed() || !(getSkin() instanceof PdfViewSkin skin)) {
            return CompletableFuture.completedFuture("");
        }
        return skin.selectedTextAsync(selection);
    }

    /**
     * Copies the selected text to the system clipboard once extracted; no-op with nothing selected,
     * or when the document doesn't permit copying ({@link #copyAllowedProperty()}).  A selection
     * over more than {@value #DEFAULT_MAX_COPY_PAGES} pages ({@link #MAX_COPY_PAGES_PROPERTY}) isn't
     * copied; a {@link PdfViewErrorEvent} says why.
     */
    public void copySelection() {
        if (!isCopyAllowed()) return;
        DocumentSelection selection = getTextSelection();
        int maxPages = Integer.getInteger(MAX_COPY_PAGES_PROPERTY, DEFAULT_MAX_COPY_PAGES);
        if (selection != null && !selection.isCollapsed() && selection.endPage() - selection.startPage() + 1 > maxPages) {
            fireEvent(new PdfViewErrorEvent(this, this, Messages.format("pdfView.copyTooManyPages", maxPages), null));
            return;
        }
        selectedTextAsync().thenAccept(text -> {
            if (text.isEmpty()) return;
            Platform.runLater(() -> {
                ClipboardContent content = new ClipboardContent();
                content.putString(text);
                Clipboard.getSystemClipboard().setContent(content);
            });
        });
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

    /**
     * Zero-based page the user is looking at; setting it scrolls that page into view.  In the
     * continuous modes it's the page the view was last taken to (by setting this, a search hit, a
     * link, the caret...) until the user scrolls, then the page showing the most of itself.
     */
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

    /**
     * Clockwise user rotation in degrees, applied on top of the page's /Rotate: 0, 90, 180 or 270.
     * Other values snap to the nearest quarter turn and are normalised to [0, 360).
     */
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

    /**
     * True shows the wait cursor over the pages - for an application reading a file the view will
     * show.  The view also shows it on its own from a new document until its first pages are fully
     * painted, then goes back to the tool's cursor.
     */
    public final BooleanProperty busyProperty() {
        return busy;
    }

    public final boolean isBusy() {
        return busy.get();
    }

    public final void setBusy(boolean value) {
        busy.set(value);
    }

    /**
     * Whether annotation appearances are drawn.  They render in their own layers above the page
     * content, so annotation changes never re-render the page.
     */
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
     * Whether search hits are highlighted on the pages.  Turning it off keeps the hits (and the
     * current one), so turning it back on shows them again without searching again.  True by default.
     */
    public final BooleanProperty showSearchHitsProperty() {
        return showSearchHits;
    }

    public final boolean isShowSearchHits() {
        return showSearchHits.get();
    }

    public final void setShowSearchHits(boolean value) {
        showSearchHits.set(value);
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

    /**
     * Re-renders every page, for when what the document draws has changed underneath the view -
     * an optional content group (layer) switched on or off, say.  The pages already showing stay
     * until their new rendering is ready.
     */
    public void refreshContent() {
        contentVersion.set(contentVersion.get() + 1);
    }

    /**
     * Counts {@link #refreshContent()} calls: anything else that draws the document's pages (page
     * thumbnails) can watch it to know when to draw them again.
     */
    public final ReadOnlyIntegerProperty contentVersionProperty() {
        return contentVersion.getReadOnlyProperty();
    }

    public final int getContentVersion() {
        return contentVersion.get();
    }

    // ---- document permissions -------------------------------------------------------------

    /**
     * Whether the document permits copying its text (permission "copy or extract content").  When
     * false, {@link #copySelection()} and the copy shortcut do nothing; text can still be selected
     * and searched.  {@link #selectedTextAsync()} is not restricted: what an application does with
     * the text it asks for is its own call.
     */
    public final ReadOnlyBooleanProperty copyAllowedProperty() {
        return copyAllowed.getReadOnlyProperty();
    }

    public final boolean isCopyAllowed() {
        return copyAllowed.get();
    }

    /**
     * Whether the document permits adding and changing annotations (permission "add or modify
     * annotations and fill in forms").  When false, the annotation tools fall back to text
     * selection and existing annotations can't be moved, resized or deleted.
     */
    public final ReadOnlyBooleanProperty annotationEditingAllowedProperty() {
        return annotationEditingAllowed.getReadOnlyProperty();
    }

    public final boolean isAnnotationEditingAllowed() {
        return annotationEditingAllowed.get();
    }

    /**
     * Whether the document permits filling in its form fields (either "add or modify annotations and
     * fill in forms" or "fill in form fields").  When false, fields can't be focused or edited by the
     * user, as if {@link #formFieldsEditableProperty()} were false.  {@link #setFieldValue} and
     * {@link #resetForm()} are not restricted: they are the application's own calls.
     */
    public final ReadOnlyBooleanProperty formFillingAllowedProperty() {
        return formFillingAllowed.getReadOnlyProperty();
    }

    public final boolean isFormFillingAllowed() {
        return formFillingAllowed.get();
    }

    /**
     * Whether there is a document and it permits printing.  A document may also permit only
     * low-quality printing; {@link #print} then prints pages as images (see
     * {@link DocumentPrinter#LOW_RESOLUTION_DPI}).
     */
    public final ReadOnlyBooleanProperty printAllowedProperty() {
        return printAllowed.getReadOnlyProperty();
    }

    public final boolean isPrintAllowed() {
        return printAllowed.get();
    }

    /**
     * False when the document permits printing only at low quality: {@link #print} then prints pages
     * as images whatever the settings say.
     */
    public final ReadOnlyBooleanProperty highQualityPrintAllowedProperty() {
        return highQualityPrintAllowed.getReadOnlyProperty();
    }

    public final boolean isHighQualityPrintAllowed() {
        return highQualityPrintAllowed.get();
    }

    // ---- signatures -----------------------------------------------------------------------

    /**
     * The document's signature fields and what checking them found, in form order; empty until a
     * check completes (see {@link #verifySignatures()}).  Signed fields also show a validity badge.
     */
    public final ObservableList<SignatureStatus> getSignatures() {
        return signaturesView;
    }

    /** True while a signature check is running. */
    public final ReadOnlyBooleanProperty verifyingSignaturesProperty() {
        return verifyingSignatures.getReadOnlyProperty();
    }

    public final boolean isVerifyingSignatures() {
        return verifyingSignatures.get();
    }

    /**
     * Whether signatures are checked as soon as a document is set.  True by default.  Checking can
     * reach the network (revocation lists, OCSP), so an application may turn it off and call
     * {@link #verifySignatures()} itself.
     */
    public final BooleanProperty verifySignaturesOnOpenProperty() {
        return verifySignaturesOnOpen;
    }

    public final boolean isVerifySignaturesOnOpen() {
        return verifySignaturesOnOpen.get();
    }

    public final void setVerifySignaturesOnOpen(boolean value) {
        verifySignaturesOnOpen.set(value);
    }

    /**
     * Called when the user clicks a signature field or its badge, or draws one with the signature
     * tool ({@link SignatureEvent#SIGNATURE_CLICKED}).  Setting a handler replaces the default - the
     * signature properties dialog for a signed field - and offers unsigned fields for signing.
     */
    public final ObjectProperty<EventHandler<? super SignatureEvent>> onSignatureClickedProperty() {
        return onSignatureClicked;
    }

    public final EventHandler<? super SignatureEvent> getOnSignatureClicked() {
        return onSignatureClicked.get();
    }

    public final void setOnSignatureClicked(EventHandler<? super SignatureEvent> handler) {
        onSignatureClicked.set(handler);
    }

    /**
     * Checks the document's signatures on a background thread; the results replace
     * {@link #getSignatures()} on the FX thread.  A newer check or document supersedes this one.
     *
     * @return completes with the results (on the FX thread)
     */
    public CompletableFuture<List<SignatureStatus>> verifySignatures() {
        Document document = getDocument();
        int generation = ++signatureGeneration;
        if (document == null) {
            signatures.clear();
            return CompletableFuture.completedFuture(List.of());
        }
        verifyingSignatures.set(true);
        CompletableFuture<List<SignatureStatus>> result = new CompletableFuture<>();
        startWorker("icepdf-fx-signature-check", () -> {
            List<SignatureStatus> found;
            RuntimeException failure = null;
            try {
                found = SignatureVerifier.verifyAll(document);
            } catch (RuntimeException e) {
                // each signature's own problem is reported in its status; this is the whole check failing.
                logger.log(Level.WARNING, "Could not check the document's signatures", e);
                found = List.of();
                failure = e;
            }
            List<SignatureStatus> done = found;
            RuntimeException failed = failure;
            Platform.runLater(() -> {
                if (generation == signatureGeneration && document == getDocument()) {
                    signatures.setAll(done);
                    verifyingSignatures.set(false);
                    // "no signatures" would be wrong: say the check failed.
                    if (failed != null) {
                        fireEvent(new PdfViewErrorEvent(this, this,
                                Messages.format("pdfView.signatureCheckFailed", Messages.reason(failed)), failed));
                    }
                }
                result.complete(done);
            });
        });
        return result;
    }

    /** Scrolls a signature field into view (its page becomes current in the single-page modes). */
    public void revealSignature(SignatureStatus status) {
        if (status == null || status.pageIndex() < 0 || status.pageIndex() >= getPageCount()) return;
        java.awt.geom.Rectangle2D r = status.widget().getUserSpaceRectangle();
        if (!getViewMode().isContinuous()) setCurrentPageIndex(status.pageIndex());
        ensureVisible(new PagePoint(status.pageIndex(), r.getCenterX(), r.getCenterY()));
    }

    // ---- printing -------------------------------------------------------------------------

    /**
     * Prints the document on a background thread.  Pages are painted under the same lock as
     * annotation edits, so an edit never shows half-made on paper.  A document that permits only
     * low-quality printing prints as images whatever the settings say.
     *
     * @return the started task: progress is pages printed, the message names the page; cancel stops
     * the job (pages already sent may still print)
     * @throws IllegalStateException with no document, or one that doesn't permit printing
     */
    public Task<Void> print(PrintSettings settings) {
        commitFieldEdit();
        Document document = getDocument();
        if (document == null || !isPrintAllowed()) {
            throw new IllegalStateException(Messages.get("pdfView.cannotPrint"));
        }
        if (!isHighQualityPrintAllowed()) {
            settings = settings.copy();
            settings.setLowResolution(true);
        }
        DocumentPrinter printer = new DocumentPrinter(document, settings);
        if (getSkin() instanceof PdfViewSkin skin) printer.setPageLock(skin.annotationLocker()::withAnnotationLock);
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                updateMessage(Messages.get("pdfView.printing"));
                printer.print((index, total) -> {
                    updateProgress(index, total);
                    updateMessage(Messages.format("pdfView.printingPage", index + 1, total));
                });
                updateProgress(1, 1);
                updateMessage(Messages.get(printer.isCancelled() ? "pdfView.printCancelled" : "pdfView.printed"));
                return null;
            }

            @Override
            protected void cancelled() {
                printer.cancel();
            }
        };
        startWorker("icepdf-fx-print", task);
        return task;
    }

    /**
     * Whether the document permits a tool: annotation tools need annotating, the signature tool
     * needs form fill-in and signing.
     */
    public final boolean toolPermitted(ToolMode mode) {
        if (mode == ToolMode.SIGNATURE) return isFormFillingAllowed();
        return !mode.createsAnnotations() || isAnnotationEditingAllowed();
    }

    /** A field created in this view (the signature tool), listed until the next check. */
    void addSignatureStatus(SignatureStatus status) {
        signatures.add(status);
    }

    private void updatePermissions(Document doc) {
        SecurityManager security = doc != null ? doc.getSecurityManager() : null;
        Permissions permissions = security != null ? security.getPermissions() : null;
        boolean annotate = permissions == null || permissions.getPermissions(Permissions.AUTHORING_FORM_FIELDS);
        copyAllowed.set(permissions == null || permissions.getPermissions(Permissions.CONTENT_EXTRACTION));
        annotatePermitted = annotate;
        fillPermitted = annotate || permissions.getPermissions(Permissions.FORM_FIELD_FILL_SIGNING);
        applyEditing();
        printAllowed.set(doc != null && (permissions == null || permissions.getPermissions(Permissions.PRINT_DOCUMENT)));
        highQualityPrintAllowed.set(printAllowed.get()
                && (permissions == null || permissions.getPermissions(Permissions.PRINT_DOCUMENT_QUALITY)));
    }

    private void applyEditing() {
        annotationEditingAllowed.set(annotatePermitted && !isReadOnly());
        formFillingAllowed.set(fillPermitted && !isReadOnly());
        if (!toolPermitted(getToolMode())) setToolMode(ToolMode.TEXT_SELECT);
        updateHistoryState();
    }

    /**
     * While true nothing in the document can be changed through the view - annotation tools and
     * edits, form filling, undo and redo - whatever its permissions allow.  For a document being
     * written in the background ({@code DocumentSession} sets it meanwhile); what is being typed in
     * a field is applied first.
     */
    public final BooleanProperty readOnlyProperty() {
        return readOnly;
    }

    public final boolean isReadOnly() {
        return readOnly.get();
    }

    public final void setReadOnly(boolean value) {
        readOnly.set(value);
    }
}
