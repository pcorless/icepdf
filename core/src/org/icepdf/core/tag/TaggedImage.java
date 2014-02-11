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
public class TaggedImage implements Serializable {
    private Reference ref;
    private boolean inlineImage;
    private List pages;
    private List tags;

    TaggedImage(Reference ref, boolean inlineImage) {
        this.ref = ref;
        this.inlineImage = inlineImage;
        this.pages = new ArrayList(2);
        this.tags = new ArrayList(6);
    }

    public Reference getReference() {
        return ref;
    }

    public boolean isInlineImage() {
        return inlineImage;
    }

    /**
     * If pages.size() == 1, then pages.get(0) is the page index, if pages.size() == 2,
     * then pages.get(0) is the inclusive starting page index and pages.get(1) is the
     * inclusive ending index. If there is an even size, then they are all index ranges,
     * and if there is an odd size, then the last entry is a page index by itself.
     *
     * @return List of page indexes in the document where the image is found
     */
    public List getPages() {
        return pages;
    }

    public String describePages() {
        StringBuilder sb = new StringBuilder(32);
        int sz = pages.size();
        for (int i = 0; i < sz; i++) {
            boolean oddIndex = ((i % 2) == 1);
            boolean closingRangeForSinglePage = false;
            if (oddIndex) {
                closingRangeForSinglePage = pages.get(i).equals(pages.get(i - 1));
                if (!closingRangeForSinglePage) {
                    sb.append('-');
                }
            } else if (i > 0) {
                sb.append(", ");
            }
            if (!closingRangeForSinglePage) {
                sb.append(pages.get(i).toString());
            }
        }
        return sb.toString();
    }

    public List getTags() {
        return tags;
    }

    /**
     * There is the assumption that we're processing pages in ascending sequence
     */
    void addPage(int pageIndex) {
        int size = pages.size();
        if (size == 0) {
            pages.add(new Integer(pageIndex));
        }
        // Even, so last entry is end of range
        else if ((size % 2) == 0) {
            Integer end = (Integer) pages.get(size - 1);
            // Continuing the existing sequential page range
            if (end.intValue() == (pageIndex - 1)) {
                pages.set(size - 1, new Integer(pageIndex));
            }
            // Starting a new page range
            else if (end.intValue() < (pageIndex - 1)) {
                pages.add(new Integer(pageIndex));
            }
        }
        // Odd, so last entry is start of range
        else {
            Integer begin = (Integer) pages.get(size - 1);
            // Continuing the existing sequential page range
            if (begin.intValue() == (pageIndex - 1)) {
                pages.add(new Integer(pageIndex));
            }
            // Starting a new page range, end the previous one first
            else if (begin.intValue() < (pageIndex - 1)) {
                pages.add(begin);
                pages.add(new Integer(pageIndex));
            }
        }
    }

    void tag(String tag) {
        if (!tags.contains(tag)) {
            tags.add(tag);
        }
    }

    String describe() {
        StringBuilder sb = new StringBuilder(4096);
        for (Iterator tgs = tags.iterator(); tgs.hasNext(); ) {
            String t = (String) tgs.next();
            sb.append(t);
            sb.append("\n");
        }
        return sb.toString();
    }
}
