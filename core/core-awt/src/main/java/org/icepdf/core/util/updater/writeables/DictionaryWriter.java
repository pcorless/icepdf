package org.icepdf.core.util.updater.writeables;

import org.icepdf.core.io.CountingOutputStream;
import org.icepdf.core.pobjects.Dictionary;
import org.icepdf.core.pobjects.Name;

import java.io.IOException;
import java.util.HashMap;
import java.util.Set;

public class DictionaryWriter extends BaseWriter {

    private static final byte[] BEGIN_DICTIONARY = "<<".getBytes();
    private static final byte[] END_DICTIONARY = ">>".getBytes();


    public void write(Dictionary dictionary, CountingOutputStream output) throws IOException {
        HashMap<Object, Object> dictEntries = dictionary.getEntries();
        write(dictEntries, output);
    }

    public void write(HashMap<Object, Object> dictEntries, CountingOutputStream output) throws IOException {
        output.write(BEGIN_DICTIONARY);
        Set<Object> keys = dictEntries.keySet();
        for (Object key : keys) {
            Object val = dictEntries.get(key);
            writeName((Name) key, output);
            output.write(SPACE);
            writeValue(val, output);
            output.write(SPACE);
        }
        output.write(END_DICTIONARY);
    }
}
