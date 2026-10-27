/*
 * Copyright 2026 Patrick Corless
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.icepdf.core.pobjects.graphics;

import org.icepdf.core.pobjects.graphics.commands.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * GH-632: Shapes drops transforms that change nothing, and Q re-emits only the state that is not already in
 * effect.  The painted state after every command must be what the full list would have left.
 */
public class ShapesPaintStateTest {

    private static final AffineTransform A = AffineTransform.getTranslateInstance(10, 20);
    private static final AffineTransform B = AffineTransform.getScaleInstance(2, 3);

    private static List<Class<?>> kinds(Shapes shapes) {
        List<Class<?>> kinds = new ArrayList<>();
        for (DrawCmd cmd : shapes.getShapes()) {
            kinds.add(cmd.getClass());
        }
        return kinds;
    }

    @DisplayName("a transform already in effect is dropped")
    @Test
    void sameTransformDropped() {
        Shapes shapes = new Shapes();
        shapes.add(new TransformDrawCmd(new AffineTransform(A)));
        shapes.add(new ShapeDrawCmd(new Rectangle2D.Float(0, 0, 1, 1)));
        shapes.add(new FillDrawCmd());
        shapes.add(new TransformDrawCmd(new AffineTransform(A)));
        assertEquals(3, shapes.getShapesCount());
    }

    @DisplayName("a transform nothing used is replaced in place; state commands between keep their order")
    @Test
    void unusedTransformReplaced() {
        Shapes shapes = new Shapes();
        shapes.add(new TransformDrawCmd(new AffineTransform()));
        shapes.add(new FillDrawCmd());
        shapes.add(new TransformDrawCmd(new AffineTransform(A)));
        shapes.add(new ColorDrawCmd(Color.RED));
        shapes.add(new TransformDrawCmd(new AffineTransform(B)));
        shapes.add(new FillDrawCmd());
        List<DrawCmd> list = shapes.getShapes();
        assertEquals(5, list.size());
        assertEquals(B, ((TransformDrawCmd) list.get(2)).getAffineTransform());
        assertTrue(list.get(3) instanceof ColorDrawCmd);
    }

    @DisplayName("the list's first transform is never replaced: a soft mask group reads it as the group's cm")
    @Test
    void firstTransformKept() {
        Shapes shapes = new Shapes();
        shapes.add(new TransformDrawCmd(new AffineTransform(A)));
        shapes.add(new ColorDrawCmd(Color.RED));
        shapes.add(new TransformDrawCmd(new AffineTransform(B)));
        List<DrawCmd> list = shapes.getShapes();
        assertEquals(3, list.size());
        assertEquals(A, ((TransformDrawCmd) list.get(0)).getAffineTransform());
    }

    @DisplayName("a used transform is kept")
    @Test
    void usedTransformKept() {
        Shapes shapes = new Shapes();
        shapes.add(new TransformDrawCmd(new AffineTransform(A)));
        shapes.add(new ShapeDrawCmd(new Rectangle2D.Float(0, 0, 1, 1)));
        shapes.add(new FillDrawCmd());
        shapes.add(new TransformDrawCmd(new AffineTransform(B)));
        assertEquals(4, shapes.getShapesCount());
    }

    @DisplayName("an unknown command forgets the state; the list handed out is never rewritten behind the caller")
    @Test
    void unknownCommandAndGetShapesForget() {
        Shapes shapes = new Shapes();
        shapes.add(new TransformDrawCmd(new AffineTransform(A)));
        shapes.add(new PaintDrawCmd(Color.BLUE));
        shapes.add(new TransformDrawCmd(new AffineTransform(A)));
        assertEquals(3, shapes.getShapesCount(), "the paint command may change the transform");

        Shapes other = new Shapes();
        other.add(new TransformDrawCmd(new AffineTransform(A)));
        int held = other.getShapes().size();
        other.add(new TransformDrawCmd(new AffineTransform(B)));
        assertEquals(held + 1, other.getShapes().size(), "the transform before the held index is not replaced");
        other.getShapes().add(new TransformDrawCmd(new AffineTransform(A)));
        other.add(new TransformDrawCmd(new AffineTransform(A)));
        assertEquals(held + 3, other.getShapes().size(), "a list changed directly is not trusted");
    }

    @DisplayName("q/Q with nothing changed in between re-emits nothing; a changed line width re-emits the stroke")
    @Test
    void restoreSkipsStateInEffect() {
        Shapes shapes = new Shapes();
        GraphicsState state = new GraphicsState(shapes);
        state.set(new AffineTransform(A));
        shapes.add(new StrokeDrawCmd(new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10)));
        shapes.add(new ColorDrawCmd(Color.black));
        int before = shapes.getShapesCount();

        GraphicsState child = state.save();
        child.restore();
        assertEquals(before, shapes.getShapesCount(), "nothing to restore");

        child = state.save();
        child.setLineWidth(5);
        shapes.add(new StrokeDrawCmd(new BasicStroke(5)));
        shapes.add(new ShapeDrawCmd(new Rectangle2D.Float(0, 0, 1, 1)));
        shapes.add(new DrawDrawCmd());
        child.restore();
        List<Class<?>> kinds = kinds(shapes);
        assertEquals(StrokeDrawCmd.class, kinds.get(kinds.size() - 1), "only the stroke is restored");
        BasicStroke restored = (BasicStroke) ((StrokeDrawCmd) shapes.getShapes().get(kinds.size() - 1)).getStroke();
        assertEquals(1f, restored.getLineWidth());
    }

    @DisplayName("Q after a cm restores the parent transform")
    @Test
    void restoreAfterCm() {
        Shapes shapes = new Shapes();
        GraphicsState state = new GraphicsState(shapes);
        state.set(new AffineTransform(A));
        GraphicsState child = state.save();
        AffineTransform cm = new AffineTransform(A);
        cm.concatenate(B);
        child.set(cm);
        shapes.add(new ShapeDrawCmd(new Rectangle2D.Float(0, 0, 1, 1)));
        shapes.add(new FillDrawCmd());
        child.restore();
        DrawCmd last = null;
        for (DrawCmd cmd : shapes.getShapes()) {
            if (cmd instanceof TransformDrawCmd) last = cmd;
        }
        assertEquals(A, ((TransformDrawCmd) last).getAffineTransform());
    }
}
