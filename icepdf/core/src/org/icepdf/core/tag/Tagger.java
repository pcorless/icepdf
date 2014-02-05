/*
 * Copyright 2006-2013 ICEsoft Technologies Inc.
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

import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.Reference;
import org.icepdf.core.util.Defs;

/**
 * @author mcollette
 * @since 4.0
 */
public class Tagger {
    public static final boolean
            tagging = property("ice.tag.tagging");

    private static boolean property(String name) {
        String value = null;
        try {
            value = Defs.sysProperty(name);
        } catch (RuntimeException ex) {
        }
        if (value != null) {
            return value.equals("yes") || value.equals("true");
        }
        return false;
    }

    private static TagState state = new TagState();

    public static void setCurrentDocument(Document doc) {
        state.setCurrentDocument(doc);
//System.out.println("Tagger.setCurrentDocument()  " + doc.getDocumentOrigin());
    }

    public static void setCurrentPageIndex(int currentPageIndex) {
        state.setCurrentPageIndex(currentPageIndex);
//System.out.println("Tagger.setCurrentPageIndex()  " + currentPageIndex);
    }

    public static void beginImage(Reference ref, boolean inlineImage) {
        state.beginImage(ref, inlineImage);
//System.out.println("Tagger.beginImage()  ref: " + ref.toString() + "  inlineImage: " + inlineImage);
    }

    public static void endImage(Reference ref) {
        state.endImage(ref);
//System.out.println("Tagger.endImage()  ref: " + ref.toString());
    }

    public static void tagImage(String tag) {
        state.tagImage(tag);
//System.out.println("Tagger.tagImage()  " + tag);
    }

    public static String describe() {
        return state.describe();
    }

    public static TagState getTagState() {
        return state;
    }
}
