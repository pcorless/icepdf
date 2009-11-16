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
package org.icepdf.core.util;

import org.icepdf.core.pobjects.*;
import org.icepdf.core.pobjects.Dictionary;
import org.icepdf.core.pobjects.fonts.Font;
import org.icepdf.core.pobjects.fonts.FontDescriptor;
import org.icepdf.core.pobjects.graphics.ICCBased;
import org.icepdf.core.pobjects.security.SecurityManager;

import java.awt.geom.Rectangle2D;
import java.util.*;
import java.util.logging.Logger;
import java.util.concurrent.ConcurrentHashMap;

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

    private static final Logger log =
            Logger.getLogger(Library.class.toString());

    // new incremental file loader class.
    private LazyObjectLoader m_LazyObjectLoader;

    private ConcurrentHashMap<Reference, Object> refs =
            new ConcurrentHashMap<Reference, Object>(1024);
    private ConcurrentHashMap<Reference, ICCBased> lookupReference2ICCBased =
            new ConcurrentHashMap<Reference, ICCBased>(256);

    // Instead of keeping Names names, Dictionary dests, we keep
    //   a reference to the Catalog, which actually owns them
    private Catalog catalog;

    // centrally locate these objects for later clean up.
    public MemoryManager memoryManager;
    public CacheManager cacheManager;
    public SecurityManager securityManager;

    private boolean isEncrypted = false;
    private boolean isLinearTraversal = false;

    /**
     * Sets a document loader for the library.
     *
     * @param lol loader object.
     */
    public void setLazyObjectLoader(LazyObjectLoader lol) {
        m_LazyObjectLoader = lol;
        if (m_LazyObjectLoader != null)
            memoryManager.registerMemoryManagerDelegate(m_LazyObjectLoader);
    }

    /**
     * Gets the document's trailer.
     *
     * @param position byte offset of the trailer in the PDF file.
     * @return trailer dictionary
     */
    public PTrailer getTrailerByFilePosition(long position) {
        if (m_LazyObjectLoader == null)
            return null;
        return m_LazyObjectLoader.loadTrailer(position);
    }

    /**
     * Gets the object specified by the reference.
     *
     * @param reference reference to a PDF object in the document structure.
     * @return PDF object dictionary that the reference refers to.  Null if the
     *         object reference can not be found.
     */
    public Object getObject(Reference reference) {
        Object ob;
        while (true) {
            ob = refs.get(reference);
            if (ob == null && m_LazyObjectLoader != null) {
                if ( m_LazyObjectLoader.loadObject(reference)) {
                    ob = refs.get(reference);
//                    printObjectDebug(ob);
                }
            }

            if (ob == null)
                break;
            else if (!(ob instanceof Reference))
                break;
            reference = (Reference) ob;
        }
        return ob;
    }

    /**
     * Utility method for displaying debug info related to PDF object loading.
     *
     * @param ob object to show debug information for
     */
    private void printObjectDebug(Object ob) {
        if (ob == null) {
            log.finer("null object found");
        } else if (ob instanceof PObject) {
            PObject tmp = (PObject) ob;
            log.finer(tmp.getReference() + " " + tmp.toString());
        } else if (ob instanceof Dictionary) {
            Dictionary tmp = (Dictionary) ob;
            log.finer(tmp.getPObjectReference() + " " + tmp.toString());
        } else {
            log.finer(ob.getClass() + " " + ob.toString());
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
     * @see #getObjectReference(java.util.Hashtable, String)
     */
    public Object getObject(Hashtable dictionaryEntries, String key) {
        if (dictionaryEntries == null) {
            return null;
        }
        Object o = dictionaryEntries.get(key);
        if (o == null)
            return null;
        if (o instanceof Reference)
            o = getObject((Reference) o);
        return o;
    }

    /**
     * Gets the PDF object that the <code>referenceObject</code> references.
     *
     * @param referenceObject reference object.
     * @return PDF object that <code>referenceObject</code> references.  If
     *         <code>referenceObject</code> is not an instance of a Reference, the
     *         origional <code>referenceObject</code> is returned.
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
    public boolean isValidEntry(Hashtable dictionaryEntries, String key) {
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
        Object ob = refs.get(reference);
        return ob != null ||
                m_LazyObjectLoader != null &&
                        m_LazyObjectLoader.haveEntry(reference);
    }

    /**
     * Gets a Number specified by the <code>key</code> in the dictionary
     * entries.  If the key value is a reference, the Number object that the
     * reference points to is returned.
     *
     * @param dictionaryEntries the dictionary entries to look up the key in.
     * @param key               string value representing the dictionary key.
     * @return Number object if a valid key;  null, if the key does not point
     *         to Number or is invalid.
     */
    public Number getNumber(Hashtable dictionaryEntries, String key) {
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
     *         to Number or is invalid.
     */
    public Boolean getBoolean(Hashtable dictionaryEntries, String key) {
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
     *         to a float or is invalid.
     */
    public float getFloat(Hashtable dictionaryEntries, String key) {
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
     *         to an int or is invalid.
     */
    public int getInt(Hashtable dictionaryEntries, String key) {
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
     *         to a float or is invalid.
     */
    public long getLong(Hashtable dictionaryEntries, String key) {
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
     *         to Name or is invalid.
     */
    public String getName(Hashtable dictionaryEntries, String key) {
        Object o = getObject(dictionaryEntries, key);
        if (o != null) {
            if (o instanceof Name) {
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
     *         to dictionary or is invalid.
     */
    public Hashtable getDictionary(Hashtable dictionaryEntries, String key) {
        Object o = getObject(dictionaryEntries, key);
        if (o instanceof Hashtable) {
            return (Hashtable) o;
        } else if (o instanceof Vector) {
            Vector v = (Vector) o;
            Hashtable h1 = new Hashtable();
            for (Enumeration e = v.elements(); e.hasMoreElements();) {
                Object o1 = e.nextElement();
                if (o1 instanceof Map) {
                    h1.putAll((Map) o1);
                }
            }
            return h1;
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
    public Rectangle2D.Float getRectangle(Hashtable dictionaryEntries, String key) {
        Vector v = (Vector) getObject(dictionaryEntries, key);
        if (v != null) {
            // s by default contains data in the Cartesian plain.
            return new PRectangle(v).toJava2dCoordinates();
        } else {
            return null;
        }
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
        ICCBased cs = lookupReference2ICCBased.get(ref);
        if (cs == null) {
            Object obj = getObject(ref);
            if (obj instanceof Stream) {
                Stream stream = (Stream) obj;
                cs = new ICCBased(this, stream);
                lookupReference2ICCBased.put(ref, cs);
            }
        }
        return cs;
    }

    public Resources getResources(Hashtable dictionaryEntries, String key) {
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
        } else if (ob instanceof Hashtable) {
            Hashtable ht = (Hashtable) ob;
            Resources resources = new Resources(this, ht);
            dictionaryEntries.put(key, resources);
            return resources;
        }
        return null;
    }

    public Resources getResources(Reference reference) {
        while (true) {
            Object ob = refs.get(reference);
            if (ob == null && m_LazyObjectLoader != null) {
                if (m_LazyObjectLoader.loadObject(reference)) {
                    ob = refs.get(reference);
                }
            }
            if (ob == null)
                return null;
            else if (ob instanceof Resources)
                return (Resources) ob;
            else if (ob instanceof Reference) {
                reference = (Reference) ob;
                continue;
            } else if (ob instanceof Hashtable) {
                Hashtable ht = (Hashtable) ob;
                Resources resources = new Resources(this, ht);
                addObject(resources, reference);
                return resources;
            }
            break;
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
        refs.put(objectReference, object);
    }

    /**
     * Creates a new instance of a Library.
     */
    public Library() {
        // set Catalog memory Manager and cache manager.
        memoryManager = MemoryManager.getInstance();
        cacheManager = new CacheManager();
    }

    /**
     * Gets the PDF object specified by the <code>key</code> in the dictionary
     * entries.  If the key value is a reference it is returned.
     *
     * @param dictionaryEntries the dictionary entries to look up the key in.
     * @param key               string value representing the dictionary key.
     * @return the Reference specified by the PDF key.  If the key is invalid
     *         or does not reference a Reference object, null is returned.
     * @see #getObject(java.util.Hashtable, String)
     */
    public Reference getObjectReference(Hashtable dictionaryEntries,
                                        String key) {
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
     *         otherwise.
     */
    public SecurityManager getSecurityManager() {
        return securityManager;
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
     * Gets the library cache manager.
     *
     * @return cache manager used by library and document.
     */
    public CacheManager getCacheManager() {
        return cacheManager;
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
     * Utility/demo functionality to clear all font and font descriptor
     * resources.  The library will refetch the font resources in question
     * when needed again.
     */
    public void disposeFontResources() {
        Enumeration<Reference> test = refs.keys();
        Reference ref;
        Object tmp;
        while (test.hasMoreElements()) {
            ref = test.nextElement();
            tmp = refs.get(ref);
            if (tmp instanceof Font ||
                    tmp instanceof FontDescriptor) {
                refs.remove(ref);
            }
        }
    }

    /**
     * Dispose the library's resources.
     */
    public void dispose() {
        if (memoryManager != null) {
            memoryManager.releaseAllByLibrary(this);
        }
        if (cacheManager != null) {
            cacheManager.dispose();
        }
        if (refs != null) {
            refs.clear();
        }
        if (lookupReference2ICCBased != null) {
            lookupReference2ICCBased.clear();
            lookupReference2ICCBased = null;
        }
        if (m_LazyObjectLoader != null) {
            m_LazyObjectLoader.dispose();
        }
    }
}
