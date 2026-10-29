/*
 * Copyright 2026 Patrick Corless
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.icepdf.core.util.updater.modifiers;

import org.icepdf.core.pobjects.*;
import org.icepdf.core.util.Library;

import java.util.ArrayList;
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
    private final Catalog catalog;
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
            // xObjects
            Object resources = library.getObject(page.getEntries(), RESOURCES_KEY);
            if (resources instanceof DictionaryEntries) {
                removeDictionaryEntries((DictionaryEntries) resources, XOBJECT_KEY, stateManager);
            }
            // properties
        }
    }

    private void removeDictionaryEntries(DictionaryEntries dictionary, Name key, StateManager stateManager) {
        Object entries = dictionary.get(key);

        // if a stream process it as needed
        if (entries instanceof Reference) {
            Reference reference = (Reference) entries;
            stateManager.addDeletion(reference);
        }
        // if a vector, process it as needed
        else if (entries instanceof List) {
            List<?> references = (List<?>) entries;
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

    /**
     * Removes the page from the node that lists it and takes one off /Count there and at every ancestor, so the
     * saved tree is consistent.  /Kids is replaced rather than edited: the node's last init() captured the list,
     * and render threads walk it without a lock.  Each changed node is reset so the open document's tree is
     * re-read on its next lookup.
     *
     * @return true if the page was found under this node, and removed.
     */
    private boolean findAndRemovePageTreeReference(PageTree pageTree, Reference pageReference) {
        DictionaryEntries entries = pageTree.getEntries();
        Object kidsObject = library.getObject(entries, KIDS_KEY);
        if (!(kidsObject instanceof List)) {
            return false;
        }
        List<?> kidsReferences = (List<?>) kidsObject;
        int index = kidsReferences.indexOf(pageReference);
        if (index >= 0) {
            List<Object> remaining = new ArrayList<>(kidsReferences);
            remaining.remove(index);
            entries.put(KIDS_KEY, remaining);
            stateManager.addDeletion(pageReference);
        } else if (!removeFromKid(kidsReferences, pageReference)) {
            return false;
        }
        Number count = library.getNumber(entries, COUNT_KEY);
        entries.put(COUNT_KEY, Math.max(0, (count != null ? count.intValue() : 0) - 1));
        stateManager.addChange(new PObject(pageTree, pageTree.getPObjectReference()));
        pageTree.resetInitializedState();
        return true;
    }

    private boolean removeFromKid(List<?> kidsReferences, Reference pageReference) {
        for (Object ref : kidsReferences) {
            if (ref instanceof Reference) {
                Object kid = library.getObject((Reference) ref);
                if (kid instanceof PageTree && findAndRemovePageTreeReference((PageTree) kid, pageReference)) {
                    return true;
                }
            }
        }
        return false;
    }
}
