package org.icepdf.core.util.updater.modifiers;

import org.icepdf.core.pobjects.*;
import org.icepdf.core.pobjects.acroform.SignatureDictionary;
import org.icepdf.core.pobjects.annotations.*;
import org.icepdf.core.util.Library;

import java.util.List;

import static org.icepdf.core.pobjects.Page.ANNOTS_KEY;
import static org.icepdf.core.pobjects.Page.RESOURCES_KEY;

/**
 * Takes care of removing all traces of an annotation and its dependencies.
 *
 * @since 7.2
 */
public class AnnotationRemovalModifier implements Modifier<Annotation> {

    private final Page parentPage;

    public AnnotationRemovalModifier(Object parent) {
        this.parentPage = (Page) parent;
    }

    @Override
    public void modify(Annotation annot) {
        Library library = annot.getLibrary();

        StateManager stateManager = library.getStateManager();

        Object annots = parentPage.getObject(ANNOTS_KEY);
        boolean isAnnotAReference = library.isReference(parentPage.getEntries(), ANNOTS_KEY);

        // mark the item as deleted so the state manager can clean up the reference.
        annot.setDeleted(true);
        stateManager.addDeletion(annot.getPObjectReference());
        Stream nAp = annot.getAppearanceStream();
        if (nAp != null) {
            nAp.setDeleted(true);
            // clean up resources.
            Object tmp = library.getObject(nAp.getEntries(), RESOURCES_KEY);
            if (tmp instanceof Resources) {
                Resources resources = (Resources) tmp;
                // only remove our font instance, if we remove another font we would have
                // to check the document to see if it was used anywhere else.
                Dictionary font = resources.getFont(FreeTextAnnotation.EMBEDDED_FONT_NAME);
                if (font != null) {
                    font.setDeleted(true);
                    stateManager.addDeletion(font.getPObjectReference());
                }
                DictionaryEntries xObject = resources.getXObjects();
                if (xObject != null) {
                    for (Object key : xObject.keySet()) {
                        Object obj = xObject.get(key);
                        if (obj instanceof Reference) {
                            stateManager.addDeletion((Reference) obj);
                        }
                    }
                }
            }
        }
        // check for /V key which is a reference to a signature dictionary
        // todo new annotation base method to encapsulate the cleanup
        if (annot instanceof SignatureWidgetAnnotation) {
            SignatureWidgetAnnotation signatureWidgetAnnotation = (SignatureWidgetAnnotation) annot;
            Object v = signatureWidgetAnnotation.getEntries().get(SignatureDictionary.V_KEY);
            if (v instanceof Reference) {
                stateManager.addDeletion((Reference) v);
            }
            library.getSignatureDictionaries().clearSignatures();
        }

        // check to see if this is an existing annotations, if the annotations
        // is existing then we have to mark either the page or annot ref as changed.
        if (!annot.isNew() && !isAnnotAReference) {
            // add the page as state change
            stateManager.addChange(
                    new PObject(parentPage, parentPage.getPObjectReference()));
        }
        // if not new and annot is a ref, we have to add annot ref as changed.
        if (!annot.isNew() && isAnnotAReference) {
            stateManager.addChange(
                    new PObject(annots, library.getObjectReference(
                            parentPage.getEntries(), ANNOTS_KEY)));
        }
        // if new annotation, then we can remove it from the state manager.
        else if (annot.isNew()) {
            stateManager.removeChange(
                    new PObject(annot, annot.getPObjectReference()));
            // check for an appearance stream which also needs to be removed.
            if (nAp != null) {
                stateManager.removeChange(new PObject(
                        nAp, nAp.getPObjectReference()));
                library.removeObject(nAp.getPObjectReference());
            }
        }
        // removed the annotations from the annots vector
        if (annots instanceof List) {
            // update annots dictionary with new annotations reference,
            ((List<?>) annots).remove(annot.getPObjectReference());
        }

        // remove the annotations form the annotation cache in the page object
        List<Annotation> annotations = parentPage.getAnnotations();
        if (annotations != null) {
            annotations.remove(annot);
        }

        // remove any markupGlue so that it doesn't get written.  Glue is never added to the document, it created
        // dynamically for print purposes.
        if (annot instanceof MarkupAnnotation && annotations != null) {
            MarkupAnnotation markupAnnotation = (MarkupAnnotation) annot;
            for (Annotation annotation : annotations) {
                if (annotation instanceof MarkupGlueAnnotation &&
                        ((MarkupGlueAnnotation) annotation).getMarkupAnnotation().equals(markupAnnotation)) {
                    annotations.remove(annotation);
                    // no need to add to state manager as it was never part of the document.
                    break;
                }
            }
            for (Annotation annotation : annotations) {
                if (annotation instanceof PopupAnnotation &&
                        ((PopupAnnotation) annotation).getParent().equals(markupAnnotation)) {
                    annotations.remove(annotation);
                    if (annots instanceof List) {
                        ((List<?>) annots).remove(annotation.getPObjectReference());
                        stateManager.addDeletion(annotation.getPObjectReference());
                        break;
                    }
                }
            }
        }
        if (annotations != null && annotations.isEmpty()) {
            parentPage.getEntries().remove(ANNOTS_KEY);
            // change should already be registered
        }
        // finally remove it from the library to free up the memory
        library.removeObject(annot.getPObjectReference());
    }
}
