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
package org.icepdf.core.util;

import org.icepdf.core.pobjects.Catalog;
import org.icepdf.core.pobjects.Dictionary;
import org.icepdf.core.pobjects.DictionaryEntries;
import org.icepdf.core.pobjects.PObject;
import org.icepdf.core.pobjects.Permissions;
import org.icepdf.core.pobjects.Reference;
import org.icepdf.core.pobjects.StateManager;
import org.icepdf.core.pobjects.acroform.*;
import org.icepdf.core.pobjects.annotations.SignatureWidgetAnnotation;

import java.util.ArrayList;
import java.util.List;

import static org.icepdf.core.pobjects.acroform.DocMDPTransferParam.PERMISSION_VALUE_NO_CHANGES;

/**
 * SignatureManager is used to manage the signature dictionaries associated with a document.  A users can create
 * more than one Signature annotation but, they must be linked to the same SignatureDictionary.  When a document is
 * written to disk, only one signature dictionary can be used to sign the document.
 * <p>
 * This class also does basic validation to make sure there is only one dictionary marked as the
 * /DocMDP or "certifier" distinction.
 */
public class SignatureManager {

    private final Library library;
    private SignatureDictionary currentSignatureDictionary;

    /** A manager that can't see the document's own signatures; prefer {@link #SignatureManager(Library)}. */
    public SignatureManager() {
        this(null);
    }

    /**
     * @param library the document's library, so permission checks can see the signatures already in
     *                the file
     */
    public SignatureManager(Library library) {
        this.library = library;
    }
    private final ArrayList<SignatureWidgetAnnotation> signatureWidgetAnnotations = new ArrayList<>();

    public void addSignature(SignatureDictionary signatureDictionary, SignatureWidgetAnnotation signatureAnnotation) {
        // if not the same dictionary then we need to apply it to all the existing signature widgets and clean up
        // the old dictionary.
        if (currentSignatureDictionary != null && !currentSignatureDictionary.equals(signatureDictionary)) {
            for (SignatureWidgetAnnotation signatureWidgetAnnotation : signatureWidgetAnnotations) {
                signatureWidgetAnnotation.setSignatureDictionary(signatureDictionary);
            }
            // remove the old signature dictionary
            StateManager stateManager = signatureAnnotation.getLibrary().getStateManager();
            stateManager.removeChange(new PObject(currentSignatureDictionary,
                    currentSignatureDictionary.getPObjectReference()));
        }

        currentSignatureDictionary = signatureDictionary;
        signatureAnnotation.setSignatureDictionary(currentSignatureDictionary);

        // add the new signature widget to the list
        if (!signatureWidgetAnnotations.contains(signatureAnnotation)) {
            signatureWidgetAnnotations.add(signatureAnnotation);
        }
    }

    /**
     * Clears the current signature dictionary and references to associated SignatureWidgetAnnotation.
     * This should be done after the document has been signed or if the signature process is cancelled.
     */
    public void clearSignatures() {
        currentSignatureDictionary = null;
        signatureWidgetAnnotations.clear();
    }

    /**
     * Returns the signature dictionaries associated with the document edits and will be used to sign the document.
     *
     * @return current signature dictionary for signing or null if not set.
     */
    public SignatureDictionary getCurrentSignatureDictionary() {
        return currentSignatureDictionary;
    }

    /**
     * Check if a signature dictionary has been set. If a signature dictionary has been set, then the current
     * signature dictionary should be used to sign the document and a new one should not be created
     *
     * @return true if a signature dictionary has been set, otherwise false.
     */
    public boolean hasSignatureDictionary() {
        return currentSignatureDictionary != null;
    }

    /**
     * Checks to see if a certifier signature already exists in the document.  Looks at the
     * signatures already in the file (and the catalog's /Perms /DocMDP), not the one being added in
     * this session.
     *
     * @param library document library
     * @return true if there is already a certifier signature, otherwise false.
     */
    public boolean hasExistingCertifier(Library library) {
        return existingCertificationPermission(library) != 0;
    }

    /**
     * Checks whether a new signature may be added.  A certification signature already in the file
     * with DocMDP permission 1 (no changes) forbids it (PDF 32000-1 12.8.2.2); so does a
     * certification prepared in this session with that permission.
     *
     * @return true if signing is allowed, otherwise false.
     */
    public boolean hasPermissionToSignDocument() {
        if (library != null && existingCertificationPermission(library) == PERMISSION_VALUE_NO_CHANGES) {
            return false;
        }
        if (currentSignatureDictionary != null) {
            Integer permission = docMdpPermission(currentSignatureDictionary);
            if (permission != null) {
                return permission != PERMISSION_VALUE_NO_CHANGES;
            }
        }
        return true;
    }

    /**
     * The DocMDP permission of the document's certification signature already in the file: 0 when
     * there is none, else 1 (no changes), 2 (form fill-in and signing) or 3 (also annotations).
     * Found through the catalog's /Perms /DocMDP, or failing that the signature fields.  The
     * signature prepared in this session doesn't count.
     *
     * @param library document library
     * @return the permission, or 0 when the document isn't certified
     */
    public int existingCertificationPermission(Library library) {
        Catalog catalog = library.getCatalog();
        if (catalog == null) {
            return 0;
        }
        DictionaryEntries perms = library.getDictionary(catalog.getEntries(), Catalog.PERMS_KEY);
        if (perms != null) {
            Object docMdp = perms.get(Permissions.DOC_MDP_KEY);
            if (docMdp instanceof Reference && !isCurrent((Reference) docMdp)) {
                Object signature = library.getObject((Reference) docMdp);
                SignatureDictionary dictionary = signature instanceof SignatureDictionary
                        ? (SignatureDictionary) signature
                        : signature instanceof DictionaryEntries
                        ? new SignatureDictionary(library, (DictionaryEntries) signature)
                        : signature instanceof Dictionary
                        ? new SignatureDictionary(library, ((Dictionary) signature).getEntries()) : null;
                Integer permission = dictionary != null ? docMdpPermission(dictionary) : null;
                if (permission != null) {
                    return permission;
                }
            }
        }
        InteractiveForm interactiveForm = catalog.getInteractiveForm();
        if (interactiveForm != null) {
            for (SignatureWidgetAnnotation signatureWidget : interactiveForm.getSignatureFields()) {
                // the field's own /V: the widget's copy is only set once its page has been initialised.
                SignatureDictionary dictionary = signatureWidget.getFieldDictionary().getSignatureDictionary();
                if (dictionary == null || dictionary == currentSignatureDictionary
                        || dictionary.getEntries() == null || isCurrent(dictionary.getPObjectReference())) {
                    continue;
                }
                Integer permission = docMdpPermission(dictionary);
                if (permission != null) {
                    return permission;
                }
            }
        }
        return 0;
    }

    private boolean isCurrent(Reference reference) {
        return currentSignatureDictionary != null && reference != null
                && reference.equals(currentSignatureDictionary.getPObjectReference());
    }

    /**
     * @return the DocMDP permission of a certification signature (2 when it doesn't say, the
     * default), or null for a signature that doesn't certify
     */
    private static Integer docMdpPermission(SignatureDictionary dictionary) {
        for (SignatureReferenceDictionary reference : dictionary.getReferences()) {
            if (reference.getTransformMethod() == SignatureReferenceDictionary.TransformMethods.DocMDP) {
                TransformParams transformParams = reference.getTransformParams();
                int permission = transformParams instanceof DocMDPTransferParam
                        ? ((DocMDPTransferParam) transformParams).getPermissions() : 0;
                return permission > 0 ? permission : DocMDPTransferParam.PERMISSION_VALUE_FORMS_SIGNING;
            }
        }
        return null;
    }
}
