/**
 * Copyright (C) 2005, ICEsoft Technologies Inc.
 */
package org.icepdf.ri.images;

import java.net.URL;

/**
 * <p>Utility class to allow easy access to image resources in the
 * package com.icesoft.pdf.ri.images.
 * Used as an accessor to the images. Just call:</p>
 * <ul>
 * Images.get("<filename>.gif")
 * </ul>
 *
 * @author Mark Collette
 * @since 2.0
 */
public class Images {
    public static URL get(String name) {
        return Images.class.getResource(name);
    }
}
