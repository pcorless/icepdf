/*
 * Copyright 2006-2017 ICEsoft Technologies Canada Corp.
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

import org.icepdf.core.pobjects.Name;
import org.icepdf.core.pobjects.Stream;
import org.icepdf.core.pobjects.graphics.GraphicsState;

import javax.imageio.ImageIO;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public class JBig2Decoder extends AbstractImageDecoder {

    private static final Logger logger =
            Logger.getLogger(JBig2Decoder.class.toString());

    private static final String[] JBIG2_PDF_BOX = new String[]{
            "org.apache.pdfbox.jbig2.JBIG2ImageReader",
            "org.apache.pdfbox.jbig2.JBIG2ImageReaderSpi",
            "org.apache.pdfbox.jbig2.JBIG2Globals"};

    private static final String[] JBIG2_LEVIGO = new String[]{
            "com.levigo.jbig2.JBIG2ImageReader",
            "com.levigo.jbig2.JBIG2ImageReaderSpi",
            "com.levigo.jbig2.jbig2.JBIG2Globals"};

    private static final Name JBIG2_GLOBALS_KEY = new Name("JBIG2Globals");
    private static final Name DECODE_PARMS_KEY = new Name("DecodeParms");

    public JBig2Decoder(ImageStream imageStream, GraphicsState graphicsState) {
        super(imageStream, graphicsState);
    }

    @Override
    public BufferedImage decode() {

        BufferedImage tmpImage = null;

        ImageParams imageParams = imageStream.getImageParams();
        // get the decode params form the stream
        HashMap decodeParams = imageParams.getDictionary(DECODE_PARMS_KEY);
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

            // ICEpdf-pro has a commercial license of the levigo library but the OS library can use it to if the project
            // can comply with levigo's open source licence.
            tmpImage = decodePDFBoxJbig2(decodeParams, globalsStream, imageInputStream, JBIG2_LEVIGO);
        } catch (IOException | InstantiationException | InvocationTargetException | NoSuchMethodException |
                IllegalAccessException | ClassNotFoundException e) {
            logger.log(Level.WARNING, "Could not find Levigo JBIG2 library on class path.");
            try {
                tmpImage = decodePDFBoxJbig2(decodeParams, globalsStream, imageInputStream, JBIG2_PDF_BOX);
            } catch (IOException | InstantiationException | InvocationTargetException | NoSuchMethodException |
                    IllegalAccessException | ClassNotFoundException ex) {
                logger.log(Level.WARNING, "Could not find Apache JBIG2 library on class path.");
            }
        } finally {
            // dispose the stream
            if (imageInputStream != null) {
                try {
                    imageInputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        // apply the fill colour and alpha if masking is enabled.
        return tmpImage;

    }

    protected BufferedImage decodePDFBoxJbig2(HashMap decodeParams, Stream globalsStream, ImageInputStream imageInputStream,
                                              String[] jbigClasses)
            throws ClassNotFoundException, IllegalAccessException, InstantiationException, NoSuchMethodException, InvocationTargetException, IOException {
        // ICEpdf-pro has a commercial license of the levigo library but the OS library can use it to if the project
        // can comply with levigo's open source licence.
        Class<?> levigoJBIG2ImageReaderClass = Class.forName(jbigClasses[0]);
        Class<?> jbig2ImageReaderSpiClass = Class.forName(jbigClasses[1]);
        Class<?> jbig2GlobalsClass = Class.forName(jbigClasses[2]);
        Object jbig2ImageReaderSpi = jbig2ImageReaderSpiClass.newInstance();
        Constructor levigoJbig2DecoderClassConstructor =
                levigoJBIG2ImageReaderClass.getDeclaredConstructor(javax.imageio.spi.ImageReaderSpi.class);
        Object levigoJbig2Reader = levigoJbig2DecoderClassConstructor.newInstance(jbig2ImageReaderSpi);
        // set the input
        Class partypes[] = new Class[1];
        partypes[0] = Object.class;
        Object arglist[] = new Object[1];
        arglist[0] = imageInputStream;
        Method setInput =
                levigoJBIG2ImageReaderClass.getMethod("setInput", partypes);
        setInput.invoke(levigoJbig2Reader, arglist);
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
                            levigoJBIG2ImageReaderClass.getMethod("processGlobals", partypes);
                    Object globalSegments = processGlobals.invoke(levigoJbig2Reader, arglist);
                    if (globalSegments != null) {
                        // invoked encoder.setGlobalData(globals);
                        partypes = new Class[1];
                        partypes[0] = jbig2GlobalsClass;
                        arglist = new Object[1];
                        arglist[0] = globalSegments;
                        // pass the segment data back into the decoder.
                        Method setGlobalData =
                                levigoJBIG2ImageReaderClass.getMethod("setGlobals", partypes);
                        setGlobalData.invoke(levigoJbig2Reader, arglist);
                    }
                }
            }
        }
        partypes = new Class[1];
        partypes[0] = int.class;
        arglist = new Object[1];
        arglist[0] = 0;
        Method read =
                levigoJBIG2ImageReaderClass.getMethod("read", partypes);
        BufferedImage tmpImage = (BufferedImage) read.invoke(levigoJbig2Reader, arglist);
        // call dispose on the reader
        Method dispose =
                levigoJBIG2ImageReaderClass.getMethod("dispose");
        dispose.invoke(levigoJbig2Reader);
        return tmpImage;
    }
}
