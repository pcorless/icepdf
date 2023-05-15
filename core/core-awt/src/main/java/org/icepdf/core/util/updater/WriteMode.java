package org.icepdf.core.util.updater;

/**
 * Specifies which write mode to use when saving change to a PDF document.
 */
public enum WriteMode {
    /**
     * Appends all changes to the end of the current PDF document.
     */
    INCREMENT_UPDATE,
    /**
     * Rewrites file removing modified object from the PDF document.
     */
    FULL_UPDATE,
}
