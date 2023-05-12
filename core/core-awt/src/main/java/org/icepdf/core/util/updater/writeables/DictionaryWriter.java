package org.icepdf.core.util.updater.writeables;

import org.icepdf.core.io.CountingOutputStream;
import org.icepdf.core.pobjects.Dictionary;
import org.icepdf.core.pobjects.DictionaryEntries;
import org.icepdf.core.pobjects.Name;

import java.io.IOException;
import java.util.Set;

public class DictionaryWriter extends BaseWriter {

    private static final byte[] BEGIN_DICTIONARY = "<<".getBytes();
    private static final byte[] END_DICTIONARY = ">>".getBytes();


    public void write(Dictionary dictionary, CountingOutputStream output) throws IOException {
        DictionaryEntries dictEntries = dictionary.getEntries();
        write(dictEntries, output);
    }

    public void write(DictionaryEntries dictEntries, CountingOutputStream output) throws IOException {
        output.write(BEGIN_DICTIONARY);
        Set<Name> keys = dictEntries.keySet();
        for (Name key : keys) {
            Object val = dictEntries.get(key);
            writeName(key, output);
            output.write(SPACE);
            writeValue(val, output);
            output.write(SPACE);
        }
        output.write(END_DICTIONARY);
    }
}
