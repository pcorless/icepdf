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

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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

    public static final Set<Name> ALL_COMMON_KEYS = new HashSet<>(Arrays.asList(
            RESOURCES_KEY, TITLE_KEY, AUTHOR_KEY, SUBJECT_KEY, KEYWORDS_KEY, CREATOR_KEY, PRODUCER_KEY,
            CREATIONDATE_KEY, MODDATE_KEY, TRAPPED_KEY
    ));
    private static final Pattern KEYWORD_SPLIT = Pattern.compile("[,;:]\\s?");

    // security manager need for decrypting strings.
    private final SecurityManager securityManager;

    /**
     * Create a new instance of a <code>PInfo</code> object.
     *
     * @param library document library
     * @param entries entries for this object dictionary.
     */
    public PInfo(final Library library, final DictionaryEntries entries) {
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
    public Object getCustomExtension(final Name name) {
        final Object value = library.getObject(entries, name);
        if (value instanceof StringObject) {
            final StringObject text = (StringObject) value;
            return Utils.convertStringObject(library, text);
        }
        return value;
    }

    /**
     * @return All the custom extensions of the document
     */
    public Map<Object, Object> getAllCustomExtensions() {
        return entries.entrySet().stream().filter(e -> !ALL_COMMON_KEYS.contains(e.getKey())).collect(Collectors.toMap(Map.Entry::getKey,
                Map.Entry::getValue));
    }

    /**
     * Sets a custom property to the PDF
     *
     * @param key   The name of the property
     * @param value The unencrypted value
     */
    public void setCustomExtension(final Name key, final String value) {
        setProperty(key, getEncryptedString(value));
    }

    /**
     * Sets a custom property to the PDF
     *
     * @param key   The name of the property
     * @param value The unencrypted value
     */
    public void setCustomExtension(final String key, final String value) {
        setCustomExtension(new Name(key), value);
    }

    /**
     * Gets the title of the document.
     *
     * @return the documents title.
     */
    public String getTitle() {
        return getString(TITLE_KEY);
    }

    /**
     * Sets the title of the PDF
     *
     * @param title The title
     */
    public void setTitle(final String title) {
        setProperty(TITLE_KEY, getEncryptedString(title));
    }

    /**
     * Gets the name of the person who created the document.
     *
     * @return author name.
     */
    public String getAuthor() {
        return getString(AUTHOR_KEY);
    }

    /**
     * Sets the author of the PDF
     *
     * @param author The author
     */
    public void setAuthor(final String author) {
        setProperty(AUTHOR_KEY, getEncryptedString(author));
    }

    /**
     * Gets the subject of the document.
     *
     * @return documents subject.
     */
    public String getSubject() {
        return getString(SUBJECT_KEY);
    }

    /**
     * Sets the subject of the PDF
     *
     * @param subject The subject
     */
    public void setSubject(final String subject) {
        setProperty(SUBJECT_KEY, getEncryptedString(subject));
    }

    /**
     * Gets the keywords associated with the document.
     *
     * @return documents keywords.
     */
    public String getKeywords() {
        return getString(KEYWORDS_KEY);
    }

    /**
     * Sets the keywords of the pdf
     *
     * @param keywords A varargs of keywords. They will be separated by a comma
     */
    public void setKeywords(final String... keywords) {
        setProperty(KEYWORDS_KEY, getEncryptedString(String.join(", ", keywords)));
    }

    /**
     * Gets the name of the application. If the PDF document was converted from
     * another format that <b>created</b> the original document.
     *
     * @return creator name.
     */
    public String getCreator() {
        return getString(CREATOR_KEY);
    }

    /**
     * Sets the creator of the PDF
     *
     * @param creator the creator
     */
    public void setCreator(final String creator) {
        setProperty(CREATOR_KEY, getEncryptedString(creator));
    }

    /**
     * Gets the name of the application. If the PDF document was converted from
     * another format that <b>converted</b> the original document.
     *
     * @return producer name.
     */
    public String getProducer() {
        return getString(PRODUCER_KEY);
    }

    /**
     * Sets the producer of the PDF
     *
     * @param producer the producer
     */
    public void setProducer(final String producer) {
        setProperty(PRODUCER_KEY, getEncryptedString(producer));
    }

    /**
     * Gets the date and time the document was created.
     *
     * @return creation date.
     */
    public PDate getCreationDate() {
        final Object value = library.getObject(entries, CREATIONDATE_KEY);
        if (value instanceof StringObject) {
            final StringObject text = (StringObject) value;
            return new PDate(text.getDecryptedLiteralString(securityManager));
        }
        return null;
    }

    /**
     * Sets the creation date of the PDF
     *
     * @param date The creation date
     */
    public void setCreationDate(final PDate date) {
        setProperty(CREATIONDATE_KEY, getEncryptedString(date.toString()));
    }

    /**
     * Gets the date and time the document was most recently modified.
     *
     * @return modification date.
     */
    public PDate getModDate() {
        final Object value = library.getObject(entries, MODDATE_KEY);
        if (value instanceof StringObject) {
            final StringObject text = (StringObject) value;
            return new PDate(text.getDecryptedLiteralString(securityManager));
        }
        return null;
    }

    /**
     * Sets the modification date of the PDF
     *
     * @param date The modification date
     */
    public void setModDate(final PDate date) {
        setProperty(MODDATE_KEY, getEncryptedString(date.toString()));
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
        return getString(TRAPPED_KEY);
    }

    private String getString(final Name key) {
        final Object value = library.getObject(entries, key);
        if (value instanceof StringObject) {
            final StringObject text = (StringObject) value;
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

    /**
     * Updates the info with the given Map
     *
     * @param values The new values
     * @return If a value has changed
     */
    public boolean update(final Map<String, String> values) {
        boolean hasChanged = false;
        final Map<Object, Object> customProps = getAllCustomExtensions();
        clearCustomProps();
        for (final Map.Entry<String, String> entry : values.entrySet()) {
            final String key = entry.getKey();
            final String value = entry.getValue();
            final Name name = new Name(key);
            if (name.equals(RESOURCES_KEY)) {
                //TODO
            } else if (name.equals(TITLE_KEY)) {
                if (!isEmptyOrNullEqual(value, getTitle())) {
                    setTitle(value);
                    hasChanged = true;
                }
            } else if (name.equals(AUTHOR_KEY)) {
                if (!isEmptyOrNullEqual(value, getAuthor())) {
                    setAuthor(value);
                    hasChanged = true;
                }
            } else if (name.equals(SUBJECT_KEY)) {
                if (!isEmptyOrNullEqual(value, getSubject())) {
                    setSubject(value);
                    hasChanged = true;
                }
            } else if (name.equals(KEYWORDS_KEY)) {
                final String[] keywords = KEYWORD_SPLIT.split(value);
                if (!isEmptyOrNullEqual(String.join(", ", keywords), getKeywords())) {
                    setKeywords(keywords);
                    hasChanged = true;
                }
            } else if (name.equals(CREATOR_KEY)) {
                if (!isEmptyOrNullEqual(value, getCreator())) {
                    setCreator(value);
                    hasChanged = true;
                }
            } else if (name.equals(PRODUCER_KEY)) {
                if (!isEmptyOrNullEqual(value, getProducer())) {
                    setProducer(value);
                    hasChanged = true;
                }
            } else if (name.equals(CREATIONDATE_KEY)) {
                //TODO
            } else if (name.equals(MODDATE_KEY)) {
                //TODO
            } else if (name.equals(TRAPPED_KEY)) {
                if (!isEmptyOrNullEqual(value, getTrappingInformation())) {
                    setTrappingInformation(value);
                    hasChanged = true;
                }
            } else {
                setCustomExtension(key, value);
            }
        }
        if (!mapEquals(customProps, getAllCustomExtensions())) {
            hasChanged = true;
        }
        return hasChanged;
    }

    private static boolean mapEquals(final Map<Object, Object> first, final Map<Object, Object> second) {
        if (first.size() != second.size()) {
            return false;
        }
        for (final Map.Entry<Object, Object> entry : first.entrySet()) {
            final Object key = entry.getKey();
            final Object value = entry.getValue();
            if (!second.containsKey(key)) {
                return false;
            } else {
                final Object secondValue = second.get(key);
                if (!value.equals(secondValue)) {
                    if (value instanceof LiteralStringObject && secondValue instanceof LiteralStringObject) {
                        if (!((LiteralStringObject) value).getLiteralString()
                                .equals(((LiteralStringObject) secondValue).getLiteralString())) {
                            return false;
                        }
                    } else {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private static boolean isEmptyOrNullEqual(final String first, final String second) {
        return (first == null && second == null) ||
                (first == null && second.isEmpty()) ||
                (first != null && first.isEmpty() && second == null) ||
                (Objects.equals(first, second));
    }

    private void clearCustomProps() {
        getAllCustomExtensions().keySet().forEach(k -> entries.remove(k));
    }

    private LiteralStringObject getEncryptedString(final String value) {
        if (securityManager != null) {
            try {
                return new LiteralStringObject(value, getPObjectReference());
            } catch (final Exception e){
                return new LiteralStringObject(value);
            }
        } else {
            return new LiteralStringObject(value);
        }
    }
}
