package org.icepdf.qa.utilities;

import javafx.util.Duration;

/**
 * Utility for capture work time.
 */

public class TimeTestWatcher {
    Duration start;
    Duration end;
    String testName;

    public void starting(String testName) {
        this.testName = testName;
        start = new Duration(System.currentTimeMillis());
    }

    public double finished() {
        end = new Duration(System.currentTimeMillis());
        double elapsed = end.subtract(start).toMinutes();
        System.out.printf("%nTest %s took %.2f min.%n", testName, elapsed);
        return elapsed;
    }
}