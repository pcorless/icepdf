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

import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.Reference;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

/**
 * @author mcollette
 * @since 4.0
 */
public class TagState implements Serializable {
    private static final long serialVersionUID = 1511020842437155686L;

    private List documents;
    private transient HashMap origin2document;
    private transient TaggedDocument currentDocument;

    TagState() {
        documents = new ArrayList(128);
        origin2document = new HashMap(128);
        currentDocument = null;

        /*
        Properties props = System.getProperties();
        Enumeration keys = props.keys();
        while (keys.hasMoreElements()) {
            Object key = keys.nextElement();
            Object value = props.get(key);
            System.out.println("'" + key + "' -> '" + value + "'");
        }
        */
    }

    void setCurrentDocument(Document doc) {
        String origin = doc.getDocumentOrigin();
        TaggedDocument td = (TaggedDocument) origin2document.get(origin);
        if (td != null) {
            currentDocument = td;
        } else {
            currentDocument = new TaggedDocument(origin);
            documents.add(currentDocument);
        }
    }

    void setCurrentPageIndex(int currentPageIndex) {
        if (currentDocument != null)
            currentDocument.setCurrentPageIndex(currentPageIndex);
    }

    void beginImage(Reference ref, boolean inlineImage) {
        if (currentDocument != null)
            currentDocument.beginImage(ref, inlineImage);
    }

    void endImage(Reference ref) {
        if (currentDocument != null)
            currentDocument.endImage(ref);
    }

    void tagImage(String tag) {
        if (currentDocument != null)
            currentDocument.tagImage(tag);
    }

    String describe() {
        StringBuilder sb = new StringBuilder(4096);
        for (Iterator docs = documents.iterator(); docs.hasNext(); ) {
            TaggedDocument td = (TaggedDocument) docs.next();
            sb.append(td.describe());
            sb.append("=================================\n");
        }
        return sb.toString();
    }

    public List getDocuments() {
        return documents;
    }
}
