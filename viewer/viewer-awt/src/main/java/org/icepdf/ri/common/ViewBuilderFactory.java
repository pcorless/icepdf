/*
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
package org.icepdf.ri.common;

import org.icepdf.ri.common.views.Controller;
import org.icepdf.ri.util.ViewerPropertiesManager;

import java.awt.*;

/**
 * Factory for ViewBuilders
 */
public interface ViewBuilderFactory {

    /**
     * Creates a ViewBuilder for the given controller
     *
     * @param c The controller
     * @return The viewbuilder
     */
    ViewBuilder create(Controller c);

    /**
     * Creates a ViewBuilder for the given controller and properties
     *
     * @param c          The controller
     * @param properties The properties
     * @return The viewbuilder
     */
    ViewBuilder create(Controller c, ViewerPropertiesManager properties);

    /**
     * Creates a ViewBuilder for the given controller and arguments
     *
     * @param c                   The controller
     * @param documentViewType    The initial view type
     * @param documentPageFitMode The initial fit mode
     * @return The viewbuilder
     */
    ViewBuilder create(Controller c, int documentViewType,
                       int documentPageFitMode);

    /**
     * Creates a ViewBuilder for the given controller and arguments
     *
     * @param c                   The controller
     * @param documentViewType    The initial view type
     * @param documentPageFitMode The initial fit mode
     * @param rotation            The initial view rotation
     * @return The viewbuilder
     */
    ViewBuilder create(Controller c, int documentViewType,
                       int documentPageFitMode, float rotation);

    /**
     * Creates a ViewBuilder for the given controller and arguments
     *
     * @param c                   The controller
     * @param documentViewType    The initial view type
     * @param documentPageFitMode The initial fit mode
     * @return The viewbuilder
     */
    ViewBuilder create(Controller c, Font bf, boolean bt, int ts,
                       float[] zl, final int documentViewType,
                       final int documentPageFitMode);

    /**
     * Creates a ViewBuilder for the given controller and arguments
     *
     * @param c                   The controller
     * @param properties          The initial properties
     * @param documentViewType    The initial view type
     * @param documentPageFitMode The initial fit mode
     * @param rotation            The initial document rotation
     * @return The viewbuilder
     */
    ViewBuilder create(Controller c, ViewerPropertiesManager properties,
                       Font bf, boolean bt, int ts,
                       float[] zl, final int documentViewType,
                       final int documentPageFitMode, final float rotation);
}
