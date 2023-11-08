package org.icepdf.core.util.updater.modifiers;

import org.icepdf.core.pobjects.*;
import org.icepdf.core.util.Library;

import java.util.List;

import static org.icepdf.core.pobjects.Page.CONTENTS_KEY;
import static org.icepdf.core.pobjects.Page.RESOURCES_KEY;
import static org.icepdf.core.pobjects.PageTree.COUNT_KEY;
import static org.icepdf.core.pobjects.PageTree.KIDS_KEY;
import static org.icepdf.core.pobjects.Resources.XOBJECT_KEY;

/**
 * Removes a page and all of direct resources.  Fonts are not touched as they can be shared but images
 * are removed.
 *
 * @since 7.2
 */
public class PageRemovalModifier implements Modifier<Page> {

    private Library library;
    private Catalog catalog;
    private StateManager stateManager;

    public PageRemovalModifier(Object parent) {
        this.catalog = (Catalog) parent;
    }

    @Override
    public void modify(Page page) {
        library = page.getLibrary();
        stateManager = library.getStateManager();
        Reference pageReference = page.getPObjectReference();

        // remove page tree
        PageTree pageTree = catalog.getPageTree();
        if (findAndRemovePageTreeReference(pageTree, pageReference)) {
            // remove related resources
            // contents
            removeDictionaryEntries(page.getEntries(), CONTENTS_KEY, stateManager);
            // xobjects
            DictionaryEntries entries = (DictionaryEntries) page.getEntries().get(RESOURCES_KEY);
            removeDictionaryEntries(entries, XOBJECT_KEY, stateManager);
            // properties
        }
    }

    // todo start building out utility class.
    private void removeDictionaryEntries(DictionaryEntries dictionary, Name key, StateManager stateManager) {
        Object entries = dictionary.get(key);

        // if a stream process it as needed
        if (entries instanceof Reference) {
            Reference reference = (Reference) entries;
            stateManager.addDeletion(reference);
        }
        // if a vector, process it as needed
        else if (entries instanceof List) {
            List references = (List) entries;
            for (Object cont : references) {
                Object tmp = library.getObject(cont);
                if (tmp instanceof Stream) {
                    Stream tmpStream = (Stream) tmp;
                    Reference reference = tmpStream.getPObjectReference();
                    stateManager.addDeletion(new PObject(tmpStream, reference));
                }
            }
        } else if (entries instanceof DictionaryEntries) {
            DictionaryEntries dictionaryEntries = (DictionaryEntries) entries;
            dictionaryEntries.values().forEach(o -> {
                if (o instanceof Reference) {
                    Reference reference = (Reference) o;
                    stateManager.addDeletion(reference);
                }
            });
        }
    }

    private boolean findAndRemovePageTreeReference(PageTree pageTree, Reference pageReference) {
        // work with raw dictionary entries as we don't want to initialize if we don't have to.
        List kidsReferences = (List) pageTree.getObject(KIDS_KEY);
        boolean found = false;
        for (Object kid : kidsReferences) {
            // quick check for an easy find
            if (pageReference.equals(kid)) {
                removePageTreeReference(pageTree, kidsReferences, kid);
                found = true;
                break;
            }
        }
        if (!found) {
            // need to resolve each reference and dive into each page tree.
            for (Object ref : kidsReferences) {
                Object kid = library.getObject((Reference) ref);
                if (kid instanceof PageTree) {
                    found = findAndRemovePageTreeReference((PageTree) kid, pageReference);
                    if (found) {
                        removePageTreeReference(pageTree, kidsReferences, kid);
                        return true;
                    }
                }
            }
        }
        return found;
    }

    // remove and push the dictionary to the StateManager.
    private void removePageTreeReference(PageTree pageTree, List kidsReferences, Object kid) {
        DictionaryEntries dictionaryEntries = pageTree.getEntries();
        kidsReferences.remove(kid);
        dictionaryEntries.put(KIDS_KEY, kidsReferences);
        dictionaryEntries.put(COUNT_KEY, kidsReferences.size());
        stateManager.addChange(new PObject(pageTree, pageTree.getPObjectReference()));
        if (kid instanceof Reference) {
            stateManager.addDeletion((Reference) kid);
        }
    }
}
