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
package org.icepdf.core.views.common;

import org.icepdf.core.pobjects.Name;
import org.icepdf.core.pobjects.annotations.LineAnnotation;
import org.icepdf.core.pobjects.graphics.Shapes;
import org.icepdf.core.pobjects.graphics.commands.*;
import org.icepdf.core.views.DocumentViewController;
import org.icepdf.core.views.DocumentViewModel;
import org.icepdf.core.views.swing.AbstractPageViewComponent;

import java.awt.*;
import java.awt.geom.*;

/**
 * LineArrowAnnotationHandler tool is responsible for painting representation of
 * a line arrow on the screen during a click and drag mouse event.  The first point
 * is recorded on mousePressed and the line is drawn from first point the current
 * location of the mouse.  An open arrow is drawn at the starting point.
 * <p/>
 * Once the mouseReleased event is fired this handler will create new
 * LineArrowAnnotation and respective AnnotationComponent.  The addition of the
 * Annotation object to the page is handled by the annotation callback.
 *
 * @since 5.0
 */
public class LineArrowAnnotationHandler extends LineAnnotationHandler {


    public LineArrowAnnotationHandler(DocumentViewController documentViewController,
                                      AbstractPageViewComponent pageViewComponent,
                                      DocumentViewModel documentViewModel) {
        super(documentViewController, pageViewComponent, documentViewModel);

        startLineEnding = LineAnnotation.LINE_END_OPEN_ARROW;
        endLineEnding = LineAnnotation.LINE_END_NONE;
    }

    public void paintTool(Graphics g) {
        if (startOfLine != null && endOfLine != null) {
            Graphics2D gg = (Graphics2D) g;
            Color oldColor = gg.getColor();
            Stroke oldStroke = gg.getStroke();
            g.setColor(lineColor);
            gg.setStroke(stroke);

            // draw the line
            gg.drawLine((int) startOfLine.getX(), (int) startOfLine.getY(),
                    (int) endOfLine.getX(), (int) endOfLine.getY());
            // draw start cap
            if (!startLineEnding.equals(LineAnnotation.LINE_END_NONE)) {
                drawLineStart(gg, startLineEnding, startOfLine);
            }
            // draw end cap
            if (!endLineEnding.equals(LineAnnotation.LINE_END_NONE)) {
                drawLineEnd(gg, endLineEnding, endOfLine);
            }
            g.setColor(oldColor);
            gg.setStroke(oldStroke);
        }
    }

    private void drawLineStart(Graphics2D g, Name lineEnding, Point2D point) {

        if (lineEnding.equals(LineAnnotation.LINE_END_OPEN_ARROW)) {
            drawOpenArrowStart(g);
        } else if (lineEnding.equals(LineAnnotation.LINE_END_CLOSED_ARROW)) {
            drawClosedArrowStart(g);
        } else if (lineEnding.equals(LineAnnotation.LINE_END_CIRCLE)) {
            drawCircle(g, point);
        } else if (lineEnding.equals(LineAnnotation.LINE_END_DIAMOND)) {
            drawDiamond(g, point);
        } else if (lineEnding.equals(LineAnnotation.LINE_END_SQUARE)) {
            drawSquare(g, point);
        }
    }

    private void drawLineEnd(Graphics2D g, Name lineEnding, Point2D point) {
        if (lineEnding.equals(LineAnnotation.LINE_END_OPEN_ARROW)) {
            drawOpenArrowEnd(g);
        } else if (lineEnding.equals(LineAnnotation.LINE_END_CLOSED_ARROW)) {
            drawClosedArrowEnd(g);
        } else if (lineEnding.equals(LineAnnotation.LINE_END_CIRCLE)) {
            drawCircle(g, point);
        } else if (lineEnding.equals(LineAnnotation.LINE_END_DIAMOND)) {
            drawDiamond(g, point);
        } else if (lineEnding.equals(LineAnnotation.LINE_END_SQUARE)) {
            drawSquare(g, point);
        }
    }

    public static void circleDrawOps(Shapes shapes, AffineTransform at,
                                     Point2D point, Point2D start,
                                     Point2D end, Color lineColor,
                                     Color internalColor) {
        AffineTransform af = createRotation(point, start, end);
        at = new AffineTransform(at);
        at.concatenate(af);
        shapes.add(new TransformDrawCmd(at));
        shapes.add(new ColorDrawCmd(lineColor));
        shapes.add(new ShapeDrawCmd(createCircleEnd()));
        shapes.add(new FillDrawCmd());
    }

    private static Shape createCircleEnd() {
        return new Ellipse2D.Double(-4, -4, 8, 8);
    }

    private void drawCircle(Graphics2D g, Point2D point) {
        AffineTransform oldAf = g.getTransform();
        AffineTransform af = createRotation(point, startOfLine, endOfLine);
        AffineTransform gAf = g.getTransform();
        gAf.concatenate(af);
        g.setTransform(gAf);
        g.setColor(lineColor);
        g.fill(createCircleEnd());
        g.setTransform(oldAf);
    }

    public static void diamondDrawOps(Shapes shapes, AffineTransform at,
                                      Point2D point, Point2D start,
                                      Point2D end, Color lineColor,
                                      Color internalColor) {
        AffineTransform tx = new AffineTransform();
        Line2D.Double line = new Line2D.Double(start, end);
        tx.setToIdentity();
        double angle = Math.atan2(line.y2 - line.y1, line.x2 - line.x1);
        tx.translate(point.getX(), point.getY());
        tx.rotate(angle - (Math.PI / 4));

        AffineTransform af = createRotation(point, start, end);
        at = new AffineTransform(at);
        at.concatenate(tx);
        shapes.add(new TransformDrawCmd(at));
        shapes.add(new ColorDrawCmd(lineColor));
        shapes.add(new ShapeDrawCmd(createSquareEnd()));
        shapes.add(new FillDrawCmd());
    }


    private void drawDiamond(Graphics2D g, Point2D point) {
        AffineTransform oldAf = g.getTransform();
        AffineTransform tx = new AffineTransform();
        Line2D.Double line = new Line2D.Double(startOfLine, endOfLine);
        tx.setToIdentity();
        double angle = Math.atan2(line.y2 - line.y1, line.x2 - line.x1);
        tx.translate(point.getX(), point.getY());
        // quarter rotation
        tx.rotate(angle - (Math.PI / 4));
        AffineTransform gAf = g.getTransform();
        gAf.concatenate(tx);
        g.setTransform(gAf);
        g.setColor(lineColor);
        g.fill(createSquareEnd());
        g.setTransform(oldAf);
    }

    public static void squareDrawOps(Shapes shapes, AffineTransform at,
                                     Point2D point, Point2D start,
                                     Point2D end, Color lineColor,
                                     Color internalColor) {
        AffineTransform af = createRotation(point, start, end);
        at = new AffineTransform(at);
        at.concatenate(af);
        shapes.add(new TransformDrawCmd(at));
        shapes.add(new ColorDrawCmd(lineColor));
        shapes.add(new ShapeDrawCmd(createSquareEnd()));
        shapes.add(new FillDrawCmd());
    }

    private static Shape createSquareEnd() {
        return new Rectangle2D.Double(-4, -4, 8, 8);
    }

    private void drawSquare(Graphics2D g, Point2D point) {
        AffineTransform oldAf = g.getTransform();
        AffineTransform af = createRotation(point, startOfLine, endOfLine);
        AffineTransform gAf = g.getTransform();
        gAf.concatenate(af);
        g.setTransform(gAf);
        g.setColor(lineColor);
        g.fill(createSquareEnd());
        g.setTransform(oldAf);
    }

    public static void openArrowEndDrawOps(Shapes shapes, AffineTransform at,
                                           Point2D start, Point2D end,
                                           Color lineColor, Color internalColor) {
        AffineTransform af = createRotation(end, start, end);
        at = new AffineTransform(at);
        at.concatenate(af);
        shapes.add(new TransformDrawCmd(at));
        shapes.add(new ColorDrawCmd(lineColor));
        shapes.add(new ShapeDrawCmd(createOpenArrowEnd()));
        shapes.add(new DrawDrawCmd());
    }

    private static Shape createOpenArrowEnd() {
        GeneralPath arrowHead = new GeneralPath();
        arrowHead.moveTo(0, 0);
        arrowHead.lineTo(5, -10);
        arrowHead.moveTo(0, 0);
        arrowHead.lineTo(-5, -10);
        arrowHead.closePath();
        return arrowHead;
    }

    private void drawOpenArrowEnd(Graphics2D g) {
        Shape arrowHead = createOpenArrowEnd();
        AffineTransform oldAf = g.getTransform();
        AffineTransform af = createRotation(endOfLine, startOfLine, endOfLine);
        AffineTransform gAf = g.getTransform();
        gAf.concatenate(af);
        g.setTransform(gAf);
        g.setColor(lineColor);
        g.draw(arrowHead);
        g.setTransform(oldAf);
    }

    public static void openArrowStartDrawOps(Shapes shapes, AffineTransform at,
                                             Point2D start, Point2D end,
                                             Color lineColor, Color internalColor) {
        AffineTransform af = createRotation(start, start, end);
        at = new AffineTransform(at);
        at.concatenate(af);
        shapes.add(new TransformDrawCmd(at));
        shapes.add(new ColorDrawCmd(lineColor));
        shapes.add(new ShapeDrawCmd(createOpenArrowStart()));
        shapes.add(new DrawDrawCmd());
    }

    private static Shape createOpenArrowStart() {
        GeneralPath arrowHead = new GeneralPath();
        arrowHead.moveTo(0, 0);
        arrowHead.lineTo(5, 10);
        arrowHead.moveTo(0, 0);
        arrowHead.lineTo(-5, 10);
        arrowHead.closePath();
        return arrowHead;
    }

    private void drawOpenArrowStart(Graphics2D g) {
        Shape arrowHead = createOpenArrowStart();
        AffineTransform oldAf = g.getTransform();
        AffineTransform af = createRotation(startOfLine, startOfLine, endOfLine);
        AffineTransform gAf = g.getTransform();
        gAf.concatenate(af);
        g.setTransform(gAf);
        g.setColor(lineColor);
        g.draw(arrowHead);
        g.setTransform(oldAf);
    }

    public static void closedArrowStartDrawOps(Shapes shapes, AffineTransform at,
                                               Point2D start, Point2D end,
                                               Color lineColor, Color internalColor) {
        AffineTransform af = createRotation(start, start, end);
        at = new AffineTransform(at);
        at.concatenate(af);
        shapes.add(new TransformDrawCmd(at));
        shapes.add(new ColorDrawCmd(internalColor));
        shapes.add(new ShapeDrawCmd(createClosedArrowStart()));
        shapes.add(new FillDrawCmd());
        shapes.add(new ColorDrawCmd(lineColor));
        shapes.add(new ShapeDrawCmd(createClosedArrowStart()));
        shapes.add(new DrawDrawCmd());
    }

    private static Shape createClosedArrowStart() {
        Polygon arrowHead = new Polygon();
        arrowHead.addPoint(0, -5);
        arrowHead.addPoint(-5, 5);
        arrowHead.addPoint(5, 5);
        return arrowHead;
    }

    private void drawClosedArrowStart(Graphics2D g) {
        Shape arrowHead = createClosedArrowStart();
        AffineTransform oldAf = g.getTransform();
        AffineTransform af = createRotation(startOfLine, startOfLine, endOfLine);
        AffineTransform gAf = g.getTransform();
        gAf.concatenate(af);
        g.setTransform(gAf);
        g.setColor(internalColor);
        g.fill(arrowHead);
        g.setColor(lineColor);
        g.draw(arrowHead);
        g.setTransform(oldAf);
    }

    public static void closedArrowEndDrawOps(Shapes shapes, AffineTransform at,
                                             Point2D start, Point2D end,
                                             Color lineColor, Color internalColor) {
        AffineTransform af = createRotation(end, start, end);
        at = new AffineTransform(at);
        at.concatenate(af);

        shapes.add(new TransformDrawCmd(at));
        shapes.add(new ColorDrawCmd(internalColor));
        shapes.add(new ShapeDrawCmd(createClosedArrowEnd()));
        shapes.add(new FillDrawCmd());
        shapes.add(new ColorDrawCmd(lineColor));
        shapes.add(new ShapeDrawCmd(createClosedArrowEnd()));
        shapes.add(new DrawDrawCmd());
    }


    private static Shape createClosedArrowEnd() {
        Polygon arrowHead = new Polygon();
        arrowHead.addPoint(0, 5);
        arrowHead.addPoint(-5, -5);
        arrowHead.addPoint(5, -5);
        return arrowHead;
    }

    private void drawClosedArrowEnd(Graphics2D g) {
        Shape arrowHead = createClosedArrowEnd();
        AffineTransform oldAf = g.getTransform();
        AffineTransform af = createRotation(endOfLine, startOfLine, endOfLine);
        AffineTransform gAf = g.getTransform();
        gAf.concatenate(af);
        g.setTransform(gAf);
        g.setColor(internalColor);
        g.fill(arrowHead);
        g.setColor(lineColor);
        g.draw(arrowHead);
        g.setTransform(oldAf);
    }

    private static AffineTransform createRotation(Point2D point,
                                                  Point2D startOfLine,
                                                  Point2D endOfLine) {
        AffineTransform tx = new AffineTransform();
        Line2D.Double line = new Line2D.Double(startOfLine, endOfLine);
        tx.setToIdentity();
        double angle = Math.atan2(line.y2 - line.y1, line.x2 - line.x1);
        tx.translate(point.getX(), point.getY());
        tx.rotate(angle - (Math.PI / 2));
        return tx;
    }


}
