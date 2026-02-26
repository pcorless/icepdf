package org.icepdf.ri.common.print;

import org.junit.jupiter.api.Test;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.ColorModel;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class PrintHelperImplTest {
    @Test
    void linuxStyleCoordinates() {
        GraphicsConfiguration gc = new DummyGraphicsConfiguration(new Rectangle(0, 0, 1920, 1080));
        Window window = new DummyWindow(100, 100);
        Point p = PrintHelperImpl.calculateDialogPosition(window, null, gc);
        assertEquals(150, p.x);
        assertEquals(150, p.y);
    }

    @Test
    void windowsStyleNegativeCoordinates() {
        GraphicsConfiguration gc = new DummyGraphicsConfiguration(new Rectangle(-1920, 0, 1920, 1080));
        Window window = new DummyWindow(-1920, 100);
        Point p = PrintHelperImpl.calculateDialogPosition(window, null, gc);
        assertEquals(-1870, p.x);
        assertEquals(150, p.y);
    }

    // Minimal dummy classes for test
    static class DummyWindow extends Window {
        private final int x, y;

        DummyWindow(int x, int y) {
            super((Frame) null);
            this.x = x;
            this.y = y;
        }

        @Override
        public int getX() {
            return x;
        }

        @Override
        public int getY() {
            return y;
        }
    }

    static class DummyGraphicsConfiguration extends GraphicsConfiguration {
        private final Rectangle bounds;

        DummyGraphicsConfiguration(Rectangle bounds) {
            this.bounds = bounds;
        }

        @Override
        public Rectangle getBounds() {
            return bounds;
        }

        // ...other methods throw UnsupportedOperationException...
        @Override
        public GraphicsDevice getDevice() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ColorModel getColorModel() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ColorModel getColorModel(int transparency) {
            throw new UnsupportedOperationException();
        }

        @Override
        public AffineTransform getDefaultTransform() {
            throw new UnsupportedOperationException();
        }

        @Override
        public AffineTransform getNormalizingTransform() {
            throw new UnsupportedOperationException();
        }
    }
}
