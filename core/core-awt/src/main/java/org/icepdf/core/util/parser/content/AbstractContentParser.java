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
package org.icepdf.core.util.parser.content;

import org.icepdf.core.pobjects.*;
import org.icepdf.core.pobjects.fonts.FontFile;
import org.icepdf.core.pobjects.fonts.FontManager;
import org.icepdf.core.pobjects.graphics.*;
import org.icepdf.core.pobjects.graphics.commands.*;
import org.icepdf.core.pobjects.graphics.images.ImageStream;
import org.icepdf.core.pobjects.graphics.images.references.ImageReference;
import org.icepdf.core.pobjects.graphics.images.references.ImageReferenceFactory;
import org.icepdf.core.pobjects.graphics.text.GlyphText;
import org.icepdf.core.pobjects.graphics.text.PageText;
import org.icepdf.core.util.Defs;
import org.icepdf.core.util.Library;
import org.icepdf.core.util.updater.callbacks.ContentStreamCallback;

import java.awt.*;
import java.awt.geom.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Stack;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * AbstractContentParser contains all base operand implementations for the
 * Post Script operand set.
 *
 * @since 5.0
 */
public abstract class AbstractContentParser {
    private static final Logger logger =
            Logger.getLogger(AbstractContentParser.class.toString());

    private static final boolean disableTransparencyGroups;
    private static final boolean enabledOverPrint;
    private static final boolean enabledFontFallback;

    private static final boolean strokeAdjustmentEnabled;
    private static final float strokeAdjustmentThreshold;
    private static final float strokeAdjustmentValue;

    public enum AlphaPaintType {
        ALPHA_FILL, ALPHA_STROKE
    }

    static {
        // decide if large images will be scaled
        disableTransparencyGroups =
                Defs.sysPropertyBoolean("org.icepdf.core.disableTransparencyGroup",
                        false);

        // decide if basic over print support will be enabled.
        enabledOverPrint =
                Defs.sysPropertyBoolean("org.icepdf.core.enabledOverPrint",
                        true);

        enabledFontFallback =
                Defs.sysPropertyBoolean("org.icepdf.core.enabledFontFallback",
                        false);

        strokeAdjustmentEnabled =
                Defs.sysPropertyBoolean("org.icepdf.core.strokeAdjustmentEnabled",
                        false);

        strokeAdjustmentThreshold =
                Defs.floatProperty("org.icepdf.core.strokeAdjustmentThreshold", 0.09f);

        strokeAdjustmentValue =
                Defs.floatProperty("org.icepdf.core.strokeAdjustmentValue", 0.2f);
    }

    private static final float OVERPAINT_ALPHA = 0.4f;

    private static final ClipDrawCmd clipDrawCmd = new ClipDrawCmd();
    private static final NoClipDrawCmd noClipDrawCmd = new NoClipDrawCmd();

    protected GraphicsState graphicState;
    protected Library library;
    protected Resources resources;
    protected ContentStreamCallback contentStreamCallback;

    protected Shapes shapes;
    // keep track of embedded marked content
    protected LinkedList<OptionalContents> oCGs;

    // represents a geometric path constructed from straight lines, and
    // quadratic and cubic (Beauctezier) curves.  It can contain
    // multiple sub paths.
    protected GeneralPath geometricPath;

    // flag to handle none text based coordinate operand "cm" inside a text block
    protected boolean inTextBlock;

    // TextBlock affine transform can be altered by the "cm" operand and thus
    // the text base affine transform must be accessible outside the parseText method
    protected AffineTransform textBlockBase;

    // when parsing a type3 font we need to keep track of the scale factor
    // of the device space ctm.
    protected float glyph2UserSpaceScale = 1.0f;

    // xObject image count;
    protected final AtomicInteger imageIndex = new AtomicInteger(1);

    // stack to help with the parse
    protected final Stack<Object> stack = new Stack<>();

    /**
     * @param l PDF library master object.
     * @param r resources
     */
    public AbstractContentParser(Library l, Resources r, ContentStreamCallback contentStreamCallback) {
        library = l;
        resources = r;
        this.contentStreamCallback = contentStreamCallback;
    }

    /**
     * Returns the Shapes that have accumulated turing multiple calls to
     * parse().
     *
     * @return resultant shapes object of all processed content streams.
     */
    public Shapes getShapes() {
        shapes.contract();
        return shapes;
    }

    /**
     * Returns the stack of object used to parse content streams. If parse
     * was successful the stack should be empty.
     *
     * @return stack of objects accumulated during a cotent stream parse.
     */
    public Stack<Object> getStack() {
        return stack;
    }

    /**
     * Returns the current graphics state object being used by this content
     * stream.
     *
     * @return current graphics context of content stream.  May be null if
     * parse method has not been previously called.
     */
    public GraphicsState getGraphicsState() {
        return graphicState;
    }

    /**
     * Sets the graphics state object which will be used for the current content
     * parsing.  This method must be called before the parse method is called
     * otherwise it will not have an effect on the state of the draw operands.
     *
     * @param graphicState graphics state of this content stream
     */
    public void setGraphicsState(GraphicsState graphicState) {
        this.graphicState = graphicState;
    }

    /**
     * Parse a pages content stream.
     *
     * @param streams stream page content pObject
     * @return a Shapes Object containing all the pages text and images shapes.
     * @throws InterruptedException if current parse thread is interrupted.
     */
    public abstract ContentParser parse(Stream[] streams, Page page)
            throws InterruptedException, IOException;

    /**
     * Specialized method for extracting text from documents.
     *
     * @param streams stream page content pObject
     * @return vector where each entry is the text extracted from a text block.
     */
    public abstract Shapes parseTextBlocks(Stream[] streams) throws InterruptedException, IOException;

    protected static void consume_G(GraphicsState graphicState, Stack<Object> stack,
                                    Library library) {
        float gray = ((Number) stack.pop()).floatValue();
        // Stroke Color Gray
        graphicState.setStrokeColorSpace(
                PColorSpace.getColorSpace(library, DeviceGray.DEVICEGRAY_KEY));
        if (gray <= 1.0f) {
            graphicState.setStrokeColor(new Color(gray, gray, gray));
        }
    }

    protected static void consume_g(GraphicsState graphicState, Stack<Object> stack,
                                    Library library) {
        float gray = Math.abs(((Number) stack.pop()).floatValue());
        // Fill Color Gray
        graphicState.setFillColorSpace(
                PColorSpace.getColorSpace(library, DeviceGray.DEVICEGRAY_KEY));
        if (gray <= 1.0f) {
            graphicState.setFillColor(new Color(gray, gray, gray));
        }
    }

    protected static void consume_RG(GraphicsState graphicState, Stack<Object> stack,
                                     Library library) {
        if (stack.size() >= 3) {
            // set stoke colour
            graphicState.setStrokeColorSpace(
                    PColorSpace.getColorSpace(library, DeviceRGB.DEVICERGB_KEY));
            graphicState.setStrokeColor(commonRGB(stack));
        }
    }

    protected static void consume_rg(GraphicsState graphicState, Stack<Object> stack,
                                     Library library) {
        if (stack.size() >= 3) {
            // set fill colour
            graphicState.setFillColorSpace(
                    PColorSpace.getColorSpace(library, DeviceRGB.DEVICERGB_KEY));
            graphicState.setFillColor(commonRGB(stack));
        }
    }

    protected static void consume_K(GraphicsState graphicState, Stack<Object> stack, Library library) {
        if (stack.size() >= 4) {
            PColorSpace pColorSpace =
                    PColorSpace.getColorSpace(library, DeviceCMYK.DEVICECMYK_KEY);
            // set stroke colour
            graphicState.setStrokeColorSpace(pColorSpace);
            graphicState.setStrokeColor(pColorSpace.getColor(
                    commonCMYK(stack), true));
        }
    }

    protected static void consume_k(GraphicsState graphicState, Stack<Object> stack, Library library) {
        if (stack.size() >= 4) {
            // build a colour space.
            PColorSpace pColorSpace =
                    PColorSpace.getColorSpace(library, DeviceCMYK.DEVICECMYK_KEY);
            // set fill colour
            graphicState.setFillColorSpace(pColorSpace);
            graphicState.setFillColor(pColorSpace.getColor(
                    commonCMYK(stack), true));
        }
    }

    protected static void consume_CS(GraphicsState graphicState, Stack<Object> stack, Resources resources) {
        Object tmp = stack.pop();
        if (tmp instanceof Name) {
            // Fill Color ColorSpace, resources call uses factory call to PColorSpace.getColorSpace
            // which returns a colour space including a pattern
            graphicState.setStrokeColorSpace(resources.getColorSpace(tmp));
        }
    }

    protected static void consume_cs(GraphicsState graphicState, Stack<Object> stack, Resources resources) {
        Name n = (Name) stack.pop();
        // Fill Color ColorSpace, resources call uses factory call to PColorSpace.getColorSpace
        // which returns a colour space including a pattern
        graphicState.setFillColorSpace(resources.getColorSpace(n));
    }

    protected static void consume_ri(Stack<Object> stack) {
        stack.pop();
    }

    protected static void consume_SC(GraphicsState graphicState, Stack<Object> stack,
                                     Library library, Resources resources,
                                     boolean isTint) {
        Object o = stack.peek();
        // if a name then we are dealing with a pattern
        if (o instanceof Name) {
            Name patternName = (Name) stack.pop();
            Pattern pattern = resources.getPattern(patternName);
            // Create or update the current PatternColorSpace with an instance
            // of the current pattern. These object will be used later during
            // fill, show text and Do with image masks.
            if (graphicState.getStrokeColorSpace() instanceof PatternColor) {
                PatternColor pc = (PatternColor) graphicState.getStrokeColorSpace();
                pc.setPattern(pattern);
            } else {
                PatternColor pc = new PatternColor(null, null);
                pc.setPattern(pattern);
                graphicState.setStrokeColorSpace(pc);
            }

            // two cases to take into account:
            // for none coloured tiling patterns we must parse the component
            // values that specify colour.  otherwise we just use the name
            // for all other pattern types.
            if (pattern instanceof TilingPattern) {
                TilingPattern tilingPattern = (TilingPattern) pattern;
                if (tilingPattern.getPaintType() ==
                        TilingPattern.PAINTING_TYPE_UNCOLORED_TILING_PATTERN) {
                    // parsing is of the form 'C1...Cn name scn'
                    // first find out colour space specified by name
                    int compLength = graphicState.getStrokeColorSpace().getNumComponents();
                    // next calculate the colour based ont he space and c1...Cn
                    float[] colour = popFloatInOrder(stack, compLength);
                    Color color = graphicState.getStrokeColorSpace().getColor(colour, isTint);
                    graphicState.setStrokeColor(color);
                    tilingPattern.setUnColored(color);
                }
            }
        } else if (o instanceof Number) {

            // some pdfs encoding do not explicitly change the default colour
            // space from the default DeviceGrey.  The following code checks
            // how many n values are available and if different from current
            // graphicState.strokeColorSpace it is changed as needed

            // first get assumed number of components
            int colorSpaceN = graphicState.getStrokeColorSpace().getNumComponents();

            float[] f = popFloatInOrder(stack, colorSpaceN);
            graphicState.setStrokeColor(graphicState.getStrokeColorSpace().getColor(f, isTint));
        }
    }

    protected static void consume_sc(GraphicsState graphicState, Stack<Object> stack,
                                     Library library, Resources resources, boolean isTint) {
        Object o = null;
        if (!stack.isEmpty()) {
            o = stack.peek();
        }
        // if a name then we are dealing with a pattern.
        if (o instanceof Name) {
            Name patternName = (Name) stack.pop();
            Pattern pattern = resources.getPattern(patternName);
            // Create or update the current PatternColorSpace with an instance
            // of the current pattern. These object will be used later during
            // fill, show text and Do with image masks.
            if (graphicState.getFillColorSpace() instanceof PatternColor) {
                PatternColor pc = (PatternColor) graphicState.getFillColorSpace();
                pc.setPattern(pattern);
            } else {
                PatternColor pc = new PatternColor(library, null);
                pc.setPattern(pattern);
                graphicState.setFillColorSpace(pc);
            }

            // two cases to take into account:
            // for none coloured tiling patterns we must parse the component
            // values that specify colour.  otherwise we just use the name
            // for all other pattern types.
            if (pattern instanceof TilingPattern) {
                TilingPattern tilingPattern = (TilingPattern) pattern;
                if (tilingPattern.getPaintType() ==
                        TilingPattern.PAINTING_TYPE_UNCOLORED_TILING_PATTERN) {
                    // parsing is of the form 'C1...Cn name scn'
                    // first find out colour space specified by name
                    int compLength = graphicState.getFillColorSpace().getNumComponents();
                    // peek and then pop until a none Float is found
                    float[] colour = popFloatInOrder(stack, compLength);
                    // fill colour to be used when painting.
                    Color color = graphicState.getFillColorSpace().getColor(colour, isTint);
                    graphicState.setFillColor(color);
                    tilingPattern.setUnColored(color);
                }
            }
        } else if (o instanceof Number) {
            // some PDFs encoding do not explicitly change the default colour
            // space from the default DeviceGrey.  The following code checks
            // how many n values are available and if different from current
            // graphicState.fillColorSpace it is changed as needed

            // first get assumed number of components
            int colorSpaceN = graphicState.getFillColorSpace().getNumComponents();
            float[] f = popFloatInOrder(stack, colorSpaceN);
            graphicState.setFillColor(graphicState.getFillColorSpace().getColor(f, true));
        }
    }

    protected static GraphicsState consume_q(GraphicsState graphicState) {
        return graphicState.save();
    }

    protected GraphicsState consume_Q(GraphicsState graphicState, Shapes shapes) {
        GraphicsState gs1 = graphicState.restore();
        // point returned stack
        if (gs1 != null) {
            graphicState = gs1;
        }
        // otherwise start a new stack
        else {
            graphicState = new GraphicsState(shapes);
            graphicState.set(new AffineTransform());
            shapes.add(noClipDrawCmd);
        }

        return graphicState;
    }

    protected static void consume_cm(GraphicsState graphicState, Stack<Object> stack,
                                     boolean inTextBlock, AffineTransform textBlockBase) {
        float[] affineTransformArray = popFloatInOrder(stack, 6);
        AffineTransform affineTransform = new AffineTransform(
                affineTransformArray[0], affineTransformArray[1], affineTransformArray[2],
                affineTransformArray[3], affineTransformArray[4], affineTransformArray[5]);
        // get the current CTM
        AffineTransform af = new AffineTransform(graphicState.getCTM());
        // do the matrix concatenation math
        af.concatenate(affineTransform);
        // add the transformation to the graphics state
        graphicState.set(af);
        // update the clip, translate by this CM
        graphicState.updateClipCM(affineTransform);
        // apply the cm just as we would a tm
        if (inTextBlock) {
            // update the textBlockBase with the cm matrix
            AffineTransform af2 = new AffineTransform(graphicState.getTextState().tlmatrix);
            graphicState.getTextState().tmatrix = affineTransform;
            af2.concatenate(graphicState.getTextState().tmatrix);
            graphicState.set(af2);
            graphicState.scale(1, -1);

            // update the textBlockBase as the tm was specified in the BT block, and
            // we still need to keep the offset. Only reset the block on a simple translation
            if (af2.getScaleX() == 1.0 && af2.getScaleY() == 1.0) {
                textBlockBase.setTransform(af2);
            }
            graphicState.getTextState().tlmatrix.setTransform(af2);
        }
    }

    protected static void consume_i(Stack<Object> stack) {
        if (stack.size() >= 1) {
            stack.pop();
        }
    }

    protected static void consume_J(GraphicsState graphicState, Stack<Object> stack, Shapes shapes) {
//        collectTokenFrequency(PdfOps.J_TOKEN);
        // get the value from the stack
        graphicState.setLineCap((int) (((Number) stack.pop()).floatValue()));
        // Butt cap, stroke is squared off at the endpoint of the path
        // there is no projection beyond the end of the path
        if (graphicState.getLineCap() == 0) {
            graphicState.setLineCap(BasicStroke.CAP_BUTT);
        }
        // Round cap, a semicircular arc with a diameter equal to the line
        // width is drawn around the endpoint and filled in
        else if (graphicState.getLineCap() == 1) {
            graphicState.setLineCap(BasicStroke.CAP_ROUND);
        }
        // Projecting square cap.  The stroke continues beyond the endpoint
        // of the path for a distance equal to half the line width and is
        // then squared off.
        else if (graphicState.getLineCap() == 2) {
            graphicState.setLineCap(BasicStroke.CAP_SQUARE);
        }
        // Mark the stroke as being changed and store state in the
        // shapes object
        setStroke(shapes, graphicState);
    }

    /**
     * Process the xObject content.
     *
     * @param graphicState graphic state to appent
     * @param stack        stack of object being parsed.
     * @param shapes       shapes object.
     * @param resources    associated resources.
     * @param imageIndex   page index of page.
     * @param page         parent page getting parsed.
     * @param viewParse    true indicates parsing is for a normal view.  If false
     *                     the consumption of Do will skip Image based xObjects for performance.
     * @return graphic state after parsing xObject.
     */
    protected static GraphicsState consume_Do(GraphicsState graphicState, Stack<Object> stack,
                                              Shapes shapes, Resources resources,
                                              boolean viewParse, // events
                                              AtomicInteger imageIndex, Page page,
                                              ContentStreamCallback contentStreamCallback,
                                              boolean inTextBlock) throws InterruptedException, IOException {
        Name xobjectName = (Name) stack.pop();
        if (resources == null) return graphicState;
        // Form XObject
        Object xObject = resources.getXObject(xobjectName);
        if (xObject instanceof Form) {
            // Do operator steps:
            //  1. save the graphics context
            graphicState = graphicState.save();
            // Try and find the named reference 'xobjectName', pass in a copy
            // of the current graphics state for the new content stream
            Form formXObject = (Form) xObject;
            // check if the form is an optional content group.
            Object oc = formXObject.getObject(OptionalContent.OC_KEY);
            if (oc != null) {
                OptionalContent optionalContent = resources.getLibrary().getCatalog().getOptionalContent();
                optionalContent.init();
                if (!optionalContent.isVisible(oc)) {
                    return graphicState;
                }
            }
            // init form XObject with current gs state, but we need to keep the original state for blending
            GraphicsState xformGraphicsState = new GraphicsState(graphicState);
            formXObject.setGraphicsState(xformGraphicsState);
            if (formXObject.isTransparencyGroup()) {
                // assign the state to the graphic state for later
                // processing during the paint
                xformGraphicsState.setTransparencyGroup(formXObject.isTransparencyGroup());
                xformGraphicsState.setIsolated(formXObject.isIsolated());
                xformGraphicsState.setKnockOut(formXObject.isKnockOut());
            }
            // according to spec the formXObject might not have
            // resources reference as a result we pass in the current
            // one in the hope that any resources can be found.
            formXObject.setParentResources(resources);
            // need a new instance, so we don't corrupt the stream offset.
            ContentStreamCallback formContentStreamRedactorCallback = null;
            if (contentStreamCallback != null) {
                AffineTransform xObjectTransform = graphicState.getCTM();
                xObjectTransform.concatenate(formXObject.getMatrix());
                formContentStreamRedactorCallback = contentStreamCallback.createChildInstance(xObjectTransform);
            }
            formXObject.init(formContentStreamRedactorCallback);
            // 2. concatenate matrix entry with the current CTM
            AffineTransform af = new AffineTransform(graphicState.getCTM());
            af.concatenate(formXObject.getMatrix());
            shapes.add(new TransformDrawCmd(af));
            // 3. Clip according to the form BBox entry
            if (graphicState.getClip() != null) {
                AffineTransform matrix = formXObject.getMatrix();
                Area bbox = new Area(formXObject.getBBox());
                Area clip = graphicState.getClip();
                // create inverse of matrix, so we can transform
                // the clip to form space.
                try {
                    matrix = matrix.createInverse();
                } catch (NoninvertibleTransformException e) {
                    logger.warning("Error create xObject matrix inverse");
                }
                // apply the new clip now that they are in the
                // same space.
                Shape shape = matrix.createTransformedShape(clip);
                bbox.intersect(new Area(shape));
                shapes.add(new ShapeDrawCmd(bbox));
            } else {
                shapes.add(new ShapeDrawCmd(formXObject.getBBox()));
            }
            shapes.add(clipDrawCmd);
            // 4. Paint the graphics objects in font stream.
            // still some work to do here regarding BM vs. alpha comp.
            if ((formXObject.getExtGState() != null &&
                    (formXObject.getExtGState().getBlendingMode() == null ||
                            formXObject.getExtGState().getBlendingMode().equals(BlendComposite.NORMAL_VALUE)))) {
                setAlpha(formXObject.getShapes(), graphicState, graphicState.getAlphaRule(),
                        graphicState.getFillAlpha());
                setAlpha(shapes, graphicState, graphicState.getAlphaRule(),
                        graphicState.getFillAlpha());
            }
            // If we have a transparency group we paint it
            // slightly different from a regular xObject as we
            // need to capture the alpha which is only possible
            // by paint the xObject to an image.
            if (!disableTransparencyGroups &&
                    ((formXObject.getBBox().getWidth() < FormDrawCmd.MAX_IMAGE_SIZE && formXObject.getBBox().getWidth() > 1) &&
                            (formXObject.getBBox().getHeight() < FormDrawCmd.MAX_IMAGE_SIZE && formXObject.getBBox().getHeight() > 1)
                            && (formXObject.getExtGState() != null &&
                            (formXObject.getExtGState().getSMask() != null ||
                                    (formXObject.getExtGState().getBlendingMode() != null &&
                                            !formXObject.getExtGState().getBlendingMode().equals(BlendComposite.NORMAL_VALUE))
                                    || (formXObject.getExtGState().getNonStrokingAlphConstant() < 1
                                    && formXObject.getExtGState().getNonStrokingAlphConstant() > 0)))
                    )) {
                // add the hold form for further processing.
                shapes.add(new FormDrawCmd(formXObject));
            }
            // the downside of painting to an image is that we
            // lose quality if there is an affine transform, so
            // if it isn't a group transparency we paint old way
            // by just adding the objects to the shapes stack.
            else {
                shapes.add(new ShapesDrawCmd(formXObject.getShapes()));
            }
            // update text sprites with geometric path state
            if (formXObject.getShapes() != null &&
                    formXObject.getShapes().getPageText() != null) {
                // normalize each sprite.
                AffineTransform pageSpace = graphicState.getCTM();
                pageSpace.concatenate(formXObject.getMatrix());
                formXObject.getShapes().getPageText()
                        .applyXObjectTransform(pageSpace);
                // add the text to the current shapes for extraction and
                // selection purposes.
                PageText pageText = formXObject.getShapes().getPageText();
                if (pageText != null && pageText.getPageLines() != null) {
                    shapes.getPageText().addPageLines(
                            pageText.getPageLines());
                }
            }
            // Some Do object will have images, and we need to make sure we account for localized space.
            if (contentStreamCallback != null &&
                    formXObject.getShapes() != null) {
                formContentStreamRedactorCallback.endContentStream();
                Shapes pageShapes = formXObject.getShapes();
                ArrayList<DrawCmd> xObjectShapes = pageShapes.getShapes();
                for (DrawCmd object : xObjectShapes) {
                    if (object instanceof ImageDrawCmd) {
                        ImageDrawCmd imageDrawCmd = (ImageDrawCmd) object;
                        ImageStream imageStream = imageDrawCmd.getImageStream();
                        AffineTransform pageSpace = new AffineTransform(graphicState.getCTM());
                        pageSpace.concatenate(formXObject.getMatrix());
                        imageStream.setGraphicsTransformMatrix(af);
                    }
                }
            }
            shapes.add(new NoClipDrawCmd());
            //  5. Restore the saved graphics state
            graphicState = graphicState.restore();
        }
        // Image XObject
        else if (viewParse) {
            ImageStream imageStream = (ImageStream) xObject;
            if (imageStream != null) {
                Object oc = imageStream.getObject(OptionalContent.OC_KEY);
                if (oc != null) {
                    OptionalContent optionalContent = resources.getLibrary().getCatalog().getOptionalContent();
                    optionalContent.init();
                    // avoid loading the image if oc is not visible
                    // may have to add this logic to the stack for dynamic content
                    // if we get an example.
                    if (!optionalContent.isEmptyDefinition() && !optionalContent.isVisible(oc)) {
                        return graphicState;
                    }
                }

                // set CTM so we can convert image location to user space at a later time.
                AffineTransform af = new AffineTransform(graphicState.getCTM());
                imageStream.setGraphicsTransformMatrix(af);

                // create an ImageReference for future decoding
                ImageReference imageReference = ImageReferenceFactory.getImageReference(
                        imageStream, xobjectName, resources, graphicState,
                        imageIndex.get(), page);
                imageIndex.incrementAndGet();

                // GH-243,  there is a weird duality between cm and Do inside text blocks that this adjusts for.
                // Not ideal but solves the inverted rendering problem for now, another sample might give more info
                // into the actual problem and a better solution.
                if (!inTextBlock) {
                    graphicState.scale(1, -1);
                }
                graphicState.translate(0, -1);
                setAlpha(shapes, graphicState, AlphaPaintType.ALPHA_FILL);


                if (contentStreamCallback != null) {
                    contentStreamCallback.checkAndModifyImageXObject(imageReference);
                }

                shapes.add(new ImageDrawCmd(imageReference));
                graphicState.set(af);
            }
        }
        return graphicState;
    }

    protected static void consume_d(GraphicsState graphicState, Stack<Object> stack, Shapes shapes) {
        float dashPhase;
        float[] dashArray;
        try {
            // pop dashPhase off the stack
            dashPhase = Math.abs(((Number) stack.pop()).floatValue());
            // pop the dashVector of the stack
            //noinspection unchecked
            java.util.List<Object> dashVector = (java.util.List<Object>) stack.pop();
            // if the dash vector size is zero we have a default none dashed
            // line and thus we skip out
            if (!dashVector.isEmpty() && dashVector.get(0) != null) {
                // convert dash vector to an array of floats
                final int sz = dashVector.size();
                dashArray = new float[sz];
                Object tmp;
                boolean nullArray = false;
                for (int i = 0; i < sz; i++) {
                    tmp = dashVector.get(i);
                    float dash;
                    if (tmp instanceof Number) {
                        dash = Math.abs(((Number) dashVector.get(i)).floatValue());
                        // java has a hard time with painting dash array with values < 0.05.
                        // null the dash array as we can't pain it PDF-966.
                        if (dash < 0.05f) nullArray = true;
                        dashArray[i] = dash;
                    }
                }
                // corner case check to see if the dash array contains a first element
                // that is very different from second which is likely the result of
                // an itext/MS Word bug where a dash element of the array isn't scaled to
                // user space.
                if (dashArray.length > 1 && dashArray[0] != 0) {
                    boolean isOffice = false;
                    int spread = 10000;
                    double min = dashArray[0];
                    for (int i = 0, max = dashArray.length - 1; i < max; i++) {
                        float diff = dashArray[i] - dashArray[i + 1];
                        if (Math.abs(diff) > spread) {
                            isOffice = true;
                        }
                        if (dashArray[i] < min) {
                            min = dashArray[i];
                        }
                    }
                    if (isOffice) {
                        min = Math.ceil(min);
                        for (int i = 0, max = dashArray.length; i < max; i++) {
                            if (dashArray[i] <= min && dashArray[i] < 100) {
                                // scale to PDF space.
                                dashArray[i] = dashArray[i] * 1000;
                            }
                        }
                    }
                }
                // null the dash array if one of the dash values was less than 0.05.
                if (nullArray) {
                    dashArray = null;
                }
            }
            // default to standard black line
            else {
                dashPhase = 0;
                dashArray = null;
            }
            // assign state now that everything is assumed good
            // from a class cast exception point of view.
            graphicState.setDashArray(dashArray);
            graphicState.setDashPhase(dashPhase);
        } catch (ClassCastException e) {
            logger.log(Level.FINE, "Dash pattern syntax error: ", e);
        }
        // update stroke state with possibly new dash data.
        setStroke(shapes, graphicState);
    }

    protected static void consume_j(GraphicsState graphicState, Stack<Object> stack, Shapes shapes) {
        // grab the value
        graphicState.setLineJoin((int) (((Number) stack.pop()).floatValue()));
        // Miter Join - the outer edges of the strokes for the two
        // segments are extended until they meet at an angle, like a picture
        // frame
        if (graphicState.getLineJoin() == 0) {
            graphicState.setLineJoin(BasicStroke.JOIN_MITER);
        }
        // Round join - an arc of a circle with a diameter equal to the line
        // width is drawn around the point where the two segments meet,
        // connecting the outer edges of the strokes for the two segments
        else if (graphicState.getLineJoin() == 1) {
            graphicState.setLineJoin(BasicStroke.JOIN_ROUND);
        }
        // Bevel join - The two segments are finished with butt caps and the
        // ends of the segments is filled with a triangle
        else if (graphicState.getLineJoin() == 2) {
            graphicState.setLineJoin(BasicStroke.JOIN_BEVEL);
        }
        // updates shapes with the new stroke type
        setStroke(shapes, graphicState);
    }

    protected static void consume_w(GraphicsState graphicState, Stack<Object> stack,
                                    Shapes shapes, float glyph2UserSpaceScale) {
        // apply any type3 font scalling which is set via the glyph2User space affine transform.
        if (!stack.isEmpty()) {
            float scale = ((Number) stack.pop()).floatValue() * glyph2UserSpaceScale;
            if (strokeAdjustmentEnabled && scale < strokeAdjustmentThreshold) {
                scale = strokeAdjustmentValue;
            }
            // A line width of 0 shall denote the thinnest line that can be rendered at device resolution: 1 device
            // pixel wide.  0.15f is about the thinnest line we can draw reliably at low zoom levels
            if (scale == 0f) {
                scale = (float) (0.15f / graphicState.getCTM().getScaleX());
            }
            graphicState.setLineWidth(scale);
            setStroke(shapes, graphicState);
        }
    }

    protected static void consume_M(GraphicsState graphicState, Stack<Object> stack, Shapes shapes) {
        graphicState.setMiterLimit(((Number) stack.pop()).floatValue());
        setStroke(shapes, graphicState);
    }

    protected static void consume_gs(GraphicsState graphicState, Stack<Object> stack, Resources resources,
                                     Shapes shapes) {
        Object gs = stack.pop();
        if (gs instanceof Name && resources != null) {
            // Get ExtGState and merge it with
            ExtGState extGState =
                    resources.getExtGState((Name) gs);
            if (extGState != null) {
                graphicState.concatenate(extGState);
            }

            setAlpha(shapes, graphicState, AlphaPaintType.ALPHA_FILL);
        }
    }

    protected static void consume_Tf(GraphicsState graphicState, Stack<Object> stack, Resources resources) {
        float size = ((Number) stack.pop()).floatValue();
        Name name2 = (Name) stack.pop();
        // build the new font and initialize it.
        graphicState.getTextState().tsize = size;
        graphicState.getTextState().fontName = name2;
        graphicState.getTextState().font = resources.getFont(name2);
        // in the rare case that the font can't be found then we try and build
        // one so the document can be rendered in some shape or form.
        if (graphicState.getTextState().font == null ||
                graphicState.getTextState().font.getFont() == null) {
            try {
                // this should almost never happen but of course we have a few corner cases:
                // get the first pages resources, no need to lock the page, already locked.
                // todo lock assumption doesn't sound right.
                Page page = resources.getLibrary().getCatalog().getPageTree().getPage(0);
                page.initPageResources();
                Resources res = page.getResources();
                // try and get a font off the first page.
                Object pageFonts = res.getEntries().get(Resources.FONT_KEY);
                // check for an indirect reference
                if (pageFonts instanceof Reference) {
                    pageFonts = resources.getLibrary().getObject((Reference) pageFonts);
                }
                if (pageFonts instanceof HashMap) {
                    // get first font
                    Reference fontRef = (Reference) ((HashMap<?, ?>) pageFonts).get(name2);
                    if (fontRef != null) {
                        graphicState.getTextState().font =
                                (org.icepdf.core.pobjects.fonts.Font) resources.getLibrary()
                                        .getObject(fontRef);
                        graphicState.getTextState().font.init();
                    }
                }
            } catch (Exception throwable) {
                // keep block protected as we don't want to accidentally fail parsing the reset of the stream
                logger.warning("Warning could not find font by named resource " + name2);
            }
        }
        if (graphicState.getTextState().font != null) {
            FontFile font = graphicState.getTextState().font.getFont();
            font.deriveFont(size);
            graphicState.getTextState().currentfont =
                    graphicState.getTextState().font.getFont().deriveFont(size);
        } else {
            // not font found which is a problem,  so we need to check for interactive form dictionary
            graphicState.getTextState().font = resources.getLibrary().getInteractiveFormFont(name2.getName());
            if (graphicState.getTextState().font != null) {
                graphicState.getTextState().currentfont = graphicState.getTextState().font.getFont();
                graphicState.getTextState().currentfont =
                        graphicState.getTextState().font.getFont().deriveFont(size);
            }
        }
    }

    protected static void consume_Tc(GraphicsState graphicState, Stack<Object> stack) {
        graphicState.getTextState().cspace = ((Number) stack.pop()).floatValue();
    }

    protected static void consume_tm(GraphicsState graphicState, Stack<Object> stack,
                                     TextMetrics textMetrics,
                                     PageText pageText,
                                     double previousBTStart,
                                     AffineTransform textBlockBase,
                                     LinkedList<OptionalContents> oCGs) {
        textMetrics.setShift(0);
        textMetrics.setPreviousAdvance(0);
        textMetrics.getAdvance().setLocation(0, 0);
        // pop carefully, as there are few corner cases where
        // the af is split up with a BT or other token
        Object next;
        // initialize an identity matrix, add parse out the
        // numbers we have working from f6 down to f1.
        float[] tm = new float[]{1f, 0, 0, 1f, 0, 0};
        for (int i = 0, hits = 5, max = stack.size(); hits != -1 && i < max; i++) {
            next = stack.pop();
            if (next instanceof Number) {
                tm[hits] = ((Number) next).floatValue();
                hits--;
            }
        }

        AffineTransform af = new AffineTransform(textBlockBase);
        graphicState.getTextState().tmatrix = new AffineTransform(tm);
        af.concatenate(graphicState.getTextState().tmatrix);
        graphicState.getTextState().tlmatrix.setTransform(af);
        graphicState.set(af);
        graphicState.scale(1, -1);

        // text extraction logic
        // capture x coord of BT y offset, tm, Td, TD.
        if (textMetrics.isYstart()) {
            textMetrics.setyBTStart(tm[5]);
            textMetrics.setYstart(false);
        }

        // update the extract text
        pageText.setTextTransform(new AffineTransform(tm));

    }

    protected static void consume_T_star(GraphicsState graphicState,
                                         TextMetrics textMetrics, PageText pageText,
                                         LinkedList<OptionalContents> oCGs) {
        graphicState.translate(-textMetrics.getShift(), 0);
        textMetrics.setShift(0);
        textMetrics.setPreviousAdvance(0);
        textMetrics.getAdvance().setLocation(0, 0);
        graphicState.translate(0, graphicState.getTextState().leading);
        // always indicates a new line
        pageText.newLine(oCGs);
    }

    protected static void consume_TD(GraphicsState graphicState, Stack<Object> stack,
                                     TextMetrics textMetrics,
                                     PageText pageText,
                                     LinkedList<OptionalContents> oCGs) {
        float y = ((Number) stack.pop()).floatValue();
        float x = ((Number) stack.pop()).floatValue();
        graphicState.translate(-textMetrics.getShift(), 0);
        textMetrics.setShift(0);
        textMetrics.setPreviousAdvance(0);
        textMetrics.getAdvance().setLocation(0, 0);
        graphicState.translate(x, -y);
        graphicState.getTextState().leading = -y;

        // capture x coord of BT y offset, tm, Td, TD.
        if (textMetrics.isYstart()) {
            textMetrics.setyBTStart(y);
            textMetrics.setYstart(false);
        }
    }

    protected static void consume_double_quote(GraphicsState graphicState, Stack<Object> stack,
                                               Shapes shapes,
                                               TextMetrics textMetrics,
                                               GlyphOutlineClip glyphOutlineClip,
                                               LinkedList<OptionalContents> oCGs,
                                               ContentStreamCallback contentStreamCallback) throws IOException {
        StringObject stringObject = (StringObject) stack.pop();
        graphicState.getTextState().cspace = ((Number) stack.pop()).floatValue();
        graphicState.getTextState().wspace = ((Number) stack.pop()).floatValue();
        // push the string back on, so we can reuse the single quote layout code
        stack.push(stringObject);
        consume_T_star(graphicState, textMetrics, shapes.getPageText(), oCGs);
        consume_Tj(graphicState, stack, shapes, textMetrics, glyphOutlineClip, oCGs, contentStreamCallback);
    }

    protected static void consume_single_quote(GraphicsState graphicState, Stack<Object> stack,
                                               Shapes shapes,
                                               TextMetrics textMetrics,
                                               GlyphOutlineClip glyphOutlineClip,
                                               LinkedList<OptionalContents> oCGs,
                                               ContentStreamCallback contentStreamCallback)
            throws IOException {
        // ' = T* + Tj,  who knew?
        consume_T_star(graphicState, textMetrics, shapes.getPageText(), oCGs);
        consume_Tj(graphicState, stack, shapes, textMetrics, glyphOutlineClip, oCGs, contentStreamCallback);
    }

    protected static void consume_Td(GraphicsState graphicState, Stack<Object> stack,
                                     TextMetrics textMetrics,
                                     PageText pageText,
                                     double previousBTStart,
                                     LinkedList<OptionalContents> oCGs) {
        float y = ((Number) stack.pop()).floatValue();
        float x = ((Number) stack.pop()).floatValue();
        graphicState.translate(-textMetrics.getShift(), 0);
        textMetrics.setShift(0);
        textMetrics.setPreviousAdvance(0);
        textMetrics.getAdvance().setLocation(0, 0);
        // x,y are expressed in unscaled, but we don't scale until
        // a text showing operator is called.
        graphicState.translate(x, -y);
        // capture x coord of BT y offset, tm, Td, TD.
        if (textMetrics.isYstart()) {
            float newY = (float) graphicState.getCTM().getTranslateY();
            textMetrics.setyBTStart(newY);
            textMetrics.setYstart(false);
        }
    }

    protected static void consume_Tz(GraphicsState graphicState, Stack<Object> stack) {
        Object ob = stack.pop();
        if (ob instanceof Number) {
            float hScaling = ((Number) ob).floatValue();
            // store the scaled value, but not apply the state operator at this time
            graphicState.getTextState().hScalling = hScaling / 100.0f;
        }
    }

    protected static void consume_Tw(GraphicsState graphicState, Stack<Object> stack) {
        graphicState.getTextState().wspace = ((Number) stack.pop()).floatValue();
    }

    protected static void consume_Tr(GraphicsState graphicState, Stack<Object> stack) {
        graphicState.getTextState().rmode = (int) ((Number) stack.pop()).floatValue();
    }

    protected static void consume_TL(GraphicsState graphicState, Stack<Object> stack) {
        graphicState.getTextState().leading = ((Number) stack.pop()).floatValue();
    }

    protected static void consume_Ts(GraphicsState graphicState, Stack<Object> stack) {
        graphicState.getTextState().trise = ((Number) stack.pop()).floatValue();
    }

    protected static GeneralPath consume_L(Stack<Object> stack,
                                           GeneralPath geometricPath) {
        float y = ((Number) stack.pop()).floatValue();
        float x = ((Number) stack.pop()).floatValue();
        if (geometricPath == null) {
            geometricPath = new GeneralPath();
        }
        geometricPath.lineTo(x, y);
        return geometricPath;
    }

    protected static GeneralPath consume_m(Stack<Object> stack,
                                           GeneralPath geometricPath) {
        if (geometricPath == null) {
            geometricPath = new GeneralPath();
        }
        if (stack.size() >= 2) {
            float y = ((Number) stack.pop()).floatValue();
            float x = ((Number) stack.pop()).floatValue();
            geometricPath.moveTo(x, y);
        }
        return geometricPath;
    }

    protected static GeneralPath consume_c(Stack<Object> stack,
                                           GeneralPath geometricPath) {
        if (!stack.isEmpty()) {
            float[] affineTransform = popFloatInOrder(stack, 6);
            if (geometricPath == null) {
                geometricPath = new GeneralPath();
            }
            geometricPath.curveTo(
                    affineTransform[0], affineTransform[1],
                    affineTransform[2], affineTransform[3],
                    affineTransform[4], affineTransform[5]);
        }
        return geometricPath;
    }

    protected static GeneralPath consume_S(GraphicsState graphicState,
                                           Shapes shapes,
                                           GeneralPath geometricPath) throws InterruptedException {
        if (geometricPath != null) {
            commonStroke(graphicState, shapes, geometricPath);
        }
        return null;
    }

    protected static GeneralPath consume_F(GraphicsState graphicState,
                                           Shapes shapes,
                                           GeneralPath geometricPath)
            throws InterruptedException {
        if (geometricPath != null) {
            geometricPath.setWindingRule(GeneralPath.WIND_NON_ZERO);
            commonFill(shapes, graphicState, geometricPath);
        }
        return null;
    }

    protected static GeneralPath consume_f(GraphicsState graphicState,
                                           Shapes shapes,
                                           GeneralPath geometricPath)
            throws InterruptedException {
        if (geometricPath != null) {
            geometricPath.setWindingRule(GeneralPath.WIND_NON_ZERO);
            commonFill(shapes, graphicState, geometricPath);
        }
        return null;
    }

    protected static GeneralPath consume_re(Stack<Object> stack,
                                            GeneralPath geometricPath) {
        if (geometricPath == null) {
            geometricPath = new GeneralPath();
        }
        float h = ((Number) stack.pop()).floatValue();
        float w = ((Number) stack.pop()).floatValue();
        float y = ((Number) stack.pop()).floatValue();
        float x = ((Number) stack.pop()).floatValue();
        geometricPath.moveTo(x, y);
        geometricPath.lineTo(x + w, y);
        geometricPath.lineTo(x + w, y + h);
        geometricPath.lineTo(x, y + h);
        geometricPath.lineTo(x, y);
        return geometricPath;
    }

    protected static void consume_h(GeneralPath geometricPath) {
        if (geometricPath != null) {
            geometricPath.closePath();
        }
    }

    protected static void consume_BDC(Stack<Object> stack,
                                      Shapes shapes,
                                      LinkedList<OptionalContents> oCGs,
                                      Resources resources) throws InterruptedException {
        Object properties = stack.pop();// properties
        Name tag = (Name) stack.pop();// tag
        OptionalContents optionalContents = null;
        // try and process the Optional content.
        if (tag.equals(OptionalContent.OC_KEY)) {
            if (properties instanceof Name) {
                optionalContents = resources.getPropertyEntry((Name) properties);
                // make sure the reference is valid, no point
                // jumping through all the hopes if we don't have too.
                if (optionalContents != null) {
                    optionalContents.init();
                    // valid OC, add a marker command to the stack.
                    shapes.add(new OCGStartDrawCmd(optionalContents));
                }
            }
        }
        if (optionalContents == null) {
            // create a temporary optional object.
            Name tmp = OptionalContent.NONE_OC_FLAG;
            if (properties instanceof Name) {
                tmp = (Name) properties;
            }
            optionalContents = new OptionalContentGroup(tmp.getName(), true);
        }
        if (oCGs != null) {
            oCGs.add(optionalContents);
        }
    }

    protected static void consume_EMC(Shapes shapes,
                                      LinkedList<OptionalContents> oCGs) {
        // add the new draw command to the stack.
        // restore the main stack.
        if (oCGs != null && !oCGs.isEmpty()) {
            OptionalContents optionalContents = oCGs.removeLast();
            // mark the end of an OCG.
            if (optionalContents.isOCG()) {
                // push the OC end command on the shapes
                shapes.add(new OCGEndDrawCmd());
            }
        }
    }

    protected static void consume_BMC(Stack<Object> stack,
                                      Shapes shapes,
                                      LinkedList<OptionalContents> oCGs,
                                      Resources resources) throws InterruptedException {
        Object properties = stack.pop();// properties
        // try and process the Optional content.
        if (properties instanceof Name && resources != null) {
            OptionalContents optionalContents = resources.getPropertyEntry((Name) properties);
            // make sure the reference is valid, no point
            // jumping through all the hopes if we don't have too.
            if (optionalContents != null) {
                optionalContents.init();
                shapes.add(new OCGStartDrawCmd(optionalContents));
            } else {
                Name tmp = (Name) properties;
                optionalContents =
                        new OptionalContentGroup(tmp.getName(), true);
            }
            if (oCGs != null) {
                oCGs.add(optionalContents);
            }
        }
    }

    protected static GeneralPath consume_f_star(GraphicsState graphicState,
                                                Shapes shapes,
                                                GeneralPath geometricPath)
            throws InterruptedException {
        if (geometricPath != null) {
            // need to apply pattern...
            geometricPath.setWindingRule(GeneralPath.WIND_EVEN_ODD);
            commonFill(shapes, graphicState, geometricPath);
        }
        return null;
    }

    protected static GeneralPath consume_b(GraphicsState graphicState,
                                           Shapes shapes,
                                           GeneralPath geometricPath)
            throws InterruptedException {
        if (geometricPath != null) {
            geometricPath.setWindingRule(GeneralPath.WIND_NON_ZERO);
            geometricPath.closePath();
            commonFill(shapes, graphicState, geometricPath);
            commonStroke(graphicState, shapes, geometricPath);
        }
        return null;
    }

    protected static GeneralPath consume_n(GeneralPath geometricPath) {
        return null;
    }

    protected static void consume_W(GraphicsState graphicState, GeneralPath geometricPath) {
        if (geometricPath != null) {
            geometricPath.setWindingRule(GeneralPath.WIND_NON_ZERO);
            geometricPath.closePath();
            graphicState.setClip(geometricPath);
        }
    }

    protected static void consume_v(Stack<Object> stack,
                                    GeneralPath geometricPath) {
        float y3 = ((Number) stack.pop()).floatValue();
        float x3 = ((Number) stack.pop()).floatValue();
        float y2 = ((Number) stack.pop()).floatValue();
        float x2 = ((Number) stack.pop()).floatValue();
        geometricPath.curveTo(
                (float) geometricPath.getCurrentPoint().getX(),
                (float) geometricPath.getCurrentPoint().getY(),
                x2,
                y2,
                x3,
                y3);
    }

    protected static void consume_y(Stack<Object> stack,
                                    GeneralPath geometricPath) {
        float y3 = ((Number) stack.pop()).floatValue();
        float x3 = ((Number) stack.pop()).floatValue();
        float y1 = ((Number) stack.pop()).floatValue();
        float x1 = ((Number) stack.pop()).floatValue();
        geometricPath.curveTo(x1, y1, x3, y3, x3, y3);
    }

    protected static GeneralPath consume_B(GraphicsState graphicState,
                                           Shapes shapes,
                                           GeneralPath geometricPath)
            throws InterruptedException {
        if (geometricPath != null) {
            geometricPath.setWindingRule(GeneralPath.WIND_NON_ZERO);
            commonFill(shapes, graphicState, geometricPath);
            commonStroke(graphicState, shapes, geometricPath);
        }
        return null;
    }

    protected static GraphicsState consume_d0(GraphicsState graphicState, Stack<Object> stack) {
        // save the stack
        graphicState = graphicState.save();
        // need two pops to get  Wx and Wy data
        float y = ((Number) stack.pop()).floatValue();
        float x = ((Number) stack.pop()).floatValue();
        TextState textState = graphicState.getTextState();
        textState.setType3HorizontalDisplacement(new Point.Float(x, y));
        return graphicState;
    }

    protected static GeneralPath consume_s(GraphicsState graphicState,
                                           Shapes shapes,
                                           GeneralPath geometricPath) throws InterruptedException {
        if (geometricPath != null) {
            geometricPath.closePath();
            commonStroke(graphicState, shapes, geometricPath);
        }
        return null;
    }

    protected static GeneralPath consume_b_star(GraphicsState graphicState,
                                                Shapes shapes,
                                                GeneralPath geometricPath)
            throws InterruptedException {
        if (geometricPath != null) {
            geometricPath.setWindingRule(GeneralPath.WIND_EVEN_ODD);
            geometricPath.closePath();
            commonStroke(graphicState, shapes, geometricPath);
            commonFill(shapes, graphicState, geometricPath);
        }
        return null;
    }

    protected static GraphicsState consume_d1(GraphicsState graphicState, Stack<Object> stack) {
        // save the stack
        graphicState = graphicState.save();
        // need two pops to get  Wx and Wy data
        float[] affineTransform = popFloatInOrder(stack, 6);
        TextState textState = graphicState.getTextState();
        textState.setType3HorizontalDisplacement(
                new Point2D.Float(affineTransform[0], affineTransform[1]));
        textState.setType3BBox(new PRectangle(
                new Point2D.Float(affineTransform[2], affineTransform[3]),
                new Point2D.Float(affineTransform[4], affineTransform[5])));
        return graphicState;
    }

    protected static GeneralPath consume_B_star(GraphicsState graphicState,
                                                Shapes shapes,
                                                GeneralPath geometricPath)
            throws InterruptedException {
        if (geometricPath != null) {
            geometricPath.setWindingRule(GeneralPath.WIND_EVEN_ODD);
            commonStroke(graphicState, shapes, geometricPath);
            commonFill(shapes, graphicState, geometricPath);
        }
        return null;
    }

    protected static void consume_W_star(GraphicsState graphicState,
                                         GeneralPath geometricPath) {
        if (geometricPath != null) {
            geometricPath.setWindingRule(GeneralPath.WIND_EVEN_ODD);
            geometricPath.closePath();
            graphicState.setClip(geometricPath);
        }
    }

    protected static void consume_DP(Stack<Object> stack) {
        stack.pop(); // properties
        stack.pop(); // name
    }

    protected static void consume_MP(Stack<Object> stack) {
        stack.pop();
    }

    protected static void consume_sh(GraphicsState graphicState, Stack<Object> stack,
                                     Shapes shapes,
                                     Resources resources) throws InterruptedException {
        Object o = stack.peek();
        // if a name then we are dealing with a pattern.
        if (o instanceof Name) {
            Name patternName = (Name) stack.pop();
            Pattern pattern = resources.getShading(patternName);
            if (pattern != null) {
                pattern.init(graphicState);
                // we paint the shape and color shading as defined
                // by the pattern dictionary and respect the current clip
                // TODO further work is needed here to build out the pattern fill.
                if (graphicState.getExtGState() != null &&
                        graphicState.getExtGState().getSMask() != null) {
                    setAlpha(shapes, graphicState,
                            graphicState.getAlphaRule(),
                            0.50f);
                } else {
                    setAlpha(shapes, graphicState,
                            graphicState.getAlphaRule(),
                            graphicState.getFillAlpha());
                }
                shapes.add(new PaintDrawCmd(pattern.getPaint()));
            } else {
                // apply the current fill color along ith a little alpha
                // to at least try to paint a colour for an unsupported mesh
                // type pattern.
                setAlpha(shapes, graphicState,
                        graphicState.getAlphaRule(),
                        0.50f);
                shapes.add(new PaintDrawCmd(graphicState.getFillColor()));
            }
            shapes.add(new ShapeDrawCmd(graphicState.getClip()));
            shapes.add(new FillDrawCmd());
        }
    }

    protected static void consume_TJ(GraphicsState graphicState, Stack<Object> stack,
                                     Shapes shapes,
                                     TextMetrics textMetrics,
                                     GlyphOutlineClip glyphOutlineClip,
                                     LinkedList<OptionalContents> oCGs,
                                     ContentStreamCallback contentStreamCallback) throws IOException {
        // apply scaling
        AffineTransform tmp = applyTextScaling(graphicState);
        // apply transparency
        setAlpha(shapes, graphicState, AlphaPaintType.ALPHA_FILL);
        java.util.List v = (java.util.List) stack.pop();
        Number f;
        StringObject stringObject;
        TextState textState;
        ArrayList<TextSprite> textOperators = new ArrayList<>(v.size());
        for (Object currentObject : v) {
            if (currentObject instanceof StringObject) {
                stringObject = (StringObject) currentObject;
                textState = graphicState.getTextState();
                // draw string takes care of PageText extraction
                if (stringObject.getLength() > 0) {
                    TextSprite textSprite = drawString(stringObject.getLiteralStringBuffer(
                                    textState.font.getSubTypeFormat(),
                                    textState.font.getFont()),
                            textMetrics,
                            graphicState.getTextState(), shapes, glyphOutlineClip,
                            graphicState, oCGs, contentStreamCallback);
                    textOperators.add(textSprite);
                }
            } else if (currentObject instanceof Number) {
                f = (Number) currentObject;
                textMetrics.getAdvance().x -= (f.floatValue() / 1000f) *
                        graphicState.getTextState().currentfont.getSize();
            }
            textMetrics.setPreviousAdvance(textMetrics.getAdvance().x);
        }
        graphicState.set(tmp);
        if (contentStreamCallback != null) {
            contentStreamCallback.writeModifiedStringObject(textOperators, Operands.TJ);
        }
    }

    protected static void consume_Tj(GraphicsState graphicState, Stack<Object> stack,
                                     Shapes shapes,
                                     TextMetrics textMetrics,
                                     GlyphOutlineClip glyphOutlineClip,
                                     LinkedList<OptionalContents> oCGs,
                                     ContentStreamCallback contentStreamCallback) throws IOException {
        if (stack.size() != 0) {
            Object tjValue = stack.pop();
            StringObject stringObject;
            TextState textState;
            if (tjValue instanceof StringObject) {
                stringObject = (StringObject) tjValue;
                textState = graphicState.getTextState();
                ArrayList<TextSprite> textOperators = new ArrayList<>(1);
                // apply scaling
                AffineTransform tmp = applyTextScaling(graphicState);
                // apply transparency
                setAlpha(shapes, graphicState, AlphaPaintType.ALPHA_FILL);
                // draw string will take care of text pageText construction
                if (stringObject.getLength() > 0) {
                    TextSprite textSprite = drawString(stringObject.getLiteralStringBuffer(
                                    textState.font.getSubTypeFormat(),
                                    textState.font.getFont()),
                            textMetrics,
                            textState,
                            shapes,
                            glyphOutlineClip,
                            graphicState, oCGs, contentStreamCallback);
                    textOperators.add(textSprite);
                }
                graphicState.set(tmp);
                // pass them back to the redactor,
                if (contentStreamCallback != null) {
                    contentStreamCallback.writeModifiedStringObject(textOperators, Operands.Tj);
                }
            }
        }
    }

    /**
     * Utility method for calculating the advanceX need for the
     * <code>displayText</code> given the strings parsed textState.  Each of
     * <code>displayText</code> glyphs and respective, text state is added to
     * the shape's collection.
     *
     * @param displayText      text that will be drawn to the screen
     * @param textMetrics      current advanceX of last drawn string,
     *                         last advance of where the string should be drawn
     * @param textState        formatting properties associated with displayText
     * @param glyphOutlineClip clip used for cut out fonts.
     * @param graphicState     current graphics state
     * @param oCGs             optional content group, can be null
     * @param shapes           collection of all shapes for page content being parsed.
     */
    private static TextSprite drawString(
            StringBuilder displayText,
            TextMetrics textMetrics,
            TextState textState,
            Shapes shapes,
            GlyphOutlineClip glyphOutlineClip,
            GraphicsState graphicState,
            LinkedList<OptionalContents> oCGs,
            ContentStreamCallback contentStreamCallback) {

        float advanceX = textMetrics.getAdvance().x;
        float advanceY = textMetrics.getAdvance().y;

        if (displayText.length() == 0) {
            return null;
        }

        // Position of previous Glyph, all relative to text block
        float lastx = 0, lasty = 0;
        // Make sure that the previous advanceX is greater than then where we
        // are going to place the next glyph,  see not 57 in 1.6 spec for more
        // information.
        char currentChar = displayText.charAt(0);
        // Position of the specified glyph relative to the origin of glyphVector
        float firstCharWidth = (float) textState.currentfont.getAdvance(currentChar).getX();

        if ((advanceX + firstCharWidth) < textMetrics.getPreviousAdvance()) {
            advanceX = textMetrics.getPreviousAdvance();
        }

        // Data need on font
        FontFile currentFont = textState.currentfont;
        boolean isVerticalWriting = textState.font.isVerticalWriting();
        // int spaceCharacter = currentFont.getSpaceEchar();

        // font metrics data
        float textRise = textState.trise;
        float characterSpace = textState.cspace * textState.hScalling;
        float whiteSpace = textState.wspace * textState.hScalling;
        int textLength = displayText.length();

        // create a new sprite to hold the text objects
        TextSprite textSprites =
                new TextSprite(currentFont,
                        textState.font.getSubTypeFormat(),
                        textLength,
                        new AffineTransform(graphicState.getCTM()),
                        new AffineTransform(textState.tmatrix));

        // glyph placement params
        float currentX, currentY;
        float newAdvanceX, newAdvanceY;
        for (int i = 0; i < textLength; i++) {
            currentChar = displayText.charAt(i);

            if (enabledFontFallback) {
                boolean display = currentFont.canDisplay(currentChar);
                // slow display test, but allows us to fall back on a different font if needed.
                if (!display) {
                    FontFile fontFile = FontManager.getInstance().getInstance(currentFont.getName(), 0);
                    textSprites.setFont(fontFile);
                }
            }

            // Position of the specified glyph relative to the origin of glyphVector
            // advance is handled by the particular font implementation.
            newAdvanceX = (float) currentFont.getAdvance(currentChar).getX();

            newAdvanceY = newAdvanceX;
            if (!isVerticalWriting) {
                // add fonts rise to the glyph position (sup,sub scripts)
                currentX = advanceX + lastx;
                currentY = lasty - textRise;
                lastx += newAdvanceX;
                // store the pre Tc and Tw dimension.
                textMetrics.setPreviousAdvance(lastx);
                lastx += characterSpace;
                // lastly add space widths, no funny corner case yet for this one.
                if (displayText.charAt(i) == 32) { // currently to unreliable currentFont.getSpaceEchar()
                    lastx += whiteSpace;
                }
            } else {
                // add fonts rise to the glyph position (sup,sub scripts)
                lasty += (newAdvanceY - textRise);
                currentX = advanceX - (newAdvanceX / 2.0f);
                currentY = advanceY + lasty;
            }

            // get normalized from text sprite
            GlyphText glyphText = textSprites.addText(
                    currentChar, // cid
                    textState.fontName,
                    textState.currentfont.toUnicode(currentChar), // unicode value
                    currentX, currentY, newAdvanceX, newAdvanceY, shapes.getRotation());
            shapes.getPageText().addGlyph(glyphText, oCGs);

            if (contentStreamCallback != null) {
                contentStreamCallback.checkAndModifyText(glyphText);
            }

        }
        // append the finally offset of the width of the character
        advanceX += lastx;
        advanceY += lasty;

        /*
          The text rendering mode, Tmode, determines whether showing text
          causes glyph outlines to be stroked, filled, used as a clipping
          boundary, or some combination of the three.

          No Support for 4, 5, 6 and 7.

          0 - Fill text
          1 - Stroke text
          2 - fill, then stroke text
          3 - Neither fill nor stroke text (invisible)
          4 - Fill text and add to path for clipping
          5 - Stroke text and add to path for clipping.
          6 - Fill, then stroke text and add to path for clipping.
          7 - Add text to path for clipping.
         */

        int rMode = textState.rmode;
        switch (rMode) {
            // fill text: 0
            case TextState.MODE_FILL:
                drawModeFill(graphicState, textSprites, shapes, rMode);
                break;
            // Stroke text: 1
            case TextState.MODE_STROKE:
                drawModeStroke(graphicState, textSprites, textState, shapes, rMode);
                break;
            // Fill, then stroke text: 2
            case TextState.MODE_FILL_STROKE:
                drawModeFillStroke(graphicState, textSprites, textState, shapes, rMode);
                break;
            // Neither fill nor stroke text (invisible): 3
            case TextState.MODE_INVISIBLE:
                // do nothing
                break;
            // Fill text and add to path for clipping: 4
            case TextState.MODE_FILL_ADD:
                drawModeFill(graphicState, textSprites, shapes, rMode);
                glyphOutlineClip.addTextSprite(textSprites);
                break;
            // Stroke Text and add to path for clipping: 5
            case TextState.MODE_STROKE_ADD:
                drawModeStroke(graphicState, textSprites, textState, shapes, rMode);
                glyphOutlineClip.addTextSprite(textSprites);
                break;
            // Fill, then stroke text adn add to path for clipping: 6
            case TextState.MODE_FILL_STROKE_ADD:
                drawModeFillStroke(graphicState, textSprites, textState, shapes, rMode);
                glyphOutlineClip.addTextSprite(textSprites);
                break;
            // Add text to path for clipping: 7
            case TextState.MODE_ADD:
                glyphOutlineClip.addTextSprite(textSprites);
                break;
        }
        textMetrics.getAdvance().setLocation(advanceX, advanceY);
        return textSprites;
    }

    /**
     * Utility Method for adding a text sprites to the Shapes stack, given the
     * specified rmode.
     *
     * @param textSprites  text to add to shapes stack
     * @param shapes       shapes stack
     * @param rmode        write mode
     * @param graphicState graphics state
     */
    private static void drawModeFill(GraphicsState graphicState,
                                     TextSprite textSprites, Shapes shapes, int rmode) {
        textSprites.setRMode(rmode);
        textSprites.setStrokeColor(graphicState.getFillColor());
        shapes.add(new ColorDrawCmd(graphicState.getFillColor()));
        shapes.add(new TextSpriteDrawCmd(textSprites));
    }

    /**
     * Utility Method for adding a text sprites to the Shapes stack, given the
     * specifed rmode.
     *
     * @param textSprites  text to add to shapes stack
     * @param shapes       shapes stack
     * @param textState    text state used to build new stroke
     * @param rmode        write mode
     * @param graphicState current graphics state
     */
    private static void drawModeStroke(GraphicsState graphicState,
                                       TextSprite textSprites, TextState textState,
                                       Shapes shapes, int rmode) {
        // setup textSprite with a strokeColor and the correct rmode
        textSprites.setRMode(rmode);
        textSprites.setStrokeColor(graphicState.getStrokeColor());
        // save the old line width
        float old = graphicState.getLineWidth();

        // set the line width for the glyph
        float lineWidth = graphicState.getLineWidth();
        double scale = textState.tmatrix.getScaleX();
        // double check for a near zero value as it will really mess up the division result, zero is just fine.
        if (scale > 0.001 || scale == 0) {
            lineWidth /= scale;
        } else {
            // corner case stroke adjustment,  still can't find anything in spec about this.
            lineWidth *= scale * 100;
        }
        graphicState.setLineWidth(lineWidth);
        // update the stroke and add the text to shapes
        setStroke(shapes, graphicState);
        shapes.add(new ColorDrawCmd(graphicState.getStrokeColor()));
        shapes.add(new TextSpriteDrawCmd(textSprites));

        // restore graphics state
        graphicState.setLineWidth(old);
        setStroke(shapes, graphicState);
    }

    /**
     * Utility Method for adding a text sprites to the Shapes stack, given the
     * specified rmode.
     *
     * @param textSprites  text to add to shapes stack
     * @param textState    text state used to build new stroke
     * @param shapes       shapes stack
     * @param rmode        write mode
     * @param graphicState current graphics state
     */
    private static void drawModeFillStroke(GraphicsState graphicState,
                                           TextSprite textSprites, TextState textState,
                                           Shapes shapes, int rmode) {
        // setup textSprite with a strokeColor and the correct rmode
        textSprites.setRMode(rmode);
        textSprites.setStrokeColor(graphicState.getStrokeColor());
        // save the old line width
        float old = graphicState.getLineWidth();

        // set the line width for the glyph
        float lineWidth = graphicState.getLineWidth();
        double scale = textState.tmatrix.getScaleX();
        // double check for a near zero value as it will really mess up the division result, zero is just fine.
        if (scale > 0.0001 || scale == 0) {
            lineWidth /= scale;
            graphicState.setLineWidth(lineWidth);
        }
        // update the stroke and add the text to shapes
        setStroke(shapes, graphicState);
        shapes.add(new ColorDrawCmd(graphicState.getFillColor()));
        shapes.add(new TextSpriteDrawCmd(textSprites));

        // restore graphics state
        graphicState.setLineWidth(old);
        setStroke(shapes, graphicState);
    }

    /**
     * Common stroke operations used by S and s. Takes into
     * account patternColour and regular old fill colour.
     *
     * @param shapes        current shapes stack
     * @param graphicState  current graphics state
     * @param geometricPath current path.
     * @throws InterruptedException thread interrupted.
     */
    private static void commonStroke(GraphicsState graphicState, Shapes shapes, GeneralPath geometricPath)
            throws InterruptedException {

        // get current fill alpha and concatenate with overprinting if present
        if (graphicState.isOverprintStroking()) {
            setAlpha(shapes, graphicState, graphicState.getAlphaRule(),
                    commonOverPrintAlpha(graphicState.getStrokeAlpha(),
                            graphicState.getStrokeColorSpace()));
        }
        // The knockout effect can only be achieved by changing the alpha
        // composite to source.  I don't have a test case for this for stroke
        // but what we do for stroke is usually what we do for fill...
        else if (graphicState.isKnockOut()) {
            setAlpha(shapes, graphicState, AlphaComposite.SRC, graphicState.getStrokeAlpha());
        }

        // found a PatternColor
        if (graphicState.getStrokeColorSpace() instanceof PatternColor) {
            // Create a pointer to the pattern colour
            PatternColor patternColor = (PatternColor) graphicState.getStrokeColorSpace();
            // grab the pattern from the colour
            Pattern pattern = patternColor.getPattern();
            // Start processing tiling pattern
            if (pattern != null &&
                    pattern.getPatternType() == Pattern.PATTERN_TYPE_TILING) {
                // currently not doing any special handling for colour or uncoloured
                // paint, as it done when the scn or sc tokens are parsed.
                TilingPattern tilingPattern = (TilingPattern) pattern;
                // 1. save the graphics context
                graphicState = graphicState.save();
                // 2. install the graphic state
                tilingPattern.setParentGraphicState(graphicState);
                tilingPattern.init(graphicState);
                // 4. Restore the saved graphics state
                graphicState.restore();
                if (tilingPattern.getbBoxMod() != null) {
                    shapes.add(new TilingPatternDrawCmd(tilingPattern));
                }
                shapes.add(new ShapeDrawCmd(geometricPath));
                shapes.add(new DrawDrawCmd());
            } else if (pattern != null &&
                    pattern.getPatternType() == Pattern.PATTERN_TYPE_SHADING) {
                pattern.init(graphicState);
                shapes.add(new PaintDrawCmd(pattern.getPaint()));
                shapes.add(new ShapeDrawCmd(geometricPath));
                shapes.add(new DrawDrawCmd());
            }
        } else {
            setAlpha(shapes, graphicState, AlphaPaintType.ALPHA_STROKE);
            shapes.add(new ColorDrawCmd(graphicState.getStrokeColor()));
            shapes.add(new ShapeDrawCmd(geometricPath));
            shapes.add(new DrawDrawCmd());
        }
        // set alpha back to original value.
//        if (graphicState.isOverprintStroking()) {
//            setAlpha(shapes, graphicState, AlphaComposite.SRC_OVER, graphicState.getFillAlpha());
//        }
    }

    /**
     * Utility method for fudging overprinting calculation for screen
     * representation.  This feature is optional an off by default.
     * <p>
     * Can be enable with -Dorg.icepdf.core.enabledOverPrint=true
     *
     * @param alpha      alph constant
     * @param colorSpace color space of over print
     * @return tweaked over printing alpha
     */
    private static float commonOverPrintAlpha(float alpha, PColorSpace colorSpace) {
        if (!enabledOverPrint) {
            return alpha;
        }
        if (colorSpace instanceof DeviceN) {// || colorSpace instanceof Separation) {
            // if alpha is already present we reduce it, and we minimize
            // it if it is already lower than our over paint.  This an approximation
            // only for improved screen representation.
            if (alpha != 1.0f && alpha > OVERPAINT_ALPHA) {
                alpha -= OVERPAINT_ALPHA;
            } else if (alpha < OVERPAINT_ALPHA) {
                //            alpha = 0.1f;
            } else {
                alpha = OVERPAINT_ALPHA;
            }
            return alpha;
        }
        return alpha;
    }

    /**
     * Common fill operations used by f, F, F*, b, b*,  B, B*. Takes into
     * account patternColour and regular old fill colour.
     *
     * @param shapes        current shapes stack
     * @param graphicState  current graphics state.
     * @param geometricPath current path.
     * @throws InterruptedException thread interrupted.
     */
    private static void commonFill(Shapes shapes, GraphicsState graphicState, GeneralPath geometricPath)
            throws InterruptedException {

        // get current fill alpha and concatenate with overprinting if present
        if (graphicState.isOverprintOther()) {
            setAlpha(shapes, graphicState, graphicState.getAlphaRule(),
                    commonOverPrintAlpha(graphicState.getFillAlpha(),
                            graphicState.getFillColorSpace()));
        }
        // avoid doing fill, as we likely have  blending mode that will obfuscate the underlying
        // content.
        if (graphicState.getExtGState() != null &&
                graphicState.getExtGState().getSMask() != null) {
            return;
        }
        // The knockout effect can only be achieved by changing the alpha
        // composite to source.
        else if (graphicState.isKnockOut()) {
            setAlpha(shapes, graphicState, AlphaComposite.SRC, graphicState.getFillAlpha());
        } else if (graphicState.getExtGState() == null || graphicState.getExtGState().getBlendingMode() == null) {
            setAlpha(shapes, graphicState, graphicState.getAlphaRule(), graphicState.getFillAlpha());
        }

        // found a PatternColor
        if (graphicState.getFillColorSpace() instanceof PatternColor) {
            // Create a pointer to the pattern colour
            PatternColor patternColor = (PatternColor) graphicState.getFillColorSpace();
            // grab the pattern from the colour
            Pattern pattern = patternColor.getPattern();
            // Start processing tiling pattern
            if (pattern != null &&
                    pattern.getPatternType() == Pattern.PATTERN_TYPE_TILING) {
                // currently not doing any special handling for colour or uncoloured
                // paint, as it done when the scn or sc tokens are parsed.
                TilingPattern tilingPattern = (TilingPattern) pattern;
                // 1. save the graphics context
                graphicState = graphicState.save();
                // 2.  install the graphic state
                tilingPattern.setParentGraphicState(graphicState);
                tilingPattern.init(graphicState);
                // 3.  Restore the saved graphics state
                graphicState.restore();
                if (tilingPattern.getbBoxMod() != null) {
                    shapes.add(new TilingPatternDrawCmd(tilingPattern));
                }
                shapes.add(new ShapeDrawCmd(geometricPath));
                shapes.add(new FillDrawCmd());
            } else if (pattern != null &&
                    pattern.getPatternType() == Pattern.PATTERN_TYPE_SHADING) {
                pattern.init(graphicState);
                shapes.add(new PaintDrawCmd(pattern.getPaint()));
                shapes.add(new ShapeDrawCmd(geometricPath));
                shapes.add(new FillDrawCmd());
            }

        } else {
            if (graphicState.getExtGState() != null
                    && graphicState.getExtGState().getBlendingMode() != null
                    && graphicState.getExtGState().getOverprintMode() == 1) {
                shapes.add(new BlendCompositeDrawCmd(graphicState.getExtGState().getBlendingMode(),
                        graphicState.getFillAlpha()));
            }
            shapes.add(new ColorDrawCmd(graphicState.getFillColor()));
            shapes.add(new ShapeDrawCmd(geometricPath));
            shapes.add(new FillDrawCmd());
        }
        // add old alpha back to stack
//        if (graphicState.isOverprintOther()) {
//            setAlpha(shapes, graphicState, graphicState.getAlphaRule(), graphicState.getFillAlpha());
//        }`
    }

    private static Color commonRGB(Stack<Object> stack) {
        float blue = ((Number) stack.pop()).floatValue();
        float green = ((Number) stack.pop()).floatValue();
        float red = ((Number) stack.pop()).floatValue();
        blue = Math.max(0.0f, Math.min(1.0f, blue));
        green = Math.max(0.0f, Math.min(1.0f, green));
        red = Math.max(0.0f, Math.min(1.0f, red));
        return new Color(red, green, blue);
    }

    private static float[] commonCMYK(Stack<Object> stack) {
        float k = ((Number) stack.pop()).floatValue();
        float y = ((Number) stack.pop()).floatValue();
        float m = ((Number) stack.pop()).floatValue();
        float c = ((Number) stack.pop()).floatValue();
        return new float[]{c, m, y, k};
    }

    private static float[] popFloatInOrder(Stack<Object> stack, int number) {
        float[] f = new float[number];
        int nCount = number - 1;
        // peek and pop all the colour floats
        while (!stack.isEmpty() && stack.peek() instanceof Number && nCount >= 0) {
            f[nCount] = ((Number) stack.pop()).floatValue();
            nCount--;
        }
        return f;
    }

    /**
     * Sets the state of the BasicStroke with the latest values from the
     * graphicSate instance value:
     * graphicState.lineWidth - line width
     * graphicState.lineCap - line cap type
     * graphicState.lineJoin - line join type
     * graphicState.miterLimit -  miter limit
     *
     * @param shapes       current Shapes object for the page being parsed
     * @param graphicState graphic state used to build this stroke instance.
     */
    protected static void setStroke(Shapes shapes, GraphicsState graphicState) {
        shapes.add(new StrokeDrawCmd(new BasicStroke(graphicState.getLineWidth(),
                graphicState.getLineCap(),
                graphicState.getLineJoin(),
                graphicState.getMiterLimit(),
                graphicState.getDashArray(),
                graphicState.getDashPhase())));
    }

    /**
     * Text scaling must be applied to the main graphic state.  It can not
     * be applied to the Text Matrix.  We only have two test cases for its
     * use, but it appears that the scaling has to bee applied before a text
     * write operand occurs, otherwise a call to Tm seems to break text
     * positioning.
     * <p>
     * Scaling is special as it can be negative and thus apply a horizontal
     * flip on the graphic state.
     *
     * @param graphicState current graphics state.
     * @return new text scaling AffineTransform.
     */
    private static AffineTransform applyTextScaling(GraphicsState graphicState) {
        // get the current CTM
        AffineTransform af = new AffineTransform(graphicState.getCTM());
        // the mystery continues,  it appears that only the negative or positive
        // value of tz is actually used.  If the original non 1 number is used the
        // layout will be messed up.
        AffineTransform oldHScaling = new AffineTransform(graphicState.getCTM());
        float hScaling = graphicState.getTextState().hScalling;
        AffineTransform horizontalScalingTransform =
                new AffineTransform(
                        af.getScaleX() * hScaling,
                        af.getShearY() * hScaling,
                        af.getShearX(),
                        af.getScaleY(),
                        af.getTranslateX(), af.getTranslateY());
        // add the transformation to the graphics state
        graphicState.set(horizontalScalingTransform);

        return oldHScaling;
    }

    /**
     * Adds a new Alpha Composite object ot the shapes stack.
     *
     * @param graphicsState  current graphics state
     * @param shapes         current shapes vector to add Alpha Composite to
     * @param alphaPaintType fill or stroke operation applying the alpha
     */
    private static void setAlpha(Shapes shapes, GraphicsState graphicsState, AlphaPaintType alphaPaintType) {
        if (graphicsState.getExtGState() != null
                && graphicsState.getExtGState().getBlendingMode() != null
                && !graphicsState.getExtGState().getBlendingMode().equals(BlendComposite.NORMAL_VALUE)) {
            float alpha = graphicsState.getFillAlpha();
            shapes.add(new BlendCompositeDrawCmd(graphicsState.getExtGState().getBlendingMode(),
                    graphicsState.getFillAlpha()));
            // test seems to address some do issues
            if (alpha >= 0 && alpha < 1.0) {
                setAlpha(shapes, graphicsState, graphicsState.getAlphaRule(), graphicsState.getFillAlpha());
            }
        } else {
            AlphaComposite alphaComposite = AlphaComposite.getInstance(
                    graphicsState.getAlphaRule(),
                    AlphaPaintType.ALPHA_FILL == alphaPaintType ?
                            graphicsState.getFillAlpha() : graphicsState.getStrokeAlpha());
            shapes.add(new AlphaDrawCmd(alphaComposite));
        }
    }

    /**
     * Adds a new Alpha Composite object ot the shapes stack.
     *
     * @param graphicsState current graphics state
     * @param shapes        - current shapes vector to add Alpha Composite to
     * @param rule          - rule to apply to the alphaComposite.
     * @param alpha         - alpha value, opaque = 1.0f.
     */
    protected static void setAlpha(Shapes shapes, GraphicsState graphicsState, int rule, float alpha) {
        // Build the alpha composite object and add it to the shapes but only
        // if it has changed.
        if (shapes != null && (shapes.getAlpha() != alpha || shapes.getRule() != rule)) {
            AlphaComposite alphaComposite =
                    AlphaComposite.getInstance(rule,
                            alpha);
            shapes.add(new AlphaDrawCmd(alphaComposite));
        }
    }

    public void setGlyph2UserSpaceScale(float scale) {
        glyph2UserSpaceScale = scale;
    }

}

