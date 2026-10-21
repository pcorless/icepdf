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

import org.icepdf.core.pobjects.graphics.*;

import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

public class JpxDecoder extends AbstractImageDecoder {

    private static final Logger logger =
            Logger.getLogger(JpxDecoder.class.getName());

    private static final String MISSING_DECODER =
            "No JPEG 2000 ImageIO plug-in is installed, so JPXDecode images are not drawn. " +
                    "Add com.github.jai-imageio:jai-imageio-jpeg2000 to the class path to render them.";

    private static final AtomicBoolean missingDecoderWarned = new AtomicBoolean();

    public JpxDecoder(ImageStream imageStream, GraphicsState graphicsState) {
        super(imageStream, graphicsState);
    }

    /**
     * JPEG 2000 decoding is optional: core doesn't ship a decoder because the usual one,
     * jai-imageio-jpeg2000, isn't Apache licensed.  Applications can use this to tell their users why
     * JPXDecode images are missing.
     *
     * @return true if an ImageIO reader for JPEG 2000 is on the class path.
     */
    public static boolean isDecoderAvailable() {
        return ImageIO.getImageReadersByFormatName("JPEG2000").hasNext();
    }

    @Override
    public BufferedImage decode() {

        BufferedImage tmpImage = null;
        try {
            if (!isDecoderAvailable()) {
                // the image is skipped, which leaves a hole in the page; say so loudly once, then quietly
                if (missingDecoderWarned.compareAndSet(false, true)) {
                    logger.warning(MISSING_DECODER);
                } else {
                    logger.fine(MISSING_DECODER);
                }
                return null;
            }
            ImageParams imageParams = imageStream.getImageParams();

            // the filter passes this encoding through undecoded, so the result is the compressed data;
            // presizing it to the decoded raster size would allocate far more than it holds.
            // some encoders under-count tile-parts, which makes the reader drop the last one (see Jpeg2000TileParts)
            byte[] data = Jpeg2000TileParts.repairTilePartCounts(imageStream.getDecodedStreamBytes(0));
            PColorSpace colourSpace = imageParams.getColourSpace();
            if (colourSpace instanceof Indexed) {
                // the samples are indices into the PDF's table, which replaces any palette (pclr) in the JP2
                // wrapper; a reader would expand that palette (Chevron_2010.pdf: four bands of CMYK), so decode
                // the bare codestream and every reader hands back the indices.
                byte[] codestream = Jpeg2000TileParts.unwrapCodestream(data);
                if (codestream != null) {
                    data = codestream;
                }
            }
            try {
                tmpImage = read(data);
            } catch (IOException | RuntimeException e) {
                // a JP2 wrapper with a broken box (attachment.pdf: a cmap of junk outside jp2h) fails the whole
                // read; the PDF's /ColorSpace stands in for the wrapper's colour boxes, so decode the bare codestream.
                byte[] codestream = Jpeg2000TileParts.unwrapCodestream(data);
                if (codestream == null) {
                    throw e;
                }
                logger.log(Level.FINE, "JPEG2000 wrapper unreadable, decoding its codestream alone: ", e);
                tmpImage = read(codestream);
            }
            if (tmpImage == null) {
                return null;
            }
            WritableRaster wr = tmpImage.getRaster();

            // quick sanity check to try and scale really large images before we get into heap trouble.
            if (isImageReallyBig(wr)) {
                wr = scaleReallyBigImages(wr);
            }

            int bitsPerComponent = imageParams.getBitsPerComponent();
            float[] decode = imageParams.getDecode();

            // like raw images (ImageUtility), an ICCBased image is drawn through its alternate (from /N):
            // the profile isn't applied to image samples. Applying it here only ever worked for N=3.
            if (colourSpace instanceof ICCBased) {
                colourSpace = ((ICCBased) colourSpace).getAlternate();
            }
            // apply respective colour models to the JPEG2000 image.
            if (colourSpace instanceof DeviceRGB && bitsPerComponent == 8) {
                tmpImage = ImageUtility.convertSpaceToRgb(wr, colourSpace, decode);
            } else if (colourSpace instanceof DeviceCMYK && bitsPerComponent == 8) {
                tmpImage = ImageUtility.convertCmykToRgb(wr, decode);
            } else if ((colourSpace instanceof DeviceGray)
                    && bitsPerComponent == 8) {
                tmpImage = ImageUtility.makeGrayBufferedImage(wr);
            } else if (colourSpace instanceof Separation) {
                if (((Separation) colourSpace).isNamedColor()) {
                    tmpImage = ImageUtility.convertGrayToRgb(wr, decode);
//                    tmpImage = ImageUtility.makeGrayBufferedImage(wr);
                } else {
                    tmpImage = ImageUtility.convertSpaceToRgb(wr, colourSpace, decode);
                }
            } else if (colourSpace instanceof Indexed) {
                tmpImage = ImageUtility.applyIndexColourModel(wr, colourSpace, bitsPerComponent);
            }
        } catch (IOException e) {
            logger.log(Level.FINE, "Problem loading JPEG2000 image: ", e);
        }

        return tmpImage;
    }

    private BufferedImage read(byte[] data) throws IOException {
        ImageInputStream imageInputStream = ImageIO.createImageInputStream(
                new ByteArrayInputStream(data));

        // getting the raster for JPX seems to fail in most cases.
        Iterator<ImageReader> iter = ImageIO.getImageReaders(imageInputStream);
        ImageReader reader = null;
        while (iter.hasNext()) {
            reader = iter.next();
            if (reader.canReadRaster()) {
                if (logger.isLoggable(Level.FINER)) {
                    logger.finer("JPXDecode Image reader: " + reader);
                }
                break;
            }
        }

        // read the raster data only, as we have our own logic to covert
        // the raster data to RGB colours.
        if (reader == null) {
            imageInputStream.close();
            return null;
        }
        ImageReadParam param = reader.getDefaultReadParam();
        reader.setInput(imageInputStream, true, true);
        try {
            // subsample a really big image while decoding rather than build the full raster and scale it
            // down after (see DctDecoder); the JPEG 2000 reader decodes a lower resolution level for it.
            int subsampling = subsamplingFor(reader.getWidth(0), reader.getHeight(0));
            if (subsampling > 1) {
                param.setSourceSubsampling(subsampling, subsampling, 0, 0);
            }
            return reader.read(0, param);
        } finally {
            reader.dispose();
            imageInputStream.close();
        }
    }
}
