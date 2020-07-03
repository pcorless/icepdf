/*
 * Copyright 2006-2019 ICEsoft Technologies Canada Corp.
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
package org.icepdf.core.pobjects;

import org.icepdf.core.pobjects.security.SecurityManager;
import org.icepdf.core.util.Library;
import org.icepdf.core.util.Utils;

import java.util.HashMap;

/**
 * <p>This class represents the data stored in a File trailers optional "info"
 * entry.</p>
 * <p>Any entry whose value is not known should be omitted from the dictionary,
 * rather than included with an empty string as its value.</p>
 * <p>Some plug-in extensions may choose to permit searches on the contents of the
 * document information dictionary. To facilitate browsing and editing, all keys
 * in the dictionary are fully spelled out, not abbreviated. New keys should be
 * chosen with care so that they make sense to users.</p>
 *
 * @since 1.1
 */
public class PInfo extends Dictionary {

    public static final Name RESOURCES_KEY = new Name("Resources");
    public static final Name TITLE_KEY = new Name("Title");
    public static final Name AUTHOR_KEY = new Name("Author");
    public static final Name SUBJECT_KEY = new Name("Subject");
    public static final Name KEYWORDS_KEY = new Name("Keywords");
    public static final Name CREATOR_KEY = new Name("Creator");
    public static final Name PRODUCER_KEY = new Name("Producer");
    public static final Name CREATIONDATE_KEY = new Name("CreationDate");
    public static final Name MODDATE_KEY = new Name("ModDate");
    public static final Name TRAPPED_KEY = new Name("Trapped");

    // security manager need for decrypting strings.
    private SecurityManager securityManager;

    /**
     * Create a new instance of a <code>PInfo</code> object.
     *
     * @param library document library
     * @param entries entries for this object dictionary.
     */
    public PInfo(Library library, HashMap entries) {
        super(library, entries);
        securityManager = library.getSecurityManager();
    }

    /**
     * Sets a property to a value given its name
     *
     * @param name  The name of the property
     * @param value The value
     */
    public void setProperty(final Name name, final Object value) {
        entries.put(name, value);
    }

    /**
     * Gets the value of the custom extension specified by <code>name</code>.
     *
     * @param name som plug-in extensions name.
     * @return value of the plug-in extension.
     */
    public Object getCustomExtension(Name name) {
        Object value = library.getObject(entries, name);
        if (value != null && value instanceof StringObject) {
            StringObject text = (StringObject) value;
            return Utils.convertStringObject(library, text);
        }
        return value;
    }

    /**
     * Sets a custom property to the PDF
     *
     * @param key   The name of the property
     * @param value The value
     */
    public void setCustomExtension(final String key, final Object value) {
        setProperty(new Name(key), value);
    }

    /**
     * Gets the title of the document.
     *
     * @return the documents title.
     */
    public String getTitle() {
        Object value = library.getObject(entries, TITLE_KEY);
        if (value != null && value instanceof StringObject) {
            StringObject text = (StringObject) value;
            return Utils.convertStringObject(library, text);
        } else if (value instanceof String) {
            return (String) value;
        }
        return null;
    }

    /**
     * Sets the title of the PDF
     *
     * @param title The title
     */
    public void setTitle(final String title) {
        setProperty(TITLE_KEY, new LiteralStringObject(title));
    }

    /**
     * Gets the name of the person who created the document.
     *
     * @return author name.
     */
    public String getAuthor() {
        Object value = library.getObject(entries, AUTHOR_KEY);
        if (value != null && value instanceof StringObject) {
            StringObject text = (StringObject) value;
            return Utils.convertStringObject(library, text);
        } else if (value instanceof String) {
            return (String) value;
        }
        return null;
    }

    /**
     * Sets the author of the PDF
     *
     * @param author The author
     */
    public void setAuthor(final String author) {
        setProperty(AUTHOR_KEY, new LiteralStringObject(author));
    }

    /**
     * Gets the subject of the document.
     *
     * @return documents subject.
     */
    public String getSubject() {
        Object value = library.getObject(entries, SUBJECT_KEY);
        if (value != null && value instanceof StringObject) {
            StringObject text = (StringObject) value;
            return Utils.convertStringObject(library, text);
        } else if (value instanceof String) {
            return (String) value;
        }
        return null;
    }

    /**
     * Sets the subject of the PDF
     *
     * @param subject The subject
     */
    public void setSubject(final String subject) {
        setProperty(SUBJECT_KEY, new LiteralStringObject(subject));
    }

    /**
     * Gets the keywords associated with the document.
     *
     * @return documents keywords.
     */
    public String getKeywords() {
        Object value = library.getObject(entries, KEYWORDS_KEY);
        if (value != null && value instanceof StringObject) {
            StringObject text = (StringObject) value;
            return Utils.convertStringObject(library, text);
        } else if (value instanceof String) {
            return (String) value;
        }
        return null;
    }

    /**
     * Sets the keywords of the pdf
     *
     * @param keywords A varargs of keywords. They will be separated by a comma
     */
    public void setKeywords(final String... keywords) {
        setProperty(KEYWORDS_KEY, new LiteralStringObject(String.join(", ", keywords)));
    }

    /**
     * Gets the name of the application. If the PDF document was converted from
     * another format that <b>created</b> the original document.
     *
     * @return creator name.
     */
    public String getCreator() {
        Object value = library.getObject(entries, CREATOR_KEY);
        if (value != null && value instanceof StringObject) {
            StringObject text = (StringObject) value;
            return Utils.convertStringObject(library, text);
        } else if (value instanceof String) {
            return (String) value;
        }
        return null;
    }

    /**
     * Sets the creator of the PDF
     *
     * @param creator the creator
     */
    public void setCreator(final String creator) {
        setProperty(CREATOR_KEY, new LiteralStringObject(creator));
    }

    /**
     * Gets the name of the application. If the PDF document was converted from
     * another format that <b>converted</b> the original document.
     *
     * @return producer name.
     */
    public String getProducer() {
        Object value = library.getObject(entries, PRODUCER_KEY);
        if (value != null && value instanceof StringObject) {
            StringObject text = (StringObject) value;
            return Utils.convertStringObject(library, text);
        } else if (value instanceof String) {
            return (String) value;
        }
        return null;
    }

    /**
     * Sets the producer of the PDF
     *
     * @param producer the producer
     */
    public void setProducer(final String producer) {
        setProperty(PRODUCER_KEY, new LiteralStringObject(producer));
    }

    /**
     * Gets the date and time the document was created.
     *
     * @return creation date.
     */
    public PDate getCreationDate() {
        Object value = library.getObject(entries, CREATIONDATE_KEY);
        if (value != null && value instanceof StringObject) {
            StringObject text = (StringObject) value;
            return new PDate(securityManager, text.getDecryptedLiteralString(securityManager));
        }
        return null;
    }

    /**
     * Sets the creation date of the PDF
     *
     * @param date The creation date
     */
    public void setCreationDate(final PDate date) {
        setProperty(CREATIONDATE_KEY, new LiteralStringObject(date.toString()));
    }

    /**
     * Gets the date and time the document was most recently modified.
     *
     * @return modification date.
     */
    public PDate getModDate() {
        Object value = library.getObject(entries, MODDATE_KEY);
        if (value != null && value instanceof StringObject) {
            StringObject text = (StringObject) value;
            return new PDate(securityManager, text.getDecryptedLiteralString(securityManager));
        }
        return null;
    }

    /**
     * Sets the modification date of the PDF
     *
     * @param date The modification date
     */
    public void setModDate(final PDate date) {
        setProperty(MODDATE_KEY, new LiteralStringObject(date.toString()));
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
        Object value = library.getObject(entries, TRAPPED_KEY);
        if (value != null && value instanceof StringObject) {
            StringObject text = (StringObject) value;
            return Utils.convertStringObject(library, text);
        } else if (value instanceof String) {
            return (String) value;
        }
        return null;
    }

    /**
     * Sets the trapping value of the PDF
     *
     * @param value The trapping value
     */
    public void setTrappingInformation(final String value) {
        setProperty(TRAPPED_KEY, new LiteralStringObject(value));
    }
}
