/**
 * Copyright (C) 2004, ICEsoft Technologies, Inc.
 */
package org.icepdf.core;

import org.icepdf.core.pobjects.Document;

/**
 * Security callback.
 * An application that uses ICEpdf can provide it with the callback.
 * This will allow the document class to ask an application for security related
 * resources.
 *
 * @since 1.1
 */
public interface SecurityCallback {

    /**
     * This method is called when a security manager needs to receive a
     * password for opening an encrypted PDF document.
     *
     * @param document document being opened.
     * @return received password.
     */
    public String requestPassword(Document document);
}
