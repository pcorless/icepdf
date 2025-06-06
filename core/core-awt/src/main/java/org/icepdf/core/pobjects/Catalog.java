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

import org.icepdf.core.pobjects.acroform.InteractiveForm;
import org.icepdf.core.util.Library;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * <p>The <code>Catalog</code> object represents the root of a PDF document's
 * object heirarchy.  The <code>Catalog</code> is located by means of the
 * <b>Root</b> entry in the trailer of the PDF file.  The catalog contains
 * references to other objects defining the document's contents, outline, names,
 * destinations, and other attributes.</p>
 * <br>
 * <p>The <code>Catalog</code> class can be accessed from the {@link Document}
 * class for convenience, but can also be accessed via the {@link PTrailer} class.
 * Useful information about the document can be extracted from the Catalog
 * Dictionary, such as PDF version information and Viewer Preferences.  All
 * Catalog dictionary properties can be accessed via the getEntries method.
 * See section 3.6.1 of the PDF Reference version 1.6 for more information on
 * the properties available in the Catalog Object. </p>
 *
 * @since 1.0
 */
public class Catalog extends Dictionary {

    private static final Logger logger =
            Logger.getLogger(Catalog.class.toString());

    public static final Name TYPE = new Name("Catalog");
    public static final Name DESTS_KEY = new Name("Dests");
    public static final Name VIEWERPREFERENCES_KEY = new Name("ViewerPreferences");
    public static final Name NAMES_KEY = new Name("Names");
    public static final Name OUTLINES_KEY = new Name("Outlines");
    public static final Name OCPROPERTIES_KEY = new Name("OCProperties");
    public static final Name PAGES_KEY = new Name("Pages");
    public static final Name PAGELAYOUT_KEY = new Name("PageLayout");
    public static final Name PAGEMODE_KEY = new Name("PageMode");
    public static final Name ACRO_FORM_KEY = new Name("AcroForm");
    public static final Name COLLECTION_KEY = new Name("Collection");
    public static final Name METADATA_KEY = new Name("Metadata");
    public static final Name PERMS_KEY = new Name("Perms");

    public static final Name PAGE_MODE_USE_NONE_VALUE = new Name("UseNone");
    public static final Name PAGE_MODE_USE_OUTLINES_VALUE = new Name("UseOutlines");
    public static final Name PAGE_MODE_USE_THUMBS_VALUE = new Name("UseThumbs");
    public static final Name PAGE_MODE_FULL_SCREEN_VALUE = new Name("FullScreen");
    public static final Name PAGE_MODE_OPTIONAL_CONTENT_VALUE = new Name("UseOC");
    public static final Name PAGE_MODE_USE_ATTACHMENTS_VALUE = new Name("UseAttachments");

    private PageTree pageTree;
    private Outlines outlines;
    private Names names;
    private OptionalContent optionalContent;
    private NamedDestinations dests;
    private ViewerPreferences viewerPref;
    private InteractiveForm interactiveForm;

    private volatile boolean outlinesInited = false;
    private final boolean namesTreeInited = false;
    private volatile boolean destsInited = false;
    private volatile boolean viewerPrefInited = false;
    private volatile boolean optionalContentInited = false;

    // Announce ICEpdf Core
    static {
        logger.log(Level.INFO, () -> "ICEpdf Core " + Document.getLibraryVersion());
    }

    public Catalog(Library library, DictionaryEntries dictionaryEntries) {
        super(library, dictionaryEntries);
    }

    /**
     * Initiate the PageTree.
     */
    public synchronized void init() throws InterruptedException {
        Object tmp = library.getObject(entries, PAGES_KEY);
        pageTree = null;
        if (tmp instanceof PageTree) {
            pageTree = (PageTree) tmp;
        }
        // malformed core corner case, pages must not be references, but we
        // have a couple cases that break the spec.
        else if (tmp instanceof DictionaryEntries) {
            pageTree = new PageTree(library, (DictionaryEntries) tmp);
        }
        // malformed corner case, just have a page object, instead of tree.
        else if (tmp instanceof Page) {
            Page tmpPage = (Page) tmp;
            DictionaryEntries tmpPages = new DictionaryEntries();
            List<Reference> kids = new ArrayList<>();
            kids.add(tmpPage.getPObjectReference());
            tmpPages.put(PageTree.KIDS_KEY, kids);
            tmpPages.put(PageTree.COUNT_KEY, 1);
            pageTree = new PageTree(library, tmpPages);
        }

        // let any exception bubble up.
        if (pageTree != null) {
            pageTree.init();
        }

        // check for the collections dictionary for the presence of a portable collection
        tmp = library.getObject(entries, NAMES_KEY);
        if (tmp != null) {
            names = new Names(library, (DictionaryEntries) tmp);
            names.init();
            if (entries.get(NAMES_KEY) instanceof Reference) {
                names.setPObjectReference((Reference) entries.get(NAMES_KEY));
            }
        }

        // load the Acroform data.
        tmp = library.getObject(entries, ACRO_FORM_KEY);
        if (tmp instanceof DictionaryEntries) {
            interactiveForm = new InteractiveForm(library, (DictionaryEntries) tmp);
            interactiveForm.init();
        }
        // todo namesTree contains forms javascript, might need to be initialized here

    }

    /**
     * Gets PageTree node that is the root of the document's page tree.
     * The PageTree can be traversed to access child PageTree and Page objects.
     *
     * @return Catalogs PageTree.
     * {@link Page}
     */
    public PageTree getPageTree() {
        return pageTree;
    }

    /**
     * Gets the Outlines Dictionary that is the root of the document's outline
     * hierarchy. The Outline can be traversed to access child OutlineItems.
     *
     * @return Outlines object if one exists; null, otherwise.
     * {@link OutlineItem}
     */
    public Outlines getOutlines() {
        synchronized (this) {
            if (!outlinesInited) {
                Reference ref = library.getReference(entries, OUTLINES_KEY);
                if (ref != null) {
                    PObject o = library.getPObject(ref);
                    if (o != null) {
                        outlines = new Outlines(library, (DictionaryEntries) o.getObject());
                        outlines.setPObjectReference(o.getReference());
                    }
                }
                outlinesInited = true;
            }
        }
        return outlines;
    }

    /**
     * Creates a new Outlines object and sets the root outline item.
     *
     * @param outline root outline item.
     * @throws InterruptedException
     */
    public void createOutlines(OutlineItem outline) throws InterruptedException {
        if (outlines != null) {
            throw new IllegalStateException("Outlines already exist");
        }
        DictionaryEntries outlinesDictionary = new DictionaryEntries();
        outlinesDictionary.put(Outlines.TYPE_KEY, OUTLINES_KEY);
        outlinesDictionary.put(Outlines.COUNT_KEY, 1);
        outlinesDictionary.put(OutlineItem.FIRST_KEY, outline.getPObjectReference());
        outlinesDictionary.put(OutlineItem.LAST_KEY, outline.getPObjectReference());
        outlines = new Outlines(library, outlinesDictionary);
        outlines.init();
        outlines.setPObjectReference(library.getStateManager().getNewReferenceNumber());
        entries.put(OUTLINES_KEY, outlines.getPObjectReference());
        outline.setParent(outlines.getPObjectReference());

        library.getStateManager().addChange(new PObject(this, getPObjectReference()));
        library.getStateManager().addChange(new PObject(outlines, outlines.getPObjectReference()));
        outlinesInited = true;
    }

    /**
     * Adds a destination to the names tree of a document.  If no names exist a new tree is created and attached
     * to the document catalogue.  State manager is updated appropriately to allow the new state to be saved.
     *
     * @param name        name of name tree insert label
     * @param destination associated destination
     * @return true if the addition was successful.
     */
    public boolean addNamedDestination(String name, Destination destination) {
        // make sure we have s structure to work with
        StateManager stateManager = library.getStateManager();
        if (names == null) {
            names = new Names(library, new DictionaryEntries());
            // add the object to the catalog
            names.setPObjectReference(stateManager.getNewReferenceNumber());
            entries.put(NAMES_KEY, names.getPObjectReference());
            // add the catalog and the new destination object.
            stateManager.addChange(new PObject(this, getPObjectReference()));
            stateManager.addChange(new PObject(names, names.getPObjectReference()));
        }
        if (names.getDestsNameTree() == null) {
            // create a the name tree.
            NameTree destsNameTree = new NameTree(library, new DictionaryEntries());
            destsNameTree.init();
            destsNameTree.setPObjectReference(stateManager.getNewReferenceNumber());
            names.setDestsNameTree(destsNameTree);
            stateManager.addChange(new PObject(names, names.getPObjectReference()));
            stateManager.addChange(new PObject(destsNameTree, destsNameTree.getPObjectReference()));
        }
        NameTree nameTree = getNames().getDestsNameTree();
        return nameTree.addNameNode(name, destination);
    }

    /**
     * Updates an existing name in the name tree replacing the name title and destination.
     *
     * @param oldName     old name before change
     * @param newName     name of node
     * @param destination new destination value
     * @return true if the update was successful
     */
    public boolean updateNamedDestination(String oldName, String newName, Destination destination) {
        NameTree nameTree = getNames().getDestsNameTree();
        return nameTree.updateNameNode(oldName, newName, destination);
    }

    /**
     * Remove a node in the name tree with the specified name
     *
     * @param name name of name tree node to remove.
     * @return true if deletion was successful.
     */
    public boolean deleteNamedDestination(String name) {
        // we're deleting names, so we assume we always have valid name tree.
        NameTree nameTree = getNames().getDestsNameTree();
        return nameTree.deleteNode(name);
    }


    /**
     * A collection dictionary that a conforming reader shall use to enhance the presentation of file attachments
     * stored in the PDF document.
     *
     * @return collection dictionary.
     */
    public DictionaryEntries getCollection() {
        return library.getDictionary(entries, COLLECTION_KEY);
    }

    /**
     * A name object specifying how the document shall be displayed when opened:
     *
     * @return one of the PageMode value contants,  default is Default value: UseNone.
     */
    public Name getPageMode() {
        Name name = library.getName(entries, PAGEMODE_KEY);
        return Objects.requireNonNullElse(name, PAGE_MODE_USE_NONE_VALUE);
    }

    /**
     * Gets the document's Names dictionary.  The Names dictionary contains
     * a category of objects in a PDF file which can be referred to by name
     * rather than by object reference.
     *
     * @return names object entry.  If no names entries exists null
     * is returned.
     */
    public Names getNames() {
        return names;
    }

    /**
     * Gets the Names object's embedded files name tree if present.  The root node is also check to make sure
     * the tree has values.
     *
     * @return A name tree mapping name strings to file specifications for embedded
     * file streams.
     */
    public NameTree getEmbeddedFilesNameTree() {
        if (names != null) {
            NameTree nameTree = names.getEmbeddedFilesNameTree();
            if (nameTree != null && nameTree.getRoot() != null) {
                return nameTree;
            }
        }
        return null;
    }


    /**
     * Gets a dictionary of names and corresponding destinations.
     *
     * @return A Dictionary of Destinations; if none, null is returned.
     */
    public NamedDestinations getDestinations() {
        synchronized (this) {
            if (!destsInited) {
                Object o = library.getObject(entries, DESTS_KEY);
                if (o instanceof DictionaryEntries) {
                    dests = new NamedDestinations(library, (DictionaryEntries) o);
                }
                destsInited = true;
            }
        }
        return dests;
    }

    /**
     * Gets a dictionary of keys and corresponding viewer preferences
     * This can be used to pull information based on the PDF specification,
     * such as HideToolbar or FitWindow
     *
     * @return the constructed ViewerPreferences object
     */
    public ViewerPreferences getViewerPreferences() {
        synchronized (this) {
            if (!viewerPrefInited) {
                Object o = library.getObject(entries, VIEWERPREFERENCES_KEY);
                if (o != null) {
                    if (o instanceof DictionaryEntries) {
                        viewerPref = new ViewerPreferences(library, (DictionaryEntries) o);
                        viewerPref.init();
                    } // strange corner case where there is a incorrect reference.
                    else if (o instanceof Catalog) {
                        viewerPref = new ViewerPreferences(library, ((Catalog) o).getEntries());
                        viewerPref.init();
                    }
                }
                viewerPrefInited = true;
            }
        }
        return viewerPref;
    }

    /**
     * Gets the optional content properties dictionary if present.
     *
     * @return OptionalContent dictionary, null if none exists.
     */
    public OptionalContent getOptionalContent() {
        synchronized (this) {
            if (!optionalContentInited) {
                Object o = library.getObject(entries, OCPROPERTIES_KEY);
                if (o instanceof DictionaryEntries) {
                    optionalContent = new OptionalContent(library, (DictionaryEntries) o);
                    optionalContent.init();
                } else {
                    optionalContent = new OptionalContent(library, new DictionaryEntries());
                    optionalContent.init();
                }
                optionalContentInited = true;
            }
        }
        return optionalContent;
    }

    /**
     * A metadata stream that shall contain metadata for the document.  To
     * access the metadata stream data make a call to getMetData().getDecodedStreamBytes()
     * which can be used to create a String or open an InputStream.
     *
     * @return metadata stream if define,  otherwise null.
     */
    public Stream getMetaData() {
        Object o = library.getObject(entries, METADATA_KEY);
        if (o instanceof Stream) {
            return (Stream) o;
        }
        return null;
    }

    /**
     * Gets the permissions of the catalog if present. Perms key.
     *
     * @return permissions if present, otherwise false.
     */
    public Permissions getPermissions() {
        DictionaryEntries entries = library.getDictionary(this.entries, PERMS_KEY);
        if (entries != null) {
            return new Permissions(library, entries);
        } else {
            return null;
        }
    }

    /**
     * Gets the interactive form object that contains the form widgets for the given PDF.
     *
     * @return interactive form object,  null if no forms are pressent.
     */
    public InteractiveForm getInteractiveForm() {
        return interactiveForm;
    }

    /**
     * Gets the interactive form object that contains the form widgets for the given PDF.  This method should be
     * called before adding new widgets.
     *
     * @return The interactive form object if it exists, if null a new dictionary is inserted into the document.
     */
    public InteractiveForm getOrCreateInteractiveForm() {
        if (interactiveForm == null) {
            interactiveForm = new InteractiveForm(library, new DictionaryEntries());
            StateManager stateManager = library.getStateManager();
            this.entries.put(ACRO_FORM_KEY, interactiveForm);
            stateManager.addChange(new PObject(this, this.getPObjectReference()));
            return interactiveForm;
        }
        return interactiveForm;
    }

    /**
     * Returns a summary of the Catalog dictionary values.
     *
     * @return dictionary values.
     */
    public String toString() {
        return "CATALOG= " + entries.toString();
    }
}
