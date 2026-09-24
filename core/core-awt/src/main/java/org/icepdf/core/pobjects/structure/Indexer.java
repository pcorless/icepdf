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
package org.icepdf.core.pobjects.structure;

import org.icepdf.core.pobjects.Catalog;
import org.icepdf.core.pobjects.DictionaryEntries;
import org.icepdf.core.pobjects.ObjectStream;
import org.icepdf.core.pobjects.PObject;
import org.icepdf.core.pobjects.PTrailer;
import org.icepdf.core.pobjects.Reference;
import org.icepdf.core.pobjects.structure.exceptions.CrossReferenceStateException;
import org.icepdf.core.pobjects.structure.exceptions.ObjectStateException;
import org.icepdf.core.util.ByteBufferUtil;
import org.icepdf.core.util.Library;
import org.icepdf.core.util.parser.object.Lexer;
import org.icepdf.core.util.parser.object.Parser;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Reindex the file,  should be called anytime we have a lookup error.
 */
    public class Indexer {

    private static final Logger logger =
            Logger.getLogger(Indexer.class.getName());

    private static final byte[] OBJECT_STREAM_MARKER = "/ObjStm".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] STREAM_MARKER = "stream".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] PAGE_MARKER = "/Page".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] CATALOG_MARKER = "/Catalog".getBytes(StandardCharsets.US_ASCII);

    private final Library library;

    public Indexer(Library library) {
        this.library = library;
    }

    public CrossReferenceRoot indexObjects(ByteBuffer byteBuffer) throws IOException, CrossReferenceStateException {
        // reset the cross-reference store
        CrossReferenceRoot crossReferenceRoot = new CrossReferenceRoot(library);

        Lexer lexer = new Lexer(library);
        Parser parser = new Parser(library);

        // Recover the trailer dictionary if the file still has one.  It may not: a truncated file
        // loses the trailer before it loses anything else, being the last thing written.  That used
        // to end the repair here, which meant the commonest damage of all - a file cut short - was
        // the one case that could not be rebuilt, even though every object in it was still intact
        // and findable.  The objects are indexed either way now, and a missing trailer is
        // reconstructed from them afterwards (see findCatalog).
        DictionaryEntries xRefDictionary = findTrailerDictionary(byteBuffer, lexer, parser, crossReferenceRoot);
        int trailerPosition = xRefDictionary != null ? byteBuffer.position() : byteBuffer.limit();
        if (xRefDictionary == null) {
            xRefDictionary = new DictionaryEntries();
        } else {
            // The scan below covers the whole file, so the rebuilt table must not fall back to an older
            // cross-reference section: at best it holds stale offsets, and a truncated file's /Prev (or a
            // linearized file's first-page trailer) points past the end, failing every lookup the scan misses.
            DictionaryEntries copy = new DictionaryEntries();
            copy.putAll(xRefDictionary);
            copy.remove(PTrailer.PREV_KEY);
            xRefDictionary = copy;
        }

        // The table is handed the dictionary before it is complete; the entries are added below,
        // and a missing /Root is filled in once there are entries to find it among.
        CrossReferenceTable crossReference = new CrossReferenceTable(library, xRefDictionary, trailerPosition);
        crossReferenceRoot.addCrossReference(crossReference);

        // Scan backwards for every "objectNumber generation obj".  Working from the end means the
        // first entry found for an object is the most recent incremental update of it, which is the
        // one that should win.
        int pos = byteBuffer.limit();
        while (pos > 0) {
            int objectStart = ByteBufferUtil.findReverseString(byteBuffer, pos, 0, Parser.OBJ_MARKER);
            if (objectStart == pos || objectStart <= 0) {
                break;
            }
            int generation = ByteBufferUtil.findReverseNexNumber(byteBuffer);
            int objectNumber = ByteBufferUtil.findReverseNexNumber(byteBuffer);
            if (crossReference.getEntryNoDescendents(new Reference(objectNumber, generation)) == null) {
                crossReference.addEntry(
                        new CrossReferenceUsedEntry(objectNumber, generation, byteBuffer.position() + 1));
            } else {
                logger.fine("Not inserting " + objectNumber + " already present in file.");
            }
            pos = objectStart;
        }

        // Objects packed into object streams have no "obj" keyword of their own, so the scan above can't see
        // them; a linearized or compressed file keeps its page tree there, and losing it leaves no pages.
        // An encrypted file's object streams can only be read once the security handler exists, which on
        // opening is after this rebuild; the document runs the pass then (see indexDeferredObjectStreams).
        if (xRefDictionary.get(PTrailer.ENCRYPT_KEY) != null && library.getSecurityManager() == null) {
            crossReferenceRoot.setDeferredObjectStreams(crossReference);
        } else {
            indexObjectStreams(byteBuffer, parser, crossReference);
        }

        // Without a /Root there is no way into the document, so find the object that holds the
        // catalog and point at it.  This is what lets a file with no trailer at all be opened.
        if (xRefDictionary.get(PTrailer.ROOT_KEY) == null) {
            // Publish the rebuilt table first.  Reading candidates resolves references (an indirect stream
            // /Length, the object stream holding a compressed object), and through the table being replaced
            // those resolve to the wrong offsets or fail outright.
            library.setCrossReferenceRoot(crossReferenceRoot);
            Reference catalog = findCatalog(byteBuffer, parser, crossReference);
            if (catalog != null) {
                logger.fine("Rebuilt a missing trailer, catalog found at " + catalog);
                xRefDictionary.put(PTrailer.ROOT_KEY, catalog);
            } else {
                logger.warning("Could not find a document catalog while rebuilding the file index.");
            }
        }

        return crossReferenceRoot;
    }

    /**
     * Looks for the file's trailer dictionary, or the cross-reference stream that stands in for one.
     *
     * @param byteBuffer         whole file
     * @param lexer              lexer to read the dictionary with
     * @param parser             parser for a cross-reference stream
     * @param crossReferenceRoot root to add a recovered cross-reference stream to
     * @return the dictionary, or null when the file has neither
     */
    private DictionaryEntries findTrailerDictionary(ByteBuffer byteBuffer, Lexer lexer, Parser parser,
                                                    CrossReferenceRoot crossReferenceRoot) throws IOException {
        int trailerPosition = ByteBufferUtil.findReverseString(byteBuffer, byteBuffer.limit(), 0,
                Parser.TRAILER_MARKER);
        if (trailerPosition != byteBuffer.limit()) {
            byteBuffer.position(trailerPosition + Parser.TRAILER_MARKER.length);
            lexer.setByteBuffer(byteBuffer);
            Object object = lexer.nextToken();
            if (object instanceof DictionaryEntries) {
                DictionaryEntries xRefDictionary = (DictionaryEntries) object;
                // on the long shot that the cross-reference stream it names is valid, take it too
                if (xRefDictionary.containsKey(PTrailer.XREF_STRM_KEY)) {
                    int offset = library.getInt(xRefDictionary, PTrailer.XREF_STRM_KEY);
                    try {
                        crossReferenceRoot.addCrossReference(
                                (CrossReferenceStream) parser.getCrossReference(byteBuffer, offset));
                    } catch (CrossReferenceStateException | ObjectStateException e) {
                        logger.finer("Failed to get cross reference for offset: " + offset);
                    }
                }
                byteBuffer.position(trailerPosition);
                return xRefDictionary;
            }
        }

        // no trailer keyword, so the file may carry a cross-reference stream instead
        int xRefPosition = ByteBufferUtil.findReverseString(byteBuffer, byteBuffer.limit(),
                Math.max(0, byteBuffer.limit() - 1024), Parser.XREF_MARKER);
        try {
            CrossReferenceStream crossReferenceStream =
                    (CrossReferenceStream) parser.getCrossReference(byteBuffer, xRefPosition);
            byteBuffer.position(xRefPosition);
            return crossReferenceStream.getDictionaryEntries();
        } catch (Exception e) {
            // Neither form survived.  This used to throw, taking the whole document with it; the
            // objects are still there to be indexed, so the repair carries on without it.
            logger.finer("No trailer or cross-reference stream found, rebuilding from objects alone.");
            return null;
        }
    }

    /**
     * Runs the object stream pass that {@link #indexObjects} put off for an encrypted file, now that the security
     * handler can decrypt the streams.  Does nothing if no pass is pending.
     *
     * @param crossReferenceRoot rebuilt cross-reference
     * @param byteBuffer         whole file
     */
    public void indexDeferredObjectStreams(CrossReferenceRoot crossReferenceRoot, ByteBuffer byteBuffer) {
        CrossReferenceTable crossReference = crossReferenceRoot.getDeferredObjectStreams();
        if (crossReference == null) {
            return;
        }
        crossReferenceRoot.setDeferredObjectStreams(null);
        indexObjectStreams(byteBuffer, new Parser(library), crossReference);
    }

    /**
     * Adds a compressed entry for every object held in an object stream found by the scan.  An object that was
     * found as a plain object is left alone, and when several object streams hold the same object the one latest
     * in the file wins, as the most recent incremental update.
     *
     * @param byteBuffer     whole file
     * @param parser         parser to read each object stream with
     * @param crossReference table of the objects found by scanning
     */
    private void indexObjectStreams(ByteBuffer byteBuffer, Parser parser, CrossReferenceTable crossReference) {
        List<CrossReferenceUsedEntry> objectStreams = new ArrayList<>();
        for (CrossReferenceEntry entry : crossReference.getEntries().values()) {
            if (entry instanceof CrossReferenceUsedEntry &&
                    isObjectStream(byteBuffer, ((CrossReferenceUsedEntry) entry).getFilePositionOfObject())) {
                objectStreams.add((CrossReferenceUsedEntry) entry);
            }
        }
        objectStreams.sort(Comparator.comparingInt(CrossReferenceUsedEntry::getFilePositionOfObject).reversed());
        for (CrossReferenceUsedEntry entry : objectStreams) {
            try {
                PObject pObject = parser.getPObject(byteBuffer, entry.getFilePositionOfObject());
                if (pObject == null || !(pObject.getObject() instanceof ObjectStream)) {
                    continue;
                }
                int[] objectNumbers = ((ObjectStream) pObject.getObject()).getObjectNumbers();
                for (int i = 0; i < objectNumbers.length; i++) {
                    if (crossReference.getEntryNoDescendents(new Reference(objectNumbers[i], 0)) == null) {
                        crossReference.addEntry(new CrossReferenceCompressedEntry(objectNumbers[i],
                                pObject.getReference().getObjectNumber(), i));
                    }
                }
            } catch (Exception e) {
                // one damaged object stream shouldn't stop the others being indexed
                logger.finer("Skipping unreadable object stream at offset " + entry.getFilePositionOfObject());
            }
        }
    }

    /**
     * Lists the objects that may be pages, in file order, for a document whose page tree can't be reached - a
     * truncated linearized file loses its page tree first, as it is written at the end.  Plain objects are
     * narrowed to those whose dictionary names /Page; objects inside object streams can't be peeked at, so they
     * are all listed after the plain ones and the caller keeps only those that load as a page.
     *
     * @param crossReferenceRoot cross-reference of the document
     * @param byteBuffer         whole file
     * @return candidate page references, in file order
     */
    public static List<Reference> findPageCandidates(CrossReferenceRoot crossReferenceRoot, ByteBuffer byteBuffer) {
        Map<Reference, CrossReferenceEntry> entries = new HashMap<>();
        for (CrossReference crossReference : crossReferenceRoot.getCrossReferences()) {
            crossReference.getEntries().forEach(entries::putIfAbsent);
        }
        List<Map.Entry<Reference, CrossReferenceEntry>> plain = new ArrayList<>();
        List<Map.Entry<Reference, CrossReferenceEntry>> compressed = new ArrayList<>();
        for (Map.Entry<Reference, CrossReferenceEntry> entry : entries.entrySet()) {
            if (entry.getValue() instanceof CrossReferenceUsedEntry) {
                if (isPageObject(byteBuffer, ((CrossReferenceUsedEntry) entry.getValue()).getFilePositionOfObject())) {
                    plain.add(entry);
                }
            } else if (entry.getValue() instanceof CrossReferenceCompressedEntry) {
                compressed.add(entry);
            }
        }
        plain.sort(Comparator.comparingInt(e -> ((CrossReferenceUsedEntry) e.getValue()).getFilePositionOfObject()));
        compressed.sort(Comparator.comparingInt((Map.Entry<Reference, CrossReferenceEntry> e) ->
                        ((CrossReferenceCompressedEntry) e.getValue()).getObjectNumberOfContainingObjectStream()
                                .getObjectNumber())
                .thenComparingInt(e -> ((CrossReferenceCompressedEntry) e.getValue()).getIndexWithinObjectStream()));
        List<Reference> candidates = new ArrayList<>(plain.size() + compressed.size());
        plain.forEach(e -> candidates.add(e.getKey()));
        compressed.forEach(e -> candidates.add(e.getKey()));
        return candidates;
    }

    /**
     * Cheap pre-check for {@link #findPageCandidates}: looks for the name /Page (not /Pages or /PageLabels) in the
     * object's dictionary, before its stream or endobj keyword.
     */
    private static boolean isPageObject(ByteBuffer byteBuffer, int offset) {
        return namesInDictionary(byteBuffer, offset, PAGE_MARKER);
    }

    /**
     * Cheap pre-check for {@link #findCatalog}, as {@link #isPageObject} is for pages.
     */
    private static boolean isCatalogObject(ByteBuffer byteBuffer, int offset) {
        return namesInDictionary(byteBuffer, offset, CATALOG_MARKER);
    }

    /**
     * Looks for a whole name (so /Page does not match /Pages) in the first 1KB of an object, stopping at its stream
     * or endobj keyword.
     */
    private static boolean namesInDictionary(ByteBuffer byteBuffer, int offset, byte[] name) {
        int end = Math.min(byteBuffer.limit(), offset + 1024);
        for (int i = Math.max(offset, 0); i < end; i++) {
            if (matches(byteBuffer, i, end, STREAM_MARKER) || matches(byteBuffer, i, end, Parser.END_OBJ_MARKER)) {
                return false;
            }
            if (matches(byteBuffer, i, end, name)) {
                int next = i + name.length;
                if (next >= end || !Character.isLetterOrDigit((char) byteBuffer.get(next))) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Cheap pre-check, so only object streams are parsed: looks for /ObjStm in the object's dictionary, before
     * its stream keyword.
     */
    private static boolean isObjectStream(ByteBuffer byteBuffer, int offset) {
        int end = Math.min(byteBuffer.limit(), offset + 1024);
        for (int i = Math.max(offset, 0); i < end; i++) {
            if (matches(byteBuffer, i, end, STREAM_MARKER)) {
                return false;
            }
            if (matches(byteBuffer, i, end, OBJECT_STREAM_MARKER)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matches(ByteBuffer byteBuffer, int position, int end, byte[] marker) {
        if (position + marker.length > end) {
            return false;
        }
        for (int i = 0; i < marker.length; i++) {
            if (byteBuffer.get(position + i) != marker[i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * Finds the object holding the document catalog, by reading the objects just indexed.
     * <p>
     * Only used when the file has no usable trailer, so the cost of parsing objects until the
     * catalog turns up is paid on damaged files alone.  Candidates are tried cheapest and likeliest first: plain
     * objects that name /Catalog near their start, then objects inside object streams (where a compressed file
     * usually keeps its catalog), then every other plain object, in case /Type sits deep in a large catalog.
     * Within each group the latest in the file is tried first, as the most recent incremental update.  The
     * rebuilt table must already be the library's, as objects in object streams are read through it.
     *
     * @param byteBuffer     whole file
     * @param parser         parser to read each object with
     * @param crossReference table of the objects found by scanning
     * @return reference to the catalog, or null if no object in the file is one
     */
    private Reference findCatalog(ByteBuffer byteBuffer, Parser parser, CrossReferenceTable crossReference) {
        List<Map.Entry<Reference, CrossReferenceEntry>> named = new ArrayList<>();
        List<Map.Entry<Reference, CrossReferenceEntry>> unnamed = new ArrayList<>();
        List<Map.Entry<Reference, CrossReferenceEntry>> compressed = new ArrayList<>();
        for (Map.Entry<Reference, CrossReferenceEntry> entry : crossReference.getEntries().entrySet()) {
            if (entry.getValue() instanceof CrossReferenceUsedEntry) {
                int offset = ((CrossReferenceUsedEntry) entry.getValue()).getFilePositionOfObject();
                (isCatalogObject(byteBuffer, offset) ? named : unnamed).add(entry);
            } else if (entry.getValue() instanceof CrossReferenceCompressedEntry) {
                compressed.add(entry);
            }
        }
        Comparator<Map.Entry<Reference, CrossReferenceEntry>> latestPlainFirst = Comparator.comparingInt(
                (Map.Entry<Reference, CrossReferenceEntry> e) ->
                        ((CrossReferenceUsedEntry) e.getValue()).getFilePositionOfObject()).reversed();
        named.sort(latestPlainFirst);
        unnamed.sort(latestPlainFirst);
        compressed.sort(Comparator.comparingInt((Map.Entry<Reference, CrossReferenceEntry> e) ->
                ((CrossReferenceCompressedEntry) e.getValue()).getObjectNumberOfContainingObjectStream()
                        .getObjectNumber()).reversed());

        Reference catalog = findCatalog(byteBuffer, parser, named);
        if (catalog == null) {
            for (Map.Entry<Reference, CrossReferenceEntry> entry : compressed) {
                try {
                    if (library.getObject(entry.getKey()) instanceof Catalog) {
                        return entry.getKey();
                    }
                } catch (Exception e) {
                    logger.finer("Skipping unreadable object while looking for the catalog: " + entry.getKey());
                }
            }
            catalog = findCatalog(byteBuffer, parser, unnamed);
        }
        return catalog;
    }

    private static Reference findCatalog(ByteBuffer byteBuffer, Parser parser,
                                         List<Map.Entry<Reference, CrossReferenceEntry>> candidates) {
        for (Map.Entry<Reference, CrossReferenceEntry> entry : candidates) {
            int offset = ((CrossReferenceUsedEntry) entry.getValue()).getFilePositionOfObject();
            try {
                PObject pObject = parser.getPObject(byteBuffer, offset);
                if (pObject != null && pObject.getObject() instanceof Catalog) {
                    return entry.getKey();
                }
            } catch (Exception e) {
                // a damaged object among the rest is expected here; keep looking
                logger.finer("Skipping unreadable object while looking for the catalog: " + entry.getKey());
            }
        }
        return null;
    }
}