package org.icepdf.core.pobjects.annotations;

import org.icepdf.core.pobjects.*;
import org.icepdf.core.pobjects.acroform.*;
import org.icepdf.core.pobjects.acroform.signature.SignatureValidator;
import org.icepdf.core.pobjects.acroform.signature.appearance.SignatureAppearanceCallback;
import org.icepdf.core.util.Library;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.util.Date;
import java.util.logging.Logger;

import static org.icepdf.core.pobjects.acroform.SignatureDictionary.V_KEY;

/**
 * A digital signature (PDF 1.3) may be used to authenticate the identity of a user and the document's contents. It
 * stores information about the signer and the state of the document when it was signed. The signature may be purely
 * mathematical, such as a public/private-key encrypted document digest, or it may be a biometric form of identification,
 * such as a handwritten signature, fingerprint, or retinal scan. The specific form of authentication used shall be
 * implemented by a special software module called a signature handler. Signature handlers shall be identified in
 * accordance with the rules defined in Annex E.
 * <br>
 * NOTE 2<br>
 * The entries in the signature dictionary can be conceptualized as being in different dictionaries; they are in one
 * dictionary for historical and cryptographic reasons. The categories are signature properties (R, M, Name, Reason,
 * Location, Prop_Build, Prop_AuthTime, and Prop_AuthType); key information (Cert and portions of Contents when the
 * signature value is a PKCS#7 object); reference (Reference and ByteRange); and signature value (Contents when the
 * signature value is a PKCS#1 object).
 */
public class SignatureWidgetAnnotation extends AbstractWidgetAnnotation<SignatureFieldDictionary> {

    private static final Logger logger =
            Logger.getLogger(SignatureWidgetAnnotation.class.toString());

    // signature field dictionary,
    private final SignatureFieldDictionary fieldDictionary;

    // signatures value holds all the signature info for signing.
    private SignatureDictionary signatureDictionary;

    private SignatureValidator signatureValidator;

    private SignatureAppearanceCallback signatureAppearanceCallback;

    public SignatureWidgetAnnotation(Library l, DictionaryEntries h) {
        super(l, h);
        fieldDictionary = new SignatureFieldDictionary(library, entries);

//        DictionaryEntries valueDict = library.getDictionary(entries, FieldDictionary.V_KEY);
//        signatureDictionary = new SignatureDictionary(library, valueDict);

    }

    @Override
    public void init() throws InterruptedException {
        super.init();
        DictionaryEntries valueDict = library.getDictionary(entries, FieldDictionary.V_KEY);
        signatureDictionary = new SignatureDictionary(library, valueDict);
    }

    public SignatureValidator getSignatureValidator() {
        if (signatureValidator == null) {
            SignatureHandler signatureHandler = fieldDictionary.getLibrary().getSignatureHandler();
            signatureValidator = signatureHandler.validateSignature(fieldDictionary);
        }
        return signatureValidator;
    }

    public boolean hasSignatureDictionary() {
        return signatureDictionary != null && !signatureDictionary.getEntries().isEmpty();
    }

    public SignatureWidgetAnnotation(Annotation widgetAnnotation) {
        super(widgetAnnotation.getLibrary(), widgetAnnotation.getEntries());
        fieldDictionary = new SignatureFieldDictionary(library, entries);
        // copy over the reference number.
        setPObjectReference(widgetAnnotation.getPObjectReference());
    }

    /**
     * Gets an instance of a SignatureWidgetAnnotation that has valid Object Reference.
     *
     * @param library document library
     * @param rect    bounding rectangle in user space
     * @return new SignatureWidgetAnnotation Instance.
     */
    public static SignatureWidgetAnnotation getInstance(Library library, Rectangle rect) {
        // state manager
        StateManager stateManager = library.getStateManager();

        // create a new entries to hold the annotation properties
        DictionaryEntries entries = createCommonFieldDictionary(FieldDictionaryFactory.TYPE_SIGNATURE, rect);

        SignatureWidgetAnnotation signatureAnnotation = null;
        try {
            signatureAnnotation = new SignatureWidgetAnnotation(library, entries);
            signatureAnnotation.init();
            entries.put(NM_KEY, new LiteralStringObject(String.valueOf(signatureAnnotation.hashCode())));
            signatureAnnotation.setPObjectReference(stateManager.getNewReferenceNumber());
            signatureAnnotation.setNew(true);
            signatureAnnotation.setModifiedDate(PDate.formatDateTime(new Date()));
            stateManager.addChange(new PObject(signatureAnnotation, signatureAnnotation.getPObjectReference()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.fine("Signature markup annotation instance creation was interrupted");
        }
        return signatureAnnotation;
    }

    public SignatureDictionary getSignatureDictionary() {
        return signatureDictionary;
    }

    public void setSignatureDictionary(SignatureDictionary signatureDictionary) {
        entries.put(V_KEY, signatureDictionary.getPObjectReference());
        this.signatureDictionary = signatureDictionary;
    }

    @Override
    public void reset() {

    }

    public void setResetAppearanceCallback(SignatureAppearanceCallback signatureAppearanceCallback) {
        this.signatureAppearanceCallback = signatureAppearanceCallback;
    }

    @Override
    public void resetAppearanceStream(double dx, double dy, AffineTransform pageSpace, boolean isNew) {
        if (signatureAppearanceCallback != null) {
            signatureAppearanceCallback.createAppearanceStream(this, pageSpace, isNew);
        }
    }

    @Override
    public SignatureFieldDictionary getFieldDictionary() {
        return fieldDictionary;
    }
}
