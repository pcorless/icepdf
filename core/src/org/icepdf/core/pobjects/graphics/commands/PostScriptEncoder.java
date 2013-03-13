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
package org.icepdf.core.pobjects.graphics.commands;

import org.icepdf.core.util.PdfOps;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.PathIterator;
import java.util.ArrayList;

/**
 * The PostScriptEncoder is responsible for converting an ArrayList<DrawCmd>
 * into postscript operands.  Basically the reverse of what the content
 * parser does.
 * <p/>
 * NOTE: this is currently a partial implementation to vac
 *
 * @since 5.0
 */
public class PostScriptEncoder {

    private static final String SPACE = " ";
    private static final String NEWLINE = "\r\n";
    private static final String TRUE = "true";
    private static final String FALSE = "false";
    private static final String NAME = "/";

    private PostScriptEncoder() {

    }

    public static byte[] generatePostScript(ArrayList<DrawCmd> drawCmds) {
        StringBuilder postScript = new StringBuilder();
        Color color = null;
        Shape currentShape = null;
        for (DrawCmd drawCmd : drawCmds) {
            // setup an affine transform
            if (drawCmd instanceof TransformDrawCmd) {
                AffineTransform af = ((TransformDrawCmd) drawCmd).getAffineTransform();
                postScript.append(af.getScaleX()).append(SPACE)
                        .append(af.getShearX()).append(SPACE)
                        .append(af.getShearY()).append(SPACE)
                        .append(af.getScaleY()).append(SPACE)
                        .append(af.getTranslateX()).append(SPACE)
                        .append(af.getTranslateY()).append(SPACE)
                        .append(PdfOps.cm_TOKEN).append(NEWLINE);
            }
            // reference the colour, we'll decide later if its fill or stroke.
            else if (drawCmd instanceof ColorDrawCmd) {
                color = ((ColorDrawCmd) drawCmd).getColor();
            }
            // stroke the shape.
            else if (drawCmd instanceof DrawDrawCmd) {
                float[] colors = color.getRGBColorComponents(null);
                // set the stroke color
                postScript.append(colors[0]).append(SPACE)
                        .append(colors[1]).append(SPACE)
                        .append(colors[2]).append(SPACE)
                        .append(PdfOps.RG_TOKEN).append(NEWLINE);
                // generate the draw operands for current shape.
                generateShaePostScript(currentShape, postScript);
                // add  the fill
                postScript.append(PdfOps.S_TOKEN).append(SPACE);
            }
            // fill the shape.
            else if (drawCmd instanceof FillDrawCmd) {
                float[] colors = color.getRGBColorComponents(null);
                // set fill color
                postScript.append(colors[0]).append(SPACE)
                        .append(colors[1]).append(SPACE)
                        .append(colors[2]).append(SPACE)
                        .append(PdfOps.rg_TOKEN).append(NEWLINE);
                // generate the draw operands for the current shape.
                generateShaePostScript(currentShape, postScript);
                // add  the fill
                postScript.append(PdfOps.f_TOKEN).append(SPACE);

            }
            // current shape.
            else if (drawCmd instanceof ShapeDrawCmd) {
                currentShape = ((ShapeDrawCmd) drawCmd).getShape();
            }
            // Sets the stroke.
            else if (drawCmd instanceof StrokeDrawCmd) {
                BasicStroke stroke = (BasicStroke) ((StrokeDrawCmd) drawCmd).getStroke();
                postScript.append(
                        // line width
                        stroke.getLineWidth()).append(SPACE)
                        .append(PdfOps.w_TOKEN).append(SPACE);
                // dash phase
                postScript.append(stroke.getDashArray() != null ?
                        stroke.getDashArray() : new ArrayList()).append(SPACE)
                        .append(stroke.getDashPhase()).append(SPACE)
                        .append(PdfOps.d_TOKEN).append(SPACE);
                // cap butt
                if (stroke.getEndCap() == BasicStroke.CAP_BUTT) {
                    postScript.append(0).append(SPACE)
                            .append(PdfOps.J_TOKEN).append(SPACE);
                } else if (stroke.getEndCap() == BasicStroke.CAP_ROUND) {
                    postScript.append(1).append(SPACE)
                            .append(PdfOps.J_TOKEN).append(SPACE);
                } else if (stroke.getEndCap() == BasicStroke.CAP_SQUARE) {
                    postScript.append(2).append(SPACE)
                            .append(PdfOps.J_TOKEN).append(SPACE);
                }
                // miter join.
                if (stroke.getMiterLimit() == BasicStroke.JOIN_MITER) {
                    postScript.append(0).append(SPACE)
                            .append(PdfOps.j_TOKEN).append(SPACE);
                } else if (stroke.getMiterLimit() == BasicStroke.JOIN_ROUND) {
                    postScript.append(1).append(SPACE)
                            .append(PdfOps.j_TOKEN).append(SPACE);
                } else if (stroke.getMiterLimit() == BasicStroke.JOIN_BEVEL) {
                    postScript.append(2).append(SPACE)
                            .append(PdfOps.j_TOKEN).append(SPACE);
                }
                postScript.append(NEWLINE);
            }
            // graphics state setup
            else if (drawCmd instanceof GraphicsStateCmd) {
                postScript.append('/')
                        .append(((GraphicsStateCmd) drawCmd).getGraphicStateName()).append(SPACE)
                        .append(PdfOps.gs_TOKEN).append(SPACE);
            }
            // break out a text block and child paint operands.
            else if (drawCmd instanceof TextSpriteDrawCmd) {

            }
        }
        return postScript.toString().getBytes();
    }

    /**
     * Utility to create postscript draw operations from a shapes path
     * iterator.
     *
     * @param currentShape shape to build out draw commands.
     * @param postScript   string to append draw opperands to.
     */
    private static void generateShaePostScript(Shape currentShape, StringBuilder postScript) {
        PathIterator pathIterator = currentShape.getPathIterator(null);
        float[] segment = new float[6];
        int segmentType;
        while (!pathIterator.isDone()) {
            segmentType = pathIterator.currentSegment(segment);
            switch (segmentType) {
                case PathIterator.SEG_MOVETO:
                    postScript.append(segment[0]).append(SPACE)
                            .append(segment[1]).append(SPACE)
                            .append(PdfOps.m_TOKEN).append(NEWLINE);
                    break;
                case PathIterator.SEG_LINETO:
                    postScript.append(segment[0]).append(SPACE)
                            .append(segment[1]).append(SPACE)
                            .append(PdfOps.l_TOKEN).append(NEWLINE);
                    break;
                case PathIterator.SEG_QUADTO:
                    postScript.append(segment[0]).append(SPACE)
                            .append(segment[1]).append(SPACE)
                            .append(segment[2]).append(SPACE)
                            .append(segment[3]).append(SPACE)
                            .append(PdfOps.v_TOKEN).append(NEWLINE);
                    break;
                case PathIterator.SEG_CUBICTO:
                    postScript.append(segment[0]).append(SPACE)
                            .append(segment[1]).append(SPACE)
                            .append(segment[2]).append(SPACE)
                            .append(segment[3]).append(SPACE)
                            .append(segment[4]).append(SPACE)
                            .append(segment[5]).append(SPACE)
                            .append(PdfOps.c_TOKEN).append(NEWLINE);
                    break;
                case PathIterator.SEG_CLOSE:
                    postScript.append(PdfOps.h_TOKEN).append(SPACE);
                    break;
            }
            pathIterator.next();
        }
    }
}
