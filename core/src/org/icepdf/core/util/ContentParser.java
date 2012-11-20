/*
 * Copyright 2006-2012 ICEsoft Technologies Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an "AS
 * IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either * express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */
package org.icepdf.core.util;

import org.icepdf.core.pobjects.*;
import org.icepdf.core.pobjects.fonts.FontFactory;
import org.icepdf.core.pobjects.fonts.FontFile;
import org.icepdf.core.pobjects.graphics.*;
import org.icepdf.core.pobjects.graphics.commands.*;
import org.icepdf.core.pobjects.graphics.text.GlyphText;
import org.icepdf.core.pobjects.graphics.text.PageText;
import org.icepdf.core.util.content.Lexer;
import org.icepdf.core.util.content.OperandNames;

import java.awt.*;
import java.awt.geom.*;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.List;
import java.util.Stack;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The ContentParser is responsible for parsing a page's content streams.  The
 * parsed text, image and other PDF object types are added the pages Shapes
 * object for later drawing and display.
 */
public class ContentParser {

    private static final Logger logger =
            Logger.getLogger(ContentParser.class.toString());

    private static boolean disableTransparencyGroups;

    static {
        // decide if large images will be scaled
        disableTransparencyGroups =
                Defs.sysPropertyBoolean("org.icepdf.core.disableTransparencyGroup",
                        false);

    }

    public static final float OVERPAINT_ALPHA = 0.4f;

    private GraphicsState graphicState;
    private Library library;
    private Resources resources;

    private Shapes shapes;

    // represents a geometric path constructed from straight lines, and
    // quadratic and cubic (Beauctezier) curves.  It can contain
    // multiple sub paths.
    private GeneralPath geometricPath;

    // flag to handle none text based coordinate operand "cm" inside of a text block
    private boolean inTextBlock;

    // TextBlock affine transform can be altered by the "cm" operand an thus
    // the text base affine transform must be accessible outside the parsTtext method
    private AffineTransform textBlockBase;

    // stack to help with the parse
    private Stack<Object> stack = new Stack<Object>();

    /**
     * @param l PDF library master object.
     * @param r resources
     */
    public ContentParser(Library l, Resources r) {
        library = l;
        resources = r;

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
     *         parse method has not been previously called.
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
     * @param streamBytes byte stream containing page content
     * @return a Shapes Ojbect containing all the pages text and images shapes.
     * @throws InterruptedException if current parse thread is interruped.
     */
    public ContentParser parse(byte[][] streamBytes) throws InterruptedException, IOException {
        if (shapes == null) {
            shapes = new Shapes();
            // Normal, clean content parse where graphics state is null
            if (graphicState == null) {
                graphicState = new GraphicsState(shapes);
            }
            // If not null we have an Form XObject that contains a content stream
            // and we must copy the previous graphics states draw settings in order
            // preserve colour and fill data for the XOjbects content stream.
            else {
                // the graphics state gets a new coordinate system.
                graphicState.setCTM(new AffineTransform());
                // reset the clipping area.
                graphicState.setClip(null);
                // copy previous stroke info
                setStroke(shapes, graphicState);
                // assign new shapes to the new graphics state
                graphicState.setShapes(shapes);
            }
        }

        if (logger.isLoggable(Level.FINER)) {
            // print all the stream byte chunks.
            for (byte[] streamByte : streamBytes) {
                if (streamByte != null) {
                    String tmp = new String(streamByte, "ISO-8859-1");
                    logger.finer("Content = " + tmp);
                }
            }
        }

        // great a parser to get tokens for stream
        Lexer lexer;

        // test case for progress bar
        lexer = new Lexer();
        lexer.contentStream(streamBytes);

        // text block y offset.
        float yBTstart = 0;

        try {
            // loop through each token returned form the parser
            Object tok;
            while (true) {

                tok = lexer.nextToken();
//                if (logger.isLoggable(Level.FINEST)){
//                    if (tok instanceof Integer) {
//                        logger.finest(OperandNames.OPP_LOOKUP.get(tok));
//                    } else {
//                        logger.finest(String.valueOf(tok));
//                    }
//                }
                // no more tokens break out.
                if (tok == null) {
                    break;
                }

                // add any names and numbers and every thing else on the
                // stack for future reference
                if (!(tok instanceof Integer)) {
                    stack.push(tok);
                } else {
                    int operand = (Integer) tok;
                    // Append a straight line segment from the current point to the
                    // point (x, y). The new current point is (x, y).
                    switch (operand) {
                        case OperandNames.OP_l:
                            float y = ((Number) stack.pop()).floatValue();
                            float x = ((Number) stack.pop()).floatValue();
                            if (geometricPath == null) {
                                geometricPath = new GeneralPath();
                            }
                            geometricPath.lineTo(x, y);
                            break;

                        // Begin a new subpath by moving the current point to
                        // coordinates (x, y), omitting any connecting line segment. If
                        // the previous path construction operator in the current path
                        // was also m, the new m overrides it; no vestige of the
                        // previous m operation remains in the path.
                        case OperandNames.OP_m:
                            if (geometricPath == null) {
                                geometricPath = new GeneralPath();
                            }
                            y = ((Number) stack.pop()).floatValue();
                            x = ((Number) stack.pop()).floatValue();
                            geometricPath.moveTo(x, y);
                            break;

                        // Append a cubic Bezier curve to the current path. The curve
                        // extends from the current point to the point (x3, y3), using
                        // (x1, y1) and (x2, y2) as the Bezier control points.
                        // The new current point is (x3, y3).
                        case OperandNames.OP_c:
                            float y3 = ((Number) stack.pop()).floatValue();
                            float x3 = ((Number) stack.pop()).floatValue();
                            float y2 = ((Number) stack.pop()).floatValue();
                            float x2 = ((Number) stack.pop()).floatValue();
                            float y1 = ((Number) stack.pop()).floatValue();
                            float x1 = ((Number) stack.pop()).floatValue();
                            if (geometricPath == null) {
                                geometricPath = new GeneralPath();
                            }
                            geometricPath.curveTo(x1, y1, x2, y2, x3, y3);
                            break;

                        // Stroke the path
                        case OperandNames.OP_S:
                            if (geometricPath != null) {
                                commonStroke(graphicState, shapes, geometricPath);
                                geometricPath = null;
                            }
                            break;

                        // Font selection
                        case OperandNames.OP_Tf:
                            consume_Tf(graphicState, stack, resources);
                            break;

                        // Begin a text object, initializing the text matrix, Tm, and
                        // the text line matrix, Tlm, to the identity matrix. Text
                        // objects cannot be nested; a second BT cannot appear before
                        // an ET.
                        case OperandNames.OP_BT:
                            // start parseText, which parses until ET is reached
                            yBTstart = parseText(lexer, shapes, yBTstart);
                            break;

                        // Fill the path, using the nonzero winding number rule to
                        // determine the region to fill (see "Nonzero Winding
                        // Number Rule" ). Any subpaths that are open are implicitly
                        // closed before being filled. f or F
                        case OperandNames.OP_F:
                            if (geometricPath != null) {
                                geometricPath.setWindingRule(GeneralPath.WIND_NON_ZERO);
                                commonFill(shapes, geometricPath);
                            }
                            geometricPath = null;
                            break;
                        case OperandNames.OP_f:
                            if (geometricPath != null) {
                                geometricPath.setWindingRule(GeneralPath.WIND_NON_ZERO);
                                commonFill(shapes, geometricPath);
                            }
                            geometricPath = null;
                            break;

                        // Saves Graphics State, should copy the entire  graphics state onto
                        // the graphicsState object's stack
                        case OperandNames.OP_q:
                            graphicState = consume_q(graphicState);
                            break;
                        // Restore Graphics State, should restore the entire graphics state
                        // to its former value by popping it from the stack
                        case OperandNames.OP_Q:
                            graphicState = consume_Q(graphicState, shapes);
                            break;

                        // Append a rectangle to the current path as a complete subpath,
                        // with lower-left corner (x, y) and dimensions width and height
                        // in user space. The operation x y width height re is equivalent to
                        //        x y m
                        //        (x + width) y l
                        //       (x + width) (y + height) l
                        //        x (y + height) l
                        //        h
                        case OperandNames.OP_re:
                            if (geometricPath == null) {
                                geometricPath = new GeneralPath();
                            }
                            float h = ((Number) stack.pop()).floatValue();
                            float w = ((Number) stack.pop()).floatValue();
                            y = ((Number) stack.pop()).floatValue();
                            x = ((Number) stack.pop()).floatValue();
                            geometricPath.moveTo(x, y);
                            geometricPath.lineTo(x + w, y);
                            geometricPath.lineTo(x + w, y + h);
                            geometricPath.lineTo(x, y + h);
                            geometricPath.lineTo(x, y);
                            break;

                        // Modify the current transformation matrix (CTM) by concatenating the
                        // specified matrix
                        case OperandNames.OP_cm:
                            consume_cm(graphicState, stack, inTextBlock, textBlockBase);
                            break;

                        // Close the current sub path by appending a straight line segment
                        // from the current point to the starting point of the sub path.
                        // This operator terminates the current sub path; appending
                        // another segment to the current path will begin a new subpath,
                        // even if the new segment begins at the endpoint reached by the
                        // h operation. If the current subpath is already closed,
                        // h does nothing.
                        case OperandNames.OP_h:
                            if (geometricPath != null) {
                                geometricPath.closePath();
                            }
                            break;
                        // Begin a marked-content sequence with an associated property
                        // list, terminated by a balancing EMC operator. tag is a name
                        // object indicating the role or significance of the sequence;
                        // properties is either an inline dictionary containing the
                        // property list or a name object associated with it in the
                        // Properties sub dictionary of the current resource dictionary
                        case OperandNames.OP_BDC:
                            stack.pop(); // properties
                            stack.pop(); // name
                            break;

                        // End a marked-content sequence begun by a BMC or BDC operator.
                        case OperandNames.OP_EMC:
                            break;

                        /**
                         * External Object (XObject) a graphics object whose contents
                         * are defined by a self-contained content stream, separate
                         * from the content stream in which it is used. There are three
                         * types of external object:
                         *
                         *   - An image XObject (Section 4.8.4, "Image Dictionaries")
                         *     represents a sampled visual image such as a photograph.
                         *   - A form XObject (Section 4.9, "Form XObjects") is a
                         *     self-contained description of an arbitrary sequence of
                         *     graphics objects.
                         *   - A PostScript XObject (Section 4.7.1, "PostScript XObjects")
                         *     contains a fragment of code expressed in the PostScript
                         *     page description language. PostScript XObjects are no
                         *     longer recommended to be used. (NOT SUPPORTED)
                         */
                        // Paint the specified XObject. The operand name must appear as
                        // a key in the XObject subdictionary of the current resource
                        // dictionary (see Section 3.7.2, "Resource Dictionaries"); the
                        // associated value must be a stream whose Type entry, if
                        // present, is XObject. The effect of Do depends on the value of
                        // the XObject's Subtype entry, which may be Image , Form, or PS
                        case OperandNames.OP_Do:
                            graphicState = consume_Do(graphicState, stack, shapes, resources, true);
                            break;

                        // Fill the path, using the even-odd rule to determine the
                        // region to fill
                        case OperandNames.OP_f_STAR:
                            if (geometricPath != null) {
                                // need to apply pattern..
                                geometricPath.setWindingRule(GeneralPath.WIND_EVEN_ODD);
                                commonFill(shapes, geometricPath);
                            }
                            geometricPath = null;
                            break;

                        // Sets the specified parameters in the graphics state.  The gs operand
                        // points to a name resource which should be a an ExtGState object.
                        // The graphics state parameters in the ExtGState must be concatenated
                        // with the the current graphics state.
                        case OperandNames.OP_gs:
                            consume_gs(graphicState, stack, resources);
                            break;

                        // End the path object without filling or stroking it. This
                        // operator is a "path-painting no-op," used primarily for the
                        // side effect of changing the current clipping path
                        case OperandNames.OP_n:
                            // clipping path outlines are visible when this is set to null;
                            geometricPath = null;
                            break;

                        // Set the line width in the graphics state
                        case OperandNames.OP_w:
                            consume_w(graphicState, stack, shapes);
                            break;
                        case OperandNames.OP_LW:
                            consume_w(graphicState, stack, shapes);
                            break;

                        // Modify the current clipping path by intersecting it with the
                        // current path, using the nonzero winding number rule to
                        // determine which regions lie inside the clipping path.
                        case OperandNames.OP_W:
                            if (geometricPath != null) {
                                geometricPath.setWindingRule(GeneralPath.WIND_NON_ZERO);
                                geometricPath.closePath();
                                graphicState.setClip(geometricPath);
                            }
                            break;

                        // Fill Color with ColorSpace
                        case OperandNames.OP_sc:
                            consume_sc(graphicState, stack, library, resources);
                            break;
                        case OperandNames.OP_scn:
                            consume_sc(graphicState, stack, library, resources);
                            break;

                        // Close, fill, and then stroke the path, using the nonzero
                        // winding number rule to determine the region to fill. This
                        // operator has the same effect as the sequence h B. See also
                        // "Special Path-Painting Considerations"
                        case OperandNames.OP_b:
                            if (geometricPath != null) {
                                geometricPath.setWindingRule(GeneralPath.WIND_NON_ZERO);
                                geometricPath.closePath();
                                commonFill(shapes, geometricPath);
                                commonStroke(graphicState, shapes, geometricPath);
                            }
                            geometricPath = null;
                            break;

                        // Same as K, but for non-stroking operations.
                        case OperandNames.OP_k:
                            consume_k(graphicState, stack, library);
                            break;

                        // Same as g but for none stroking operations
                        case OperandNames.OP_g:
                            consume_g(graphicState, stack, library);
                            break;

                        // Sets the flatness tolerance in the graphics state, NOT SUPPORTED
                        // flatness is a number in the range 0 to 100, a value of 0 specifies
                        // the default tolerance
                        case OperandNames.OP_i:
                            consume_i(stack);
                            break;

                        // Miter Limit
                        case OperandNames.OP_M:
                            consume_M(graphicState, stack, shapes);
                            break;

                        // Set the line cap style of the graphic state, related to Line Join
                        // style
                        case OperandNames.OP_J:
                            consume_J(graphicState, stack, shapes);
                            break;

                        // Same as RG, but for non-stroking operations.
                        case OperandNames.OP_rg:
                            consume_rg(graphicState, stack, library);
                            break;

                        // Sets the line dash pattern in the graphics state. A normal line
                        // is [] 0.  See Graphics State -> Line dash patter for more information
                        // in the PDF Reference.  Java 2d uses the same notation so there
                        // is not much work to be done other then parsing the data.
                        case OperandNames.OP_d:
                            consume_d(graphicState, stack, shapes);
                            break;

                        // Append a cubic Bezier curve to the current path. The curve
                        // extends from the current point to the point (x3, y3), using
                        // the current point and (x2, y2) as the Bezier control points.
                        // The new current point is (x3, y3).
                        case OperandNames.OP_v:
                            y3 = ((Number) stack.pop()).floatValue();
                            x3 = ((Number) stack.pop()).floatValue();
                            y2 = ((Number) stack.pop()).floatValue();
                            x2 = ((Number) stack.pop()).floatValue();
                            geometricPath.curveTo(
                                    (float) geometricPath.getCurrentPoint().getX(),
                                    (float) geometricPath.getCurrentPoint().getY(),
                                    x2,
                                    y2,
                                    x3,
                                    y3);
                            break;

                        // Set the line join style in the graphics state
                        case OperandNames.OP_j:
                            consume_j(graphicState, stack, shapes);
                            break;

                        // Append a cubic Bezier curve to the current path. The curve
                        // extends from the current point to the point (x3, y3), using
                        // (x1, y1) and (x3, y3) as the Bezier control points.
                        // The new current point is (x3, y3).
                        case OperandNames.OP_y:
                            y3 = ((Number) stack.pop()).floatValue();
                            x3 = ((Number) stack.pop()).floatValue();
                            y1 = ((Number) stack.pop()).floatValue();
                            x1 = ((Number) stack.pop()).floatValue();
                            geometricPath.curveTo(x1, y1, x3, y3, x3, y3);
                            break;

                        // Same as CS, but for nonstroking operations.
                        case OperandNames.OP_cs:
                            consume_cs(graphicState, stack, resources);
                            break;

                        // Color rendering intent in the graphics state
                        case OperandNames.OP_ri:
                            stack.pop();
                            break;

                        // Set the color to use for stroking operations in a device, CIE-based
                        // (other than ICCBased), or Indexed color space. The number of operands
                        // required and their interpretation depends on the current stroking color space:
                        //   - For DeviceGray, CalGray, and Indexed color spaces, one operand
                        //     is required (n = 1).
                        //   - For DeviceRGB, CalRGB, and Lab color spaces, three operands are
                        //     required (n = 3).
                        //   - For DeviceCMYK, four operands are required (n = 4).
                        case OperandNames.OP_SC:
                            consume_SC(graphicState, stack, library, resources);
                            break;
                        case OperandNames.OP_SCN:
                            consume_SC(graphicState, stack, library, resources);
                            break;

                        // Fill and then stroke the path, using the nonzero winding
                        // number rule to determine the region to fill. This produces
                        // the same result as constructing two identical path objects,
                        // painting the first with f and the second with S. Note,
                        // however, that the filling and stroking portions of the
                        // operation consult different values of several graphics state
                        // parameters, such as the current color.
                        case OperandNames.OP_B:
                            if (geometricPath != null) {
                                geometricPath.setWindingRule(GeneralPath.WIND_NON_ZERO);
                                commonFill(shapes, geometricPath);
                                commonStroke(graphicState, shapes, geometricPath);
                            }
                            geometricPath = null;
                            break;

                        // Set the stroking color space to DeviceCMYK (or the DefaultCMYK color
                        // space; see "Default Color Spaces" on page 227) and set the color to
                        // use for stroking operations. Each operand must be a number between
                        // 0.0 (zero concentration) and 1.0 (maximum concentration). The
                        // behavior of this operator is affected by the overprint mode
                        // (see Section 4.5.6, "Overprint Control").
                        case OperandNames.OP_K:
                            consume_K(graphicState, stack, library);
                            break;

                        /**
                         * Type3 operators, update the text state with data from these operands
                         */
                        case OperandNames.OP_d0:
                            // save the stack
                            graphicState = graphicState.save();
                            // need two pops to get  Wx and Wy data
                            y = ((Number) stack.pop()).floatValue();
                            x = ((Number) stack.pop()).floatValue();
                            TextState textState = graphicState.getTextState();
                            textState.setType3HorizontalDisplacement(new Point.Float(x, y));
                            break;

                        // Close and stroke the path. This operator has the same effect
                        // as the sequence h S.
                        case OperandNames.OP_s:
                            if (geometricPath != null) {
                                geometricPath.closePath();
                                commonStroke(graphicState, shapes, geometricPath);
                                geometricPath = null;
                            }
                            break;

                        // Set the stroking color space to DeviceGray (or the DefaultGray color
                        // space; see "Default Color Spaces" ) and set the gray level to use for
                        // stroking operations. gray is a number between 0.0 (black)
                        // and 1.0 (white).
                        case OperandNames.OP_G:
                            consume_G(graphicState, stack, library);
                            break;

                        // Close, fill, and then stroke the path, using the even-odd
                        // rule to determine the region to fill. This operator has the
                        // same effect as the sequence h B*. See also "Special
                        // Path-Painting Considerations"
                        case OperandNames.OP_b_STAR:
                            if (geometricPath != null) {
                                geometricPath.setWindingRule(GeneralPath.WIND_EVEN_ODD);
                                geometricPath.closePath();
                                commonStroke(graphicState, shapes, geometricPath);
                                commonFill(shapes, geometricPath);
                            }
                            geometricPath = null;
                            break;

                        // Set the stroking color space to DeviceRGB (or the DefaultRGB color
                        // space; see "Default Color Spaces" on page 227) and set the color to
                        // use for stroking operations. Each operand must be a number between
                        // 0.0 (minimum intensity) and 1.0 (maximum intensity).
                        case OperandNames.OP_RG:
                            consume_RG(graphicState, stack, library);
                            break;

                        // Set the current color space to use for stroking operations. The
                        // operand name must be a name object. If the color space is one that
                        // can be specified by a name and no additional parameters (DeviceGray,
                        // DeviceRGB, DeviceCMYK, and certain cases of Pattern), the name may be
                        // specified directly. Otherwise, it must be a name defined in the
                        // ColorSpace sub dictionary of the current resource dictionary; the
                        // associated value is an array describing the color space.
                        // <b>Note:</b>
                        // The names DeviceGray, DeviceRGB, DeviceCMYK, and Pattern always
                        // identify the corresponding color spaces directly; they never refer to
                        // resources in the ColorSpace sub dictionary. The CS operator also sets
                        // the current stroking color to its initial value, which depends on the
                        // color space:
                        // <li>In a DeviceGray, DeviceRGB, CalGray, or CalRGB color space, the
                        //     initial color has all components equal to 0.0.</li>
                        // <li>In a DeviceCMYK color space, the initial color is
                        //     [0.0 0.0 0.0 1.0].   </li>
                        // <li>In a Lab or ICCBased color space, the initial color has all
                        //     components equal to 0.0 unless that falls outside the intervals
                        //     specified by the space's Range entry, in which case the nearest
                        //     valid value is substituted.</li>
                        // <li>In an Indexed color space, the initial color value is 0. </li>
                        // <li>In a Separation or DeviceN color space, the initial tint value is
                        //     1.0 for all colorants. </li>
                        // <li>In a Pattern color space, the initial color is a pattern object
                        //     that causes nothing to be painted. </li>
                        case OperandNames.OP_CS:
                            consume_CS(graphicState, stack, resources);
                            break;
                        case OperandNames.OP_d1:
                            // save the stack
                            graphicState = graphicState.save();
                            // need two pops to get  Wx and Wy data
                            x2 = ((Number) stack.pop()).floatValue();
                            y2 = ((Number) stack.pop()).floatValue();
                            x1 = ((Number) stack.pop()).floatValue();
                            y1 = ((Number) stack.pop()).floatValue();
                            y = ((Number) stack.pop()).floatValue();
                            x = ((Number) stack.pop()).floatValue();
                            textState = graphicState.getTextState();
                            textState.setType3HorizontalDisplacement(
                                    new Point2D.Float(x, y));
                            textState.setType3BBox(new PRectangle(
                                    new Point2D.Float(x1, y1),
                                    new Point2D.Float(x2, y2)));
                            break;

                        // Fill and then stroke the path, using the even-odd rule to
                        // determine the region to fill. This operator produces the same
                        // result as B, except that the path is filled as if with f*
                        // instead of f. See also "Special Path-Painting Considerations"
                        case OperandNames.OP_B_STAR:
                            if (geometricPath != null) {
                                geometricPath.setWindingRule(GeneralPath.WIND_EVEN_ODD);
                                commonStroke(graphicState, shapes, geometricPath);
                                commonFill(shapes, geometricPath);
                            }
                            geometricPath = null;
                            break;

                        // Begin a marked-content sequence terminated by a balancing EMC
                        // operator.tag is a name object indicating the role or
                        // significance of the sequence.
                        case OperandNames.OP_BMC:
                            stack.pop();
                            break;

                        // Begin an inline image object
                        case OperandNames.OP_BI:
                            // start parsing image object, which leads to ID and EI
                            // tokends.
                            //    ID - Begin in the image data for an inline image object
                            //    EI - End an inline image object
                            parseInlineImage(lexer, shapes);
                            break;

                        // Begin a compatibility section. Unrecognized operators
                        // (along with their operands) will be ignored without error
                        // until the balancing EX operator is encountered.
                        case OperandNames.OP_BX:
                            break;
//                        }
                        // End a compatibility section begun by a balancing BX operator.
                        case OperandNames.OP_EX:
                            break;

                        // Modify the current clipping path by intersecting it with the
                        // current path, using the even-odd rule to determine which
                        // regions lie inside the clipping path.
                        case OperandNames.OP_W_STAR:
                            if (geometricPath != null) {
                                geometricPath.setWindingRule(GeneralPath.WIND_EVEN_ODD);
                                geometricPath.closePath();
                                graphicState.setClip(geometricPath);
                            }
                            break;

                        /**
                         * Single marked-content point
                         */
                        // Designate a marked-content point with an associated property
                        // list. tag is a name object indicating the role or significance
                        // of the point; properties is either an in line dictionary
                        // containing the property list or a name object associated with
                        // it in the Properties sub dictionary of the current resource
                        // dictionary.
                        case OperandNames.OP_DP:
                            stack.pop(); // properties
                            stack.pop(); // name
                            break;
                        // Designate a marked-content point. tag is a name object
                        // indicating the role or significance of the point.
                        case OperandNames.OP_MP:
                            stack.pop();
                            break;

                        // shading operator.
                        case OperandNames.OP_sh:
                            Object o = stack.peek();
                            // if a name then we are dealing with a pattern.
                            if (o instanceof Name) {
                                Name patternName = (Name) stack.pop();
                                Pattern pattern = resources.getShading(patternName);
                                if (pattern != null) {
                                    pattern.init();
                                    // we paint the shape and color shading as defined
                                    // by the pattern dictionary and respect the current clip

                                    // apply a rudimentary softmask for an shading .
                                    if (graphicState.getSoftMask() != null) {
                                        setAlpha(shapes,
                                                graphicState.getAlphaRule(),
                                                0.50f);
                                    } else {
                                        setAlpha(shapes,
                                                graphicState.getAlphaRule(),
                                                graphicState.getFillAlpha());
                                    }
                                    shapes.add(new PaintDrawCmd(pattern.getPaint()));
                                    shapes.add(new ShapeDrawCmd(graphicState.getClip()));
                                    shapes.add(new FillDrawCmd());
                                }
                            }
                            break;

                        /**
                         * We've seen a couple cases when the text state parameters are written
                         * outside of text blocks, this should cover these cases.
                         */
                        // Character Spacing
                        case OperandNames.OP_Tc:
                            consume_Tc(graphicState, stack);
                            break;
                        // Word spacing
                        case OperandNames.OP_Tw:
                            consume_Tw(graphicState, stack);
                            break;
                        // Text leading
                        case OperandNames.OP_TL:
                            consume_TL(graphicState, stack);
                            break;
                        // Rendering mode
                        case OperandNames.OP_Tr:
                            consume_Tr(graphicState, stack);
                            break;
                        // Horizontal scaling
                        case OperandNames.OP_Tz:
                            consume_Tz(graphicState, stack);
                            break;
                        case OperandNames.OP_Ts:
                            consume_Ts(graphicState, stack);
                            break;
                    }
                }
            }
        } catch (IOException e) {
            // eat the result as it a normal occurrence
            logger.finer("End of Content Stream");
        } catch (Throwable e) {
            logger.log(Level.WARNING, "Error parsing content stream. ", e);
            e.printStackTrace();
        } finally {
            // End of stream set alpha state back to 1.0f, so that other
            // streams aren't applied an incorrect alpha value.
            setAlpha(shapes, AlphaComposite.SRC_OVER, 1.0f);
        }

        return this;
    }

    /**
     * Specialized method for extracting text from documents.
     *
     * @param source content stream source.
     * @return vector where each entry is the text extracted from a text block.
     */
    public Shapes parseTextBlocks(byte[][] source) throws UnsupportedEncodingException {

        // great a parser to get tokens for stream
        Lexer parser = new Lexer();
        parser.contentStream(source);
        Shapes shapes = new Shapes();

        if (graphicState == null) {
            graphicState = new GraphicsState(shapes);
        }

        try {
            // loop through each token returned form the parser
            Object tok = parser.nextToken();
            Stack<Object> stack = new Stack<Object>();
            double yBTstart = 0;
            while (tok != null) {

                // add any names and numbers and every thing else on the
                // stack for future reference
                if (tok instanceof String) {

                    if (tok.equals(PdfOps.BT_TOKEN)) {
                        // start parseText, which parses until ET is reached
                        yBTstart = parseText(parser, shapes, yBTstart);
                        // free up some memory along the way. we don't need
                        // a full stack consume Tf tokens.
                        stack.clear();
                    }
                    // for malformed core docs we need to consume any font
                    // to ensure we can result toUnicode values.
                    else if (tok.equals(PdfOps.Tf_TOKEN)) {
                        consume_Tf(graphicState, stack, resources);
                        stack.clear();
                    }
                    // pick up on xObject content streams.
                    else if (tok.equals(PdfOps.Do_TOKEN)) {
                        consume_Do(graphicState, stack, shapes, resources, false);
                        stack.clear();
                    }
                } else {
                    stack.push(tok);
                }
                tok = parser.nextToken();
            }
            // clear our temporary stack.
            stack.clear();
        } catch (IOException e) {
            // eat the result as it a normal occurrence
            logger.finer("End of Content Stream");
        }
        shapes.contract();
        return shapes;
    }

    /**
     * Parses Text found with in a BT block.
     *
     * @param lexer           parser containing BT tokens
     * @param shapes          container of all shapes for the page content being parsed
     * @param previousBTStart y offset of previous BT definition.
     * @return y offset of the this BT definition.
     * @throws java.io.IOException end of content stream is found
     */
    private float parseText(Lexer lexer, Shapes shapes, double previousBTStart)
            throws IOException {
        Object nextToken;
        inTextBlock = true;
        float shift = 0;
        // keeps track of previous text placement so that Compatibility and
        // implementation note 57 is respected.  That is text drawn after a TJ
        // must not be less then the previous glyphs coords.
        float previousAdvance = 0;
        Point2D.Float advance = new Point2D.Float(0, 0);
        textBlockBase = new AffineTransform(graphicState.getCTM());

        // transformation matrix used to cMap core space to drawing space
        graphicState.getTextState().tmatrix = new AffineTransform();
        graphicState.getTextState().tlmatrix = new AffineTransform();
        graphicState.scale(1, -1);

        // get reference to PageText.
        PageText pageText = shapes.getPageText();
        // previous Td, TD or Tm y coordinate value for text extraction
        boolean isYstart = true;
        float yBTStart = 0;

        // glyphOutline to support text clipping modes, life span is BT->ET.
        GlyphOutlineClip glyphOutlineClip = new GlyphOutlineClip();

        // start parsing of the BT block
        nextToken = lexer.nextToken();
        int operand;
        while (!(nextToken instanceof Integer && (Integer) nextToken == OperandNames.OP_ET)) {

            if (nextToken instanceof Integer) {
                operand = (Integer) nextToken;
                switch (operand) {
                    // Normal text token, string, hex
                    case OperandNames.OP_Tj:
                        Object tjValue = stack.pop();
                        StringObject stringObject;
                        TextState textState;
                        if (tjValue instanceof StringObject) {
                            stringObject = (StringObject) tjValue;
                            textState = graphicState.getTextState();
                            // apply scaling
                            AffineTransform tmp = applyTextScaling(graphicState);
                            // apply transparency
                            setAlpha(shapes, graphicState.getAlphaRule(), graphicState.getFillAlpha());
                            // draw string will take care of text pageText construction
                            Point2D.Float d = (Point2D.Float) drawString(
                                    stringObject.getLiteralStringBuffer(
                                            textState.font.getSubTypeFormat(),
                                            textState.font.getFont()),
                                    advance,
                                    previousAdvance,
                                    graphicState.getTextState(),
                                    shapes,
                                    glyphOutlineClip);
                            graphicState.set(tmp);
                            graphicState.translate(d.x, 0);
                            shift += d.x;
                            previousAdvance = 0;
                            advance.setLocation(0, 0);
                        }
                        break;

                    // Character Spacing
                    case OperandNames.OP_Tc:
                        graphicState.getTextState().cspace = ((Number) stack.pop()).floatValue();
                        break;

                    // Word spacing
                    case OperandNames.OP_Tw:
                        graphicState.getTextState().wspace = ((Number) stack.pop()).floatValue();
                        break;


                    // move to the start of he next line, offset from the start of the
                    // current line by (tx,ty)*tx
                    case OperandNames.OP_Td:
                        float y = ((Number) stack.pop()).floatValue();
                        float x = ((Number) stack.pop()).floatValue();
                        double oldY = graphicState.getCTM().getTranslateY();
                        graphicState.translate(-shift, 0);
                        shift = 0;
                        previousAdvance = 0;
                        advance.setLocation(0, 0);
                        // x,y are expressed in unscaled but we don't scale until
                        // a text showing operator is called.
                        graphicState.translate(x, -y);
                        float newY = (float) graphicState.getCTM().getTranslateY();
                        // capture x coord of BT y offset, tm, Td, TD.
                        if (isYstart) {
                            yBTStart = newY;
                            isYstart = false;
                            if (previousBTStart != yBTStart) {
                                pageText.newLine();
                            }
                        }

                        // ty will dictate the vertical shift, many pdf will use
                        // ty=0 do just do a horizontal shift for layout.
                        if (y != 0 && Math.round(newY) != Math.round(oldY)) {
                            pageText.newLine();
                        }
                        break;

                    /**
                     * Tranformation matrix
                     * tm =   |f1 f2 0|
                     *        |f3 f4 0|
                     *        |f5 f6 0|
                     */
                    case OperandNames.OP_Tm:
                        shift = 0;
                        previousAdvance = 0;
                        advance.setLocation(0, 0);
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

                        // grab old values.
                        double oldTransY = graphicState.getCTM().getTranslateY();
                        double oldScaleY = graphicState.getCTM().getScaleY();

                        // apply the transform
                        graphicState.getTextState().tmatrix = new AffineTransform(tm);
                        af.concatenate(graphicState.getTextState().tmatrix);
                        graphicState.set(af);
                        graphicState.scale(1, -1);

                        // text extraction logic
                        // capture x coord of BT y offset, tm, Td, TD.
                        if (isYstart) {
                            yBTStart = tm[5];//f6;
                            isYstart = false;
                            if (previousBTStart != yBTStart) {
                                pageText.newLine();
                            }
                        }
                        double newTransY = graphicState.getCTM().getTranslateY();
                        double newScaleY = graphicState.getCTM().getScaleY();
                        // f5 and f6 will dictate a horizontal or vertical shift
                        // this information could be used to detect new lines

                        if (Math.round(oldTransY) != Math.round(newTransY)) {
                            pageText.newLine();
                        } else if (Math.abs(oldScaleY) != Math.abs(newScaleY)) {
                            pageText.newLine();
                        }

                        break;

                    // Font selection
                    case OperandNames.OP_Tf:
                        consume_Tf(graphicState, stack, resources);
                        break;

                    // TJ marks a vector, where.......
                    case OperandNames.OP_TJ:
                        // apply scaling
                        AffineTransform tmp = applyTextScaling(graphicState);
                        // apply transparency
                        setAlpha(shapes, graphicState.getAlphaRule(), graphicState.getFillAlpha());
                        List v = (List) stack.pop();
                        Number f;
                        for (Object currentObject : v) {
                            if (currentObject instanceof StringObject) {
                                stringObject = (StringObject) currentObject;
                                textState = graphicState.getTextState();
                                // draw string takes care of PageText extraction
                                advance = (Point2D.Float) drawString(
                                        stringObject.getLiteralStringBuffer(
                                                textState.font.getSubTypeFormat(),
                                                textState.font.getFont()),
                                        advance, previousAdvance,
                                        graphicState.getTextState(), shapes, glyphOutlineClip);
                            } else if (currentObject instanceof Number) {
                                f = (Number) currentObject;
                                advance.x -=
                                        f.floatValue() * graphicState.getTextState().currentfont.getSize()
                                                / 1000.0;
                            }
                            previousAdvance = advance.x;
                        }
                        graphicState.set(tmp);
                        break;

                    // Move to the start of the next line, offset from the start of the
                    // current line by (tx,ty)
                    case OperandNames.OP_TD:
                        y = ((Number) stack.pop()).floatValue();
                        x = ((Number) stack.pop()).floatValue();
                        graphicState.translate(-shift, 0);
                        shift = 0;
                        previousAdvance = 0;
                        advance.setLocation(0, 0);
                        graphicState.translate(x, -y);
                        graphicState.getTextState().leading = -y;

                        // capture x coord of BT y offset, tm, Td, TD.
                        if (isYstart) {
                            yBTStart = y;
                            isYstart = false;
                        }
                        // ty will dictate the vertical shift, many pdf will use
                        // ty=0 do just do a horizontal shift for layout.
                        if (y != 0f) {
                            pageText.newLine();
                        }
                        break;

                    // Text leading
                    case OperandNames.OP_TL:
                        graphicState.getTextState().leading = ((Number) stack.pop()).floatValue();
                        break;

                    // Saves Graphics State, should copy the entire  graphics state onto
                    // the graphicsState object's stack
                    case OperandNames.OP_q:
                        graphicState = consume_q(graphicState);
                        break;
                    // Restore Graphics State, should restore the entire graphics state
                    // to its former value by popping it from the stack
                    case OperandNames.OP_Q:
                        graphicState = consume_Q(graphicState, shapes);
                        break;

                    // Modify the current transformation matrix (CTM) by concatenating the
                    // specified matrix
                    case OperandNames.OP_cm:
                        consume_cm(graphicState, stack, inTextBlock, textBlockBase);
                        break;

                    // Move to the start of the next line
                    case OperandNames.OP_T_STAR:
                        graphicState.translate(-shift, 0);
                        shift = 0;
                        previousAdvance = 0;
                        advance.setLocation(0, 0);
                        graphicState.translate(0, graphicState.getTextState().leading);
                        // always indicates a new line
                        pageText.newLine();
                        break;
                    case OperandNames.OP_BDC:
                        stack.pop();
                        stack.pop();
                        break;
                    case OperandNames.OP_EMC:
                        break;

                    // Sets the specified parameters in the graphics state.  The gs operand
                    // points to a name resource which should be a an ExtGState object.
                    // The graphics state parameters in the ExtGState must be concatenated
                    // with the the current graphics state.
                    case OperandNames.OP_gs:
                        consume_gs(graphicState, stack, resources);
                        break;

                    // Set the line width in the graphics state
                    case OperandNames.OP_w:
                        consume_w(graphicState, stack, shapes);
                        break;
                    case OperandNames.OP_LW:
                        consume_w(graphicState, stack, shapes);
                        break;


                    // Fill Color with ColorSpace
                    case OperandNames.OP_sc:
                        consume_sc(graphicState, stack, library, resources);
                        break;
                    case OperandNames.OP_scn:
                        consume_sc(graphicState, stack, library, resources);
                        break;

                    // Same as K, but for nonstroking operations.
                    case OperandNames.OP_k:
                        consume_k(graphicState, stack, library);
                        break;

                    // Same as g but for none stroking operations
                    case OperandNames.OP_g:
                        consume_g(graphicState, stack, library);
                        break;

                    // Sets the flatness tolerance in the graphics state, NOT SUPPORTED
                    // flatness is a number in the range 0 to 100, a value of 0 specifies
                    // the default tolerance
                    case OperandNames.OP_i:
                        consume_i(stack);
                        break;

                    // Miter Limit
                    case OperandNames.OP_M:
                        consume_M(graphicState, stack, shapes);
                        break;

                    // Set the line cap style of the graphic state, related to Line Join
                    // style
                    case OperandNames.OP_J:
                        consume_J(graphicState, stack, shapes);
                        break;

                    // Same as RG, but for nonstroking operations.
                    case OperandNames.OP_rg:
                        consume_rg(graphicState, stack, library);
                        break;

                    // Sets the line dash pattern in the graphics state. A normal line
                    // is [] 0.  See Graphics State -> Line dash patter for more information
                    // in the PDF Reference.  Java 2d uses the same notation so there
                    // is not much work to be done other then parsing the data.
                    case OperandNames.OP_d:
                        consume_d(graphicState, stack, shapes);
                        break;

                    // Set the line join style in the graphics state
                    case OperandNames.OP_j:
                        consume_j(graphicState, stack, shapes);
                        break;

                    // Same as CS, but for non-stroking operations.
                    case OperandNames.OP_cs:
                        consume_cs(graphicState, stack, resources);
                        break;

                    // Set the color rendering intent in the graphics state
                    case OperandNames.OP_ri:
                        stack.pop();
                        break;

                    // Set the color to use for stroking operations in a device, CIE-based
                    // (other than ICCBased), or Indexed color space. The number of operands
                    // required and their interpretation depends on the current stroking color space:
                    //   - For DeviceGray, CalGray, and Indexed color spaces, one operand
                    //     is required (n = 1).
                    //   - For DeviceRGB, CalRGB, and Lab color spaces, three operands are
                    //     required (n = 3).
                    //   - For DeviceCMYK, four operands are required (n = 4).
                    case OperandNames.OP_SC:
                        consume_SC(graphicState, stack, library, resources);
                        break;
                    case OperandNames.OP_SCN:
                        consume_SC(graphicState, stack, library, resources);
                        break;

                    // Set the stroking color space to DeviceCMYK (or the DefaultCMYK color
                    // space; see "Default Color Spaces" on page 227) and set the color to
                    // use for stroking operations. Each operand must be a number between
                    // 0.0 (zero concentration) and 1.0 (maximum concentration). The
                    // behavior of this operator is affected by the overprint mode
                    // (see Section 4.5.6, "Overprint Control").
                    case OperandNames.OP_K:
                        consume_K(graphicState, stack, library);
                        break;

                    // Set the stroking color space to DeviceGray (or the DefaultGray color
                    // space; see "Default Color Spaces" ) and set the gray level to use for
                    // stroking operations. gray is a number between 0.0 (black)
                    // and 1.0 (white).
                    case OperandNames.OP_G:
                        consume_G(graphicState, stack, library);
                        break;

                    // Set the stroking color space to DeviceRGB (or the DefaultRGB color
                    // space; see "Default Color Spaces" on page 227) and set the color to
                    // use for stroking operations. Each operand must be a number between
                    // 0.0 (minimum intensity) and 1.0 (maximum intensity).
                    case OperandNames.OP_RG:
                        consume_RG(graphicState, stack, library);
                        break;
                    case OperandNames.OP_CS:
                        consume_CS(graphicState, stack, resources);
                        break;

                    // Rendering mode
                    case OperandNames.OP_Tr:
                        graphicState.getTextState().rmode = (int) ((Number) stack.pop()).floatValue();
                        break;

                    // Horizontal scaling
                    case OperandNames.OP_Tz:
                        consume_Tz(graphicState, stack);
                        break;

                    // Text rise
                    case OperandNames.OP_Ts:
                        graphicState.getTextState().trise = ((Number) stack.pop()).floatValue();
                        break;

                    /**
                     * Begin a compatibility section. Unrecognized operators (along with
                     * their operands) will be ignored without error until the balancing
                     * EX operator is encountered.
                     */
                    case OperandNames.OP_BX:
                        break;
                    // End a compatibility section begun by a balancing BX operator.
                    case OperandNames.OP_EX:
                        break;

                    // Move to the next line and show a text string.
                    case OperandNames.OP_SINGLE_QUOTE:
                        graphicState.translate(-shift, graphicState.getTextState().leading);

                        // apply transparency
                        setAlpha(shapes, graphicState.getAlphaRule(), graphicState.getFillAlpha());

                        shift = 0;
                        previousAdvance = 0;
                        advance.setLocation(0, 0);
                        stringObject = (StringObject) stack.pop();

                        textState = graphicState.getTextState();
                        // apply scaling
                        tmp = applyTextScaling(graphicState);
                        // draw the text.
                        Point2D.Float d = (Point2D.Float) drawString(
                                stringObject.getLiteralStringBuffer(
                                        textState.font.getSubTypeFormat(),
                                        textState.font.getFont()),
                                new Point2D.Float(0, 0), 0, graphicState.getTextState(),
                                shapes, glyphOutlineClip);
                        graphicState.set(tmp);
                        graphicState.translate(d.x, 0);
                        shift += d.x;
                        break;
                    /**
                     * Move to the next line and show a text string, using aw as the
                     * word spacing and ac as the character spacing (setting the
                     * corresponding parameters in the text state). aw and ac are
                     * numbers expressed in unscaled text space units.
                     */
                    case OperandNames.OP_DOUBLE_QUOTE:
                        stringObject = (StringObject) stack.pop();
                        graphicState.getTextState().cspace = ((Number) stack.pop()).floatValue();
                        graphicState.getTextState().wspace = ((Number) stack.pop()).floatValue();
                        graphicState.translate(-shift, graphicState.getTextState().leading);

                        // apply transparency
                        setAlpha(shapes, graphicState.getAlphaRule(), graphicState.getFillAlpha());

                        shift = 0;
                        previousAdvance = 0;
                        advance.setLocation(0, 0);
                        textState = graphicState.getTextState();

                        tmp = applyTextScaling(graphicState);
                        d = (Point2D.Float) drawString(
                                stringObject.getLiteralStringBuffer(
                                        textState.font.getSubTypeFormat(),
                                        textState.font.getFont()),
                                new Point2D.Float(0, 0), 0, graphicState.getTextState(),
                                shapes, glyphOutlineClip);
                        graphicState.set(tmp);
                        graphicState.translate(d.x, 0);
                        shift += d.x;
                        break;
                }
            }
            // push everything else on the stack for consumptions
            else {
                stack.push(nextToken);
            }

            nextToken = lexer.nextToken();
            if (nextToken == null) {
                break;
            }
        }
        // during a BT -> ET text parse there is a change that we might be
        // in MODE_ADD or MODE_Fill_Add which require that the we push the
        // shapes that make up the clipping path to the shapes stack.  When
        // encountered the path will be used as the current clip.
        if (!glyphOutlineClip.isEmpty()) {
            // set the clips so further clips can use the clip outline
            graphicState.setClip(glyphOutlineClip.getGlyphOutlineClip());
            // add the glyphOutline so the clip can be calculated.
            shapes.add(new GlyphOutlineDrawCmd(glyphOutlineClip));
        }
        graphicState.set(textBlockBase);
        if (nextToken instanceof Integer && (Integer) nextToken == OperandNames.OP_ET) {
            inTextBlock = false;
        }

        return yBTStart;
    }

    private void parseInlineImage(Lexer p, Shapes shapes) throws IOException {
        try {
            Object tok;
            HashMap<Object, Object> iih = new HashMap<Object, Object>();
            tok = p.nextToken();
            while (!tok.equals(OperandNames.OP_ID)) {
                if (tok.equals(ImageStream.BPC_KEY)) {
                    tok = ImageStream.BITSPERCOMPONENT_KEY;
                } else if (tok.equals(ImageStream.CS_KEY)) {
                    tok = ImageStream.COLORSPACE_KEY;
                } else if (tok.equals(ImageStream.D_KEY)) {
                    tok = ImageStream.DECODE_KEY;
                } else if (tok.equals(ImageStream.DP_KEY)) {
                    tok = ImageStream.DECODEPARMS_KEY;
                } else if (tok.equals(ImageStream.F_KEY)) {
                    tok = ImageStream.FILTER_KEY;
                } else if (tok.equals(ImageStream.H_KEY)) {
                    tok = ImageStream.HEIGHT_KEY;
                } else if (tok.equals(ImageStream.IM_KEY)) {
                    tok = ImageStream.IMAGEMASK_KEY;
                } else if (tok.equals(ImageStream.I_KEY)) {
                    tok = ImageStream.INDEXED_KEY;
                } else if (tok.equals(ImageStream.W_KEY)) {
                    tok = ImageStream.WIDTH_KEY;
                }
                Object tok1 = p.nextToken();
                iih.put(tok, tok1);
                tok = p.nextToken();
            }
            // For inline images in content streams, we have to use
            //   a byte[], instead of going back to the original file,
            //   to re-get the image data, because the inline image is
            //   only a small part of a content stream, which is also
            //   filtered, and potentially concatenated with other
            //   content streams.
            // Long story short: it's too hard to re-get from PDF file
            // Now, since non-inline-image streams can go back to the
            //   file, we have to fake it as coming from the file ...
            byte[] data = p.getImageBytes();
            // create the image stream
            ImageStream st = new ImageStream(library, iih, data);
            ImageReference imageStreamReference =
                    new InlineImageStreamReference(st, graphicState.getFillColor(), resources);
            AffineTransform af = new AffineTransform(graphicState.getCTM());
            graphicState.scale(1, -1);
            graphicState.translate(0, -1);
            shapes.add(new ImageDrawCmd(imageStreamReference));
            graphicState.set(af);
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            logger.log(Level.FINE, "Error parsing inline image.", e);
        }
    }

    private static void consume_G(GraphicsState graphicState, Stack stack,
                                  Library library) {
        float gray = ((Number) stack.pop()).floatValue();
        // Stroke Color Gray
        graphicState.setStrokeColorSpace(
                PColorSpace.getColorSpace(library, DeviceGray.DEVICEGRAY_KEY));
        graphicState.setStrokeColor(new Color(gray, gray, gray));
    }

    private static void consume_g(GraphicsState graphicState, Stack stack,
                                  Library library) {
        float gray = ((Number) stack.pop()).floatValue();
        // Fill Color Gray
        graphicState.setFillColorSpace(
                PColorSpace.getColorSpace(library, DeviceGray.DEVICEGRAY_KEY));
        graphicState.setFillColor(new Color(gray, gray, gray));
    }

    private static void consume_RG(GraphicsState graphicState, Stack stack,
                                   Library library) {
        float b = ((Number) stack.pop()).floatValue();
        float gg = ((Number) stack.pop()).floatValue();
        float r = ((Number) stack.pop()).floatValue();
        b = Math.max(0.0f, Math.min(1.0f, b));
        gg = Math.max(0.0f, Math.min(1.0f, gg));
        r = Math.max(0.0f, Math.min(1.0f, r));
        // set stoke colour
        graphicState.setStrokeColorSpace(
                PColorSpace.getColorSpace(library, DeviceRGB.DEVICERGB_KEY));
        graphicState.setStrokeColor(new Color(r, gg, b));
    }

    private static void consume_rg(GraphicsState graphicState, Stack stack,
                                   Library library) {
        float b = ((Number) stack.pop()).floatValue();
        float gg = ((Number) stack.pop()).floatValue();
        float r = ((Number) stack.pop()).floatValue();
        b = Math.max(0.0f, Math.min(1.0f, b));
        gg = Math.max(0.0f, Math.min(1.0f, gg));
        r = Math.max(0.0f, Math.min(1.0f, r));
        // set fill colour
        graphicState.setFillColorSpace(
                PColorSpace.getColorSpace(library, DeviceRGB.DEVICERGB_KEY));
        graphicState.setFillColor(new Color(r, gg, b));
    }

    private static void consume_K(GraphicsState graphicState, Stack stack,
                                  Library library) {
        float k = ((Number) stack.pop()).floatValue();
        float y = ((Number) stack.pop()).floatValue();
        float m = ((Number) stack.pop()).floatValue();
        float c = ((Number) stack.pop()).floatValue();

        PColorSpace pColorSpace =
                PColorSpace.getColorSpace(library, DeviceCMYK.DEVICECMYK_KEY);
        // set stroke colour
        graphicState.setStrokeColorSpace(pColorSpace);
        graphicState.setStrokeColor(pColorSpace.getColor(PColorSpace.reverse(new float[]{c, m, y, k})));
    }

    private static void consume_k(GraphicsState graphicState, Stack stack,
                                  Library library) {
        float k = ((Number) stack.pop()).floatValue();
        float y = ((Number) stack.pop()).floatValue();
        float m = ((Number) stack.pop()).floatValue();
        float c = ((Number) stack.pop()).floatValue();
        // build a colour space.
        PColorSpace pColorSpace =
                PColorSpace.getColorSpace(library, DeviceCMYK.DEVICECMYK_KEY);
        // set fill colour
        graphicState.setFillColorSpace(pColorSpace);
        graphicState.setFillColor(pColorSpace.getColor(PColorSpace.reverse(new float[]{c, m, y, k})));
    }

    private static void consume_CS(GraphicsState graphicState, Stack stack, Resources resources) {
        Name n = (Name) stack.pop();
        // Fill Color ColorSpace, resources call uses factory call to PColorSpace.getColorSpace
        // which returns an colour space including a pattern
        graphicState.setStrokeColorSpace(resources.getColorSpace(n));
    }

    private static void consume_cs(GraphicsState graphicState, Stack stack, Resources resources) {
        Name n = (Name) stack.pop();
        // Fill Color ColorSpace, resources call uses factory call to PColorSpace.getColorSpace
        // which returns an colour space including a pattern
        graphicState.setFillColorSpace(resources.getColorSpace(n));
    }

    private static void consume_SC(GraphicsState graphicState, Stack stack,
                                   Library library, Resources resources) {
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
                    // peek and then pop until a none Float is found
                    int nCount = 0;
                    // next calculate the colour based ont he space and c1..Cn
                    float colour[] = new float[compLength];
                    // peek and pop all of the colour floats
                    while (!stack.isEmpty() && stack.peek() instanceof Number &&
                            nCount < compLength) {
                        colour[nCount] = ((Number) stack.pop()).floatValue();
                        nCount++;
                    }
                    graphicState.setStrokeColor(graphicState.getStrokeColorSpace().getColor(colour));
                    tilingPattern.setUnColored(
                            graphicState.getStrokeColorSpace().getColor(colour));
                }
            }
        } else if (o instanceof Number) {

            // some pdfs encoding do not explicitly change the default colour
            // space from the default DeviceGrey.  The following code checks
            // how many n values are available and if different then current
            // graphicState.strokeColorSpace it is changed as needed

            // first get assumed number of components
            int colorSpaceN = graphicState.getStrokeColorSpace().getNumComponents();

            // peek and then pop until a none Float is found
            int nCount = 0;
            // set colour to max of 4 which is cymk,
            int compLength = 4;
            float colour[] = new float[compLength];
            // peek and pop all of the colour floats
            while (!stack.isEmpty() && stack.peek() instanceof Number &&
                    nCount < compLength) {
                colour[nCount] = ((Number) stack.pop()).floatValue();
                nCount++;
            }

            // check to see if nCount and colorSpaceN are the same
            if (nCount != colorSpaceN) {
                // change the colour state to nCount equivalent
                graphicState.setStrokeColorSpace(
                        PColorSpace.getColorSpace(library, nCount));
            }
            // shrink the array to the correct length
            float[] f = new float[nCount];
            System.arraycopy(colour, 0, f, 0, nCount);
            graphicState.setStrokeColor(graphicState.getStrokeColorSpace().getColor(f));
        }
    }


    private static void consume_sc(GraphicsState graphicState, Stack stack,
                                   Library library, Resources resources) {
        Object o = stack.peek();
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
                    int nCount = 0;
                    // next calculate the colour based ont he space and c1..Cn
                    float colour[] = new float[compLength];
                    // peek and pop all of the colour floats
                    while (!stack.isEmpty() && stack.peek() instanceof Number &&
                            nCount < compLength) {
                        colour[nCount] = ((Number) stack.pop()).floatValue();
                        nCount++;
                    }
                    // fill colour to be used when painting. 
                    graphicState.setFillColor(graphicState.getFillColorSpace().getColor(colour));
                    tilingPattern.setUnColored(
                            graphicState.getFillColorSpace().getColor(colour));
                }
            }
        } else if (o instanceof Number) {
            // some PDFs encoding do not explicitly change the default colour
            // space from the default DeviceGrey.  The following code checks
            // how many n values are available and if different then current
            // graphicState.fillColorSpace it is changed as needed

            // first get assumed number of components
            int colorSpaceN = graphicState.getFillColorSpace().getNumComponents();

            // peek and then pop until a none Float is found
            int nCount = 0;
            // set colour to max of 4 which is cymk,
            int compLength = 4;
            float colour[] = new float[compLength];
            // peek and pop all of the colour floats
            while (!stack.isEmpty() && stack.peek() instanceof Number &&
                    nCount < compLength) {
                colour[nCount] = ((Number) stack.pop()).floatValue();
                nCount++;
            }

            // check to see if nCount and colorSpaceN are the same
            if (nCount != colorSpaceN) {
                // change the colour state to nCount equivalent
                graphicState.setFillColorSpace(
                        PColorSpace.getColorSpace(library, nCount));
            }
            // shrink the array to the correct length
            float[] f = new float[nCount];
            System.arraycopy(colour, 0, f, 0, nCount);
            graphicState.setFillColor(graphicState.getFillColorSpace().getColor(f));
        }
    }

    private static GraphicsState consume_q(GraphicsState graphicState) {
        return graphicState.save();
    }

    private GraphicsState consume_Q(GraphicsState graphicState, Shapes shapes) {
        GraphicsState gs1 = graphicState.restore();
        // point returned stack
        if (gs1 != null) {
            graphicState = gs1;
        }
        // otherwise start a new stack
        else {
            graphicState = new GraphicsState(shapes);
            graphicState.set(new AffineTransform());
            shapes.add(new NoClipDrawCmd());
        }

        return graphicState;
    }

    private static void consume_cm(GraphicsState graphicState, Stack stack,
                                   boolean inTextBlock, AffineTransform textBlockBase) {
        float f = ((Number) stack.pop()).floatValue();
        float e = ((Number) stack.pop()).floatValue();
        float d = ((Number) stack.pop()).floatValue();
        float c = ((Number) stack.pop()).floatValue();
        float b = ((Number) stack.pop()).floatValue();
        float a = ((Number) stack.pop()).floatValue();
        // get the current CTM
        AffineTransform af = new AffineTransform(graphicState.getCTM());
        // do the matrix concatenation math
        af.concatenate(new AffineTransform(a, b, c, d, e, f));
        // add the transformation to the graphics state
        graphicState.set(af);
        // update the clip, translate by this CM
        graphicState.updateClipCM(new AffineTransform(a, b, c, d, e, f));
        // apply the cm just as we would a tm
        if (inTextBlock) {
            // update the textBlockBase with the cm matrix
            af = new AffineTransform(textBlockBase);
            // apply the transform
            graphicState.getTextState().tmatrix = new AffineTransform(a, b, c, d, e, f);
            af.concatenate(graphicState.getTextState().tmatrix);
            graphicState.set(af);
            // update the textBlockBase as the tm was specified in the BT block
            // and we still need to keep the offset.
            textBlockBase.setTransform(new AffineTransform(graphicState.getCTM()));
        }
    }

    private static void consume_i(Stack stack) {
        stack.pop();
    }

    private static void consume_J(GraphicsState graphicState, Stack stack, Shapes shapes) {
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
     * @param viewParse    true indicates parsing is for a normal view.  If false
     *                     the consumption of Do will skip Image based xObjects for performance.
     */
    private static GraphicsState consume_Do(GraphicsState graphicState, Stack stack,
                                            Shapes shapes, Resources resources,
                                            boolean viewParse) {
        Name xobjectName = (Name) stack.pop();
        // Form XObject
        if (resources != null && resources.isForm(xobjectName)) {
            // Do operator steps:
            //  1.)save the graphics context
            graphicState = graphicState.save();
            // Try and find the named reference 'xobjectName', pass in a copy
            // of the current graphics state for the new content stream
            Form formXObject = resources.getForm(xobjectName);
            if (formXObject != null) {
                // init formXobject
                GraphicsState xformGraphicsState =
                        new GraphicsState(graphicState);
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
                formXObject.init();
                // 2.) concatenate matrix entry with the current CTM
                AffineTransform af =
                        new AffineTransform(graphicState.getCTM());
                af.concatenate(formXObject.getMatrix());
                shapes.add(new TransformDrawCmd(af));
                // 3.) Clip according to the form BBox entry
                if (graphicState.getClip() != null) {
                    AffineTransform matrix = formXObject.getMatrix();
                    Area bbox = new Area(formXObject.getBBox());
                    Area clip = graphicState.getClip();
                    // create inverse of matrix so we can transform
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
                shapes.add(new ClipDrawCmd());
                // 4.) Paint the graphics objects in font stream.
                setAlpha(shapes, graphicState.getAlphaRule(),
                        graphicState.getFillAlpha());
                // If we have a transparency group we paint it
                // slightly different then a regular xObject as we
                // need to capture the alpha which is only possible
                // by paint the xObject to an image.
                if (!disableTransparencyGroups &&
                        formXObject.isTransparencyGroup() &&
                        graphicState.getFillAlpha() < 1.0f &&
                        (formXObject.getBBox().getWidth() < Short.MAX_VALUE &&
                                formXObject.getBBox().getHeight() < Short.MAX_VALUE)) {
                    // add the hold form for further processing.
                    shapes.add(new FormDrawCmd(formXObject));
                }
                // the down side of painting to an image is that we
                // lose quality if there is a affine transform, so
                // if it isn't a group transparency we paint old way
                // by just adding the objects to the shapes stack.
                else {
                    shapes.add(new ShapesDrawCmd(formXObject.getShapes()));
                }
                // update text sprites with geometric path state
                if (formXObject.getShapes() != null &&
                        formXObject.getShapes().getPageText() != null) {
                    // normalize each sprite.
                    formXObject.getShapes().getPageText()
                            .applyXObjectTransform(graphicState.getCTM());
                    // add the text to the current shapes for extraction and
                    // selection purposes.
                    formXObject.getShapes().getPageText().getPageLines().addAll(
                            formXObject.getShapes().getPageText().getPageLines());
                }
                shapes.add(new NoClipDrawCmd());
            }
            //  5.) Restore the saved graphics state
            graphicState = graphicState.restore();
        }
        // Image XObject
        else if (viewParse) {
            setAlpha(shapes, graphicState.getAlphaRule(), graphicState.getFillAlpha());

            // create an ImageReference for future decoding
            ImageReference imageReference = ImageReferenceFactory.getImageReference(
                    resources.getImageStream(xobjectName), resources, graphicState.getFillColor());

            if (imageReference != null) {
                AffineTransform af =
                        new AffineTransform(graphicState.getCTM());
                graphicState.scale(1, -1);
                graphicState.translate(0, -1);
                // add the image
                shapes.add(new ImageDrawCmd(imageReference));
                graphicState.set(af);
            }
        }
        return graphicState;
    }

    private static void consume_d(GraphicsState graphicState, Stack stack, Shapes shapes) {
        float dashPhase;
        float[] dashArray;
        try {
            // pop dashPhase off the stack
            dashPhase = Math.abs(((Number) stack.pop()).floatValue());
            // pop the dashVector of the stack
            List dashVector = (List) stack.pop();
            // if the dash vector size is zero we have a default none dashed
            // line and thus we skip out
            if (dashVector.size() > 0) {
                // convert dash vector to a array of floats
                final int sz = dashVector.size();
                dashArray = new float[sz];
                for (int i = 0; i < sz; i++) {
                    dashArray[i] = Math.abs(((Number) dashVector.get(i)).floatValue());
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

    private static void consume_j(GraphicsState graphicState, Stack stack, Shapes shapes) {
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
        // updates shapes with with the new stroke type
        setStroke(shapes, graphicState);
    }

    private static void consume_w(GraphicsState graphicState, Stack stack, Shapes shapes) {
        graphicState.setLineWidth(((Number) stack.pop()).floatValue());
        setStroke(shapes, graphicState);
    }

    private static void consume_M(GraphicsState graphicState, Stack stack, Shapes shapes) {
        graphicState.setMiterLimit(((Number) stack.pop()).floatValue());
        setStroke(shapes, graphicState);
    }

    private static void consume_gs(GraphicsState graphicState, Stack stack, Resources resources) {
        Object gs = stack.pop();
        if (gs instanceof Name) {
            // Get ExtGState and merge it with
            ExtGState extGState =
                    resources.getExtGState((Name) gs);
            if (extGState != null) {
                graphicState.concatenate(extGState);
            }
        }
    }

    private static void consume_Tf(GraphicsState graphicState, Stack stack, Resources resources) {
        float size = ((Number) stack.pop()).floatValue();
        Name name2 = (Name) stack.pop();
        // build the new font and initialize it.

        graphicState.getTextState().font = resources.getFont(name2);
        // in the rare case that the font can't be found then we try and build
        // one so the document can be rendered in some shape or form.
        if (graphicState.getTextState().font == null ||
                graphicState.getTextState().font.getFont() == null) {
            // turn on the old awt font engine, as we have a null font
            FontFactory fontFactory = FontFactory.getInstance();
            boolean awtState = fontFactory.isAwtFontSubstitution();
            fontFactory.setAwtFontSubstitution(true);
            // get the first pages resources, no need to lock the page, already locked.
            Resources res = resources.getLibrary().getCatalog().getPageTree()
                    .getPage(0).getResources();
            // try and get a font off the first page.
            Object pageFonts = res.getEntries().get(Resources.FONT_KEY);
            if (pageFonts instanceof HashMap) {
                // get first font
                Reference fontRef = (Reference) ((HashMap) pageFonts).get(name2);
                graphicState.getTextState().font =
                        (org.icepdf.core.pobjects.fonts.Font) resources.getLibrary()
                                .getObject(fontRef);
                // might get a null pointer but we'll get on on deriveFont too
                graphicState.getTextState().font.init();
            }
            // return factory to original state.
            fontFactory.setAwtFontSubstitution(awtState);
            // if no fonts found then we just bail and accept the null pointer
        }
        graphicState.getTextState().currentfont =
                graphicState.getTextState().font.getFont().deriveFont(size);
    }

    private static void consume_Tc(GraphicsState graphicState, Stack stack) {
        graphicState.getTextState().cspace = ((Number) stack.pop()).floatValue();
    }

    private static void consume_Tz(GraphicsState graphicState, Stack stack) {
        Object ob = stack.pop();
        if (ob instanceof Number) {
            float hScaling = ((Number) ob).floatValue();
            // store the scaled value, but not apply the state operator at this time
            graphicState.getTextState().hScalling = hScaling / 100.0f;
        }
    }

    private static void consume_Tw(GraphicsState graphicState, Stack stack) {
        graphicState.getTextState().wspace = ((Number) stack.pop()).floatValue();
    }

    private static void consume_Tr(GraphicsState graphicState, Stack stack) {
        graphicState.getTextState().rmode = (int) ((Number) stack.pop()).floatValue();
    }

    private static void consume_TL(GraphicsState graphicState, Stack stack) {
        graphicState.getTextState().leading = ((Number) stack.pop()).floatValue();
    }

    private static void consume_Ts(GraphicsState graphicState, Stack stack) {
        graphicState.getTextState().trise = ((Number) stack.pop()).floatValue();
    }

    /**
     * Utility method for calculating the advanceX need for the
     * <code>displayText</code> given the strings parsed textState.  Each of
     * <code>displayText</code> glyphs and respective, text state is added to
     * the shapes collection.
     *
     * @param displayText     text that will be drawn to the screen
     * @param advance         current advanceX of last drawn string
     * @param previousAdvance last advance of where the string should be drawn
     * @param textState       formating properties associated with displayText
     * @param shapes          collection of all shapes for page content being parsed.
     * @return the modified advanceX value which can be used for the the next
     *         string that needs to be drawn
     */
    private Point2D drawString(
            StringBuilder displayText,
            Point2D advance,
            float previousAdvance,
            TextState textState,
            Shapes shapes,
            GlyphOutlineClip glyphOutlineClip) {

        float advanceX = ((Point2D.Float) advance).x;
        float advanceY = ((Point2D.Float) advance).y;

        if (displayText.length() == 0) {
            return new Point2D.Float(previousAdvance, 0);
        }

        // Postion of previous Glyph, all relative to text block
        float lastx = 0, lasty = 0;
        // Make sure that the previous advanceX is greater then then where we
        // are going to place the next glyph,  see not 57 in 1.6 spec for more
        // information.
        char currentChar = displayText.charAt(0);
        // Position of the specified glyph relative to the origin of glyphVector
        float firstCharWidth = (float) textState.currentfont.echarAdvance(currentChar).getX();

        if ((advanceX + firstCharWidth) < previousAdvance) {
            advanceX = previousAdvance;
        }

        // Data need on font
        FontFile currentFont = textState.currentfont;
        boolean isVerticalWriting = textState.font.isVerticalWriting();
        // int spaceCharacter = currentFont.getSpaceEchar();

        // font metrics data
        float textRise = textState.trise;
        float charcterSpace = textState.cspace * textState.hScalling;
        float whiteSpace = textState.wspace * textState.hScalling;
        int textLength = displayText.length();

        // create a new sprite to hold the text objects
        TextSprite textSprites =
                new TextSprite(currentFont,
                        textLength,
                        new AffineTransform(graphicState.getCTM()));

        // glyph placement params
        float currentX, currentY;
        float newAdvanceX, newAdvanceY;
        // Iterate through displayText to calculate the the new advanceX value
        for (int i = 0; i < textLength; i++) {
            currentChar = displayText.charAt(i);

            // Position of the specified glyph relative to the origin of glyphVector
            // advance is handled by the particular font implementation.
            newAdvanceX = (float) currentFont.echarAdvance(currentChar).getX();

            newAdvanceY = newAdvanceX;
            if (!isVerticalWriting) {
                // add fonts rise to the to glyph position (sup,sub scripts)
                currentX = advanceX + lastx;
                currentY = lasty - textRise;
                lastx += newAdvanceX;
                // add the space between chars value
                lastx += charcterSpace;
                // lastly add space widths,
                if (displayText.charAt(i) == 32) { // currently to unreliable currentFont.getSpaceEchar()
                    lastx += whiteSpace;
                }
            } else {
                // add fonts rise to the to glyph position (sup,sub scripts)
                lasty += (newAdvanceY - textRise);
                currentX = advanceX - (newAdvanceX / 2.0f);
                currentY = advanceY + lasty;
            }

            // get normalized from from text sprite
            GlyphText glyphText = textSprites.addText(
                    String.valueOf(currentChar), // cid
                    textState.currentfont.toUnicode(currentChar), // unicode value
                    currentX, currentY, newAdvanceX);
            shapes.getPageText().addGlyph(glyphText);

        }
        // append the finally offset of the with of the character
        advanceX += lastx;
        advanceY += lasty;

        /**
         * The text rendering mode, Tmode, determines whether showing text
         * causes glyph outlines to be stroked, filled, used as a clipping
         * boundary, or some combination of the three.
         *
         * No Support for 4, 5, 6 and 7.
         *
         * 0 - Fill text
         * 1 - Stroke text
         * 2 - fill, then stroke text
         * 3 - Neither fill nor stroke text (invisible)
         * 4 - Fill text and add to path for clipping
         * 5 - Stroke text and add to path for clipping.
         * 6 - Fill, then stroke text and add to path for clipping.
         * 7 - Add text to path for clipping.
         */

        int rmode = textState.rmode;
        switch (rmode) {
            // fill text: 0
            case TextState.MODE_FILL:
                drawModeFill(textSprites, shapes, rmode);
                break;
            // Stroke text: 1
            case TextState.MODE_STROKE:
                drawModeStroke(textSprites, textState, shapes, rmode);
                break;
            // Fill, then stroke text: 2
            case TextState.MODE_FILL_STROKE:
                drawModeFillStroke(textSprites, textState, shapes, rmode);
                break;
            // Neither fill nor stroke text (invisible): 3
            case TextState.MODE_INVISIBLE:
                // do nothing
                break;
            // Fill text and add to path for clipping: 4
            case TextState.MODE_FILL_ADD:
                drawModeFill(textSprites, shapes, rmode);
                glyphOutlineClip.addTextSprite(textSprites);
                break;
            // Stroke Text and add to path for clipping: 5
            case TextState.MODE_STROKE_ADD:
                drawModeStroke(textSprites, textState, shapes, rmode);
                glyphOutlineClip.addTextSprite(textSprites);
                break;
            // Fill, then stroke text adn add to path for clipping: 6
            case TextState.MODE_FILL_STROKE_ADD:
                drawModeFillStroke(textSprites, textState, shapes, rmode);
                glyphOutlineClip.addTextSprite(textSprites);
                break;
            // Add text to path for clipping: 7
            case TextState.MODE_ADD:
                glyphOutlineClip.addTextSprite(textSprites);
                break;
        }
        return new Point2D.Float(advanceX, advanceY);
    }

    /**
     * Utility Method for adding a text sprites to the Shapes stack, given the
     * specified rmode.
     *
     * @param textSprites text to add to shapes stack
     * @param shapes      shapes stack
     * @param rmode       write mode
     */
    private void drawModeFill(TextSprite textSprites, Shapes shapes, int rmode) {
        textSprites.setRMode(rmode);
        shapes.add(new ColorDrawCmd(graphicState.getFillColor()));
        shapes.add(new TextSpriteDrawCmd(textSprites));
    }

    /**
     * Utility Method for adding a text sprites to the Shapes stack, given the
     * specifed rmode.
     *
     * @param textSprites text to add to shapes stack
     * @param shapes      shapes stack
     * @param textState   text state used to build new stroke
     * @param rmode       write mode
     */
    private void drawModeStroke(TextSprite textSprites, TextState textState,
                                Shapes shapes, int rmode) {
        // setup textSprite with a strokeColor and the correct rmode
        textSprites.setRMode(rmode);
        textSprites.setStrokeColor(graphicState.getStrokeColor());
        // save the old line width
        float old = graphicState.getLineWidth();

        // set the line width for the glyph
        float lineWidth = graphicState.getLineWidth();
        lineWidth /= textState.tmatrix.getScaleX();
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
     * specifed rmode.
     *
     * @param textSprites text to add to shapes stack
     * @param textState   text state used to build new stroke
     * @param shapes      shapes stack
     * @param rmode       write mode
     */
    private void drawModeFillStroke(TextSprite textSprites, TextState textState,
                                    Shapes shapes, int rmode) {
        // setup textSprite with a strokeColor and the correct rmode
        textSprites.setRMode(rmode);
        textSprites.setStrokeColor(graphicState.getStrokeColor());
        // save the old line width
        float old = graphicState.getLineWidth();

        // set the line width for the glyph
        float lineWidth = graphicState.getLineWidth();
        lineWidth /= textState.tmatrix.getScaleX();
        graphicState.setLineWidth(lineWidth);
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
     * @param geometricPath current path.
     */
    private static void commonStroke(GraphicsState graphicState, Shapes shapes, GeneralPath geometricPath) {

        // get current fill alpha and concatenate with overprinting if present
        if (graphicState.isOverprintStroking()) {
            setAlpha(shapes, graphicState.getAlphaRule(),
                    commonOverPrintAlpha(graphicState.getStrokeAlpha()));
        }
        // The knockout effect can only be achieved by changing the alpha
        // composite to source.  I don't have a test case for this for stroke
        // but what we do for stroke is usually what we do for fill...
        else if (graphicState.isKnockOut()) {
            setAlpha(shapes, AlphaComposite.SRC, graphicState.getStrokeAlpha());
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
                // 1.)save the graphics context
                graphicState = graphicState.save();
                // 2.) install the graphic state
                tilingPattern.setParentGraphicState(graphicState);
                tilingPattern.init();
                // 4.) Restore the saved graphics state
                graphicState = graphicState.restore();
                // 1x1 tiles don't seem to paint so we'll resort to using the
                // first pattern colour or the uncolour.
                if ((tilingPattern.getBBox().getWidth() > 1 &&
                        tilingPattern.getBBox().getHeight() > 1)) {
                    shapes.add(new TilingPatternDrawCmd(tilingPattern));
                } else {
                    // draw partial fill colour
                    if (tilingPattern.getPaintType() ==
                            TilingPattern.PAINTING_TYPE_UNCOLORED_TILING_PATTERN) {
                        shapes.add(new ColorDrawCmd(tilingPattern.getUnColored()));
                    } else {
                        shapes.add(new ColorDrawCmd(tilingPattern.getFirstColor()));
                    }
                }
                shapes.add(new ShapeDrawCmd(geometricPath));
                shapes.add(new DrawDrawCmd());
            } else if (pattern != null &&
                    pattern.getPatternType() == Pattern.PATTERN_TYPE_SHADING) {
                pattern.init();
                shapes.add(new PaintDrawCmd(pattern.getPaint()));
                shapes.add(new ShapeDrawCmd(geometricPath));
                shapes.add(new DrawDrawCmd());
            }
        } else {
            setAlpha(shapes, graphicState.getAlphaRule(), graphicState.getStrokeAlpha());
            shapes.add(new ColorDrawCmd(graphicState.getStrokeColor()));
            shapes.add(new ShapeDrawCmd(geometricPath));
            shapes.add(new DrawDrawCmd());
        }
        // set alpha back to origional value.
        if (graphicState.isOverprintStroking()) {
            setAlpha(shapes, AlphaComposite.SRC_OVER, graphicState.getFillAlpha());
        }
    }

    /**
     * Utility method for fudging overprinting calculation for screen
     * representation.
     *
     * @param alpha alph constant
     * @return tweaked over printing alpha
     */
    private static float commonOverPrintAlpha(float alpha) {
        // if alpha is already present we reduce it and we minimize
        // it if it is already lower then our over paint.  This an approximation
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

    /**
     * Common fill operations used by f, F, F*, b, b*,  B, B*. Takes into
     * account patternColour and regular old fill colour.
     *
     * @param shapes        current shapes stack
     * @param geometricPath current path.
     */
    private void commonFill(Shapes shapes, GeneralPath geometricPath) throws NoninvertibleTransformException {

        // get current fill alpha and concatenate with overprinting if present
        if (graphicState.isOverprintOther()) {
            setAlpha(shapes, graphicState.getAlphaRule(),
                    commonOverPrintAlpha(graphicState.getFillAlpha()));
        }
        // The knockout effect can only be achieved by changing the alpha
        // composite to source.
        else if (graphicState.isKnockOut()) {
            setAlpha(shapes, AlphaComposite.SRC, graphicState.getFillAlpha());
        } else {
            setAlpha(shapes, graphicState.getAlphaRule(), graphicState.getFillAlpha());
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
                // 1.)save the graphics context
                graphicState = graphicState.save();
                // 2.) install the graphic state
                tilingPattern.setParentGraphicState(graphicState);
                tilingPattern.init();
                // 4.) Restore the saved graphics state
                graphicState = graphicState.restore();
                // tiles nee to be 1x1 or larger to paint so we'll resort to using the
                // first pattern colour or the uncolour.
                if ((tilingPattern.getBBox().getWidth() >= 1 ||
                        tilingPattern.getBBox().getHeight() >= 1)) {
                    shapes.add(new TilingPatternDrawCmd(tilingPattern));
                } else {
                    // draw partial fill colour
                    if (tilingPattern.getPaintType() ==
                            TilingPattern.PAINTING_TYPE_UNCOLORED_TILING_PATTERN) {
                        shapes.add(new ColorDrawCmd(tilingPattern.getUnColored()));
                    } else {
                        shapes.add(new ColorDrawCmd(tilingPattern.getFirstColor()));
                    }
                }
                shapes.add(new ShapeDrawCmd(geometricPath));
                shapes.add(new FillDrawCmd());
            } else if (pattern != null &&
                    pattern.getPatternType() == Pattern.PATTERN_TYPE_SHADING) {
                pattern.init();
                shapes.add(new PaintDrawCmd(pattern.getPaint()));
                shapes.add(new ShapeDrawCmd(geometricPath));
                shapes.add(new FillDrawCmd());
            }

        } else {
            shapes.add(new ColorDrawCmd(graphicState.getFillColor()));
            shapes.add(new ShapeDrawCmd(geometricPath));
            shapes.add(new FillDrawCmd());
        }
        // add old alpha back to stack
        if (graphicState.isOverprintOther()) {
            setAlpha(shapes, graphicState.getAlphaRule(), graphicState.getFillAlpha());
        }
    }

    /**
     * Sets the state of the BasicStrok with the latest values from the
     * graphicSate instance value:
     * graphicState.lineWidth - line width
     * graphicState.lineCap - line cap type
     * graphicState.lineJoin - line join type
     * graphicState.miterLimit -  miter limit
     *
     * @param shapes       current Shapes object for the page being parsed
     * @param graphicState graphic state used to build this stroke instance.
     */
    private static void setStroke(Shapes shapes, GraphicsState graphicState) {
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
     * use but it appears that the scaling has to bee applied before a text
     * write operand occurs, otherwise a call to Tm seems to break text
     * positioning.
     * <p/>
     * Scalling is special as it can be negative and thus apply a horizontal
     * flip on the graphic state.
     *
     * @param graphicState current graphics state.
     */
    private static AffineTransform applyTextScaling(GraphicsState graphicState) {
        // get the current CTM
        AffineTransform af = new AffineTransform(graphicState.getCTM());
        // the mystery continues,  it appears that only the negative or positive
        // value of tz is actually used.  If the original non 1 number is used the
        // layout will be messed up.
        AffineTransform oldHScaling = new AffineTransform(graphicState.getCTM());
        float hScalling = graphicState.getTextState().hScalling;
        AffineTransform horizontalScalingTransform =
                new AffineTransform(
                        af.getScaleX() * hScalling,
                        af.getShearY(),
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
     * @param shapes - current shapes vector to add Alpha Composite to
     * @param rule   - rule to apply to the alphaComposite.
     * @param alpha  - alpha value, opaque = 1.0f.
     */
    private static void setAlpha(Shapes shapes, int rule, float alpha) {
        // Build the alpha composite object and add it to the shapes
        AlphaComposite alphaComposite =
                AlphaComposite.getInstance(rule,
                        alpha);
        shapes.add(new AlphaDrawCmd(alphaComposite));
    }
}
