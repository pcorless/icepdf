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
package org.icepdf.core.pobjects.annotations;

import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.Page;
import org.icepdf.core.util.Library;
import org.icepdf.core.util.updater.WriteMode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.awt.Rectangle;
import java.io.BufferedOutputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The private annotation flag (/F 0x400) and the rule that hides other authors' private markup
 * annotations when a page loads: the bit itself, who sees what, popups, annotations without an
 * author, and that saving never drops what was hidden.
 */
class PrivateAnnotationsTest {

    private static final Path FIXTURE = Paths.get("src/test/resources/annotations/text_field.pdf");
    private static final String ALICE = "alice", BOB = "bob";

    @TempDir
    Path dir;

    @AfterEach
    void reset() {
        PrivateAnnotations.reset();
    }

    private static Document open(Path file) throws Exception {
        Document document = new Document();
        document.setFile(file.toString());
        return document;
    }

    /** A sticky note by {@code author} with contents {@code text}, private or not, with its popup listed in /Annots. */
    private static TextAnnotation note(Page page, String author, String text, boolean priv) {
        Library library = page.getLibrary();
        TextAnnotation note = (TextAnnotation) AnnotationFactory.buildAnnotation(library, Annotation.SUBTYPE_TEXT,
                new Rectangle(50, 50, 20, 20));
        if (author != null) note.setTitleText(author); // null: no /T at all
        note.setContents(text);
        note.setFlag(Annotation.FLAG_PRINT, true);
        note.setFlag(Annotation.FLAG_PRIVATE_CONTENTS, priv);
        PopupAnnotation popup = (PopupAnnotation) AnnotationFactory.buildAnnotation(library, Annotation.SUBTYPE_POPUP,
                new Rectangle(80, 50, 120, 80));
        popup.setParent(note);
        popup.setOpen(true);
        popup.setFlag(Annotation.FLAG_PRIVATE_CONTENTS, priv);
        note.setPopupAnnotation(popup);
        page.addAnnotation(note);
        page.addAnnotation(popup);
        return note;
    }

    /** Alice's private note, Bob's private and public notes, and a private note with no author; saved. */
    private Path fixture(WriteMode mode) throws Exception {
        Document document = open(FIXTURE);
        try {
            Page page = document.getPageTree().getPage(0);
            note(page, ALICE, "alice private", true);
            note(page, BOB, "bob private", true);
            note(page, BOB, "bob public", false);
            note(page, null, "nobody private", true);
            Path saved = dir.resolve("private-" + mode + ".pdf");
            try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(saved))) {
                document.saveToOutputStream(out, mode);
            }
            return saved;
        } finally {
            document.dispose();
        }
    }

    /** The contents of the markup annotations a page shows, and of the popups' parents. */
    private static List<String> shown(Page page) {
        List<String> shown = new ArrayList<>();
        for (Annotation a : page.getAnnotations()) {
            if (a instanceof TextAnnotation) shown.add(((TextAnnotation) a).getContents());
            else if (a instanceof PopupAnnotation) shown.add("popup:" + ((PopupAnnotation) a).getParent().getContents());
        }
        return shown;
    }

    @DisplayName("the private bit is its own: setting and clearing it leaves the other flags alone")
    @Test
    void flagBit() throws Exception {
        Document document = open(FIXTURE);
        try {
            TextAnnotation note = note(document.getPageTree().getPage(0), ALICE, "x", false);
            note.setFlag(Annotation.FLAG_NO_ZOOM, true);
            assertEquals(0x400, Annotation.FLAG_PRIVATE_CONTENTS);
            assertFalse(note.getFlagPrivateContents());
            note.setFlag(Annotation.FLAG_PRIVATE_CONTENTS, true);
            assertTrue(note.getFlagPrivateContents());
            note.setFlag(Annotation.FLAG_PRIVATE_CONTENTS, true);
            assertTrue(note.getFlagPrivateContents(), "setting twice keeps it set");
            assertTrue(note.getFlagPrint() && note.getFlagNoZoom());
            note.setFlag(Annotation.FLAG_PRIVATE_CONTENTS, false);
            assertFalse(note.getFlagPrivateContents());
            assertTrue(note.getFlagPrint() && note.getFlagNoZoom(), "clearing it keeps the others");
        } finally {
            document.dispose();
        }
    }

    @DisplayName("who is hidden: another author's private markup and its popup; never with the feature off")
    @Test
    void rule() throws Exception {
        Document document = open(FIXTURE);
        try {
            Page page = document.getPageTree().getPage(0);
            TextAnnotation mine = note(page, ALICE, "a", true);
            TextAnnotation theirs = note(page, BOB, "b", true);
            TextAnnotation theirsPublic = note(page, BOB, "c", false);
            TextAnnotation nobody = note(page, null, "d", true);
            LinkAnnotation link = (LinkAnnotation) AnnotationFactory.buildAnnotation(page.getLibrary(),
                    Annotation.SUBTYPE_LINK, new Rectangle(0, 0, 10, 10));
            link.setFlag(Annotation.FLAG_PRIVATE_CONTENTS, true);

            PrivateAnnotations.configure(false, ALICE);
            assertFalse(PrivateAnnotations.hides(theirs), "feature off: nothing is hidden");

            PrivateAnnotations.configure(true, ALICE);
            assertFalse(PrivateAnnotations.hides(mine));
            assertFalse(PrivateAnnotations.hides(mine.getPopupAnnotation()));
            assertTrue(PrivateAnnotations.hides(theirs));
            assertTrue(PrivateAnnotations.hides(theirs.getPopupAnnotation()), "the popup goes with it");
            assertFalse(PrivateAnnotations.hides(theirsPublic));
            assertTrue(PrivateAnnotations.hides(nobody), "no author is nobody's: hidden from everyone");
            assertFalse(PrivateAnnotations.hides(link), "only markup annotations can be private");
            assertFalse(PrivateAnnotations.hides(null));

            PrivateAnnotations.configure(true, "Alice");
            assertTrue(PrivateAnnotations.hides(mine), "the author must match exactly, as core always has");
        } finally {
            document.dispose();
        }
    }

    @DisplayName("loading a page: each author sees their own private notes and everyone's public ones")
    @ParameterizedTest
    @EnumSource(WriteMode.class)
    void pageLoad(WriteMode mode) throws Exception {
        Path saved = fixture(mode);

        PrivateAnnotations.configure(true, ALICE);
        Document asAlice = open(saved);
        try {
            List<String> shown = shown(asAlice.getPageTree().getPage(0));
            assertTrue(shown.containsAll(List.of("alice private", "popup:alice private", "bob public",
                    "popup:bob public")), shown.toString());
            assertFalse(shown.contains("bob private"), shown.toString());
            assertFalse(shown.contains("popup:bob private"), "Bob's popup is left out too: " + shown);
            assertFalse(shown.contains("nobody private"), shown.toString());
        } finally {
            asAlice.dispose();
        }

        PrivateAnnotations.configure(true, BOB);
        Document asBob = open(saved);
        try {
            List<String> shown = shown(asBob.getPageTree().getPage(0));
            assertTrue(shown.containsAll(List.of("bob private", "bob public")), shown.toString());
            assertFalse(shown.contains("alice private"), shown.toString());
        } finally {
            asBob.dispose();
        }

        PrivateAnnotations.configure(false, ALICE);
        Document plain = open(saved);
        try {
            List<String> shown = shown(plain.getPageTree().getPage(0));
            assertTrue(shown.containsAll(List.of("alice private", "bob private", "bob public", "nobody private")),
                    "feature off: everything, private bit or not: " + shown);
            for (Annotation a : plain.getPageTree().getPage(0).getAnnotations()) {
                if (a instanceof TextAnnotation && ((TextAnnotation) a).getContents().endsWith("private")) {
                    TextAnnotation note = (TextAnnotation) a;
                    assertTrue(note.getFlagPrivateContents(), "the bit survives the save: " + note.getContents());
                    assertTrue(note.getPopupAnnotation().getFlagPrivateContents());
                }
            }
        } finally {
            plain.dispose();
        }
    }

    @DisplayName("Alice adds a note and saves: Bob's private note, hidden from her, is still in the file")
    @ParameterizedTest
    @EnumSource(WriteMode.class)
    void saveKeepsHidden(WriteMode mode) throws Exception {
        Path saved = fixture(WriteMode.INCREMENT_UPDATE);

        PrivateAnnotations.configure(true, ALICE);
        Path again = dir.resolve("again-" + mode + ".pdf");
        Document asAlice = open(saved);
        try {
            Page page = asAlice.getPageTree().getPage(0);
            assertFalse(shown(page).contains("bob private"));
            note(page, ALICE, "alice later", false);
            try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(again))) {
                asAlice.saveToOutputStream(out, mode);
            }
        } finally {
            asAlice.dispose();
        }

        PrivateAnnotations.configure(true, BOB);
        Document asBob = open(again);
        try {
            List<String> shown = shown(asBob.getPageTree().getPage(0));
            assertTrue(shown.contains("bob private"), "not lost by Alice's save: " + shown);
            assertTrue(shown.contains("alice later"), shown.toString());
            assertFalse(shown.contains("alice private"), shown.toString());
        } finally {
            asBob.dispose();
        }
    }
}
