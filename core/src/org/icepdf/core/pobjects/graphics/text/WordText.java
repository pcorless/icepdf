package org.icepdf.core.pobjects.graphics.text;

import org.icepdf.core.util.Defs;

import java.awt.*;
import java.awt.geom.GeneralPath;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Word text represents an individual word in the document.  A word can
 * also represent white space between words th isWhiteSpace method can be used
 * to distguish between words and whiteSpace
 * <p/>
 * If extracted text has extract white space then the space width fraction
 * can be adjusted.  The deault value a 4th of the current character width.  To
 * add more sapces the number can be increase or decrease to limit the number
 * of spaces that are added. The system property is as follows:
 * Default<br/>
 * org.icepdf.core.views.page.text.spaceFraction=4
 *
 * @since 4.0
 */
public class WordText extends AbstractText implements TextSelect {

    private static final Logger logger =
            Logger.getLogger(WordText.class.toString());

    // Space Glyph width fraction, the default is 1/x of the character width,
    // constitutes a potential space between glyphs.
    public static int spaceFraction;
    static {
        // sets the shadow colour of the decorator.
        try {
            spaceFraction = Defs.sysPropertyInt(
                    "org.icepdf.core.views.page.text.spaceFraction", 3);
        } catch (NumberFormatException e) {
            if (logger.isLoggable(Level.WARNING)) {
                logger.warning("Error reading text selection colour");
            }
        }
    }


    private GlyphText currentGlyph;
    private ArrayList<GlyphText> glyphs;

    // cached text values.
    private StringBuffer text;
    // is glyph white space.
    private boolean isWhiteSpace;

    public WordText() {
        text = new StringBuffer();
        glyphs = new ArrayList<GlyphText>();
    }

    public boolean isWhiteSpace() {
        return isWhiteSpace;
    }

    public void setWhiteSpace(boolean whiteSpace) {
        isWhiteSpace = whiteSpace;
    }

    protected boolean detectSpace(GlyphText sprite) {
        if (currentGlyph != null) {
            Rectangle2D.Float bounds1 = (Rectangle2D.Float)
                    currentGlyph.getGeneralPath().getBounds2D();
            Rectangle.Float bounds2 = (Rectangle2D.Float)
                    sprite.getGeneralPath().getBounds2D();
            float space = bounds2.x - (bounds1.x + bounds1.width);
            if (space <= 0) {
                return false;
            }
            // half previous glyph width will be used to determine a space
            float tolerance = bounds1.width / spaceFraction;
            return space > tolerance;
        } else {
            return false;
        }
    }

    protected static boolean detectPunctuation(GlyphText sprite) {
        int c = sprite.getUnicode();
        return isPunctuation(c);
    }

    protected static boolean detectWhiteSpace(GlyphText sprite) {
        int c = sprite.getUnicode();
        return isWhiteSpace(c);
    }

    public static boolean isPunctuation(int c) {
        return ((c == '.') || (c == ',') || (c == '?') || (c == '!') ||
                (c == ':') || (c == ';') || (c == '"') || (c == '\'')
                || (c == '/') || (c == '\\') || (c == '`') || (c == '#'));
    }

    public static boolean isWhiteSpace(int c) {
        return ((c == ' ') || (c == '\t') || (c == '\r') ||
                (c == '\n') || (c == '\f'));
    }

    protected WordText buildSpaceWord(GlyphText sprite) {

        // because we are in a normalized user space we can work with ints
        Rectangle2D.Float bounds1 = (Rectangle2D.Float)
                currentGlyph.getGeneralPath().getBounds2D();
        Rectangle.Float bounds2 = (Rectangle2D.Float)
                sprite.getGeneralPath().getBounds2D();
        float space = bounds2.x - (bounds1.x + bounds1.width);

        // max width of previous and next glyph, average can be broken by l or i etc.
        float maxWidth = Math.max(bounds1.width, bounds2.width) / 2f;
        int spaces = (int) (space / maxWidth);
        spaces = spaces < 1 ? 1 : spaces;
        float spaceWidth = space / spaces;
        // add extra spaces
        WordText whiteSpace = new WordText();
        double offset = bounds1.x + bounds1.width;
        GlyphText spaceText;

        Rectangle2D.Float spaceBounds = new Rectangle2D.Float(
                bounds1.x + bounds1.width,
                bounds1.y,
                spaceWidth, bounds1.height);
        // consider just using one space with a wider bound
        for (int i = 0; i < spaces; i++) {
            spaceText = new GlyphText((float) offset,
                    currentGlyph.getY(),
                    new Rectangle2D.Float(spaceBounds.x,
                            spaceBounds.y,
                            spaceBounds.width,
                            spaceBounds.height),
                    32, 32);
            spaceBounds.x += spaceBounds.width;
            whiteSpace.addText(spaceText);
            whiteSpace.setWhiteSpace(true);
            offset += spaceWidth;
        }
        return whiteSpace;
    }

    protected void addText(GlyphText sprite) {
        // the sprite
        glyphs.add(sprite);

        currentGlyph = sprite;

        // append the bounds calculation
        if (bounds == null) {
            bounds = sprite.getBounds();
        } else {
            bounds.add(sprite.getBounds());
        }

        // update general path
        generalPath = new GeneralPath(bounds);

        // append the text that maps up the sprite
        text.append((char) sprite.getUnicode());
    }

    public Rectangle2D.Float getBounds() {
        if (bounds == null) {
            // increase bounds as glyphs are detected.
            for (GlyphText glyph : glyphs) {
                if (bounds == null) {
                    bounds = glyph.getBounds();
                } else {
                    bounds.add(glyph.getBounds());
                }
            }
        }
        return bounds;
    }

    public GeneralPath getGeneralPath() {
        if (generalPath == null) {
            generalPath = new GeneralPath(getBounds());
        }
        return generalPath;
    }

    public ArrayList<GlyphText> getGlyphs() {
        return glyphs;
    }

    public StringBuffer getSelected() {
        StringBuffer selectedText = new StringBuffer();
        for (GlyphText glyph : glyphs) {
            if (glyph.isSelected()) {
                selectedText.append((char) glyph.getUnicode());
            }
        }
        return selectedText;
    }

    public void clearHighlighted() {
        setHighlighted(false);
        setHasHighlight(false);
        for (GlyphText glyph : glyphs) {
            glyph.setHighlighted(false);
        }
    }

    public void clearSelected() {
        setSelected(false);
        setHasSelected(false);
        for (GlyphText glyph : glyphs) {
            glyph.setSelected(false);
        }
    }

    public void selectAll() {
        setSelected(true);
        setHasSelected(true);
        for (GlyphText glyph : glyphs) {
            glyph.setSelected(true);
        }
    }

    public String getText() {
        // iterate of sprites and get text.
//        if (isWhiteSpace) {
//            return text.toString().replace(" ", "_|");
//        } else if (text.toString().equals(""))
//            return text.toString().replace("", "*");
//        else {
            return text.toString();
//        }
    }

    public String toString() {
        return getText();
    }
}
