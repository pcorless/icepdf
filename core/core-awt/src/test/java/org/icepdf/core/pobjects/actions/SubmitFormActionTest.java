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

import com.sun.net.httpserver.HttpServer;
import org.icepdf.core.pobjects.*;
import org.icepdf.core.pobjects.acroform.FieldDictionary;
import org.icepdf.core.pobjects.annotations.AbstractWidgetAnnotation;
import org.icepdf.core.pobjects.annotations.Annotation;
import org.icepdf.core.util.Library;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * An HTML-format SubmitForm action (12.7.5.2) posts every exportable terminal field by its fully
 * qualified name, URL-encoded, to a local server here.  Uses the project-made all_fields.pdf (see
 * make_form_fixture.py): name "Ada", country "France", colour radio group "Red", toppings (multi)
 * "Apple" and "Cherry", and the reset/submit push buttons.
 */
public class SubmitFormActionTest {

    private static final int EXPORT_FORMAT = 1 << 2;
    private static final int GET_METHOD = 1 << 3;
    private static final int SUBMIT_COORDINATES = 1 << 4;

    private HttpServer server;
    private final List<String[]> requests = Collections.synchronizedList(new ArrayList<>());
    private Document document;
    private Page page;

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/submit", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            requests.add(new String[]{exchange.getRequestMethod(), exchange.getRequestURI().getRawQuery(), body,
                    exchange.getRequestHeaders().getFirst("Content-Type")});
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.start();
        document = new Document();
        document.setFile(Paths.get("src/test/resources/acroform/all_fields.pdf").toString());
        page = document.getPageTree().getPage(0);
        page.init();
    }

    @AfterEach
    void stop() {
        server.stop(0);
        document.dispose();
    }

    private String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/submit";
    }

    private SubmitFormAction action(int flags, Object... fields) {
        DictionaryEntries fileSpec = new DictionaryEntries();
        fileSpec.put(new Name("FS"), new Name("URL"));
        fileSpec.put(new Name("F"), new LiteralStringObject(url()));
        DictionaryEntries entries = new DictionaryEntries();
        entries.put(Action.ACTION_TYPE_KEY, new Name("SubmitForm"));
        entries.put(FormAction.F_KEY, fileSpec);
        entries.put(FormAction.FLAGS_KEY, flags);
        if (fields.length > 0) {
            entries.put(FormAction.FIELDS_KEY, new ArrayList<>(Arrays.asList(fields)));
        }
        return new SubmitFormAction(document.getCatalog().getLibrary(), entries);
    }

    /** Submits and returns the decoded pairs the server received, in order. */
    private List<String[]> submit(SubmitFormAction action) {
        assertEquals(200, action.executeFormAction(10, 20));
        assertEquals(1, requests.size(), "one request");
        String[] request = requests.get(0);
        String encoded = "GET".equals(request[0]) ? request[1] : request[2];
        List<String[]> pairs = new ArrayList<>();
        if (encoded != null && !encoded.isEmpty()) {
            for (String pair : encoded.split("&")) {
                String[] kv = pair.split("=", 2);
                pairs.add(new String[]{URLDecoder.decode(kv[0], StandardCharsets.UTF_8),
                        kv.length > 1 ? URLDecoder.decode(kv[1], StandardCharsets.UTF_8) : null});
            }
        }
        return pairs;
    }

    private static List<String> values(List<String[]> pairs, String name) {
        List<String> out = new ArrayList<>();
        for (String[] pair : pairs) {
            if (pair[0].equals(name)) out.add(pair[1]);
        }
        return out;
    }

    private static Set<String> names(List<String[]> pairs) {
        Set<String> out = new LinkedHashSet<>();
        for (String[] pair : pairs) out.add(pair[0]);
        return out;
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

    @DisplayName("every terminal field is submitted by name; buttons are not; no coordinates unless asked")
    @Test
    public void submitsTheForm() {
        // the terminal fields are widget annotations: they used to be skipped, leaving only the
        // two radio-group parents in the post (and coordinates nobody asked for).
        List<String[]> pairs = submit(action(EXPORT_FORMAT));
        assertEquals(List.of("Ada"), values(pairs, "name"));
        assertEquals(List.of("France"), values(pairs, "country"));
        assertEquals(List.of("Red"), values(pairs, "color"), "a radio group submits once, as the group");
        assertEquals(List.of("Apple", "Cherry"), values(pairs, "toppings"), "a multi-select list repeats its name");
        assertFalse(names(pairs).contains("reset"), "push buttons hold no data");
        assertFalse(names(pairs).contains("submit"));
        assertFalse(names(pairs).contains("x"), "coordinates only with SubmitCoordinates");
        assertEquals("POST", requests.get(0)[0]);
        assertTrue(requests.get(0)[3].startsWith("application/x-www-form-urlencoded"), requests.get(0)[3]);
        assertTrue(requests.get(0)[3].contains("UTF-8"), requests.get(0)[3]);
    }

    @DisplayName("names and values are URL-encoded")
    @Test
    public void encodes() {
        widget("name").getFieldDictionary().setFieldValue("Grace & Hopper/é", widget("name").getPObjectReference());
        submit(action(EXPORT_FORMAT));
        assertTrue(requests.get(0)[2].contains("name=Grace+%26+Hopper%2F%C3%A9"), requests.get(0)[2]);
    }

    @DisplayName("GetMethod submits with GET")
    @Test
    public void get() {
        List<String[]> pairs = submit(action(EXPORT_FORMAT | GET_METHOD));
        assertEquals("GET", requests.get(0)[0]);
        assertEquals(List.of("Ada"), values(pairs, "name"));
    }

    @DisplayName("SubmitCoordinates adds the click point")
    @Test
    public void coordinates() {
        List<String[]> pairs = submit(action(EXPORT_FORMAT | SUBMIT_COORDINATES));
        assertEquals(List.of("10"), values(pairs, "x"));
        assertEquals(List.of("20"), values(pairs, "y"));
    }

    @DisplayName("/Fields by name: only those fields")
    @Test
    public void includeByName() {
        List<String[]> pairs = submit(action(EXPORT_FORMAT,
                new LiteralStringObject("name"), new LiteralStringObject("country")));
        assertEquals(Set.of("name", "country"), names(pairs));
    }

    @DisplayName("Include/Exclude set: every field but the listed ones")
    @Test
    public void exclude() {
        List<String[]> pairs = submit(action(EXPORT_FORMAT | 1, new LiteralStringObject("name")));
        assertFalse(names(pairs).contains("name"));
        assertTrue(names(pairs).contains("country"));
    }

    @DisplayName("/Fields by reference to a parent field covers its widgets")
    @Test
    public void includeParentByReference() {
        // the colour group is a non-terminal field built from a bare dictionary: matching it needs
        // the reference it was reached by.
        Reference colour = widget("color").getFieldDictionary().getParent().getPObjectReference();
        assertNotNull(colour);
        List<String[]> pairs = submit(action(EXPORT_FORMAT, colour));
        assertEquals(Set.of("color"), names(pairs));
    }

    @DisplayName("/F may be a plain URL string")
    @Test
    public void plainStringUrl() {
        DictionaryEntries entries = submitAction();
        entries.put(FormAction.F_KEY, new LiteralStringObject("http://example.invalid/submit"));
        assertEquals("http://example.invalid/submit",
                new SubmitFormAction(document.getCatalog().getLibrary(), entries).getSubmitUrl());
    }

    @DisplayName("a nested field is submitted by its full name; NoExport and empty fields are left out")
    @Test
    public void hierarchy() {
        Library library = new Library();
        Reference parent = new Reference(500, 0);
        Reference street = new Reference(501, 0);
        Reference zip = new Reference(502, 0);
        Reference pin = new Reference(503, 0);
        library.addObject(field("address", null, null, Arrays.asList(street, zip, pin)), parent);
        library.addObject(kid("street", "1 Main St", parent, 0), street);
        library.addObject(kid("zip", null, parent, 0), zip);
        library.addObject(kid("pin", "1234", parent, FieldDictionary.NO_EXPORT_BIT_FLAG), pin);

        HashMap<String, String> params = new HashMap<>();
        new SubmitFormAction(library, submitAction()).descendFormTree(parent, params);
        assertEquals(Map.of("address.street", "1 Main St"), params);

        DictionaryEntries withEmpty = submitAction();
        withEmpty.put(FormAction.FLAGS_KEY, 2); // IncludeNoValueFields
        params.clear();
        new SubmitFormAction(library, withEmpty).descendFormTree(parent, params);
        assertTrue(params.containsKey("address.zip"), "an empty field goes by name alone");
        assertFalse(params.containsKey("address.pin"), "NoExport is never submitted");
    }

    private static DictionaryEntries submitAction() {
        DictionaryEntries entries = new DictionaryEntries();
        entries.put(Action.ACTION_TYPE_KEY, new Name("SubmitForm"));
        return entries;
    }

    private static DictionaryEntries field(String name, String value, Reference parent, List<Reference> kids) {
        DictionaryEntries entries = new DictionaryEntries();
        entries.put(FieldDictionary.FT_KEY, new Name("Tx"));
        entries.put(FieldDictionary.T_KEY, new LiteralStringObject(name));
        if (value != null) entries.put(FieldDictionary.V_KEY, new LiteralStringObject(value));
        if (parent != null) entries.put(FieldDictionary.PARENT_KEY, parent);
        if (kids != null) entries.put(FieldDictionary.KIDS_KEY, new ArrayList<>(kids));
        return entries;
    }

    private static DictionaryEntries kid(String name, String value, Reference parent, int flags) {
        DictionaryEntries entries = field(name, value, parent, null);
        if (flags != 0) entries.put(FieldDictionary.Ff_KEY, flags);
        return entries;
    }
}
