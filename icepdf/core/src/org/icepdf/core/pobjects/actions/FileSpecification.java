/*
 * Copyright 2006-2014 ICEsoft Technologies Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an "AS
 * IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */
package org.icepdf.core.pobjects.actions;

import org.icepdf.core.pobjects.Dictionary;
import org.icepdf.core.pobjects.Name;
import org.icepdf.core.pobjects.StringObject;
import org.icepdf.core.util.Library;

import java.util.HashMap;

/**
 * <p>The File Specification diction provides more flexibility then the string
 * form.  allowing different files to be specified for different file systems or
 * platforms, or for file system othere than the standard ones (DOS/Windows, Mac
 * OS, and Unix).</p>
 *
 * @author ICEsoft Technologies, Inc.
 * @since 2.6
 */
public class FileSpecification extends Dictionary {

    public static final Name FS_KEY = new Name("FS");
    public static final Name F_KEY = new Name("F");
    public static final Name DOS_KEY = new Name("DOS");
    public static final Name MAC_KEY = new Name("Mac");
    public static final Name UNIX_KEY = new Name("Unix");
    public static final Name ID_KEY = new Name("ID");
    public static final Name V_KEY = new Name("V");
    public static final Name EF_KEY = new Name("EF");
    public static final Name RF_KEY = new Name("RF");
    public static final Name DESC_KEY = new Name("Desc");

    /**
     * Constructs a new specification dictionary.
     *
     * @param l document library.
     * @param h dictionary entries.
     */
    public FileSpecification(Library l, HashMap h) {
        super(l, h);
    }

    /**
     * The type of the PDF object that this dictionary describes which is always
     * "Filespec".
     *
     * @return type of PDF object, "Filespec".
     */
    public Name getType() {
        return library.getName(entries, TYPE_KEY);
    }

    /**
     * Gets the name of the file system to be used to interpret this file
     * specification. This entry is independent of the F, DOS, Mac and Unix
     * entries.
     *
     * @return the name of the file system to be used to interpret this file.
     */
    public Name getFileSystemName() {
        return library.getName(entries, FS_KEY);
    }

    /**
     * Gets the file specification string.
     *
     * @return file specification string.
     */
    public String getFileSpecification() {
        Object tmp = library.getObject(entries, F_KEY);
        if (tmp instanceof StringObject) {
            return ((StringObject) tmp)
                    .getDecryptedLiteralString(
                            library.getSecurityManager());
        } else {
            return null;
        }
    }

    /**
     * Gets the file specification string representing a DOS file name.
     *
     * @return DOS file name.
     */
    public String getDos() {
        Object tmp = library.getObject(entries, DOS_KEY);
        if (tmp instanceof StringObject) {
            return ((StringObject) tmp)
                    .getDecryptedLiteralString(
                            library.getSecurityManager());
        } else {
            return null;
        }
    }

    /**
     * Gets the file specification string representing a Mac file name.
     *
     * @return Mac file name.
     */
    public String getMac() {
        Object tmp = library.getObject(entries, MAC_KEY);
        if (tmp instanceof StringObject) {
            return ((StringObject) tmp)
                    .getDecryptedLiteralString(
                            library.getSecurityManager());
        } else {
            return null;
        }
    }

    /**
     * Gets the file specification string representing a Unix file name.
     *
     * @return Unix file name.
     */
    public String getUnix() {
        Object tmp = library.getObject(entries, UNIX_KEY);
        if (tmp instanceof StringObject) {
            return ((StringObject) tmp)
                    .getDecryptedLiteralString(
                            library.getSecurityManager());
        } else {
            return null;
        }
    }

    /**
     * Gets an array of two strings constituting a file identifier that is also
     * included in the referenced file.
     *
     * @return file identifier.
     */
    public String getId() {
        Object tmp = library.getObject(entries, ID_KEY);
        if (tmp != null) {
            return tmp.toString();
        } else {
            return null;
        }
    }

    /**
     * Returns a flag indicating whether the file referenced by the file
     * specification is volatile (changes frequently with time).
     *
     * @return true indicates the file is volitile and should not be cached,
     *         otherwise true.
     */
    public Boolean isVolitile() {
        return library.getBoolean(entries, V_KEY);
    }

    /**
     * Gets a dictionary containing a subset of the keys F, DOS, Mac, and
     * Unix.  The value of each key is an embedded file stream.
     *
     * @return embbed file stream properties.
     */
    public HashMap getEmbeddedFileDictionary() {
        return library.getDictionary(entries, EF_KEY);
    }

    /**
     * Gets a dictionary with the same structure as the EF dectionary, which
     * must also b present.  EAch key in the RF dictionary must also be present
     * in the EF diciontary.  Each value is a related file array identifying
     * files that a re related to the corresponding file in the EF dictionary.
     *
     * @return related files dictionary.
     */
    public HashMap getRelatedFilesDictionary() {
        return library.getDictionary(entries, RF_KEY);
    }

    /**
     * Gets the descriptive text associated with the file specification.
     *
     * @return file identifier.
     */
    public String getDescription() {
        Object description = library.getObject(entries, DESC_KEY);
        if (description instanceof StringObject) {
            StringObject tmp = (StringObject) description;
            return tmp.getDecryptedLiteralString(library.securityManager);
        } else if (description instanceof String) {
            return description.toString();
        } else {
            return null;
        }
    }
}
