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
package org.icepdf.fx.view.internal.form;

import javafx.scene.input.KeyCode;
import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.Name;
import org.icepdf.core.pobjects.Page;
import org.icepdf.core.pobjects.Reference;
import org.icepdf.core.pobjects.StringObject;
import org.icepdf.core.pobjects.acroform.ChoiceFieldDictionary;
import org.icepdf.core.pobjects.acroform.FieldDictionary;
import org.icepdf.core.pobjects.actions.FormAction;
import org.icepdf.core.pobjects.actions.ResetFormAction;
import org.icepdf.core.pobjects.annotations.AbstractWidgetAnnotation;
import org.icepdf.core.pobjects.annotations.Annotation;
import org.icepdf.core.pobjects.annotations.ChoiceWidgetAnnotation;
import org.icepdf.core.pobjects.annotations.TextWidgetAnnotation;
import org.icepdf.fx.view.PagePoint;
import org.icepdf.fx.view.PdfView;
import org.icepdf.fx.view.internal.ViewHost;
import org.icepdf.fx.view.internal.annot.AnnotationEdits;
import org.icepdf.fx.view.internal.annot.AnnotationHit;
import org.icepdf.fx.view.internal.annot.AnnotationUiLayer;

import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Form filling in the view: which fields are fillable and where, tab order, the native editor over
 * a text or choice field, check box / radio / push button actuation and resets - every change an
 * undoable edit through {@link FormController}.
 */
public final class FormFields {

    private final ViewHost host;
    private FormController forms;
    // the open text field editor, and which field it edits.
    private FieldEditor fieldEditor;
    private FormController.Located editing;
    // a click (not Tab) is focusing a field: a combo opens its drop-down.
    private boolean openPopupOnFocus;

    public FormFields(ViewHost host) {
        this.host = host;
    }

    /** A new document: any editor belonged to the old one. */
    public void documentChanged() {
        fieldEditor = null;
        editing = null;
    }

    public FormController forms() {
        if (forms == null) forms = new FormController(host.annotationLocker());
        return forms;
    }

    /** The application allows filling fields and so does the document. */
    public boolean editable() {
        PdfView view = host.view();
        return view.isFormFieldsEditable() && view.isFormFillingAllowed();
    }

    /** Fillable widgets of an initialised page, in /Annots order. */
    private static List<AbstractWidgetAnnotation> fillable(Page page) {
        List<AbstractWidgetAnnotation> out = new ArrayList<>();
        if (page == null || !page.isInitiated() || page.getAnnotations() == null) return out;
        for (Annotation a : page.getAnnotations()) {
            if (a instanceof AbstractWidgetAnnotation w && !w.isDeleted() && !w.getFlagHidden()
                    && FormController.isFillable(w)) {
                out.add(w);
            }
        }
        return out;
    }

    /** User-space rectangles of a page's fillable fields, for the field highlight. */
    public List<Rectangle2D> fieldRects(Page page) {
        List<Rectangle2D> rects = new ArrayList<>();
        for (AbstractWidgetAnnotation w : fillable(page)) rects.add(w.getUserSpaceRectangle());
        return rects;
    }

    /** The fillable widget under a viewport point (top-most), or null; never parses on the FX thread. */
    public AnnotationHit fieldAtViewport(double vx, double vy) {
        if (!editable()) return null;
        PagePoint point = host.pageAtViewport(vx, vy);
        if (point == null) return null;
        List<AbstractWidgetAnnotation> widgets = fillable(host.document().getPageTree().getPage(point.pageIndex()));
        for (int i = widgets.size() - 1; i >= 0; i--) {
            if (widgets.get(i).getUserSpaceRectangle().contains(point.x(), point.y())) {
                return new AnnotationHit(point.pageIndex(), widgets.get(i));
            }
        }
        return null;
    }

    /**
     * All fillable fields of the document in tab order: pages in order, each page's fields by its
     * /Tabs entry (see {@link FieldOrder}).  Reads annotations of pages not yet shown (annotation
     * dictionaries only, not content); empty for a document without an AcroForm.
     */
    public List<FormController.Located> fieldsInTabOrder() {
        List<FormController.Located> out = new ArrayList<>();
        Document document = host.document();
        if (document == null || document.getCatalog().getInteractiveForm() == null) return out;
        for (int i = 0; i < document.getNumberOfPages(); i++) {
            Page page = document.getPageTree().getPage(i);
            List<Annotation> annotations = page.getAnnotations();
            if (annotations == null) continue;
            List<AbstractWidgetAnnotation> widgets = new ArrayList<>();
            for (Annotation a : annotations) {
                if (a instanceof AbstractWidgetAnnotation w && !w.isDeleted() && !w.getFlagHidden()
                        && FormController.isFillable(w)) {
                    widgets.add(w);
                }
            }
            Object tabs = page.getEntries().get(new Name("Tabs"));
            for (AbstractWidgetAnnotation w : FieldOrder.order(widgets, AbstractWidgetAnnotation::getUserSpaceRectangle,
                    tabs instanceof Name n ? n.getName() : null)) {
                out.add(new FormController.Located(i, page, w));
            }
        }
        return out;
    }

    /** The field after (or before) {@code current} in tab order, wrapping; null if there are none. */
    public AbstractWidgetAnnotation adjacentField(AbstractWidgetAnnotation current, boolean backwards) {
        List<FormController.Located> fields = fieldsInTabOrder();
        int index = -1;
        for (int i = 0; i < fields.size(); i++) {
            if (fields.get(i).widget() == current) index = i;
        }
        int next = FieldOrder.next(fields.size(), index, backwards);
        return next < 0 ? null : fields.get(next).widget();
    }

    /** Scrolls a field into view, switching page in the non-continuous modes. */
    public void revealField(AbstractWidgetAnnotation widget) {
        FormController.Located located = locate(widget);
        if (located == null) return;
        Rectangle2D r = widget.getUserSpaceRectangle();
        host.reveal(new PagePoint(located.pageIndex(), r.getCenterX(), r.getCenterY()));
    }

    /** The located form field (page and widget) of a widget, among the document's pages. */
    public FormController.Located locate(AbstractWidgetAnnotation widget) {
        Document document = host.document();
        for (int i = 0; i < document.getNumberOfPages(); i++) {
            Page page = document.getPageTree().getPage(i);
            if (page.getAnnotations() != null && page.getAnnotations().contains(widget)) {
                return new FormController.Located(i, page, widget);
            }
        }
        return null;
    }

    /** The focused field changed: commit an open editor elsewhere; open one on a text or choice field. */
    public void focusChanged() {
        AbstractWidgetAnnotation focused = host.view().getFocusedField();
        if (focused != null && !editable()) {
            host.view().clearFieldFocus();
            return;
        }
        if (fieldEditor != null && (editing == null || editing.widget() != focused)) {
            fieldEditor.commitNow();
        }
        if (focused != null && fieldEditor == null) openEditor(focused, false);
    }

    /**
     * A click on a field: focuses it (opening its editor), toggles a check box or radio, or runs a
     * push button's action.  A click on the focused text field reopens its editor after an Esc.
     */
    public void press(AnnotationHit field) {
        AbstractWidgetAnnotation widget = (AbstractWidgetAnnotation) field.annotation();
        FormController.FieldKind kind = FormController.kindOf(widget);
        if (host.view().getFocusedField() != widget) {
            openPopupOnFocus = true;
            try {
                host.view().focusField(widget);
            } finally {
                openPopupOnFocus = false;
            }
        } else if (fieldEditor == null) {
            openEditor(widget, true);
        }
        actuate(widget, kind);
    }

    public void reopenEditor(AbstractWidgetAnnotation widget) {
        if (fieldEditor == null) openEditor(widget, false);
    }

    /**
     * Space toggles a focused check box or radio and presses a focused button (Enter too).
     *
     * @return true if the key acted on the focused field
     */
    public boolean actuateFocused(KeyCode code) {
        AbstractWidgetAnnotation focused = host.view().getFocusedField();
        if (focused == null || fieldEditor != null) return false;
        FormController.FieldKind kind = FormController.kindOf(focused);
        if (code != KeyCode.SPACE && !(code == KeyCode.ENTER && kind == FormController.FieldKind.PUSH)) return false;
        if (kind != FormController.FieldKind.CHECK && kind != FormController.FieldKind.RADIO
                && kind != FormController.FieldKind.PUSH) {
            return false;
        }
        actuate(focused, kind);
        return true;
    }

    /** Check boxes and radios toggle, push buttons act; as a click or Space does. */
    private void actuate(AbstractWidgetAnnotation widget, FormController.FieldKind kind) {
        if (kind != FormController.FieldKind.CHECK && kind != FormController.FieldKind.RADIO
                && kind != FormController.FieldKind.PUSH) {
            return;
        }
        FormController.Located located = locate(widget);
        if (located == null) return;
        switch (kind) {
            case CHECK -> host.recordEdit(forms().toggleCheck(located, host.toPageSpace(located.page())));
            case RADIO -> {
                String name = FormController.fieldNameOf(widget);
                List<FormController.Located> siblings = new ArrayList<>();
                for (FormController.Located f : fieldsInTabOrder()) {
                    if (FormController.kindOf(f.widget()) == FormController.FieldKind.RADIO
                            && Objects.equals(name, FormController.fieldNameOf(f.widget()))) {
                        siblings.add(f);
                    }
                }
                if (siblings.stream().noneMatch(f -> f.widget() == widget)) siblings.add(located);
                host.recordEdit(forms().selectRadio(located, siblings, host.toPageSpace(located.page())));
            }
            default -> push(located);
        }
    }

    /**
     * A push button: a ResetForm action resets the form in the view, honouring its /Fields
     * include/exclude list as core's ResetFormAction does, but through FormController so the reset
     * is one undoable edit with regenerated appearances (core's button reset() leaves the model to
     * a listening Swing component).  Anything else (SubmitForm, JavaScript, URI, ...) goes where a
     * link's action goes.
     */
    private void push(FormController.Located button) {
        if (button.widget().getAction() instanceof ResetFormAction reset) {
            resetFields(fieldsForReset(reset));
        } else {
            host.view().performAnnotationAction(button.widget());
        }
    }

    /** Resets fields to their defaults as one undoable edit; every fillable field when null. */
    public void resetFields(List<FormController.Located> fields) {
        if (fields == null) fields = fieldsInTabOrder();
        // buttons and signatures have no value to reset (and their appearances must not be redrawn).
        fields = fields.stream().filter(f -> switch (FormController.kindOf(f.widget())) {
            case PUSH, SIGNATURE, OTHER -> false;
            default -> true;
        }).toList();
        if (fields.isEmpty()) return;
        // a reset discards what is being typed (synchronously: a pending commit would land after it).
        if (fieldEditor != null) fieldEditor.closeNow(false);
        // the transform is only used to regenerate appearances, which are page-local per field.
        host.recordEdit(forms().reset(fields, host.toPageSpace(fields.get(0).page())));
    }

    /** Closes an open field editor at once, committing or discarding what is typed. */
    public void closeEditor(boolean commit) {
        if (fieldEditor != null) fieldEditor.closeNow(commit);
    }

    /** The fields a ResetForm action names: all, the /Fields listed, or all but those (Flags bit 1). */
    private List<FormController.Located> fieldsForReset(ResetFormAction reset) {
        List<FormController.Located> all = fieldsInTabOrder();
        Document document = host.document();
        Object listed = reset.getEntries().get(FormAction.FIELDS_KEY);
        if (listed instanceof Reference ref) listed = document.getCatalog().getLibrary().getObject(ref);
        if (!(listed instanceof List<?> entries) || entries.isEmpty()) return all;
        Set<String> names = new HashSet<>();
        for (Object entry : entries) {
            if (entry instanceof Reference ref) entry = document.getCatalog().getLibrary().getObject(ref);
            if (entry instanceof AbstractWidgetAnnotation w) names.add(FormController.fieldNameOf(w));
            else if (entry instanceof FieldDictionary f) names.add(f.getFullyQualifiedFieldName());
            else if (entry instanceof StringObject str) names.add(str.getDecryptedLiteralString(
                    document.getCatalog().getLibrary().getSecurityManager()));
            else if (entry instanceof String str) names.add(str);
        }
        boolean exclude = reset.isIncludeExclude();
        List<FormController.Located> out = new ArrayList<>();
        for (FormController.Located f : all) {
            String name = FormController.fieldNameOf(f.widget());
            boolean named = names.stream().anyMatch(n -> name.equals(n) || name.startsWith(n + "."));
            if (named != exclude) out.add(f);
        }
        return out;
    }

    /**
     * Opens the native editor over a text, password, combo or list field; the widget leaves the
     * tiles meanwhile.  Buttons have no editor.
     */
    private void openEditor(AbstractWidgetAnnotation widget, boolean openPopup) {
        FormController.FieldKind kind = FormController.kindOf(widget);
        boolean text = kind == FormController.FieldKind.TEXT || kind == FormController.FieldKind.PASSWORD;
        boolean choice = kind == FormController.FieldKind.COMBO || kind == FormController.FieldKind.LIST;
        if (!text && !choice) return;
        FormController.Located located = locate(widget);
        if (located == null) return;
        AnnotationUiLayer ui = host.uiLayer(located.pageIndex());
        if (ui == null) return;
        FieldEditor[] holder = new FieldEditor[1];
        FieldEditor.Listener listener = new FieldEditor.Listener() {
            public void commit(Object value, int advance) {
                editorListener(holder[0], located).commit(value, advance);
            }

            public void cancel() {
                editorListener(holder[0], located).cancel();
            }
        };
        FieldEditor editor = text
                ? FieldEditor.text((TextWidgetAnnotation) widget, FormController.textOf((TextWidgetAnnotation) widget),
                ui.viewBounds(widget), host.zoom(), listener)
                : FieldEditor.choice((ChoiceWidgetAnnotation) widget, ui.viewBounds(widget), host.zoom(),
                openPopup || openPopupOnFocus, listener);
        holder[0] = editor;
        fieldEditor = editor;
        editing = located;
        host.setExcluded(located.pageIndex(), widget, true);
        ui.addFieldEditor(editor.control());
        editor.control().requestFocus();
        if (editor.control() instanceof javafx.scene.control.TextInputControl input) input.selectAll();
    }

    /** Closes an editor (once): commit applies the value as an undoable edit; the widget re-renders. */
    private FieldEditor.Listener editorListener(FieldEditor editor, FormController.Located located) {
        return new FieldEditor.Listener() {
            public void commit(Object value, int advance) {
                close(editor, located);
                AnnotationEdits.Edit edit = null;
                AffineTransform toPage = host.toPageSpace(located.page());
                if (located.widget() instanceof TextWidgetAnnotation textWidget) {
                    if (!value.equals(FormController.textOf(textWidget))) {
                        edit = forms().setText(located, (String) value, toPage);
                    }
                } else if (located.widget() instanceof ChoiceWidgetAnnotation choice) {
                    ChoiceFieldDictionary field = choice.getFieldDictionary();
                    if (value instanceof List<?> indexes) {
                        List<Integer> current = FormController.selectedIndexes(field);
                        if (!indexes.equals(current)) {
                            @SuppressWarnings("unchecked") List<Integer> chosen = (List<Integer>) indexes;
                            edit = forms().chooseIndexes(located, chosen, toPage);
                        }
                    } else if (value instanceof String typed) {
                        Object current = field.getFieldValue();
                        if (!typed.equals(current instanceof String s ? s : current != null ? current.toString() : "")) {
                            edit = forms().choose(located, typed, toPage);
                        }
                    }
                }
                host.recordEdit(edit);
                if (advance > 0) host.view().focusNextField();
                else if (advance < 0) host.view().focusPreviousField();
            }

            public void cancel() {
                close(editor, located);
            }
        };
    }

    private void close(FieldEditor editor, FormController.Located located) {
        AnnotationUiLayer ui = host.uiLayer(located.pageIndex());
        if (ui != null) ui.removeFieldEditor(editor.control());
        if (fieldEditor == editor) {
            fieldEditor = null;
            editing = null;
        }
        host.setExcluded(located.pageIndex(), located.widget(), false);
        // keyboard focus back to the view, so Tab and the arrows work after an editor closes.
        PdfView view = host.view();
        if (!view.isFocused() && view.getScene() != null && view.getScene().getFocusOwner() == null) {
            view.requestFocus();
        }
    }
}
