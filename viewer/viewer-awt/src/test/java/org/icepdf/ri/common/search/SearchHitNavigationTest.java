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
import org.icepdf.core.pobjects.graphics.text.WordText;
import org.icepdf.core.search.SearchMode;
import org.icepdf.ri.util.FontPropertiesManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests stepping through the hits a search found.
 * <p>
 * Finding the hits and walking them are separate jobs, and only the first was covered.  The walk is
 * the stateful half: a cursor of page, line and word that has to advance exactly one hit at a time,
 * wrap at the end, and come back the same way in reverse.  Every failure of it looks the same to
 * someone using the viewer - a hit that gets skipped, or a next that lands somewhere already
 * visited - and none of it shows up in a test that only counts what was found.
 * <p>
 * The fixture is a page of Spanish verse that opens with ten occurrences of the same word, which
 * makes the expected order something a test can state outright.
 */
public class SearchHitNavigationTest {

    /** A page whose text repeats one word ten times, in a known order. */
    private static final String FIXTURE = "/redact/test_print.pdf";
    private static final String TERM = "Un";
    private static final int EXPECTED_HITS = 10;

    private Document document;
    private DocumentSearchControllerImpl controller;

    @BeforeAll
    public static void init() {
        FontPropertiesManager.getInstance().loadOrReadSystemFonts();
    }

    @BeforeEach
    public void setUp() throws Exception {
        document = new Document();
        document.setFile(SearchHitNavigationTest.class.getResource(FIXTURE).getFile());
        controller = new DocumentSearchControllerImpl(document);
        controller.setSearchMode(SearchMode.WORD);
    }

    @AfterEach
    public void tearDown() {
        if (document != null) {
            document.dispose();
        }
    }

    /**
     * Runs the search and returns how many hits page 0 holds.
     */
    private int search() {
        controller.addSearchTerm(TERM, false, false);
        return controller.searchHighlightPage(0);
    }

    /**
     * Steps forward {@code count} times and returns what each step landed on.
     */
    private List<String> walkForward(int count) {
        List<String> visited = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            WordText word = controller.nextSearchHit();
            visited.add(word == null ? null : word.getText());
        }
        return visited;
    }

    // ------------------------------------------------------------------
    // the search itself, as the ground the rest stands on
    // ------------------------------------------------------------------

    @DisplayName("the fixture holds the hits the navigation tests assume")
    @Test
    public void theSearchFindsItsHits() {
        // Stated on its own so a change to the fixture or to matching fails here, rather than
        // making every navigation test below fail for a reason that has nothing to do with
        // navigation.
        assertEquals(EXPECTED_HITS, search());
    }

    // ------------------------------------------------------------------
    // stepping forward
    // ------------------------------------------------------------------

    @DisplayName("a search run without a viewer can still be stepped through")
    @Test
    public void navigationWorksHeadless() {
        // The Document constructor exists for headless use and the search half supports it, so the
        // walk has to as well; it used to find every hit and then throw on the first step.
        search();
        assertNotNull(controller.nextSearchHit(), "the first hit should be reachable headless");
    }

    @DisplayName("each step forward lands on a hit")
    @Test
    public void everyStepLandsOnAHit() {
        search();
        for (String visited : walkForward(EXPECTED_HITS)) {
            assertNotNull(visited, "a step should not run out of hits before the last one");
            // the search is a substring match, so a hit need not begin with the term -
            // "vagabundo" contains "un" and is a legitimate hit
            assertTrue(visited.toLowerCase().contains(TERM.toLowerCase()),
                    "a step landed on " + visited + ", which does not contain the term");
        }
    }

    @DisplayName("stepping forward visits every hit once before repeating any")
    @Test
    public void forwardVisitsEachHitOnce() {
        // The cursor advances by one word, so an off-by-one either skips a hit or stays put; both
        // show up here as a walk of ten steps that does not cover ten distinct positions.
        search();
        List<WordText> visited = new ArrayList<>();
        for (int i = 0; i < EXPECTED_HITS; i++) {
            visited.add(controller.nextSearchHit());
        }
        for (int i = 0; i < visited.size(); i++) {
            for (int j = i + 1; j < visited.size(); j++) {
                assertTrue(visited.get(i) != visited.get(j),
                        "hit " + i + " and hit " + j + " are the same word");
            }
        }
    }

    @DisplayName("stepping past the last hit wraps to the first")
    @Test
    public void forwardWrapsAtTheEnd() {
        search();
        WordText first = controller.nextSearchHit();
        for (int i = 1; i < EXPECTED_HITS; i++) {
            controller.nextSearchHit();
        }
        assertEquals(first, controller.nextSearchHit(),
                "the step after the last hit should be the first hit again");
    }

    // ------------------------------------------------------------------
    // stepping back
    // ------------------------------------------------------------------

    @DisplayName("a search can be stepped backwards too")
    @Test
    public void backwardsNavigation() {
        search();
        assertNotNull(controller.previousSearchHit());
    }

    @DisplayName("stepping back undoes a step forward")
    @Test
    public void backAfterForwardReturns() {
        // The cursor is stored on the hit rather than just past it, so changing direction moves by
        // one hit like every other step.  It used to be left past the hit, which made the first
        // press of Previous land on the hit just visited and so appear to do nothing.
        search();
        controller.nextSearchHit();
        WordText second = controller.nextSearchHit();
        WordText third = controller.nextSearchHit();
        assertNotNull(third);

        assertEquals(second, controller.previousSearchHit(),
                "stepping back from the third hit should land on the second");
    }

    @DisplayName("changing direction repeatedly walks one hit at a time")
    @Test
    public void directionCanBeChangedRepeatedly() {
        // The cursor has to mean the same thing whichever way it was last moved, or the two
        // buttons disagree about where the user is as soon as they are alternated.
        search();
        WordText first = controller.nextSearchHit();
        WordText second = controller.nextSearchHit();

        assertEquals(first, controller.previousSearchHit(), "back to the first");
        assertEquals(second, controller.nextSearchHit(), "forward to the second again");
        assertEquals(first, controller.previousSearchHit(), "and back to the first again");
    }

    @DisplayName("a hit spanning several words is one stop, not one per word")
    @Test
    public void aMultipleWordHitIsASingleStop() {
        // A phrase match highlights a run of consecutive words.  Stepping forward has to pass the
        // whole run: stepping a single word would read the run's second word as a hit of its own,
        // which is the trap in storing the cursor on the hit rather than past it.
        DocumentSearchControllerImpl phrase = new DocumentSearchControllerImpl(document);
        phrase.setSearchMode(SearchMode.PAGE);
        phrase.addSearchTerm("Un vagabundo", false, false);
        int hits = phrase.searchHighlightPage(0);
        assertEquals(1, hits, "the fixture should hold the phrase once");

        WordText firstStop = phrase.nextSearchHit();
        assertNotNull(firstStop);
        // the only hit on the page, so stepping again has to wrap back to it rather than land on
        // the second word of the same run
        assertEquals(firstStop, phrase.nextSearchHit(),
                "a run of words should be one stop, so the next step wraps back to it");
    }

    @DisplayName("stepping back visits every hit once before repeating any")
    @Test
    public void backwardVisitsEachHitOnce() {
        search();
        List<WordText> visited = new ArrayList<>();
        for (int i = 0; i < EXPECTED_HITS; i++) {
            visited.add(controller.previousSearchHit());
        }
        for (WordText word : visited) {
            assertNotNull(word);
        }
        for (int i = 0; i < visited.size(); i++) {
            for (int j = i + 1; j < visited.size(); j++) {
                assertTrue(visited.get(i) != visited.get(j),
                        "hit " + i + " and hit " + j + " are the same word");
            }
        }
    }

    @DisplayName("stepping back past the first hit wraps to the last")
    @Test
    public void backwardWrapsAtTheStart() {
        search();
        WordText firstBack = controller.previousSearchHit();
        for (int i = 1; i < EXPECTED_HITS; i++) {
            controller.previousSearchHit();
        }
        assertEquals(firstBack, controller.previousSearchHit(),
                "the step before the first hit should be the last hit again");
    }

    @DisplayName("forwards and backwards walk the same hits in opposite orders")
    @Test
    public void forwardAndBackwardAgree() {
        // The strongest statement the two can make together: whatever order forward produces,
        // backward from the same start has to produce its reverse.
        search();
        List<WordText> forward = new ArrayList<>();
        for (int i = 0; i < EXPECTED_HITS; i++) {
            forward.add(controller.nextSearchHit());
        }

        // the cursor is back at the start after a full lap, so walk it the other way
        List<WordText> backward = new ArrayList<>();
        for (int i = 0; i < EXPECTED_HITS; i++) {
            backward.add(controller.previousSearchHit());
        }

        assertEquals(EXPECTED_HITS, forward.size());
        assertEquals(EXPECTED_HITS, backward.size());
        for (WordText word : backward) {
            assertTrue(forward.contains(word),
                    "stepping back found " + word.getText() + ", which stepping forward never did");
        }
    }

    // ------------------------------------------------------------------
    // nothing to walk
    // ------------------------------------------------------------------

    @DisplayName("a search that found nothing has nothing to step to")
    @Test
    public void noHitsToNavigate() {
        // Both directions have to answer rather than run off the end of an empty result, and this
        // is the case that says the wrap above cannot spin forever on an empty search.
        controller.addSearchTerm("thistermisnotinthedocument", false, false);
        assertEquals(0, controller.searchHighlightPage(0));
        assertNull(controller.nextSearchHit());
        assertNull(controller.previousSearchHit());
    }

    @DisplayName("navigating before any search has been run does nothing")
    @Test
    public void navigationBeforeSearching() {
        assertNull(controller.nextSearchHit());
        assertNull(controller.previousSearchHit());
    }

    @DisplayName("clearing the results stops the navigation finding anything")
    @Test
    public void navigationAfterClearing() {
        search();
        assertNotNull(controller.nextSearchHit());

        controller.clearSearchHighlight(0);
        assertNull(controller.nextSearchHit(),
                "a cleared search should have no hits left to step through");
    }

    // ------------------------------------------------------------------
    // where the cursor is left
    // ------------------------------------------------------------------

    @DisplayName("setting the current page moves the cursor to it")
    @Test
    public void setCurrentPageMovesTheCursor() {
        // This is what the viewer calls when the user scrolls: the next step should carry on from
        // the page being looked at rather than from wherever the last step left off.
        search();
        controller.nextSearchHit();
        controller.setCurrentPage(0);
        assertNotNull(controller.nextSearchHit(),
                "the walk should resume from the page that was set");
    }
}
