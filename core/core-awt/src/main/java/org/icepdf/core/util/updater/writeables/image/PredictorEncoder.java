/*
 * Copyright 2014 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.icepdf.core.util.updater.writeables.image;

import org.icepdf.core.pobjects.DictionaryEntries;
import org.icepdf.core.pobjects.Stream;
import org.icepdf.core.pobjects.graphics.images.ImageStream;

import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;
import java.awt.image.Raster;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

import static org.icepdf.core.pobjects.filters.FlateDecode.*;
import static org.icepdf.core.pobjects.filters.PredictorDecode.PREDICTOR_PNG_OPTIMUM;
import static org.icepdf.core.pobjects.graphics.DeviceRGB.DEVICERGB_KEY;
import static org.icepdf.core.pobjects.graphics.images.ImageParams.BITS_PER_COMPONENT_KEY;
import static org.icepdf.core.pobjects.graphics.images.ImageParams.COLORSPACE_KEY;

/**
 * Factory for creating a ImageStream containing a PNG compressed image.
 *
 * @author Tilman Hausherr
 * @version <p>
 * * Taken from commit cc02281746af9938e6e95d5b2d61a18b5c743b71 of  3.0.0  from
 * * pdfbox/src/main/java/org/apache/pdfbox/filter/Predictor.java
 * * <p>
 * * Initial changes for icepdf
 * * - extract PredictorEncoder class
 * * - added compatability for for ImageStreams and DictionaryEntries
 */
class PredictorEncoder implements ImageEncoder {
    private final ImageStream imageStream;
    private final int componentsPerPixel;
    private final int bytesPerComponent;
    private final int bytesPerPixel;

    private final int height;
    private final int width;

    private final byte[] dataRawRowNone;
    private final byte[] dataRawRowSub;
    private final byte[] dataRawRowUp;
    private final byte[] dataRawRowAverage;
    private final byte[] dataRawRowPaeth;

    final int imageType;
    final boolean hasAlpha;
    final byte[] alphaImageData;

    final byte[] aValues;
    final byte[] cValues;
    final byte[] bValues;
    final byte[] xValues;
    final byte[] tmpResultValues;

    /**
     * Initialize the encoder and set all final fields
     */
    PredictorEncoder(ImageStream imageStream) {
        this.imageStream = imageStream;
        BufferedImage image = imageStream.getDecodedImage();
        // The raw count of components per pixel including optional alpha
        this.componentsPerPixel = image.getColorModel().getNumComponents();
        int transferType = image.getRaster().getTransferType();
        this.bytesPerComponent = (transferType == DataBuffer.TYPE_SHORT
                || transferType == DataBuffer.TYPE_USHORT) ? 2 : 1;

        // Only the bytes we need in the output (excluding alpha)
        this.bytesPerPixel = image.getColorModel().getNumColorComponents() * bytesPerComponent;

        this.height = image.getHeight();
        this.width = image.getWidth();
        this.imageType = image.getType();
        this.hasAlpha = image.getColorModel().getNumComponents() != image.getColorModel()
                .getNumColorComponents();
        this.alphaImageData = hasAlpha ? new byte[width * height * bytesPerComponent] : null;

        // The rows have 1-byte encoding marker and width * BYTES_PER_PIXEL pixel-bytes
        int dataRowByteCount = width * bytesPerPixel + 1;
        this.dataRawRowNone = new byte[dataRowByteCount];
        this.dataRawRowSub = new byte[dataRowByteCount];
        this.dataRawRowUp = new byte[dataRowByteCount];
        this.dataRawRowAverage = new byte[dataRowByteCount];
        this.dataRawRowPaeth = new byte[dataRowByteCount];

        // Write the encoding markers
        dataRawRowNone[0] = 0;
        dataRawRowSub[0] = 1;
        dataRawRowUp[0] = 2;
        dataRawRowAverage[0] = 3;
        dataRawRowPaeth[0] = 4;

        // c | b
        // -----
        // a | x
        //
        // x => current pixel
        this.aValues = new byte[bytesPerPixel];
        this.cValues = new byte[bytesPerPixel];
        this.bValues = new byte[bytesPerPixel];
        this.xValues = new byte[bytesPerPixel];
        this.tmpResultValues = new byte[bytesPerPixel];
    }

    /**
     * Tries to compress the image using a predictor.
     *
     * @return the image or null if it is not possible to encoded the image (e.g. not supported
     * raster format etc.)
     */
    public ImageStream encode() throws IOException {
        BufferedImage image = imageStream.getDecodedImage();
        Raster imageRaster = image.getRaster();
        final int elementsInRowPerPixel;

        // These variables store a row of the image each, the exact type depends
        // on the image encoding. Can be a int[], short[] or byte[]
        Object prevRow;
        Object transferRow;

        switch (imageType) {
            case BufferedImage.TYPE_CUSTOM:
                switch (imageRaster.getTransferType()) {
                    case DataBuffer.TYPE_USHORT:
                        elementsInRowPerPixel = componentsPerPixel;
                        prevRow = new short[width * elementsInRowPerPixel];
                        transferRow = new short[width * elementsInRowPerPixel];
                        break;
                    case DataBuffer.TYPE_BYTE:
                        elementsInRowPerPixel = componentsPerPixel;
                        prevRow = new byte[width * elementsInRowPerPixel];
                        transferRow = new byte[width * elementsInRowPerPixel];
                        break;
                    default:
                        return null;
                }
                break;

            case BufferedImage.TYPE_3BYTE_BGR:
            case BufferedImage.TYPE_4BYTE_ABGR:
                elementsInRowPerPixel = componentsPerPixel;
                prevRow = new byte[width * elementsInRowPerPixel];
                transferRow = new byte[width * elementsInRowPerPixel];
                break;

            case BufferedImage.TYPE_INT_BGR:
            case BufferedImage.TYPE_INT_ARGB:
            case BufferedImage.TYPE_INT_RGB:
                elementsInRowPerPixel = 1;
                prevRow = new int[width * elementsInRowPerPixel];
                transferRow = new int[width * elementsInRowPerPixel];
                break;

            default:
                // We can not handle this unknown format
                return null;
        }

        final int elementsInTransferRow = width * elementsInRowPerPixel;

        // pre-size the output stream to half of the maximum size
        ByteArrayOutputStream stream = new ByteArrayOutputStream(height * width * bytesPerPixel / 2);
        Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION);
        DeflaterOutputStream zip = new DeflaterOutputStream(stream, deflater);

        int alphaPtr = 0;

        for (int rowNum = 0; rowNum < height; rowNum++) {
            imageRaster.getDataElements(0, rowNum, width, 1, transferRow);

            // We start to write at index one, as the predictor marker is in index zero
            int writerPtr = 1;
            Arrays.fill(aValues, (byte) 0);
            Arrays.fill(cValues, (byte) 0);

            final byte[] transferRowByte;
            final byte[] prevRowByte;
            final int[] transferRowInt;
            final int[] prevRowInt;
            final short[] transferRowShort;
            final short[] prevRowShort;

            if (transferRow instanceof byte[]) {
                transferRowByte = (byte[]) transferRow;
                prevRowByte = (byte[]) prevRow;
                transferRowInt = prevRowInt = null;
                transferRowShort = prevRowShort = null;
            } else if (transferRow instanceof int[]) {
                transferRowInt = (int[]) transferRow;
                prevRowInt = (int[]) prevRow;
                transferRowShort = prevRowShort = null;
                transferRowByte = prevRowByte = null;
            } else {
                // This must be short[]
                transferRowShort = (short[]) transferRow;
                prevRowShort = (short[]) prevRow;
                transferRowInt = prevRowInt = null;
                transferRowByte = prevRowByte = null;
            }

            for (int indexInTransferRow = 0; indexInTransferRow < elementsInTransferRow;
                 indexInTransferRow += elementsInRowPerPixel, alphaPtr += bytesPerComponent) {
                // Copy the pixel values into the byte array
                if (transferRowByte != null) {
                    copyImageBytes(transferRowByte, indexInTransferRow, xValues, alphaImageData,
                            alphaPtr);
                    copyImageBytes(prevRowByte, indexInTransferRow, bValues, null, 0);
                } else if (transferRowInt != null) {
                    copyIntToBytes(transferRowInt, indexInTransferRow, xValues, alphaImageData,
                            alphaPtr);
                    copyIntToBytes(prevRowInt, indexInTransferRow, bValues, null, 0);
                } else {
                    // This must be short[]
                    copyShortsToBytes(transferRowShort, indexInTransferRow, xValues, alphaImageData, alphaPtr);
                    copyShortsToBytes(prevRowShort, indexInTransferRow, bValues, null, 0);
                }

                // Encode the pixel values in the different encodings
                int length = xValues.length;
                for (int bytePtr = 0; bytePtr < length; bytePtr++) {
                    int x = xValues[bytePtr] & 0xFF;
                    int a = aValues[bytePtr] & 0xFF;
                    int b = bValues[bytePtr] & 0xFF;
                    int c = cValues[bytePtr] & 0xFF;
                    dataRawRowNone[writerPtr] = (byte) x;
                    dataRawRowSub[writerPtr] = pngFilterSub(x, a);
                    dataRawRowUp[writerPtr] = pngFilterUp(x, b);
                    dataRawRowAverage[writerPtr] = pngFilterAverage(x, a, b);
                    dataRawRowPaeth[writerPtr] = pngFilterPaeth(x, a, b, c);
                    writerPtr++;
                }

                //  We shift the values into the prev / upper left values for the next pixel
                System.arraycopy(xValues, 0, aValues, 0, bytesPerPixel);
                System.arraycopy(bValues, 0, cValues, 0, bytesPerPixel);
            }

            byte[] rowToWrite = chooseDataRowToWrite();

            // Write and compress the row as long it is hot (CPU cache wise)
            zip.write(rowToWrite, 0, rowToWrite.length);

            // We swap prev and transfer row, so that we have the prev row for the next row.
            Object temp = prevRow;
            prevRow = transferRow;
            transferRow = temp;
        }
        zip.close();
        deflater.end();

        return preparePredictorPDImage(stream, bytesPerComponent * 8);
    }

    private void copyIntToBytes(int[] transferRow, int indexInTranferRow, byte[] targetValues,
                                byte[] alphaImageData, int alphaPtr) {
        int val = transferRow[indexInTranferRow];
        byte b0 = (byte) (val & 0xFF);
        byte b1 = (byte) ((val >> 8) & 0xFF);
        byte b2 = (byte) ((val >> 16) & 0xFF);

        switch (imageType) {
            case BufferedImage.TYPE_INT_BGR:
                targetValues[0] = b0;
                targetValues[1] = b1;
                targetValues[2] = b2;
                break;

            case BufferedImage.TYPE_INT_ARGB:
                targetValues[0] = b2;
                targetValues[1] = b1;
                targetValues[2] = b0;
                if (alphaImageData != null) {
                    byte b3 = (byte) ((val >> 24) & 0xFF);
                    alphaImageData[alphaPtr] = b3;
                }
                break;
            case BufferedImage.TYPE_INT_RGB:
                targetValues[0] = b2;
                targetValues[1] = b1;
                targetValues[2] = b0;
                break;
            default:
                break;
        }
    }

    private void copyImageBytes(byte[] transferRow, int indexInTranferRow, byte[] targetValues,
                                byte[] alphaImageData, int alphaPtr) {
        System.arraycopy(transferRow, indexInTranferRow, targetValues, 0, targetValues.length);
        if (alphaImageData != null) {
            alphaImageData[alphaPtr] = transferRow[indexInTranferRow + targetValues.length];
        }
    }

    private static void copyShortsToBytes(short[] transferRow, int indexInTranferRow,
                                          byte[] targetValues, byte[] alphaImageData, int alphaPtr) {
        int itr = indexInTranferRow;
        for (int i = 0; i < targetValues.length - 1; i += 2) {
            short val = transferRow[itr++];
            targetValues[i] = (byte) ((val >> 8) & 0xFF);
            targetValues[i + 1] = (byte) (val & 0xFF);
        }
        if (alphaImageData != null) {
            short alpha = transferRow[itr];
            alphaImageData[alphaPtr] = (byte) ((alpha >> 8) & 0xFF);
            alphaImageData[alphaPtr + 1] = (byte) (alpha & 0xFF);
        }
    }

    private ImageStream preparePredictorPDImage(ByteArrayOutputStream stream,
                                                int bitsPerComponent) throws IOException {
        BufferedImage image = imageStream.getDecodedImage();
        int width = image.getWidth();

        imageStream.setRawBytes(stream.toByteArray());

        imageStream.getEntries().put(Stream.FILTER_KEY, Stream.FILTER_FLATE_DECODE);
        imageStream.getEntries().put(COLORSPACE_KEY, DEVICERGB_KEY);

        // setup predictor decode params
        if (imageStream.getEntries().get(Stream.DECODEPARAM_KEY) == null) {
            imageStream.getEntries().put(Stream.DECODEPARAM_KEY, new DictionaryEntries());
        }
        DictionaryEntries decodeParams = imageStream.getLibrary().getDictionary(imageStream.getEntries(),
                Stream.DECODEPARAM_KEY);
        decodeParams.put(BITS_PER_COMPONENT_KEY, bitsPerComponent);
        decodeParams.put(PREDICTOR_KEY, PREDICTOR_PNG_OPTIMUM);
        decodeParams.put(COLUMNS_KEY, width);
        decodeParams.put(COLORS_KEY, image.getColorModel().getNumColorComponents());

        return imageStream;
    }

    /**
     * We look which row encoding is the "best" one, ie. has the lowest sum. We don't implement
     * anything fancier to choose the right row encoding. This is just the recommend algorithm
     * in the spec. The get the perfect encoding you would need to do a brute force check how
     * all the different encoded rows compress in the zip stream together. You have would have
     * to check 5*image-height permutations...
     *
     * @return the "best" row encoding of the row encodings
     */
    private byte[] chooseDataRowToWrite() {
        byte[] rowToWrite = dataRawRowNone;
        long estCompressSum = estCompressSum(dataRawRowNone);
        long estCompressSumSub = estCompressSum(dataRawRowSub);
        long estCompressSumUp = estCompressSum(dataRawRowUp);
        long estCompressSumAvg = estCompressSum(dataRawRowAverage);
        long estCompressSumPaeth = estCompressSum(dataRawRowPaeth);
        if (estCompressSum > estCompressSumSub) {
            rowToWrite = dataRawRowSub;
            estCompressSum = estCompressSumSub;
        }
        if (estCompressSum > estCompressSumUp) {
            rowToWrite = dataRawRowUp;
            estCompressSum = estCompressSumUp;
        }
        if (estCompressSum > estCompressSumAvg) {
            rowToWrite = dataRawRowAverage;
            estCompressSum = estCompressSumAvg;
        }
        if (estCompressSum > estCompressSumPaeth) {
            rowToWrite = dataRawRowPaeth;
        }
        return rowToWrite;
    }

    /*
     * PNG Filters, see https://www.w3.org/TR/PNG-Filters.html
     */
    private static byte pngFilterSub(int x, int a) {
        return (byte) ((x & 0xFF) - (a & 0xFF));
    }

    private static byte pngFilterUp(int x, int b) {
        // Same as pngFilterSub, just called with the prior row
        return pngFilterSub(x, b);
    }

    private static byte pngFilterAverage(int x, int a, int b) {
        return (byte) (x - ((b + a) / 2));
    }

    private static byte pngFilterPaeth(int x, int a, int b, int c) {
        int p = a + b - c;
        int pa = Math.abs(p - a);
        int pb = Math.abs(p - b);
        int pc = Math.abs(p - c);
        final int pr;
        if (pa <= pb && pa <= pc) {
            pr = a;
        } else if (pb <= pc) {
            pr = b;
        } else {
            pr = c;
        }

        int r = x - pr;
        return (byte) (r);
    }

    private static long estCompressSum(byte[] dataRawRowSub) {
        long sum = 0;
        for (byte aDataRawRowSub : dataRawRowSub) {
            // https://www.w3.org/TR/PNG-Encoders.html#E.Filter-selection
            sum += Math.abs(aDataRawRowSub);
        }
        return sum;
    }
}