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
package org.icepdf.core.util;

import org.icepdf.core.io.SeekableByteArrayInputStream;
import org.icepdf.core.io.SeekableInput;
import org.icepdf.core.io.SeekableInputConstrainedWrapper;
import org.icepdf.core.pobjects.*;
import org.icepdf.core.pobjects.fonts.FontFile;
import org.icepdf.core.pobjects.graphics.*;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Stack;
import java.util.Vector;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * The ContentParser is responsible for parsing a page's content streams.  The
 * parsed text, image and other PDF object types are added the pages Shapes
 * object for later drawing and display.
 */
public class ContentParser {

    private static final Logger logger =
            Logger.getLogger(ContentParser.class.toString());

    public static final float OVERPAINT_ALPHA = 0.4f;

    private GraphicsState graphicState;
    private Library library;
    private Resources resources;

//    private static HashTable tokenFrequency = new Hashtable(90);

    // flag to handle none text based coordinate operand "cm" inside of a text block
    private boolean inTextBlock;

    // textObjects vector insertion index, incremented when a new text block
    // is encountered so that extract text is easier to manipulate.
    private int textBlockIndex = 0;

    // TextBlock affine transform can be altered by the "cm" operand an thus
    // the text base affine transform must be accessible outside the parsTtext method
    private AffineTransform textBlockBase;

    /**
     * @param l PDF library master object.
     * @param r resources
     */
    public ContentParser(Library l, Resources r) {
        library = l;
        resources = r;

    }

    /*  private static void collectTokenFrequency(String token){
        Float count = (Float)tokenFrequency.get(token);
        float value;
        if (count != null){
            value = count.floatValue();
            value ++;
            tokenFrequency.remove(token);
            tokenFrequency.put(token, new Float(value));
        }
        else{
            tokenFrequency.put(token, new Float(1));
        }
    }

    private void printTokenFrequency(){
        Enumeration enum = tokenFrequency.keys();
        while (enum.hasMoreElements()){

            String key = (String)enum.nextElement();
            Float tmp = (Float)tokenFrequency.get(key);
            System.out.print(key + ", ");
            if (tmp != null){
                System.out.println(tmp.toString());
            }
            else{
                System.out.println("");
            }
        }
    }*/

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
     * otherwise it will have not effect on state of the draw operands.
     *
     * @param graphicState
     */
    public void setGraphicsState(GraphicsState graphicState) {
        this.graphicState = graphicState;
    }

    /**
     * Parse a pages content stream.
     *
     * @param source byte stream containing page content
     * @return a Shapes Ojbect containing all the pages text and images shapes.
     */
    public Shapes parse(InputStream source) {
        Shapes shapes = new Shapes();
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

        if (logger.isLoggable(Level.FINER)) {
            String content;
            if (source instanceof SeekableInput) {
                content = Utils.getContentFromSeekableInput((SeekableInput) source, false);
            } else {
                InputStream[] inArray = new InputStream[]{source};
                content = Utils.getContentAndReplaceInputStream(inArray, false);
                source = inArray[0];
            }
            logger.finer("Content = " + content);
        }

        // great a parser to get tokens for stream
        Parser parser;

        // test case for progress bar
        parser = new Parser(source);

        // stack to help with the parse
        Stack<Object> stack = new Stack<Object>();

//        long startTime = System.currentTimeMillis();
        try {
            //represents a geometric path constructed from straight lines, and
            // quadratic and cubic (B&eacute;zier) curves.  It can contain
            // multiple subpaths.
            GeneralPath geometricPath = null;

            // loop through each token returned form the parser
            Object tok;
            while (true) {

                tok = parser.getStreamObject();

                // add any names and numbers and every thing else on the
                // stack for future reference
                if (!(tok instanceof String)) {
                    stack.push(tok);
                } else {

                    // Append a straight line segment from the current point to the
                    // point (x, y). The new current point is (x, y).
                    if (tok.equals(PdfOps.l_TOKEN)) {
//                        collectTokenFrequency(PdfOps.l_TOKEN);
                        float y = ((Number) stack.pop()).floatValue();
                        float x = ((Number) stack.pop()).floatValue();
                        geometricPath.lineTo(x, y);
                    }

                    // Begin a new subpath by moving the current point to
                    // coordinates (x, y), omitting any connecting line segment. If
                    // the previous path construction operator in the current path
                    // was also m, the new m overrides it; no vestige of the
                    // previous m operation remains in the path.
                    else if (tok.equals(PdfOps.m_TOKEN)) {
//                        collectTokenFrequency(PdfOps.m_TOKEN);
                        if (geometricPath == null) {
                            geometricPath = new GeneralPath();
                        }
                        float y = ((Number) stack.pop()).floatValue();
                        float x = ((Number) stack.pop()).floatValue();
                        geometricPath.moveTo(x, y);
                    }

                    // Append a cubic Bézier curve to the current path. The curve
                    // extends from the current point to the point (x3, y3), using
                    // (x1, y1) and (x2, y2) as the Bézier control points.
                    // The new current point is (x3, y3).
                    else if (tok.equals(PdfOps.c_TOKEN)) {
//                        collectTokenFrequency(PdfOps.c_TOKEN);
                        float y3 = ((Number) stack.pop()).floatValue();
                        float x3 = ((Number) stack.pop()).floatValue();
                        float y2 = ((Number) stack.pop()).floatValue();
                        float x2 = ((Number) stack.pop()).floatValue();
                        float y1 = ((Number) stack.pop()).floatValue();
                        float x1 = ((Number) stack.pop()).floatValue();
                        geometricPath.curveTo(x1, y1, x2, y2, x3, y3);
                    }

                    // Stroke the path
                    else if (tok.equals(PdfOps.S_TOKEN)) {
//                        collectTokenFrequency(PdfOps.S_TOKEN);
                        if (geometricPath != null) {
                            commonStroke(shapes, geometricPath);
                            geometricPath = null;
                        }
                    }

                    // Font selection
                    else if (tok.equals(PdfOps.Tf_TOKEN)) {
                        consume_Tf(graphicState, stack, resources);
                    }

                    // Begin a text object, initializing the text matrix, Tm, and
                    // the text line matrix, Tlm, to the identity matrix. Text
                    // objects cannot be nested; a second BT cannot appear before
                    // an ET.
                    else if (tok.equals(PdfOps.BT_TOKEN)) {
//                        collectTokenFrequency(PdfOps.BT_TOKEN);
                        // set graphics state alpha back to 1.0f for text
                        setAlpha(shapes, 1.0f);
                        // start parseText, which parses until ET is reached
                        parseText(parser, shapes, false, null);
                    }

                    // Fill the path, using the nonzero winding number rule to
                    // determine the region to fill (see “Nonzero Winding
                    // Number Rule” ). Any subpaths that are open are implicitly
                    // closed before being filled. f or F
                    else if (tok.equals(PdfOps.F_TOKEN) ||
                            tok.equals(PdfOps.f_TOKEN)) {
//                        collectTokenFrequency(PdfOps.F_TOKEN);
//                        collectTokenFrequency(PdfOps.f_TOKEN);
                        if (geometricPath != null) {
                            geometricPath.setWindingRule(GeneralPath.WIND_NON_ZERO);
                            commonFill(shapes, geometricPath);
                        }
                        geometricPath = null;
                    }

                    // Saves Graphics State, should copy the entire  graphics state onto
                    // the graphicsState object's stack
                    else if (tok.equals(PdfOps.q_TOKEN)) {
                        graphicState = consume_q(graphicState);
                    }
                    // Restore Graphics State, should restore teh entire graphics state
                    // to its former value by popping it from the stack
                    else if (tok.equals(PdfOps.Q_TOKEN)) {
                        graphicState = consume_Q(graphicState, shapes);
                    }

                    // Append a rectangle to the current path as a complete subpath,
                    // with lower-left corner (x, y) and dimensions width and height
                    // in user space. The operation x y width height re is equivalent to
                    //        x y m
                    //        (x + width) y l
                    //       (x + width) (y + height) l
                    //        x (y + height) l
                    //        h
                    else if (tok.equals(PdfOps.re_TOKEN)) {
//                        collectTokenFrequency(PdfOps.re_TOKEN);
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
                    }

                    // Modify the current transformation matrix (CTM) by concatenating the
                    // specified matrix
                    else if (tok.equals(PdfOps.cm_TOKEN)) {
                        consume_cm(graphicState, stack, inTextBlock, textBlockBase);
                    }

                    // Close the current sub path by appending a straight line segment
                    // from the current point to the starting point of the sub path.
                    // This operator terminates the current sub path; appending
                    // another segment to the current path will begin a new subpath,
                    // even if the new segment begins at the endpoint reached by the
                    // h operation. If the current subpath is already closed,
                    // h does nothing.
                    else if (tok.equals(PdfOps.h_TOKEN)) {
//                        collectTokenFrequency(PdfOps.h_TOKEN);
                        if (geometricPath != null) {
                            geometricPath.closePath();
                        }
                    }

                    // Begin a marked-content sequence with an associated property
                    // list, terminated by a balancing EMC operator. tag is a name
                    // object indicating the role or significance of the sequence;
                    // properties is either an inline dictionary containing the
                    // property list or a name object associated with it in the
                    // Properties sub dictionary of the current resource dictionary
                    else if (tok.equals(PdfOps.BDC_TOKEN)) {
//                        collectTokenFrequency(PdfOps.BDC_TOKEN);
                        stack.pop(); // properties
                        stack.pop(); // name
                    }

                    // End a marked-content sequence begun by a BMC or BDC operator.
                    else if (tok.equals(PdfOps.EMC_TOKEN)) {
//                        collectTokenFrequency(PdfOps.EMC_TOKEN);
                    }

                    /**
                     * External Object (XObject) a graphics object whose contents
                     * are defined by a self-contained content stream, separate
                     * from the content stream in which it is used. There are three
                     * types of external object:
                     *
                     *   • An image XObject (Section 4.8.4, “Image Dictionaries”)
                     *     represents a sampled visual image such as a photograph.
                     *   • A form XObject (Section 4.9, “Form XObjects”) is a
                     *     self-contained description of an arbitrary sequence of
                     *     graphics objects.
                     *   • A PostScript XObject (Section 4.7.1, “PostScript XObjects”)
                     *     contains a fragment of code expressed in the PostScript
                     *     page description language. PostScript XObjects are no
                     *     longer recommended to be used. (NOT SUPPORTED)
                     */
                    // Paint the specified XObject. The operand name must appear as
                    // a key in the XObject subdictionary of the current resource
                    // dictionary (see Section 3.7.2, “Resource Dictionaries”); the
                    // associated value must be a stream whose Type entry, if
                    // present, is XObject. The effect of Do depends on the value of
                    // the XObject’s Subtype entry, which may be Image , Form, or PS
                    else if (tok.equals(PdfOps.Do_TOKEN)) {
//                        collectTokenFrequency(PdfOps.Do_TOKEN);
                        String xobjectName = ((Name) (stack.pop())).getName();
                        // Form XObject
                        if (resources.isForm(xobjectName)) {
                            // Do operator steps:
                            //  1.)save the graphics context
                            graphicState = graphicState.save();
                            // Try and find the named reference 'xobjectName', pass in a copy
                            // of the current graphics state for the new content stream
                            Form formXObject = resources.getForm(xobjectName);
                            if (formXObject != null) {
                                // init formXobject
                                formXObject.setGraphicsState(new GraphicsState(graphicState));
                                // according to spec the formXObject might not have
                                // resources reference as a result we pass in the current
                                // one in the hope that any resources can be found.
                                formXObject.setParentResources(resources);
                                formXObject.init();
                                // 2.) concatenate matrix entry with the current CTM
                                AffineTransform af =
                                        new AffineTransform(graphicState.getCTM());
                                af.concatenate(formXObject.getMatrix());
                                shapes.add(af);
                                // 3.) Clip according to the form BBox entry
                                if (graphicState.getClip() != null) {
                                    shapes.add(formXObject.getBBox().createIntersection(
                                            graphicState.getClip().getBounds2D()));
                                } else {
                                    shapes.add(formXObject.getBBox());
                                }
                                shapes.addClipCommand();
                                // 4.) Paint the graphics objects in font stream.
                                shapes.add(formXObject.getShapes());
                                // makes sure we add xobject images so we can extract them.
                                if (formXObject.getShapes() != null) {
                                    shapes.add(formXObject.getShapes().getImages());
                                }
                                shapes.addNoClipCommand();
                            }
                            //  5.) Restore the saved graphics state
                            graphicState = graphicState.restore();
                        }
                        // Image XObject
                        else {
                            Image im = resources.getImage(xobjectName,
                                    graphicState.getFillColor());
                            if (im != null) {
                                AffineTransform af =
                                        new AffineTransform(graphicState.getCTM());
                                graphicState.scale(1, -1);
                                graphicState.translate(0, -1);
                                // add the image
                                shapes.add(im);
                                graphicState.set(af);
                            }
                        }
                    }

                    // Fill the path, using the even-odd rule to determine the
                    // region to fill
                    else if (tok.equals(PdfOps.f_STAR_TOKEN)) {
//                        collectTokenFrequency(PdfOps.f_STAR_TOKEN);
                        if (geometricPath != null) {
                            // need to apply pattern..
                            geometricPath.setWindingRule(GeneralPath.WIND_EVEN_ODD);
                            commonFill(shapes, geometricPath);
                        }
                        geometricPath = null;
                    }

                    // Sets the specified parameters in the graphics state.  The gs operand
                    // points to a name resource which should be a an ExtGState object.
                    // The graphics state parameters in the ExtGState must be concatenated
                    // with the the current graphics state.
                    else if (tok.equals(PdfOps.gs_TOKEN)) {
                        consume_gs(graphicState, stack, resources);
                    }

                    // End the path object without filling or stroking it. This
                    // operator is a “path-painting no-op,” used primarily for the
                    // side effect of changing the current clipping path
                    else if (tok.equals(PdfOps.n_TOKEN)) {
//                        collectTokenFrequency(PdfOps.n_TOKEN);
                        //graphicState.setClip(geometricPath);
                        // clipping path outlines are visible when this is set to null;
                        geometricPath = null;
                    }

                    // Set the line width in the graphics state
                    else if (tok.equals(PdfOps.w_TOKEN) ||
                            tok.equals(PdfOps.LW_TOKEN)) {
                        consume_w(graphicState, stack, shapes);
                    }

                    // Modify the current clipping path by intersecting it with the
                    // current path, using the nonzero winding number rule to
                    // determine which regions lie inside the clipping path.
                    else if (tok.equals(PdfOps.W_TOKEN)) {
//                        collectTokenFrequency(PdfOps.W_TOKEN);
                        if (geometricPath != null) {
                            geometricPath.setWindingRule(GeneralPath.WIND_NON_ZERO);
                            geometricPath.closePath();
                            graphicState.setClip(geometricPath);
                        }

                    }

                    // Fill Color with ColorSpace
                    else if (tok.equals(PdfOps.sc_TOKEN) ||
                            tok.equals(PdfOps.scn_TOKEN)) {
                        consume_sc(graphicState, stack, library, resources);
                    }

                    // Close, fill, and then stroke the path, using the nonzero
                    // winding number rule to determine the region to fill. This
                    // operator has the same effect as the sequence h B. See also
                    // “Special Path-Painting Considerations”
                    else if (tok.equals(PdfOps.b_TOKEN)) {
//                        collectTokenFrequency(PdfOps.b_TOKEN);
                        if (geometricPath != null) {
                            geometricPath.setWindingRule(GeneralPath.WIND_NON_ZERO);
                            geometricPath.closePath();
                            commonStroke(shapes, geometricPath);
                            commonFill(shapes, geometricPath);
                        }
                        geometricPath = null;
                    }

                    // Same as K, but for non-stroking operations.
                    else if (tok.equals(PdfOps.k_TOKEN)) { // Fill Color CMYK
                        consume_k(graphicState, stack, library, resources);
                    }

                    // Same as g but for none stroking operations
                    else if (tok.equals(PdfOps.g_TOKEN)) {
                        consume_g(graphicState, stack, library, resources);
                    }

                    // Sets the flatness tolerance in the graphics state, NOT SUPPORTED
                    // flatness is a number in the range 0 to 100, a value of 0 specifies
                    // the default tolerance
                    else if (tok.equals(PdfOps.i_TOKEN)) {
                        consume_i(stack);
                    }

                    // Miter Limit
                    else if (tok.equals(PdfOps.M_TOKEN)) {
                        consume_M(graphicState, stack, shapes);
                    }

                    // Set the line cap style of the graphic state, related to Line Join
                    // style
                    else if (tok.equals(PdfOps.J_TOKEN)) {
                        consume_J(graphicState, stack, shapes);
                    }

                    // Same as RG, but for non-stroking operations.
                    else if (tok.equals(PdfOps.rg_TOKEN)) { // Fill Color RGB
                        consume_rg(graphicState, stack, library, resources);
                    }

                    // Sets the line dash pattern in the graphics state. A normal line
                    // is [] 0.  See Graphics State -> Line dash patter for more information
                    // in the PDF Reference.  Java 2d uses the same notation so there
                    // is not much work to be done other then parsing the data.
                    else if (tok.equals(PdfOps.d_TOKEN)) {
                        consume_d(graphicState, stack, shapes);
                    }

                    // Append a cubic Bézier curve to the current path. The curve
                    // extends from the current point to the point (x3, y3), using
                    // the current point and (x2, y2) as the Bézier control points.
                    // The new current point is (x3, y3).
                    else if (tok.equals(PdfOps.v_TOKEN)) {
//                        collectTokenFrequency(PdfOps.v_TOKEN);
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

                    // Set the line join style in the graphics state
                    else if (tok.equals(PdfOps.j_TOKEN)) {
                        consume_j(graphicState, stack, shapes);
                    }

                    // Append a cubic Bézier curve to the current path. The curve
                    // extends from the current point to the point (x3, y3), using
                    // (x1, y1) and (x3, y3) as the Bézier control points.
                    // The new current point is (x3, y3).
                    else if (tok.equals(PdfOps.y_TOKEN)) {
//                        collectTokenFrequency(PdfOps.y_TOKEN);
                        float y3 = ((Number) stack.pop()).floatValue();
                        float x3 = ((Number) stack.pop()).floatValue();
                        float y1 = ((Number) stack.pop()).floatValue();
                        float x1 = ((Number) stack.pop()).floatValue();
                        geometricPath.curveTo(x1, y1, x3, y3, x3, y3);
                    }

                    // Same as CS, but for nonstroking operations.
                    else if (tok.equals(PdfOps.cs_TOKEN)) {
                        consume_cs(graphicState, stack, resources);
                    }

                    // Color rendering intent in the graphics state
                    else if (tok.equals(PdfOps.ri_TOKEN)) {
//                        collectTokenFrequency(PdfOps.ri_TOKEN);
                        stack.pop();
                    }

                    // Set the color to use for stroking operations in a device, CIE-based
                    // (other than ICCBased), or Indexed color space. The number of operands
                    // required and their interpretation depends on the current stroking color space:
                    //   • For DeviceGray, CalGray, and Indexed color spaces, one operand
                    //     is required (n = 1).
                    //   • For DeviceRGB, CalRGB, and Lab color spaces, three operands are
                    //     required (n = 3).
                    //   • For DeviceCMYK, four operands are required (n = 4).
                    else if (tok.equals(PdfOps.SC_TOKEN) ||
                            tok.equals(PdfOps.SCN_TOKEN)) { // Stroke Color with ColorSpace
                        consume_SC(graphicState, stack, library, resources);
                    }

                    // Fill and then stroke the path, using the nonzero winding
                    // number rule to determine the region to fill. This produces
                    // the same result as constructing two identical path objects,
                    // painting the first with f and the second with S. Note,
                    // however, that the fillingand stroking portions of the
                    // operation consult different values of several graphics state
                    // parameters, such as the current color.
                    else if (tok.equals(PdfOps.B_TOKEN)) {
//                        collectTokenFrequency(PdfOps.B_TOKEN);
                        if (geometricPath != null) {
                            geometricPath.setWindingRule(GeneralPath.WIND_NON_ZERO);
                            commonStroke(shapes, geometricPath);
                            commonFill(shapes, geometricPath);
                        }
                        geometricPath = null;
                    }

                    // Set the stroking color space to DeviceCMYK (or the DefaultCMYK color
                    // space; see “Default Color Spaces” on page 227) and set the color to
                    // use for stroking operations. Each operand must be a number between
                    // 0.0 (zero concentration) and 1.0 (maximum concentration). The
                    // behavior of this operator is affected by the overprint mode
                    // (see Section 4.5.6, “Overprint Control”).
                    else if (tok.equals(PdfOps.K_TOKEN)) { // Stroke Color CMYK
                        consume_K(graphicState, stack, library, resources);
                    }

                    /**
                     * Type3 operators, update the text state with data from these operands
                     */
                    else if (tok.equals(PdfOps.d0_TOKEN)) {
//                        collectTokenFrequency(PdfOps.d0_TOKEN);
                        // save the stack
                        graphicState = graphicState.save();
                        // need two pops to get  Wx and Wy data
                        float y = ((Number) stack.pop()).floatValue();
                        float x = ((Number) stack.pop()).floatValue();
                        TextState textState = graphicState.getTextState();
                        textState.setType3HorizontalDisplacement(new Point.Float(x, y));
                    }

                    // Close and stroke the path. This operator has the same effect
                    // as the sequence h S.
                    else if (tok.equals(PdfOps.s_TOKEN)) {
//                        collectTokenFrequency(PdfOps.s_TOKEN);
                        if (geometricPath != null) {
                            geometricPath.closePath();
                            commonStroke(shapes, geometricPath);
                            geometricPath = null;
                        }
                    }

                    // Set the stroking color space to DeviceGray (or the DefaultGray color
                    // space; see “Default Color Spaces” ) and set the gray level to use for
                    // stroking operations. gray is a number between 0.0 (black)
                    // and 1.0 (white).
                    else if (tok.equals(PdfOps.G_TOKEN)) {
                        consume_G(graphicState, stack, library, resources);
                    }

                    // Close, fill, and then stroke the path, using the even-odd
                    // rule to determine the region to fill. This operator has the
                    // same effect as the sequence h B*. See also “Special
                    // Path-Painting Considerations”
                    else if (tok.equals(PdfOps.b_STAR_TOKEN)) {
//                        collectTokenFrequency(PdfOps.b_STAR_TOKEN);
                        if (geometricPath != null) {
                            geometricPath.setWindingRule(GeneralPath.WIND_EVEN_ODD);
                            geometricPath.closePath();
                            commonStroke(shapes, geometricPath);
                            commonFill(shapes, geometricPath);
                        }
                        geometricPath = null;
                    }

                    // Set the stroking color space to DeviceRGB (or the DefaultRGB color
                    // space; see “Default Color Spaces” on page 227) and set the color to
                    // use for stroking operations. Each operand must be a number between
                    // 0.0 (minimum intensity) and 1.0 (maximum intensity).
                    else if (tok.equals(PdfOps.RG_TOKEN)) { // Stroke Color RGB
                        consume_RG(graphicState, stack, library, resources);
                    }

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
                    //     specified by the space’s Range entry, in which case the nearest
                    //     valid value is substituted.</li>
                    // <li>In an Indexed color space, the initial color value is 0. </li>
                    // <li>In a Separation or DeviceN color space, the initial tint value is
                    //     1.0 for all colorants. </li>
                    // <li>In a Pattern color space, the initial color is a pattern object
                    //     that causes nothing to be painted. </li>
                    else if (tok.equals(PdfOps.CS_TOKEN)) {
                        consume_CS(graphicState, stack, resources);
                    } else if (tok.equals(PdfOps.d1_TOKEN)) {
//                        collectTokenFrequency(PdfOps.d1_TOKEN);
                        // save the stack
                        graphicState = graphicState.save();
                        // need two pops to get  Wx and Wy data
                        float x2 = ((Number) stack.pop()).floatValue();
                        float y2 = ((Number) stack.pop()).floatValue();
                        float x1 = ((Number) stack.pop()).floatValue();
                        float y1 = ((Number) stack.pop()).floatValue();
                        float y = ((Number) stack.pop()).floatValue();
                        float x = ((Number) stack.pop()).floatValue();
                        TextState textState = graphicState.getTextState();
                        textState.setType3HorizontalDisplacement(
                                new Point2D.Float(x, y));
                        textState.setType3BBox(new PRectangle(
                                new Point2D.Float(x1, y1),
                                new Point2D.Float(x2, y2)));
                    }

                    // Fill and then stroke the path, using the even-odd rule to
                    // determine the region to fill. This operator produces the same
                    // result as B, except that the path is filled as if with f*
                    // instead of f. See also “Special Path-Painting Considerations”
                    else if (tok.equals(PdfOps.B_STAR_TOKEN)) {
//                        collectTokenFrequency(PdfOps.B_STAR_TOKEN);
                        if (geometricPath != null) {
                            geometricPath.setWindingRule(GeneralPath.WIND_EVEN_ODD);
                            commonStroke(shapes, geometricPath);
                            commonFill(shapes, geometricPath);
                        }
                        geometricPath = null;
                    }

                    // Begin a marked-content sequence terminated by a balancing EMC
                    // operator.tag is a name object indicating the role or
                    // significance of the sequence.
                    else if (tok.equals(PdfOps.BMC_TOKEN)) {
//                        collectTokenFrequency(PdfOps.BMC_TOKEN);
                        stack.pop();
                    }

                    // Begin an inline image object
                    else if (tok.equals(PdfOps.BI_TOKEN)) {
//                        collectTokenFrequency(PdfOps.BI_TOKEN);
                        // start parsing image object, which leads to ID and EI
                        // tokends.
                        //    ID - Begin in the image data for an inline image object
                        //    EI - End an inline image object
                        parseInlineImage(parser, shapes);
                    }

                    // Begin a compatibility section. Unrecognized operators
                    // (along with their operands) will be ignored without error
                    // until the balancing EX operator is encountered.
                    else if (tok.equals(PdfOps.BX_TOKEN)) {
//                        collectTokenFrequency(PdfOps.BX_TOKEN);
                    }
                    // End a compatibility section begun by a balancing BX operator.
                    else if (tok.equals(PdfOps.EX_TOKEN)) {
//                        collectTokenFrequency(PdfOps.EX_TOKEN);
                    }

                    // Modify the current clipping path by intersecting it with the
                    // current path, using the even-odd rule to determine which
                    // regions lie inside the clipping path.
                    else if (tok.equals(PdfOps.W_STAR_TOKEN)) {
                        if (geometricPath != null) {
                            geometricPath.setWindingRule(GeneralPath.WIND_EVEN_ODD);
                            geometricPath.closePath();
                            graphicState.setClip(geometricPath);
                        }
                    }

                    /**
                     * Single marked-content point
                     */
                    // Designate a marked-content point with an associated property
                    // list. tag is a name object indicating the role or significance
                    // of the point; properties is either an inline dictionary
                    // containing the property list or a name object associated with
                    // it in the Properties subdictionary of the current resource
                    // dictionary.
                    else if (tok.equals(PdfOps.DP_TOKEN)) {
//                        collectTokenFrequency(PdfOps.DP_TOKEN);
                        stack.pop(); // properties
                        stack.pop(); // name
                    }
                    // Designate a marked-content point. tag is a name object
                    // indicating the role or significance of the point.
                    else if (tok.equals(PdfOps.MP_TOKEN)) {
//                        collectTokenFrequency(PdfOps.MP_TOKEN);
                        stack.pop();
                    }

                    // shading operator.
                    else if (tok.equals(PdfOps.sh_TOKEN)) {
//                        collectTokenFrequency(PdfOps.sh_TOKEN);
                        Object o = stack.peek();
                        // if a name then we are dealing with a pattern.
                        if (o instanceof Name) {
                            Name patternName = (Name) stack.pop();
                            Pattern pattern = resources.getShading(patternName.toString());
                            if (pattern != null) {
                                pattern.init();
                                // we paint the shape and color shadig as defined
                                // by the pattern dictionary and respect the current clip
                                setAlpha(shapes, graphicState.getFillAlpha());
                                shapes.add(pattern.getPaint());
                                shapes.add(graphicState.getClip());
                                shapes.addFillCommand();

                            }
                        }
                    }

                    /**
                     * We've seen a couple cases when the text state parameters are written
                     * outside of text blocks, this should cover these cases.
                     */
                    // Character Spacing
                    else if (tok.equals(PdfOps.Tc_TOKEN)) {
                        consume_Tc(graphicState, stack);
                    }
                    // Word spacing
                    else if (tok.equals(PdfOps.Tw_TOKEN)) {
                        consume_Tw(graphicState, stack);
                    }
                    // Text leading
                    else if (tok.equals(PdfOps.TL_TOKEN)) {
                        consume_TL(graphicState, stack);
                    }
                    // Rendering mode
                    else if (tok.equals(PdfOps.Tr_TOKEN)) {
                        consume_Tr(graphicState, stack);
                    }
                    // Horizontal scaling
                    else if (tok.equals(PdfOps.Tz_TOKEN)) {
                        consume_Tz(graphicState, stack);
                    }
                    // Text rise
                    else if (tok.equals(PdfOps.Ts_TOKEN)) {
                        consume_Ts(graphicState, stack);
                    }
                }
            }
        }
        catch (IOException e) {
            // eat the result as it a normal occurance
            logger.finer("End of Content Stream");
        }
        finally {
            // End of stream set alpha state back to 1.0f, so that other
            // streams aren't applied an incorrect alpha value.
            setAlpha(shapes, 1.0f);
        }
//        long endTime = System.currentTimeMillis();
//        System.out.println("Paring Duration " + (endTime - startTime));
//        printTokenFrequency();

        // Print off anything left on the stack, any "Stack" traces should
        // indicate a parsing problem or a not supported operand
        while (!stack.isEmpty()) {
            String tmp = stack.pop().toString();
            if (logger.isLoggable(Level.FINE)) {
                logger.fine("STACK=" + tmp);
            }
        }
        return shapes;
    }

    /**
     * Specialized method for extracting text from documents.
     *
     * @param source content stream source.
     * @return vector where each entry is the text extracted from a text block.
     */
    public Vector<StringBuffer> parseTextBlocks(InputStream source) {
        Vector<StringBuffer> extractedText = new Vector<StringBuffer>(15);
        // great a parser to get tokens for stream
        Parser parser = new Parser(source);
        Shapes shapes = new Shapes();

        if (graphicState == null) {
            graphicState = new GraphicsState(shapes);
        }

//        long startTime = System.currentTimeMillis();
        try {

            // loop through each token returned form the parser
            Object tok = parser.getStreamObject();
            Stack<Object> stack = new Stack<Object>();
            while (tok != null) {

                // add any names and numbers and every thing else on the
                // stack for future reference
                if (tok instanceof String) {

                    if (tok.equals(PdfOps.BT_TOKEN)) {
                        // start parseText, which parses until ET is reached
                        parseText(parser, shapes, true, extractedText);
                        // This is the end of a text block, for text extraction purposes it is marked
                        textBlockIndex++;
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
                } else {
                    stack.push(tok);
                }
                tok = parser.getStreamObject();
            }
            // clear our temporary stack. 
            stack.clear();
        } catch (IOException e) {
            // eat the result as it a normal occurrence
            logger.finer("End of Content Stream");
        }
//        long endTime = System.currentTimeMillis();
//        System.out.println("Extraction Duration " + (endTime - startTime));
        shapes.dispose();
        return extractedText;
    }

    /**
     * Adds extracted text to the this object for central storage.
     *
     * @param s text extract in one TJ or Tj operation from the content parser
     */
    public final void addExtractedText(Vector<StringBuffer> textObjects,
                                       StringBuffer s) {

        // check if the the vector is empty
        if (textObjects.isEmpty()) {
            // removed, as an empty vector that has insertElement called on
            // it will produce an index out of found exception.
            //textObjects.insertElementAt(s, textBlockIndex);
            textObjects.add(s);
        } else {
            if (textBlockIndex < textObjects.size()) {
                StringBuffer tmp = textObjects.elementAt(textBlockIndex);
                tmp.append(s.toString());
                textObjects.setElementAt(tmp, textBlockIndex);
            } else {
                textObjects.add(s);
            }

        }
    }

    /**
     * Parses Text found with in a BT block.
     *
     * @param parser        parser containging BT tokens
     * @param shapes        container of all shapes for the page content being parsed
     * @param extractText   indicates if text extraction algorithms should be used.
     * @param extractedText if text extraction is used this vector collects the
     *                      textdata
     * @throws java.io.IOException end of content stream is found
     */
    void parseText(Parser parser, Shapes shapes,
                   boolean extractText, Vector<StringBuffer> extractedText)
            throws IOException {
        Object nextToken;
        Stack stack = new Stack();
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

        // start parsing of the BT block
        nextToken = parser.getStreamObject();
        while (!nextToken.equals("ET")) { // ET - end text object
            // add names to the stack, save for later parsing, colour state
            // and graphics state (includes font).

            if (nextToken instanceof String) {

                // Normal text token, string, hex
                if (nextToken.equals(PdfOps.Tj_TOKEN)) {
//                    collectTokenFrequency(PdfOps.Tj_TOKEN);
                    Object tjValue = stack.pop();
                    StringObject stringObject;
                    TextState textState;
                    if (tjValue instanceof StringObject) {
                        stringObject = (StringObject) tjValue;
                        textState = graphicState.getTextState();

                        // apply text scaling
                        applyTextScaling(graphicState);

                        Point2D.Float d = (Point2D.Float) drawString(
                                stringObject.getLiteralStringBuffer(
                                        textState.font.getSubTypeFormat(),
                                        textState.font.getFont()),
                                advance,
                                previousAdvance, 0,
                                graphicState.getTextState(),
                                shapes, extractText, extractedText);
                        graphicState.translate(d.x, 0);
                        shift += d.x;
                        previousAdvance = 0;
                        advance.setLocation(0, 0);
                    }
                    if (extractText) {
                        addExtractedText(extractedText, new StringBuffer(" "));
                    }
                }

                // Character Spacing
                else if (nextToken.equals(PdfOps.Tc_TOKEN)) {
//                    collectTokenFrequency(PdfOps.Tc_TOKEN);
                    graphicState.getTextState().cspace = ((Number) stack.pop()).floatValue();
                }

                // Word spacing
                else if (nextToken.equals(PdfOps.Tw_TOKEN)) {
//                    collectTokenFrequency(PdfOps.Tw_TOKEN);
                    graphicState.getTextState().wspace = ((Number) stack.pop()).floatValue();
                }

                // move to the start of he next line, offset from the start of the
                // current line by (tx,ty)*tx
                else if (nextToken.equals(PdfOps.Td_TOKEN)) {
//                    collectTokenFrequency(PdfOps.Td_TOKEN);
                    float y = ((Number) stack.pop()).floatValue();
                    float x = ((Number) stack.pop()).floatValue();
                    graphicState.translate(-shift, 0);
                    shift = 0;
                    previousAdvance = 0;
                    advance.setLocation(0, 0);
                    graphicState.translate(x, -y);
                    // add a new line for text extraction
                    if (extractText) {
                        addExtractedText(extractedText, new StringBuffer("\n"));
                    }
                }

                /**
                 * Tranformation matrix
                 * tm =   |f1 f2 0|
                 *        |f3 f4 0|
                 *        |f5 f6 0|
                 */
                else if (nextToken.equals(PdfOps.Tm_TOKEN)) {
//                    collectTokenFrequency(PdfOps.Tm_TOKEN);
                    shift = 0;
                    previousAdvance = 0;
                    advance.setLocation(0, 0);
                    float f6 = ((Number) stack.pop()).floatValue();
                    float f5 = ((Number) stack.pop()).floatValue();
                    float f4 = ((Number) stack.pop()).floatValue();
                    float f3 = ((Number) stack.pop()).floatValue();
                    float f2 = ((Number) stack.pop()).floatValue();
                    float f1 = ((Number) stack.pop()).floatValue();
                    AffineTransform af = new AffineTransform(textBlockBase);
                    graphicState.getTextState().tmatrix = new AffineTransform(f1, f2, f3, f4, f5, f6);
                    af.concatenate(graphicState.getTextState().tmatrix);
                    graphicState.set(af);
                    graphicState.scale(1, -1);
                    // add a new line for text extraction
//                    if (extractText){
//                        if (graphicState.getTextState().tmatrix.getTranslateY() != textBlockBase.getTranslateY() )
//                        addExtractedText(extractedText, new StringBuffer("\n"));
//                    }
                }

                // Font selection
                else if (nextToken.equals(PdfOps.Tf_TOKEN)) {
                    consume_Tf(graphicState, stack, resources);
                }

                // TJ marks a vector, where.......
                else if (nextToken.equals(PdfOps.TJ_TOKEN)) {
//                    collectTokenFrequency(PdfOps.TJ_TOKEN);

                    // apply text scaling
                    applyTextScaling(graphicState);

                    Vector v = (Vector) stack.pop();
                    Object currentObject;
                    StringObject stringObject;
                    TextState textState;
                    Number f;
                    float lastTextAdvance = previousAdvance;
                    for (Enumeration e = v.elements(); e.hasMoreElements();) {
                        currentObject = e.nextElement();
                        if (currentObject instanceof StringObject) {
                            stringObject = (StringObject) currentObject;
                            textState = graphicState.getTextState();
                            advance = (Point2D.Float) drawString(
                                    stringObject.getLiteralStringBuffer(
                                            textState.font.getSubTypeFormat(),
                                            textState.font.getFont()),
                                    advance, previousAdvance, lastTextAdvance,
                                    graphicState.getTextState(), shapes,
                                    extractText, extractedText);
                            // update the text advance
                            lastTextAdvance = advance.x;
                        } else if (currentObject instanceof Number) {
                            f = (Number) currentObject;
                            advance.x -=
                                    f.floatValue() * graphicState.getTextState().currentfont.getSize()
                                            / 1000.0;
                            // try and sense when a TJ offect will result in a space
                            // pretty fuzzy logic, will refine over time.
                            if (extractText) {
                                float distance = advance.x - lastTextAdvance;
                                if (distance > 0.01) {
                                    addExtractedText(extractedText, new StringBuffer(" "));
                                }
                            }
                        }
                        previousAdvance = advance.x;
                    }
                    // add a space for text extraction
                    if (extractText) {
                        addExtractedText(extractedText, new StringBuffer(" "));
                    }
                }

                // Move to the start of the next line, offset from the start of the
                // current line by (tx,ty)
                else if (nextToken.equals(PdfOps.TD_TOKEN)) {
//                    collectTokenFrequency(PdfOps.TD_TOKEN);
                    float y = ((Number) stack.pop()).floatValue();
                    float x = ((Number) stack.pop()).floatValue();
                    graphicState.translate(-shift, 0);
                    shift = 0;
                    previousAdvance = 0;
                    advance.setLocation(0, 0);
                    graphicState.translate(x, -y);
                    graphicState.getTextState().leading = -y;
                    if (extractText) {
                        addExtractedText(extractedText, new StringBuffer("\n"));
                    }
                }

                // Text leading
                else if (nextToken.equals(PdfOps.TL_TOKEN)) {
//                    collectTokenFrequency(PdfOps.TL_TOKEN);
                    graphicState.getTextState().leading = ((Number) stack.pop()).floatValue();
                }

                // Saves Graphics State, should copy the entire  graphics state onto
                // the graphicsState object's stack
                else if (nextToken.equals(PdfOps.q_TOKEN)) {
                    graphicState = consume_q(graphicState);
                }
                // Restore Graphics State, should restore teh entire graphics state
                // to its former value by popping it from the stack
                else if (nextToken.equals(PdfOps.Q_TOKEN)) {
                    graphicState = consume_Q(graphicState, shapes);
                }

                // Modify the current transformation matrix (CTM) by concatenating the
                // specified matrix
                else if (nextToken.equals(PdfOps.cm_TOKEN)) {
                    consume_cm(graphicState, stack, inTextBlock, textBlockBase);
                }

                // Move to the start of the next line
                else if (nextToken.equals(PdfOps.T_STAR_TOKEN)) {
//                    collectTokenFrequency(PdfOps.T_STAR_TOKEN);
                    graphicState.translate(-shift, 0);
                    shift = 0;
                    previousAdvance = 0;
                    advance.setLocation(0, 0);
                    graphicState.translate(0, graphicState.getTextState().leading);
                    if (extractText) {
                        addExtractedText(extractedText, new StringBuffer("\n"));
                    }
                } else if (nextToken.equals(PdfOps.BDC_TOKEN)) {
//                    collectTokenFrequency(PdfOps.BDC_TOKEN);
                    stack.pop();
                    stack.pop();
                } else if (nextToken.equals(PdfOps.EMC_TOKEN)) {
//                    collectTokenFrequency(PdfOps.EMC_TOKEN);
                }

                // Sets the specifed parameters in the graphics state.  The gs operand
                // points to a name resource which should be a an ExtGState object.
                // The graphics state paramaters in the ExtGState must be concatenated
                // with the the current graphics state.
                else if (nextToken.equals(PdfOps.gs_TOKEN)) {
                    consume_gs(graphicState, stack, resources);
                }

                // Set the line width in the graphics state
                else if (nextToken.equals(PdfOps.w_TOKEN) ||
                        nextToken.equals(PdfOps.LW_TOKEN)) {
                    consume_w(graphicState, stack, shapes);
                }

                // Fill Color with ColorSpace
                else if (nextToken.equals(PdfOps.sc_TOKEN) ||
                        nextToken.equals(PdfOps.scn_TOKEN)) {
                    consume_sc(graphicState, stack, library, resources);
                }

                // Same as K, but for nonstroking operations.
                else if (nextToken.equals(PdfOps.k_TOKEN)) { // Fill Color CMYK
                    consume_k(graphicState, stack, library, resources);
                }

                // Same as g but for none stroking operations
                else if (nextToken.equals(PdfOps.g_TOKEN)) {
                    consume_g(graphicState, stack, library, resources);
                }

                // Sets the flatness tolerance in the graphics state, NOT SUPPORTED
                // flatness is a number in the range 0 to 100, a value of 0 specifies
                // the default tolerance
                else if (nextToken.equals(PdfOps.i_TOKEN)) {
                    consume_i(stack);
                }

                // Miter Limit
                else if (nextToken.equals(PdfOps.M_TOKEN)) {
                    consume_M(graphicState, stack, shapes);
                }

                // Set the line cap style of the graphic state, related to Line Join
                // style
                else if (nextToken.equals(PdfOps.J_TOKEN)) {
                    consume_J(graphicState, stack, shapes);
                }

                // Same as RG, but for nonstroking operations.
                else if (nextToken.equals(PdfOps.rg_TOKEN)) { // Fill Color RGB
                    consume_rg(graphicState, stack, library, resources);
                }

                // Sets the line dash pattern in the graphics state. A normal line
                // is [] 0.  See Graphics State -> Line dash patter for more information
                // in the PDF Reference.  Java 2d uses the same notation so there
                // is not much work to be done other then parsing the data.
                else if (nextToken.equals(PdfOps.d_TOKEN)) {
                    consume_d(graphicState, stack, shapes);
                }

                // Sets the line dash pattern in the graphics state. A normal line
                // is [] 0.  See Graphics State -> Line dash patter for more information
                // in the PDF Reference.  Java 2d uses the same notation so there
                // is not much work to be done other then parsing the data.
                else if (nextToken.equals(PdfOps.d_TOKEN)) {
                    consume_d(graphicState, stack, shapes);
                }

                // Set the line join style in the graphics state
                else if (nextToken.equals(PdfOps.j_TOKEN)) {
                    consume_j(graphicState, stack, shapes);
                }

                // Same as CS, but for nonstroking operations.
                else if (nextToken.equals(PdfOps.cs_TOKEN)) {
                    consume_cs(graphicState, stack, resources);
                }

                // Set the color rendering intent in the graphics state
                else if (nextToken.equals("ri")) {
//                    collectTokenFrequency(PdfOps.ri_TOKEN);
                    stack.pop();
                }

                // Set the color to use for stroking operations in a device, CIE-based
                // (other than ICCBased), or Indexed color space. The number of operands
                // required and their interpretation depends on the current stroking color space:
                //   • For DeviceGray, CalGray, and Indexed color spaces, one operand
                //     is required (n = 1).
                //   • For DeviceRGB, CalRGB, and Lab color spaces, three operands are
                //     required (n = 3).
                //   • For DeviceCMYK, four operands are required (n = 4).
                else if (nextToken.equals(PdfOps.SC_TOKEN) ||
                        nextToken.equals(PdfOps.SCN_TOKEN)) { // Stroke Color with ColorSpace
                    consume_SC(graphicState, stack, library, resources);
                }

                // Set the stroking color space to DeviceCMYK (or the DefaultCMYK color
                // space; see “Default Color Spaces” on page 227) and set the color to
                // use for stroking operations. Each operand must be a number between
                // 0.0 (zero concentration) and 1.0 (maximum concentration). The
                // behavior of this operator is affected by the overprint mode
                // (see Section 4.5.6, “Overprint Control”).
                else if (nextToken.equals(PdfOps.K_TOKEN)) { // Stroke Color CMYK
                    consume_K(graphicState, stack, library, resources);
                }

                // Set the stroking color space to DeviceGray (or the DefaultGray color
                // space; see “Default Color Spaces” ) and set the gray level to use for
                // stroking operations. gray is a number between 0.0 (black)
                // and 1.0 (white).
                else if (nextToken.equals(PdfOps.G_TOKEN)) {
                    consume_G(graphicState, stack, library, resources);
                }

                // Set the stroking color space to DeviceRGB (or the DefaultRGB color
                // space; see “Default Color Spaces” on page 227) and set the color to
                // use for stroking operations. Each operand must be a number between
                // 0.0 (minimum intensity) and 1.0 (maximum intensity).
                else if (nextToken.equals(PdfOps.RG_TOKEN)) { // Stroke Color RGB
                    consume_RG(graphicState, stack, library, resources);
                } else if (nextToken.equals(PdfOps.CS_TOKEN)) {
                    consume_CS(graphicState, stack, resources);
                }

                // Rendering mode
                else if (nextToken.equals(PdfOps.Tr_TOKEN)) {
//                    collectTokenFrequency(PdfOps.Tr_TOKEN);
                    graphicState.getTextState().rmode = (int) ((Number) stack.pop()).floatValue();
                }

                // Horizontal scalling
                else if (nextToken.equals(PdfOps.Tz_TOKEN)) {
//                    collectTokenFrequency(PdfOps.Tz_TOKEN);
                    consume_Tz(graphicState, stack);
                }

                // Text rise
                else if (nextToken.equals(PdfOps.Ts_TOKEN)) {
//                    collectTokenFrequency(PdfOps.Ts_TOKEN);
                    graphicState.getTextState().trise = ((Number) stack.pop()).floatValue();
                }

                /**
                 * Begin a compatibility section. Unrecognized operators (along with
                 * their operands) will be ignored without error until the balancing
                 * EX operator is encountered.
                 */
                else if (nextToken.equals(PdfOps.BX_TOKEN)) {
//                    collectTokenFrequency(PdfOps.BX_TOKEN);
                }
//                 End a compatibility section begun by a balancing BX operator.
                else if (nextToken.equals(PdfOps.EX_TOKEN)) {
//                    collectTokenFrequency(PdfOps.EX_TOKEN);
                }
                // Move to the next line and show a text string.
                else if (nextToken.equals(PdfOps.SINGLE_QUOTE_TOKEN)) {
//                    collectTokenFrequency(PdfOps.SINGLE_QUOTE_TOKEN);
                    graphicState.translate(-shift, graphicState.getTextState().leading);

                    // apply text scaling
                    applyTextScaling(graphicState);

                    shift = 0;
                    previousAdvance = 0;
                    advance.setLocation(0, 0);
                    StringObject stringObject = (StringObject) stack.pop();

                    TextState textState = graphicState.getTextState();

                    Point2D.Float d = (Point2D.Float) drawString(
                            stringObject.getLiteralStringBuffer(
                                    textState.font.getSubTypeFormat(),
                                    textState.font.getFont()),
                            new Point2D.Float(0, 0), 0, 0, graphicState.getTextState(),
                            shapes, extractText, extractedText);
                    graphicState.translate(d.x, 0);
                    shift += d.x;
                    if (extractText) {
                        addExtractedText(extractedText, new StringBuffer("\n"));
                    }
                }
                /**
                 * Move to the next line and show a text string, using aw as the
                 * word spacing and ac as the character spacing (setting the
                 * corresponding parameters in the text state). aw and ac are
                 * numbers expressed in unscaled text space units.
                 */
                else if (nextToken.equals(PdfOps.DOUBLE_QUOTE__TOKEN)) {
//                    collectTokenFrequency(PdfOps.DOUBLE_QUOTE__TOKEN);
                    StringObject stringObject = (StringObject) stack.pop();
                    graphicState.getTextState().cspace = ((Number) stack.pop()).floatValue();
                    graphicState.getTextState().wspace = ((Number) stack.pop()).floatValue();
                    graphicState.translate(-shift, graphicState.getTextState().leading);

                    // apply text scaling
                    applyTextScaling(graphicState);

                    shift = 0;
                    previousAdvance = 0;
                    advance.setLocation(0, 0);
                    TextState textState = graphicState.getTextState();

                    Point2D.Float d = (Point2D.Float) drawString(
                            stringObject.getLiteralStringBuffer(
                                    textState.font.getSubTypeFormat(),
                                    textState.font.getFont()),
                            new Point2D.Float(0, 0), 0, 0, graphicState.getTextState(),
                            shapes, extractText, extractedText);
                    graphicState.translate(d.x, 0);
                    shift += d.x;
                    if (extractText) {
                        addExtractedText(extractedText, new StringBuffer("\n"));
                    }
                }


            }
            // push everything else on the stack for consumptions
            else {
                stack.push(nextToken);
            }

            nextToken = parser.getStreamObject();
        }
        // add a cr when we get to the end of a text block
        if (extractText) {
            addExtractedText(extractedText, new StringBuffer("\n\n"));
        }

        // get rid of the rest
        while (!stack.isEmpty()) {
            String tmp = stack.pop().toString();
            if (logger.isLoggable(Level.FINE)) {
                logger.warning("Text=" + tmp);
            }
        }
        graphicState.set(textBlockBase);
        inTextBlock = false;
    }

    void parseInlineImage(Parser p, Shapes shapes) throws IOException {
        try {
            //int width = 0, height = 0, bitspercomponent = 0;
            // boolean imageMask = false; // from old pdfgo never used
            // PColorSpace cs = null; // from old pdfgo never used

            Object tok;
            Hashtable iih = new Hashtable();
            tok = p.getStreamObject();
            while (!tok.equals("ID")) {
                if (tok.equals("BPC")) {
                    tok = new Name("BitsPerComponent");
                } else if (tok.equals("CS")) {
                    tok = new Name("ColorSpace");
                } else if (tok.equals("D")) {
                    tok = new Name("Decode");
                } else if (tok.equals("DP")) {
                    tok = new Name("DecodeParms");
                } else if (tok.equals("F")) {
                    tok = new Name("Filter");
                } else if (tok.equals("H")) {
                    tok = new Name("Height");
                } else if (tok.equals("IM")) {
                    tok = new Name("ImageMask");
                } else if (tok.equals("I")) {
                    tok = new Name("Indexed");
                } else if (tok.equals("W")) {
                    tok = new Name("Width");
                }
                Object tok1 = p.getStreamObject();
                //System.err.println(tok+" - "+tok1);
                iih.put(tok, tok1);
                tok = p.getStreamObject();
            }
            // For inline images in content streams, we have to use
            //   a byte[], instead of going back to the original file,
            //   to reget the image data, because the inline image is
            //   only a small part of a content stream, which is also
            //   filtered, and potentially concatenated with other
            //   content streams.
            // Long story short: it's too hard to reget from PDF file
            // Now, since non-inline-image streams can go back to the
            //   file, we have to fake it as coming from the file ...
            ByteArrayOutputStream buf = new ByteArrayOutputStream(4096);
            tok = p.peek2();
            boolean ateEI = false;
            while (tok != null && !tok.equals(" EI")) {
                ateEI = p.readLineForInlineImage(buf);
                if (ateEI)
                    break;
                tok = p.peek2();
            }
            if (!ateEI) {
                // get rid of trash...
                p.getToken();
            }
            buf.flush();
            buf.close();
            byte[] data = buf.toByteArray();
            SeekableByteArrayInputStream sbais =
                    new SeekableByteArrayInputStream(data);
            SeekableInputConstrainedWrapper streamInputWrapper =
                    new SeekableInputConstrainedWrapper(sbais, 0L, data.length, true);
            Stream st = new Stream(library, iih, streamInputWrapper);
            //System.out.println("----------> ContentParser creating image from stream");
            BufferedImage im = st.getImage(graphicState.getFillColor(), resources, true);
            st.dispose(false);
            AffineTransform af = new AffineTransform(graphicState.getCTM());
            graphicState.scale(1, -1);
            graphicState.translate(0, -1);
            shapes.add(im);
            graphicState.set(af);
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            logger.log(Level.FINE, "Error parsing inline image.", e);
        }
    }

    private static void consume_G(GraphicsState graphicState, Stack stack,
                                  Library library, Resources resources) {
//        collectTokenFrequency(PdfOps.G_TOKEN);
        float gray = ((Number) stack.pop()).floatValue();
        // Stroke Color Gray
        graphicState.setStrokeColorSpace(
                PColorSpace.getColorSpace(library, new Name("DeviceGray")));
        graphicState.setStrokeColor(new Color(gray, gray, gray));
    }

    private static void consume_g(GraphicsState graphicState, Stack stack,
                                  Library library, Resources resources) {
//        collectTokenFrequency(PdfOps.g_TOKEN);
        float gray = ((Number) stack.pop()).floatValue();
        // Fill Color Gray
        graphicState.setFillColorSpace(
                PColorSpace.getColorSpace(library, new Name("DeviceGray")));
        graphicState.setFillColor(new Color(gray, gray, gray));
    }

    private static void consume_RG(GraphicsState graphicState, Stack stack,
                                   Library library, Resources resources) {
//        collectTokenFrequency(PdfOps.RG_TOKEN);
        float b = ((Number) stack.pop()).floatValue();
        float gg = ((Number) stack.pop()).floatValue();
        float r = ((Number) stack.pop()).floatValue();
        b = Math.max(0.0f, Math.min(1.0f, b));
        gg = Math.max(0.0f, Math.min(1.0f, gg));
        r = Math.max(0.0f, Math.min(1.0f, r));
        // set stoke colour
        graphicState.setStrokeColorSpace(
                PColorSpace.getColorSpace(library, new Name("DeviceRGB")));
        graphicState.setStrokeColor(new Color(r, gg, b));
    }

    private static void consume_rg(GraphicsState graphicState, Stack stack,
                                   Library library, Resources resources) {
//        collectTokenFrequency(PdfOps.rg_TOKEN);
        float b = ((Number) stack.pop()).floatValue();
        float gg = ((Number) stack.pop()).floatValue();
        float r = ((Number) stack.pop()).floatValue();
        b = Math.max(0.0f, Math.min(1.0f, b));
        gg = Math.max(0.0f, Math.min(1.0f, gg));
        r = Math.max(0.0f, Math.min(1.0f, r));
        // set fill colour
        graphicState.setFillColorSpace(
                PColorSpace.getColorSpace(library, new Name("DeviceRGB")));
        graphicState.setFillColor(new Color(r, gg, b));
    }

    private static void consume_K(GraphicsState graphicState, Stack stack,
                                  Library library, Resources resources) {
//        collectTokenFrequency(PdfOps.K_TOKEN);
        float k = ((Number) stack.pop()).floatValue();
        float y = ((Number) stack.pop()).floatValue();
        float m = ((Number) stack.pop()).floatValue();
        float c = ((Number) stack.pop()).floatValue();
//        float r = 0, gg = 0, b = 0;
//        if ((c + k) <= 1.0)
//            r = 1 - (c + k);
//        if ((m + k) <= 1.0)
//            gg = 1 - (m + k);
//        if ((y + k) <= 1.0)
//            b = 1 - (y + k);

        PColorSpace pColorSpace =
                PColorSpace.getColorSpace(library, new Name("DeviceCMYK"));
        // set stroke colour
        graphicState.setStrokeColorSpace(pColorSpace);
//        graphicState.setStrokeColor(new Color(r, gg, b));
        graphicState.setStrokeColor(pColorSpace.getColor(PColorSpace.reverse(new float[]{c, m, y, k})));
    }

    private static void consume_k(GraphicsState graphicState, Stack stack,
                                  Library library, Resources resources) {
//        collectTokenFrequency(PdfOps.k_TOKEN);
        float k = ((Number) stack.pop()).floatValue();
        float y = ((Number) stack.pop()).floatValue();
        float m = ((Number) stack.pop()).floatValue();
        float c = ((Number) stack.pop()).floatValue();
//        float r = 0, gg = 0, b = 0;
//        if ((c + k) <= 1.0)
//            r = 1 - (c + k);
//        if ((m + k) <= 1.0)
//            gg = 1 - (m + k);
//        if ((y + k) <= 1.0)
//            b = 1 - (y + k);

        // build a colour space.
        PColorSpace pColorSpace =
                PColorSpace.getColorSpace(library, new Name("DeviceCMYK"));
        // set fill colour
        graphicState.setFillColorSpace(pColorSpace);
//        graphicState.setFillColor( new Color(r, gg, b));
        graphicState.setFillColor(pColorSpace.getColor(PColorSpace.reverse(new float[]{c, m, y, k})));
    }

    private static void consume_CS(GraphicsState graphicState, Stack stack, Resources resources) {
//        collectTokenFrequency(PdfOps.CS_TOKEN);
        Name n = (Name) stack.pop();
        // Fill Color ColorSpace, resources call uses factory call to PColorSpace.getColorSpace
        // which returns an colour space including a pattern
        graphicState.setStrokeColorSpace(resources.getColorSpace(n));
    }

    private static void consume_cs(GraphicsState graphicState, Stack stack, Resources resources) {
//        collectTokenFrequency(PdfOps.cs_TOKEN);
        Name n = (Name) stack.pop();
        // Fill Color ColorSpace, resources call uses factory call to PColorSpace.getColorSpace
        // which returns an colour space including a pattern
        graphicState.setFillColorSpace(resources.getColorSpace(n));
    }

    private static void consume_SC(GraphicsState graphicState, Stack stack,
                                   Library library, Resources resources) {
//        collectTokenFrequency(PdfOps.SC_TOKEN);
//        collectTokenFrequency(PdfOps.SCN_TOKEN);
        Object o = stack.peek();
        // if a name then we are dealing with a pattern
        if (o instanceof Name) {
            Name patternName = (Name) stack.pop();
            Pattern pattern = resources.getPattern(patternName.toString());
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
//        collectTokenFrequency(PdfOps.sc_TOKEN);
//        collectTokenFrequency(PdfOps.scn_TOKEN);
        Object o = stack.peek();
        // if a name then we are dealing with a pattern.
        if (o instanceof Name) {
            Name patternName = (Name) stack.pop();
            Pattern pattern = resources.getPattern(patternName.toString());
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
                }
            }
        } else if (o instanceof Number) {
            // some pdfs encoding do not explicitly change the default colour
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
//        collectTokenFrequency(PdfOps.q_TOKEN);
        return graphicState.save();

    }

    private static GraphicsState consume_Q(GraphicsState graphicState, Shapes shapes) {
//        collectTokenFrequency(PdfOps.Q_TOKEN);
        GraphicsState gs1 = graphicState.restore();
        // point returned stack
        if (gs1 != null) {
            graphicState = gs1;
        }
        // otherwise start a new stack
        else {
            graphicState = new GraphicsState(shapes);
            graphicState.set(new AffineTransform());
            shapes.addNoClipCommand();
        }

        return graphicState;
    }

    private static void consume_cm(GraphicsState graphicState, Stack stack,
                                   boolean inTextBlock, AffineTransform textBlockBase) {
//        collectTokenFrequency(PdfOps.cm_TOKEN);
        float f = ((Number) stack.pop()).floatValue();
        float e = ((Number) stack.pop()).floatValue();
        float d = ((Number) stack.pop()).floatValue();
        float c = ((Number) stack.pop()).floatValue();
        float b = ((Number) stack.pop()).floatValue();
        float a = ((Number) stack.pop()).floatValue();
        if (!inTextBlock) {
            // get the current CTM
            AffineTransform af = new AffineTransform(graphicState.getCTM());
            // do the matrix concatenation math
            af.concatenate(new AffineTransform(a, b, c, d, e, f));
            // add the transformation to the graphics state
            graphicState.set(af);
            // update the clip, translate by this CM
            graphicState.updateClipCM(new AffineTransform(a, b, c, d, e, f));
        } else {
            // update the textBlockBase with the cm matrix
            textBlockBase.concatenate(new AffineTransform(a, b, c, d, e, f));
        }
    }

    private static void consume_i(Stack stack) {
//        collectTokenFrequency(PdfOps.i_TOKEN);
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

    private static void consume_d(GraphicsState graphicState, Stack stack, Shapes shapes) {
//        collectTokenFrequency(PdfOps.d_TOKEN);
        float dashPhase;
        float[] dashArray;
        try {
            // pop dashPhase off the stack
            dashPhase = ((Number) stack.pop()).floatValue();
            // pop the dashVector of the stack
            Vector dashVector = (Vector) stack.pop();
            // if the dash vector size is zero we have a default none dashed
            // line and thus we skip out
            if (dashVector.size() > 0) {
                // convert dash vector to a array of floats
                Enumeration dashEnum = dashVector.elements();
                dashArray = new float[dashVector.size()];
                int count = 0;
                while (dashEnum.hasMoreElements()) {
                    dashArray[count++] = ((Number) dashEnum.nextElement()).floatValue();
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
        }
        catch (ClassCastException e) {
            logger.log(Level.FINE, "Dash pattern syntax error: ", e);
        }
        // update stroke state with possibly new dash data.
        setStroke(shapes, graphicState);
    }

    private static void consume_j(GraphicsState graphicState, Stack stack, Shapes shapes) {
//        collectTokenFrequency(PdfOps.j_TOKEN);
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
//        collectTokenFrequency(PdfOps.w_TOKEN);
//        collectTokenFrequency(PdfOps.LW_TOKEN);
        graphicState.setLineWidth(((Number) stack.pop()).floatValue());
        setStroke(shapes, graphicState);
    }

    private static void consume_M(GraphicsState graphicState, Stack stack, Shapes shapes) {
//        collectTokenFrequency(PdfOps.M_TOKEN);
        graphicState.setMiterLimit(((Number) stack.pop()).floatValue());
        setStroke(shapes, graphicState);
    }

    private static void consume_gs(GraphicsState graphicState, Stack stack, Resources resources) {
//        collectTokenFrequency(PdfOps.gs_TOKEN);
        Object gs = stack.pop();
        if (gs instanceof Name) {
            // Get ExtGState and merge it with
            ExtGState extGState =
                    resources.getExtGState(((Name) gs).getName());
            if (extGState != null) {
                graphicState.concatenate(extGState);
            }
        }
    }

    private static void consume_Tf(GraphicsState graphicState, Stack stack, Resources resources) {
//        collectTokenFrequency(PdfOps.Tf_TOKEN);
        //graphicState.translate(-shift,0);
        //shift=0;
        float size = ((Number) stack.pop()).floatValue();
        Name name2 = (Name) stack.pop();
        // build the new font and initialize it.
        graphicState.getTextState().font = resources.getFont(name2.getName());
        graphicState.getTextState().currentfont =
                graphicState.getTextState().font.getFont().deriveFont(size);
    }

    private static void consume_Tc(GraphicsState graphicState, Stack stack) {
//        collectTokenFrequency(PdfOps.Tc_TOKEN);
        graphicState.getTextState().cspace = ((Number) stack.pop()).floatValue();
    }

    private static void consume_Tz(GraphicsState graphicState, Stack stack) {
//        collectTokenFrequency(PdfOps.Tz_TOKEN);
        Object ob = stack.pop();
        if (ob instanceof Number) {
            float hScaling = ((Number) ob).floatValue();
            // values is represented in percent but we want it as a none percent
            graphicState.getTextState().hScalling = hScaling / 100f;
        }
    }

    private static void consume_Tw(GraphicsState graphicState, Stack stack) {
//        collectTokenFrequency(PdfOps.Tw_TOKEN);
        graphicState.getTextState().wspace = ((Number) stack.pop()).floatValue();
    }

    private static void consume_Tr(GraphicsState graphicState, Stack stack) {
//        collectTokenFrequency(PdfOps.Tr_TOKEN);
        graphicState.getTextState().rmode = (int) ((Number) stack.pop()).floatValue();
    }

    private static void consume_TL(GraphicsState graphicState, Stack stack) {
//        collectTokenFrequency(PdfOps.TL_TOKEN);
        graphicState.getTextState().leading = ((Number) stack.pop()).floatValue();
    }

    private static void consume_Ts(GraphicsState graphicState, Stack stack) {
//        collectTokenFrequency(PdfOps.Ts_TOKEN);
        graphicState.getTextState().trise = ((Number) stack.pop()).floatValue();
    }


    public void dispose(boolean cache) {
        graphicState = null;
        library = null;
        resources.dispose(cache);
    }

    /**
     * Utility method for calculating the advanceX need for the
     * <code>displayText</code> given the strings parsed textState.  Each of
     * <code>displayText</code> glyphs and respective, text state is added to
     * the shapes collection.
     *
     * @param displayText         text that will be drawn to the screen
     * @param advance             current advanceX of last drawn string
     * @param previousAdvance     last advance of where the string should be drawn
     * @param previousTextAdvance last advance of the last drawn string
     * @param textState           formating properties associated with displayText
     * @param shapes              collection of all shapes for page content being parsed.
     * @return the modified advanceX value which can be used for the the next
     *         string that needs to be drawn
     */
    private Point2D drawString(
            StringBuffer displayText,
            Point2D advance,
            float previousAdvance,
            float previousTextAdvance,
            TextState textState,
            Shapes shapes,
            boolean isExtractText,
            Vector extractedTextVector) {

        // check to see if we are in font substitution mode, if we are
        // then we need to apply toUnicode mappings, to get the correct layout
        // for the substituted glyph
        StringBuffer unmodifiedDisplayText = new StringBuffer(displayText);

        float advanceX = ((Point2D.Float) advance).x;
        float advanceY = ((Point2D.Float) advance).y;

        if (displayText.length() == 0) {
            return new Point2D.Float(0, 0);
        }

        // Postion of previous Glyph, all relative to text block
        float lastx = 0, lasty = 0;
        // Make sure that the prevous advanceX is greater then then where we
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

        // check in unicode cMap exists
        boolean isToUnicode = textState.currentfont.getToUnicode() != null;

        // font metrics data
        float textRise = textState.trise;
        float charcterSpace = textState.cspace;
        float whiteSpace = textState.wspace;
        int textLength = displayText.length();

        // create a new sprite to hold the text objects
        TextSprite textSprites =
                new TextSprite(currentFont, textLength);

        // glyph placement params
        float currentX, currentY;
        float newAdvanceX, newAdvanceY;
//        System.out.println("-> " + displayText + " " + whiteSpace);
        // Iterate through displayText to calculate the the new advanceX value
        for (int i = 0; i < textLength; i++) {
            currentChar = displayText.charAt(i);

            // Position of the specified glyph relative to the origin of glyphVector
            if (!textState.font.isAFMFont()) {
                newAdvanceX = (float) currentFont.echarAdvance(currentChar).getX();
            } else {
                // Problematic Type1 characters that hare hard to draw.
                if (currentChar != 160 && currentChar > 31) {
                    newAdvanceX = (float) currentFont.echarAdvance(currentChar).getX();
                } else {
                    newAdvanceX = (float) currentFont.echarAdvance(' ').getX();
                }
            }
//            System.out.println(currentChar + " : " + (int)currentChar + " : " + newAdvanceX + " : " +
//                               currentFont.echarAdvance(currentChar).getX() + " : " + currentFont.echarAdvance(' ').getX());
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
//                     System.out.println("spacechar " + " : " + (int)currentFont.getSpaceEchar() );
                    lastx += whiteSpace;
                }
            } else {
                // add fonts rise to the to glyph position (sup,sub scripts)
                lasty += (newAdvanceY - textRise);
                currentX = advanceX - (newAdvanceX / 2.0f);
                currentY = advanceY + lasty;
            }
            textSprites.addText(currentChar, currentX, currentY, newAdvanceX);

            // add extract text.
            if (isExtractText) {
                // check char value from the unicode mpa and if not we just use the character code. 
                int charValue = isToUnicode ? textState.currentfont.getToUnicode()
                        .toSelector(unmodifiedDisplayText.charAt(i)) : currentChar;
                // add regular ascii
                if (charValue <= 255) {
                    addExtractedText(extractedTextVector,
                            new StringBuffer(String.valueOf((char) charValue)));
                }
                // add Unicode
                else {
                    addExtractedText(extractedTextVector,
                            new StringBuffer("\\U" + Integer.toHexString(charValue)));
                }
            }

        }
        // append the finaly offest of the with of the character
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
//        System.out.println("RMode " + rmode);
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
            // Fill text anda dd to path for clipping: 4
            case TextState.MODE_FILL_ADD:
                drawModeFill(textSprites, shapes, rmode);
                break;
            // Stroke Text and add to path for clippsing: 5
            case TextState.MODE_STROKE_ADD:
                drawModeStroke(textSprites, textState, shapes, rmode);
                break;
            // Fill, then stroke text adn add to path for clipping: 6
            case TextState.MODE_FILL_STROKE_ADD:
                drawModeFillStroke(textSprites, textState, shapes, rmode);
                break;
            // Add text to path for clipping: 7
            case TextState.MODE_ADD:
                textSprites.setRMode(rmode);
                shapes.add(textSprites);
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
        shapes.add(graphicState.getFillColor());
        shapes.add(textSprites);
    }

    /**
     * Utility Method for adding a text sprites to the Shapes stack, given the
     * specifed rmode.
     *
     * @param textSprites text to add to shapes stack
     * @param shapes      shapes stack
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
        shapes.add(graphicState.getStrokeColor());
        shapes.add(textSprites);

        // restore graphics state
        graphicState.setLineWidth(old);
        setStroke(shapes, graphicState);
    }

    /**
     * Utility Method for adding a text sprites to the Shapes stack, given the
     * specifed rmode.
     *
     * @param textSprites text to add to shapes stack
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
        // update the stroke and add the text to chapes
        setStroke(shapes, graphicState);
        shapes.add(graphicState.getFillColor());
        shapes.add(textSprites);

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
    private void commonStroke(Shapes shapes, GeneralPath geometricPath) {

        // get current fill alpha and concatenate with overprinting if present
        if (graphicState.isOverprintStroking()) {
            setAlpha(shapes, commonOverPrintAlpha(graphicState.getStrokeAlpha()));
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
                // todo:
                // currently no support for tiling, but we can still try
                // and fill using the specified uncoloured value.
                TilingPattern tilingPattern = (TilingPattern) pattern;
                if (tilingPattern.getPaintType() ==
                        TilingPattern.PAINTING_TYPE_UNCOLORED_TILING_PATTERN) {
                    setAlpha(shapes, graphicState.getFillAlpha());
                    shapes.add(tilingPattern.getUnColored());
                    shapes.add(geometricPath);
                    shapes.addDrawCommand();
                } else if (tilingPattern.getPaintType() ==
                        TilingPattern.PAINTING_TYPE_COLORED_TILING_PATTERN) {
                    setAlpha(shapes, graphicState.getFillAlpha());
                    shapes.add(tilingPattern.getFirstColor());
                    shapes.add(geometricPath);
                    shapes.addDrawCommand();
                }
            } else if (pattern != null &&
                    pattern.getPatternType() == Pattern.PATTERN_TYPE_SHADING) {
                pattern.init();
                setAlpha(shapes, graphicState.getFillAlpha());
                shapes.add(pattern.getPaint());
                shapes.add(geometricPath);
                shapes.addDrawCommand();
            }
        } else {
            setAlpha(shapes, graphicState.getStrokeAlpha());
            shapes.add(graphicState.getStrokeColor());
            shapes.add(geometricPath);
            shapes.addDrawCommand();
        }
        // set alpha back to origional value.
        if (graphicState.isOverprintStroking()) {
            setAlpha(shapes, graphicState.getFillAlpha());
        }
    }

    /**
     * Utility method for fudging overprinting calculation for screen
     * representation.
     *
     * @param alpha alph constant
     * @return tweaked overpring alpha
     */
    private float commonOverPrintAlpha(float alpha) {
        // if alpha is already present we reduce it and we minimize
        // it if it is already lower then our overpaint.  This an approximation
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
    private void commonFill(Shapes shapes, GeneralPath geometricPath) {

        // get current fill alpha and concatenate with overprinting if present
        if (graphicState.isOverprintOther()) {
            setAlpha(shapes, commonOverPrintAlpha(graphicState.getFillAlpha()));
        } else {
            setAlpha(shapes, graphicState.getFillAlpha());
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
                // currently no support for tiling, but we can still try
                // and fill using the specified uncoloured value.
                TilingPattern tilingPattern = (TilingPattern) pattern;
                if (tilingPattern.getPaintType() ==
                        TilingPattern.PAINTING_TYPE_UNCOLORED_TILING_PATTERN) {
                    shapes.add(tilingPattern.getUnColored());
                    shapes.add(geometricPath);
                    shapes.addFillCommand();
                } else if (tilingPattern.getPaintType() ==
                        TilingPattern.PAINTING_TYPE_COLORED_TILING_PATTERN) {
                    tilingPattern.init();
                    shapes.add(tilingPattern.getFirstColor());
                    shapes.add(geometricPath);
                    shapes.addFillCommand();
                }
            } else if (pattern != null &&
                    pattern.getPatternType() == Pattern.PATTERN_TYPE_SHADING) {
                pattern.init();
                shapes.add(pattern.getPaint());
                shapes.add(geometricPath);
                shapes.addFillCommand();
            }

        } else {
            shapes.add(graphicState.getFillColor());
            shapes.add(geometricPath);
            shapes.addFillCommand();
        }
        // add old alpha back to stack
        if (graphicState.isOverprintOther()) {
            setAlpha(shapes, graphicState.getFillAlpha());
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
     * @param shapes current Shapes object for the page being parsed
     */
    static void setStroke(Shapes shapes, GraphicsState graphicState) {
        shapes.add(new BasicStroke(graphicState.getLineWidth(),
                graphicState.getLineCap(),
                graphicState.getLineJoin(),
                graphicState.getMiterLimit(),
                graphicState.getDashArray(),
                graphicState.getDashPhase()));
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
    private static void applyTextScaling(GraphicsState graphicState) {
        // apply horizontal scaling if any.
        AffineTransform horizontalScalingTransform =
                new AffineTransform(graphicState.getTextState().hScalling,
                        0, 0, 1, 0, 0);
        // get the current CTM
        AffineTransform af = new AffineTransform(graphicState.getCTM());
        // do the matrix concatenation math
        af.concatenate(horizontalScalingTransform);
        // add the transformation to the graphics state
        graphicState.set(af);
    }

    /**
     * Adds a new Alpha Composite object ot the shapes stack.
     *
     * @param shapes - current shapes vector to add Alpha Composite to
     * @param alpha  - alpha value, opaque = 1.0f.
     */
    void setAlpha(Shapes shapes, float alpha) {
        // Build the alpha composite object and add it to the shapes
        AlphaComposite alphaComposite =
                AlphaComposite.getInstance(AlphaComposite.SRC_OVER,
                        alpha);
        shapes.add(alphaComposite);
    }
}
