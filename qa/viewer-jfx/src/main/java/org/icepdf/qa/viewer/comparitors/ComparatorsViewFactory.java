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
package org.icepdf.qa.viewer.comparitors;

import org.icepdf.qa.config.CaptureSet;
import org.icepdf.qa.viewer.common.Mediator;

/**
 * Factory for build compare panes based on the CaptureSet type.  It's always assumed that
 * comparisons will be done using two captures sets with the same type.
 */
public class ComparatorsViewFactory {

    private ComparatorsViewFactory() {
    }

    public static ComparatorPane buildComparatorView(CaptureSet.Type type, Mediator mediator) {
        switch (type) {
            case capture:
                return new ImageComparePane(mediator);
            case metric:
                return new MetricComparePane(mediator);
            case textExtraction:
                return new TextComparePane(mediator);
            default:
                return null;
        }
    }
}
