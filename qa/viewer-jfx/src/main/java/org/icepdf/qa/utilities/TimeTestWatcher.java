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