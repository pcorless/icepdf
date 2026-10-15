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
package org.icepdf.core.pobjects.graphics.images.references;

import org.icepdf.core.pobjects.Name;
import org.icepdf.core.pobjects.Page;
import org.icepdf.core.pobjects.Resources;
import org.icepdf.core.pobjects.graphics.GraphicsState;
import org.icepdf.core.pobjects.graphics.images.ImageStream;

/**
 * The MipMappedImageReference paints a minified image from a box-filtered mip level of the
 * decoded image, chosen per paint from the device scale, so small zoom levels keep the detail
 * that a point-sampled shrink drops.
 * <p>
 * The levels come from {@link ImageDownsampler}, which every reference type uses when
 * downsampling is enabled (the default).  This type keeps mip mapping on when it has been
 * disabled globally with {@code org.icepdf.core.imageReference.downsample=false}.
 * <p>
 * Before 7.5 each level was a {@link ScaledImageReference} sharing the source's image-pool key,
 * so every level resolved to the full-size image and the mode painted the same as the default.
 *
 * @since 5.0
 */
class MipMappedImageReference extends ImageStreamReference {

    protected MipMappedImageReference(ImageStream imageStream, Name xobjectName, GraphicsState graphicsState,
                                      Resources resources, int imageIndex,
                                      Page page) {
        super(imageStream, xobjectName, graphicsState, resources, imageIndex, page);
    }

    @Override
    protected boolean useDownsampling() {
        return true;
    }
}
