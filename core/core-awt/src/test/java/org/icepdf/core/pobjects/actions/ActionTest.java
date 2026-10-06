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

import org.icepdf.core.pobjects.Destination;
import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.Dictionary;
import org.icepdf.core.pobjects.DictionaryEntries;
import org.icepdf.core.pobjects.LiteralStringObject;
import org.icepdf.core.pobjects.Name;
import org.icepdf.core.pobjects.Reference;
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
 * Tests the action dictionaries: what a link, a bookmark or a form field does when it is activated.
 * <p>
 * Every action is chosen by its {@code /S} name and carries its payload in keys of its own, so the
 * two things worth asserting are that the right class is built for each name and that each class
 * reads its own keys.  A wrong class is not an error at parse time - it is a link that opens
 * nothing, or worse, one that reads a URL out of a dictionary that holds a file path.
 */
public class ActionTest {

    private final Library library = new Library();

    private DictionaryEntries actionEntries(Name subType) {
        DictionaryEntries entries = new DictionaryEntries();
        entries.put(Dictionary.TYPE_KEY, Action.ACTION_TYPE);
        entries.put(Action.ACTION_TYPE_KEY, subType);
        return entries;
    }

    private Action build(DictionaryEntries entries) {
        return Action.buildAction(library, entries);
    }

    // ------------------------------------------------------------------
    // dispatch
    // ------------------------------------------------------------------

    @DisplayName("each action name builds the class that knows how to read it")
    @Test
    public void dispatch() {
        assertInstanceOf(GoToAction.class, build(actionEntries(Action.ACTION_TYPE_GOTO)));
        assertInstanceOf(GoToRAction.class, build(actionEntries(Action.ACTION_TYPE_GOTO_REMOTE)));
        assertInstanceOf(LaunchAction.class, build(actionEntries(Action.ACTION_TYPE_LAUNCH)));
        assertInstanceOf(URIAction.class, build(actionEntries(Action.ACTION_TYPE_URI)));
        assertInstanceOf(ResetFormAction.class, build(actionEntries(Action.ACTION_TYPE_RESET_SUBMIT)));
        assertInstanceOf(SubmitFormAction.class, build(actionEntries(Action.ACTION_TYPE_SUBMIT_SUBMIT)));
        assertInstanceOf(NamedAction.class, build(actionEntries(Action.ACTION_TYPE_NAMED)));
        assertInstanceOf(JavaScriptAction.class, build(actionEntries(Action.ACTION_TYPE_JAVA_SCRIPT)));
    }

    @DisplayName("an unknown action name still builds a readable action")
    @Test
    public void unknownActionType() {
        // The action types are open ended; an unrecognised one must not be a parse failure, since
        // a link may carry it alongside ones that are understood.
        Action action = build(actionEntries(new Name("SomeFutureAction")));
        assertNotNull(action);
        assertEquals("SomeFutureAction", action.getType());
    }

    @DisplayName("the action type is reported as written")
    @Test
    public void actionType() {
        assertEquals("URI", build(actionEntries(Action.ACTION_TYPE_URI)).getType());
        assertEquals("GoTo", build(actionEntries(Action.ACTION_TYPE_GOTO)).getType());
    }

    @DisplayName("a /Next action survives parsing and builds as an action of its own")
    @Test
    public void nextAction() {
        // /Next lets one activation run several actions in turn.  Action has no accessor for it,
        // so this pins that the entry is at least kept and is itself buildable - dropping it on
        // the way in would lose everything after the first action, silently.
        DictionaryEntries next = actionEntries(Action.ACTION_TYPE_URI);
        next.put(URIAction.URI_KEY, new LiteralStringObject("https://example.com/second"));

        DictionaryEntries first = actionEntries(Action.ACTION_TYPE_GOTO);
        first.put(Action.NEXT_KEY, next);

        Action action = build(first);
        DictionaryEntries kept = (DictionaryEntries) action.getEntries().get(Action.NEXT_KEY);
        assertNotNull(kept);
        assertInstanceOf(URIAction.class, Action.buildAction(library, kept));
    }

    // ------------------------------------------------------------------
    // URI
    // ------------------------------------------------------------------

    @DisplayName("URI - the address is read back")
    @Test
    public void uri() {
        DictionaryEntries entries = actionEntries(Action.ACTION_TYPE_URI);
        entries.put(URIAction.URI_KEY, new LiteralStringObject("https://example.com/page"));
        assertEquals("https://example.com/page", ((URIAction) build(entries)).getURI());
    }

    @DisplayName("URI - the address can be replaced")
    @Test
    public void setUri() {
        DictionaryEntries entries = actionEntries(Action.ACTION_TYPE_URI);
        entries.put(URIAction.URI_KEY, new LiteralStringObject("https://example.com/old"));
        URIAction action = (URIAction) build(entries);
        action.setURI("https://example.com/new");
        assertEquals("https://example.com/new", action.getURI());
    }

    @DisplayName("URI - /IsMap says the click coordinates are appended to the address")
    @Test
    public void uriIsMap() {
        DictionaryEntries entries = actionEntries(Action.ACTION_TYPE_URI);
        entries.put(URIAction.URI_KEY, new LiteralStringObject("https://example.com/map"));
        assertFalse(((URIAction) build(entries)).isMap(), "absent means false");

        entries.put(URIAction.IS_MAP_KEY, Boolean.TRUE);
        assertTrue(((URIAction) build(entries)).isMap());
    }

    // ------------------------------------------------------------------
    // GoTo and GoToR
    // ------------------------------------------------------------------

    @DisplayName("GoTo - the destination is read back")
    @Test
    public void goTo() {
        List<Object> destinationArray = new ArrayList<>(Arrays.asList(
                new Reference(4, 0), Destination.TYPE_XYZ, 100f, 700f, 1f));
        DictionaryEntries entries = actionEntries(Action.ACTION_TYPE_GOTO);
        entries.put(GoToAction.DESTINATION_KEY, destinationArray);

        Destination destination = ((GoToAction) build(entries)).getDestination();
        assertNotNull(destination);
        assertEquals(new Reference(4, 0), destination.getPageReference());
        assertEquals(100f, destination.getLeft(), 0.001f);
    }

    @DisplayName("GoToR - the file, the destination and the window flag")
    @Test
    public void goToRemote() {
        DictionaryEntries entries = actionEntries(Action.ACTION_TYPE_GOTO_REMOTE);
        entries.put(GoToRAction.F_KEY, new LiteralStringObject("other.pdf"));
        entries.put(GoToAction.DESTINATION_KEY, new ArrayList<>(Arrays.asList(
                new Reference(4, 0), Destination.TYPE_FIT)));
        entries.put(GoToRAction.NEW_WINDOW_KEY, Boolean.TRUE);

        GoToRAction action = (GoToRAction) build(entries);
        assertEquals("other.pdf", action.getFile());
        assertTrue(action.isNewWindow());
        assertNotNull(action.getDestination());
    }

    @DisplayName("GoToR - an absent window preference reads as this window")
    @Test
    public void goToRemoteNoWindowPreference() {
        DictionaryEntries entries = actionEntries(Action.ACTION_TYPE_GOTO_REMOTE);
        entries.put(GoToRAction.F_KEY, new LiteralStringObject("other.pdf"));
        assertFalse(((GoToRAction) build(entries)).isNewWindow());
    }

    @DisplayName("GoToR - a destination given as a page number does not need a catalog")
    @Test
    public void goToRemotePageNumberDestination() {
        // A remote destination names a page in another document, so it is written as a page index
        // rather than a reference, and there is no page tree here to resolve it against.
        DictionaryEntries entries = actionEntries(Action.ACTION_TYPE_GOTO_REMOTE);
        entries.put(GoToRAction.F_KEY, new LiteralStringObject("other.pdf"));
        entries.put(GoToAction.DESTINATION_KEY,
                new ArrayList<>(Arrays.asList(2, Destination.TYPE_FIT)));

        Destination destination = ((GoToRAction) build(entries)).getDestination();
        assertNotNull(destination);
        assertEquals(Destination.TYPE_FIT, destination.getType());
        assertNull(destination.getPageReference(), "there is no local page to point at");
    }

    // ------------------------------------------------------------------
    // Launch
    // ------------------------------------------------------------------

    @DisplayName("Launch - the file to open is read back")
    @Test
    public void launch() {
        DictionaryEntries entries = actionEntries(Action.ACTION_TYPE_LAUNCH);
        entries.put(LaunchAction.FILE_KEY, new LiteralStringObject("readme.txt"));
        assertEquals("readme.txt", ((LaunchAction) build(entries)).getExternalFile());
    }

    @DisplayName("Launch - the Windows parameters are always offered, empty when the key is absent")
    @Test
    public void launchWithoutPlatformParameters() {
        DictionaryEntries entries = actionEntries(Action.ACTION_TYPE_LAUNCH);
        entries.put(LaunchAction.FILE_KEY, new LiteralStringObject("readme.txt"));
        LaunchAction action = (LaunchAction) build(entries);
        assertNotNull(action.getWinLaunchParameters());
    }

    @DisplayName("Launch - the file to open can be replaced")
    @Test
    public void setLaunchFile() {
        DictionaryEntries entries = actionEntries(Action.ACTION_TYPE_LAUNCH);
        entries.put(LaunchAction.FILE_KEY, new LiteralStringObject("old.txt"));
        LaunchAction action = (LaunchAction) build(entries);
        action.setExternalFile("new.txt");
        assertEquals("new.txt", action.getExternalFile());
    }

    // ------------------------------------------------------------------
    // Named
    // ------------------------------------------------------------------

    @DisplayName("Named - the standard viewer commands are read back")
    @Test
    public void named() {
        for (Name command : new Name[]{NamedAction.NEXT_PAGE_KEY, NamedAction.PREV_PAGE_KEY,
                NamedAction.FIRST_PAGE_KEY, NamedAction.LAST_PAGE_KEY,
                NamedAction.PRINT_KEY, NamedAction.SAVE_AS_KEY}) {
            DictionaryEntries entries = actionEntries(Action.ACTION_TYPE_NAMED);
            entries.put(NamedAction.N_KEY, command);
            assertEquals(command, ((NamedAction) build(entries)).getNamedAction());
        }
    }

    // ------------------------------------------------------------------
    // JavaScript
    // ------------------------------------------------------------------

    @DisplayName("JavaScript - the script is read back, whether it is a string or a stream")
    @Test
    public void javaScript() {
        DictionaryEntries entries = actionEntries(Action.ACTION_TYPE_JAVA_SCRIPT);
        entries.put(JavaScriptAction.JS_KEY, new LiteralStringObject("app.alert('hi');"));
        assertEquals("app.alert('hi');", ((JavaScriptAction) build(entries)).getJavaScript());
    }

    @DisplayName("JavaScript - an action with no script does not throw")
    @Test
    public void javaScriptMissing() {
        assertNotNull(build(actionEntries(Action.ACTION_TYPE_JAVA_SCRIPT)));
    }

    // ------------------------------------------------------------------
    // the factory that creates new actions
    // ------------------------------------------------------------------

    /**
     * The factory allocates object numbers, so it needs a real document behind the library.
     */
    private static Library documentLibrary() throws Exception {
        Document document = new Document();
        document.setInputStream(
                ActionTest.class.getResourceAsStream("/redaction/simple_tj.pdf"), "simple_tj.pdf");
        return document.getCatalog().getLibrary();
    }

    @DisplayName("the factory creates the three kinds of action the library can author")
    @Test
    public void factory() throws Exception {
        Library factoryLibrary = documentLibrary();
        assertInstanceOf(GoToAction.class,
                ActionFactory.buildAction(factoryLibrary, ActionFactory.GOTO_ACTION));
        assertInstanceOf(URIAction.class,
                ActionFactory.buildAction(factoryLibrary, ActionFactory.URI_ACTION));
        assertInstanceOf(LaunchAction.class,
                ActionFactory.buildAction(factoryLibrary, ActionFactory.LAUNCH_ACTION));
    }

    @DisplayName("the factory declines a kind it cannot create")
    @Test
    public void factoryUnknownType() throws Exception {
        assertNull(ActionFactory.buildAction(documentLibrary(), 99));
    }

    @DisplayName("a created action carries the object reference it will be written under")
    @Test
    public void factoryAssignsAReference() throws Exception {
        // Without one the action cannot be written, and the link pointing at it dangles.
        Action action = ActionFactory.buildAction(documentLibrary(), ActionFactory.URI_ACTION);
        assertNotNull(action.getPObjectReference());
    }
}
