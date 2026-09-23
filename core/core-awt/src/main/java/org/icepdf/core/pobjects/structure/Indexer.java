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
        indexObjectStreams(byteBuffer, parser, crossReference);

        // Without a /Root there is no way into the document, so find the object that holds the
        // catalog and point at it.  This is what lets a file with no trailer at all be opened.
        if (xRefDictionary.get(PTrailer.ROOT_KEY) == null) {
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
     * catalog turns up is paid on damaged files alone.
     *
     * @param byteBuffer     whole file
     * @param parser         parser to read each object with
     * @param crossReference table of the objects found by scanning
     * @return reference to the catalog, or null if no object in the file is one
     */
    private Reference findCatalog(ByteBuffer byteBuffer, Parser parser, CrossReferenceTable crossReference) {
        for (Map.Entry<Reference, CrossReferenceEntry> entry : crossReference.getEntries().entrySet()) {
            if (!(entry.getValue() instanceof CrossReferenceUsedEntry)) {
                continue;
            }
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