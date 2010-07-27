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
 * 2004-2010 ICEsoft Technologies Canada, Corp. All Rights Reserved.
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
package org.icepdf.core.pobjects;

import org.icepdf.core.pobjects.fonts.ofont.Encoding;
import org.icepdf.core.pobjects.security.SecurityManager;

import java.util.Hashtable;

/**
 * <p>This class represents the data stored in a File trailers optional "info"
 * entry.</p>
 * <p/>
 * <p>Any entry whose value is not known should be omitted from the dictionary,
 * rather than included with an empty string as its value.</p>
 * <p/>
 * <p>Some plug-in extensions may choose to permit searches on the contents of the
 * document information dictionary. To facilitate browsing and editing, all keys
 * in the dictionary are fully spelled out, not abbreviated. New keys should be
 * chosen with care so that they make sense to users.</p>
 *
 * @since 1.1
 */
public class PInfo {

    // attributes for this core object
    private Hashtable attributes = null;

    // security manager need for decrypting strings.
    private SecurityManager securityManager;

    /**
     * Create a new instance of a <code>PInfo</code> object.
     *
     * @param attributes info attributes.
     */
    public PInfo(SecurityManager securityManager, Hashtable attributes) {
        this.attributes = attributes;
        this.securityManager = securityManager;
    }

    /**
     * Gets the value of the custom extension specified by <code>name</code>.
     *
     * @param name som plug-in extensions name.
     * @return value of the plug-in extension.
     */
    public Object getCustomExtension(String name) {
        return attributes.get(name);
    }

    /**
     * Gets the title of the document.
     *
     * @return the documents title.
     */
    public String getTitle() {
        Object tmp = attributes.get("Title");
        if (tmp != null && tmp instanceof StringObject) {
            StringObject text = (StringObject) tmp;
            return cleanString(text.getDecryptedLiteralString(securityManager));
        } else {
            return "";
        }
    }

    /**
     * Gets the name of the person who created the document.
     *
     * @return author name.
     */
    public String getAuthor() {
        Object tmp = attributes.get("Author");
        if (tmp != null && tmp instanceof StringObject) {
            StringObject text = (StringObject) tmp;
            return cleanString(text.getDecryptedLiteralString(securityManager));
        } else {
            return "";
        }
    }

    /**
     * Gets the subject of the document.
     *
     * @return documents subject.
     */
    public String getSubject() {
        Object tmp = attributes.get("Subject");
        if (tmp != null && tmp instanceof StringObject) {
            StringObject text = (StringObject) tmp;
            return cleanString(text.getDecryptedLiteralString(securityManager));
        } else {
            return "";
        }
    }

    /**
     * Gets the keywords associated with the document.
     *
     * @return documents keywords.
     */
    public String getKeywords() {
        Object tmp = attributes.get("Keywords");
        if (tmp != null && tmp instanceof StringObject) {
            StringObject text = (StringObject) tmp;
            return cleanString(text.getDecryptedLiteralString(securityManager));
        } else {
            return "";
        }
    }

    /**
     * Gets the name of the application. If the PDF document was converted from
     * another format that <b>created</b> the original document.
     *
     * @return creator name.
     */
    public String getCreator() {
        Object tmp = attributes.get("Creator");
        if (tmp != null && tmp instanceof StringObject) {
            StringObject text = (StringObject) tmp;
            return cleanString(text.getDecryptedLiteralString(securityManager));
        } else {
            return "";
        }
    }

    /**
     * Gets the name of the application. If the PDF document was converted from
     * another format that <b>converted</b> the original document.
     *
     * @return producer name.
     */
    public String getProducer() {
        Object tmp = attributes.get("Producer");
        if (tmp != null && tmp instanceof StringObject) {
            StringObject text = (StringObject) tmp;
            return cleanString(text.getDecryptedLiteralString(securityManager));
        } else {
            return "";
        }
    }

    /**
     * Gets the date and time the document was created.
     *
     * @return creation date.
     */
    public PDate getCreationDate() {
        Object tmp = attributes.get("CreationDate");
        if (tmp != null && tmp instanceof StringObject) {
            StringObject text = (StringObject) tmp;
            return new PDate(securityManager, text.getDecryptedLiteralString(securityManager));
        }
        return null;
    }

    /**
     * Gets the date and time the document was most recently modified.
     *
     * @return modification date.
     */
    public PDate getModDate() {
        Object tmp = attributes.get("ModDate");
        if (tmp != null && tmp instanceof StringObject) {
            StringObject text = (StringObject) tmp;
            return new PDate(securityManager, text.getDecryptedLiteralString(securityManager));
        }
        return null;
    }

    /**
     * Get the name object indicating whether the document has been modified to
     * include trapping information:
     * <ul>
     * <li><b>False</b> - The document has not yet been trapped; any desired
     * trapping must still be done.</li>
     * <li><b>Unknown</b> - (default) Either it is unknown whether the document has
     * been trapped or it has been partly but not yet fully
     * trapped; some additional trapping may still be needed.</li>
     * </ul>
     *
     * @return trapped name.
     */
    public String getTrappingInformation() {
        Object tmp = attributes.get("Trapped");
        if (tmp != null && tmp instanceof StringObject) {
            StringObject text = (StringObject) tmp;
            return cleanString(text.getDecryptedLiteralString(securityManager));
        } else {
            return "";
        }
    }

    /**
     * Utility method for removing extra characters associated with 4 byte
     * characters codes.
     */
    private String cleanString(String text) {
        if (text != null && text.length() > 0) {
            if (((int) text.charAt(0)) == 254 && ((int) text.charAt(1)) == 255) {
                StringBuilder sb1 = new StringBuilder();

                // strip and white space, as the will offset the below algorithm
                // which assumes the string is made up of two byte chars.
                String hexTmp = "";
                for (int i = 0; i < text.length(); i++) {
                    char c = text.charAt(i);
                    if (!((c == '\t') || (c == '\r') || (c == '\n'))) {
                        hexTmp = hexTmp + text.charAt(i);
                    }
                }
                byte title1[] = hexTmp.getBytes();

                for (int i = 2; i < title1.length; i += 2) {
                    try {
                        int b1 = ((int) title1[i]) & 0xFF;
                        int b2 = ((int) title1[i + 1]) & 0xFF;
                        sb1.append((char) (b1 * 256 + b2));
                    } catch (Exception ex) {
                        // intentionally left empty
                    }
                }
                text = sb1.toString();
            } else {
                StringBuilder sb = new StringBuilder();
                Encoding enc = Encoding.getPDFDoc();
                for (int i = 0; i < text.length(); i++) {
                    sb.append(enc.get(text.charAt(i)));
                }
                text = sb.toString();
            }
            return text;
        } else {
            return "";
        }
    }

}
