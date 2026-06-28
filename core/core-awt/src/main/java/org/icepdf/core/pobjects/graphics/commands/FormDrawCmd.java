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
import org.icepdf.core.pobjects.Name;
import org.icepdf.core.pobjects.Page;
import org.icepdf.core.pobjects.graphics.*;
import org.icepdf.core.pobjects.graphics.images.ImageUtility;
import org.icepdf.core.util.Defs;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;


/**
 * The FormDrawCmd when executed will draw an xForm's shapes to a raster and
 * then paint the raster.  This procedure is only executed if the xForm
 * is part of transparency group that has a alpha value &lt; 1.0f.
 *
 * @since 5.0
 */
public class FormDrawCmd extends AbstractDrawCmd {

    private final Form xForm;

    private BufferedImage xFormBuffer;
    private int x, y;

    // When a transparency group's bbox exceeds MAX_IMAGE_SIZE it is rasterised
    // into a proportionally down-scaled buffer to bound heap; these capture the
    // scale-up applied when the buffer is finally drawn (see paintOperand).
    private double formScale = 1.0;
    private int formWidth, formHeight;

    // Backdrop capture (§10 POC): the Shapes this command lives in and its index,
    // so a non-isolated group can replay the stack before it to reconstruct the
    // page backdrop behind the group (instead of the white-fill proxy).
    private Shapes backdropShapes;
    private int backdropIndex;
    private boolean usedBackdropComposite;

    private static final boolean disableXObjectSMask;

    // Used to use Max_value but we have a few corner cases where the dimension is +-5 of Short.MAX_VALUE, but
    // realistically we seldom have enough memory to load anything bigger then 8000px.  4k+ image are big!
    public static int MAX_IMAGE_SIZE = 2000; // Short.MAX_VALUE

    // Upper bound on a transparency-group bbox that may be rasterised into a
    // down-scaled buffer (see createBufferXObject).  Forms larger than this are
    // treated as having an "unbounded" sentinel bbox (typically +-Short.MAX_VALUE)
    // whose real painted content is small and clipped elsewhere; scaling such a
    // bbox into the buffer would collapse the content, so they keep the inline
    // path instead.  Kept well below Short.MAX_VALUE but above realistic page
    // geometry.
    public static int MAX_SCALED_FORM_SIZE =
            Defs.sysPropertyInt("org.icepdf.core.maxScaledFormSize", 16384);

    static {
        // decide if large images will be scaled
        disableXObjectSMask =
                Defs.sysPropertyBoolean("org.icepdf.core.disableXObjectSMask",
                        false);

        MAX_IMAGE_SIZE = Defs.sysPropertyInt("org.icepdf.core.maxSmaskImageSize", MAX_IMAGE_SIZE);
    }

    public FormDrawCmd(Form xForm) {
        this.xForm = xForm;
    }

    /**
     * Records where this command sits in its parent {@link Shapes} so the
     * backdrop behind the group can be reconstructed by replaying the prior
     * commands (§10 backdrop-aware compositing POC).
     */
    public void setBackdropSource(Shapes shapes, int index) {
        this.backdropShapes = shapes;
        this.backdropIndex = index;
    }

    @Override
    public Shape paintOperand(Graphics2D g, Page parentPage, Shape currentShape,
                              Shape clip, AffineTransform base,
                              OptionalContentState optionalContentState,
                              boolean paintAlpha, PaintTimer paintTimer) {
        if (optionalContentState.isVisible() && xFormBuffer == null) {
            RenderingHints renderingHints = g.getRenderingHints();
            Rectangle2D bBox = xForm.getBBox();
            x = (int) bBox.getX();
            y = (int) bBox.getY();
            boolean hasMask = ((xForm.getGraphicsState().getExtGState() != null &&
                    xForm.getGraphicsState().getExtGState().getSMask() != null) ||
                    (xForm.getExtGState() != null && xForm.getExtGState().getSMask() != null));
            boolean isExtendGraphicState = xForm.getGraphicsState().getExtGState() != null &&
                    xForm.getExtGState() != null;
            boolean normalBM = false;
            if (isExtendGraphicState && xForm.getExtGState().getBlendingMode() != null) {
                normalBM = xForm.getExtGState().getBlendingMode().equals(new Name("Normal")) &&
                        xForm.getGraphicsState().getExtGState().getBlendingMode().equals(new Name("Normal")) &&
                        (xForm.getExtGState() != null &&
                                (!xForm.getExtGState().isAlphaAShape() || xForm.getExtGState().getOverprintMode() == 0));
            }

            SoftMask formSoftMask = null;
            SoftMask softMask = null;

            if (xForm.getGraphicsState().getExtGState().getSMask() != null) {
                softMask = xForm.getGraphicsState().getExtGState().getSMask();
                boolean isShading = softMask.getG().getResources().isShading();
                if (isShading) {
                    isShading = checkForShaddingFill(softMask.getG());
                    softMask.getG().setShading(isShading);
                }
                if (!isShading) {
                    x = (int) softMask.getG().getBBox().getX();
                    y = (int) softMask.getG().getBBox().getY();
                }
            }
            if (xForm.getExtGState().getSMask() != null) {
                formSoftMask = xForm.getExtGState().getSMask();
                boolean isShading = formSoftMask.getG().getResources().isShading();
                if (isShading) {
                    isShading = checkForShaddingFill(formSoftMask.getG());
                    formSoftMask.getG().setShading(isShading);
                }
                if (!isShading) {
                    x = (int) formSoftMask.getG().getBBox().getX();
                    y = (int) formSoftMask.getG().getBBox().getY();
                }
            }
            // check if we have the same xobject.
            if (softMask != null && formSoftMask != null) {
                if (softMask.getPObjectReference() != null && formSoftMask.getPObjectReference() != null &&
                        softMask.getPObjectReference().equals(formSoftMask.getPObjectReference())) {
                    softMask = null;
                } else if (softMask.getG().getPObjectReference() != null &&
                        formSoftMask.getG().getPObjectReference() != null &&
                        softMask.getG().getPObjectReference().equals(formSoftMask.getG().getPObjectReference())) {
                    softMask = null;
                }
            }
            // need to check if we really have a shading pattern, as the resources check can be false positive.
            if (xForm.getResources().isShading()) {
                boolean isFormShading = checkForShaddingFill(xForm);
                xForm.setShading(isFormShading);
            }

            // create the form and we'll paint it at the very least.  An isolated
            // group composites against a transparent backdrop, so flag the blend
            // for this buffer's paint: a separable blend over the still
            // -transparent backdrop is weighted by backdrop alpha and reduces to
            // the source colour where the backdrop is empty, instead of
            // multiplying against zero and going black (see
            // BlendComposite.TRANSPARENT_BACKDROP).  Scoped to isolated groups
            // only.  Restored after, so the mask/outline sub-buffers below and
            // the shared paths are unaffected.
            boolean isolatedGroup = xForm.isIsolated();
            boolean previousTransparentBackdrop = isolatedGroup
                    && BlendComposite.setTransparentBackdrop(true);
            try {
                if (!hasMask && !annotationAppearance.get() && backdropShapes != null
                        && isBackdropCompositeCandidate(xForm)) {
                    // §10 backdrop-aware compositing: render the group over its
                    // real page backdrop (reconstructed by replaying the stack),
                    // then remove the backdrop so the page is not composited
                    // twice.  Replaces the legacy white-fill backdrop proxy.
                    xFormBuffer = compositeOverBackdrop(parentPage, xForm, renderingHints, normalBM, g, base);
                    usedBackdropComposite = xFormBuffer != null;
                } else {
                    xFormBuffer = createBufferXObject(parentPage, xForm, null, renderingHints, normalBM);
                }
            } finally {
                if (isolatedGroup) {
                    BlendComposite.setTransparentBackdrop(previousTransparentBackdrop);
                }
            }
            if (!disableXObjectSMask && hasMask) {

                // apply the mask and paint.
                if (!xForm.isShading()) {
                    if (softMask != null && softMask.getS().equals(SoftMask.SOFT_MASK_TYPE_ALPHA)) {
                        logger.warning("Smask alpha example, currently not supported.");
                    } else if (softMask != null && softMask.getS().equals(SoftMask.SOFT_MASK_TYPE_LUMINOSITY)) {
                        xFormBuffer = applyMask(parentPage, xFormBuffer, softMask, formSoftMask, g.getRenderingHints());
                    }
                } else if (softMask != null) {
                    // still not property aligning the form or mask space to correctly apply a shading pattern.
                    // experimental as it fixes some, breaks others, but regardless we don't support it well.
                    logger.warning("Smask pattern paint example, currently not supported.");
                    xFormBuffer.flush();
                    xFormBuffer = createBufferXObject(parentPage, softMask.getG(), null, renderingHints, true);
                    return currentShape;
                }
                // apply the form mask to current form content that has been rasterized to xFormBuffer
                if (formSoftMask != null) {
                    BufferedImage formSMaskBuffer = applyMask(parentPage, xFormBuffer, formSoftMask, softMask,
                            g.getRenderingHints());
                    // compost all the images.
                    if (softMask != null) {
                        BufferedImage formBuffer = ImageUtility.createTranslucentCompatibleImage(
                                xFormBuffer.getWidth(), xFormBuffer.getHeight());
                        Graphics2D g2d = (Graphics2D) formBuffer.getGraphics();
//                        java.util.List<Number> compRaw = formSoftMask.getBC();
//                        if (compRaw != null) {
//                            g2d.setColor(Color.BLACK);
//                            g2d.fillRect(0, 0, xFormBuffer.getWidth(), xFormBuffer.getHeight());
//                        }
                        g2d.drawImage(formSMaskBuffer, 0, 0, null);
//                        g2d.drawImage(xFormBuffer, 0, 0, null);
                        xFormBuffer.flush();
                        xFormBuffer = formBuffer;
                    } else {
                        xFormBuffer = formSMaskBuffer;
                    }
                }
            } else if (isExtendGraphicState) {
                BufferedImage shape = createBufferXObject(parentPage, xForm, null, renderingHints, true);
                xFormBuffer = ImageUtility.applyExplicitOutline(xFormBuffer, shape);
            }
//            ImageUtility.displayImage(xFormBuffer, "final" + xForm.getGroup() + " " + xForm.getPObjectReference() +
//                    xFormBuffer.getHeight() + "x" + xFormBuffer.getHeight());
        }
        // §10: a backdrop-composited buffer already holds the group's
        // contribution with the backdrop removed; the page blend (e.g. Multiply)
        // would double-count the backdrop it was just composited over, so draw it
        // back with plain SRC_OVER instead of the active group blend.
        java.awt.Composite savedComposite = null;
        if (usedBackdropComposite) {
            savedComposite = g.getComposite();
            g.setComposite(java.awt.AlphaComposite.SrcOver);
        }
        if (formScale != 1.0) {
            // buffer was rasterised at a reduced size; scale it back up to the
            // group's full footprint so the blend composites over the page at
            // the correct location and dimensions.
            g.drawImage(xFormBuffer, x, y, formWidth, formHeight, null);
        } else {
            g.drawImage(xFormBuffer, null, x, y);
        }
        if (savedComposite != null) {
            g.setComposite(savedComposite);
        }
        return currentShape;
    }

    // guard so replaying the stack to build a backdrop doesn't recursively try
    // to build backdrops for the groups inside that replay.
    private static final ThreadLocal<Boolean> capturingBackdrop =
            ThreadLocal.withInitial(() -> Boolean.FALSE);

    // Set while an annotation appearance stream is being painted.  An annotation
    // is drawn ON TOP of the already-rendered page, so its real backdrop is the
    // page content sitting in the destination Graphics2D -- which the replay-based
    // captureBackdrop cannot reconstruct (the page is not in the appearance form's
    // shape stack; the replay only sees a blank white seed).  Compositing a
    // separable blend (e.g. a Multiply highlight) over that blank backdrop and
    // drawing it SRC_OVER obliterates the underlying text.  When this is set the
    // group instead takes the plain buffered path and is drawn back with the
    // active blend composite, letting Java2D read the real page pixels as the
    // blend destination (the spec-correct result for an annotation over the page).
    private static final ThreadLocal<Boolean> annotationAppearance =
            ThreadLocal.withInitial(() -> Boolean.FALSE);

    /** Toggle the annotation-appearance backdrop bypass (see field). */
    public static void setAnnotationAppearance(boolean painting) {
        annotationAppearance.set(painting);
    }

    /**
     * §10 P0: reconstruct the page/parent backdrop behind this group, aligned
     * 1:1 with a {@code w x h} group buffer, by replaying the stack commands
     * before this one.  The replay transform maps the page's device space into
     * the group buffer's (scaled, origin-translated) space:
     * {@code scale . translate(-x,-y) . curTransform^-1 . base}.  No raster
     * readback required.  Returns null if a backdrop can't be built.
     */
    private BufferedImage captureBackdrop(Graphics2D g, AffineTransform base, int w, int h) {
        if (capturingBackdrop.get() || backdropShapes == null || w <= 0 || h <= 0) {
            return null;
        }
        capturingBackdrop.set(Boolean.TRUE);
        try {
            BufferedImage backdrop = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            Graphics2D bg = backdrop.createGraphics();
            // The page's paper is white and is NOT a draw command (it's the
            // render target's initial fill), so seed it before replaying the
            // painted stack -- otherwise a group over blank paper sees a
            // transparent backdrop and the inner blends go wrong (regressing the
            // white-page case).  Painted content covers it where present.
            bg.setColor(Color.WHITE);
            bg.fillRect(0, 0, w, h);
            // clip to the backdrop image (device space, before the page
            // transform): stops commands that read the clip from NPEing and
            // bounds the replay cost to the group's region.
            bg.setClip(0, 0, w, h);
            bg.setRenderingHints(g.getRenderingHints());
            AffineTransform b = new AffineTransform();
            if (formScale != 1.0) {
                b.scale(formScale, formScale);
            }
            b.translate(-x, -y);
            b.concatenate(g.getTransform().createInverse());
            b.concatenate(base);
            bg.setTransform(b);
            backdropShapes.paintBackdrop(bg, backdropIndex);
            bg.dispose();
            return backdrop;
        } catch (Exception e) {
            logger.fine("backdrop capture failed: " + e);
            return null;
        } finally {
            capturingBackdrop.set(Boolean.FALSE);
        }
    }

    /** A non-isolated transparency group whose blend benefits from compositing
     *  against the real reconstructed backdrop rather than a transparent buffer:
     *  a non-Normal blend over an additive (RGB/CMYK/ICC) colour space. */
    private static boolean isBackdropCompositeCandidate(Form xForm) {
        if (xForm.getExtGState() == null || xForm.getExtGState().getBlendingMode() == null
                || new Name("Normal").equals(xForm.getExtGState().getBlendingMode())
                || xForm.getGroup() == null) {
            return false;
        }
        Object cs = xForm.getLibrary().getObject(xForm.getGroup(), new Name("CS"));
        return cs == null || cs instanceof ICCBased || (cs instanceof Name
                && (((Name) cs).equals(DeviceRGB.DEVICERGB_KEY) || ((Name) cs).equals(DeviceCMYK.DEVICECMYK_KEY)));
    }

    /**
     * §10 backdrop-aware compositing (single render).  Renders the group's
     * content ONCE over a transparent backdrop with backdrop-aware blending
     * ({@code TRANSPARENT_BACKDROP}) -- which yields the *isolated* group result
     * directly (colour {@code Cs}, alpha {@code ag}), since a separable blend over
     * a transparent backdrop reduces to the source colour.  No second
     * over-backdrop render and no §11.4.8 removal are needed.  The reconstructed
     * page backdrop {@code Cb} is then applied at draw-back (composeContribution).
     * Falls back to the isolated buffer if no backdrop is available.
     */
    private BufferedImage compositeOverBackdrop(Page parentPage, Form xForm, RenderingHints hints,
                                                boolean normalBM, Graphics2D g, AffineTransform base) {
        boolean prevBackdrop = BlendComposite.setTransparentBackdrop(true);
        BufferedImage isolated;
        try {
            isolated = createBufferXObject(parentPage, xForm, null, hints, normalBM);
        } finally {
            BlendComposite.setTransparentBackdrop(prevBackdrop);
        }
        BufferedImage backdrop = captureBackdrop(g, base, isolated.getWidth(), isolated.getHeight());
        if (backdrop == null) {
            return isolated;
        }
        Name blend = xForm.getExtGState() != null ? xForm.getExtGState().getBlendingMode() : null;
        float caRaw = xForm.getExtGState() != null ? xForm.getExtGState().getNonStrokingAlphConstant() : -1f;
        float ca = (caRaw < 0f || caRaw > 1f) ? 1f : caRaw;
        BufferedImage result = composeContribution(isolated, backdrop, blend, ca, blendingSpaceFor(xForm));
        backdrop.flush(); // transient -- contribution is now in the isolated buffer
        return result;
    }

    /**
     * Builds the group's contribution to draw back with SRC_OVER (spec-correct
     * draw-back).  Given the isolated group result ({@code Cs, ag}) and the real
     * backdrop {@code Cb}, emit colour {@code B(Cb,Cs)} (the group's own blend) at
     * alpha {@code ca*ag}, so SRC_OVER over the page (= Cb) yields the §11.4.6
     * result {@code (1-ca*ag)*Cb + ca*ag*B(Cb,Cs)} -- the group's blend and
     * constant alpha applied to its real backdrop, no double-count.
     */
    // The colour-space math for group compositing (GH-501).  The composition flow
    // below is channel-count-agnostic; swapping this strategy is how a DeviceCMYK
    // group blends subtractively without forking the flow -- an RGB group keeps the
    // additive sRGB behaviour the compositor has always used, a DeviceCMYK group
    // gets the §11.3.5 ink-complement blend (see CmykBlendingSpace), and a colour
    // change in one cannot reach the other.
    private static BlendingSpace blendingSpaceFor(Form xForm) {
        if (xForm.getGroup() == null) {
            return RgbBlendingSpace.INSTANCE;
        }
        Object cs = xForm.getLibrary().getObject(xForm.getGroup(), new Name("CS"));
        if (cs instanceof Name && ((Name) cs).equals(DeviceCMYK.DEVICECMYK_KEY)) {
            return CmykBlendingSpace.INSTANCE;
        }
        if (cs instanceof ICCBased && ((ICCBased) cs).getNumComponents() == 4) {
            return CmykBlendingSpace.INSTANCE;
        }
        return RgbBlendingSpace.INSTANCE;
    }

    private static BufferedImage composeContribution(BufferedImage isolated, BufferedImage backdrop,
                                                     Name blend, float ca, BlendingSpace space) {
        BlendComposite.BlendingMode mode = toBlendingMode(blend);
        int w = isolated.getWidth(), h = isolated.getHeight();
        int n = space.channelCount();
        // Reuse the isolated buffer as the output (the contribution replaces it in
        // place) instead of allocating a third full-page buffer per group; the
        // backdrop row is the only other input and is read before the matching
        // isolated pixel is overwritten.
        int[] is = new int[w], b0 = new int[w];
        double[] cs = new double[n], cb = new double[n], out = new double[n];
        for (int yy = 0; yy < h; yy++) {
            isolated.getRGB(0, yy, w, 1, is, 0, w);
            backdrop.getRGB(0, yy, w, 1, b0, 0, w);
            for (int xx = 0; xx < w; xx++) {
                int ag = is[xx] >>> 24;
                if (ag == 0) {
                    is[xx] = 0; // no group contribution -> page shows through
                    continue;
                }
                int outAlpha = (int) Math.round(ca * ag);
                if (outAlpha < 0) outAlpha = 0; else if (outAlpha > 255) outAlpha = 255;
                space.fromSRGB(is[xx], cs);   // isolated group colour Cs
                space.fromSRGB(b0[xx], cb);   // real backdrop colour Cb
                for (int c = 0; c < n; c++) {
                    out[c] = space.separable(mode, cb[c], cs[c]);   // B(Cb, Cs)
                }
                is[xx] = space.toSRGB(out, outAlpha);
            }
            isolated.setRGB(0, yy, w, 1, is, 0, w);
        }
        return isolated;
    }

    /** Maps a PDF blend-mode {@link Name} to the {@link BlendComposite.BlendingMode}
     *  the {@link BlendingSpace} understands; only the separable modes appear on a
     *  transparency group, anything else falls back to Normal. */
    private static BlendComposite.BlendingMode toBlendingMode(Name blend) {
        if (blend == null) return BlendComposite.BlendingMode.NORMAL;
        if (BlendComposite.MULTIPLY_VALUE.equals(blend)) return BlendComposite.BlendingMode.MULTIPLY;
        if (BlendComposite.SCREEN_VALUE.equals(blend)) return BlendComposite.BlendingMode.SCREEN;
        if (BlendComposite.OVERLAY_VALUE.equals(blend)) return BlendComposite.BlendingMode.OVERLAY;
        if (BlendComposite.DARKEN_VALUE.equals(blend)) return BlendComposite.BlendingMode.DARKEN;
        if (BlendComposite.LIGHTEN_VALUE.equals(blend)) return BlendComposite.BlendingMode.LIGHTEN;
        if (BlendComposite.COLOR_DODGE_VALUE.equals(blend)) return BlendComposite.BlendingMode.COLOR_DODGE;
        if (BlendComposite.COLOR_BURN_VALUE.equals(blend)) return BlendComposite.BlendingMode.COLOR_BURN;
        if (BlendComposite.HARD_LIGHT_VALUE.equals(blend)) return BlendComposite.BlendingMode.HARD_LIGHT;
        if (BlendComposite.SOFT_LIGHT_VALUE.equals(blend)) return BlendComposite.BlendingMode.SOFT_LIGHT;
        if (BlendComposite.DIFFERENCE_VALUE.equals(blend)) return BlendComposite.BlendingMode.DIFFERENCE;
        if (BlendComposite.EXCLUSION_VALUE.equals(blend)) return BlendComposite.BlendingMode.EXCLUSION;
        return BlendComposite.BlendingMode.NORMAL;
    }

    private BufferedImage applyMask(Page parentPage, BufferedImage xFormBuffer, SoftMask softMask, SoftMask gsSoftMask,
                                    RenderingHints renderingHints) {
        if (softMask != null && softMask.getS().equals(SoftMask.SOFT_MASK_TYPE_ALPHA)) {
            logger.warning("Smask alpha example, currently not supported.");
        } else if (softMask != null && softMask.getS().equals(SoftMask.SOFT_MASK_TYPE_LUMINOSITY)) {
            BufferedImage sMaskBuffer = createBufferXObject(parentPage, softMask.getG(), softMask, renderingHints, true);
//            ImageUtility.displayImage(xFormBuffer, "base " + xForm.getPObjectReference() + " " + xFormBuffer.getHeight() + " x " + xFormBuffer.getHeight());
//            ImageUtility.displayImage(sMaskBuffer, "smask " + softMask.getG().getPObjectReference() + " " + useLuminosity);
            if (gsSoftMask == null) {
                xFormBuffer = ImageUtility.applyExplicitSMask(xFormBuffer, sMaskBuffer);
            } else {
                // todo try and figure out how to apply an AIS=false alpha to an xobject.
//                xFormBuffer = ImageUtility.applyExplicitLuminosity(xFormBuffer, sMaskBuffer);
                xFormBuffer = ImageUtility.applyExplicitOutline(xFormBuffer, sMaskBuffer);
            }
            // test for TR function
            if (softMask.getTR() != null) {
                logger.warning("Smask Transfer Function example, currently not supported.");
            }
            // todo need to look at matte too which is on the xobject.
        }
//        ImageUtility.displayImage(xFormBuffer, "final  " + softMask.getG().getPObjectReference());
        return xFormBuffer;
    }

    /**
     * Paint the form content to a BufferedImage so that the forms content can be
     * used to apply the sMask data.  Further work is needed to fully support this
     * section of transparency groups.
     *
     * @param parentPage     parent page object
     * @param xForm          form being drawn to buffer.
     * @param renderingHints graphic state rendering hinds of parent.
     * @return buffered image of xObject content.
     */
    private BufferedImage createBufferXObject(Page parentPage, Form xForm, SoftMask softMask,
                                              RenderingHints renderingHints, boolean isMask) {
        Rectangle2D bBox = xForm.getBBox();
        int width = (int) bBox.getWidth();
        int height = (int) bBox.getHeight();
        double scale = 1.0;
        int bufferWidth;
        int bufferHeight;
        if (isMask && xForm == this.xForm && xFormBuffer != null) {
            // Outline pass for the SAME group (applyExplicitOutline): match the
            // main buffer's (possibly area-scaled) dimensions and scale exactly,
            // so the outline lines up 1:1 and applyExplicitOutline does not have
            // to scaleImagesToSameSize.  That reconciling resample softened an
            // oversized, down-scaled main buffer (e.g. 1.pdf's 7742x517 main vs a
            // 7742x631 outline), noticeably blurring the line-art.
            bufferWidth = xFormBuffer.getWidth();
            bufferHeight = xFormBuffer.getHeight();
            scale = formScale;
        } else if (isMask && xFormBuffer != null) {
            // Mask / outline sub-buffers for a DIFFERENT form (soft masks) keep
            // the original clamp behaviour: an oversized dimension is pinned to
            // the already-built main buffer so the two stay aligned for
            // compositing.  Down-scaling is applied only to the main form buffer
            // below.
            //
            // The `xFormBuffer != null` guard is essential: the MAIN buffer is
            // also created with isMask=true when the group's blend is Normal
            // (the `normalBM` argument), and at that point xFormBuffer (the field
            // this clamp pins to) is still null.  A within-MAX_IMAGE_SIZE group
            // never reached the `>= MAX_IMAGE_SIZE` lines, but an oversized one
            // NPE'd here; routing it past the gate exposed that.  The main buffer
            // must instead use the area-scale branch below, so fall through.
            if (width == 0) {
                width = 1;
            } else if (width >= MAX_IMAGE_SIZE) {
                width = xFormBuffer.getWidth();
            }
            if (height == 0) {
                height = 1;
            } else if (height >= MAX_IMAGE_SIZE) {
                height = xFormBuffer.getHeight();
            }
            bufferWidth = width;
            bufferHeight = height;
        } else {
            // Bound the main offscreen buffer so an oversized group is rasterised
            // into a proportionally down-scaled buffer and scaled back up when
            // drawn, keeping the group's blend/alpha intact instead of dropping
            // it (which previously left e.g. a Multiply line-art group painting an
            // opaque white background over the page).
            //
            // Bound by total pixel AREA, preserving aspect, rather than capping
            // the largest single dimension.  Capping the largest dimension
            // collapses a high-aspect-ratio strip: 1.pdf's ~9455x631 group became
            // 2001x134 (uniform scale driven by the width), leaving almost no
            // vertical detail.  The area budget keeps the same peak memory as a
            // MAX_IMAGE_SIZE square but distributes it by aspect, so that strip
            // becomes ~7742x517 -- ~3.9x the height for the same ~16 MB ceiling.
            if (width == 0) {
                width = 1;
            }
            if (height == 0) {
                height = 1;
            }
            long area = (long) width * height;
            long maxArea = (long) MAX_IMAGE_SIZE * MAX_IMAGE_SIZE;
            if (area > maxArea) {
                scale = Math.sqrt((double) maxArea / area);
            }
            bufferWidth = Math.max(1, (int) Math.ceil(width * scale));
            bufferHeight = Math.max(1, (int) Math.ceil(height * scale));
            formScale = scale;
            formWidth = width;
            formHeight = height;
        }
        // create the new image to write too.
        BufferedImage bi = ImageUtility.createTranslucentCompatibleImage(bufferWidth, bufferHeight);
        Graphics2D canvas = bi.createGraphics();
        // copy over the rendering hints
        canvas.setRenderingHints(renderingHints);
        // map form-space drawing into the (possibly down-scaled) buffer.
        if (scale != 1.0) {
            canvas.scale(scale, scale);
        }
        // get shapes and paint them.
        try {
            Shapes xFormShapes = xForm.getShapes();
            if (xFormShapes != null) {
                xFormShapes.setPageParent(parentPage);
                // translate the coordinate system as we'll paint the g
                // graphic at the correctly location later.
                if (!xForm.isShading()) {
                    canvas.translate(-(int) bBox.getX(), -(int) bBox.getY());
                    canvas.setClip(bBox);
                }
                // basic support for gradient fills,  still have a few corners cases to work on.
                else {
                    // The `sh` fill shape defaults to the form bbox.  When that
                    // bbox is a +-Short.MAX_VALUE sentinel (common for shading
                    // soft masks) and the form's cm scales it up, the shape maps
                    // to ~1e8 device coordinates and overflows Java2D's
                    // rasteriser -> zero coverage -> an empty (fully transparent)
                    // mask -> the masked content vanishes (WhiteGradient.pdf).
                    // Detect that overflow and substitute the buffer region (the
                    // area the mask actually covers), reusing the same transform
                    // the fill will run under so the gradient still lands 1:1.
                    AffineTransform fillXform = AffineTransform.getTranslateInstance(-x, -y);
                    for (DrawCmd cmd : xFormShapes.getShapes()) {
                        if (cmd instanceof TransformDrawCmd) {
                            // TransformDrawCmd sets transform = base . cm; base
                            // here is the translate below, so track the latest cm.
                            fillXform = AffineTransform.getTranslateInstance(-x, -y);
                            fillXform.concatenate(((TransformDrawCmd) cmd).getAffineTransform());
                        } else if (cmd instanceof ShapeDrawCmd && ((ShapeDrawCmd) cmd).getShape() == null) {
                            Rectangle2D bounds = bBox.getBounds2D();
                            ((ShapeDrawCmd) cmd).setShape(clampShadingFillShape(bounds, fillXform,
                                    bufferWidth, bufferHeight));
                        }
                    }
                    canvas.translate(-x, -y);
                    canvas.setClip(bBox.getBounds2D());
                }
                xFormShapes.paint(canvas);
                xFormShapes.setPageParent(null);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.fine("Form draw thread interrupted.");
        }
        canvas.dispose();
        return bi;
    }

    // Device coordinate beyond which Java2D's rasteriser loses coverage; a
    // shading-mask buffer is at most a few thousand pixels, so anything past this
    // is a sentinel-bbox blow-up, not real geometry.
    private static final double SHADING_FILL_DEVICE_LIMIT = 1_000_000d;

    /**
     * Returns a fill shape for a shading `sh` whose original shape is the form
     * bbox.  If that bbox maps within the rasteriser's safe device range under
     * {@code fillXform}, it is returned unchanged.  If it would overflow (a
     * +-Short.MAX_VALUE sentinel bbox scaled up by the form's cm), it is replaced
     * with the buffer region transformed back into the fill's user space, so the
     * shading covers exactly the mask buffer without overflowing.
     */
    private static Shape clampShadingFillShape(Rectangle2D bounds, AffineTransform fillXform,
                                               int bufferWidth, int bufferHeight) {
        Rectangle2D device = fillXform.createTransformedShape(bounds).getBounds2D();
        if (device.getMinX() > -SHADING_FILL_DEVICE_LIMIT
                && device.getMinY() > -SHADING_FILL_DEVICE_LIMIT
                && device.getMaxX() < SHADING_FILL_DEVICE_LIMIT
                && device.getMaxY() < SHADING_FILL_DEVICE_LIMIT) {
            return bounds;
        }
        try {
            return fillXform.createInverse().createTransformedShape(
                    new Rectangle2D.Double(0, 0, bufferWidth, bufferHeight));
        } catch (java.awt.geom.NoninvertibleTransformException e) {
            return bounds;
        }
    }

    private boolean checkForShaddingFill(Form xform) {
        boolean found = false;
        for (DrawCmd cmd : xform.getShapes().getShapes()) {
            if (cmd instanceof ShapeDrawCmd && ((ShapeDrawCmd) cmd).getShape() == null) {
                found = true;
                break;
            }
        }
        return found;
    }
}
