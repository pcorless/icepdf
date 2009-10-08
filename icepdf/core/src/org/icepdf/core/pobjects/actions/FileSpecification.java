/*
 * Version: MPL 1.1/GPL 2.0/LGPL 2.1
 *
 * "The contents of this file are subject to the Mozilla Public License
 * Version 1.1 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
 * License for the specific language governing rights and limitations under
 * the License.
 *
 * The Original Code is ICEpdf 3.0 open source software code, released
 * May 1st, 2009. The Initial Developer of the Original Code is ICEsoft
 * Technologies Canada, Corp. Portions created by ICEsoft are Copyright (C)
 * 2004-2009 ICEsoft Technologies Canada, Corp. All Rights Reserved.
 *
 * Contributor(s): _____________________.
 *
 * Alternatively, the contents of this file may be used under the terms of
 * the GNU Lesser General Public License Version 2.1 or later (the "LGPL"
 * License), in which case the provisions of the LGPL License are
 * applicable instead of those above. If you wish to allow use of your
 * version of this file only under the terms of the LGPL License and not to
 * allow others to use your version of this file under the MPL, indicate
 * your decision by deleting the provisions above and replace them with
 * the notice and other provisions required by the LGPL License. If you do
 * not delete the provisions above, a recipient may use your version of
 * this file under either the MPL or the LGPL License."
 *
 */
package org.icepdf.core.pobjects.actions;

import org.icepdf.core.pobjects.Dictionary;
import org.icepdf.core.pobjects.StringObject;
import org.icepdf.core.util.Library;

import java.util.Hashtable;

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

    /**
     * Constructs a new specification dictionary.
     *
     * @param l document library.
     * @param h dictionary entries.
     */
    public FileSpecification(Library l, Hashtable h) {
        super(l, h);
    }

    /**
     * The type of the PDF object that this dictionary describes which is always
     * "Filespec".
     *
     * @return type of PDF object, "Filespec".
     */
    public String getType() {
        return library.getName(entries, "Type");
    }

    /**
     * Gets the name of the file system to be used to interpret this file
     * specification. This entry is independent of the F, DOS, Mac and Unix
     * entries.
     *
     * @return the name of the file system to be used to interpret this file.
     */
    public String getFileSystemName() {
        return library.getName(entries, "FS");
    }

    /**
     * Gets the file specification string.
     *
     * @return file specification string.
     */
    public String getFileSpecification() {
        if (library.getObject(entries, "F") instanceof StringObject) {
            return ((StringObject) library.getObject(entries, "F"))
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
        if (library.getObject(entries, "DOS") instanceof StringObject) {
            return ((StringObject) library.getObject(entries, "DOS"))
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
        if (library.getObject(entries, "Mac") instanceof StringObject) {
            return ((StringObject) library.getObject(entries, "Mac"))
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
        if (library.getObject(entries, "Unix") instanceof StringObject) {
            return ((StringObject) library.getObject(entries, "Unix"))
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
        if (library.getObject(entries, "ID") != null) {
            return library.getObject(entries, "ID").toString();
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
        return library.getBoolean(entries, "V");
    }

    /**
     * Gets a dictionary containing a subset of the keys F, DOS, Mac, and
     * Unix.  The value of each key is an embedded file stream.
     *
     * @return embbed file stream properties.
     */
    public Hashtable getEmbeddedFileDictionary() {
        return library.getDictionary(entries, "EF");
    }

    /**
     * Gets a dictionary with the same structure as the EF dectionary, which
     * must also b present.  EAch key in the RF dictionary must also be present
     * in the EF diciontary.  Each value is a related file array identifying
     * files that a re related to the corresponding file in the EF dictionary.
     *
     * @return related files dictionary.
     */
    public Hashtable getRelatedFilesDictionary() {
        return library.getDictionary(entries, "RF");
    }

    /**
     * Gets the descriptive text associated with the file specification.
     *
     * @return file identifier.
     */
    public String getDescription() {
        Object description = library.getObject(entries, "Desc");
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
