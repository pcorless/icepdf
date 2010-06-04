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
package org.icepdf.core.tag;

import org.icepdf.core.pobjects.Reference;

import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Iterator;
import java.io.Serializable;

/**
 * @author mcollette
 * @since 4.0
 */
public class TaggedDocument implements Serializable {
    private String origin;
    private int currentPageIndex;
    private List allImages;
    private transient List currentImages;

    TaggedDocument(String origin) {
        this.origin = origin;
        this.allImages = new ArrayList(32);
        this.currentImages = new ArrayList(4);
    }

    public String getOrigin() {
        return origin;
    }

    public List getImages() {
        return allImages;
    }

    void setCurrentPageIndex(int currentPageIndex) {
        this.currentPageIndex = currentPageIndex;
    }

    void beginImage(Reference ref, boolean inlineImage) {
        TaggedImage ti = findImage(ref);
        if(ti == null) {
            ti = new TaggedImage(ref, inlineImage);
            allImages.add(ti);
        }
        currentImages.add(ti);
        ti.addPage(currentPageIndex);
    }

    private TaggedImage findImage(Reference ref) {
        if (ref == null)
            return null;
        for (int i = allImages.size()-1; i >= 0; i--) {
            TaggedImage ti = (TaggedImage) allImages.get(i);
            if (ti.getReference() != null && ti.getReference().equals(ref))
                return ti;
        }
        return null;
    }

    void endImage(Reference ref) {
        currentImages.remove(currentImages.size()-1);
    }

    void tagImage(String tag) {
        TaggedImage ti = (TaggedImage) currentImages.get(currentImages.size()-1);
        ti.tag(tag);
    }

    String describe() {
        StringBuilder sb = new StringBuilder(4096);
        sb.append("ORIGIN: ");
        sb.append(origin);
        sb.append("\n");
        for(Iterator imgs = allImages.iterator(); imgs.hasNext();) {
            TaggedImage ti = (TaggedImage) imgs.next();
            sb.append(ti.describe());
            sb.append("---------------------------------\n");
        }
        return sb.toString();
    }
}
