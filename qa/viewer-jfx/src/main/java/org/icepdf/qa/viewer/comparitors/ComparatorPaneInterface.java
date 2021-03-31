package org.icepdf.qa.viewer.comparitors;

import org.icepdf.qa.config.Result;

/**
 * Work in progress but a comparator needs to open the result and show some sort of comparator related
 * data to the end user.
 */
public interface ComparatorPaneInterface {

    void openResult(Result result);

    void toggleDiffFilter();

    void nextDiffFilter();
}
