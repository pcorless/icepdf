package org.icepdf.core.util.parser.object;


import org.icepdf.core.pobjects.*;
import org.icepdf.core.pobjects.structure.CrossReference;
import org.icepdf.core.pobjects.structure.CrossReferenceStream;
import org.icepdf.core.pobjects.structure.CrossReferenceTable;
import org.icepdf.core.pobjects.structure.CrossReferenceUsedEntry;
import org.icepdf.core.pobjects.structure.exceptions.CrossReferenceStateException;
import org.icepdf.core.pobjects.structure.exceptions.ObjectStateException;
import org.icepdf.core.util.ByteBufferUtil;
import org.icepdf.core.util.Library;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 *
 */
public class Parser {

    // legacy xref markers.
    //                                              x    r    e    f
    public static final byte[] XREF_MARKER = new byte[]{120, 114, 101, 102};
    //                                                       t    r    a    i   l    e    r
    public static final byte[] TRAILER_MARKER = new byte[]{116, 114, 97, 105, 108, 101, 114};

    // object markers                                      o    b   j
    public static final byte[] OBJ_MARKER = new byte[]{32, 111, 98, 106};
    //                                                      e    n    d    o    b   j
    public static final byte[] END_OBJ_MARKER = new byte[]{101, 110, 100, 111, 98, 106};

    //                                                         e    n    d    s    t    r    e    a   m
    public static final byte[] END_STREAM_MARKER = new byte[]{101, 110, 100, 115, 116, 114, 101, 97, 109};

    private final Library library;

    public Parser(Library library) {
        this.library = library;
    }

    public PObject getPObject(ByteBuffer byteBuffer, int objectOffsetStart)
            throws IOException, ObjectStateException {

        int objectOffsetEnd;
        int streamOffsetStart;
        ByteBuffer streamByteBuffer;
        byteBuffer.position(objectOffsetStart);

        // grab the pieces of the object
        Lexer lexer = new Lexer(library);
        lexer.setByteBuffer(byteBuffer);
        // number
        Object token = lexer.nextToken();
        int objectNumber;
        if (token instanceof Integer) {
            objectNumber = (Integer) token;
        } else {
            token = lexer.nextToken();
            if (token instanceof Integer) {
                objectNumber = (Integer) token;
            } else {
                throw new ObjectStateException();
            }
        }
        // generation
        token = lexer.nextToken();
        int objectGeneration;
        if (token instanceof Integer) {
            objectGeneration = (Integer) token;
        } else {
            throw new ObjectStateException();
        }
        // int = 1 obj
        Object objectOp = lexer.nextToken();
        if (!(objectOp instanceof Integer && ((Integer) objectOp) == OperandNames.OP_obj)) {
            throw new ObjectStateException();
        }
        // dictionary or single value
        Object objectData = lexer.nextToken(new Reference(objectNumber, objectGeneration));
        // stream or endobj
        Object streamOrEndObj = lexer.nextToken();
        if (streamOrEndObj instanceof Integer && ((Integer) streamOrEndObj) == OperandNames.OP_stream) {
            lexer.skipWhiteSpace();
            // stream offset
            streamOffsetStart = byteBuffer.position();
            int streamLength = library.getInt((DictionaryEntries) objectData, Dictionary.LENGTH_KEY);
            // create a new buffer to encapsulate the stream data using the length
            streamByteBuffer = ByteBufferUtil.sliceObjectStream(
                    byteBuffer,
                    streamOffsetStart,
                    streamOffsetStart + streamLength);

            // double-check a streamLength = zero, some encoders are lazy and there is actually data.
            if (streamLength == 0) {
                // scan ahead to find the end obj position
                byteBuffer.position(objectOffsetStart);
                boolean foundEndObjMarker = ByteBufferUtil.findString(byteBuffer, END_OBJ_MARKER);
                if (foundEndObjMarker) {
                    objectOffsetEnd = byteBuffer.position() - END_OBJ_MARKER.length;
                    int lookBackLength = objectOffsetEnd - (END_STREAM_MARKER.length + 10);
                    objectOffsetEnd = ByteBufferUtil.findReverseString(
                            byteBuffer,
                            objectOffsetEnd,
                            lookBackLength,
                            END_STREAM_MARKER);
                } else {
                    // corner case were there is no endobj but xref delimits last object.
                    objectOffsetEnd = byteBuffer.position() - XREF_MARKER.length;
                }

                // copy the bytes to a new buffer, so we can work on the bytes without thread position issues.
                streamByteBuffer = ByteBufferUtil.sliceObjectStream(byteBuffer, streamOffsetStart, objectOffsetEnd);
                streamLength = streamByteBuffer.limit();
                // sometimes there is just garbage too.  If there is no filter assume so.
                if (streamLength < 10 && library.getName((DictionaryEntries) objectData, Stream.FILTER_KEY) == null) {
                    streamLength = 0;
                                    streamByteBuffer.position(0);
                    streamByteBuffer.limit(streamLength);
                    streamByteBuffer = streamByteBuffer.slice();
                }
            }
        } else {
            streamByteBuffer = null;
        }
        // push dictionary through factory to build correct instance.
        return ObjectFactory.getInstance(library, objectNumber, objectGeneration, objectData, streamByteBuffer);
    }

    public PObject getCompressedObject(ByteBuffer streamObjectByteBuffer, int objectNumber,
                                       int objectOffsetStart) throws IOException {
        // grab the pieces of the object
        streamObjectByteBuffer.position(objectOffsetStart);
        Lexer lexer = new Lexer(library);
        lexer.setByteBuffer(streamObjectByteBuffer);
        // dictionary or single value
        Object objectData = lexer.nextToken(null);
        // push dictionary through factory to build correct instance.
        return ObjectFactory.getInstance(library, objectNumber, 0, objectData, null);
    }

    public CrossReference getCrossReference(ByteBuffer byteBuffer, int starXref)
            throws CrossReferenceStateException, ObjectStateException, IOException {
        // sometimes the offset is off just by a few bytes
        byteBuffer.position(starXref - 10);
        int xrefPositionStart = byteBuffer.position();

        // make sure we have a xref declaration
        int bytesLeft = Math.min(byteBuffer.limit() - byteBuffer.position(), 48);
        byteBuffer.limit(byteBuffer.position() + bytesLeft);
        ByteBuffer lookAheadBuffer = byteBuffer.slice();
        byteBuffer.limit(byteBuffer.capacity());

        boolean foundXrefMarker = ByteBufferUtil.findString(lookAheadBuffer, XREF_MARKER);
        Lexer objectLexer = new Lexer(library);

        // see if we found xref marking and thus an < 1.5 formatted xref table.
        if (foundXrefMarker) {
            // update the xref position as we will have removed any white space.
            starXref = xrefPositionStart + lookAheadBuffer.position();
            // scan ahead to find the trailer position
            byteBuffer.position(starXref);
            boolean foundTrailerMarker = ByteBufferUtil.findString(byteBuffer, TRAILER_MARKER);
            if (!foundTrailerMarker || byteBuffer.position() == byteBuffer.limit()) {
                throw new CrossReferenceStateException();
            }
            // parse the dictionary using our lexer, so we can look for a /hrefstm entry.
            int startTrailer = byteBuffer.position() - TRAILER_MARKER.length;
            objectLexer.setByteBuffer(byteBuffer);
            Object token = objectLexer.nextToken();
            if (token instanceof DictionaryEntries) {
                DictionaryEntries xrefDictionary = (DictionaryEntries) token;
                return parseCrossReferenceTable(xrefDictionary, objectLexer, byteBuffer, starXref, startTrailer);
            }
        }
        // if there is an entry we can ignore parsing the table as it's redundant and just parse the strmObject
        return parseCrossReferenceStream(byteBuffer, starXref);
    }

    private CrossReference parseCrossReferenceTable(DictionaryEntries dictionaryEntries, Lexer objectLexer, ByteBuffer byteBuffer,
                                                    int start, int end) throws IOException {
        // mark the xref start, so it can be used to write future /prev entries.
        // allocate to a new buffer as the data is well-defined.
        ByteBuffer xrefTableBuffer = ByteBufferUtil.sliceObjectStream(byteBuffer, start, end);
        CrossReferenceTable crossReferenceTable = new CrossReferenceTable(library, dictionaryEntries, start);
        objectLexer.setByteBuffer(xrefTableBuffer);
        // parse the sub groupings
        while (true) {
            Integer startObjectNumber = (Integer) objectLexer.nextToken();
            // buffer end will result in a null token
            if (startObjectNumber == null) break;
            int numberOfObjects = (Integer) objectLexer.nextToken();
            int currentNumber = startObjectNumber;
            for (int i = 0; i < numberOfObjects; i++) {
                int offset = (Integer) objectLexer.nextToken();
                int generation = (Integer) objectLexer.nextToken();
                int state = (Integer) objectLexer.nextToken();
                if (state == OperandNames.OP_n) {
                    crossReferenceTable.addEntry(
                            new CrossReferenceUsedEntry(currentNumber, generation, offset));
                } else if (state == OperandNames.OP_f) {    // Free
                    // check for the first entry 0000000000 65535 f  and
                    // an object range where the first entry isn't zero.  The
                    // code below will treat the first entry as zero and then
                    // start counting.
                    if (i == 0 && startObjectNumber > 0 && generation == 65535) {
                        // offset the count, so we start counting after the zeroed entry
                        currentNumber--;
                    }
                }
                currentNumber++;
            }
        }

        return crossReferenceTable;
    }

    private CrossReference parseCrossReferenceStream(ByteBuffer byteBuffer, int offset)
            throws IOException, ObjectStateException {
        // use parser to get xref stream object.
        CrossReferenceStream crossReferenceStream = (CrossReferenceStream) getPObject(byteBuffer, offset).getObject();
        crossReferenceStream.initialize();
        crossReferenceStream.setXrefStartPos(offset);
        return crossReferenceStream;
    }
}