/*
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
package org.icepdf.core.util;

import org.icepdf.core.SecurityCallback;
import org.icepdf.core.exceptions.PDFSecurityException;
import org.icepdf.core.pobjects.*;
import org.icepdf.core.pobjects.acroform.InteractiveForm;
import org.icepdf.core.pobjects.acroform.SignatureHandler;
import org.icepdf.core.pobjects.fonts.Font;
import org.icepdf.core.pobjects.fonts.FontDescriptor;
import org.icepdf.core.pobjects.graphics.ICCBased;
import org.icepdf.core.pobjects.graphics.images.ImageStream;
import org.icepdf.core.pobjects.graphics.images.references.ImagePool;
import org.icepdf.core.pobjects.security.SecurityManager;
import org.icepdf.core.pobjects.structure.CrossReferenceRoot;
import org.icepdf.core.pobjects.structure.Indexer;
import org.icepdf.core.pobjects.structure.exceptions.CrossReferenceStateException;
import org.icepdf.core.pobjects.structure.exceptions.ObjectStateException;
import org.icepdf.core.util.parser.object.ObjectLoader;

import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * <p>The <code>Library</code> class acts a central repository for the access
 * of PDF objects in a document.  The Library class has many utility methods
 * which are designed to access PDF objects as easily as possible.  The
 * <code>Library</code> class has direct access to the PDF file and loads the
 * needed objects from the file system when needed. </p>
 *
 * @since 1.0
 */
public class Library {

    private static final Logger logger =
            Logger.getLogger(Library.class.toString());

    protected static ThreadPoolExecutor commonThreadPool;
    protected static ThreadPoolExecutor imageThreadPool;

    public static int commonPoolThreads;
    public static int imagePoolThreads;
    private static final long KEEP_ALIVE_TIME = 90;

    static {
        try {
            commonPoolThreads =
                    Defs.intProperty("org.icepdf.core.library.threadPoolSize", 6);
            if (commonPoolThreads < 1) {
                commonPoolThreads = 2;
            }
        } catch (NumberFormatException e) {
            logger.warning("Error reading buffered scale factor");
        }

        try {
            // todo make ImageReference call interruptible and then we can get rid of this pool.
            imagePoolThreads =
                    Defs.intProperty("org.icepdf.core.library.imageThreadPoolSize", 2);
            if (imagePoolThreads < 1) {
                imagePoolThreads = 2;
            }
        } catch (NumberFormatException e) {
            logger.warning("Error reading buffered scale factor");
        }
    }

    private final ConcurrentHashMap<Reference, java.lang.ref.Reference<Object>> objectStore =
            new ConcurrentHashMap<>(1024);
    private final ConcurrentHashMap<Reference, WeakReference<ICCBased>> lookupReference2ICCBased =
            new ConcurrentHashMap<>(256);

    private double fileVersion = 1.0;
    private String fileOrigin;

    private ByteBuffer mappedFileByteBuffer;
    private final Object mappedFileByteBufferLock = new Object();

    private CrossReferenceRoot crossReferenceRoot;

    private final ObjectLoader objectLoader;

    private Catalog catalog;

    private SecurityManager securityManager;

    // handles signature validation and signing.
    private final SignatureHandler signatureHandler;

    // signature permissions
    private Permissions permissions;

    // state manager reference needed by most classes to properly managed state
    // changes and new object creation
    public StateManager stateManager;

    private boolean isEncrypted;
    private boolean isLinearTraversal;
    private ImagePool imagePool;


    /**
     * Creates a new instance of a Library.
     */
    public Library() {
        objectLoader = new ObjectLoader(this);
        // set Catalog memory Manager and cache manager.
        imagePool = new ImagePool();
        signatureHandler = new SignatureHandler();
    }

    /**
     * Gets the object specified by the reference.
     *
     * @param reference reference to a PDF object in the document structure.
     * @return PDF object dictionary that the reference refers to.  Null if the
     * object reference can not be found.
     */
    public Object getObject(Reference reference) {
        return getObject(reference, null);
    }

    private Object getObject(Reference reference, Name hint) {
        Object obj;
        java.lang.ref.Reference<Object> obRef = objectStore.get(reference);
        // check stateManager first to allow for annotations to be injected
        // from a separate file.
        if (stateManager != null) {
            if (stateManager.contains(reference)) {
                obj = stateManager.getChange(reference);
                if (obj instanceof PObject) {
                    return ((PObject) obj).getObject();
                }
                return obj;
            }
        }
        obj = obRef != null ? obRef.get() : null;
        if (obj == null && crossReferenceRoot != null) {
            try {
                obj = crossReferenceRoot.loadObject(objectLoader, reference, hint);
            } catch (ObjectStateException | CrossReferenceStateException | IOException e) {
                // a null object is ok in this case we are looking at likely an incorrectly indexed file.
                logger.log(Level.WARNING, e,
                        () -> "Cross reference indexing failed, reindexing file. " + getFileOrigin());
                try {
                    rebuildCrossReferenceTable();
                    return getObject(reference);
                } catch (IOException | CrossReferenceStateException | ObjectStateException e1) {
                    logger.log(Level.WARNING, "Linear traversal of file failed, can not load file.", e);
                    return null;
                }
            } catch (ClassCastException e) {
                Reference finalReference = reference;
                logger.log(Level.WARNING, e,
                        () -> "Failed to load object, likely malformed. " + finalReference + " " + getFileOrigin());
                return null;
            }
            if (obj == null) return null;
            // keep expensive like fonts, images, page tree
            Object object = ((PObject) obj).getObject();
            if (isSoftReferenceAble(object)) {
                objectStore.put(reference, new SoftReference<>(obj));
            } else {
                objectStore.put(reference, new WeakReference<>(obj));
            }
            return object;
        }
        if (obj instanceof PObject) {
            return ((PObject) obj).getObject();
        } else if (obj instanceof Reference) {
            Reference secondReference = (Reference) obj;
            logger.log(Level.WARNING,  () -> "Found a reference to a reference: " + secondReference);
            return getObject(secondReference);
        }
        return obj;
    }

    private boolean isSoftReferenceAble(Object object) {
        if (object instanceof Dictionary) {
            DictionaryEntries entries = ((Dictionary)object).getEntries();
            Name type = getName(entries, Dictionary.TYPE_KEY);
            if (type != null) {
                return type.equals(Font.TYPE) ||
                        type.equals(PageTree.TYPE) ||
                        type.equals(Font.TYPE) ||
                        type.equals(ImageStream.TYPE_VALUE);
            } else {
                return false;
            }

        }
        return false;
    }

    public CrossReferenceRoot rebuildCrossReferenceTable()
            throws IOException, CrossReferenceStateException, ObjectStateException {
        Indexer indexer = new Indexer(this);
        synchronized (mappedFileByteBufferLock) {
            crossReferenceRoot = indexer.indexObjects(mappedFileByteBuffer);
            setCrossReferenceRoot(crossReferenceRoot);
        }
        return crossReferenceRoot;
    }

    public String getFileOrigin() {
        return fileOrigin;
    }

    public void setCrossReferenceRoot(CrossReferenceRoot crossReferenceRoot) throws ObjectStateException {
        this.crossReferenceRoot = crossReferenceRoot;
    }

    public CrossReferenceRoot getCrossReferenceRoot() {
        return crossReferenceRoot;
    }

    /**
     * Utility method for building the SecurityManager if the document
     * contains a crypt entry in the PTrailer.
     *
     * @param documentTrailer document trailer
     * @return true if created, false otherwise
     * @throws PDFSecurityException if there is an issue finding encryption libraries.
     */
    public boolean makeSecurityManager(PTrailer documentTrailer) throws PDFSecurityException {
        /*
          Before a security manager can be created or needs to be created
          we need the following
               1.  The trailer object must have an encrypt entry
               2.  The trailer object must have an ID entry
         */
        boolean madeSecurityManager = false;
        DictionaryEntries encryptDictionary = documentTrailer.getEncrypt();
        List fileID = documentTrailer.getID();
        // check for a missing file ID.
        if (fileID == null) {
            // we have a couple malformed documents that don't specify a FILE ID.
            // but proving two empty string allows the document to be decrypted.
            fileID = new ArrayList(2);
            fileID.add(new LiteralStringObject(""));
            fileID.add(new LiteralStringObject(""));
        }

        if (encryptDictionary != null && fileID != null) {
            // create new security manager
            securityManager = new SecurityManager(this, encryptDictionary, fileID);
            madeSecurityManager = true;
        }
        return madeSecurityManager;
    }

    /**
     * Initializes permission object as it is uses with encrypt permission to define
     * document characteristics at load time.
     *
     * @return true if permissions where found, false otherwise.
     */
    public boolean configurePermissions() {
        if (catalog != null) {
            Permissions permissions = catalog.getPermissions();
            if (permissions != null) {
                this.permissions = permissions;
                if (logger.isLoggable(Level.FINER)) {
                    logger.finer("Document perms dictionary found and configured. ");
                }
                return true;
            }
        }
        return false;
    }

    /**
     * If the document has a SecurityManager it is encrypted and as a result the
     * following method is used with the SecurityCallback to prompt a user for
     * a password if needed.
     *
     * @throws PDFSecurityException error during authorization manager setup
     */
    public void attemptAuthorizeSecurityManager(Document document, SecurityCallback securityCallback) throws PDFSecurityException {
        // check if pdf is password protected, by passing in black
        // password
        if (!securityManager.isAuthorized("")) {
            // count password tries
            int count = 1;
            // store temporary password
            String password;

            // Give user 3 chances to type the correct password
            // before throwing security exceptions
            while (true) {
                // Display password dialog
                // make sure a callback has been set.
                if (securityCallback != null) {
                    password = securityCallback.requestPassword(document);
                    if (password == null) {
                        throw new PDFSecurityException("Encryption error");
                    }
                } else {
                    throw new PDFSecurityException("Encryption error");
                }

                // Verify new password,  proceed if authorized,
                //    fatal exception otherwise.
                if (securityManager.isAuthorized(password)) {
                    break;
                }
                count++;
                // after 3 tries throw an error.
                if (count > 3) {
                    throw new PDFSecurityException("Encryption error");
                }
            }
        }

        // set the encryption flag on catalog
        setEncrypted(true);
    }

    public ByteBuffer getMappedFileByteBuffer() {
        return mappedFileByteBuffer;
    }

    public void setDocumentByteBuffer(ByteBuffer mappedFileByteBuffer) {
        this.mappedFileByteBuffer = mappedFileByteBuffer;
    }

    public void setFileOrigin(String fileOrigin) {
        this.fileOrigin = fileOrigin;
    }

    public Object getMappedFileByteBufferLock() {
        return mappedFileByteBufferLock;
    }

    public double getFileVersion() {
        return fileVersion;
    }

    public void setFileVersion(double fileVersion) {
        this.fileVersion = fileVersion;
    }

    /**
     * Utility method for displaying debug info related to PDF object loading.
     *
     * @param ob object to show debug information for
     */
    private void printObjectDebug(Object ob) {
        if (ob == null) {
            logger.finer("null object found");
        } else if (ob instanceof PObject) {
            PObject tmp = (PObject) ob;
            logger.finer(tmp.getReference() + " " + tmp);
        } else if (ob instanceof Dictionary) {
            Dictionary tmp = (Dictionary) ob;
            logger.finer(tmp.getPObjectReference() + " " + tmp);
        } else {
            logger.finer(ob.getClass() + " " + ob);
        }
    }

    /**
     * Gets the PDF object specified by the <code>key</code> in the dictionary
     * entries.  If the key value is a reference, the object that the reference
     * points to is returned.
     *
     * @param dictionaryEntries the dictionary entries to look up the key in.
     * @param key               string value representing the dictionary key.
     * @return PDF object that the key references.
     */
    public Object getObject(DictionaryEntries dictionaryEntries, Name key) {
        if (dictionaryEntries == null) {
            return null;
        }
        Object o = dictionaryEntries.get(key);
        if (o == null) {
            return null;
        }
        if (o instanceof Reference) {
            o = getObject((Reference) o);
        }
        if (o instanceof StateManager.Change) {
            o = ((StateManager.Change) o).getPObject().getObject();
        }
        if (o instanceof PObject) {
            o = ((PObject) o).getObject();
        }
        return o;
    }

    /**
     * Test to see if the given key is a reference and not an inline dictinary
     *
     * @param dictionaryEntries dictionary to test
     * @param key               dictionary key
     * @return true if the key value exists and is a reference, false if the
     * dictionaryEntries are null or the key references an inline dictionary
     */
    public boolean isReference(DictionaryEntries dictionaryEntries, Name key) {
        return dictionaryEntries != null &&
                dictionaryEntries.get(key) instanceof Reference;

    }

    /**
     * Gets the reference association of the key if any.  This method is usual
     * used in combination with #isReference to get and assign the Reference
     * for a given PObject.
     *
     * @param dictionaryEntries dictionary to search in.
     * @param key               key to search for in dictionary.
     * @return reference of the object that key points if any.  Null if the key
     * points to an inline dictionary and not a reference.
     */
    public Reference getReference(DictionaryEntries dictionaryEntries, Name key) {
        Object ref = dictionaryEntries.get(key);
        if (ref instanceof Reference) {
            return (Reference) ref;
        } else {
            return null;
        }
    }

    /**
     * Gets the state manager class which keeps track of changes PDF objects.
     *
     * @return document state manager
     */
    public StateManager getStateManager() {
        return stateManager;
    }

    /**
     * Sets the document state manager so that all object can access the
     * state manager via the central library instance.
     *
     * @param stateManager reference to the state change class
     */
    public void setStateManager(StateManager stateManager) {
        this.stateManager = stateManager;
    }

    /**
     * Gets the PDF object that the <code>referenceObject</code> references.
     *
     * @param referenceObject reference object.
     * @return PDF object that <code>referenceObject</code> references.  If
     * <code>referenceObject</code> is not an instance of a Reference, the
     * origional <code>referenceObject</code> is returned.
     */
    public Object getObject(Object referenceObject) {
        if (referenceObject instanceof Reference) {
            return getObject((Reference) referenceObject);
        }
        return referenceObject;
    }

    /**
     * Tests if the given key will return a non-null PDF object from the
     * specified dictionary entries.  A null PDF object would result if no
     * PDF object could be found with the specified key.
     *
     * @param dictionaryEntries dictionary entries
     * @param key               dictionary key
     * @return true, if the key's value is non-null PDF object; false, otherwise.
     */
    public boolean isValidEntry(DictionaryEntries dictionaryEntries, Name key) {
        if (dictionaryEntries == null) {
            return false;
        }
        Object o = dictionaryEntries.get(key);
        return o != null && (!(o instanceof Reference) || isValidEntry((Reference) o));
    }

    /**
     * Tests if there exists a cross-reference entry for this reference.
     *
     * @param reference reference to a PDF object in the document structure.
     * @return true, if a cross-reference entry exists for this reference; false, otherwise.
     */
    public boolean isValidEntry(Reference reference) {
        try {
            java.lang.ref.Reference ob = objectStore.get(reference);
            return (ob != null && ob.get() != null) ||
                    crossReferenceRoot.loadObject(objectLoader, reference, null) != null;
        } catch (ObjectStateException | CrossReferenceStateException | IOException e) {
            return false;
        }
    }

    /**
     * Gets a Number specified by the <code>key</code> in the dictionary
     * entries.  If the key value is a reference, the Number object that the
     * reference points to is returned.
     *
     * @param dictionaryEntries the dictionary entries to look up the key in.
     * @param key               string value representing the dictionary key.
     * @return Number object if a valid key;  null, if the key does not point
     * to Number or is invalid.
     */
    public Number getNumber(DictionaryEntries dictionaryEntries, Name key) {
        Object o = getObject(dictionaryEntries, key);
        if (o instanceof Number)
            return (Number) o;
        return null;
    }

    /**
     * Gets a Boolean specified by the <code>key</code> in the dictionary
     * entries.  If the key value is a reference, the Boolean object that the
     * reference points to is returned.
     *
     * @param dictionaryEntries the dictionary entries to look up the key in.
     * @param key               string value representing the dictionary key.
     * @return Number object if a valid key;  null, if the key does not point
     * to Number or is invalid.
     */
    public Boolean getBoolean(DictionaryEntries dictionaryEntries, Name key) {
        Object o = getObject(dictionaryEntries, key);
        if (o instanceof String)
            return Boolean.valueOf((String) o);
        else return o instanceof Boolean && (Boolean) o;
    }

    /**
     * Gets a float specified by the <code>key</code> in the dictionary
     * entries.  If the key value is a reference, the object that the
     * reference points to is returned.
     *
     * @param dictionaryEntries the dictionary entries to look up the key in.
     * @param key               string value representing the dictionary key.
     * @return float value if a valid key;  null, if the key does not point
     * to a float or is invalid.
     */
    public float getFloat(DictionaryEntries dictionaryEntries, Name key) {
        Number n = getNumber(dictionaryEntries, key);
        return (n != null) ? n.floatValue() : 0.0f;
    }

    /**
     * Gets an int specified by the <code>key</code> in the dictionary
     * entries.  If the key value is a reference, the object that the
     * reference points to is returned.
     *
     * @param dictionaryEntries the dictionary entries to look up the key in.
     * @param key               string value representing the dictionary key.
     * @return int value if a valid key,  null if the key does not point
     * to an int or is invalid.
     */
    public int getInt(DictionaryEntries dictionaryEntries, Name key) {
        Number n = getNumber(dictionaryEntries, key);
        return (n != null) ? n.intValue() : 0;
    }

    /**
     * Gets a float specified by the <code>key</code> in the dictionary
     * entries.  If the key value is a reference, the object that the
     * reference points to is returned.
     *
     * @param dictionaryEntries the dictionary entries to look up the key in.
     * @param key               string value representing the dictionary key.
     * @return float value if a valid key;  null, if the key does not point
     * to a float or is invalid.
     */
    public long getLong(DictionaryEntries dictionaryEntries, Name key) {
        Number n = getNumber(dictionaryEntries, key);
        return (n != null) ? n.longValue() : 0L;
    }

    /**
     * Gets a Name specified by the <code>key</code> in the dictionary
     * entries.  If the key value is a reference, the Name object that the
     * reference points to is returned.
     *
     * @param dictionaryEntries the dictionary entries to look up the key in.
     * @param key               string value representing the dictionary key.
     * @return Name object if a valid key;  null, if the key does not point
     * to Name or is invalid.
     */
    public Name getName(DictionaryEntries dictionaryEntries, Name key) {
        Object o = getObject(dictionaryEntries, key);
        if (o != null) {
            if (o instanceof Name) {
                return (Name) o;
            }
        }
        return null;
    }

    /**
     * Gets a text string specified by the <code>key</code> in the dictionary
     * entries.  If the key value is a reference, the string object that the
     * reference points to is returned.
     *
     * @param dictionaryEntries the dictionary entries to look up the key in.
     * @param key               string value representing the dictionary key.
     * @return string object if a valid key;  null, if the key does not point
     * to Name or is invalid.
     */
    public String getString(DictionaryEntries dictionaryEntries, Name key) {
        Object o = getObject(dictionaryEntries, key);
        if (o != null) {
            if (o instanceof String) {
                return ((String) o);
            } else if (o instanceof StringObject) {
                return ((StringObject) o).getDecryptedLiteralString(securityManager);
            } else if (o instanceof Name) {
                return ((Name) o).getName();
            }
        }
        return null;
    }

    /**
     * Gets a dictionary specified by the <code>key</code> in the dictionary
     * entries.  If the key value is a reference, the dictionary object that the
     * reference points to is returned.
     *
     * @param dictionaryEntries the dictionary entries to look up the key in.
     * @param key               string value representing the dictionary key.
     * @return dictionary object if a valid key;  null, if the key does not point
     * to dictionary or is invalid.
     */
    @SuppressWarnings("unchecked")
    public DictionaryEntries getDictionary(DictionaryEntries dictionaryEntries, Name key) {
        Object o = getObject(dictionaryEntries, key);
        if (o instanceof DictionaryEntries) {
            return (DictionaryEntries) o;
        } else if (o instanceof List) {
            List v = (List) o;
            DictionaryEntries h1 = new DictionaryEntries();
            for (Object o1 : v) {
                if (o1 instanceof DictionaryEntries) {
                    h1.putAll((DictionaryEntries) o1);
                }
            }
            return h1;
        }
        return null;
    }

    public List getArray(DictionaryEntries dictionaryEntries, Name key) {
        Object o = getObject(dictionaryEntries, key);
        if (o instanceof List) {
            return (List) o;
        }
        return null;
    }

    /**
     * Gets a rectangle specified by the key.  The rectangle is already
     * in the coordinate system of Java2D, and thus must be used carefully.
     *
     * @param dictionaryEntries the dictionary entries to look up the key in.
     * @param key               string value representing the dictionary key.
     * @return rectangle in Java2D coordinate system.
     */
    public Rectangle2D.Float getRectangle(DictionaryEntries dictionaryEntries, Name key) {
        List v = (List) getObject(dictionaryEntries, key);
        if (v != null) {
            // s by default contains data in the Cartesian plain.
            if (v.get(0) instanceof Number) {
                return new PRectangle(v).toJava2dCoordinates();
            } // crazy corner case that contains each number as reference.
            else if (v.get(0) instanceof Reference) {
                v.set(0, getObject(v.get(0)));
                v.set(1, getObject(v.get(1)));
                v.set(2, getObject(v.get(2)));
                v.set(3, getObject(v.get(3)));
                return new PRectangle(v).toJava2dCoordinates();
            }
        }
        return null;
    }

    /**
     * The Reference is to the Stream from which the ICC color space data
     * is to be parsed. So, without this method, we would be making and
     * initializing a new ICCBased object every time one was needed, since
     * the Reference is not for the ICCBased object itself.
     *
     * @param ref Reference to Stream containing ICC color space data
     * @return ICCBased color model object for the given reference
     */
    public ICCBased getICCBased(Reference ref) {
        ICCBased cs = null;

        WeakReference<ICCBased> csRef = lookupReference2ICCBased.get(ref);
        if (csRef != null) {
            cs = csRef.get();
        }

        if (cs == null) {
            Object obj = getObject(ref);
            if (obj instanceof Stream) {
                Stream stream = (Stream) obj;
                cs = new ICCBased(this, stream);
                lookupReference2ICCBased.put(ref, new WeakReference<>(cs));
            }
        }
        return cs;
    }

    @SuppressWarnings("unchecked")
    public Resources getResources(DictionaryEntries dictionaryEntries, Name key) {
        if (dictionaryEntries == null)
            return null;
        Object ob = dictionaryEntries.get(key);
        if (ob == null)
            return null;
        else if (ob instanceof Resources)
            return (Resources) ob;
        else if (ob instanceof Reference) {
            Reference reference = (Reference) ob;
            return getResources(reference);
        } else if (ob instanceof DictionaryEntries) {
            DictionaryEntries ht = (DictionaryEntries) ob;
            Resources resources = new Resources(this, ht);
            dictionaryEntries.put(key, resources);
            return resources;
        } else {
            logger.log(Level.WARNING, () -> "Failed to get resource for key: " + key + " in " + dictionaryEntries.toString());
        }
        return null;
    }

    public Resources getResources(Reference reference) {
        Object object = getObject(reference);
        if (object instanceof Resources) {
            return (Resources) object;
        } else if (object instanceof DictionaryEntries) {
            DictionaryEntries ht = (DictionaryEntries) object;
            Resources resources = new Resources(this, ht);
            addObject(resources, reference);
            return resources;
        } else if (object instanceof Dictionary) {
            // corner case: object is of an unexpected type,  grab the dictionary and try to continue.
            DictionaryEntries ht = ((Dictionary) object).getEntries();
            Resources resources = new Resources(this, ht);
            addObject(resources, reference);
            return resources;
        }
        return null;
    }

    /**
     * Adds a PDF object and its respective object reference to the library.
     *
     * @param object          PDF object to add.
     * @param objectReference PDF object reference object.
     */
    public void addObject(Object object, Reference objectReference) {
        objectStore.put(objectReference, new WeakReference<>(object));
    }

    /**
     * Removes an object from from the library.
     *
     * @param objetReference object reference to remove to library
     */
    public void removeObject(Reference objetReference) {
        if (objetReference != null) {
            objectStore.remove(objetReference);
        }
    }

    /**
     * Sets a pointer to the orginal document input stream
     *
     * @param documentInput seekable inputstream.
     */
//    public void setDocumentInput(SeekableInput documentInput) {
//        this.documentInput = documentInput;
//    }

    /**
     * Gets the SeekableInput of the document underlying bytes.
     *
     * @return document bytes.
     */
//    public SeekableInput getDocumentInput() {
//        return documentInput;
//    }

    /**
     * Gets the PDF object specified by the <code>key</code> in the dictionary
     * entries.  If the key value is a reference it is returned.
     *
     * @param dictionaryEntries the dictionary entries to look up the key in.
     * @param key               string value representing the dictionary key.
     * @return the Reference specified by the PDF key.  If the key is invalid
     * or does not reference a Reference object, null is returned.
     */
    public Reference getObjectReference(DictionaryEntries dictionaryEntries,
                                        Name key) {
        if (dictionaryEntries == null) {
            return null;
        }
        Object o = dictionaryEntries.get(key);
        if (o == null)
            return null;
        Reference currentRef = null;
        while (o != null && (o instanceof Reference)) {
            currentRef = (Reference) o;
            o = getObject(currentRef);
        }
        return currentRef;
    }

    /**
     * Indicates that document is encrypted using Adobe Standard Encryption.
     *
     * @return true if the document is encrypted, false otherwise.
     */
    public boolean isEncrypted() {
        return isEncrypted;
    }

    /**
     * Gets the document's security manger.
     *
     * @return document's security manager if the document is encrypted, null
     * otherwise.
     */
    public SecurityManager getSecurityManager() {
        return securityManager;
    }

    public void setSecurityManager(SecurityManager securityManager) {
        this.securityManager = securityManager;
    }

    public SignatureHandler getSignatureHandler() {
        return signatureHandler;
    }

    /**
     * Set a documents permissions for a given certificate of signature, optional.
     * The permission should also be used with the encryption permissions if present
     * to configure the viewer permissions.
     *
     * @return permission object if present, otherwise null.
     */
    public Permissions getPermissions() {
        return permissions;
    }

    public void setPermissions(Permissions permissions) {
        this.permissions = permissions;
    }

    /**
     * Set the document is encrypted flag.
     *
     * @param flag true, if the document is encrypted; false, otherwize.
     */
    public void setEncrypted(boolean flag) {
        isEncrypted = flag;
    }

    /**
     * When we fail to load the required xref tables or streams that are
     * needed to access the objects in the PDF, then we simply go to the
     * beginning of the file and read in all of the objects into memory,
     * which we call linear traversal.
     */
    public void setLinearTraversal() {
        isLinearTraversal = true;
    }

    /**
     * There are several implications from using linear traversal, which
     * affect how we parse the PDF objects, and maintain them in memory,
     * so those sections of code need to check this flag here.
     *
     * @return If PDF was parsed via linear traversal
     */
    public boolean isLinearTraversal() {
        return isLinearTraversal;
    }

    /**
     * Gets the document's catalog.
     *
     * @return document's catalog.
     */
    public Catalog getCatalog() {
        return catalog;
    }

    /**
     * Sets the document's catalog.  Normally only accessed by the document's parser.
     *
     * @param c document catalog object.
     */
    public void setCatalog(Catalog c) {
        catalog = c;
    }

    /**
     * Checks the Catalog for an interactive Forms dictionary and if found the resources object
     * is used for a font lookup.
     *
     * @param fontName font name to look for.
     * @return font font,  null otherwise.
     */
    public Font getInteractiveFormFont(String fontName) {
        InteractiveForm form = getCatalog().getInteractiveForm();
        if (form != null && form.getResources() != null) {
            Resources resources = form.getResources();
            return resources.getFont(new Name(fontName));
        }
        return null;
    }

    /**
     * Utility/demo functionality to clear all font and font descriptor
     * resources.  The library will re-fetch the font resources in question
     * when needed again.
     */
    public void disposeFontResources() {
        Set<Reference> test = objectStore.keySet();
        for (Reference ref : test) {
            java.lang.ref.Reference reference = objectStore.get(ref);
            Object tmp = reference != null ? reference.get() : null;
            if (tmp instanceof Font || tmp instanceof FontDescriptor) {
                objectStore.remove(ref);
            }
        }
    }

    public ImagePool getImagePool() {
        return imagePool;
    }

    public static void initializeThreadPool() {

        logger.log(Level.FINE, () -> "Starting ICEpdf Thread Pool: " + commonPoolThreads + " threads.");

        if (commonThreadPool == null || commonThreadPool.isShutdown()) {
            commonThreadPool = new ThreadPoolExecutor(
                    commonPoolThreads, commonPoolThreads, KEEP_ALIVE_TIME, TimeUnit.SECONDS,
                    new LinkedBlockingQueue<>());
            // set a lower thread priority
            commonThreadPool.setThreadFactory(command -> {
                Thread newThread = new Thread(command);
                newThread.setName("ICEpdf-thread-pool");
                newThread.setPriority(Thread.NORM_PRIORITY);
                newThread.setDaemon(true);
                return newThread;
            });
        }

        logger.log(Level.FINE, () -> "Starting ICEpdf image proxy Pool: " + imagePoolThreads + " threads.");
        if (imageThreadPool == null || imageThreadPool.isShutdown()) {
            imageThreadPool = new ThreadPoolExecutor(
                    imagePoolThreads, imagePoolThreads, KEEP_ALIVE_TIME, TimeUnit.SECONDS,
                    new LinkedBlockingQueue<>());
            // set a lower thread priority
            imageThreadPool.setThreadFactory(command -> {
                Thread newThread = new Thread(command);
                newThread.setName("ICEpdf-thread-image-pool");
                newThread.setPriority(Thread.NORM_PRIORITY);
                newThread.setDaemon(true);
                return newThread;
            });
        }
    }

    public static void shutdownThreadPool() {
        // do a little clean up.
        commonThreadPool.purge();
        commonThreadPool.shutdownNow();
        imageThreadPool.purge();
        imageThreadPool.shutdownNow();
    }

    public static void execute(Runnable runnable) {
        try {
            if (commonThreadPool == null || commonThreadPool.isShutdown()) {
                initializeThreadPool();
            }
            commonThreadPool.execute(runnable);
        } catch (RejectedExecutionException e) {
            logger.severe("ICEpdf Common Thread Pool was shutdown!");
        }
    }

    public static void executeImage(FutureTask callable) {
        try {
            if (imageThreadPool == null || imageThreadPool.isShutdown()) {
                initializeThreadPool();
            }
            imageThreadPool.execute(callable);
        } catch (RejectedExecutionException e) {
            logger.severe("ICEpdf Common Thread Pool was shutdown!");
        }
    }
}
