/*
 * Copyright 2006-2014 ICEsoft Technologies Inc.
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
package org.icepdf.core.tag;

import org.icepdf.core.pobjects.Reference;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

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
        if (ti == null) {
            ti = new TaggedImage(ref, inlineImage);
            allImages.add(ti);
        }
        currentImages.add(ti);
        ti.addPage(currentPageIndex);
    }

    private TaggedImage findImage(Reference ref) {
        if (ref == null)
            return null;
        for (int i = allImages.size() - 1; i >= 0; i--) {
            TaggedImage ti = (TaggedImage) allImages.get(i);
            if (ti.getReference() != null && ti.getReference().equals(ref))
                return ti;
        }
        return null;
    }

    void endImage(Reference ref) {
        currentImages.remove(currentImages.size() - 1);
    }

    void tagImage(String tag) {
        TaggedImage ti = (TaggedImage) currentImages.get(currentImages.size() - 1);
        ti.tag(tag);
    }

    String describe() {
        StringBuilder sb = new StringBuilder(4096);
        sb.append("ORIGIN: ");
        sb.append(origin);
        sb.append("\n");
        for (Iterator imgs = allImages.iterator(); imgs.hasNext(); ) {
            TaggedImage ti = (TaggedImage) imgs.next();
            sb.append(ti.describe());
            sb.append("---------------------------------\n");
        }
        return sb.toString();
    }
}
