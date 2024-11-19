package org.icepdf.core.util.updater.writeables;

import org.icepdf.core.io.CountingOutputStream;
import org.icepdf.core.pobjects.Dictionary;
import org.icepdf.core.pobjects.DictionaryEntries;
import org.icepdf.core.pobjects.Name;
import org.icepdf.core.pobjects.PObject;

import java.io.IOException;
import java.util.Set;

public class DictionaryWriter extends BaseWriter {

    private static final byte[] BEGIN_DICTIONARY = "<<".getBytes();
    private static final byte[] END_DICTIONARY = ">>".getBytes();

    public void write(PObject pObject, CountingOutputStream output) throws IOException {
        if (pObject.getObject() instanceof Dictionary) {
            Dictionary dictionary = (Dictionary) pObject.getObject();
            DictionaryEntries dictEntries = dictionary.getEntries();
            write(dictEntries, pObject, output);
        } else {
            DictionaryEntries dictEntries = (DictionaryEntries) pObject.getObject();
            write(dictEntries, pObject, output);
        }
    }

    public void write(DictionaryEntries dictEntries, PObject pObject, CountingOutputStream output) throws IOException {
        output.write(BEGIN_DICTIONARY);
        Set<Name> keys = dictEntries.keySet();
        for (Name key : keys) {
            Object val = dictEntries.get(key);
            writeName(key, output);
            output.write(SPACE);
            writeValue(new PObject(val, pObject.getReference(), pObject.isDoNotEncrypt()), output);
            output.write(SPACE);
        }
        output.write(END_DICTIONARY);
    }

    public void writeInline(DictionaryEntries dictEntries, CountingOutputStream output) throws IOException {
        Set<Name> keys = dictEntries.keySet();
        for (Name key : keys) {
            Object val = dictEntries.get(key);
            writeName(key, output);
            output.write(SPACE);
            writeValue(new PObject(val, null, true), output);
            output.write(NEWLINE);
        }
    }
}
