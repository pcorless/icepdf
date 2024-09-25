package org.icepdf.core.util.updater.writeables;

import org.icepdf.core.io.CountingOutputStream;
import org.icepdf.core.pobjects.*;
import org.icepdf.core.pobjects.graphics.images.ImageStream;
import org.icepdf.core.pobjects.security.SecurityManager;
import org.icepdf.core.pobjects.structure.CrossReferenceRoot;
import org.icepdf.core.pobjects.structure.Header;
import org.icepdf.core.util.Library;

import java.awt.geom.AffineTransform;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

/**
 * Base writer, responsible for setting up the writers and writing the base pobjects.
 */
public class BaseWriter {

    public static final byte[] SPACE = " ".getBytes();
    public static final byte[] NEWLINE = "\n".getBytes();
    public static final byte[] TRUE = "true".getBytes();
    public static final byte[] FALSE = "false".getBytes();
    public static final byte[] NULL = "null".getBytes();
    protected static final byte[] REFERENCE = "R".getBytes();

    protected static final byte[] BEGIN_OBJECT = "obj\n".getBytes();
    protected static final byte[] END_OBJECT = "endobj\n".getBytes();

    private static HeaderWriter headerWriter;
    private static NameWriter nameWriter;
    private static DictionaryWriter dictionaryWriter;
    private static ReferenceWriter referenceWriter;
    private static HexStringObjectWriter hexStringObjectWriter;
    private static LiteralStringWriter literalObjectWriter;
    private static ArrayWriter arrayWriter;
    private static AffineTransformWriter affineTransformWriter;
    private static PObjectWriter pObjectWriter;
    protected static StreamWriter streamWriter;
    protected static ImageStreamWriter imageStreamWriter;

    private static XRefTableWriter xRefTableWriter;
    private static TrailerWriter trailerWriter;
    private static CompressedXrefTableWriter compressedXrefTableWriter;

    private CountingOutputStream output;
    protected SecurityManager securityManager;
    private CrossReferenceRoot crossReferenceRoot;
    private long startingPosition;
    private ArrayList<Entry> entries;
    private HashMap<Reference, Entry> entriesMap;
    private long xrefPosition;

    public BaseWriter() {

    }

    public BaseWriter(CrossReferenceRoot crossReferenceRoot, SecurityManager securityManager,
                      CountingOutputStream output, long startingPosition) {
        this.output = output;
        this.crossReferenceRoot = crossReferenceRoot;
        this.securityManager = securityManager;
        this.startingPosition = startingPosition;
        entries = new ArrayList<>(512);
        entriesMap = new HashMap<>(512);
    }

    public void initializeWriters() {
        // reuse instances of primitive pdf types.
        streamWriter = new StreamWriter(securityManager);
        imageStreamWriter = new ImageStreamWriter(securityManager);
        headerWriter = new HeaderWriter();
        nameWriter = new NameWriter();
        dictionaryWriter = new DictionaryWriter();
        referenceWriter = new ReferenceWriter();
        hexStringObjectWriter = new HexStringObjectWriter(securityManager);
        literalObjectWriter = new LiteralStringWriter(securityManager);
        arrayWriter = new ArrayWriter();
        affineTransformWriter = new AffineTransformWriter();
        pObjectWriter = new PObjectWriter();
        xRefTableWriter = new XRefTableWriter();
        trailerWriter = new TrailerWriter();
        compressedXrefTableWriter = new CompressedXrefTableWriter();
    }

    public boolean hasNotWrittenReference(Reference reference) {
        return !entriesMap.containsKey(reference);
    }

    public long getBytesWritten() {
        return output.getCount();
    }

    public void writePObject(PObject pobject) throws IOException {
        Entry entry = new Entry(pobject.getReference(), startingPosition + output.getCount());
        entries.add(entry);
        entriesMap.put(pobject.getReference(), entry);
        if (pobject.getObject() instanceof Stream) {
            Stream stream = (Stream) pobject.getObject();
            Reference reference = stream.getPObjectReference();
            if (stream.isDeleted()) {
                entries.add(new Entry(reference)); // empty reference, no bytes needed
                return;
            }
            if (pobject.getObject() instanceof ImageStream) {
                imageStreamWriter.write((ImageStream) pobject.getObject(), securityManager, output);
            } else {
                streamWriter.write((Stream) pobject.getObject(), securityManager, output);
            }
        } else {
            pObjectWriter.write(pobject, output);
        }
    }

    public void writeXRefTable() throws IOException {
        // sort entries by object number
        sortEntries();
        xrefPosition = xRefTableWriter.writeXRefTable(entries, startingPosition, output);
    }

    public void writeIncrementalUpdateTrailer() throws IOException {
        trailerWriter.writeIncrementalUpdateTrailer(crossReferenceRoot, xrefPosition, entries, output);
    }

    public void writeFullTrailer() throws IOException {
        trailerWriter.writeFullTrailer(crossReferenceRoot, xrefPosition, entries, output);
    }

    public void writeIncrementalCompressedXrefTable() throws IOException {
        compressedXrefTableWriter.writeIncrementalCompressedXrefTable(crossReferenceRoot, securityManager, entries,
                startingPosition, output);
    }

    public void writeFullCompressedXrefTable() throws IOException {
        sortEntries();
        compressedXrefTableWriter.writeFullCompressedXrefTable(crossReferenceRoot, securityManager, entries,
                startingPosition, output);
    }

    public void writeNewLine() throws IOException {
        output.write(NEWLINE);
    }

    public void writeHeader(Header header) throws IOException {
        headerWriter.write(header, output);
    }

    public void writeValue(PObject pObject, CountingOutputStream output) throws IOException {
        Object val = pObject.getObject();
        if (val == null) {
            output.write(NULL);
        } else if (val instanceof Name) {
            nameWriter.write((Name) val, output);
        } else if (val instanceof Reference) {
            referenceWriter.write((Reference) val, output);
        } else if (val instanceof Boolean) {
            writeBoolean((Boolean) val, output);
        } else if (val instanceof Integer) {
            writeInteger((Integer) val, output);
        } else if (val instanceof Long) {
            writeLong((Long) val, output);
        } else if (val instanceof Double) {
            writeDouble((Double) val, output);
        } else if (val instanceof Float) {
            writeFloat((Float) val, output);
        } else if (val instanceof Number) {
            writeNumber((Number) val, output);
        } else if (val instanceof String) {
            String value = (String) val;
            // We need to unwrap null as we special case it in the object parser, not ideal
            if (value.equals("null")) {
                output.write(NULL);
            } else {
                literalObjectWriter.write(value, output);
            }

        } else if (val instanceof LiteralStringObject) {
            literalObjectWriter.write(pObject, output);
        } else if (val instanceof HexStringObject) {
            hexStringObjectWriter.write(pObject, output);
        } else if (val instanceof List) {
            arrayWriter.write(pObject, output);
        } else if (val instanceof Dictionary) {
            writeDictionary(pObject, output);
        } else if (val instanceof DictionaryEntries) {
            dictionaryWriter.write(pObject, output);
        } else if (val instanceof AffineTransform) {
            affineTransformWriter.write((AffineTransform) val, output);
        } else {
            throw new IllegalArgumentException("Unknown value:" + val.getClass().getName());
        }
    }

    protected void writeName(Name name, CountingOutputStream output) throws IOException {
        nameWriter.write(name, output);
    }

    protected void writeDictionary(PObject pObject, CountingOutputStream output) throws IOException {
        dictionaryWriter.write(pObject, output);
    }

    protected void writeBoolean(boolean bool, CountingOutputStream output) throws IOException {
        if (bool) {
            output.write(TRUE);
        } else {
            output.write(FALSE);
        }
    }

    protected void writeInteger(int i, CountingOutputStream output) throws IOException {
        String str = Integer.toString(i);
        writeByteString(str, output);
    }

    protected void writeLong(long i, CountingOutputStream output) throws IOException {
        String str = BigDecimal.valueOf(i).toString();
        writeByteString(str, output);
    }

    protected void writeDouble(double r, CountingOutputStream output) throws IOException {
        String str = BigDecimal.valueOf(r).toString();
        writeByteString(str, output);
    }

    protected void writeFloat(float f, CountingOutputStream output) throws IOException {
        String str = BigDecimal.valueOf(f).toString();
        writeByteString(str, output);
    }

    protected void writeNumber(Number n, CountingOutputStream output) throws IOException {
        String str = n.toString();
        writeByteString(str, output);
    }

    protected void writeByteString(String str, CountingOutputStream output) throws IOException {
        int val;
        for (int i = 0, len = str.length(); i < len; i++) {
            val = ((int) str.charAt(i)) & 0xFF;
            output.write(val);
        }
    }

    protected byte[] encryptStream(Stream stream, byte[] outputData) throws IOException {
        // check if we need to encrypt the stream
        if (securityManager != null) {
            DictionaryEntries decodeParams;
            Library library = stream.getLibrary();
            if (stream.getEntries().get(Stream.DECODEPARAM_KEY) != null) {
                // needed to check for a custom crypt filter
                decodeParams = library.getDictionary(stream.getEntries(), Stream.DECODEPARAM_KEY);
            } else {
                decodeParams = new DictionaryEntries();
            }
            InputStream decryptedStream = securityManager.encryptInputStream(stream.getPObjectReference(),
                    securityManager.getDecryptionKey(), decodeParams, new ByteArrayInputStream(outputData), true);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            int nRead;
            byte[] data = new byte[16384];
            while ((nRead = decryptedStream.read(data, 0, data.length)) != -1) {
                out.write(data, 0, nRead);
            }
            return out.toByteArray();
        }
        return null;
    }

    /**
     * Sort entries by object number,  makes the write a little big more efficient.
     */
    protected void sortEntries() {
        entries.sort(new Comparator<Entry>() {
            @Override
            public int compare(Entry entry1, Entry entry2) {
                return Integer.compare(entry1.getReference().getObjectNumber(),
                        entry2.getReference().getObjectNumber());
            }
        });
    }

}
