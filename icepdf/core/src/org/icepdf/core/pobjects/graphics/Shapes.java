/*
 * Version: MPL 1.1/GPL 2.0/LGPL 2.1
 *
 * "The contents of this file are subject to the Mozilla Public License
 * Version 1.1 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
 * License for the specific language governing rights and limitations under
 * the License.
 *
 * The Original Code is ICEpdf 3.0 open source software code, released
 * May 1st, 2009. The Initial Developer of the Original Code is ICEsoft
 * Technologies Canada, Corp. Portions created by ICEsoft are Copyright (C)
 * 2004-2009 ICEsoft Technologies Canada, Corp. All Rights Reserved.
 *
 * Contributor(s): _____________________.
 *
 * Alternatively, the contents of this file may be used under the terms of
 * the GNU Lesser General Public License Version 2.1 or later (the "LGPL"
 * License), in which case the provisions of the LGPL License are
 * applicable instead of those above. If you wish to allow use of your
 * version of this file only under the terms of the LGPL License and not to
 * allow others to use your version of this file under the MPL, indicate
 * your decision by deleting the provisions above and replace them with
 * the notice and other provisions required by the LGPL License. If you do
 * not delete the provisions above, a recipient may use your version of
 * this file under either the MPL or the LGPL License."
 *
 */
package org.icepdf.core.pobjects.graphics;

import org.icepdf.core.pobjects.Form;
import org.icepdf.core.pobjects.Page;
import org.icepdf.core.pobjects.graphics.text.PageText;
import org.icepdf.core.util.Defs;
import org.icepdf.core.views.swing.PageViewComponentImpl;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * <p>The Shapes class hold all object that are parsed from a Page's content
 * streams.  These contained object make up a pages graphics stack which can
 * be interated through to paint a page's content.<p>
 * <p/>
 * <p>This class is genearlly only used by the Content parser during content
 * parsing.  The class also stores points to the images found in the content
 * as well as the text that is encoded on a page.</p>
 *
 * @since 1.0
 */
public class Shapes {

    private static final Logger logger =
            Logger.getLogger(Shapes.class.toString());

    private static int paintDelay = 200;

    // allow scaling of large images to improve clarity on screen
    private static boolean scaleImages;

    static {
        // decide if large images will be scaled
        scaleImages =
                Defs.sysPropertyBoolean("org.icepdf.core.scaleImages",
                        true);

        try {
            // Delay between painting calls.
            paintDelay =
                    Defs.intProperty("org.icepdf.core.views.refreshfrequency",
                            200);
        } catch (NumberFormatException e) {
            logger.log(Level.FINE, "Error reading buffered scale factor");
        }
    }

    // Graphics stack for a page's content.
    protected Vector<Object> shapes = new Vector<Object>(1000);
    // Vector of images found a page.
    private Vector<Image> images = new Vector<Image>();
    // last colour used during the painting process, avoid unnecessary additions
    // to the stack.
//    private Color lastColor;
    // last basic stroke used, avoids unnecessary additions to the stack
//    private BasicStroke lastBasicStroke;

    // the collection of objects listening for page paint events
    private Page parentPage;

    // text extraction data structure
    private PageText pageText = new PageText();

    /**
     * Classes used to make the paint process a little easier, when poping object
     * off the stack.
     */
    class Draw {
    }

    class Fill {
    }

    class Clip {
    }

    class NoClip {
    }

    public Shapes() {

    }

    public PageText getPageText(){
        return pageText;
    }

    /**
     * Gets the number of shapes on the shapes stack.
     *
     * @return number of shapes on the stack
     */
    public int getShapesCount() {
        if (shapes != null) {
            return shapes.size();
        } else {
            return 0;
        }
    }

    public void setPageParent(Page parent) {
        parentPage = parent;
    }

    /**
     * Clean up.
     */
    public void dispose() {
        //System.out.println("   Shapes Images vector size " + images.size());
        for (Image image : images) {
            image.flush();
        }
        // one more try to free up some memory
        images.clear();
        images.trimToSize();

        //System.out.println("   Shapes Shapes vector  size " + images.size());
        if (shapes != null){
            for (Object tmp : shapes) {
                if (tmp instanceof Image) {
                    //System.out.println("  -------------> Found images");
                    Image image = (Image) tmp;
                    image.flush();
                } else if (tmp instanceof TextSprite) {
                    ((TextSprite) tmp).dispose();
                }else if (tmp instanceof Shapes) {
                    ((Shapes) tmp).dispose();
                }  else {
                    //System.out.println("  -------------> Found other " + shapes.size());
                    //tmp = null;
                }
            }
            shapes.clear();
            shapes = null;
        }

        if (pageText != null){
            pageText.dispose();
            pageText = null;
        }
    }

    /**
     * Add a new object to the Shapes stack.
     *
     * @param o object to add to the graphics stack.
     */
    public void add(Object o) {
        // if we have an new image we'll check to see if scaling is enabled
        if (o instanceof Image) {
            Image image = (Image) o;
            int width = image.getWidth(null);
            Image scaledImage;
            // do image scaling on larger images.  This improves the softness
            // of some images that contains black and white text.
            if (scaleImages) {
                double scaleFactor = 1.0;
                if (width > 1000 && width < 1500) {
                    scaleFactor = 0.75;
                } else if (width > 1500) {
                    scaleFactor = 0.5;
                }
                if (scaleFactor < 1.0) {
                    scaledImage = image.getScaledInstance(
                            (int) (width * scaleFactor), -1, Image.SCALE_SMOOTH);
                    image.flush();
                } else {
                    scaledImage = image;
                }
            } else {
                scaledImage = image;
            }
            images.add(scaledImage);
            shapes.add(scaledImage);
            return;
        }

        // this allows us to capture images from an xObject.  We unwrap
        // the images from the xobject shapes vector, otherwise we have no
        // way to extract them. 
        if (o instanceof Vector) {
            Vector tmp = (Vector) o;
            Iterator iterator = tmp.iterator();
            Object tmpImage;
            while (iterator.hasNext()) {
                tmpImage = iterator.next();
                if (tmpImage instanceof Image) {
                    images.addElement((Image)tmpImage);
                }
            }
        }
        // copy any shapes fro xForms.
        if (o instanceof Shapes){
            Shapes tmp = (Shapes) o;
            pageText.getPageLines().addAll(tmp.getPageText().getPageLines());
        }
        shapes.add(o);

    }

    /**
     * Adds a new draw command to the graphics stack.  When the paint method encounters this
     * object the current geometric shape is drawn.
     */
    public void addDrawCommand() {
        shapes.add(new Draw());
    }

    /**
     * Adds a new fill command to the graphics stack. When the paint method encouters this
     * object the current geometric shape is filled with the current fill colour.
     */
    public void addFillCommand() {
        shapes.add(new Fill());
    }

    /**
     * Adds a new clip command to the graphics stack.  When the paint method
     * encounters this object the current geometic shape is used as the new
     * clip shape.
     */
    public void addClipCommand() {
        shapes.add(new Clip());
    }

    /**
     * Adds a new no clip command to the graphics stack.
     */
    public void addNoClipCommand() {
        shapes.add(new NoClip());
    }

    /**
     * Paint the graphics stack to the graphics context
     *
     * @param g graphics context to paint to.
     */
    public synchronized void paint(Graphics2D g) {
        paint(g, null);
    }

    /**
     * Paint the graphics stack to the graphics context
     *
     * @param g graphics context to paint to.
     * @param pagePainter parent page painter
     */
    public synchronized void paint(Graphics2D g, PageViewComponentImpl.PagePainter pagePainter) {
        
        // disable clipping, helps with printing issues on windows where the
        // clip can sometimes blank a whole page.  This should only be used as
        // a lost resort.  Buffering to an image is another way to avoid the clip
        // problem.
        boolean disableClipping =
                Defs.sysPropertyBoolean("org.icepdf.core.paint.disableClipping",
                        false);
        // disables alpha painting.
        boolean disableAlpha =
                Defs.sysPropertyBoolean("org.icepdf.core.paint.disableAlpha",
                        false);
        Shape shape = null;
        AffineTransform base = new AffineTransform(g.getTransform());
        Shape clip = g.getClip();

//        int rule = AlphaComposite.SRC_OVER;
//        float alpha = 1.0f;
//        g.setComposite(AlphaComposite.getInstance(rule, alpha));

        Object nextShape;
        Area clipArea = new Area(g.getClip());
//        System.out.println("Shapes vector  size " + shapes.size() + " " + currentClip);
//        long startTime = System.currentTimeMillis();
        long currentTime;
        long lastPaintTime = System.currentTimeMillis();

//        int paintCount = 0;
        Iterator<Object> shapesEnumeration = shapes.iterator();
        try {
            while (shapesEnumeration.hasNext() ||
                    (pagePainter != null && pagePainter.isStopPaintingRequested())) {

//                if (pagePainter != null && pagePainter.isStopPaintingRequested()){
//                    break;
//                }

                nextShape = shapesEnumeration.next();

                if (nextShape instanceof TextSprite) {
                    if (((TextSprite) nextShape).intersects(clipArea)) {
                        ((TextSprite) nextShape).paint(g);
                        // Send a PaintPage Event to listeners
                        currentTime = System.currentTimeMillis();
                        if (currentTime - lastPaintTime > paintDelay) {
//                            paintCount++;
                            lastPaintTime = currentTime;
                            parentPage.notifyPaintPageListeners();
                        }
                    }
                } else if (nextShape instanceof Shape) {
                    shape = (Shape) nextShape;
                } else if (nextShape instanceof Fill) {
                    if (clipArea.intersects(shape.getBounds2D())) {
                        g.fill(shape);
                        // Send a PaintPage Event to listeners
                        currentTime = System.currentTimeMillis();
                        if (currentTime - lastPaintTime > paintDelay) {
//                            paintCount++;
                            lastPaintTime = currentTime;
                            parentPage.notifyPaintPageListeners();
                        }
                    }
                } else if (nextShape instanceof AffineTransform) {
                    AffineTransform af = new AffineTransform(base);
                    af.concatenate((AffineTransform) nextShape);
                    g.setTransform(af);
                    // update current clip shape
                    if (g.getClip() != null)
                        clipArea = new Area(g.getClip());
                } else if (nextShape instanceof AlphaComposite &&
                        !disableAlpha) {
                    g.setComposite((AlphaComposite) nextShape);
                } else if (nextShape instanceof Paint) {
                    g.setPaint((Paint) nextShape);
                } else if (nextShape instanceof Clip) {
                    // Capture the current af for the
                    //  page
                    AffineTransform af = new AffineTransform(g.getTransform());
                    // Set the transform to the base, which is fact where the page
                    // lies in the viewport, very dynamic.
                    g.setTransform(base);
                    // apply the clip, which is always the initial paper size,
                    g.setClip(clip);
                    // apply the af, which places the clip in the correct location
                    g.setTransform(af);
                    if (shape != null && !disableClipping) {
                        // clip outline
                        //                    g.setComposite(AlphaComposite.getInstance(rule, 1.0f));
                        //                    Color tmp = g.getColor();
                        //                    g.setColor(Color.red);
                        //                    g.draw(shape);
                        //                    g.setColor(tmp);
                        //                    g.setComposite(AlphaComposite.getInstance(rule, alpha));
                        // apply the new clip
                        g.clip(shape);
                    }
                    // update clip
                    clipArea = new Area(g.getClip());
                } else if (nextShape instanceof Draw) {
                    if (shape.intersects(clipArea.getBounds2D()) ||
                            (shape.getBounds2D().getWidth() < 1.0 ||
                                    shape.getBounds2D().getHeight() < 1.0)) {
                        g.draw(shape);
                        // Send a PaintPage Event to listeners
                        currentTime = System.currentTimeMillis();
                        if (currentTime - lastPaintTime > paintDelay) {
//                            paintCount++;
                            lastPaintTime = currentTime;
                            parentPage.notifyPaintPageListeners();
                        }
                    }
                } else if (nextShape instanceof NoClip) {
                    AffineTransform af = new AffineTransform(g.getTransform());
                    g.setTransform(base);
                    g.setClip(clip);
                    g.setTransform(af);
                    clipArea = new Area(g.getClip());
                } else if (nextShape instanceof Stroke) {
                    g.setStroke((Stroke) nextShape);
                } else if (nextShape instanceof Image) {
                    Image tmpImage = (Image) nextShape;
                    if (clipArea.intersects(0, 0, 1, 1)) {
                        try {
                            g.drawImage(tmpImage, 0, 0, 1, 1, null);
                        }
                        catch (OutOfMemoryError memErr) {
                            // If we have a large image and if we're scaling it down,
                            //  then that tends to make a memory spike.
                            // So, lets try redrawing it with the crappiest interpolation
                            //  setting, which uses the least memory
                            int width = tmpImage.getWidth(null);
                            int height = tmpImage.getHeight(null);
                            if (width >= 600 && height >= 600) {
                                AffineTransform at = g.getTransform();
                                int scaleX = (int) at.getScaleX();
                                int scaleY = (int) at.getScaleX();
                                if (scaleX < width || scaleY < height) {
                                    RenderingHints renderingHints = g.getRenderingHints();
                                    Object oldInterpolation = renderingHints.get(RenderingHints.KEY_INTERPOLATION);
                                    try {
                                        renderingHints.put(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
                                        g.setRenderingHints(renderingHints);
                                        g.drawImage(tmpImage, 0, 0, 1, 1, null);
                                    }
                                    catch (OutOfMemoryError memErr2) {
                                        shapesEnumeration.remove();
                                        logger.log(Level.FINE, "Image too large to draw", memErr);
                                    }
                                    finally {
                                        renderingHints.put(RenderingHints.KEY_INTERPOLATION, oldInterpolation);
                                        g.setRenderingHints(renderingHints);
                                        currentTime = System.currentTimeMillis();
                                        // Send a PaintPage Event to listeners
                                        if (currentTime - lastPaintTime > paintDelay) {
//                                            paintCount++;
                                            lastPaintTime = currentTime;
                                            parentPage.notifyPaintPageListeners();
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                // paint non transparency group xForm objects.
                else if (nextShape instanceof Shapes) {
                    ((Shapes) nextShape).setPageParent(parentPage);
                    ((Shapes) nextShape).paint(g);
                    ((Shapes) nextShape).setPageParent(null);
                }
                // Handle the painting of xForm transparency groups, this block
                // attempts to handle isolated painting by painting to an image
                // then painting the image using which ever alph rule is on the
                // shapes stack.
                else if (nextShape instanceof Form) {
                    //todo move logic into form object. 

                    Form xForm = (Form)nextShape;

                    Rectangle2D bBox = xForm.getBBox();
                    int width = (int)bBox.getWidth();
                    int height = (int)bBox.getHeight();
                    // corner cases where some bBoxes don't have a dimension.
                    if (width == 0){
                        width = 1;
                    }
                    if (height == 0){
                        height = 1;
                    }
                    // create the new image to write too.
                    BufferedImage bi = new BufferedImage(width, height,
                            BufferedImage.TYPE_INT_ARGB);
                    Graphics2D canvas = bi.createGraphics();
                    // copy over the rendering hints
                    canvas.setRenderingHints(g.getRenderingHints());
                    // get shapes and paint them.
                    Shapes xFormShapes = xForm.getShapes();
                    if (xFormShapes != null){
                        xFormShapes.setPageParent(parentPage);
                        // translate the coordinate system as we'll paint the g
                        // graphic at the correctly location later.
                        canvas.translate(-(int)bBox.getX(),-(int)bBox.getY());
                        canvas.setClip(bBox);
                        xFormShapes.paint(canvas);
                        xFormShapes.setPageParent(null);
                    }
                    // finally paint the graphic using the current gs.
                    g.drawImage(bi, null, (int)bBox.getX(), (int)bBox.getY());

                } else if (nextShape instanceof Color) {
                    g.setColor((Color) nextShape);
                }
                // handle tiled painting
                else if (nextShape instanceof TilingPattern) {
                    TilingPattern tilingPattern = (TilingPattern)nextShape;
                    tilingPattern.paintPattern(g, parentPage);
                }
//                else if (Debug.ex){
//                    Debug.p("Found unhandled Shapes Operand ");
//                }
            }
            // not pretty, but avoid any problems which disposing a page in the middle
            // of a paint.
        }
        catch (NoSuchElementException e) {
            // eat any errors. 
        }
        catch (Exception e) {
            logger.log(Level.FINE, "Error painting shapes.", e);
        }
//        System.out.println("Paint Count " + paintCount);

//        long stopTime = System.currentTimeMillis();
//        long elapsedTime = stopTime - startTime;
//            System.out.println("Paint Time: " + elapsedTime );
    }

// Dangerous method
//    /**
//     * Dump of all objects in graphics stack.
//     * @return
//     */
//    public String toString() {
//        StringBuffer sb = new StringBuffer();
//        for (int i = 0; i < shapes.size(); i++) {
//            sb.append(shapes.elementAt(i).toString() + "\n");
//        }
//        return sb.toString();
//    }

    /**
     * Gets all the images that where found when parsing the pages' content.  Each
     * element in the Vector represents a seperate image.
     *
     * @return all images in a page's content, if any.
     */
    public Vector getImages() {
        return images;
    }
    
    public void contract() {
        if (shapes != null) {
            if (shapes.capacity() - shapes.size() > 200) {
                shapes.trimToSize();
            }
        }
    }
}
