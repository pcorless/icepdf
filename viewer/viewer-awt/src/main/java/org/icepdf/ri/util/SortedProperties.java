package org.icepdf.ri.util;


import java.util.Enumeration;
import java.util.Properties;
import java.util.Vector;
import java.util.stream.Collectors;

/**
 * Properties that are enumerated in a deterministic and sorted way
 */
public class SortedProperties extends Properties {

    public SortedProperties(Properties props) {
        super(props);
    }

    public SortedProperties() {
        super();
    }

    @Override
    public Enumeration<Object> keys() {
        Enumeration<Object> keysEnum = super.keys();
        Vector<Object> keyList = new Vector<>();
        while (keysEnum.hasMoreElements()) {
            keyList.add(keysEnum.nextElement());
        }
        keyList = keyList.stream().map(o -> (String) o).sorted().collect(Collectors.toCollection(Vector::new));
        return keyList.elements();
    }
}