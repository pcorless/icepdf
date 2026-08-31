/*
 * Copyright 2026 Patrick Corless
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.icepdf.core.util.redaction;

import org.icepdf.core.pobjects.PObject;
import org.icepdf.core.pobjects.annotations.Annotation;
import org.icepdf.core.pobjects.annotations.RedactionAnnotation;
import org.icepdf.core.pobjects.graphics.CalGray;
import org.icepdf.core.pobjects.graphics.DeviceGray;
import org.icepdf.core.pobjects.graphics.PColorSpace;
import org.icepdf.core.pobjects.graphics.images.ImageDecoder;
import org.icepdf.core.pobjects.graphics.images.ImageParams;
import org.icepdf.core.pobjects.graphics.images.ImageStream;
import org.icepdf.core.pobjects.graphics.images.ImageUtility;
import org.icepdf.core.pobjects.graphics.images.references.ImageReference;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.IndexColorModel;
import java.awt.image.WritableRaster;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

/**
 * Burn the redactionPath into the given image stream.  The image stream holds the fully decode image with masking
 * data as well a converted colour space.
 *
 * @since 7.2.0
 */
public class ImageBurner {

    private static final Logger logger = Logger.getLogger(ImageBurner.class.getName());

    /** A fully opaque eight-bit sample - white in a soft mask, alpha in a colour raster. */
    private static final int OPAQUE = 255;

    public static ImageStream burn(ImageReference imageReference, RedactionAnnotation annotation,
                                   Color redactionColor) throws InterruptedException {
        return burn(imageReference, Collections.singletonList(annotation), redactionColor);
    }

    /**
     * Burns every area covering this image, in one pass.
     * <p>
     * A scanned page is one large image with as many redactions over it as the operator drew, so
     * taking the areas together rather than one at a time is the normal case. Doing them one at a
     * time meant decoding, converting and re-publishing the whole image once per annotation - on a
     * full-page scan, a full-size allocation and copy each time - and re-decoding its masks from the
     * stream on every pass, which threw away what the previous one had burned into them.
     *
     * @param imageReference image and the placement being redacted
     * @param annotations    redactions covering it, each carrying its own area and colour
     * @param redactionColor colour to leave behind for a redaction that does not name one
     */
    public static ImageStream burn(ImageReference imageReference,
                                   List<RedactionAnnotation> annotations,
                                   Color redactionColor) throws InterruptedException {
        ImageStream imageStream = imageReference.getImageStream();
        BufferedImage image = imageStream.getDecodedImage();
        if (image == null) {
            image = imageReference.getBaseImage();
        }
        List<RedactionAnnotation> areas = new ArrayList<>(annotations.size());
        for (RedactionAnnotation annotation : annotations) {
            if (annotation != null && annotation.getMarkupPath() != null) {
                areas.add(annotation);
            }
        }
        if (areas.isEmpty()) {
            return imageStream;
        }
        // Where this drawing of the image sits.  Taken from the reference, which belongs to one Do,
        // rather than from the image stream, which is shared by every placement of the image.
        AffineTransform placement = imageReference.getPlacement();
        // An image can be drawn through as many as three surfaces, and a redaction has to reach all
        // of them: the samples themselves, the stencil deciding which of them are painted, and the
        // soft mask deciding how opaque they are.  Leaving any one alone leaves either content or the
        // shape of it behind.
        burnMaskStream(imageStream, placement, areas);
        burnSoftMask(imageStream, placement, areas);
        return burnBaseImage(imageStream, placement, image, areas, redactionColor);
    }

    /**
     * Burns the stencil an image is masked by, when {@code /Mask} is a stream.
     * <p>
     * The mask says which of the image's pixels are painted at all, so the covered area is set to
     * paint: the block burned into the image below is then visible rather than masked away. Scanned
     * documents in particular carry content in the mask rather than the image.
     */
    private static void burnMaskStream(ImageStream imageStream, AffineTransform placement,
                                       List<RedactionAnnotation> areas) {
        ImageStream maskImageStream = imageStream.getImageParams().getMaskImageStream();
        ImageDecoder maskDecoder = imageStream.getImageParams().getMask(null);
        if (maskImageStream == null || maskDecoder == null) {
            return;
        }
        // Whatever a previous burn on this image left, if there was one. Decoding afresh would
        // start from the original and discard it, leaving only the last of several redactions.
        BufferedImage mask = maskImageStream.getDecodedImage();
        if (mask == null) {
            mask = maskDecoder.decode();
        }
        if (mask == null) {
            return;
        }
        burnAreas(mask, placement, areas, sample(paintSample(mask)));
        publish(maskImageStream, mask);
    }

    /**
     * Makes the redacted area of a soft-masked image opaque.
     * <p>
     * An {@code /SMask} is a greyscale image whose samples are the base image's alpha - white is
     * opaque, black is transparent (PDF 32000-1 §11.6.5.3). Two things follow for a redaction.
     * <p>
     * The block burned into the base image is only visible where the soft mask lets it show, so a
     * redaction over a transparent part of the image paints a block nobody can see and the page
     * underneath shows through instead. The data is gone either way, but a redaction that appears to
     * have done nothing is not one anybody should ship.
     * <p>
     * The mask also outlines what was there. Even with the pixels replaced, the alpha channel still
     * carries the shape of the removed content - a cut-out signature or logo is recognisable from
     * its silhouette alone.
     * <p>
     * Both are answered by setting the covered samples to fully opaque. Note this is the opposite of
     * what filling the redaction colour would do: black in a soft mask means <em>transparent</em>, so
     * treating it like any other image would erase the redaction rather than apply it.
     */
    private static void burnSoftMask(ImageStream imageStream, AffineTransform placement,
                                     List<RedactionAnnotation> areas) {
        ImageStream softMask = imageStream.getImageParams().getSMaskImageStream();
        ImageDecoder softMaskDecoder = imageStream.getImageParams().getSMask(null);
        if (softMask == null || softMaskDecoder == null) {
            return;
        }
        // As above: pick up where the last burn left off rather than decoding the original again.
        BufferedImage decoded = softMask.getDecodedImage();
        if (decoded == null) {
            decoded = softMaskDecoder.decode();
        }
        if (decoded == null) {
            return;
        }
        // A soft mask is greyscale by definition, and has to be written back that way.  Handed the
        // decoder's colour image, the raster encoder writes DeviceRGB - three bytes a pixel for a
        // one-channel image, and an /SMask that no longer says what §11.6.5.3 requires it to.
        BufferedImage mask = toGrayscale(decoded);
        burnAreas(mask, placement, areas, sample(OPAQUE));
        publish(softMask, mask);
    }

    /**
     * Burns the image's own samples.
     * <p>
     * What gets written depends on what the samples mean, which is the one thing that varies between
     * image kinds: a stencil's samples say paint or do not paint, a greyscale image's are one
     * channel, everything else is colour. The area covered is worked out once, the same way, for all
     * of them.
     */
    private static ImageStream burnBaseImage(ImageStream imageStream, AffineTransform placement,
                                             BufferedImage image, List<RedactionAnnotation> areas,
                                             Color redactionColor) {
        ImageParams imageParams = imageStream.getImageParams();
        BufferedImage target;
        boolean stencil = imageParams.isImageMask();
        boolean grayscale = !stencil && isGrayscale(imageParams);
        boolean colourKey = !stencil && !grayscale && imageParams.isColorKeyMask();
        if (stencil) {
            // A stencil says paint or do not paint, one bit per pixel, and takes its colour from
            // whatever fill colour is in force - so there is nothing here to fill with the redaction
            // colour.  Converting it to RGB like any other image threw away the one bit that
            // mattered and produced an image still declared /ImageMask true but carrying eight-bit
            // RGB samples, which no reader can make sense of.  Setting its samples to paint leaves
            // the region solid instead of showing what was drawn.
            target = image;
        } else if (grayscale) {
            // A greyscale image stays greyscale.  Converting it to RGB the way everything else is
            // converted triples its data for no visible difference, which on a scanned page - the
            // common thing to redact - is most of the file.
            target = toGrayscale(image);
        } else if (colourKey) {
            // Needs the alpha channel: encodeColorKeyMask reads it back to work out which pixels
            // were masked out, so the burned pixels have to be explicitly opaque.
            target = asType(image, BufferedImage.TYPE_INT_ARGB);
        } else {
            // A new image to get around the indexed colour space issue.
            target = asType(image, BufferedImage.TYPE_INT_RGB);
        }
        // Each redaction is burned in its own colour.  A stencil has none to burn - its colour comes
        // from the content stream - so those are the one kind where the annotations cannot disagree.
        for (RedactionAnnotation annotation : areas) {
            PixelWriter writer;
            if (stencil) {
                writer = sample(paintSample(target));
            } else {
                Color colour = colourOf(annotation, redactionColor);
                writer = grayscale ? sample(luminance(colour)) : colour(colour, colourKey);
            }
            burnArea(target, placement, annotation.getMarkupPath(), writer);
        }
        publish(imageStream, target);
        if (imageParams.isColorKeyMask()) {
            ImageUtility.encodeColorKeyMask(imageStream);
        }
        return imageStream;
    }

    /**
     * The image in the type wanted, converting only when it is not already that type.
     * <p>
     * {@code ImageUtility.createBufferedImage} allocates and redraws unconditionally, which on a
     * full-page scan is a copy of the whole page for no change at all - and once per redaction, since
     * a burn hands its result back for the next one to build on.
     */
    private static BufferedImage asType(BufferedImage image, int type) {
        return image.getType() == type ? image : ImageUtility.createBufferedImage(image, type);
    }

    /**
     * What a redaction writes into one pixel of one surface.
     * <p>
     * The only thing that differs between an image, its stencil and its soft mask - everything about
     * <em>which</em> pixels is common, and lives in {@link #burnArea}.
     */
    @FunctionalInterface
    private interface PixelWriter {
        void write(WritableRaster raster, int x, int y);
    }

    /** Writes one sample into band zero: a stencil's paint value, or a single greyscale channel. */
    private static PixelWriter sample(int value) {
        return (raster, x, y) -> raster.setSample(x, y, 0, value);
    }

    /**
     * Writes a colour across the raster's bands.
     * <p>
     * Written band by band rather than painted, because painting runs a colour-space conversion and
     * the sample that lands is not the one asked for - the trap that had a soft mask's 160 written
     * back as 208.
     */
    private static PixelWriter colour(Color redactionColor, boolean opaque) {
        Color safe = redactionColor != null ? redactionColor : Color.BLACK;
        return (raster, x, y) -> {
            raster.setSample(x, y, 0, safe.getRed());
            raster.setSample(x, y, 1, safe.getGreen());
            raster.setSample(x, y, 2, safe.getBlue());
            if (opaque && raster.getNumBands() > 3) {
                raster.setSample(x, y, 3, OPAQUE);
            }
        };
    }

    /**
     * Every pixel a redaction covers, once.
     * <p>
     * Pixel centres decide coverage rather than an antialiased fill: a partly-covered edge pixel
     * blended with what was underneath keeps a fraction of the very thing being removed.
     */
    private static void burnArea(BufferedImage image, AffineTransform placement,
                                 GeneralPath redactionPath, PixelWriter writer) {
        Shape area = userSpaceToImageSpace(placement, image).createTransformedShape(redactionPath);
        Rectangle bounds = area.getBounds().intersection(
                new Rectangle(0, 0, image.getWidth(), image.getHeight()));
        WritableRaster samples = image.getRaster();
        for (int y = bounds.y; y < bounds.y + bounds.height; y++) {
            for (int x = bounds.x; x < bounds.x + bounds.width; x++) {
                if (area.contains(x + 0.5, y + 0.5)) {
                    writer.write(samples, x, y);
                }
            }
        }
    }

    /**
     * The same, for the surfaces where every redaction writes the same thing - a stencil's paint
     * value, a soft mask's opacity - so the colour a redaction was drawn in does not come into it.
     */
    private static void burnAreas(BufferedImage image, AffineTransform placement,
                                  List<RedactionAnnotation> areas, PixelWriter writer) {
        for (RedactionAnnotation annotation : areas) {
            burnArea(image, placement, annotation.getMarkupPath(), writer);
        }
    }

    /**
     * The colour this redaction is to be burned in.
     * <p>
     * The annotation's own colour wins. It is what the redaction is drawn in on screen, so burning
     * something else would leave the marker and the pixels underneath it disagreeing - visible on a
     * scanned page, where the burn <em>is</em> the result. {@link RedactionOptions#getRedactionColor()}
     * covers a redaction that names no colour, which is what a headless caller building annotations
     * from search hits produces.
     * <p>
     * Read from the dictionary rather than through {@code getColor()}, which is the resolved colour:
     * {@code RedactionAnnotation.init} substitutes a default of its own for an annotation with no
     * {@code /C}, so asking it can never report "no colour" and the option would never be reached -
     * a setting that exists and does nothing, which is worse than not offering it.
     */
    private static Color colourOf(RedactionAnnotation annotation, Color fallback) {
        boolean saysSo = annotation.getEntries().get(Annotation.COLOR_KEY) != null;
        Color annotationColour = saysSo ? annotation.getColor() : null;
        if (annotationColour != null) {
            return annotationColour;
        }
        return fallback != null ? fallback : Color.BLACK;
    }

    /**
     * Hands the burned raster back to the stream and tells the writer the object changed.
     * <p>
     * The decoded image is kept rather than discarded because a page can hold more than one
     * redaction over the same image, and the second has to burn what the first left.
     */
    private static void publish(ImageStream imageStream, BufferedImage image) {
        imageStream.setDecodedImage(image);
        if (imageStream.getPObjectReference() != null) {
            imageStream.getLibrary().getStateManager().addChange(new PObject(imageStream,
                    imageStream.getPObjectReference()));
        }
    }

    /**
     * Whether this image is a single greyscale channel that can be written back as one.
     * <p>
     * A colour-key mask is excluded: it needs the alpha channel of an RGB raster to carry which
     * pixels are masked out, which a single-channel image cannot hold.
     */
    private static boolean isGrayscale(ImageParams imageParams) {
        if (imageParams.isColorKeyMask() || imageParams.isImageMask()) {
            return false;
        }
        PColorSpace colourSpace = imageParams.getColourSpace();
        return (colourSpace instanceof DeviceGray || colourSpace instanceof CalGray)
                && imageParams.getBitsPerComponent() <= 8;
    }

    /**
     * The redaction colour as a single greyscale sample. Black stays 0, which is what a redaction
     * uses by default; a colour is taken at its perceived brightness so the block reads the way the
     * caller meant it to.
     */
    private static int luminance(Color colour) {
        if (colour == null) {
            return 0;
        }
        return Math.min(255, Math.round(0.299f * colour.getRed()
                + 0.587f * colour.getGreen() + 0.114f * colour.getBlue()));
    }

    /**
     * Copies a decoded soft mask into a single-channel greyscale raster.
     * <p>
     * Sample by sample rather than by drawing it: painting an sRGB image into a greyscale one runs a
     * colour-space conversion, and a soft mask's samples are alpha values, not colours - 80 has to
     * come out as 80. The channels of a decoded mask are equal, so any of them is the sample.
     */
    private static BufferedImage toGrayscale(BufferedImage image) {
        if (image.getType() == BufferedImage.TYPE_BYTE_GRAY) {
            return image;
        }
        BufferedImage gray = new BufferedImage(image.getWidth(), image.getHeight(),
                BufferedImage.TYPE_BYTE_GRAY);
        WritableRaster raster = gray.getRaster();
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                raster.setSample(x, y, 0, image.getRGB(x, y) & 0xFF);
            }
        }
        return gray;
    }

    /**
     * Which raster sample means "paint" in this decoded stencil.
     * <p>
     * A decoder resolves a stencil into an image whose painting samples are opaque and whose
     * remaining samples are transparent, so the palette says which is which. The mapping is not
     * fixed: it follows the image's {@code /Decode}, which is exactly the entry a redaction must not
     * assume.
     */
    private static int paintSample(BufferedImage image) {
        ColorModel colorModel = image.getColorModel();
        if (colorModel instanceof IndexColorModel) {
            IndexColorModel indexed = (IndexColorModel) colorModel;
            for (int index = 0, max = indexed.getMapSize(); index < max; index++) {
                if (indexed.getAlpha(index) != 0) {
                    return index;
                }
            }
        }
        // No palette to read it off, so fall back to the default reading, where 0 paints.
        return 0;
    }

    /**
     * Maps user space onto the image's own pixel grid.
     * <p>
     * An image occupies the unit square in user space, placed by the CTM in force at its {@code Do},
     * so going the other way is that CTM inverted followed by the unit square scaled onto the pixel
     * grid - with y flipped, since PDF puts the origin at the bottom left and a raster puts it at the
     * top left.
     * <p>
     * Deriving this from the CTM rather than from the axis-aligned bounding box matters as soon as a
     * placement is anything but upright: a rotated or sheared image has a bounding box larger than
     * itself, and scaling by its width and height puts the redaction in the wrong part of the raster
     * and distorts its shape. A 90 degree rotation also swaps which of the box's dimensions
     * corresponds to the image's width.
     *
     * <p>
     * There is deliberately no approximate fallback. A missing placement matrix means the parser
     * never recorded one, and a matrix that cannot be inverted means the image was drawn collapsed
     * to a line or a point; in either case the area to redact cannot be located within the raster.
     * Burning some other part of the image would leave the caller believing content had been
     * removed when it had not, which is the one failure a redaction must not have. Better to stop
     * and say so.
     * <p>
     * Neither state is reachable through the content parser as it stands - it sets the matrix at
     * every {@code Do}, and an image collapsed to no area intersects no redaction, so the burn is
     * never asked for. These are precondition checks against a future caller, not a case with a
     * test behind it.
     *
     * @param placement CTM in force at this drawing of the image
     * @param image     decoded raster, whose dimensions give the pixel grid
     * @return transform from user space to image space
     * @throws IllegalStateException if the placement cannot be used to locate the redaction
     */
    private static AffineTransform userSpaceToImageSpace(AffineTransform placement, BufferedImage image) {
        if (placement == null) {
            throw new IllegalStateException("Image has no placement matrix, so the area to redact " +
                    "cannot be located within it");
        }
        AffineTransform userToImage = new AffineTransform(
                image.getWidth(), 0, 0, -image.getHeight(), 0, image.getHeight());
        try {
            userToImage.concatenate(placement.createInverse());
        } catch (NoninvertibleTransformException e) {
            throw new IllegalStateException("Image placement matrix cannot be inverted, so the " +
                    "area to redact cannot be located within it: " + placement, e);
        }
        return userToImage;
    }
}
