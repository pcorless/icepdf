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
package org.icepdf.core.util.redaction;

import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.Page;
import org.icepdf.core.pobjects.graphics.text.WordText;
import org.icepdf.core.util.updater.WriteMode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the write-mode contract for redaction.
 * <p>
 * Burning is implemented in FullUpdater; by its nature an incremental update can only append, so it
 * cannot remove redacted content and IncrementalUpdater does no redaction handling. Saving
 * incrementally with redactions still pending is therefore legitimate - it saves unburned markup as
 * work in progress - and is left to the caller, warned rather than overridden. Promoting the write
 * would rewrite every object and invalidate signatures the incremental path preserves.
 * <p>
 * What must hold is that the constraint is detectable before the caller commits to a write mode.
 */
public class RedactionWriteModeTest {

    private static final String FIXTURE = "src/test/resources/updater/annotation_popup.pdf";

    /** The first word of six or more letters on page 0 of the fixture, asserted when it is found. */
    private static final String TARGET_TERM = "DEFAULT";

    @DisplayName("a full update burns the redaction out of the content stream")
    @Test
    public void fullUpdateRemovesRedactedText() throws Exception {
        byte[] saved = redactFirstLongWordAndSave(WriteMode.FULL_UPDATE);
        assertFalse(RedactionFixtures.extractedText(saved).contains(TARGET_TERM),
                "FULL_UPDATE should have burned '" + TARGET_TERM + "' out of the stream");
    }

    /**
     * Not the desired end state for a user who believes they have redacted, but it is the honest
     * contract: an incremental update cannot remove content. Pinned so that any future change to the
     * write path is a deliberate one, and paired with
     * {@link #pendingRedactionsAreDetectableBeforeWriting()}, which is what lets a caller avoid it.
     */
    @DisplayName("an incremental update leaves the redaction unburned")
    @Test
    public void incrementalUpdateLeavesRedactedTextInPlace() throws Exception {
        byte[] saved = redactFirstLongWordAndSave(null);   // null = one-arg saveToOutputStream
        assertTrue(RedactionFixtures.extractedText(saved).contains(TARGET_TERM),
                "an incremental update cannot remove content, so '" + TARGET_TERM + "' remains");
    }

    @DisplayName("pending redactions are detectable before choosing a write mode")
    @Test
    public void pendingRedactionsAreDetectableBeforeWriting() throws Exception {
        Document document = new Document();
        document.setFile(FIXTURE);
        try {
            assertFalse(document.hasRedactions(), "fixture should start with no redactions");
            addRedactionOverFirstLongWord(document);
            assertTrue(document.hasRedactions(), "a pending redaction must be reported");
        } finally {
            document.dispose();
        }
    }

    /**
     * The reopened case is the one that matters, and it is a different code path: with an empty
     * StateManager, {@code hasRedactions} falls through to scanning the page tree. A document saved
     * with a single unburned redaction and reopened is exactly the state the viewer's
     * unburned-redaction warning has to recognise.
     */
    @DisplayName("a single unburned redaction is still detectable after a reopen")
    @Test
    public void singleRedactionIsDetectableAfterReopen() throws Exception {
        byte[] saved = redactFirstLongWordAndSave(null);   // incremental: redaction stays unburned

        Document reopened = new Document();
        reopened.setInputStream(new ByteArrayInputStream(saved), "redacted");
        try {
            assertTrue(reopened.getCatalog().getLibrary().getStateManager().isNoChange(),
                    "a freshly reopened document should have no pending state changes, so " +
                            "hasRedactions must fall through to the page scan");
            assertTrue(reopened.hasRedactions(),
                    "one unburned redaction on a page must be reported");
        } finally {
            reopened.dispose();
        }
    }

    // -- helpers ---------------------------------------------------------------------------------

    /**
     * Opens the fixture, drops a redaction annotation over the first long word on page 0, saves with
     * the given write mode (null selects the one-arg convenience overload).
     */
    private byte[] redactFirstLongWordAndSave(WriteMode writeMode) throws Exception {
        Document document = new Document();
        document.setFile(FIXTURE);
        try {
            addRedactionOverFirstLongWord(document);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            if (writeMode == null) {
                document.saveToOutputStream(out);
            } else {
                document.saveToOutputStream(out, writeMode);
            }
            return out.toByteArray();
        } finally {
            document.dispose();
        }
    }

    /**
     * Drops a redaction annotation over the first word of six or more letters on page 0.
     *
     * @param document document to add the annotation to
     */
    private void addRedactionOverFirstLongWord(Document document) throws Exception {
        Page page = document.getPageTree().getPage(0);
        page.init();
        WordText target = RedactionFixtures.firstLongWord(page, 6);
        assertEquals(TARGET_TERM, target.getText().trim(),
                "fixture's first long word changed; the assertions below name it explicitly");
        page.addAnnotation(RedactionFixtures.redactionOver(document,
                target.getBounds().getBounds()), true);
    }

}
