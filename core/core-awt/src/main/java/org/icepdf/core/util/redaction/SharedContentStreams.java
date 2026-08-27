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
import org.icepdf.core.pobjects.Name;
import org.icepdf.core.pobjects.Page;
import org.icepdf.core.pobjects.PageTree;
import org.icepdf.core.pobjects.Reference;
import org.icepdf.core.util.Library;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Which pages share a content stream, and whether that matters to a redaction.
 * <p>
 * Two pages can point at one {@code /Contents} stream. Burning a redaction into it changes the page
 * it was drawn on and every other page drawing the same stream - the redaction propagates. That is
 * the same behaviour a shared image or form has, and for the same reason it is usually right: pages
 * sharing a stream draw the same content, and the same content is the same disclosure.
 * <p>
 * It stops being right when the pages do not in fact draw that stream the same way. The operators
 * are shared but their meaning is not: a different {@code /Resources} points the same operators at
 * different fonts and images, and a different {@code /Rotate} or page box puts the result somewhere
 * else, so a rectangle covering a name on one page covers something else on the other. Sharing can
 * also be partial - one page's {@code /Contents} is {@code [a b]} and another's is {@code [a c]} -
 * where only some of what is drawn is common.
 * <p>
 * None of that is silently corrected. The redaction propagates either way, erring towards removing
 * too much, and this reports the cases where the caller should know it did.
 *
 * @since 7.5.0
 */
class SharedContentStreams {

    private static final Name CONTENTS_KEY = new Name("Contents");
    private static final Name RESOURCES_KEY = new Name("Resources");
    private static final Name ROTATE_KEY = new Name("Rotate");
    private static final Name MEDIA_BOX_KEY = new Name("MediaBox");
    private static final Name CROP_BOX_KEY = new Name("CropBox");

    private final Library library;
    /** Content stream reference to the pages drawing it, in page order. */
    private final Map<Reference, List<Integer>> pagesByStream = new LinkedHashMap<>();
    private final Map<Integer, List<Reference>> streamsByPage = new LinkedHashMap<>();

    SharedContentStreams(Document document) {
        PageTree pageTree = document.getPageTree();
        this.library = document.getCatalog().getLibrary();
        for (int index = 0, max = pageTree.getNumberOfPages(); index < max; index++) {
            Page page = pageTree.getPage(index);
            if (page == null) {
                continue;
            }
            List<Reference> streams = contentStreamReferences(page);
            streamsByPage.put(index, streams);
            for (Reference stream : streams) {
                pagesByStream.computeIfAbsent(stream, key -> new ArrayList<>()).add(index);
            }
        }
    }

    /**
     * Reports any stream this page shares with another page that does not draw it the same way.
     * <p>
     * Read straight from the page dictionaries: working this out has to be possible for pages the
     * redaction never touches, and initialising every page of a document to find out would cost more
     * than the redaction itself.
     *
     * @param pageIndex page about to be burned
     * @param report    collects a warning per stream worth reporting
     */
    void reportSharing(int pageIndex, RedactionReport report) {
        List<Reference> streams = streamsByPage.get(pageIndex);
        if (streams == null) {
            return;
        }
        for (Reference stream : streams) {
            List<Integer> pages = pagesByStream.get(stream);
            if (pages == null || pages.size() < 2) {
                continue;
            }
            Set<String> reasons = new LinkedHashSet<>();
            for (int other : pages) {
                if (other != pageIndex) {
                    reasons.addAll(differences(pageIndex, other));
                }
            }
            if (!reasons.isEmpty()) {
                report.warn(RedactionWarning.Kind.SHARED_OBJECT_BURNED_IN_PLACE,
                        "Content stream " + stream + " is shared by pages " + describe(pages)
                                + ", which do not draw it alike (" + String.join(", ", reasons)
                                + "); the redaction applies to all of them");
            }
        }
    }

    /**
     * What makes two pages sharing a stream draw it differently. Empty when they draw it alike, in
     * which case the redaction propagating to both is removing the same content twice over.
     */
    private Set<String> differences(int pageIndex, int otherIndex) {
        Set<String> reasons = new LinkedHashSet<>();
        Page page = library.getCatalog().getPageTree().getPage(pageIndex);
        Page other = library.getCatalog().getPageTree().getPage(otherIndex);
        if (page == null || other == null) {
            return reasons;
        }
        if (!sameEntry(page, other, RESOURCES_KEY)) {
            reasons.add("different /Resources, so the same operators draw different things");
        }
        if (!sameEntry(page, other, ROTATE_KEY)) {
            reasons.add("different /Rotate");
        }
        if (!sameEntry(page, other, MEDIA_BOX_KEY) || !sameEntry(page, other, CROP_BOX_KEY)) {
            reasons.add("different page box, so a redaction covers a different area");
        }
        if (!streamsByPage.get(pageIndex).equals(streamsByPage.get(otherIndex))) {
            reasons.add("only part of their content is shared");
        }
        return reasons;
    }

    /**
     * Compares by reference where the entry is one, and by value otherwise, so two pages naming the
     * same resources object are alike whether they name it directly or by reference.
     */
    private boolean sameEntry(Page page, Page other, Name key) {
        Reference pageReference = library.getObjectReference(page.getEntries(), key);
        Reference otherReference = library.getObjectReference(other.getEntries(), key);
        if (pageReference != null || otherReference != null) {
            return pageReference != null && pageReference.equals(otherReference);
        }
        Object pageValue = library.getObject(page.getEntries(), key);
        Object otherValue = library.getObject(other.getEntries(), key);
        return pageValue == null ? otherValue == null : pageValue.equals(otherValue);
    }

    private List<Reference> contentStreamReferences(Page page) {
        List<Reference> references = new ArrayList<>(1);
        Reference single = library.getObjectReference(page.getEntries(), CONTENTS_KEY);
        if (single != null) {
            references.add(single);
            return references;
        }
        Object contents = library.getObject(page.getEntries(), CONTENTS_KEY);
        if (contents instanceof List) {
            for (Object entry : (List<?>) contents) {
                if (entry instanceof Reference) {
                    references.add((Reference) entry);
                }
            }
        }
        return references;
    }

    private static String describe(List<Integer> pages) {
        StringBuilder out = new StringBuilder();
        for (int page : pages) {
            if (out.length() > 0) {
                out.append(", ");
            }
            // one-based, which is how a reader refers to a page
            out.append(page + 1);
        }
        return out.toString();
    }
}
