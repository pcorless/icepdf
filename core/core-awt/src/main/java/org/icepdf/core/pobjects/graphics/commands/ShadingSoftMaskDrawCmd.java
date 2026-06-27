/*
 * Copyright 2006-2019 ICEsoft Technologies Canada Corp.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an "AS
 * IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */
package org.icepdf.core.pobjects.graphics.commands;

import org.icepdf.core.pobjects.Form;
import org.icepdf.core.pobjects.Page;
import org.icepdf.core.pobjects.graphics.OptionalContentState;
import org.icepdf.core.pobjects.graphics.PaintTimer;
import org.icepdf.core.pobjects.graphics.SoftMask;
import org.icepdf.core.pobjects.graphics.Shapes;
import org.icepdf.core.pobjects.graphics.images.ImageUtility;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.logging.Logger;

/**
 * Paints a shading ({@code sh}) fill that has a graphics-state <i>luminosity</i>
 * soft mask active.  The shading colour and the soft-mask luminosity are
 * rendered to separate buffers over the current clip and combined per
 * PDF 32000-1 §11.6.5.2, so the shading is modulated by the mask instead of
 * being painted at a flat alpha (the legacy {@code consume_sh} behaviour, which
 * dropped the spatial mask entirely -- e.g. faded.pdf's white fade washing the
 * whole image).
 */
public class ShadingSoftMaskDrawCmd extends AbstractDrawCmd {

    private static final Logger logger = Logger.getLogger(ShadingSoftMaskDrawCmd.class.getName());

    // Hard cap on the offscreen buffer so a runaway clip can't allocate the heap.
    private static final int MAX_DIM = 4096;

    private final Paint shadingPaint;
    private final SoftMask softMask;
    private final float alpha;
    // When non-null, the explicit region to fill (a path `f` fill); otherwise the
    // current clip is used (a shading `sh` fill).
    private final Shape fillShape;
    // True for `f` fills: draw the masked result with g's current composite (the
    // blend mode + constant alpha already installed by the preceding `gs`), rather
    // than overriding to SrcOver.  Lets a Screen/Multiply bevel blend correctly.
    private final boolean useCurrentComposite;

    /** Shading `sh` fill with a luminosity soft mask (faded.pdf). */
    public ShadingSoftMaskDrawCmd(Paint shadingPaint, SoftMask softMask, float alpha) {
        this(shadingPaint, null, softMask, alpha, false);
    }

    /** Path `f` fill with a luminosity soft mask, drawn with the active blend
     *  composite (bevel highlights/shadows -- trans.pdf, Lesson Plans.pdf). */
    public ShadingSoftMaskDrawCmd(Paint fillPaint, Shape fillShape, SoftMask softMask) {
        this(fillPaint, fillShape, softMask, 1f, true);
    }

    private ShadingSoftMaskDrawCmd(Paint shadingPaint, Shape fillShape, SoftMask softMask,
                                   float alpha, boolean useCurrentComposite) {
        this.shadingPaint = shadingPaint;
        this.fillShape = fillShape;
        this.softMask = softMask;
        this.alpha = alpha;
        this.useCurrentComposite = useCurrentComposite;
    }

    @Override
    public Shape paintOperand(Graphics2D g, Page parentPage, Shape currentShape,
                              Shape clip, AffineTransform base,
                              OptionalContentState optionalContentState,
                              boolean paintAlpha, PaintTimer paintTimer) {
        if (!optionalContentState.isVisible() || shadingPaint == null) {
            return currentShape;
        }
        // Region to fill: the explicit path for an `f` fill, else the clip for `sh`.
        Shape region = fillShape != null ? fillShape : g.getClip();
        Shape gClip = g.getClip();
        if (region == null) {
            return currentShape;
        }
        AffineTransform ctm = g.getTransform();
        Rectangle device = ctm.createTransformedShape(region).getBounds();
        if (gClip != null) {
            device = device.intersection(ctm.createTransformedShape(gClip).getBounds());
        }
        if (device.width <= 0 || device.height <= 0) {
            return currentShape;
        }
        if (device.width > MAX_DIM || device.height > MAX_DIM) {
            // fall back to a plain fill rather than over-allocate.
            g.setPaint(shadingPaint);
            g.fill(region);
            return currentShape;
        }
        try {
            // 1. fill colour/shading over the region.
            BufferedImage shade = ImageUtility.createTranslucentCompatibleImage(device.width, device.height);
            Graphics2D sg = shade.createGraphics();
            sg.setRenderingHints(g.getRenderingHints());
            sg.translate(-device.x, -device.y);
            sg.transform(ctm);
            if (gClip != null) {
                sg.setClip(gClip);
            }
            sg.setPaint(shadingPaint);
            sg.fill(region);
            sg.dispose();

            // 2. soft-mask luminosity over the same region.
            BufferedImage mask = renderLuminosityMask(parentPage, g, ctm, region, device);

            // 3. combine: mask luminosity weights the fill alpha.
            BufferedImage result = (mask != null)
                    ? ImageUtility.applyExplicitSMask(shade, mask) : shade;

            // 4. blit back in device space.
            AffineTransform saved = g.getTransform();
            Composite savedComposite = g.getComposite();
            g.setTransform(new AffineTransform());
            if (!useCurrentComposite && alpha >= 0f && alpha < 1f) {
                g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
            }
            // else: keep g's composite -- the blend mode + ca installed by `gs`.
            g.drawImage(result, device.x, device.y, null);
            g.setComposite(savedComposite);
            g.setTransform(saved);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            logger.fine("soft-mask fill paint failed, falling back to plain fill: " + e);
            g.setPaint(shadingPaint);
            g.fill(region);
        }
        return currentShape;
    }

    /**
     * Render the soft-mask group's luminosity into a {@code device}-sized buffer
     * aligned with the shading buffer.  A luminosity mask is composited over an
     * opaque BC backdrop (default black); the group's content (typically a grey
     * shading) then supplies the per-pixel luminosity that applyExplicitSMask
     * reads as alpha.  Null shading-fill shapes (sentinel-bbox {@code sh} fills)
     * are substituted with the fill region so they cover the buffer instead of
     * overflowing the rasteriser.
     */
    private BufferedImage renderLuminosityMask(Page parentPage, Graphics2D g, AffineTransform ctm,
                                               Shape fillClip, Rectangle device) throws InterruptedException {
        Form maskForm = softMask.getG();
        if (maskForm == null) {
            return null;
        }
        Shapes maskShapes = maskForm.getShapes();
        if (maskShapes == null) {
            return null;
        }
        BufferedImage mask = new BufferedImage(device.width, device.height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D mg = mask.createGraphics();
        // BC backdrop: a luminosity mask sees an opaque backdrop coloured by BC
        // (default black -> luminosity 0 -> fully masked outside the content).
        mg.setColor(backdropColour());
        mg.fillRect(0, 0, device.width, device.height);
        mg.setRenderingHints(g.getRenderingHints());
        // The mask group's coordinate system is the CTM in effect when the soft
        // mask was *set* (§11.6.5.2).  For an `sh` fill the page applies the
        // gradient matrix between `gs` and `sh`, so the sh-time `ctm` includes it
        // AND the mask group re-applies its own (identical) cm internally --
        // transforming the gradient twice (rotated/offset fade).  Undo the mask's
        // own cm so its internal cm lands it on exactly the sh-time CTM.  A plain
        // `f` fill has no intermediate cm (the fill follows `gs` directly), so the
        // mask group's cm is legitimate and must NOT be undone -- undoing it there
        // mis-positions the mask off-buffer (all-black -> the fill vanishes).
        AffineTransform base = new AffineTransform();
        base.translate(-device.x, -device.y);
        base.concatenate(ctm);
        if (!useCurrentComposite) {
            AffineTransform maskCm = firstTransform(maskShapes);
            if (maskCm != null) {
                try {
                    base.concatenate(maskCm.createInverse());
                } catch (java.awt.geom.NoninvertibleTransformException e) {
                    // fall back to the raw ctm base
                }
            }
        }
        mg.setTransform(base);
        mg.setClip(fillClip);
        // Substitute any null sentinel shading-fill shape with the fill region so
        // the gradient covers the buffer (mirrors FormDrawCmd.clampShadingFillShape).
        for (DrawCmd cmd : maskShapes.getShapes()) {
            if (cmd instanceof ShapeDrawCmd && ((ShapeDrawCmd) cmd).getShape() == null) {
                ((ShapeDrawCmd) cmd).setShape(fillClip);
            }
        }
        maskShapes.setPageParent(parentPage);
        maskShapes.paint(mg);
        maskShapes.setPageParent(null);
        mg.dispose();
        return mask;
    }

    /** The mask group's own cm (its first transform), used to keep the gradient
     *  from being transformed twice; null if the group has no transform. */
    private static AffineTransform firstTransform(Shapes maskShapes) {
        for (DrawCmd cmd : maskShapes.getShapes()) {
            if (cmd instanceof TransformDrawCmd) {
                return ((TransformDrawCmd) cmd).getAffineTransform();
            }
        }
        return null;
    }

    private Color backdropColour() {
        List<Number> bc = softMask.getBC();
        if (bc != null && !bc.isEmpty()) {
            float v = bc.get(0).floatValue();
            if (bc.size() >= 3) {
                return new Color(clamp(bc.get(0).floatValue()), clamp(bc.get(1).floatValue()),
                        clamp(bc.get(2).floatValue()));
            }
            return new Color(clamp(v), clamp(v), clamp(v));
        }
        return Color.BLACK;
    }

    private static float clamp(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }
}
