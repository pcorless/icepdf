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


import org.icepdf.core.pobjects.*;
import org.icepdf.core.pobjects.graphics.DeviceGray;
import org.icepdf.core.pobjects.graphics.GraphicsState;
import org.icepdf.core.pobjects.graphics.PColorSpace;
import org.icepdf.core.util.Library;

import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.util.logging.Logger;

import static org.icepdf.core.pobjects.graphics.images.ImageParams.*;

/**
 * ImageStream contains image data that is contains in an XObject of subtype
 * Image.
 *
 * @since 5.0
 */
public class ImageStream extends Stream {

    private static final Logger logger = Logger.getLogger(ImageStream.class.getName());

    public static final Name TYPE_VALUE = new Name("Image");

    private ImageParams imageParams;

    private AffineTransform graphicsTransformMatrix;
    private BufferedImage decodedImage;


    public ImageStream(Library l, DictionaryEntries h, byte[] rawBytes) {
        super(l, h, rawBytes);
        imageParams = new ImageParams(library, entries, null);
    }

    public ImageStream(Library l, DictionaryEntries h, ByteBuffer streamDataView) {
        super(l, h, streamDataView);
        imageParams = new ImageParams(library, entries, null);
    }

    /**
     * @see #getInstance(Library, Reference, Reference, BufferedImage, boolean)
     */
    public static ImageStream getInstance(Library library, Reference reference, BufferedImage bufferedImage,
                                          boolean useAlpha) {
        return getInstance(library, reference, null, bufferedImage, useAlpha);
    }

    /**
     * Builds a new image XObject around a {@code BufferedImage}, ready to be written out, and
     * registers it - and its soft mask, if it needs one - with the document's state manager.
     * <p>
     * Transparency is carried as a soft mask ({@code /SMask}): an eight bit greyscale image of the
     * alpha channel, which is what lets a partly transparent pixel actually be partly transparent.
     * The alternative, a colour key {@code /Mask}, can only say paint or do not paint about a
     * pixel, so every antialiased edge in the image comes out either hard or fringed against
     * whatever colour was keyed out - which is what a scanned or drawn signature is made almost
     * entirely of.  An image whose alpha channel turns out to be fully opaque gets no soft mask at
     * all, so nothing is paid for the check.
     *
     * @param library           document library
     * @param reference         object number to write the image as; a new one is taken when null.
     *                          Passing the previous one back rebuilds the image in place instead of
     *                          leaving the old one behind, which matters when an appearance is
     *                          regenerated each time a setting changes
     * @param softMaskReference object number to write the soft mask as, on the same terms
     * @param bufferedImage     image to write
     * @param useAlpha          whether to carry the image's transparency into the PDF.  When false
     *                          a transparent image is flattened onto white
     * @return the registered image stream, whose {@code /SMask} entry names the soft mask when one
     * was needed
     */
    public static ImageStream getInstance(Library library, Reference reference, Reference softMaskReference,
                                          BufferedImage bufferedImage, boolean useAlpha) {
        // The encoders read the image back through its raster at write time, so it has to be in a
        // shape they recognise before anything is measured off it.
        BufferedImage image = ImageUtility.normalizeForEncoding(bufferedImage, useAlpha);

        DictionaryEntries imageDictionary = new DictionaryEntries();
        imageDictionary.put(TYPE_KEY, Form.TYPE_VALUE);
        imageDictionary.put(SUBTYPE_KEY, ImageStream.TYPE_VALUE);
        imageDictionary.put(BITS_PER_COMPONENT_KEY, 8);
        imageDictionary.put(WIDTH_KEY, image.getWidth());
        imageDictionary.put(HEIGHT_KEY, image.getHeight());
        // The encoder writes the samples and sets the filter and colour space it actually used; this
        // is only what the dictionary says until then.
        imageDictionary.put(FILTER_KEY, FILTER_FLATE_DECODE);

        ImageStream imageStream = new ImageStream(library, imageDictionary, (byte[]) null);
        imageStream.setDecodedImage(image);

        StateManager stateManager = library.getStateManager();
        ImageStream softMask = useAlpha ? createSoftMask(library, softMaskReference, image) : null;
        if (softMask != null) {
            imageDictionary.put(SMASK_KEY, softMask.getPObjectReference());
        } else if (softMaskReference != null) {
            // The image no longer has anything to be masked by, so a mask written for a previous
            // version of it would be an object nothing refers to.
            stateManager.removeChange(new PObject(null, softMaskReference));
        }

        // setup object reference and put in state manager
        if (reference == null) {
            reference = stateManager.getNewReferenceNumber();
        }
        imageStream.setPObjectReference(reference);
        stateManager.addChange(new PObject(imageStream, reference));
        return imageStream;
    }

    /**
     * Builds the {@code /SMask} image holding {@code image}'s alpha channel and registers it.
     *
     * @return the soft mask stream, or null when the image has no alpha channel or is fully opaque
     */
    private static ImageStream createSoftMask(Library library, Reference reference, BufferedImage image) {
        BufferedImage alphaImage = ImageUtility.extractAlpha(image);
        if (alphaImage == null) {
            return null;
        }
        DictionaryEntries maskDictionary = new DictionaryEntries();
        maskDictionary.put(TYPE_KEY, Form.TYPE_VALUE);
        maskDictionary.put(SUBTYPE_KEY, ImageStream.TYPE_VALUE);
        maskDictionary.put(BITS_PER_COMPONENT_KEY, 8);
        maskDictionary.put(WIDTH_KEY, alphaImage.getWidth());
        maskDictionary.put(HEIGHT_KEY, alphaImage.getHeight());
        maskDictionary.put(COLORSPACE_KEY, DeviceGray.DEVICEGRAY_KEY);
        maskDictionary.put(FILTER_KEY, FILTER_FLATE_DECODE);

        ImageStream softMask = new ImageStream(library, maskDictionary, (byte[]) null);
        // Left as a decoded image rather than pre-compressed bytes so that it goes out through the
        // same writer the colour samples do - which is what compresses it and, in an encrypted
        // document, encrypts it.  Raw bytes would be written through unencrypted.
        softMask.setDecodedImage(alphaImage);

        StateManager stateManager = library.getStateManager();
        if (reference == null) {
            reference = stateManager.getNewReferenceNumber();
        }
        softMask.setPObjectReference(reference);
        stateManager.addChange(new PObject(softMask, reference));
        return softMask;
    }

    /**
     * @return the object the image's {@code /SMask} names, or null when it has none.  Useful to a
     * caller that rebuilds an image and wants the mask rewritten in place rather than orphaned
     */
    public Reference getSoftMaskReference() {
        Object softMask = entries.get(SMASK_KEY);
        return softMask instanceof Reference ? (Reference) softMask : null;
    }

    /**
     * Gets the image param wrapper class for quick access to parameters that are needed now!
     *
     * @return image params for the given image stream
     */
    public ImageParams getImageParams() {
        return imageParams;
    }

    /**
     * Gets the image object for the given resource.  This method can optionally
     * scale an image to reduce the total memory footprint or to increase the
     * perceived render quality on screen at low zoom levels.
     *
     * @param graphicsState graphic state for image or parent form
     * @param resources     resources containing image reference
     * @return new image object
     */
    public BufferedImage getImage(GraphicsState graphicsState, Resources resources){
        // check the pool encase we already parse this image.
        imageParams = new ImageParams(library, entries, resources);
        if (pObjectReference != null) {
            BufferedImage tmp = library.getImagePool().get(pObjectReference);
            if (tmp != null) {
                return tmp;
            }
        }
        // corner case when working with newly added images
        if (decodedImage != null) {
            return decodedImage;
        }
        // decode the given image.
        ImageDecoder imageDecoder = ImageDecoderFactory.createDecoder(this, graphicsState);
        BufferedImage decodedImage = imageDecoder.decode();

        // Fallback image code that will use pixel primitives to build out the image.  Only for plain sample
        // data: when a codec (CCITT, DCT, JBIG2, JPX) fails, the stream still holds compressed bytes, and reading
        // those as samples paints noise - an image mask comes out as a solid black page.  Better to skip it.
        if (decodedImage == null) {
            if (imageDecoder instanceof RasterDecoder) {
                decodedImage = new RawDecoder(this, graphicsState).decode();
            } else {
                logger.fine(() -> "Skipping image that could not be decoded: " + pObjectReference);
                return null;
            }
        }
        // GH-501 step 2: the decoder output may carry preserved TRUE CMYK samples
        // (keyed by this object); mask processing below replaces decodedImage with a
        // new BufferedImage, so remember the decoder output to re-key the samples to
        // the final image that actually gets drawn.
        BufferedImage rawDecoded = decodedImage;
        if (decodedImage != null) {
            if (imageParams.isImageMask()) {
                decodedImage = ImageUtility.applyExplicitMask(decodedImage, graphicsState.getFillColor());
            }
//            ImageUtility.displayImage(decodedImage, pObjectReference.toString() + decodedImage.getWidth() +
//                    " " + "x" + decodedImage.getHeight());
            // apply common mask and sMask processing
            ImageDecoder smaskDecoder = imageParams.getSMask(graphicsState);
            if (smaskDecoder != null) {
                BufferedImage smaskImage = smaskDecoder.decode();
//                ImageUtility.displayImage(smaskImage, "SMask " + entries.get(SMASK_KEY).toString());
                decodedImage = ImageUtility.applyExplicitSMask(decodedImage, smaskImage);
            }
            ImageDecoder maskDecoder = imageParams.getMask(graphicsState);
            if (maskDecoder != null) {
                BufferedImage maskImage = maskDecoder.decode();
//                ImageUtility.displayImage(maskImage, "Mask " + entries.get(MASK_KEY).toString());
                decodedImage = ImageUtility.applyExplicitMask(decodedImage, maskImage);
            }
//            if (maskDecoder != null || smaskDecoder != null)
//                ImageUtility.displayImage(decodedImage, "Final " + pObjectReference.toString());
        }
        // associate any preserved CMYK samples with this stable stream key so the
        // draw-time ink capture finds them regardless of downstream mask/scale.
        ImageUtility.associateCmykStream(this, rawDecoded);
        // Trim the resident footprint: an opaque, effectively-grayscale result
        // (common for DeviceN / Separation / DeviceGray scans that decode through
        // the sRGB ARGB path) is repacked to 8-bit, 1/4 the memory, no visual
        // change.  No-op for coloured/translucent/CMYK-preserving images.
        decodedImage = ImageUtility.compactImage(decodedImage);
        return decodedImage;
    }

    public void setDecodedImage(BufferedImage decodedImage) {
        this.decodedImage = decodedImage;
    }

    public BufferedImage getDecodedImage() {
        return decodedImage;
    }

    /**
     * @param af CTM in force at the {@code Do} being parsed
     * @deprecated an image XObject is shared by every placement of it in the document, so a
     * transform kept here only ever describes the last one parsed. Where a placement is drawn now
     * lives on {@link org.icepdf.core.pobjects.graphics.images.references.ImageReference}, which is
     * created per {@code Do}. Retained because the parser still records it for painting.
     */
    @Deprecated
    public void setGraphicsTransformMatrix(AffineTransform af) {
        graphicsTransformMatrix = af;
    }

    public int getWidth() {
        return imageParams.getWidth();
    }

    public int getHeight() {
        return imageParams.getHeight();
    }

    public PColorSpace getColourSpace() {
        return imageParams.getColourSpace();
    }

    /**
     * Return a string description of the object.  Primarily used for debugging.
     */
    public String toString() {
        StringBuilder sb = new StringBuilder(64);
        sb.append("Image stream= ");
        sb.append(entries);
        if (getPObjectReference() != null) {
            sb.append("  ");
            sb.append(getPObjectReference());
        }
        return sb.toString();
    }


}
