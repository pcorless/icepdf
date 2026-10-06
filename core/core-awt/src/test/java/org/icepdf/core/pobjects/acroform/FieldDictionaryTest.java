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
package org.icepdf.core.pobjects.acroform;

import org.icepdf.core.pobjects.DictionaryEntries;
import org.icepdf.core.pobjects.LiteralStringObject;
import org.icepdf.core.pobjects.Name;
import org.icepdf.core.util.Library;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the form field dictionaries: what a field is called, what it holds, and what it allows.
 * <p>
 * A field's behaviour is carried almost entirely in the bits of its {@code /Ff} flag word, and the
 * bits are far apart and easy to confuse - a combo box and a list box differ by one bit, and so do
 * "may be edited" and "may hold several selections".  Reading the wrong bit produces a field that
 * looks right and behaves as a different kind of control, so each flag is asserted on its own.
 */
public class FieldDictionaryTest {

    private final Library library = new Library();

    private DictionaryEntries fieldEntries(Name fieldType) {
        DictionaryEntries entries = new DictionaryEntries();
        entries.put(FieldDictionary.FT_KEY, fieldType);
        entries.put(FieldDictionary.T_KEY, new LiteralStringObject("field"));
        return entries;
    }

    private FieldDictionary build(DictionaryEntries entries) {
        return FieldDictionaryFactory.buildField(library, entries);
    }

    // ------------------------------------------------------------------
    // dispatch
    // ------------------------------------------------------------------

    @DisplayName("each field type builds the dictionary that knows how to read it")
    @Test
    public void dispatch() {
        assertInstanceOf(ButtonFieldDictionary.class,
                build(fieldEntries(FieldDictionaryFactory.TYPE_BUTTON)));
        assertInstanceOf(TextFieldDictionary.class,
                build(fieldEntries(FieldDictionaryFactory.TYPE_TEXT)));
        assertInstanceOf(ChoiceFieldDictionary.class,
                build(fieldEntries(FieldDictionaryFactory.TYPE_CHOICE)));
        assertInstanceOf(SignatureFieldDictionary.class,
                build(fieldEntries(FieldDictionaryFactory.TYPE_SIGNATURE)));
    }

    @DisplayName("a field with no type is still readable")
    @Test
    public void noFieldType() {
        // A child of a field inherits its parent's type, so a kid may carry none of its own.
        DictionaryEntries entries = new DictionaryEntries();
        entries.put(FieldDictionary.T_KEY, new LiteralStringObject("child"));
        assertNotNull(build(entries));
    }

    // ------------------------------------------------------------------
    // names
    // ------------------------------------------------------------------

    @DisplayName("the partial, alternative and mapping names are read from their own keys")
    @Test
    public void names() {
        DictionaryEntries entries = fieldEntries(FieldDictionaryFactory.TYPE_TEXT);
        entries.put(FieldDictionary.T_KEY, new LiteralStringObject("surname"));
        entries.put(FieldDictionary.TU_KEY, new LiteralStringObject("Family name"));
        entries.put(FieldDictionary.TM_KEY, new LiteralStringObject("lastName"));

        FieldDictionary field = build(entries);
        assertEquals("surname", field.getPartialFieldName());
        assertEquals("Family name", field.getAlternativeFieldName());
        assertEquals("lastName", field.getExportMappingName());
    }

    @DisplayName("a field with no parent is fully qualified by its own name")
    @Test
    public void fullyQualifiedName() {
        DictionaryEntries entries = fieldEntries(FieldDictionaryFactory.TYPE_TEXT);
        entries.put(FieldDictionary.T_KEY, new LiteralStringObject("surname"));
        assertEquals("surname", build(entries).getFullyQualifiedFieldName());
    }

    // ------------------------------------------------------------------
    // flags
    // ------------------------------------------------------------------

    @DisplayName("the three flags every field has")
    @Test
    public void commonFlags() {
        DictionaryEntries entries = fieldEntries(FieldDictionaryFactory.TYPE_TEXT);
        FieldDictionary plain = build(entries);
        assertFalse(plain.isReadOnly());
        assertFalse(plain.isRequired());
        assertFalse(plain.isNoExport());

        entries.put(FieldDictionary.Ff_KEY, FieldDictionary.READ_ONLY_BIT_FLAG);
        assertTrue(build(entries).isReadOnly());

        entries.put(FieldDictionary.Ff_KEY, FieldDictionary.REQUIRED_BIT_FLAG);
        assertTrue(build(entries).isRequired());
        assertFalse(build(entries).isReadOnly(), "one flag must not imply another");

        entries.put(FieldDictionary.Ff_KEY, FieldDictionary.NO_EXPORT_BIT_FLAG);
        assertTrue(build(entries).isNoExport());
    }

    @DisplayName("several flags set at once are all reported")
    @Test
    public void combinedFlags() {
        DictionaryEntries entries = fieldEntries(FieldDictionaryFactory.TYPE_TEXT);
        entries.put(FieldDictionary.Ff_KEY,
                FieldDictionary.READ_ONLY_BIT_FLAG | FieldDictionary.REQUIRED_BIT_FLAG);
        FieldDictionary field = build(entries);
        assertTrue(field.isReadOnly());
        assertTrue(field.isRequired());
        assertFalse(field.isNoExport());
    }

    // ------------------------------------------------------------------
    // values
    // ------------------------------------------------------------------

    @DisplayName("a field's value and default value are read from their own keys")
    @Test
    public void values() {
        DictionaryEntries entries = fieldEntries(FieldDictionaryFactory.TYPE_TEXT);
        entries.put(FieldDictionary.V_KEY, new LiteralStringObject("typed"));
        entries.put(FieldDictionary.DV_KEY, new LiteralStringObject("default"));

        FieldDictionary field = build(entries);
        assertTrue(field.hasFieldValue());
        assertTrue(field.hasDefaultValue());
        assertEquals("typed", field.getFieldValue().toString());
        assertEquals("default", field.getDefaultFieldValue().toString());
    }

    @DisplayName("a field with no value says so rather than reporting an empty one")
    @Test
    public void noValue() {
        // The distinction drives whether a widget's appearance is generated at all.
        FieldDictionary field = build(fieldEntries(FieldDictionaryFactory.TYPE_TEXT));
        assertFalse(field.hasFieldValue());
        assertFalse(field.hasDefaultValue());
    }

    // ------------------------------------------------------------------
    // choice fields
    // ------------------------------------------------------------------

    private ChoiceFieldDictionary choice(int flags, Object options) {
        DictionaryEntries entries = fieldEntries(FieldDictionaryFactory.TYPE_CHOICE);
        entries.put(FieldDictionary.Ff_KEY, flags);
        if (options != null) {
            entries.put(ChoiceFieldDictionary.OPT_KEY, options);
        }
        return (ChoiceFieldDictionary) build(entries);
    }

    @DisplayName("choice - the flag bits pick the kind of control")
    @Test
    public void choiceFieldType() {
        assertEquals(ChoiceFieldDictionary.ChoiceFieldType.CHOICE_LIST_SINGLE_SELECT,
                choice(0, null).getChoiceFieldType());
        assertEquals(ChoiceFieldDictionary.ChoiceFieldType.CHOICE_LIST_MULTIPLE_SELECT,
                choice(ChoiceFieldDictionary.MULTI_SELECT_BIT_FLAG, null).getChoiceFieldType());
        assertEquals(ChoiceFieldDictionary.ChoiceFieldType.CHOICE_COMBO,
                choice(ChoiceFieldDictionary.COMBO_BIT_FLAG, null).getChoiceFieldType());
        assertEquals(ChoiceFieldDictionary.ChoiceFieldType.CHOICE_EDITABLE_COMBO,
                choice(ChoiceFieldDictionary.COMBO_BIT_FLAG | ChoiceFieldDictionary.EDIT_BIT_FLAG,
                        null).getChoiceFieldType());
    }

    @DisplayName("choice - options written as plain strings are both label and value")
    @Test
    public void choiceOptionsAsStrings() {
        List<Object> options = new ArrayList<>(Arrays.asList(
                new LiteralStringObject("Red"), new LiteralStringObject("Green")));
        ChoiceFieldDictionary field = choice(0, options);

        assertEquals(2, field.getOptions().size());
        assertEquals("Red", field.getOptions().get(0).getLabel());
        assertEquals("Red", field.getOptions().get(0).getValue(),
                "a bare string option exports the text the user sees");
    }

    @DisplayName("choice - options written as pairs keep the export value apart from the label")
    @Test
    public void choiceOptionsAsPairs() {
        // [[export display] ...]: exporting the label instead of the value sends the wrong data.
        List<Object> pair = new ArrayList<>(Arrays.asList(
                new LiteralStringObject("R"), new LiteralStringObject("Red")));
        List<Object> options = new ArrayList<>(Arrays.asList((Object) pair));
        ChoiceFieldDictionary field = choice(0, options);

        assertEquals(1, field.getOptions().size());
        assertEquals("Red", field.getOptions().get(0).getLabel());
        assertEquals("R", field.getOptions().get(0).getValue());
    }

    @DisplayName("choice - a field with no options has an empty list, not null")
    @Test
    public void choiceWithoutOptions() {
        assertNull(choice(0, null).getOptions());
    }

    @DisplayName("choice - the top index and the selected indexes are read back")
    @Test
    public void choiceIndexes() {
        DictionaryEntries entries = fieldEntries(FieldDictionaryFactory.TYPE_CHOICE);
        entries.put(ChoiceFieldDictionary.TI_KEY, 3);
        entries.put(ChoiceFieldDictionary.I_KEY, new ArrayList<>(Arrays.asList(1, 2)));

        ChoiceFieldDictionary field = (ChoiceFieldDictionary) build(entries);
        assertEquals(3, field.getTopIndex());
        assertEquals(Arrays.asList(1, 2), field.getIndexes());
    }

    @DisplayName("choice - an option built by hand carries both of its strings")
    @Test
    public void buildChoiceOption() {
        ChoiceFieldDictionary field = choice(0, null);
        ChoiceFieldDictionary.ChoiceOption option = field.buildChoiceOption("Blue", "B");
        assertEquals("Blue", option.getLabel());
        assertEquals("B", option.getValue());
        assertFalse(option.isSelected());
    }
}
