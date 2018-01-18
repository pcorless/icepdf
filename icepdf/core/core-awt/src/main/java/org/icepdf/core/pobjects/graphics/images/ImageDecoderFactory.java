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

import org.icepdf.core.pobjects.graphics.GraphicsState;
import org.icepdf.core.util.Defs;

import javax.imageio.ImageIO;
import java.util.List;

public class ImageDecoderFactory {

    // filter names
    private static final String[] CCITTFAX_DECODE_FILTERS = new String[]{"CCITTFaxDecode", "/CCF", "CCF"};
    private static final String[] DCT_DECODE_FILTERS = new String[]{"DCTDecode", "/DCT", "DCT"};
    private static final String[] JBIG2_DECODE_FILTERS = new String[]{"JBIG2Decode"};
    private static final String[] JPX_DECODE_FILTERS = new String[]{"JPXDecode"};

    // setup no caching for imageio.
    static {
        ImageIO.setUseCache(Defs.sysPropertyBoolean(
                "org.icepdf.core.imagedecoder.imageio.caching.enabled", false));
    }

    // singleton
    private static ImageDecoderFactory imageDecoderFactory;

    private ImageDecoderFactory() {

    }

    public static ImageDecoderFactory getInstance() {
        if (imageDecoderFactory == null) {
            imageDecoderFactory = new ImageDecoderFactory();
        }
        return imageDecoderFactory;
    }

    public static ImageDecoder createDecoder(ImageStream imageStream, GraphicsState graphicsState) {
        if (containsFilter(imageStream, CCITTFAX_DECODE_FILTERS)) {
            return new FaxDecoder(imageStream, graphicsState);
        } else if (containsFilter(imageStream, DCT_DECODE_FILTERS)) {
            return new DctDecoder(imageStream, graphicsState);
        } else if (containsFilter(imageStream, JBIG2_DECODE_FILTERS)) {
            return new JBig2Decoder(imageStream, graphicsState);
        } else if (containsFilter(imageStream, JPX_DECODE_FILTERS)) {
            return new JpxDecoder(imageStream, graphicsState);
        } else {
            // raw image type
            return new RasterDecoder(imageStream, graphicsState);
        }
    }

    private static boolean containsFilter(ImageStream imageStream, String[] searchFilterNames) {
        List filterNames = imageStream.getFilterNames();
        if (filterNames == null)
            return false;
        for (Object filterName1 : filterNames) {
            String filterName = filterName1.toString();
            for (String search : searchFilterNames) {
                if (search.equals(filterName)) {
                    return true;
                }
            }
        }
        return false;
    }
}
