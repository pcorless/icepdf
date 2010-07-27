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
 * The Original Code is ICEpdf 4.1 open source software code, released
 * May 1st, 2009. The Initial Developer of the Original Code is ICEsoft
 * Technologies Canada, Corp. Portions created by ICEsoft are Copyright (C)
 * 2004-2010 ICEsoft Technologies Canada, Corp. All Rights Reserved.
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

import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.Reference;

import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Enumeration;
import java.util.Properties;
import java.util.Iterator;
import java.io.Serializable;

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
        }
        else {
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
        for(Iterator docs = documents.iterator(); docs.hasNext();) {
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
