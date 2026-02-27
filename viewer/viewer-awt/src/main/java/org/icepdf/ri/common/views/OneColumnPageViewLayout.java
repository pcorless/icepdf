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
package org.icepdf.ri.common.views;

import java.awt.*;
import java.util.Arrays;

public class OneColumnPageViewLayout extends OnePageViewLayout {


    public OneColumnPageViewLayout(DocumentViewModel documentViewModel) {
        super(documentViewModel);
    }

    public void layoutContainer(Container parent) {
        Insets insets = parent.getInsets();
        int maxWidth = parent.getWidth() - (insets.left + insets.right);
        int maxHeight = parent.getHeight() - (insets.top + insets.bottom);
        int xCord = 0, yCord= 0;
        int previousHeight = 0;
        if (sizeUnknown) {
            setSizes(parent);
        }

        PageViewDecorator[] pages = Arrays.stream(parent.getComponents())
                .filter(component -> component instanceof PageViewDecorator && component.isVisible())
                .toArray(PageViewDecorator[]::new);

        int index = 0;
        for (PageViewDecorator pageViewDecorator : pages) {
            Dimension d = pageViewDecorator.getPreferredSize();
            // set starting position for page, everything else flows from there
            if(index == 0){
                // detect if we should be centering
                if (minWidth < maxWidth){
                    xCord = (maxWidth - d.width) / 2;
                }
                if (minHeight < maxHeight){
                    yCord = (maxHeight - minHeight) / 2;
                }
                if (xCord < 0) xCord = 0;
                if (yCord < 0) yCord = 0;

                yCord += insets.top;
            } else {
                xCord = (maxWidth - d.width) / 2;
                yCord += previousHeight + PAGE_SPACING_VERTICAL;
            }
            previousHeight = d.height;
            index++;
            xCord += insets.left;

            pageViewDecorator.setBounds(xCord, yCord, d.width, d.height);
            updatePopupAnnotationComponents(pageViewDecorator);
        }
    }
}
