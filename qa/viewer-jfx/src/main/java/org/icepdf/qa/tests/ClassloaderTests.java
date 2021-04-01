package org.icepdf.qa.tests;

import javafx.scene.image.Image;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.PixelReader;
import org.icepdf.qa.utilities.TimeTestWatcher;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.Dimension2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.*;
import java.util.ArrayList;

/**
 * Class loader tests for having two version of ICEpdf in one JVM
 */
public class ClassloaderTests {

    public static void main(String[] args) {

        // custom class loader to load pdf lib
        String userHome = System.getProperties().getProperty("user.home");
        loadPDF(userHome + "/dev/products/icepdf-pro-6.3.0/libs/", "base");
        loadPDF(userHome + "/dev/products/icepdf-pro-6.3.2/libs/", "baseTwo");

        // tests for compression speed and diff results.
        try {
            // todo try new expression to foreach notation.
            for (int i = 0; i < 4; i++) {
                File file = new File("imageCapture_" + i + "_base.png");
                File file2 = new File("imageCapture_" + i + "_baseTwo.png");
                System.out.println(file2.getAbsolutePath());
                TimeTestWatcher timer = new TimeTestWatcher();
                timer.starting("diff testAndAnalyze");
                double diff = percentageCompare(new Image(new FileInputStream(file)),
                        new Image(new FileInputStream(file2)));
                timer.finished();
                System.out.println("diff: " + diff);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    public static void loadPDF(String pdfJarDirectory, String label) {

        try {
            Path baseDir = Paths.get(pdfJarDirectory);
            ArrayList<URL> classPath = new ArrayList<>(baseDir.getNameCount());
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(baseDir, "*.{class,jar}")) {
                for (Path file : stream) {
                    System.out.println(file.toUri());
                    classPath.add(file.normalize().toUri().toURL());
                }
            } catch (IOException | DirectoryIteratorException x) {
                // IOException can never be thrown by the iteration.
                // In this snippet, it can only be thrown by newDirectoryStream.
                System.err.println(x);
            }

            ClassLoader parent = String.class.getClassLoader();
            URLClassLoader clsLoader = URLClassLoader.newInstance(classPath.toArray(new URL[0]), parent);

            Class documentClass = clsLoader.loadClass("org.icepdf.core.pobjects.Document");
            Constructor fontClassConstructor = documentClass.getDeclaredConstructor();
            Object documentObject = fontClassConstructor.newInstance();
            System.out.println(documentObject);


            // load document
            Method setFileMethod = documentClass.getMethod("setFile", String.class);
            String test = Paths.get(System.getProperty("user.home") + "/dev/pdf-qa/pdf_reference_addendum_redaction.pdf").normalize().toString();
            setFileMethod.invoke(documentObject, test);

            // number of pages.
            Method getNumberOfPagesMethod = documentClass.getMethod("getNumberOfPages");
            int pages = (int) getNumberOfPagesMethod.invoke(documentObject);

            for (int pageNumber = 0; pageNumber < pages; pageNumber++) {

                // get the page tree
                Method getPageTreeMethod = documentClass.getMethod("getPageTree");
                Object pageTree = getPageTreeMethod.invoke(documentObject);

                // get next page.
                Class pageTreeClass = clsLoader.loadClass("org.icepdf.core.pobjects.PageTree");
                Method getPageMethod = pageTreeClass.getMethod("getPage", int.class);
                Object pageObject = getPageMethod.invoke(pageTree, pageNumber);

                // init the page.
                Class pageClass = clsLoader.loadClass("org.icepdf.core.pobjects.Page");
                Method initMethod = pageClass.getMethod("init");
                initMethod.invoke(pageObject);

                // get the size
                Method getSizeMethod = pageClass.getMethod("getSize", int.class, float.class, float.class);
                Dimension2D sz = (Dimension2D) getSizeMethod.invoke(pageObject, 2, 0, 1);

                int pageWidth = (int) sz.getWidth();
                int pageHeight = (int) sz.getHeight();

                BufferedImage image = new BufferedImage(pageWidth, pageHeight, BufferedImage.TYPE_INT_RGB);
                Graphics g = image.createGraphics();

                // paint the page.
                Method paintMethod = pageClass.getMethod("paint",
                        Graphics.class, int.class, int.class, float.class, float.class);
                paintMethod.invoke(pageObject, g, 2, 2, 0f, 1f);

                g.dispose();
                // capture the page image to file

                System.out.println("Capturing page " + pageNumber);
                File file = new File("imageCapture_" + pageNumber + "_" + label + ".png");
                ImageIO.write(image, "png", file);
                image.flush();
            }

        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        } catch (InstantiationException e) {
            e.printStackTrace();
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }


    private static double percentageCompare(Image image1, Image image2) {

        assert (image1.getWidth() == image2.getWidth() && image2.getHeight() == image2.getHeight());

        PixelReader pixelReader1 = image1.getPixelReader();
        PixelReader pixelReader2 = image2.getPixelReader();

        int width = (int) image1.getWidth();
        int height = (int) image1.getHeight();

        int[] buffer1 = new int[width];
        int[] buffer2 = new int[width];

        int badPixels = 0;
        for (int y = 0; y < height; y++) {
            pixelReader1.getPixels(0, y, width, 1, PixelFormat.getIntArgbInstance(), buffer1, 0, 1);
            pixelReader2.getPixels(0, y, width, 1, PixelFormat.getIntArgbInstance(), buffer2, 0, 1);
            for (int x = 0; x < width; x++) {
                if (buffer1[x] != buffer2[x]) {
                    badPixels++;
                }
            }
        }


        int totalPixels = width * height;
        int goodPixels = totalPixels - badPixels;

        return 100d * goodPixels / totalPixels;
    }
}
