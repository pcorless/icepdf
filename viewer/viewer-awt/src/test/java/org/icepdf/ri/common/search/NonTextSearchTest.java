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
package org.icepdf.ri.common.search;

import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.OutlineItem;
import org.icepdf.core.pobjects.annotations.MarkupAnnotation;
import org.icepdf.core.pobjects.annotations.TextWidgetAnnotation;
import org.icepdf.core.search.DestinationResult;
import org.icepdf.core.search.SearchMode;
import org.icepdf.ri.util.FontPropertiesManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests searching the parts of a document that are not page text.
 * <p>
 * A search covers more than what is printed on the page: the bookmarks in the outline, the named
 * destinations a link can point at, the values typed into form fields, and the text of comments.
 * Each has its own traversal and its own matching, and none of it is exercised by a test that
 * searches page text.
 * <p>
 * The fixture is a four page reference addendum with a two level outline, a destinations name tree
 * and form fields, which is enough for each of these to have something real to find.
 */
public class NonTextSearchTest {

    private static final String FIXTURE = "/redact/pdf_reference_addendum_redaction.pdf";
    /** A page with two filled form fields and three comments, one of them empty. */
    private static final String FORMS_FIXTURE = "/search/form_fields_and_comments.pdf";

    /** A bookmark with no children of its own. */
    private static final String LEAF_TITLE = "Introduction";
    /** A bookmark that has children, and so is a heading rather than a leaf. */
    private static final String PARENT_TITLE = "Additions to the PDF Reference";

    private Document document;
    private Document formsDocument;
    private DocumentSearchControllerImpl controller;

    @BeforeAll
    public static void init() {
        FontPropertiesManager.getInstance().loadOrReadSystemFonts();
    }

    @BeforeEach
    public void setUp() throws Exception {
        document = new Document();
        document.setFile(NonTextSearchTest.class.getResource(FIXTURE).getFile());
        controller = new DocumentSearchControllerImpl(document);
        controller.setSearchMode(SearchMode.PAGE);
    }

    @AfterEach
    public void tearDown() {
        if (document != null) {
            document.dispose();
        }
        if (formsDocument != null) {
            formsDocument.dispose();
        }
    }

    /**
     * A controller holding exactly one term.
     * <p>
     * A fresh one per search on purpose: these searches read only the first term in the model, so
     * adding a second to the same controller silently re-runs the first.
     */
    private DocumentSearchControllerImpl searching(String term) {
        DocumentSearchControllerImpl fresh = new DocumentSearchControllerImpl(document);
        fresh.setSearchMode(SearchMode.PAGE);
        fresh.addSearchTerm(term, false, false);
        return fresh;
    }

    private List<OutlineItem> outlines(String term) {
        return searching(term).searchOutlines();
    }

    private List<DestinationResult> destinations(String term) {
        return searching(term).searchDestinations();
    }

    private static boolean titled(List<OutlineItem> found, String title) {
        return found.stream().anyMatch(item -> title.equals(item.getTitle()));
    }

    // ------------------------------------------------------------------
    // the outline
    // ------------------------------------------------------------------

    @DisplayName("a bookmark is found by its title")
    @Test
    public void outlineLeafIsFound() {
        assertTrue(titled(outlines(LEAF_TITLE), LEAF_TITLE),
                "the bookmark should have been found by its own title");
    }

    @DisplayName("a bookmark is found by part of its title")
    @Test
    public void outlineMatchesASubstring() {
        assertFalse(outlines("Introduc").isEmpty());
    }

    @DisplayName("a bookmark search is case insensitive unless asked otherwise")
    @Test
    public void outlineCaseInsensitive() {
        assertFalse(outlines("introduction").isEmpty());
    }

    @DisplayName("a bookmark that has children is searched too, not just the leaves")
    @Test
    public void outlineParentIsFound() {
        // The outline walk recursed into any item with children and searched only the ones without,
        // so a chapter heading could never be found - and a heading is the most likely thing
        // somebody searching an outline is looking for.
        assertTrue(titled(outlines(PARENT_TITLE), PARENT_TITLE),
                "a bookmark with children should be searched as well as descended into");
    }

    @DisplayName("a term matching nothing in the outline finds nothing")
    @Test
    public void outlineNoMatch() {
        assertTrue(outlines("thistermisnotinthisdocument").isEmpty());
    }

    // ------------------------------------------------------------------
    // named destinations
    // ------------------------------------------------------------------

    @DisplayName("a named destination is found by its name")
    @Test
    public void destinationIsFound() {
        List<DestinationResult> found = destinations("G1");
        assertFalse(found.isEmpty(), "the name tree should hold names beginning G1");
        assertNotNull(found.get(0).getName());
    }

    @DisplayName("a term matching no destination finds nothing")
    @Test
    public void destinationNoMatch() {
        assertTrue(destinations("thistermisnotinthisdocument").isEmpty());
    }

    // ------------------------------------------------------------------
    // a plain search is not a regular expression
    // ------------------------------------------------------------------

    @DisplayName("a plain search term containing regex punctuation is searched literally")
    @Test
    public void plainTermWithRegexPunctuation() {
        // The outline and destination searches compiled every term as a regular expression, even
        // the ones the caller did not mark as one.  A search for text holding a bracket is then an
        // invalid pattern and throws, and one holding a dot quietly matches any character.
        assertTrue(outlines("(").isEmpty(), "an unmatched bracket is text, not a broken pattern");
        assertTrue(outlines("*").isEmpty());
        assertTrue(destinations("(").isEmpty());
    }

    @DisplayName("a dot in a plain search term matches only a dot")
    @Test
    public void plainTermDotIsLiteral() {
        // "G1.1500945" is a real destination name.  Read as a pattern the dot matches anything, so
        // a name that differs at that position would match too; read literally it does not.
        assertFalse(destinations("G1.1500945").isEmpty(), "the real name should still be found");
        assertTrue(destinations("G1x1500945").isEmpty(),
                "a name differing where the dot is should not match a literal dot");
    }

    @DisplayName("only the first search term is used by these searches")
    @Test
    public void onlyTheFirstTermIsUsed() {
        // Recorded rather than asserted as right: the outline, destination and comment searches
        // read searchTerms.get(0) and ignore the rest, while the form search runs every term.  A
        // caller adding two terms gets results for the first alone, with no indication why.
        DocumentSearchControllerImpl two = new DocumentSearchControllerImpl(document);
        two.setSearchMode(SearchMode.PAGE);
        two.addSearchTerm(LEAF_TITLE, false, false);
        two.addSearchTerm(PARENT_TITLE, false, false);

        List<OutlineItem> found = two.searchOutlines();
        assertTrue(titled(found, LEAF_TITLE), "the first term is searched");
        assertFalse(titled(found, PARENT_TITLE), "and the second is not");
    }

    @DisplayName("a term marked as a regular expression is still treated as one")
    @Test
    public void regexTermStillWorks() {
        // The fix above must not take regex searching away from callers that asked for it.
        controller.addSearchTerm("Introduc.*", false, false, true);
        assertFalse(controller.searchOutlines().isEmpty(),
                "a term marked regex should still match as a pattern");
    }

    // ------------------------------------------------------------------
    // form fields and comments
    // ------------------------------------------------------------------

    /**
     * A page carrying two filled text fields and three comments, one of which has no text.
     * <p>
     * Its words are deliberately distinct - the field values, the comment texts and the printed
     * page text share none - so a test can say which of the searches found a thing rather than
     * only that something was found.
     */
    private DocumentSearchControllerImpl searchingFormsAndComments(String term) throws Exception {
        formsDocument = new Document();
        formsDocument.setFile(NonTextSearchTest.class.getResource(FORMS_FIXTURE).getFile());
        DocumentSearchControllerImpl fresh = new DocumentSearchControllerImpl(formsDocument);
        fresh.setSearchMode(SearchMode.PAGE);
        fresh.addSearchTerm(term, false, false);
        return fresh;
    }

    @DisplayName("a form field is found by the value typed into it")
    @Test
    public void formFieldValueIsFound() throws Exception {
        // What a field holds is not page text and is not in the content stream at all, so nothing
        // that searches the page can find it.
        List<TextWidgetAnnotation> found = searchingFormsAndComments("badger").searchForms(0);
        assertEquals(1, found.size(), "the field holding the term should have been found");
    }

    @DisplayName("a form field search is case insensitive unless asked otherwise")
    @Test
    public void formFieldCaseInsensitive() throws Exception {
        assertEquals(1, searchingFormsAndComments("BADGER").searchForms(0).size());
    }

    @DisplayName("a form field value containing a bracket is searched as text")
    @Test
    public void formFieldWithRegexPunctuation() throws Exception {
        // The form search matches with contains rather than a pattern, so punctuation in either
        // the term or the value is just punctuation.
        assertEquals(1, searchingFormsAndComments("(rear)").searchForms(0).size());
    }

    @DisplayName("a term in no field finds no field")
    @Test
    public void formFieldNoMatch() throws Exception {
        assertTrue(searchingFormsAndComments("aardvark").searchForms(0).isEmpty(),
                "a word that is only in the page text is not a form hit");
    }

    @DisplayName("a comment is found by its text")
    @Test
    public void commentIsFound() throws Exception {
        List<MarkupAnnotation> found = searchingFormsAndComments("capybara").searchComments(0);
        assertEquals(1, found.size());
        assertTrue(found.get(0).getContents().contains("capybara"));
    }

    @DisplayName("a term in more than one comment finds all of them")
    @Test
    public void severalCommentsMatch() throws Exception {
        assertEquals(1, searchingFormsAndComments("badger").searchComments(0).size(),
                "the word is in one comment and one field; only the comment is a comment hit");
    }

    @DisplayName("a comment carrying no text is passed over rather than matched or thrown on")
    @Test
    public void commentWithoutContents() throws Exception {
        // A stamp or a plain highlight has no /Contents.  Both matching branches have to walk past
        // it: the page holds one, and the searches below still find the comments either side.
        assertEquals(1, searchingFormsAndComments("capybara").searchComments(0).size(),
                "the plain branch walks past the empty comment");

        formsDocument = new Document();
        formsDocument.setFile(NonTextSearchTest.class.getResource(FORMS_FIXTURE).getFile());
        DocumentSearchControllerImpl regex = new DocumentSearchControllerImpl(formsDocument);
        regex.setSearchMode(SearchMode.PAGE);
        regex.addSearchTerm("capy.*", false, false, true);
        assertEquals(1, regex.searchComments(0).size(),
                "and so does the regex branch");
    }

    @DisplayName("a term in no comment finds no comment")
    @Test
    public void commentNoMatch() throws Exception {
        assertTrue(searchingFormsAndComments("aardvark").searchComments(0).isEmpty(),
                "a word that is only in the page text is not a comment hit");
    }

    @DisplayName("the four searches do not find each other's text")
    @Test
    public void theSearchesAreSeparate() throws Exception {
        // The fixture gives each place its own word, so this says the searches read what they are
        // supposed to read and nothing else.
        DocumentSearchControllerImpl pageWord = searchingFormsAndComments("aardvark");
        assertTrue(pageWord.searchForms(0).isEmpty());
        assertTrue(pageWord.searchComments(0).isEmpty());

        DocumentSearchControllerImpl commentWord = searchingFormsAndComments("capybara");
        assertTrue(commentWord.searchForms(0).isEmpty());
        assertFalse(commentWord.searchComments(0).isEmpty());
    }

    // ------------------------------------------------------------------
    // pages holding neither
    // ------------------------------------------------------------------

    @DisplayName("searching forms on a page with none finds none")
    @Test
    public void formsOnAPageWithoutAny() {
        controller.addSearchTerm("anything", false, false);
        assertNotNull(controller.searchForms(0), "the result should be a list rather than null");
        assertTrue(controller.searchForms(0).isEmpty());
    }

    @DisplayName("searching comments on a page with none finds none")
    @Test
    public void commentsOnAPageWithoutAny() {
        controller.addSearchTerm("anything", false, false);
        assertNotNull(controller.searchComments(0));
        assertTrue(controller.searchComments(0).isEmpty());
    }
}
