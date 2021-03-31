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
