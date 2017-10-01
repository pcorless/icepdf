package org.icepdf.core.pobjects.graphics.RasterOps;

import java.awt.image.BufferedImage;
import java.awt.image.Kernel;

public class SmartBlurFilter extends AbstractBufferedImageOp {

    private int hRadius = 5;
    private int vRadius = 5;
    private int threshold = 10;

    public BufferedImage filter(BufferedImage src, BufferedImage dst) {
        int width = src.getWidth();
        int height = src.getHeight();

        if (dst == null)
            dst = createCompatibleDestImage(src, null);

        int[] inPixels = new int[width * height];
        int[] outPixels = new int[width * height];
        getRGB(src, 0, 0, width, height, inPixels);

        Kernel kernel = GaussianFilter.makeKernel(hRadius);
        thresholdBlur(kernel, inPixels, outPixels, width, height, true);
        thresholdBlur(kernel, outPixels, inPixels, height, width, true);

        setRGB(dst, 0, 0, width, height, inPixels);
        return dst;
    }

    /**
     * Convolve with a kernel consisting of one row
     */
    public void thresholdBlur(Kernel kernel, int[] inPixels, int[] outPixels, int width, int height, boolean alpha) {
        int index = 0;
        float[] matrix = kernel.getKernelData(null);
        int cols = kernel.getWidth();
        int cols2 = cols / 2;

        for (int y = 0; y < height; y++) {
            int ioffset = y * width;
            int outIndex = y;
            for (int x = 0; x < width; x++) {
                float r = 0, g = 0, b = 0, a = 0;
                int moffset = cols2;

                int rgb1 = inPixels[ioffset + x];
                int a1 = (rgb1 >> 24) & 0xff;
                int r1 = (rgb1 >> 16) & 0xff;
                int g1 = (rgb1 >> 8) & 0xff;
                int b1 = rgb1 & 0xff;
                float af = 0, rf = 0, gf = 0, bf = 0;
                for (int col = -cols2; col <= cols2; col++) {
                    float f = matrix[moffset + col];

                    if (f != 0) {
                        int ix = x + col;
                        if (!(0 <= ix && ix < width))
                            ix = x;
                        int rgb2 = inPixels[ioffset + ix];
                        int a2 = (rgb2 >> 24) & 0xff;
                        int r2 = (rgb2 >> 16) & 0xff;
                        int g2 = (rgb2 >> 8) & 0xff;
                        int b2 = rgb2 & 0xff;

                        int d;
                        d = a1 - a2;
                        if (d >= -threshold && d <= threshold) {
                            a += f * a2;
                            af += f;
                        }
                        d = r1 - r2;
                        if (d >= -threshold && d <= threshold) {
                            r += f * r2;
                            rf += f;
                        }
                        d = g1 - g2;
                        if (d >= -threshold && d <= threshold) {
                            g += f * g2;
                            gf += f;
                        }
                        d = b1 - b2;
                        if (d >= -threshold && d <= threshold) {
                            b += f * b2;
                            bf += f;
                        }
                    }
                }
                a = af == 0 ? a1 : a / af;
                r = rf == 0 ? r1 : r / rf;
                g = gf == 0 ? g1 : g / gf;
                b = bf == 0 ? b1 : b / bf;
                int ia = alpha ? PixelUtils.clamp((int) (a + 0.5)) : 0xff;
                int ir = PixelUtils.clamp((int) (r + 0.5));
                int ig = PixelUtils.clamp((int) (g + 0.5));
                int ib = PixelUtils.clamp((int) (b + 0.5));
                outPixels[outIndex] = (ia << 24) | (ir << 16) | (ig << 8) | ib;
                outIndex += height;
            }
        }
    }

    public void setHRadius(int hRadius) {
        this.hRadius = hRadius;
    }

    public int getHRadius() {
        return hRadius;
    }

    public void setVRadius(int vRadius) {
        this.vRadius = vRadius;
    }

    public int getVRadius() {
        return vRadius;
    }

    public void setRadius(int radius) {
        this.hRadius = this.vRadius = radius;
    }

    public int getRadius() {
        return hRadius;
    }

    public void setThreshold(int threshold) {
        this.threshold = threshold;
    }

    public int getThreshold() {
        return threshold;
    }

    public String toString() {
        return "Blur/Smart Blur...";
    }
}