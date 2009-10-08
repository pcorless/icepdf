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
package org.icepdf.core.pobjects;

import org.icepdf.core.pobjects.actions.Action;
import org.icepdf.core.pobjects.fonts.ofont.Encoding;
import org.icepdf.core.util.Library;

import java.util.Hashtable;
import java.util.Vector;

/**
 * <p>The <code>OutlineItem</code> represents the individual outline item within
 * the hierarchy defined by an <code>Outlines</code> class.  The outlines items
 * are chained together through their <b>Prev</b> and <b>Next</b> entries. Each
 * outline item has a title and a destination which can be accessed by the
 * visual representation to create a function document outlines (sometimes
 * called bookmarks).</p>
 * <p/>
 * <p>This class is used mainly by the Outlines class to build the outline
 * hierarchy.  The root node of the outline hierarchy can be accessed through
 * the Outlines class. </p>
 *
 * @see org.icepdf.ri.common.OutlineItemTreeNode
 * @see org.icepdf.core.pobjects.Outlines
 * @since 2.0
 */
public class OutlineItem extends Dictionary {

    // The text to be displayed on the screen for this item.
    private String title;

    // The destination to be displayed when this item is activated
    private Destination dest;

    // The action to be performed when this item is activated
    private Action action;

    // The parent of this item in the outline hierarchy. The parent of a
    // top-level item is the outline dictionary itself.
    private Reference parent;

    // The previous item at this outline level.
    private Reference prev;

    // The next item at this outline level.
    private Reference next;

    // An outline item dictionary representing the first top-level item
    // in the outline.
    private Reference first;

    // An outline item dictionary representing the last top-level item
    // in the outline.
    private Reference last;

    // The total number of open items at all levels of the outline. This
    // entry should be omitted if there are no open outline items.
    private int count = -1;

    private boolean loadedSubItems;

    private Vector<OutlineItem> subItems;


    /**
     * Creates a new instance of an OutlineItem.
     *
     * @param l document library.
     * @param h OutlineItem dictionary entries.
     */
    public OutlineItem(Library l, Hashtable h) {
        super(l, h);
        loadedSubItems = false;
        subItems = new Vector<OutlineItem>(Math.max(Math.abs(getCount()), 16));
    }

    /**
     * Indicates if the Outline item is empty.  An outline item is empty if
     * it has no title, destination and action dictionary entries.
     *
     * @return true, if the outline entry is empty; false, otherwise.
     */
    public boolean isEmpty() {
        return getTitle() == null && getDest() == null && getAction() == null;
    }

    /**
     * Gets the number of descendants that would appear under this outline item.
     *
     * @return descendant count.
     */
    public int getSubItemCount() {
        ensureSubItemsLoaded();
        if (subItems != null)
            return subItems.size();
        else
            return 0;
    }

    /**
     * Gets the child outline item specified by the index.  All children of the
     * outline items are ordered and numbered.
     *
     * @param index child index number of desired outline item.
     * @return outline specified by index.
     */
    public OutlineItem getSubItem(int index) {
        ensureSubItemsLoaded();
        return subItems.get(index);
    }

    /**
     * Gets the action associated with this OutlineItem.
     *
     * @return the associated action; null, if there is no action.
     */
    public Action getAction() {
        // grab the action attribute
        if (action == null) {
            Object obj = library.getObject(entries, "A");
            if (obj instanceof Hashtable) {
                action = new org.icepdf.core.pobjects.actions.Action(library, (Hashtable) obj);
            }
        }
        return action;
    }

    /**
     * Gets a reference to an outline item dictionary representing the first
     * top-level item in the outline.
     *
     * @return reference to first top-level item
     */
    public Reference getFirst() {
        if (first == null) {
            Object attribute = entries.get("First");
            if (attribute instanceof Reference) {
                first = (Reference) attribute;
            }
        }
        return first;
    }

    /**
     * Gets a reference to an outline item dictionary representing the last
     * top-level item in the outline.
     *
     * @return reference to last top-level item
     */
    public Reference getLast() {
        if (last == null) {
            Object attribute = entries.get("Last");
            if (attribute instanceof Reference) {
                last = (Reference) attribute;
            }
        }
        return last;
    }

    /**
     * Gets the next outline item at this outline level.
     *
     * @return next item at this outline level.
     */
    public Reference getNext() {
        if (next == null) {
            Object attribute = entries.get("Next");
            if (attribute instanceof Reference) {
                next = (Reference) attribute;
            }
        }
        return next;
    }

    /**
     * Gets the previous outline item at this outline level.
     *
     * @return previous item at this outline level.
     */
    public Reference getPrev() {
        if (prev == null) {
            Object attribute = entries.get("Prev");
            if (attribute instanceof Reference) {
                prev = (Reference) attribute;
            }
        }
        return prev;
    }

    /**
     * Gets the parent of this outline item in the outline hierarchy.  The
     * parent of a top-level item is the outline dictionary itself.
     *
     * @return parent of this item.
     */
    public Reference getParent() {
        if (parent == null) {
            Object attribute = entries.get("Parent");
            if (attribute instanceof Reference) {
                parent = (Reference) attribute;
            }
        }
        return parent;
    }

    /**
     * Gets the number of descendants that would appear under this outline item.
     *
     * @return descendant count.
     */
    private int getCount() {
        if (count < 0) {
            // grab the count attribute
            count = library.getInt(entries, "Count");
        }
        return count;
    }

    /**
     * Gets the text to be displayed on the screen for this item.
     *
     * @return text to be displayed
     */
    public String getTitle() {

        /*
         * Some bizarre code that we have no idea what is for
         * Written by those crazy Italians

        if (title != null && title.indexOf(13) != -1) {
            StringBuffer sb = new StringBuffer();
            sb.append("<html>");
            StringTokenizer stk = new StringTokenizer(title, "\n\r");
            while (stk.hasMoreTokens()) {
                String s = stk.nextToken();
                sb.append("<p>" + s + "</p>");
            }
            sb.append("</html>");
            title = sb.toString();
        }
        */
        if (title == null) {
            // get title String for outline entry
            Object obj = library.getObject(entries, "Title");
            if (obj instanceof StringObject) {
                StringObject outlineText = (StringObject) obj;
                String titleText = outlineText.getDecryptedLiteralString(library.securityManager);
                // If the title begins with 254 and 255 we are working with
                // Octal encoded strings. Check first to make sure that the
                // title string is not null, or is at least of length 2.
                if (titleText != null && titleText.length() >= 2 &&
                        ((int) titleText.charAt(0)) == 254 &&
                        ((int) titleText.charAt(1)) == 255) {

                    StringBuffer sb1 = new StringBuffer();

                    // strip and white space, as the will offset the below algorithm
                    // which assumes the string is made up of two byte chars.
                    String hexTmp = "";
                    for (int i = 0; i < titleText.length(); i++) {
                        char c = titleText.charAt(i);
                        if (!((c == '\t') || (c == '\r') || (c == '\n'))) {
                            hexTmp = hexTmp + titleText.charAt(i);
                        }
                    }
                    byte title1[] = hexTmp.getBytes();

                    for (int i = 2; i < title1.length; i += 2) {
                        try {
                            int b1 = ((int) title1[i]) & 0xFF;
                            int b2 = ((int) title1[i + 1]) & 0xFF;
                            //System.err.println(b1 + " " + b2);
                            sb1.append((char) (b1 * 256 + b2));
                        } catch (Exception ex) {
                            // intentionally left blank.
                        }
                    }
                    title = sb1.toString();
                } else if (titleText != null) {
                    StringBuffer sb = new StringBuffer();
                    Encoding enc = Encoding.getPDFDoc();
                    for (int i = 0; i < titleText.length(); i++) {
                        sb.append(enc.get(titleText.charAt(i)));
                    }
                    title = sb.toString();
                }
            }
        }
        return title;
    }

    /**
     * Gets the destination to be displayed when this item is activated.
     *
     * @return destination to be displayed.
     */
    public Destination getDest() {
        if (dest == null) {
            // grab the Destination attribute
            Object obj = library.getObject(entries, "Dest");
            if (obj != null) {
                dest = new Destination(library, obj);
            }
        }
        return dest;
    }

    /**
     * Utility method for loading all children of this outline.  The main purpose
     * of this is to make sure the count is accurate.
     */
    private void ensureSubItemsLoaded() {
        if (loadedSubItems)
            return;

        loadedSubItems = true;

        if (getFirst() != null) {
            // get first child
            Reference nextReference = getFirst();
            Reference oldNextReference;
            OutlineItem outLineItem;
            Hashtable dictionary;
            // iterate through children and see if then have children. 
            while (nextReference != null) {

                // result the outline dictionary
                dictionary = (Hashtable) library.getObject(nextReference);
                if (dictionary == null) {
                    break;
                }
                // create the new outline
                outLineItem = new OutlineItem(library, dictionary);

                // add the new item to the list of children
                subItems.add(outLineItem);

                // old reference is kept to make sure we don't iterate out
                // of control on a circular reference.
                oldNextReference = nextReference;
                nextReference = outLineItem.getNext();

                // make sure we haven't already added this node, some
                // Outlines terminate with next being a pointer to itself.
                if (oldNextReference.equals(nextReference)) {
                    break;
                }
            }
        }
    }
}
