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

    // When set (a finite soft-mask group), both the content buffer and the mask
    // sub-buffer are framed to this UNION(content, mask) bbox: same size, same
    // origin, so applyExplicitSMask overlays them 1:1 -- no scaleImagesToSameSize
    // resize (one fewer allocation) and no offset, so the mask's soft feather
    // that extends past the content bbox (a drop shadow's outer edge) is kept.
    private Rectangle2D bufferFrame;

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

    // Soft-mask groups cannot be down-scaled: applyMask stretches the mask to the
    // content buffer, and a down-scaled buffer mis-scales/clips the mask's soft
    // feather (drop shadows lose their outer edge -> hard edge).  So a masked
    // group is buffered 1:1 up to this (much larger) ceiling instead of the
    // 2010-era 2000^2 budget; a 4096^2 ARGB buffer is ~67 MB, acceptable on
    // modern hardware for the render-quality win.  Beyond this it stays inline.
    public static int MAX_SMASK_IMAGE_SIZE =
            Defs.sysPropertyInt("org.icepdf.core.maxSmaskBufferSize", 4096);

    // Upper bound on a transparency-group bbox that may be rasterised into a
    // down-scaled buffer (see createBufferXObject).  Forms larger than this are
    // treated as having an "unbounded" sentinel bbox (typically +-Short.MAX_VALUE)
    // whose real painted content is small and clipped elsewhere; scaling such a
    // bbox into the buffer would collapse the content, so they keep the inline
    // path instead.  Kept well below Short.MAX_VALUE but above realistic page
    // geometry.
    public static int MAX_SCALED_FORM_SIZE =
            Defs.sysPropertyInt("org.icepdf.core.maxScaledFormSize", 16384);

    // GH-501 step 2: raster-level subtractive compositing of DeviceCMYK groups from
    // true preserved samples.  On by default; -Dorg.icepdf.core.cmyk.subtractive=false
    // forces the additive sRGB path (A/B comparison and safety kill-switch).
    private static final boolean cmykSubtractiveEnabled =
            Defs.sysPropertyBoolean("org.icepdf.core.cmyk.subtractive", true);

    // Prototype (§5.2): also treat isolated/knockout groups as buffer-requiring in
    // requiresOffscreenBuffer.  Off by default while we measure corpus impact.
    private static final boolean isolationAwareRouting =
            Defs.sysPropertyBoolean("org.icepdf.core.isolationAwareRouting", false);

    static {
        // decide if large images will be scaled
        disableXObjectSMask =
                Defs.sysPropertyBoolean("org.icepdf.core.disableXObjectSMask",
                        false);

        MAX_IMAGE_SIZE = Defs.sysPropertyInt("org.icepdf.core.maxSmaskImageSize", MAX_IMAGE_SIZE);
    }

    /**
     * Decides whether a transparency-group form must be rasterised into an
     * offscreen buffer ({@link FormDrawCmd}) or can be painted inline as plain
     * shapes ({@link ShapesDrawCmd}).
     * <p>
     * A buffer is required only when the group carries a <i>group effect</i>
     * that cannot be reproduced by painting its shapes straight onto the page:
     * <ul>
     *   <li>a soft mask (the mask must be read back from a raster),</li>
     *   <li>a non-Normal blend mode applied to the group as a unit, or</li>
     *   <li>a group constant alpha in the open interval (0,1), which applies to
     *       the <i>composited</i> group and therefore needs it composited first.</li>
     * </ul>
     * Groups with none of these — including plain Normal, fully-opaque groups —
     * composite identically whether painted inline or via a buffer (Porter-Duff
     * <i>over</i> is associative), so they are painted inline; this also avoids
     * the resolution loss of buffering through an affine transform.
     * <p>
     * Size handling: a group is only buffered if it fits the offscreen-buffer
     * budget.  Groups within {@link #MAX_IMAGE_SIZE} buffer 1:1.  A larger
     * <i>blend-only</i> (no soft mask) group is down-scaled into the buffer, but
     * only up to {@link #MAX_SCALED_FORM_SIZE}; beyond that the bbox is treated
     * as an unbounded {@code +-Short.MAX_VALUE} sentinel (real content small and
     * clipped elsewhere) that would collapse if scaled, so it stays inline.
     * Soft-mask groups need a 1:1 buffer and are never down-scaled.
     * <p>
     * Behaviour-preserving refactor of the previous inline
     * {@code withinMaxSize}/{@code hasGroupEffect}/{@code oversizedBlendOnly}
     * predicate; see {@code TRANSPARENCY-GROUP-BLENDING-DESIGN.md}.
     *
     * @param form transparency-group form being placed by a {@code Do}.
     * @return true if the group must be rasterised to a buffer.
     */
    public static boolean requiresOffscreenBuffer(Form form) {
        ExtGState extGState = form.getExtGState();
        if (extGState == null) {
            return false;
        }
        double formWidth = form.getBBox().getWidth();
        double formHeight = form.getBBox().getHeight();
        // degenerate / sub-pixel groups: nothing meaningful to buffer.
        if (formWidth <= 1 || formHeight <= 1) {
            return false;
        }
        boolean hasSoftMask = extGState.getSMask() != null;
        Name blendingMode = extGState.getBlendingMode();
        boolean hasBlend = blendingMode != null
                && !blendingMode.equals(BlendComposite.NORMAL_VALUE);
        float ca = extGState.getNonStrokingAlphConstant();
        boolean hasPartialAlpha = ca > 0 && ca < 1;
        // Prototype (§5.2): an isolated group needs a transparent backdrop and a
        // knockout group needs its initial backdrop preserved -- both of which
        // only a buffer provides.  These flags are otherwise parsed but never
        // consulted in routing.  Gated off by default while corpus impact is
        // measured (a fully-opaque/all-Normal isolated group renders the same
        // inline, so this over-buffers until refined to require inner
        // transparency).
        boolean needsIsolation = isolationAwareRouting
                && (form.isIsolated() || form.isKnockOut());
        // No group effect -> inline; painting the shapes SRC_OVER onto the page
        // is identical to compositing them to a buffer first.
        if (!(hasSoftMask || hasBlend || hasPartialAlpha || needsIsolation)) {
            return false;
        }
        // A sentinel/extreme bbox (typically +-Short.MAX_VALUE) would collapse if
        // scaled into a buffer, so such forms stay inline regardless of effect.
        boolean realisticallySized = formWidth < MAX_SCALED_FORM_SIZE
                && formHeight < MAX_SCALED_FORM_SIZE;
        if (!realisticallySized) {
            return false;
        }
        // "Within budget" mirrors createBufferXObject's AREA clamp, not a
        // per-dimension cap: a group whose area fits MAX_IMAGE_SIZE^2 is
        // rasterised 1:1, so it can always be buffered -- including an
        // oversized-but-thin SMask group (e.g. WhiteGradient.pdf's 1867x2079
        // white-gradient fade, height just over 2000) that the old per-dimension
        // gate dropped to inline, silently discarding its luminosity mask.
        long area = (long) formWidth * (long) formHeight;
        long maxArea = (long) MAX_IMAGE_SIZE * MAX_IMAGE_SIZE;
        if (area <= maxArea) {
            return true;
        }
        // A soft-mask group must not be down-scaled (that mis-scales its mask),
        // so buffer a finite-mask group 1:1 up to the larger SMask ceiling;
        // createBufferXObject uses the same ceiling so it is NOT down-scaled.
        // Sentinel/shading-mask groups (unbounded mask bbox) are a separate,
        // unhandled case and keep the legacy inline path.  A blend-only group can
        // be down-scaled within MAX_SCALED_FORM_SIZE.
        if (hasSoftMask) {
            long maxSMaskArea = (long) MAX_SMASK_IMAGE_SIZE * MAX_SMASK_IMAGE_SIZE;
            return hasFiniteSoftMask(form) && area <= maxSMaskArea;
        }
        return hasBlend;
    }

    public FormDrawCmd(Form xForm) {
        this.xForm = xForm;
    }

    // A mask group bbox at/above this is the +-Short.MAX_VALUE sentinel of a
    // shading soft mask, whose 1:1/down-scaled buffering is a separate (unhandled)
    // case; such groups keep the legacy inline path.
    private static final double SMASK_SENTINEL_BBOX = 30000;

    /** True when this command's group carries a soft mask (from either the
     *  captured graphics state or the form's own ExtGState); such groups are
     *  buffered 1:1 up to {@link #MAX_SMASK_IMAGE_SIZE} rather than down-scaled. */
    private boolean hasSoftMaskGroup() {
        return hasFiniteSoftMask(xForm);
    }

    /** True when the group has a soft mask with a realistically sized (non-sentinel)
     *  mask group bbox -- the case the 1:1 SMask buffer path handles. */
    private static boolean hasFiniteSoftMask(Form form) {
        SoftMask sm = null;
        GraphicsState gs = form.getGraphicsState();
        if (gs != null && gs.getExtGState() != null) {
            sm = gs.getExtGState().getSMask();
        }
        if (sm == null && form.getExtGState() != null) {
            sm = form.getExtGState().getSMask();
        }
        if (sm == null || sm.getG() == null || sm.getG().getBBox() == null) {
            return false;
        }
        Rectangle2D mb = sm.getG().getBBox();
        return mb.getWidth() < SMASK_SENTINEL_BBOX && mb.getHeight() < SMASK_SENTINEL_BBOX;
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
                // A luminosity/alpha mask group form often carries an empty
                // /Resources and resolves its XObjects (e.g. a pre-rendered
                // grayscale drop-shadow image) through the enclosing content
                // form's resources.  Wire those before getG() initialises the
                // mask so it actually rasterises instead of collapsing to an
                // unmasked box.
                softMask.setParentResources(xForm.getLeafResources());
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
                formSoftMask.setParentResources(xForm.getLeafResources());
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

            // Frame the content buffer and the mask sub-buffer to the UNION of the
            // content and (finite, non-shading) mask bboxes so the two align 1:1;
            // x,y move to the union origin for the draw-back.  This keeps the
            // mask's soft feather where it extends past the content bbox (a drop
            // shadow's outer edge) instead of clipping it to a hard edge.
            SoftMask alignMask = softMask != null ? softMask : formSoftMask;
            if (alignMask != null && alignMask.getG() != null && !alignMask.getG().isShading()
                    && !xForm.isShading() && hasFiniteSoftMask(xForm)) {
                Rectangle2D mBox = alignMask.getG().getBBox();
                if (mBox != null) {
                    bufferFrame = bBox.createUnion(mBox);
                    x = (int) Math.floor(bufferFrame.getX());
                    y = (int) Math.floor(bufferFrame.getY());
                }
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
                // Inside the page-group buffer §10 is normally bypassed so a
                // separable (non-Normal) group blends against the live accumulating
                // buffer.  But a NON-isolated NORMAL group has no blend to interact
                // with the backdrop at draw-back, so rendering it isolated (over the
                // transparent seed) yields its bare, un-composited content -- a light
                // gradient that then washes the page (P100002589 turtle divider band,
                // forms 46/58).  Such groups still need §10's backdrop-aware
                // compositing, so keep it for Normal blends even inside the buffer.
                // Only a separable (non-Normal) group blend interacts with the
                // backdrop at draw-back (its BlendComposite reads the accumulated
                // buffer as the blend destination); for those the §10 reconstruction
                // is redundant inside the page-group buffer and is bypassed.  A
                // Normal / no-blend group draws back with AlphaComposite, which just
                // overlays its isolated content -- so it still needs §10's real
                // backdrop compositing or it washes the page (turtle forms 46/58).
                boolean separableGroupBlend = g.getComposite() instanceof BlendComposite;
                boolean pageGroupBypass = renderingIntoPageGroup.get() && separableGroupBlend;
                if (!hasMask && !annotationAppearance.get() && !pageGroupBypass
                        && backdropShapes != null
                        && isBackdropCompositeCandidate(xForm)) {
                    // §10 backdrop-aware compositing: render the group over its
                    // real page backdrop (reconstructed by replaying the stack),
                    // then remove the backdrop so the page is not composited
                    // twice.  Replaces the legacy white-fill backdrop proxy.
                    // Returns null when it declines (blank/white backdrop -- see
                    // isBlankBackdrop); fall back to the direct blended path.
                    xFormBuffer = compositeOverBackdrop(parentPage, xForm, renderingHints, normalBM, g, base);
                    usedBackdropComposite = xFormBuffer != null;
                }
                if (xFormBuffer == null) {
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
            bufferFrame = null;
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
        // A buffered Normal group whose inner content re-asserted the group's own
        // constant alpha already carries that ca in the buffer.  Some authoring
        // tools (e.g. Illustrator "stacked" gradient groups) set the same ca both
        // as the group ca -- applied here at the draw-back composite -- AND via a
        // gs inside the form, which bakes it into the buffer.  Compositing the
        // buffer back under the ca composite then applies it a second time (ca^2),
        // washing out glossy sheens (GH-501, p3 "Upgrade & Save" button).  Detect
        // it from the buffer itself: when the buffer's PEAK opacity already equals
        // the ca the draw-back would apply, the group's contribution is fully
        // represented, so draw it straight (SRC_OVER) and let ca land exactly once.
        // A group with opaque content (peak 255 > ca) genuinely needs the ca at
        // draw-back and is left untouched (e.g. WhiteGradient.pdf's ca=0.8 fade).
        if (savedComposite == null && xFormBuffer != null
                && g.getComposite() instanceof java.awt.AlphaComposite) {
            java.awt.AlphaComposite ac = (java.awt.AlphaComposite) g.getComposite();
            if (ac.getRule() == java.awt.AlphaComposite.SRC_OVER && ac.getAlpha() < 1f) {
                int target = Math.round(ac.getAlpha() * 255);
                if (Math.abs(bufferPeakAlpha(xFormBuffer) - target) <= 2) {
                    savedComposite = g.getComposite();
                    g.setComposite(java.awt.AlphaComposite.SrcOver);
                }
            }
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

    // Peak (maximum) alpha found in a buffer's pixels, sampled on a stride so a
    // large buffer is scanned cheaply.  Used to tell whether a group's content
    // already carries its constant alpha (peak == ca) or is opaque (peak == 255).
    private static int bufferPeakAlpha(BufferedImage img) {
        int w = img.getWidth(), h = img.getHeight();
        int stepX = Math.max(1, w / 256), stepY = Math.max(1, h / 256);
        int peak = 0;
        for (int yy = 0; yy < h; yy += stepY) {
            for (int xx = 0; xx < w; xx += stepX) {
                int a = (img.getRGB(xx, yy) >>> 24) & 0xff;
                if (a > peak) {
                    peak = a;
                    if (peak == 255) {
                        return 255;
                    }
                }
            }
        }
        return peak;
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

    // Set while the page content is being rendered into a page-level transparency
    // group buffer (Page.paintPageGroupBuffered, for DeviceCMYK page groups).
    // Inside that buffer the groups must blend against the live accumulating
    // buffer pixels (the direct blended path), so the §10 backdrop-composite and
    // its decline gate -- which reconstruct a separate white-seeded backdrop --
    // must be bypassed: the buffer IS the backdrop, reconstruction is redundant
    // and wrong there.  This is what lets a darkening group (e.g. a ColorBurn
    // fill) darken the accumulated content instead of washing over white.
    private static final ThreadLocal<Boolean> renderingIntoPageGroup =
            ThreadLocal.withInitial(() -> Boolean.FALSE);

    /** Toggle the page-group-buffer §10 bypass (see field). */
    public static void setRenderingIntoPageGroup(boolean rendering) {
        renderingIntoPageGroup.set(rendering);
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
        return captureBackdrop(g, base, w, h, false);
    }

    /**
     * As {@link #captureBackdrop(Graphics2D, AffineTransform, int, int)}, but when
     * {@code skipFormGroups} is true the sibling transparency groups are excluded
     * from the replay (see {@link Shapes#paintBackdrop(Graphics2D, int, boolean)}).
     * Used by the §10 decline gate to evaluate the true page backdrop independent
     * of the other groups in the same stack.
     */
    private BufferedImage captureBackdrop(Graphics2D g, AffineTransform base, int w, int h, boolean skipFormGroups) {
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
            backdropShapes.paintBackdrop(bg, backdropIndex, skipFormGroups);
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
        // GH-501 step 2: for a DeviceCMYK group, preserve true CMYK samples and
        // capture them aligned to the group buffer so the blend can run
        // subtractively from real ink instead of the lossy sRGB.
        boolean cmykGroup = cmykSubtractiveEnabled && isCmykGroup(xForm);
        boolean prevPreserve = cmykGroup && ImageUtility.setPreserveCmyk(true);
        capturedInk = null;
        BufferedImage isolated;
        try {
            isolated = createBufferXObject(parentPage, xForm, null, hints, normalBM, cmykGroup);
        } finally {
            BlendComposite.setTransparentBackdrop(prevBackdrop);
            if (cmykGroup) {
                ImageUtility.setPreserveCmyk(prevPreserve);
            }
        }
        BufferedImage backdrop = captureBackdrop(g, base, isolated.getWidth(), isolated.getHeight());
        if (backdrop == null) {
            return isolated;
        }
        Name blend = xForm.getExtGState() != null ? xForm.getExtGState().getBlendingMode() : null;
        float caRaw = xForm.getExtGState() != null ? xForm.getExtGState().getNonStrokingAlphConstant() : -1f;
        float ca = (caRaw < 0f || caRaw > 1f) ? 1f : caRaw;
        // Decline §10 only for the precise case it destroys: an OPAQUE lightening
        // group over an effectively blank (white) page backdrop.  Such a blend
        // collapses B(white,Cs) -> white (ColorDodge/Screen/Overlay/ColorBurn/
        // Lighten), erasing fully-opaque content -- 978-9-7315-0059-9_1.pdf is an
        // all-CMYK stack of exactly these.  Falling back to the direct blended path
        // restores the geometry.  Translucent lightening groups (ca < 1, e.g.
        // transparency_design.pdf's Overlay/ColorDodge at ca .5-.71) are left to
        // §10 -- it renders their soft pastel result correctly, and declining would
        // turn them into harsh dark halos.  Darkening/Normal groups and groups over
        // genuine backdrop content are likewise unaffected.
        //
        // The blank test uses a backdrop that EXCLUDES the sibling transparency
        // groups (skipFormGroups): every group in the same stack then sees the
        // same true page backdrop, so the decision is stack-wide and consistent
        // rather than order-dependent.  Without this, an earlier group's painted
        // rings darken later groups' backdrops below the blank threshold, so those
        // re-engage §10 and wash out (978 recovers only partially).
        if (ca >= 0.99f && isWhiteWashingBlend(blend)) {
            BufferedImage gateBackdrop =
                    captureBackdrop(g, base, isolated.getWidth(), isolated.getHeight(), true);
            boolean blankPage = gateBackdrop == null || isBlankBackdrop(gateBackdrop);
            if (gateBackdrop != null) {
                gateBackdrop.flush();
            }
            if (blankPage) {
                isolated.flush();
                backdrop.flush();
                return null;
            }
        }
        // True-ink subtractive path when CMYK samples were captured; otherwise the
        // additive sRGB path (unchanged for RGB/ICC groups and any CMYK group whose
        // image decoded before preservation was enabled).
        BufferedImage result = (capturedInk != null && capturedInk.isCaptured())
                ? composeCmykContribution(isolated, capturedInk, backdrop, blend, ca)
                : composeContribution(isolated, backdrop, blend, ca);
        backdrop.flush(); // transient -- contribution is now in the result buffer
        return result;
    }

    // A reconstructed backdrop whose mean luminance is at/above this (0-255) is
    // treated as effectively blank white paper -- there is no meaningful content
    // behind the group to composite against, and a lightening blend over it would
    // only wash the group out.  Mean luminance (not a non-white pixel count) is
    // used deliberately: it stays near-white as sparse group content accumulates
    // into later groups' backdrops, so the decline is stable across the stack
    // rather than order-dependent.  A genuine colour/gradient backdrop sits far
    // below this.
    private static final double BLANK_BACKDROP_MIN_MEAN_LUM =
            Defs.doubleProperty("org.icepdf.core.blankBackdropMeanLum", 245d);

    /** True when {@code backdrop} is effectively uniform white paper (mean
     *  luminance at/above {@link #BLANK_BACKDROP_MIN_MEAN_LUM}), i.e. the group
     *  sits over no meaningful reconstructed content. */
    private static boolean isBlankBackdrop(BufferedImage backdrop) {
        int w = backdrop.getWidth(), h = backdrop.getHeight();
        long total = (long) w * h;
        if (total == 0) {
            return true;
        }
        double sumLum = 0;
        int[] row = new int[w];
        for (int y = 0; y < h; y++) {
            backdrop.getRGB(0, y, w, 1, row, 0, w);
            for (int p : row) {
                sumLum += 0.299 * ((p >> 16) & 0xff) + 0.587 * ((p >> 8) & 0xff) + 0.114 * (p & 0xff);
            }
        }
        return sumLum / total >= BLANK_BACKDROP_MIN_MEAN_LUM;
    }

    /** True for blend modes that collapse to white when the backdrop is white --
     *  B(white, Cs) ~ white.  Over a blank backdrop these erase opaque group
     *  content, so §10 is declined for them (see {@link #compositeOverBackdrop}).
     *  ColorBurn is included: ColorBurn(1, Cs) = 1 - min(1, 0/Cs) = 1. */
    private static boolean isWhiteWashingBlend(Name blend) {
        return blend != null && (
                blend.equals(BlendComposite.COLOR_DODGE_VALUE)
                        || blend.equals(BlendComposite.SCREEN_VALUE)
                        || blend.equals(BlendComposite.COLOR_BURN_VALUE)
                        || blend.equals(BlendComposite.OVERLAY_VALUE)
                        || blend.equals(BlendComposite.LIGHTEN_VALUE));
    }

    /** A DeviceCMYK transparency group (the raster-level subtractive path's scope;
     *  ICCBased-CMYK groups are left to the additive path for now). */
    private static boolean isCmykGroup(Form xForm) {
        if (xForm.getGroup() == null) {
            return false;
        }
        Object cs = xForm.getLibrary().getObject(xForm.getGroup(), new Name("CS"));
        return cs instanceof Name && ((Name) cs).equals(DeviceCMYK.DEVICECMYK_KEY);
    }

    /**
     * §11.3.5 subtractive compositing from TRUE CMYK ink (GH-501 step 2).  Builds
     * the per-pixel source ink from the captured samples where a CMYK image landed
     * (the real K), falling back to the sRGB-recovered pure-CMY ink elsewhere (K=0,
     * which blends identically to the additive path), then hands off to
     * {@link CmykGroupCompositor}.  Returns the group contribution to draw back
     * SRC_OVER -- the same contract as {@link #composeContribution}.
     */
    // GH-501 step 2 diagnostic: number of groups composited via the true-ink
    // subtractive path (so a test can confirm the path engaged).
    public static volatile int cmykSubtractiveComposites = 0;

    private BufferedImage composeCmykContribution(BufferedImage isolated, ImageUtility.CmykInkSink sink,
                                                  BufferedImage backdrop, Name blend, float ca) {
        cmykSubtractiveComposites++;
        int w = isolated.getWidth(), h = isolated.getHeight(), n = w * h;
        byte[] srcInk = new byte[n * 4];
        int[] srcAlpha = new int[n];
        int[] backdropArgb = new int[n];
        int[] isoRow = new int[w];
        int[] bRow = new int[w];
        java.awt.image.WritableRaster ink = sink.getInk();
        java.awt.image.WritableRaster cov = sink.getCoverage();
        for (int yy = 0; yy < h; yy++) {
            isolated.getRGB(0, yy, w, 1, isoRow, 0, w);
            backdrop.getRGB(0, yy, w, 1, bRow, 0, w);
            for (int xx = 0; xx < w; xx++) {
                int i = yy * w + xx;
                int argb = isoRow[xx];
                srcAlpha[i] = argb >>> 24;
                backdropArgb[i] = bRow[xx];
                int p = i * 4;
                if ((cov.getSample(xx, yy, 0) & 0xFF) > 127) {
                    srcInk[p] = (byte) ink.getSample(xx, yy, 0);
                    srcInk[p + 1] = (byte) ink.getSample(xx, yy, 1);
                    srcInk[p + 2] = (byte) ink.getSample(xx, yy, 2);
                    srcInk[p + 3] = (byte) ink.getSample(xx, yy, 3);
                } else {
                    // no captured ink here -> sRGB-recovered pure-CMY (K=0)
                    srcInk[p] = (byte) (255 - ((argb >> 16) & 0xFF));
                    srcInk[p + 1] = (byte) (255 - ((argb >> 8) & 0xFF));
                    srcInk[p + 2] = (byte) (255 - (argb & 0xFF));
                    srcInk[p + 3] = 0;
                }
            }
        }
        return CmykGroupCompositor.compose(srcInk, srcAlpha, backdropArgb,
                toBlendingMode(blend), ca, w, h);
    }

    /**
     * Builds the group's contribution to draw back with SRC_OVER (spec-correct
     * draw-back).  Given the isolated group result ({@code Cs, ag}) and the real
     * backdrop {@code Cb}, emit colour {@code B(Cb,Cs)} (the group's own blend) at
     * alpha {@code ca*ag}, so SRC_OVER over the page (= Cb) yields the §11.4.6
     * result {@code (1-ca*ag)*Cb + ca*ag*B(Cb,Cs)} -- the group's blend and
     * constant alpha applied to its real backdrop, no double-count.
     */
    // The colour-space math for group compositing (GH-501).  composeContribution
    // works on the group's *sRGB* isolated buffer, and a DeviceCMYK group cannot be
    // blended subtractively from there: once the content is decoded to sRGB the
    // black (K) channel is gone, and recovering CMYK from sRGB collapses the
    // chromatic channels straight back to an RGB blend -- proven against three
    // reference renderers, where the sRGB-recovered "subtractive" path fabricated a
    // K channel and shifted pattern_and_CYMK_jpeg.pdf orange->green (Acrobat/Chrome/
    // Firefox all show orange).  So this path stays additive; genuine subtractive
    // blending needs the *true* preserved CMYK samples (ImageUtility.getCmykSamples)
    // composited at the raster level (the 3a/n finding), which is the next step.
    // CmykBlendingSpace holds the §11.3.5 ink-complement math, staged for that path.
    private static final BlendingSpace blendingSpace = RgbBlendingSpace.INSTANCE;

    private static BufferedImage composeContribution(BufferedImage isolated, BufferedImage backdrop,
                                                     Name blend, float ca) {
        BlendingSpace space = blendingSpace;
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
            // A luminosity mask that renders to zero luminosity everywhere would
            // erase the whole group (applyExplicitSMask/Outline read the mask's red
            // channel as the alpha weight).  That happens when the mask group's own
            // bbox differs from the content's and createBufferXObject's oversized
            // clamp renders the mask content off its (content-sized) buffer -- e.g.
            // "Java Magazine" cover, form 1370's title-dimming box whose mask group
            // 1366 has a wider, offset bbox, so the buffered mask comes back fully
            // transparent and the dark box vanishes.  A genuine "hide everything"
            // group draws nothing and is never authored, so a zero mask is a
            // misrender: skip it and keep the unmasked content, matching the
            // pre-GH-495 inline path (which ignored the mask for such oversized
            // groups) -- the "close-enough" result.  Correctly rendered masks
            // (WhiteGradient.pdf's fade, etc.) carry non-zero luminosity and are
            // unaffected.
            if (maskContributesNothing(sMaskBuffer)) {
                sMaskBuffer.flush();
                return xFormBuffer;
            }
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
     * True when a luminosity soft-mask buffer would contribute zero everywhere --
     * no pixel has a non-zero red (luminosity) channel, so applying it as a soft
     * mask erases the group entirely.  This flags a misrendered mask (typically an
     * oversized mask group whose content landed off its content-clamped buffer),
     * not a legitimate effect; the caller skips the mask and keeps the content.
     * Early-exits on the first contributing pixel, so a valid mask costs one row.
     */
    static boolean maskContributesNothing(BufferedImage mask) {
        if (mask == null) {
            return false;
        }
        int w = mask.getWidth(), h = mask.getHeight();
        int[] row = new int[w];
        for (int y = 0; y < h; y++) {
            mask.getRGB(0, y, w, 1, row, 0, w);
            for (int p : row) {
                if (((p >> 16) & 0xff) != 0) {
                    return false;
                }
            }
        }
        return true;
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
        return createBufferXObject(parentPage, xForm, softMask, renderingHints, isMask, false);
    }

    // GH-501 step 2: the ink sink captured during the most recent capturing
    // createBufferXObject call (true CMYK samples blitted aligned to the group
    // buffer), consumed by compositeOverBackdrop for subtractive compositing.
    private ImageUtility.CmykInkSink capturedInk;

    private BufferedImage createBufferXObject(Page parentPage, Form xForm, SoftMask softMask,
                                              RenderingHints renderingHints, boolean isMask,
                                              boolean captureCmykInk) {
        Rectangle2D bBox = xForm.getBBox();
        int width = (int) bBox.getWidth();
        int height = (int) bBox.getHeight();
        double scale = 1.0;
        int bufferWidth;
        int bufferHeight;
        if (bufferFrame != null) {
            // Finite soft-mask group: frame the content and mask buffers to the
            // common UNION bbox, 1:1, so applyMask overlays them aligned.
            bufferWidth = Math.max(1, (int) Math.ceil(bufferFrame.getWidth()));
            bufferHeight = Math.max(1, (int) Math.ceil(bufferFrame.getHeight()));
            formScale = 1.0;
            formWidth = bufferWidth;
            formHeight = bufferHeight;
        } else if (isMask && xForm == this.xForm && xFormBuffer != null) {
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
            // A soft-mask group is buffered 1:1 up to the larger SMask ceiling
            // (down-scaling mis-scales its mask); other groups keep MAX_IMAGE_SIZE.
            long maxArea = hasSoftMaskGroup()
                    ? (long) MAX_SMASK_IMAGE_SIZE * MAX_SMASK_IMAGE_SIZE
                    : (long) MAX_IMAGE_SIZE * MAX_IMAGE_SIZE;
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
                    // Translate to the buffer frame origin (the union bbox when
                    // framing a masked group, else the form's own bbox), but clip
                    // to the form's own bbox so its content stays within its extent.
                    Rectangle2D frame = bufferFrame != null ? bufferFrame : bBox;
                    canvas.translate(-(int) frame.getX(), -(int) frame.getY());
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
                if (captureCmykInk) {
                    ImageUtility.beginCmykInkCapture(bufferWidth, bufferHeight);
                }
                try {
                    xFormShapes.paint(canvas);
                } finally {
                    if (captureCmykInk) {
                        capturedInk = ImageUtility.endCmykInkCapture();
                    }
                }
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
