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
package org.icepdf.core.pobjects.graphics;

import org.icepdf.core.pobjects.Page;
import org.icepdf.core.pobjects.graphics.commands.*;
import org.icepdf.core.pobjects.graphics.text.PageText;
import org.icepdf.core.util.Defs;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * <p>The Shapes class hold all object that are parsed from a Page's content
 * streams.  These contained object make up a pages graphics stack which can
 * be iterated through to paint a page's content.<p>
 * <br>
 * <p>This class is generally only used by the Content parser during content
 * parsing.  The class also stores points to the images found in the content
 * as well as the text that is encoded on a page.</p>
 *
 * @since 1.0
 */
public class Shapes {

    private static final Logger logger =
            Logger.getLogger(Shapes.class.getName());

    private static int shapesInitialCapacity = 5000;
    // disables alpha painting.
    protected boolean paintAlpha =
            !Defs.sysPropertyBoolean("org.icepdf.core.paint.disableAlpha", false);

    static {
        shapesInitialCapacity = Defs.sysPropertyInt(
                "org.icepdf.core.shapes.initialCapacity", shapesInitialCapacity);
    }

    // cache of common draw state, we try to avoid adding new operands if the
    // stack already has the needed state,  more ops take longer to paint.
    private int rule;
    private float alpha;

    private float pageRotation;

    // Graphics stack for a page's content.
    protected final ArrayList<DrawCmd> shapes = new ArrayList<>(shapesInitialCapacity);

    // stores the state of the currently visible optional content.
    protected final OptionalContentState optionalContentState = new OptionalContentState();

    // the collection of objects listening for page paint events
    private Page parentPage;

    // page-level transparency group (/Group on the page dict), null when the page
    // is not a transparency group.  Consumed by the page-group buffer compositor.
    private TransparencyGroup pageGroup;

    // text extraction data structure
    private final PageText pageText = new PageText();

    public PageText getPageText() {
        return pageText;
    }

    /**
     * Gets the number of shapes on the shapes stack.
     *
     * @return number of shapes on the stack
     */
    public int getShapesCount() {
        return shapes.size();
    }

    public ArrayList<DrawCmd> getShapes() {
        return shapes;
    }

    public void add(ArrayList<DrawCmd> shapes) {
        this.shapes.addAll(shapes);
    }

    public void setPageParent(Page parent) {
        parentPage = parent;
    }

    /**
     * The page-level transparency group ({@code /Group} on the page dictionary),
     * or null when the page is not itself a transparency group.
     */
    public TransparencyGroup getPageGroup() {
        return pageGroup;
    }

    public void setPageGroup(TransparencyGroup pageGroup) {
        this.pageGroup = pageGroup;
    }

    public void add(DrawCmd drawCmd){

        if (!(drawCmd instanceof FormDrawCmd)){
            shapes.add(drawCmd);
        }else{
            shapes.add(drawCmd);
        }
    }

    public boolean isPaintAlpha() {
        return paintAlpha;
    }

    public void setPaintAlpha(boolean paintAlpha) {
        this.paintAlpha = paintAlpha;
    }

    /**
     * Disable BlendComposites for compatibility with x11 windowing system that fail to paint this blending type.
     * This is generally on done for annotation appearance streams were the numbers of shapes is quite small compared
     * to a shapes associated with a page.
     * Work around is to use -Dsun.java2d.opengl=true when available
     */
    public void disableBlendComposite() {
        DrawCmd nextShape;
        for (DrawCmd shape : shapes) {
            nextShape = shape;
            if (nextShape instanceof BlendCompositeDrawCmd) {
                ((BlendCompositeDrawCmd) nextShape).enableAlphaCompositePaint();
            }
        }
    }

    /**
     * Paint the graphics stack to the graphics context
     *
     * @param g graphics context to paint to.
     * @throws InterruptedException thread interrupted.
     */
    public void paint(Graphics2D g) throws InterruptedException {
        paint(g, parentPage, paintAlpha);
    }

    /**
     * Paint the graphics stack to the graphics context using the supplied parent
     * page and alpha flag rather than this Shapes' instance fields.
     * <p>
     * The same nested Shapes object is shared across every concurrent paint of a
     * cached page display list, so the parent page and alpha flag must be passed
     * as call-local parameters here instead of being written into (and reset on)
     * the shared instance.  The previous set/paint/reset-to-null pattern let one
     * thread null {@code parentPage} mid-paint of another, which NPE'd a draw
     * command; that exception was swallowed below and the rest of the form's
     * shapes were skipped -- i.e. missing content on a concurrently rendered page.
     *
     * @param g          graphics context to paint to.
     * @param parentPage parent page for this paint (call-local, not stored).
     * @param paintAlpha whether alpha compositing draw commands are honoured.
     * @throws InterruptedException thread interrupted.
     */
    public void paint(Graphics2D g, Page parentPage, boolean paintAlpha) throws InterruptedException {
        try {
            AffineTransform base = new AffineTransform(g.getTransform());
            Shape clip = g.getClip();

            PaintTimer paintTimer = new PaintTimer();
            Shape previousShape = null;

            DrawCmd nextShape;
            // for loops actually faster in this case.
            for (int i = 0, max = shapes.size(); i < max; i++) {
                // try and minimize interrupted checks, costly.
                if (i % 1000 == 0 && Thread.currentThread().isInterrupted()) {
                    throw new InterruptedException("Page painting thread interrupted");
                }

                nextShape = shapes.get(i);
                previousShape = nextShape.paintOperand(g, parentPage,
                        previousShape, clip, base, optionalContentState, paintAlpha, paintTimer);
            }
        }
        catch (InterruptedException e){
            throw new InterruptedException(e.getMessage());
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error painting shapes.", e);
        }
    }


    /**
     * Replays the draw commands in [0, uptoIndex) into {@code g} -- i.e. paints
     * the page/parent stack as it stood just before the command at
     * {@code uptoIndex}.  Used to reconstruct the *backdrop* behind a
     * non-isolated transparency group without reading back the page raster
     * (see FormDrawCmd backdrop capture).  The caller sets {@code g}'s transform
     * and clip; this captures {@code base} from it exactly like {@link #paint}.
     */
    public void paintBackdrop(Graphics2D g, Page parentPage, int uptoIndex) throws InterruptedException {
        paintBackdrop(g, parentPage, uptoIndex, false);
    }

    /**
     * As {@link #paintBackdrop(Graphics2D, int)}, but when {@code skipFormGroups}
     * is true the prior {@link FormDrawCmd} commands (sibling transparency groups)
     * are NOT replayed.  This yields the page backdrop as it stands ignoring the
     * other groups in the same transparency stack -- used by the §10 decline gate
     * so the "is the backdrop blank?" decision is identical for every group in the
     * stack (otherwise an earlier group's painted content darkens later groups'
     * backdrops, making the per-group decision order-dependent).
     */
    public void paintBackdrop(Graphics2D g, Page parentPage, int uptoIndex, boolean skipFormGroups)
            throws InterruptedException {
        AffineTransform base = new AffineTransform(g.getTransform());
        Shape clip = g.getClip();
        PaintTimer paintTimer = new PaintTimer();
        Shape previousShape = null;
        int max = Math.min(uptoIndex, shapes.size());
        for (int i = 0; i < max; i++) {
            DrawCmd cmd = shapes.get(i);
            if (skipFormGroups && cmd instanceof FormDrawCmd) {
                continue;
            }
            previousShape = cmd.paintOperand(g, parentPage,
                    previousShape, clip, base, optionalContentState, paintAlpha, paintTimer);
        }
    }

    /**
     * Iterates over the Shapes objects extracting all Image objects.
     *
     * @return all images in a page's content, if any.
     * @throws InterruptedException thread interrupted.
     */
    public ArrayList<Image> getImages() throws InterruptedException {
        ArrayList<Image> images = new ArrayList<>();
        for (Object object : shapes) {
            if (object instanceof ImageDrawCmd) {
                images.add(((ImageDrawCmd) object).getImage());
            } else if (object instanceof ShapesDrawCmd) {
                images.addAll(((ShapesDrawCmd) object).getShapes().getImages());
            }
        }
        return images;
    }

    /**
     * Contracts the shapes ArrayList to the actual size of the elements
     * it contains.
     */
    public void contract() {
        shapes.trimToSize();
    }

    public int getRule() {
        return rule;
    }

    public void setRule(int rule) {
        this.rule = rule;
    }

    public float getAlpha() {
        return alpha;
    }

    public void setAlpha(float alpha) {
        this.alpha = alpha;
    }

    public float getRotation() {
        return pageRotation;
    }

    public void setRotation(float pageRotation) {
        pageText.setPageRotation(pageRotation);
        this.pageRotation = pageRotation;
    }
}
