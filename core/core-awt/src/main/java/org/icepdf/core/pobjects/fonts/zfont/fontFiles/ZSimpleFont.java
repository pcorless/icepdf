package org.icepdf.core.pobjects.fonts.zfont.fontFiles;

import org.icepdf.core.pobjects.fonts.CMap;
import org.icepdf.core.pobjects.fonts.Encoding;
import org.icepdf.core.pobjects.fonts.FontFile;

import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.util.HashMap;

/**
 * Base class for
 */
public abstract class ZSimpleFont implements FontFile {

    // text layout map, very expensive to create, so we'll cache them.
    private HashMap<String, Point2D.Float> echarAdvanceCache;

    // copied over from font descriptor
    protected float missingWidth;

    // simpleFont properties.
    protected float[] widths;
    protected int firstCh;
    protected float ascent;
    protected float descent;

    // Why have one encoding when you can three.
    protected Encoding encoding;
    protected char[] cMap;
    protected CMap toUnicode;

    // PDF specific size and text state transform
    protected float size = 1.0f;
    protected AffineTransform fontMatrix;

    // todo is this still going to be a good idea?
    //  this.echarAdvanceCache = new HashMap<>(256);

}
