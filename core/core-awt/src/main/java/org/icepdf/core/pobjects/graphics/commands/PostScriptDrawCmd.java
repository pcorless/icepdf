package org.icepdf.core.pobjects.graphics.commands;

import org.icepdf.core.pobjects.Page;
import org.icepdf.core.pobjects.graphics.OptionalContentState;
import org.icepdf.core.pobjects.graphics.PaintTimer;

import java.awt.*;
import java.awt.geom.AffineTransform;

/**
 * PostScriptDrawCmd are DrawCmds that aren't actually created when parsing postscript but instead when building
 * a Shape stack that will be converted to PostScript by the PostScriptEncoder class.  Implementation are triggers
 * used by the PostScriptEncoder to perform specific writes.
 */
public class PostScriptDrawCmd implements DrawCmd {

    public Shape paintOperand(Graphics2D g, Page parentPage, Shape currentShape,
                              Shape clip, AffineTransform base,
                              OptionalContentState optionalContentState,
                              boolean paintAlpha,
                              PaintTimer paintTimer) {
        return currentShape;
    }

}
