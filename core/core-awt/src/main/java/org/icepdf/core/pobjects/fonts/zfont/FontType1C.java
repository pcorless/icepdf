package org.icepdf.core.pobjects.fonts.zfont;

import org.icepdf.core.pobjects.Stream;
import org.icepdf.core.pobjects.fonts.CMap;
import org.icepdf.core.pobjects.fonts.Encoding;
import org.icepdf.core.pobjects.fonts.FontFile;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.net.URL;
import java.util.Map;

public class FontType1C implements FontFile {

    public FontType1C(Stream fontStream) {

    }

    @Override
    public Point2D echarAdvance(char ech) {
        return null;
    }

    @Override
    public FontFile deriveFont(AffineTransform at) {
        return null;
    }

    @Override
    public FontFile deriveFont(Encoding encoding, CMap toUnicode) {
        return null;
    }

    @Override
    public FontFile deriveFont(float[] widths, int firstCh, float missingWidth, float ascent, float descent, char[] diff) {
        return null;
    }

    @Override
    public FontFile deriveFont(Map<Integer, Float> widths, int firstCh, float missingWidth, float ascent, float descent, char[] diff) {
        return null;
    }

    @Override
    public boolean canDisplayEchar(char ech) {
        return false;
    }

    @Override
    public void setIsCid() {

    }

    @Override
    public FontFile deriveFont(float pointsize) {
        return null;
    }

    @Override
    public CMap getToUnicode() {
        return null;
    }

    @Override
    public String toUnicode(String displayText) {
        return null;
    }

    @Override
    public String toUnicode(char displayChar) {
        return null;
    }

    @Override
    public String getFamily() {
        return null;
    }

    @Override
    public float getSize() {
        return 0;
    }

    @Override
    public double getAscent() {
        return 0;
    }

    @Override
    public double getDescent() {
        return 0;
    }

    @Override
    public Rectangle2D getMaxCharBounds() {
        return null;
    }

    @Override
    public AffineTransform getTransform() {
        return null;
    }

    @Override
    public int getRights() {
        return 0;
    }

    @Override
    public String getName() {
        return null;
    }

    @Override
    public boolean isHinted() {
        return false;
    }

    @Override
    public int getNumGlyphs() {
        return 0;
    }

    @Override
    public int getStyle() {
        return 0;
    }

    @Override
    public char getSpaceEchar() {
        return 0;
    }

    @Override
    public Rectangle2D getEstringBounds(String estr, int beginIndex, int limit) {
        return null;
    }

    @Override
    public String getFormat() {
        return null;
    }

    @Override
    public void drawEstring(Graphics2D g, String estr, float x, float y, long layout, int mode, Color strokeColor) {

    }

    @Override
    public Shape getEstringOutline(String estr, float x, float y) {
        return null;
    }

    @Override
    public ByteEncoding getByteEncoding() {
        return null;
    }

    @Override
    public URL getSource() {
        return null;
    }
}
