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

import javax.swing.*;
import java.awt.*;

/**
 * Represents a view builder
 */
public interface ViewBuilder {

    /**
     * @return The built viewer frame
     */
    JFrame buildViewerFrame();

    /**
     * @return The built menu bar
     */
    JMenuBar buildCompleteMenuBar();

    /**
     * Builds and return the complete tool bar
     *
     * @param embeddableComponent Whether the component will be embedded or not
     * @return the tool bar
     */
    JToolBar buildCompleteToolBar(boolean embeddableComponent);

    /**
     * Builds the contents
     *
     * @param cp                  The container in which the contents will be put
     * @param embeddableComponent Whether the component will be embedded or not
     */
    void buildContents(Container cp, boolean embeddableComponent);

}
