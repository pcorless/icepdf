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
package org.icepdf.core.pobjects.graphics.images;

import org.icepdf.core.pobjects.DictionaryEntries;
import org.icepdf.core.pobjects.Name;
import org.icepdf.core.pobjects.Stream;
import org.icepdf.core.pobjects.graphics.DeviceGray;
import org.icepdf.core.pobjects.graphics.GraphicsState;

import javax.imageio.ImageIO;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URLClassLoader;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

public class JBig2Decoder extends AbstractImageDecoder {

    private static final Logger logger =
            Logger.getLogger(JBig2Decoder.class.getName());

    private static final String[] JBIG2_PDF_BOX = new String[]{
            "org.apache.pdfbox.jbig2.JBIG2ImageReader",
            "org.apache.pdfbox.jbig2.JBIG2ImageReaderSpi",
            "org.apache.pdfbox.jbig2.JBIG2Globals"};

    private static final Name JBIG2_GLOBALS_KEY = new Name("JBIG2Globals");
    private static final Name DECODE_PARMS_KEY = new Name("DecodeParms");

    // the JBIG2 library is optional; only warn about it being absent once per
    // JVM rather than for every JBIG2 image encountered.
    private static final AtomicBoolean jbig2LibraryMissingLogged = new AtomicBoolean(false);

    public JBig2Decoder(ImageStream imageStream, GraphicsState graphicsState) {
        super(imageStream, graphicsState);
    }

    @Override
    public BufferedImage decode() {

        BufferedImage tmpImage = null;

        ImageParams imageParams = imageStream.getImageParams();
        // get the decode params form the stream
        DictionaryEntries decodeParams = imageParams.getDictionary(DECODE_PARMS_KEY);
        Stream globalsStream = null;
        if (decodeParams != null) {
            Object jbigGlobals = imageParams.getObject(decodeParams, JBIG2_GLOBALS_KEY);
            if (jbigGlobals instanceof Stream) {
                globalsStream = (Stream) jbigGlobals;
            }
        }
        // grab the data,
        ImageInputStream imageInputStream = null;
        try {
            byte[] data = imageStream.getDecodedStreamBytes(imageParams.getDataLength());
            imageInputStream = ImageIO.createImageInputStream(new ByteArrayInputStream(data));
            tmpImage = decodeJbig2(decodeParams, globalsStream, imageInputStream, JBIG2_PDF_BOX);
        } catch (ClassNotFoundException | NoClassDefFoundError e) {
            // the optional library isn't reachable from the class loader that
            // loaded this class; warn once with enough detail to diagnose it.
            if (jbig2LibraryMissingLogged.compareAndSet(false, true)) {
                logger.log(Level.WARNING, "Optional Apache PDFBox JBIG2 library (" + JBIG2_PDF_BOX[0]
                        + ") was not found on the class path of " + describeClassLoader()
                        + "; JBIG2 images will not be decoded.");
                logger.log(Level.FINE, "JBIG2 library class load failure", e);
            }
        } catch (IOException | InstantiationException | InvocationTargetException | NoSuchMethodException |
                IllegalAccessException e) {
            // the library is present but failed to decode this particular image.
            logger.log(Level.WARNING, "Error decoding JBIG2 image with the Apache PDFBox JBIG2 library.", e);
        } finally {
            // dispose the stream
            if (imageInputStream != null) {
                try {
                    imageInputStream.close();
                } catch (IOException e) {
                    logger.log(Level.WARNING, "Could not close image input stream.");
                }
            }
        }

        // apply decode (tmpImage is null when the optional JBIG2 library is
        // absent or decoding failed, in which case there is nothing to decode)
        if (tmpImage != null && imageStream.getColourSpace() instanceof DeviceGray) {
            tmpImage = ImageUtility.applyGrayDecode(tmpImage, imageParams);
        }

        // apply the fill colour and alpha if masking is enabled.
        return tmpImage;

    }

    protected BufferedImage decodeJbig2(DictionaryEntries decodeParams, Stream globalsStream, ImageInputStream imageInputStream,
                                        String[] jbigClasses)
            throws ClassNotFoundException, IllegalAccessException, InstantiationException, NoSuchMethodException, InvocationTargetException, IOException {
        // jbig2 is an optional jar, so we try and load it reflectively
        Class<?> jbig2ImageReaderClass = Class.forName(jbigClasses[0]);
        Class<?> jbig2ImageReaderSpiClass = Class.forName(jbigClasses[1]);
        Class<?> jbig2GlobalsClass = Class.forName(jbigClasses[2]);
        Object jbig2ImageReaderSpi = jbig2ImageReaderSpiClass.getDeclaredConstructor().newInstance();
        Constructor<?> levigoJbig2DecoderClassConstructor =
                jbig2ImageReaderClass.getDeclaredConstructor(javax.imageio.spi.ImageReaderSpi.class);
        Object jbig2Reader = levigoJbig2DecoderClassConstructor.newInstance(jbig2ImageReaderSpi);
        // set the input
        Class[] partypes = new Class[1];
        partypes[0] = Object.class;
        Object[] arglist = new Object[1];
        arglist[0] = imageInputStream;
        Method setInput = jbig2ImageReaderClass.getMethod("setInput", partypes);
        setInput.invoke(jbig2Reader, arglist);
        // apply decode params if any.
        if (decodeParams != null) {
            if (globalsStream != null) {
                byte[] globals = globalsStream.getDecodedStreamBytes(0);
                if (globals != null && globals.length > 0) {
                    partypes = new Class[1];
                    partypes[0] = ImageInputStream.class;
                    arglist = new Object[1];
                    arglist[0] = ImageIO.createImageInputStream(new ByteArrayInputStream(globals));
                    Method processGlobals =
                            jbig2ImageReaderClass.getMethod("processGlobals", partypes);
                    Object globalSegments = processGlobals.invoke(jbig2Reader, arglist);
                    if (globalSegments != null) {
                        // invoked encoder.setGlobalData(globals);
                        partypes = new Class[1];
                        partypes[0] = jbig2GlobalsClass;
                        arglist = new Object[1];
                        arglist[0] = globalSegments;
                        // pass the segment data back into the decoder.
                        Method setGlobalData =
                                jbig2ImageReaderClass.getMethod("setGlobals", partypes);
                        setGlobalData.invoke(jbig2Reader, arglist);
                    }
                }
            }
        }
        partypes = new Class[1];
        partypes[0] = int.class;
        arglist = new Object[1];
        arglist[0] = 0;
        Method read = jbig2ImageReaderClass.getMethod("read", partypes);
        BufferedImage tmpImage = (BufferedImage) read.invoke(jbig2Reader, arglist);
        // call dispose on the reader
        Method dispose = jbig2ImageReaderClass.getMethod("dispose");
        dispose.invoke(jbig2Reader);
        return tmpImage;
    }

    /**
     * Describes the class loader that loaded this class, including its URLs when
     * it is a {@link URLClassLoader}. The reflective JBIG2 load resolves against
     * this loader, so surfacing its search path makes a missing optional jar
     * (e.g. an isolated QA capture-set class path) diagnosable at a glance.
     *
     * @return human readable description of the loader and its class path.
     */
    private static String describeClassLoader() {
        ClassLoader classLoader = JBig2Decoder.class.getClassLoader();
        if (classLoader instanceof URLClassLoader) {
            return classLoader + " " + Arrays.toString(((URLClassLoader) classLoader).getURLs());
        }
        return String.valueOf(classLoader);
    }
}
