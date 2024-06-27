package org.icepdf.core.pobjects.acroform.signature.appearance;

/**
 * Signatures can be defined two different ways. The first type, Signer is intended for multiple signatures
 * on a PDF document, probably the most typical use.  The second is a Certifier and is intended to be the only
 * signature attached to the document or in some cases the primary signature if more follow.
 */
public enum SignatureType {
    SIGNER, CERTIFIER
}
