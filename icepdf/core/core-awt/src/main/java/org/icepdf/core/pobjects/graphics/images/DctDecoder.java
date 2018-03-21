/*
 * Copyright 2006-2018 ICEsoft Technologies Canada Corp.
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
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DctDecoder extends AbstractImageDecoder {

    private static final Logger logger =
            Logger.getLogger(DctDecoder.class.toString());

    private static final int JPEG_ENC_UNKNOWN_PROBABLY_YCbCr = 0;
    private static final int JPEG_ENC_RGB = 1;
    private static final int JPEG_ENC_CMYK = 2;
    private static final int JPEG_ENC_YCbCr = 3;
    private static final int JPEG_ENC_YCCK = 4;
    private static final int JPEG_ENC_GRAY = 5;

    private static final int TRANSFORM_POSITION = 11;
    private static final String ADOBE = "Adobe";

    DctDecoder(ImageStream imageStream, GraphicsState graphicsState) {
        super(imageStream, graphicsState);
    }

    /**
     * The DCTDecode filter decodes grayscale or color image data that has been
     * encoded in the JPEG baseline format.  Because DCTDecode only deals
     * with images, the instance of image is update instead of decoded
     * stream.
     *
     * @return buffered images representation of the decoded JPEG data.  Null
     * if the image could not be properly decoded.
     */
    @Override
    public BufferedImage decode() {
        // BIS's buffer size should be equal to mark() size, and greater than data size (below)
        InputStream input = imageStream.getDecodedByteArrayInputStream();
        // Used to just read 1000, but found a PDF that included thumbnails first
        final int MAX_BYTES_TO_READ_FOR_ENCODING = 2048;
        BufferedInputStream bufferedInput = new BufferedInputStream(
                input, MAX_BYTES_TO_READ_FOR_ENCODING);
        bufferedInput.mark(MAX_BYTES_TO_READ_FOR_ENCODING);

        // We don't use the PColorSpace to determine how to decode the JPEG, because it tends to be wrong
        // Some files say DeviceCMYK, or ICCBased, when neither would work, because it's really YCbCrA
        // What does work though, is to look into the JPEG headers them self, via getJPEGEncoding()

        int jpegEncoding;
        BufferedImage tmpImage = null;
        ImageReader reader = null;
        ImageInputStream imageInputStream = null;
        try {
            ImageParams imageParams = imageStream.getImageParams();
            // get the full image data.
            byte[] data = imageStream.getDecodedStreamBytes(imageParams.getDataLength());

            int dataRead = data.length;
            if (dataRead > MAX_BYTES_TO_READ_FOR_ENCODING) {
                dataRead = MAX_BYTES_TO_READ_FOR_ENCODING;
            }


            imageInputStream = ImageIO.createImageInputStream(new ByteArrayInputStream(data));

            // get a reader that supports getting the raster.
            Iterator<ImageReader> iter = ImageIO.getImageReaders(imageInputStream);
            while (iter.hasNext()) {
                reader = iter.next();
                if (reader.canReadRaster()) {
                    if (logger.isLoggable(Level.FINER)) {
                        logger.finer("DCTDecode Image reader: " + reader + " " +
                                imageParams.getWidth() + "x" + imageParams.getHeight());
                    }
                    break;
                }
            }
            // should never happen but bail on an empty reader.
            if (reader == null) {
                imageInputStream.close();
                return null;
            }
            reader.setInput(imageInputStream, true, true);
            // read the raster data only, as we have our own logic to covert
            // the raster data to RGB colours.
            ImageReadParam param = reader.getDefaultReadParam();
            WritableRaster wr = (WritableRaster) reader.readRaster(0, param);

            // check the encoding type for colour conversion.
            jpegEncoding = getJPEGEncoding(data, dataRead);
            if (jpegEncoding == 0) {
                // try and find the Adobe transfer meta data.
                jpegEncoding = getAdobeTransform(imageInputStream);
            }
            PColorSpace colourSpace = imageParams.getColourSpace();
            int bitsPerComponent = imageParams.getBitsPerComponent();
            float[] decode = imageParams.getDecode();
            int bands = wr.getNumBands();

            if (jpegEncoding == JPEG_ENC_RGB && bitsPerComponent == 8) {
                tmpImage = ImageUtility.convertSpaceToRgb(wr, colourSpace, decode);
            } else if (jpegEncoding == JPEG_ENC_CMYK && bitsPerComponent == 8 && bands > 1) {
                tmpImage = ImageUtility.convertCmykToRgb(wr, decode);
            } else if (jpegEncoding == JPEG_ENC_YCbCr && bitsPerComponent == 8 && bands > 1) {
                tmpImage = ImageUtility.convertYCbCrToRGB(wr, decode);
            } else if (jpegEncoding == JPEG_ENC_YCCK && bitsPerComponent == 8 && bands > 1) {
                // YCCK to RGB works better if an CMYK intermediate is used, but slower.
                tmpImage = ImageUtility.convertYCCKToRgb(wr, decode);
            } else if (jpegEncoding == JPEG_ENC_GRAY && bitsPerComponent == 8) {
                // In DCTDecode with ColorSpace=DeviceGray, the samples are gray values (2000_SID_Service_Info.core)
                // In DCTDecode with ColorSpace=Separation, the samples are Y values (45-14550BGermanForWeb.core AKA 4570.core)
                // Avoid converting images that are already likely gray.
                if (!(colourSpace instanceof DeviceGray) &&
                        !(colourSpace instanceof ICCBased) &&
                        !(colourSpace instanceof Indexed)) {
                    if (colourSpace instanceof Separation &&
                            ((Separation) colourSpace).isNamedColor()) {
                        tmpImage = ImageUtility.convertGrayToRgb(wr, decode);
                    } else {
                        tmpImage = ImageUtility.convertSpaceToRgb(wr, colourSpace, decode);
                    }
                } else {
                    if (colourSpace instanceof Indexed) {
                        tmpImage = ImageUtility.applyIndexColourModel(wr, colourSpace, bitsPerComponent);
                    } else if (wr.getNumBands() == 1) {
                        tmpImage = ImageUtility.makeGrayBufferedImage(wr);
                    } else {
                        tmpImage = ImageUtility.convertYCbCrToRGB(wr, decode);
                    }
                }
            } else {
                if (colourSpace instanceof Indexed) {
                    return ImageUtility.applyIndexColourModel(wr, colourSpace, bitsPerComponent);
                } // assume gray based jpeg.
                if (wr.getNumBands() == 1) {
                    tmpImage = ImageUtility.convertSpaceToRgb(wr, colourSpace, decode);
                } else if (wr.getNumBands() == 2) {
                    tmpImage = ImageUtility.convertGrayToRgb(wr, decode);
                }
                // otherwise assume YCbCr bands = 3.
                else if (wr.getNumBands() == 3) {
                    tmpImage = ImageUtility.convertYCbCrToRGB(wr, decode);
                }
                // still some corner cases around 4  components and one or the other.
                else if (wr.getNumBands() == 4 && !(colourSpace instanceof ICCBased)) {
                    tmpImage = ImageUtility.convertCmykToRgb(wr, decode);
                } else {
                    tmpImage = ImageUtility.convertYCbCrToRGB(wr, decode);
                }
            }

        } catch (IOException e) {
            logger.log(Level.FINE, "Problem loading JPEG image via ImageIO: ", e);
        } finally {
            try {
                input.close();
                // clean up the image reader and image stream
                if (reader != null) {
                    reader.dispose();
                }
                if (imageInputStream != null) {
                    imageInputStream.close();
                }
            } catch (IOException e) {
                logger.log(Level.FINE, "Problem loading JPEG image via ImageIO: ", e);
            }
        }
        return tmpImage;
    }

    private int getJPEGEncoding(byte[] data, int dataLength) {
        int jpegEncoding = JPEG_ENC_UNKNOWN_PROBABLY_YCbCr;

        boolean foundAPP14 = false;
        byte compsTypeFromAPP14 = 0;
        boolean foundSOF = false;
        int numCompsFromSOF = 0;
        boolean foundSOS = false;
        int numCompsFromSOS = 0;

        int index = 0;
        while (true) {
            if (index >= dataLength)
                break;
            if (data[index] != ((byte) 0xFF))
                break;
            if (foundAPP14 && foundSOF)
                break;
            byte segmentType = data[index + 1];
            index += 2;
            if (segmentType == ((byte) 0xD8)) {
                //System.out.println("Found SOI (0xD8)");
                continue;
            }

            //System.out.println("Segment: " + Integer.toHexString( ((int)segmentType)&0xFF ));
            int length = (((data[index] << 8)) & 0xFF00) + (((int) data[index + 1]) & 0xFF);
            //System.out.println("   Length: " + length + "    Index: " + index);

            // APP14 (Might be Adobe file)
            if (segmentType == ((byte) 0xEE)) {
                //System.out.println("Found APP14 (0xEE)");
                if (length >= 14) {
                    foundAPP14 = true;
                    compsTypeFromAPP14 = data[index + 13];
                    //System.out.println("APP14 format: " + compsTypeFromAPP14);
                }
            } else if (segmentType == ((byte) 0xC0)) {
                foundSOF = true;
                //System.out.println("Found SOF (0xC0)  Start Of Frame");
                //int bitsPerSample = ( ((int)data[index+2]) & 0xFF );
                //int imageHeight = ( ((int)(data[index+3] << 8)) & 0xFF00 ) + ( ((int)data[index+4]) & 0xFF );
                //int imageWidth = ( ((int)(data[index+5] << 8)) & 0xFF00 ) + ( ((int)data[index+6]) & 0xFF );
                numCompsFromSOF = (((int) data[index + 7]) & 0xFF);
                //System.out.println("   bitsPerSample: " + bitsPerSample + ", imageWidth: " + imageWidth + ", imageHeight: " + imageHeight + ", numComps: " + numCompsFromSOF);
                //int[] compIds = new int[numCompsFromSOF];
                //for(int i = 0; i < numCompsFromSOF; i++) {
                //    compIds[i] = ( ((int)data[index+8+(i*3)]) & 0xff );
                //    System.out.println("    compId: " + compIds[i]);
                //}
            } else if (segmentType == ((byte) 0xDA)) {
                foundSOS = true;
                //System.out.println("Found SOS (0xDA)  Start Of Scan");
                numCompsFromSOS = (((int) data[index + 2]) & 0xFF);
                //int[] compIds = new int[numCompsFromSOS];
                //for(int i = 0; i < numCompsFromSOS; i++) {
                //    compIds[i] = ( ((int)data[index+3+(i*2)]) & 0xff );
                //    System.out.println("    compId: " + compIds[i]);
                //}
            }

            //System.out.println("   Data: " + org.icepdf.core.util.Utils.convertByteArrayToHexString( data, index+2, Math.min(length-2,dataLength-index-2), true, 20, '\n' ));
            index += length;
        }

        if (foundAPP14 && foundSOF) {
            if (compsTypeFromAPP14 == 0) {       // 0 seems to indicate no conversion
                if (numCompsFromSOF == 1)
                    jpegEncoding = JPEG_ENC_GRAY;
                if (numCompsFromSOF == 3)        // Most assume RGB. DesignJava_times_roman_substitution.PDF supports this.
                    jpegEncoding = JPEG_ENC_RGB;
                else if (numCompsFromSOF == 4)   // CMYK
                    jpegEncoding = JPEG_ENC_CMYK;
            } else if (compsTypeFromAPP14 == 1) {  // YCbCr
                jpegEncoding = JPEG_ENC_YCbCr;
            } else if (compsTypeFromAPP14 == 2) {  // YCCK
                jpegEncoding = JPEG_ENC_YCCK;
            }
        } else if (foundSOS) {
            if (numCompsFromSOS == 1)
                jpegEncoding = JPEG_ENC_GRAY; // Y
            else if (numCompsFromSOS == 3)
                jpegEncoding = JPEG_ENC_YCbCr;
            else if (numCompsFromSOS == 4)
                jpegEncoding = JPEG_ENC_CMYK;
        }
        return jpegEncoding;
    }

    // See AdobeDCT in https://github.com/haraldk/TwelveMonkeys/
    private int getAdobeTransform(ImageInputStream iis) throws IOException {
        int a = 0;
        iis.seek(0);
        int by;
        while ((by = iis.read()) != -1) {
            if (ADOBE.charAt(a) == by) {
                a++;
                if (a != ADOBE.length()) {
                    continue;
                }
                // match
                a = 0;
                long afterAdobePos = iis.getStreamPosition();
                iis.seek(afterAdobePos - 9);
                int tag = iis.readUnsignedShort();
                if (tag != 0xFFEE) {
                    iis.seek(afterAdobePos);
                    continue;
                }
                int len = iis.readUnsignedShort();
                if (len > TRANSFORM_POSITION) {
                    byte[] app14 = new byte[Math.max(len, TRANSFORM_POSITION + 1)];
                    if (iis.read(app14) > TRANSFORM_POSITION) {
                        int value = app14[TRANSFORM_POSITION];
                        if (value == 0) {
                            return JPEG_ENC_UNKNOWN_PROBABLY_YCbCr;
                        } else if (value == 1) {
                            return JPEG_ENC_YCbCr;
                        } else if (value == 2) {
                            return JPEG_ENC_YCCK;
                        }
                    }
                }
            } else {
                a = 0;
            }
        }
        return JPEG_ENC_UNKNOWN_PROBABLY_YCbCr;
    }
}
