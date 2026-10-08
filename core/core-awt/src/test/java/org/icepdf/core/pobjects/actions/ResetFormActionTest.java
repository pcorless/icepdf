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
package org.icepdf.core.pobjects.actions;

import org.icepdf.core.pobjects.DictionaryEntries;
import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.LiteralStringObject;
import org.icepdf.core.pobjects.Name;
import org.icepdf.core.pobjects.Page;
import org.icepdf.core.pobjects.Reference;
import org.icepdf.core.pobjects.acroform.FieldDictionary;
import org.icepdf.core.pobjects.annotations.AbstractWidgetAnnotation;
import org.icepdf.core.pobjects.annotations.Annotation;
import org.icepdf.core.pobjects.annotations.ButtonWidgetAnnotation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A ResetForm action resets the fields its /Fields array names (and their descendants), or with the
 * Include/Exclude flag set every field except those; without /Fields, every field.  Uses the
 * project-made all_fields.pdf (see make_form_fixture.py): "name" (/DV ()), "code" (no /DV), "country"
 * (/DV Canada), "fruit" (no /DV).
 */
public class ResetFormActionTest {

    private Document document;
    private Page page;

    @BeforeEach
    void open() throws Exception {
        document = new Document();
        document.setFile(Paths.get("src/test/resources/acroform/all_fields.pdf").toString());
        page = document.getPageTree().getPage(0);
        page.init();
        // fill everything the test looks at
        field("name").setFieldValue("Grace", null);
        field("code").setFieldValue("XYZ", null);
        field("country").setFieldValue("Japan", null);
        field("fruit").setFieldValue("Cherry", null);
    }

    @AfterEach
    void close() {
        document.dispose();
    }

    private AbstractWidgetAnnotation<?> widget(String name) {
        for (Annotation a : page.getAnnotations()) {
            if (a instanceof AbstractWidgetAnnotation
                    && name.equals(((AbstractWidgetAnnotation<?>) a).getFieldDictionary().getFullyQualifiedFieldName())) {
                return (AbstractWidgetAnnotation<?>) a;
            }
        }
        throw new AssertionError("no field " + name);
    }

    private FieldDictionary field(String name) {
        return widget(name).getFieldDictionary();
    }

    private Object value(String name) {
        return field(name).getFieldValue();
    }

    private void reset(int flags, Object... fields) {
        DictionaryEntries entries = new DictionaryEntries();
        entries.put(Action.ACTION_TYPE_KEY, Action.ACTION_TYPE_RESET_SUBMIT);
        if (fields.length > 0) {
            entries.put(FormAction.FIELDS_KEY, new ArrayList<>(Arrays.asList(fields)));
        }
        entries.put(FormAction.FLAGS_KEY, flags);
        new ResetFormAction(document.getCatalog().getLibrary(), entries).executeFormAction(0, 0);
    }

    @DisplayName("no /Fields: every field is reset")
    @Test
    public void all() {
        reset(0);
        assertEquals("", value("name"));
        assertEquals("", value("code"));
        assertEquals("Canada", value("country"));
    }

    @DisplayName("/Fields by name: only those fields are reset")
    @Test
    public void includeByName() {
        reset(0, new LiteralStringObject("name"), new LiteralStringObject("country"));
        assertEquals("", value("name"));
        assertEquals("Canada", value("country"));
        assertEquals("XYZ", value("code"), "not listed");
        assertEquals("Cherry", value("fruit"), "not listed");
    }

    @DisplayName("/Fields by reference: the referenced field is reset")
    @Test
    public void includeByReference() {
        Reference country = widget("country").getPObjectReference();
        reset(0, country);
        assertEquals("Canada", value("country"));
        assertEquals("Grace", value("name"), "not listed");
    }

    @DisplayName("Include/Exclude set: every field except the listed ones is reset")
    @Test
    public void exclude() {
        reset(1, new LiteralStringObject("name"));
        assertEquals("Grace", value("name"), "excluded");
        assertEquals("", value("code"));
        assertEquals("Canada", value("country"));
    }

    // ---- buttons: the model itself is reset, with no viewer listening -----------------------

    /** Every widget of a field (a radio group's kids share the group's name). */
    private List<ButtonWidgetAnnotation> buttons(String name) {
        List<ButtonWidgetAnnotation> out = new ArrayList<>();
        for (Annotation a : page.getAnnotations()) {
            if (a instanceof ButtonWidgetAnnotation
                    && name.equals(((ButtonWidgetAnnotation) a).getFieldDictionary().getFullyQualifiedFieldName())) {
                out.add((ButtonWidgetAnnotation) a);
            }
        }
        if (out.isEmpty()) throw new AssertionError("no field " + name);
        return out;
    }

    private static Name onName(ButtonWidgetAnnotation button) {
        return button.getAppearances().get(button.getCurrentAppearance()).getOnName();
    }

    /** The radio group's value lives on the parent field. */
    private static Object groupValue(ButtonWidgetAnnotation kid) {
        return kid.getFieldDictionary().getParent().getFieldValue();
    }

    /** Turns on the kids whose on-state is {@code on} and the rest off, as a click would. */
    private void select(String group, String on) {
        for (ButtonWidgetAnnotation kid : buttons(group)) {
            if (onName(kid).getName().equals(on)) kid.turnOn();
            else kid.turnOff();
            kid.getFieldDictionary().getParent().setFieldValue(new Name(on), null);
        }
    }

    @DisplayName("check box: reset turns it off (its /DV) and sets /V and /AS to match")
    @Test
    public void checkBox() {
        ButtonWidgetAnnotation agree = buttons("agree").get(0);
        agree.turnOn();
        agree.getFieldDictionary().setFieldValue(onName(agree), null);
        reset(0, new LiteralStringObject("agree"));
        assertFalse(agree.isOn(), "appearance off");
        assertEquals(new Name("Off"), agree.getFieldDictionary().getFieldValue());
        assertEquals(new Name("Off"), agree.getEntries().get(Annotation.APPEARANCE_STATE_KEY));
    }

    @DisplayName("radio group: reset selects the /DV kid and turns the others off")
    @Test
    public void radioGroup() {
        select("color", "Green");
        reset(0, new LiteralStringObject("color"));
        for (ButtonWidgetAnnotation kid : buttons("color")) {
            boolean red = onName(kid).getName().equals("Red");
            assertEquals(red, kid.isOn(), onName(kid) + " on");
            assertEquals(red ? new Name("Red") : new Name("Off"), kid.getEntries().get(Annotation.APPEARANCE_STATE_KEY));
        }
        assertEquals(new Name("Red"), groupValue(buttons("color").get(0)));
    }

    @DisplayName("radios in unison: reset to /DV /Off turns every kid off")
    @Test
    public void radiosInUnison() {
        select("pair", "A");
        reset(0, new LiteralStringObject("pair"));
        for (ButtonWidgetAnnotation kid : buttons("pair")) {
            assertFalse(kid.isOn(), onName(kid) + " on");
        }
        assertEquals(new Name("Off"), groupValue(buttons("pair").get(0)));
    }

    @DisplayName("reset is recorded, so a save writes the reset state")
    @Test
    public void resetIsPersisted() {
        ButtonWidgetAnnotation agree = buttons("agree").get(0);
        agree.turnOn();
        reset(0, new LiteralStringObject("agree"));
        assertTrue(document.getStateManager().contains(agree.getPObjectReference()), "agree recorded");
        ButtonWidgetAnnotation kid = buttons("color").get(0);
        select("color", "Blue");
        reset(0, new LiteralStringObject("color"));
        assertTrue(document.getStateManager().contains(kid.getFieldDictionary().getParent().getPObjectReference()),
                "color group recorded");
    }
}
