package org.icepdf.ri.common;

/**
 * Captures the life cycle of an open/close document cycle for many document related utility panels and dialogs.
 */
public interface MutableDocument {

    /**
     * Swing controller should be checked to retrieve a new document instance.
     */
    void refreshDocumentInstance();

    /**
     * Swing controller is calling dispose and any post document clean up should be made.
     */
    void disposeDocument();
}
