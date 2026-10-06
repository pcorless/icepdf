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

import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.Name;
import org.icepdf.core.pobjects.Page;
import org.icepdf.core.pobjects.acroform.FieldDictionary;
import org.icepdf.core.pobjects.annotations.*;
import org.icepdf.fx.view.FormController.FieldKind;
import org.icepdf.fx.view.FormController.Located;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.geom.AffineTransform;
import java.io.BufferedOutputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Field value changes on the project-made all_fields.pdf (see make_form_fixture.py), without a
 * toolkit: each change, its undo and redo, radio groups, reset, and a save round trip.
 */
class FormControllerTest {

    private static final AnnotationEdits.Locker NO_LOCK = (page, mutation) -> mutation.run();

    private Document document;
    private Page page;
    private AffineTransform toPage;
    private final FormController forms = new FormController(NO_LOCK);

    @BeforeEach
    void open() throws Exception {
        document = new Document();
        document.setFile(Paths.get(Objects.requireNonNull(
                FormControllerTest.class.getResource("/forms/all_fields.pdf")).toURI()).toString());
        page = document.getPageTree().getPage(0);
        page.init();
        toPage = page.getToPageSpaceTransform(Page.BOUNDARY_CROPBOX, 0, 1);
    }

    @AfterEach
    void close() {
        document.dispose();
    }

    /** Widgets of a field by fully-qualified name (radio groups have several). */
    private List<Located> field(String name) {
        return fieldsIn(page, name);
    }

    private static List<Located> fieldsIn(Page page, String name) {
        List<Located> out = new ArrayList<>();
        for (Annotation a : page.getAnnotations()) {
            if (a instanceof AbstractWidgetAnnotation w && name.equals(FormController.fieldNameOf(w))) {
                out.add(new Located(0, page, w));
            }
        }
        if (out.isEmpty()) {
            StringBuilder names = new StringBuilder();
            for (Annotation a : page.getAnnotations()) {
                if (a instanceof AbstractWidgetAnnotation w) {
                    names.append(FormController.fieldNameOf(w)).append('[').append(w.getClass().getSimpleName())
                            .append("] ");
                } else if (a != null) {
                    names.append(a.getClass().getSimpleName()).append(' ');
                }
            }
            fail("no field " + name + " among: " + names);
        }
        return out;
    }

    private Located one(String name) {
        return field(name).get(0);
    }

    private static Object value(Located field) {
        FieldDictionary dictionary = field.widget().getFieldDictionary();
        return dictionary.getParent() != null && dictionary.getEntries().get(FieldDictionary.V_KEY) == null
                ? dictionary.getParent().getFieldValue() : dictionary.getFieldValue();
    }

    @DisplayName("field kinds")
    @Test
    void kinds() {
        assertEquals(FieldKind.TEXT, FormController.kindOf(one("name").widget()));
        assertEquals(FieldKind.TEXT, FormController.kindOf(one("notes").widget()));
        assertEquals(FieldKind.PASSWORD, FormController.kindOf(one("secret").widget()));
        assertEquals(FieldKind.CHECK, FormController.kindOf(one("agree").widget()));
        assertEquals(FieldKind.RADIO, FormController.kindOf(one("color").widget()));
        assertEquals(3, field("color").size());
        assertEquals(FieldKind.COMBO, FormController.kindOf(one("country").widget()));
        assertEquals(FieldKind.COMBO, FormController.kindOf(one("city").widget()));
        assertEquals(FieldKind.LIST, FormController.kindOf(one("fruit").widget()));
        assertEquals(FieldKind.PUSH, FormController.kindOf(one("reset").widget()));
        assertTrue(FormController.isFillable(one("name").widget()));
        assertEquals("Ada", FormController.textOf((TextWidgetAnnotation) one("name").widget()));
    }

    @DisplayName("text: set, undo, redo")
    @Test
    void text() {
        Located name = one("name");
        AnnotationEdits.Edit edit = forms.setText(name, "Grace", toPage);
        assertEquals("Grace", value(name));
        edit.undo();
        assertEquals("Ada", value(name));
        edit.redo();
        assertEquals("Grace", value(name));
    }

    @DisplayName("check box toggles on and off; undo restores")
    @Test
    void checkBox() {
        Located agree = one("agree");
        ButtonWidgetAnnotation box = (ButtonWidgetAnnotation) agree.widget();
        assertFalse(box.isOn());
        AnnotationEdits.Edit on = forms.toggleCheck(agree, toPage);
        assertTrue(box.isOn());
        assertEquals(new Name("Yes"), value(agree));
        on.undo();
        assertFalse(box.isOn());
        assertEquals(new Name("Off"), value(agree));
    }

    @DisplayName("radio group: one on, siblings off, no toggle to off; undo restores the old choice")
    @Test
    void radio() {
        List<Located> kids = field("color");
        ButtonWidgetAnnotation red = (ButtonWidgetAnnotation) kids.get(0).widget();
        ButtonWidgetAnnotation green = (ButtonWidgetAnnotation) kids.get(1).widget();
        assertTrue(red.isOn());
        AnnotationEdits.Edit edit = forms.selectRadio(kids.get(1), kids, toPage);
        assertTrue(green.isOn());
        assertFalse(red.isOn());
        assertFalse(((ButtonWidgetAnnotation) kids.get(2).widget()).isOn());
        assertEquals(new Name("Green"), red.getFieldDictionary().getParent().getFieldValue());
        assertNull(forms.selectRadio(kids.get(1), kids, toPage), "NoToggleToOff: clicking the selected one does nothing");
        edit.undo();
        assertTrue(red.isOn());
        assertFalse(green.isOn());
        assertEquals(new Name("Red"), red.getFieldDictionary().getParent().getFieldValue());
    }

    @DisplayName("radios in unison: kids sharing an on-state switch together")
    @Test
    void unison() {
        List<Located> kids = field("pair");
        forms.selectRadio(kids.get(0), kids, toPage);
        assertTrue(((ButtonWidgetAnnotation) kids.get(0).widget()).isOn());
        assertTrue(((ButtonWidgetAnnotation) kids.get(1).widget()).isOn(), "same /A on-state, in unison");
        assertFalse(((ButtonWidgetAnnotation) kids.get(2).widget()).isOn());
        forms.selectRadio(kids.get(2), kids, toPage);
        assertFalse(((ButtonWidgetAnnotation) kids.get(0).widget()).isOn());
        assertFalse(((ButtonWidgetAnnotation) kids.get(1).widget()).isOn());
        assertTrue(((ButtonWidgetAnnotation) kids.get(2).widget()).isOn());
    }

    @DisplayName("combo and multi-select list")
    @Test
    void choices() {
        Located country = one("country");
        ChoiceWidgetAnnotation combo = (ChoiceWidgetAnnotation) country.widget();
        AnnotationEdits.Edit edit = forms.choose(country, "Japan", toPage);
        assertEquals("Japan", value(country));
        assertEquals(List.of(2), combo.getFieldDictionary().getIndexes());
        edit.undo();
        assertEquals("France", value(country));

        Located toppings = one("toppings");
        ChoiceWidgetAnnotation list = (ChoiceWidgetAnnotation) toppings.widget();
        assertEquals(List.of(0, 2), list.getFieldDictionary().getIndexes());
        AnnotationEdits.Edit pick = forms.chooseIndexes(toppings, List.of(1), toPage);
        assertEquals(List.of(1), list.getFieldDictionary().getIndexes());
        pick.undo();
        assertEquals(List.of(0, 2), list.getFieldDictionary().getIndexes());
    }

    @DisplayName("choosing one combo entry by index writes its export value as a string, and /I")
    @Test
    void chooseOneByIndex() {
        Located country = one("country");
        AnnotationEdits.Edit edit = forms.chooseIndexes(country, List.of(2), toPage);
        assertEquals("Japan", value(country));
        assertEquals(List.of(2), ((ChoiceWidgetAnnotation) country.widget()).getFieldDictionary().getIndexes());
        edit.undo();
        assertEquals("France", value(country));
    }

    @DisplayName("reset restores defaults across field types, as one undoable edit")
    @Test
    void reset() {
        forms.setText(one("name"), "Grace", toPage);
        forms.toggleCheck(one("agree"), toPage);
        forms.selectRadio(field("color").get(2), field("color"), toPage);
        forms.choose(one("country"), "Japan", toPage);

        List<Located> all = new ArrayList<>();
        for (Annotation a : page.getAnnotations()) {
            if (a instanceof AbstractWidgetAnnotation w && FormController.isFillable(w)) all.add(new Located(0, page, w));
        }
        AnnotationEdits.Edit reset = forms.reset(all, toPage);
        assertEquals("", value(one("name")), "/DV ()");
        assertFalse(((ButtonWidgetAnnotation) one("agree").widget()).isOn());
        assertTrue(((ButtonWidgetAnnotation) field("color").get(0).widget()).isOn(), "/DV /Red");
        assertFalse(((ButtonWidgetAnnotation) field("color").get(2).widget()).isOn());
        assertEquals("Canada", value(one("country")), "/DV (Canada)");

        reset.undo();
        assertEquals("Grace", value(one("name")));
        assertTrue(((ButtonWidgetAnnotation) one("agree").widget()).isOn());
        assertTrue(((ButtonWidgetAnnotation) field("color").get(2).widget()).isOn());
        assertEquals("Japan", value(one("country")));
    }

    @DisplayName("filled values survive an incremental save and reopen")
    @Test
    void roundTrip() throws Exception {
        forms.setText(one("name"), "Grace Hopper", toPage);
        forms.toggleCheck(one("agree"), toPage);
        forms.selectRadio(field("color").get(1), field("color"), toPage);
        forms.choose(one("country"), "Japan", toPage);
        forms.chooseIndexes(one("toppings"), List.of(1), toPage);

        Path saved = Files.createTempFile("forms-roundtrip", ".pdf");
        try {
            try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(saved))) {
                document.saveToOutputStream(out);
            }
            Document reopened = new Document();
            reopened.setFile(saved.toString());
            Page again = reopened.getPageTree().getPage(0);
            again.init();
            assertEquals("Grace Hopper", value(fieldsIn(again, "name").get(0)));
            assertTrue(((ButtonWidgetAnnotation) fieldsIn(again, "agree").get(0).widget()).isOn());
            List<Located> colors = fieldsIn(again, "color");
            assertTrue(((ButtonWidgetAnnotation) colors.get(1).widget()).isOn());
            assertFalse(((ButtonWidgetAnnotation) colors.get(0).widget()).isOn());
            assertEquals("Japan", value(fieldsIn(again, "country").get(0)));
            assertEquals(List.of(1), ((ChoiceWidgetAnnotation) fieldsIn(again, "toppings").get(0).widget())
                    .getFieldDictionary().getIndexes());
            reopened.dispose();
        } finally {
            Files.deleteIfExists(saved);
        }
    }
}
