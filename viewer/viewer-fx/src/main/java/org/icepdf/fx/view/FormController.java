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

import org.icepdf.core.pobjects.Name;
import org.icepdf.core.pobjects.PObject;
import org.icepdf.core.pobjects.Page;
import org.icepdf.core.pobjects.acroform.*;
import org.icepdf.core.pobjects.annotations.*;

import java.awt.geom.AffineTransform;
import java.util.*;

/**
 * Form field values with no toolkit in it: filling text, toggling check boxes, choosing radios and
 * list/combo entries, and resetting - ported from the Swing viewer's acroform components
 * ({@code TextWidgetComponent}, {@code CheckButtonComponent}, {@code RadioButtonComponent},
 * {@code ChoiceComboComponent}, {@code ChoiceListComponent}), which tie this logic to Swing widgets.
 * <p>
 * Every change is an {@link AnnotationEdits.Edit}: the widgets it touches are snapshotted before
 * and after (field /V, the parent's /V, a button's on/off state, a choice's indexes), so undo and
 * redo restore exactly rather than replaying toggles.  Applying a state regenerates each widget's
 * appearance through core and records it ({@code resetAppearanceStream}, {@code page.updateAnnotation},
 * the parent field in the state manager), under the renderer's per-page annotation lock.
 */
final class FormController {

    /** What a widget is, for choosing an editor and an interaction. */
    enum FieldKind {TEXT, PASSWORD, CHECK, RADIO, PUSH, COMBO, LIST, SIGNATURE, OTHER}

    /** A widget and the page it is on. */
    record Located(int pageIndex, Page page, AbstractWidgetAnnotation widget) {
    }

    /** One field's value before and after an edit (values as {@link #valueOf}). */
    record FieldChange(AbstractWidgetAnnotation widget, String name, Object oldValue, Object newValue) {
    }

    /** An edit of field values: which fields it changed, from what to what. */
    interface FieldEdit extends AnnotationEdits.Edit {
        List<FieldChange> changes();
    }

    private final AnnotationEdits.Locker locker;

    FormController(AnnotationEdits.Locker locker) {
        this.locker = locker;
    }

    static FieldKind kindOf(AbstractWidgetAnnotation widget) {
        if (widget instanceof TextWidgetAnnotation text) {
            return text.getFieldDictionary().getTextFieldType() == TextFieldDictionary.TextFieldType.TEXT_PASSWORD
                    ? FieldKind.PASSWORD : FieldKind.TEXT;
        }
        if (widget instanceof ButtonWidgetAnnotation button) {
            return switch (button.getFieldDictionary().getButtonFieldType()) {
                case PUSH_BUTTON -> FieldKind.PUSH;
                case RADIO_BUTTON -> FieldKind.RADIO;
                default -> FieldKind.CHECK;
            };
        }
        if (widget instanceof ChoiceWidgetAnnotation choice) {
            ChoiceFieldDictionary.ChoiceFieldType type = choice.getFieldDictionary().getChoiceFieldType();
            return type == ChoiceFieldDictionary.ChoiceFieldType.CHOICE_COMBO
                    || type == ChoiceFieldDictionary.ChoiceFieldType.CHOICE_EDITABLE_COMBO ? FieldKind.COMBO : FieldKind.LIST;
        }
        if (widget instanceof SignatureWidgetAnnotation) return FieldKind.SIGNATURE;
        return FieldKind.OTHER;
    }

    /** The field's fully-qualified name (a widget kid without /T takes its parent's, per core). */
    static String fieldNameOf(AbstractWidgetAnnotation widget) {
        return widget.getFieldDictionary().getFullyQualifiedFieldName();
    }

    /** True if the user may change the field's value. */
    static boolean isFillable(AbstractWidgetAnnotation widget) {
        FieldDictionary field = widget.getFieldDictionary();
        boolean readOnly = field != null && (field.isReadOnly()
                || (field.getParent() != null && field.getParent().isReadOnly()));
        FieldKind kind = kindOf(widget);
        return !readOnly && widget.allowScreenNormalMode() && kind != FieldKind.SIGNATURE && kind != FieldKind.OTHER;
    }

    /** The field's current text: its value, else the parent's (as Swing's TextWidgetComponent). */
    static String textOf(TextWidgetAnnotation widget) {
        Object value = widget.getFieldDictionary().getFieldValue();
        if ((value == null || "".equals(value)) && widget.getFieldDictionary().getParent() != null) {
            value = widget.getFieldDictionary().getParent().getFieldValue();
        }
        return value instanceof String s ? s.replace('\r', '\n') : "";
    }

    /**
     * A field's value as plain Java: the text of a text field; the on-state name of a check box or
     * radio group ("Off" when off); a choice's export value, or a List of them for a multi-select
     * list (null when nothing is chosen).
     */
    static Object valueOf(AbstractWidgetAnnotation widget) {
        FieldDictionary dictionary = widget.getFieldDictionary();
        switch (kindOf(widget)) {
            case TEXT, PASSWORD -> {
                return textOf((TextWidgetAnnotation) widget);
            }
            case CHECK, RADIO -> {
                Object v = dictionary.getEntries().get(FieldDictionary.V_KEY) != null || dictionary.getParent() == null
                        ? dictionary.getFieldValue() : dictionary.getParent().getFieldValue();
                return v instanceof Name name ? name.getName() : v != null ? v.toString() : "Off";
            }
            case COMBO, LIST -> {
                ChoiceFieldDictionary choice = ((ChoiceWidgetAnnotation) widget).getFieldDictionary();
                List<Integer> indexes = selectedIndexes(choice);
                List<ChoiceFieldDictionary.ChoiceOption> options = choice.getOptions();
                if (choice.isMultiSelect()) {
                    List<String> values = new ArrayList<>();
                    for (int i : indexes) if (options != null && i < options.size()) values.add(options.get(i).getValue());
                    return values;
                }
                if (!indexes.isEmpty() && options != null && indexes.get(0) < options.size()) {
                    return options.get(indexes.get(0)).getValue();
                }
                Object v = choice.getFieldValue();
                String typed = v == null ? null : text(v, choice);
                return typed == null || typed.isEmpty() ? null : typed;
            }
            default -> {
                return null;
            }
        }
    }

    /**
     * A choice's selected option indexes: /I when present, else the options whose export value (or
     * label) matches /V; many writers set only /V.
     */
    static List<Integer> selectedIndexes(ChoiceFieldDictionary field) {
        if (field.getIndexes() != null && !field.getIndexes().isEmpty()) return field.getIndexes();
        List<ChoiceFieldDictionary.ChoiceOption> options = field.getOptions();
        Object v = field.getFieldValue();
        if (options == null || v == null) return List.of();
        List<String> values = new ArrayList<>();
        if (v instanceof List<?> many) {
            for (Object o : many) values.add(text(o, field));
        } else {
            values.add(text(v, field));
        }
        return indexesOf(options, values);
    }

    /** The indexes of the options whose export value or label is among {@code values}, in option order. */
    static List<Integer> indexesOf(List<ChoiceFieldDictionary.ChoiceOption> options, Collection<String> values) {
        List<Integer> out = new ArrayList<>();
        if (options == null) return out;
        for (int i = 0; i < options.size(); i++) {
            ChoiceFieldDictionary.ChoiceOption option = options.get(i);
            if (values.contains(option.getValue()) || values.contains(option.getLabel())) out.add(i);
        }
        return out;
    }

    private static String text(Object o, FieldDictionary field) {
        return o instanceof org.icepdf.core.pobjects.StringObject str
                ? str.getDecryptedLiteralString(field.getLibrary().getSecurityManager()) : String.valueOf(o);
    }

    // ---- changes --------------------------------------------------------------------------------

    /** Sets a text field's value. */
    AnnotationEdits.Edit setText(Located field, String text, AffineTransform toPageSpace) {
        TextWidgetAnnotation widget = (TextWidgetAnnotation) field.widget();
        return change(List.of(field), toPageSpace,
                () -> widget.getFieldDictionary().setFieldValue(text, widget.getPObjectReference()));
    }

    /** Toggles a check box (Swing CheckButtonComponent.buttonActuated). */
    AnnotationEdits.Edit toggleCheck(Located field, AffineTransform toPageSpace) {
        ButtonWidgetAnnotation widget = (ButtonWidgetAnnotation) field.widget();
        return change(List.of(field), toPageSpace, () -> {
            ButtonFieldDictionary dictionary = widget.getFieldDictionary();
            FieldDictionary parent = dictionary.getParent() != null ? dictionary.getParent() : dictionary;
            Name value = widget.toggle();
            dictionary.setFieldValue(value, widget.getPObjectReference());
            parent.setFieldValue(value, widget.getPObjectReference());
        });
    }

    /**
     * Selects a radio button (Swing RadioButtonComponent.buttonActuated): its siblings turn off; in a
     * RadiosInUnison group every kid sharing its on-state turns on with it.  Clicking the selected
     * radio does nothing unless the group allows toggling to off.
     *
     * @param siblings every kid of the radio's group, with its page (the group can span pages)
     */
    AnnotationEdits.Edit selectRadio(Located radio, List<Located> siblings, AffineTransform toPageSpace) {
        ButtonWidgetAnnotation widget = (ButtonWidgetAnnotation) radio.widget();
        ButtonFieldDictionary dictionary = widget.getFieldDictionary();
        FieldDictionary parent = dictionary.getParent() != null ? dictionary.getParent() : dictionary;
        boolean noToggleToOff = parent instanceof ButtonFieldDictionary b ? b.isNoToggleToOff() : dictionary.isNoToggleToOff();
        if (widget.isOn() && noToggleToOff) return null;
        boolean unison = dictionary.isRadioInUnison()
                || (parent instanceof ButtonFieldDictionary b && b.isRadioInUnison());
        Name onName = onNameOf(widget);
        boolean turnOn = !widget.isOn();
        return change(siblings, toPageSpace, () -> {
            for (Located kid : siblings) {
                ButtonWidgetAnnotation b = (ButtonWidgetAnnotation) kid.widget();
                boolean match = kid.widget() == widget || (unison && onName != null && onName.equals(onNameOf(b)));
                if (match && turnOn) b.turnOn();
                else b.turnOff();
            }
            parent.setFieldValue(turnOn && onName != null ? onName : offName(widget), widget.getPObjectReference());
        });
    }

    /** Chooses an entry of a combo or single-select list, or types into an editable combo. */
    AnnotationEdits.Edit choose(Located field, String label, AffineTransform toPageSpace) {
        ChoiceWidgetAnnotation widget = (ChoiceWidgetAnnotation) field.widget();
        return change(List.of(field), toPageSpace,
                () -> widget.getFieldDictionary().setFieldValue(label, widget.getPObjectReference()));
    }

    /** Selects entries of a combo or list by index (several for a multi-select list). */
    AnnotationEdits.Edit chooseIndexes(Located field, List<Integer> indexes, AffineTransform toPageSpace) {
        ChoiceWidgetAnnotation widget = (ChoiceWidgetAnnotation) field.widget();
        return change(List.of(field), toPageSpace, () -> {
            ChoiceFieldDictionary dictionary = widget.getFieldDictionary();
            List<ChoiceFieldDictionary.ChoiceOption> options = dictionary.getOptions();
            List<Integer> valid = new ArrayList<>();
            List<Object> values = new ArrayList<>();
            for (int i : indexes) {
                if (options != null && i >= 0 && i < options.size()) {
                    valid.add(i);
                    values.add(new org.icepdf.core.pobjects.LiteralStringObject(options.get(i).getValue()));
                }
            }
            if (valid.size() == 1 && !dictionary.isMultiSelect()) {
                // one entry: V is its export value as a plain string (not a one-element array).
                dictionary.setFieldValue(options.get(valid.get(0)).getValue(), widget.getPObjectReference());
            } else {
                dictionary.getEntries().put(FieldDictionary.V_KEY, values);
            }
            // the chosen indexes exactly (several options may share an export value); writes /I.
            dictionary.setIndexes(new ArrayList<>(valid));
        });
    }

    /** Resets fields to their defaults (/DV), as a ResetForm action does: one undoable edit. */
    AnnotationEdits.Edit reset(List<Located> fields, AffineTransform toPageSpace) {
        return change(fields, toPageSpace, () -> {
            for (Located field : fields) resetOne(field.widget());
        });
    }

    /**
     * Core resets every fillable kind: the value from /DV (the field's, for a kid widget), check box
     * and radio on/off state (GH-579), the rebuilt appearance and the change recorded for saving
     * (GH-593).  Push buttons and signatures have nothing to reset.
     */
    private static void resetOne(AbstractWidgetAnnotation widget) {
        switch (kindOf(widget)) {
            case TEXT, PASSWORD, CHECK, RADIO, COMBO, LIST -> widget.reset();
            default -> {
            }
        }
    }


    // ---- snapshots ------------------------------------------------------------------------------

    /** The on-state name of a button (the non-Off normal appearance), or null. */
    static Name onNameOf(ButtonWidgetAnnotation button) {
        Appearance appearance = button.getAppearances().get(button.getCurrentAppearance());
        return appearance != null && appearance.hasAlternativeAppearance() ? appearance.getOnName() : null;
    }

    private static Name offName(ButtonWidgetAnnotation button) {
        Appearance appearance = button.getAppearances().get(button.getCurrentAppearance());
        return appearance != null && appearance.getOffName() != null ? appearance.getOffName() : new Name("Off");
    }

    /** Everything a value change can alter on one widget. */
    private record State(Located field, Object value, Object parentValue, Name selected, List<Integer> indexes) {

        static State of(Located field) {
            AbstractWidgetAnnotation w = field.widget();
            FieldDictionary dictionary = w.getFieldDictionary();
            FieldDictionary parent = dictionary.getParent();
            Name selected = null;
            if (w instanceof ButtonWidgetAnnotation button) {
                Appearance appearance = button.getAppearances().get(button.getCurrentAppearance());
                selected = appearance != null ? appearance.getSelectedName() : null;
            }
            List<Integer> indexes = w instanceof ChoiceWidgetAnnotation choice
                    && choice.getFieldDictionary().getIndexes() != null
                    ? new ArrayList<>(choice.getFieldDictionary().getIndexes()) : null;
            return new State(field, copy(dictionary.getEntries().get(FieldDictionary.V_KEY)),
                    parent != null ? copy(parent.getEntries().get(FieldDictionary.V_KEY)) : null, selected, indexes);
        }

        private static Object copy(Object value) {
            return value instanceof List<?> list ? new ArrayList<>(list) : value;
        }

        void restore() {
            AbstractWidgetAnnotation w = field.widget();
            FieldDictionary dictionary = w.getFieldDictionary();
            put(dictionary, value, w);
            if (dictionary.getParent() != null) put(dictionary.getParent(), parentValue, w);
            if (w instanceof ButtonWidgetAnnotation button && selected != null) {
                Appearance appearance = button.getAppearances().get(button.getCurrentAppearance());
                if (appearance != null) appearance.setSelectedName(selected);
            }
            if (w instanceof ChoiceWidgetAnnotation choice) {
                choice.getFieldDictionary().setIndexes(indexes == null ? null : new ArrayList<>(indexes));
            }
        }

        private static void put(FieldDictionary dictionary, Object value, AbstractWidgetAnnotation w) {
            if (value == null) {
                dictionary.getEntries().remove(FieldDictionary.V_KEY);
                if (dictionary instanceof TextFieldDictionary) dictionary.setFieldValue("", w.getPObjectReference());
            } else if (value instanceof List<?> list) {
                dictionary.getEntries().put(FieldDictionary.V_KEY, new ArrayList<>(list));
            } else if (value instanceof org.icepdf.core.pobjects.StringObject string) {
                dictionary.setFieldValue(string.getDecryptedLiteralString(w.getLibrary().getSecurityManager()),
                        w.getPObjectReference());
            } else {
                dictionary.setFieldValue(value, w.getPObjectReference());
            }
        }
    }

    /**
     * Runs a mutation over some widgets and returns it as an edit: before/after snapshots, appearances
     * regenerated and recorded.  Null if nothing changed.
     */
    private AnnotationEdits.Edit change(List<Located> fields, AffineTransform toPageSpace, Runnable mutation) {
        List<State> before = new ArrayList<>();
        for (Located f : fields) before.add(State.of(f));
        // one value per field name (a radio group's kids share one).
        Map<String, Located> byName = new LinkedHashMap<>();
        for (Located f : fields) byName.putIfAbsent(fieldNameOf(f.widget()), f);
        Map<String, Object> oldValues = new HashMap<>();
        byName.forEach((name, f) -> oldValues.put(name, valueOf(f.widget())));
        Set<Integer> pages = new TreeSet<>();
        for (Located f : fields) pages.add(f.pageIndex());
        AffineTransform toPage = new AffineTransform(toPageSpace);
        List<State>[] after = new List[1];
        lockAll(pages, () -> {
            mutation.run();
            after[0] = new ArrayList<>();
            for (Located f : fields) after[0].add(State.of(f));
            regenerate(fields, toPage);
        });
        List<FieldChange> changes = new ArrayList<>();
        byName.forEach((name, f) -> {
            Object now = valueOf(f.widget());
            if (!Objects.equals(oldValues.get(name), now)) {
                changes.add(new FieldChange(f.widget(), name, oldValues.get(name), now));
            }
        });
        AbstractWidgetAnnotation first = fields.get(0).widget();
        int firstPage = fields.get(0).pageIndex();
        return new FieldEdit() {
            public List<FieldChange> changes() {
                return changes;
            }

            public int pageIndex() {
                return firstPage;
            }

            public Set<Integer> pages() {
                return pages;
            }

            public Annotation annotation() {
                return first;
            }

            public void undo() {
                apply(before);
            }

            public void redo() {
                apply(after[0]);
            }

            private void apply(List<State> states) {
                lockAll(pages, () -> {
                    states.forEach(State::restore);
                    regenerate(fields, toPage);
                });
            }
        };
    }

    /** Regenerates appearances and records the widgets (and their parent fields) as changed. */
    private static void regenerate(List<Located> fields, AffineTransform toPage) {
        for (Located f : fields) {
            AbstractWidgetAnnotation w = f.widget();
            w.resetAppearanceStream(toPage);
            f.page().updateAnnotation(w);
            FieldDictionary parent = w.getFieldDictionary().getParent();
            if (parent != null && parent.getPObjectReference() != null) {
                w.getLibrary().getStateManager().addChange(new PObject(parent, parent.getPObjectReference()));
            }
        }
    }

    private void lockAll(Set<Integer> pages, Runnable action) {
        Iterator<Integer> it = pages.iterator();
        lockFrom(it, action);
    }

    private void lockFrom(Iterator<Integer> it, Runnable action) {
        if (!it.hasNext()) {
            action.run();
            return;
        }
        int page = it.next();
        // pages in ascending order, so two edits never lock the same pages in opposite orders.
        locker.withAnnotationLock(page, () -> lockFrom(it, action));
    }
}
