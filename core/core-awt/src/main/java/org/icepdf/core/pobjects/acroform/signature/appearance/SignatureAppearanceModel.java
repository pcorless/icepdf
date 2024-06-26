package org.icepdf.core.pobjects.acroform.signature.appearance;

import org.icepdf.core.pobjects.fonts.FontFile;

import java.awt.image.BufferedImage;

public class SignatureAppearanceModel {

    protected static final int INSETS = 0;
    protected float lineSpacing = 5;

    // x, y, w, h

    // middle margin

    // need signature type

    // language selection

    // signature visibility

    private BufferedImage bufferedImage;

    // should populate what we can from the cert, but should be over writable
    private String title;
    private String name;

    private final String fontName = "Helvetica";
    protected FontFile fontSize;
    protected FontFile fontColor;

    // setup property change event, so content stream reset can happen
}
