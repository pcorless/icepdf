package org.icepdf.core.util.updater.writeables.image;

import org.icepdf.core.pobjects.DictionaryEntries;
import org.icepdf.core.pobjects.Stream;
import org.icepdf.core.pobjects.graphics.images.ImageStream;

import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

import static org.icepdf.core.pobjects.graphics.DeviceGray.DEVICEGRAY_KEY;
import static org.icepdf.core.pobjects.graphics.DeviceRGB.DEVICERGB_KEY;
import static org.icepdf.core.pobjects.graphics.images.FaxDecoder.COLUMNS_KEY;
import static org.icepdf.core.pobjects.graphics.images.FaxDecoder.K_KEY;
import static org.icepdf.core.pobjects.graphics.images.ImageParams.BITS_PER_COMPONENT_KEY;
import static org.icepdf.core.pobjects.graphics.images.ImageParams.COLORSPACE_KEY;

/**
 * Raw raster encoder,  not the best compression but gets the job done in a pinch.
 */
public class RasterEncoder implements ImageEncoder {

    private final ImageStream imageStream;

    public RasterEncoder(ImageStream imageStream) {
        this.imageStream = imageStream;
    }

    @Override
    public ImageStream encode() throws IOException {
        byte[] byteArray;
        if (isGrayScaleImage(imageStream)) {
            byteArray = createFromGrayScaleImage(imageStream);
        } else {
            byteArray = createFromRGBImage(imageStream);
        }
        byte[] outputData = createFlateEncodedBytes(byteArray);

        imageStream.getEntries().put(Stream.FILTER_KEY, Stream.FILTER_FLATE_DECODE);
        imageStream.setRawBytes(outputData);
        return imageStream;
    }

    private static byte[] createFlateEncodedBytes(byte[] byteArray) throws IOException {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream(byteArray.length / 2);
        ByteArrayInputStream input = new ByteArrayInputStream(byteArray);
        Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION);
        try (DeflaterOutputStream out = new DeflaterOutputStream(byteArrayOutputStream, deflater)) {
            input.transferTo(out);
        }
        byteArrayOutputStream.flush();
        deflater.end();
        return byteArrayOutputStream.toByteArray();
    }

    private byte[] createFromRGBImage(ImageStream imageStream) {
        BufferedImage image = imageStream.getDecodedImage();
        int height = image.getHeight();
        int width = image.getWidth();
        int[] buffer = new int[width];
        byte[] imageBytes = new byte[width * height * 3];
        for (int y = 0, i = 0; y < height; y++) {
            for (int pixel : image.getRGB(0, y, width, 1, buffer, 0, width)) {
                imageBytes[i++] = (byte) ((pixel >> 16) & 0xFF);
                imageBytes[i++] = (byte) ((pixel >> 8) & 0xFF);
                imageBytes[i++] = (byte) (pixel & 0xFF);
            }
        }
        imageStream.getEntries().remove(Stream.DECODEPARAM_KEY);
        imageStream.getEntries().put(COLORSPACE_KEY, DEVICERGB_KEY);
        imageStream.getEntries().put(BITS_PER_COMPONENT_KEY, image.getColorModel().getPixelSize() / 3);
        return imageBytes;
    }

    private byte[] createFromGrayScaleImage(ImageStream imageStream) throws IOException {
        BufferedImage image = imageStream.getDecodedImage();
        int height = image.getHeight();
        int width = image.getWidth();
        int[] lineBuffer = new int[width];
        int bitPerComponent = image.getColorModel().getPixelSize();
        ByteArrayOutputStream byteArrayOutputStream =
                new ByteArrayOutputStream(((width * bitPerComponent / 8) +
                        (width * bitPerComponent % 8 != 0 ? 1 : 0)) * height);
        try (MemoryCacheImageOutputStream memoryCacheImageOutputStream =
                     new MemoryCacheImageOutputStream(byteArrayOutputStream)) {
            for (int y = 0; y < height; y++) {
                for (int pixel : image.getRGB(0, y, width, 1, lineBuffer, 0, width)) {
                    memoryCacheImageOutputStream.writeBits(pixel & 0xFF, bitPerComponent);
                }
                int bitOffset = memoryCacheImageOutputStream.getBitOffset();
                if (bitOffset != 0) {
                    memoryCacheImageOutputStream.writeBits(0, 8 - bitOffset);
                }
            }
            memoryCacheImageOutputStream.flush();
        }
        imageStream.getEntries().put(COLORSPACE_KEY, DEVICEGRAY_KEY);
        imageStream.getEntries().put(BITS_PER_COMPONENT_KEY, image.getColorModel().getPixelSize());
        if (imageStream.getEntries().get(Stream.DECODEPARAM_KEY) != null) {
            DictionaryEntries decodeParams = imageStream.getLibrary().getDictionary(imageStream.getEntries(),
                    Stream.DECODEPARAM_KEY);
            decodeParams.remove(K_KEY);
            decodeParams.remove(COLUMNS_KEY);
        }
        return byteArrayOutputStream.toByteArray();
    }

    private static boolean isGrayScaleImage(ImageStream imageStream) {
        BufferedImage image = imageStream.getDecodedImage();
        if (image.getType() == BufferedImage.TYPE_BYTE_GRAY && image.getColorModel().getPixelSize() <= 8) {
            return true;
        }
        if (image.getType() == BufferedImage.TYPE_BYTE_BINARY && image.getColorModel().getPixelSize() == 1) {
            return true;
        }
        return false;
    }
}