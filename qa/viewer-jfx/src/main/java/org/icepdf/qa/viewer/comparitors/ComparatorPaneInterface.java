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
