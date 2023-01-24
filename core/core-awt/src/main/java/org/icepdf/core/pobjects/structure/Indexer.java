package org.icepdf.core.pobjects.structure;

import org.icepdf.core.pobjects.DictionaryEntries;
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
import java.util.logging.Logger;

/**
 * Reindex the file,  should be called anytime we have a lookup error.
 */
public class Indexer {

    private static final Logger logger =
            Logger.getLogger(Indexer.class.toString());

    private Library library;

    public Indexer(Library library) {
        this.library = library;
    }

    public CrossReferenceRoot indexObjects(ByteBuffer byteBuffer) throws IOException, CrossReferenceStateException, ObjectStateException {
        // reset the cross-reference store
        CrossReferenceRoot crossReferenceRoot = new CrossReferenceRoot(library);
        CrossReferenceTable crossReference = null;

        // start looking for indirect objects pattern\byteBuffer
        byteBuffer.position(byteBuffer.limit());

        // we are looking mainly for 'obj' and the main xref dictionary.  So if we work backwards through the
        // file we should pick up the trailer dictionary first followed by the objects.   This should give us enough
        // to randomly access the file again.
        int pos = byteBuffer.limit();
        DictionaryEntries xRefDictionary = null;
        Lexer lexer = new Lexer(library);
        while (pos > 0) {
            if (xRefDictionary == null) {
                int end = 0;
                int trailerPosition = pos = ByteBufferUtil.findReverseString(byteBuffer, byteBuffer.limit(), end, Parser.TRAILER_MARKER);
                // look for a trailer first.
                if (trailerPosition != byteBuffer.limit()) {
                    int xrefStartPos = trailerPosition;
                    byteBuffer.position(trailerPosition + Parser.TRAILER_MARKER.length);
                    lexer.setByteBuffer(byteBuffer);
                    Object object = lexer.nextToken();
                    if (object instanceof DictionaryEntries) {
                        xRefDictionary = (DictionaryEntries) object;
                        // check for a /xref key,  just on the long shot that it's valid.
                        if (xRefDictionary.containsKey(PTrailer.XREF_STRM_KEY)) {
                            CrossReferenceStream crossReferenceStream;
                            int offset = library.getInt(xRefDictionary, PTrailer.XREF_STRM_KEY);
                            Parser parser = new Parser(library);
                            try {
                                crossReferenceStream = (CrossReferenceStream) parser.getCrossReference(byteBuffer, offset);
                                crossReferenceRoot.addCrossReference(crossReferenceStream);
                            } catch (CrossReferenceStateException | ObjectStateException e) {
                                logger.finer("Failed to get cross reference for offset: " + offset);
                            }
                        }
                        // fall back indexing file.
                        crossReference = new CrossReferenceTable(library, xRefDictionary, xrefStartPos);
                        crossReferenceRoot.addCrossReference(crossReference);
                        // move position to search for object from the end
                        pos = byteBuffer.limit();
                    }
                }
                // otherwise have a compressed cross-reference so find /XRef and find first << position
                else {
                    pos = byteBuffer.position();
                    end = pos > 1024 ? 1024 : pos;
                    int xRefPosition = ByteBufferUtil.findReverseString(byteBuffer, pos, end, Parser.XREF_MARKER);
                    // we don't haven an examples of this at this time, will make one.
                    Parser parser = new Parser(library);
                    try {
                        CrossReferenceStream crossReferenceStream =
                                (CrossReferenceStream) parser.getCrossReference(byteBuffer, xRefPosition);
                        xRefDictionary = crossReferenceStream.getDictionaryEntries();
                    } catch (ObjectStateException e) {
                        logger.finer("Failed to get cross reference for offset: " + xRefPosition);
                    }
                }

            } else {
                // looking for obj, float, float,  record position.
                int objectStart = ByteBufferUtil.findReverseString(byteBuffer, pos, 0, Parser.OBJ_MARKER);
                if (objectStart == pos) {
                    break;
                }
                if (objectStart > 0) {
                    // we should be able to pase out the generation and object number at this point and calculate offset.
                    int generation = ByteBufferUtil.findReverseNexNumber(byteBuffer);
                    int objectNumber = ByteBufferUtil.findReverseNexNumber(byteBuffer);
                    if (crossReference != null) {
                        // check if the object is already present,  were ready from the back of the file so object
                        // added first is likely the most resnet incremental update and should be overridden
                        if (crossReference.getEntry(new Reference(objectNumber, generation)) == null) {
                            crossReference.addEntry(
                                    new CrossReferenceUsedEntry(objectNumber, generation, byteBuffer.position() + 1));
                        } else {
                            logger.fine("Not inserting " + objectNumber + " already present in file.");
                        }
                    }
                }
                pos = objectStart;
            }
        }
        // add entries to the cross reference.
        return crossReferenceRoot;
    }

}