package org.icepdf.core.pobjects;

import org.icepdf.core.util.Library;

import java.util.logging.Logger;

/**
 * Some categories of objects in a PDF file can be referred to by name rather
 * than by object reference. The correspondence between names and objects is
 * established by the document’s name dictionary (PDF 1.2), located by means of
 * the Names entry in the document’s catalog (see 7.7.2, "Document Catalog").
 * Each entry in this dictionary designates the root of a name tree (see 7.9.6,
 * "Name Trees") defining names for a particular category of objects.
 *
 * @since 5.1.0
 */
public class Names extends Dictionary {

    private static final Logger logger =
            Logger.getLogger(Names.class.toString());

    /**
     * A name tree mapping name strings to destinations.
     */
    public static final Name DEST_KEY = new Name("Dests");
    /**
     * A name tree mapping name strings to annotation appearance streams.
     */
    public static final Name ANNOTATION_APPEARANCE_KEY = new Name("AP");
    /**
     * A name tree mapping name strings to document-level JavaScript actions.
     */
    public static final Name JAVASCRIPT_KEY = new Name("JavaScript");
    /**
     * A name tree mapping name strings to visible pages for use in interactive
     * forms.
     */
    public static final Name PAGES_KEY = new Name("Pages");
    /**
     * A name tree mapping name strings to invisible (template) pages for use
     * in interactive forms.
     */
    public static final Name TEMPLATES_KEY = new Name("Templates");
    /**
     * A name tree mapping digital identifiers to Web Capture content sets.
     */
    public static final Name IDS_KEY = new Name("IDS");
    /**
     * A name tree mapping name strings to file specifications for embedded
     * file streams.
     */
    public static final Name EMBEDDED_FILES_KEY = new Name("EmbeddedFiles");
    /**
     * A name tree mapping name strings to alternate presentations.
     */
    public static final Name ALTERNATE_PRESENTATIONS_KEY = new Name("AlternatePresentations");
    /**
     * A name tree mapping name strings (which shall have Unicode encoding) to
     * rendition objects.
     */
    public static final Name RENDITIONS_KEY = new Name("Renditions");

    private NameTree destsNameTree;
    private NameTree javaScriptNameTree;
    private NameTree pagesNameTree;
    private NameTree templatesNameTree;
    private NameTree idsNameTree;
    private NameTree embeddedFilesNameTree;
    private NameTree alternatePresentationsNameTree;
    private NameTree renditionsNameTree;
    private NameTree annotationAppearanceNameTree;

    public Names(Library l, DictionaryEntries h) {
        super(l, h);

        if (!inited) {
            // destinations
            Object tmp = library.getObject(entries, DEST_KEY);
            if (tmp instanceof DictionaryEntries) {
                destsNameTree = new NameTree(library, (DictionaryEntries) tmp);
                destsNameTree.init();
            }
            // Javascript
            tmp = library.getObject(entries, JAVASCRIPT_KEY);
            if (tmp instanceof DictionaryEntries) {
                javaScriptNameTree = new NameTree(library, (DictionaryEntries) tmp);
                javaScriptNameTree.init();
            }
            // Pages
            tmp = library.getObject(entries, PAGES_KEY);
            if (tmp instanceof DictionaryEntries) {
                pagesNameTree = new NameTree(library, (DictionaryEntries) tmp);
                pagesNameTree.init();
            }
            // templates
            tmp = library.getObject(entries, TEMPLATES_KEY);
            if (tmp instanceof DictionaryEntries) {
                templatesNameTree = new NameTree(library, (DictionaryEntries) tmp);
                templatesNameTree.init();
            }
            // ID's
            tmp = library.getObject(entries, IDS_KEY);
            if (tmp instanceof DictionaryEntries) {
                idsNameTree = new NameTree(library, (DictionaryEntries) tmp);
                idsNameTree.init();
            }
            // embedded files
            tmp = library.getObject(entries, EMBEDDED_FILES_KEY);
            if (tmp instanceof DictionaryEntries) {
                embeddedFilesNameTree = new NameTree(library, (DictionaryEntries) tmp);
                embeddedFilesNameTree.init();
            }
            // alternative presentation
            tmp = library.getObject(entries, ALTERNATE_PRESENTATIONS_KEY);
            if (tmp instanceof DictionaryEntries) {
                alternatePresentationsNameTree = new NameTree(library, (DictionaryEntries) tmp);
                alternatePresentationsNameTree.init();
            }
            // renditions
            tmp = library.getObject(entries, RENDITIONS_KEY);
            if (tmp instanceof DictionaryEntries) {
                renditionsNameTree = new NameTree(library, (DictionaryEntries) tmp);
                renditionsNameTree.init();
            }
        }
    }

    public NameTree getDestsNameTree() {
        return destsNameTree;
    }

    public void setDestsNameTree(NameTree destsNameTree) {
        this.destsNameTree = destsNameTree;
        entries.put(DEST_KEY, destsNameTree.getPObjectReference());
    }

    public NameTree getAnnotationAppearanceNameTree() {
        return annotationAppearanceNameTree;
    }

    public NameTree getJavaScriptNameTree() {
        return javaScriptNameTree;
    }

    public NameTree getPagesNameTree() {
        return pagesNameTree;
    }

    public NameTree getTemplatesNameTree() {
        return templatesNameTree;
    }

    public NameTree getIdsNameTree() {
        return idsNameTree;
    }

    public NameTree getEmbeddedFilesNameTree() {
        return embeddedFilesNameTree;
    }

    public NameTree getAlternatePresentationsNameTree() {
        return alternatePresentationsNameTree;
    }

    public NameTree getRenditionsNameTree() {
        return renditionsNameTree;
    }
}
