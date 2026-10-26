/*
 * Copyright 2026 Patrick Corless
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.icepdf.core.util.parser.content;

import org.icepdf.core.pobjects.graphics.GraphicsState;
import org.icepdf.core.pobjects.graphics.commands.*;
import org.icepdf.core.util.Defs;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.NoninvertibleTransformException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Antialiased edges for content painted through a glyph clip (text render modes 4-7).
 * <br>
 * Java2D never antialiases a clip, so letters made by clipping to the glyphs and painting a fill, shading or
 * image through them come out ragged.  When the clip's scope (ET up to the Q that pops the clip) holds exactly
 * one paint operation, the scope is rewritten so the hard clip is only the clip from outside the text, and the
 * glyphs are applied by filling instead: a fill or shading fills its shape intersected with the glyph clip, and
 * an image becomes a {@link GlyphClippedImageDrawCmd}.  Java2D antialiases fills.  Anything else in the scope
 * (text, strokes, forms, a second paint) leaves the hard clip as it was.
 */
final class GlyphClipFill {

    private static final Logger logger = Logger.getLogger(GlyphClipFill.class.getName());

    /** Set false to keep every glyph clip as a hard (aliased) clip. */
    static final String ENABLED_PROPERTY = "org.icepdf.core.glyphClip.antialias";

    /** A glyph clip set at ET, waiting for the Q that pops it. */
    static final class Scope {
        final GraphicsState state;
        // index of the first command the ET emitted (the CTM restore, then the clip).
        final int start;
        // the clip outside the text, and the CTM it is in.
        final Shape outerClip;
        final AffineTransform outerTransform;
        boolean valid = true;

        Scope(GraphicsState state, int start, Shape outerClip, AffineTransform outerTransform) {
            this.state = state;
            this.start = start;
            this.outerClip = outerClip;
            this.outerTransform = new AffineTransform(outerTransform);
        }
    }

    private GlyphClipFill() {
    }

    /**
     * Rewrites the scope's commands, {@code [scope.start, shapes.size())}, if it holds one fill or image.
     *
     * @return true if the scope was rewritten
     */
    static boolean apply(List<DrawCmd> shapes, Scope scope) {
        if (!Defs.booleanProperty(ENABLED_PROPERTY, true)) {
            return false;
        }
        if (!scope.valid) {
            logger.finer("glyph clip fallback: nested glyph clip");
            return false;
        }
        int end = shapes.size();
        AffineTransform transform = new AffineTransform(scope.outerTransform);
        int lastShape = -1;
        int clipIndex = -1;
        AffineTransform clipTransform = null;
        int paintIndex = -1;
        int paintShape = -1;
        AffineTransform paintTransform = null;
        for (int i = scope.start; i < end; i++) {
            DrawCmd cmd = shapes.get(i);
            if (cmd instanceof TransformDrawCmd) {
                transform = ((TransformDrawCmd) cmd).getAffineTransform();
            } else if (cmd instanceof ShapeDrawCmd) {
                lastShape = i;
            } else if (cmd instanceof ClipDrawCmd) {
                if (paintIndex < 0) {
                    if (lastShape != i - 1) {
                        logger.finer("glyph clip fallback: clip without its shape");
                        return false;
                    }
                    clipIndex = i;
                    clipTransform = transform;
                }
            } else if (cmd instanceof FillDrawCmd || cmd instanceof ImageDrawCmd) {
                if (paintIndex >= 0) {
                    logger.finer("glyph clip fallback: more than one paint");
                    return false;
                }
                paintIndex = i;
                paintShape = lastShape;
                paintTransform = transform;
            } else if (!(cmd instanceof ColorDrawCmd || cmd instanceof PaintDrawCmd
                    || cmd instanceof StrokeDrawCmd || cmd instanceof AlphaDrawCmd)) {
                logger.finer(() -> "glyph clip fallback: " + cmd.getClass().getSimpleName());
                return false;
            }
        }
        if (paintIndex < 0 || clipIndex < 0) {
            logger.finer("glyph clip fallback: nothing painted");
            return false;
        }
        boolean fill = shapes.get(paintIndex) instanceof FillDrawCmd;
        if (fill && (paintShape <= clipIndex || ((ShapeDrawCmd) shapes.get(paintShape)).getShape() == null)) {
            // the fill paints the clip shape itself; leave it.
            logger.finer("glyph clip fallback: fill of the clip shape");
            return false;
        }
        try {
            Shape glyphClip = ((ShapeDrawCmd) shapes.get(clipIndex - 1)).getShape();
            // the glyph clip, as it was applied, in the paint's user space.
            Shape paintClip = toSpace(glyphClip, clipTransform, paintTransform);
            // the hard clip keeps only what lies outside the text.
            if (scope.outerClip == null) {
                shapes.set(clipIndex, new NoClipDrawCmd());
            } else {
                shapes.set(clipIndex - 1, new ShapeDrawCmd(toSpace(scope.outerClip, scope.outerTransform,
                        clipTransform)));
            }
            if (fill) {
                Area area = new Area(((ShapeDrawCmd) shapes.get(paintShape)).getShape());
                area.intersect(new Area(paintClip));
                shapes.set(paintShape, new ShapeDrawCmd(area));
            } else {
                shapes.set(paintIndex, new GlyphClippedImageDrawCmd((ImageDrawCmd) shapes.get(paintIndex), paintClip));
            }
            logger.finer(fill ? "glyph clip filled: fill" : "glyph clip filled: image");
            return true;
        } catch (NoninvertibleTransformException e) {
            logger.log(Level.FINER, "glyph clip fallback: transform not invertible", e);
            return false;
        }
    }

    /** {@code shape} drawn under {@code from}, expressed under {@code to}; both relative to the page base. */
    private static Shape toSpace(Shape shape, AffineTransform from, AffineTransform to)
            throws NoninvertibleTransformException {
        if (from.equals(to)) {
            return shape;
        }
        AffineTransform map = to.createInverse();
        map.concatenate(from);
        return map.createTransformedShape(shape);
    }
}
