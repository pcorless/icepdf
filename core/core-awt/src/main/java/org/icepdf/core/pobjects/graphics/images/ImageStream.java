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
import org.icepdf.core.pobjects.graphics.Indexed;
import org.icepdf.core.pobjects.graphics.PColorSpace;
import org.icepdf.core.util.Library;

import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.util.List;
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
     * The soft mask's {@code /Matte} colour as packed RGB, or null when there is none or it cannot
     * be resolved.  A matte says the image's colour samples were pre-blended with that colour by
     * the mask (§11.6.5.3) and must be un-blended when the mask is applied; PowerPoint writes its
     * reflections this way, against black, and drawn as-is they come out as a dark slab.
     * <p>
     * Only a gray or RGB image's matte is resolved: un-blending runs on the converted RGB samples,
     * which is exact only when the conversion is linear, and the colour spaces do not agree on
     * component order for anything wider.
     */
    private int[] getMatteRgb() {
        ImageStream sMask = imageParams.getSMaskImageStream();
        if (sMask == null) {
            return null;
        }
        Object matte = library.getObject(sMask.getEntries(), MATTE_KEY);
        if (!(matte instanceof List)) {
            return null;
        }
        List<?> values = (List<?>) matte;
        PColorSpace colourSpace = imageParams.getColourSpace();
        if (colourSpace == null || colourSpace instanceof Indexed) {
            return null;
        }
        int n = colourSpace.getNumComponents();
        if (values.size() != n || (n != 1 && n != 3)) {
            return null;
        }
        int[] rgb = new int[3];
        for (int i = 0; i < 3; i++) {
            Object v = values.get(n == 1 ? 0 : i);
            if (!(v instanceof Number)) {
                return null;
            }
            float f = Math.max(0f, Math.min(1f, ((Number) v).floatValue()));
            rgb[i] = Math.round(f * 255);
        }
        return rgb;
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
    public BufferedImage getImage(GraphicsState graphicsState, Resources resources) {
        BufferedImage image = decode(graphicsState, resources, false);
        // a paint image left in the pool by a reference: its mask has not been applied yet
        return image instanceof MaskedImage ? ((MaskedImage) image).composite() : image;
    }

    /**
     * The image as the paint path wants it: like {@link #getImage}, except that an explicit stencil
     * {@code /Mask} may be left unapplied, as a {@link MaskedImage} combined only where it is drawn.
     *
     * @param graphicsState graphic state for image or parent form
     * @param resources     resources containing image reference
     * @return new image object, possibly a {@link MaskedImage}
     */
    public BufferedImage getPaintImage(GraphicsState graphicsState, Resources resources) {
        return decode(graphicsState, resources, true);
    }

    private BufferedImage decode(GraphicsState graphicsState, Resources resources, boolean deferMask) {
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
        // a big decode waits for heap budget, so a screenful of scans doesn't decode all at once.
        try (DecodeBudget.Permit ignored = DecodeBudget.acquire(imageParams.getWidth(), imageParams.getHeight())) {
            return decodeSamples(graphicsState, deferMask);
        }
    }

    private BufferedImage decodeSamples(GraphicsState graphicsState, boolean deferMask) {
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
                decodedImage = ImageUtility.applyExplicitSMask(decodedImage, smaskImage, getMatteRgb());
            }
            ImageDecoder maskDecoder = imageParams.getMask(graphicsState);
            if (maskDecoder != null) {
                BufferedImage maskImage = maskDecoder.decode();
//                ImageUtility.displayImage(maskImage, "Mask " + entries.get(MASK_KEY).toString());
                if (deferMask && smaskDecoder == null && !imageParams.isImageMask()
                        && maskImage != null) {
                    // keep the two apart, each at its own resolution, until the paint (see MaskedImage)
                    decodedImage = new MaskedImage(ImageUtility.compactImage(decodedImage),
                            MaskedImage.toStencil(maskImage));
                } else {
                    decodedImage = ImageUtility.applyExplicitMask(decodedImage, maskImage);
                }
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
        if (!(decodedImage instanceof MaskedImage)) {
            // a MaskedImage's base was compacted as it was made
            decodedImage = ImageUtility.compactImage(decodedImage);
        }
        if (decodedImage != null && pObjectReference != null) {
            // the image goes to the pool; the inflated samples behind it are only a cache
            // (re-inflated from the compressed stream if the pool ever lets the image go),
            // and for a large scan they are as big as the image itself.
            disposeDecompressed();
        }
        return decodedImage;
    }

    /**
     * GH-625: the image decoded at a reduced size, for a placement drawn far smaller than the image
     * (see {@link ReducedDecode}).  Not pooled here, and nothing of the full decode is touched: the
     * caller keeps the result apart from full images.
     *
     * @param graphicsState graphic state for image or parent form
     * @param resources     resources containing image reference
     * @param factor        the reduction wanted, a power of two
     * @return the reduced image, or null when this image or factor has no reduced decode; the caller
     * then draws the full image
     */
    public BufferedImage getReducedImage(GraphicsState graphicsState, Resources resources, int factor) {
        ImageParams params = new ImageParams(library, entries, resources);
        AbstractImageDecoder decoder = ReducedDecode.decoderFor(this, params, graphicsState, factor);
        if (decoder == null) {
            return null;
        }
        imageParams = params;
        int reduction = ReducedDecode.decodeFactor(this, factor);
        try (DecodeBudget.Permit ignored = DecodeBudget.acquire(ReducedDecode.reduced(params.getWidth(), reduction),
                ReducedDecode.reduced(params.getHeight(), reduction))) {
            BufferedImage image = decoder.decode();
            return image == null ? null : ImageUtility.compactImage(image);
        }
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
