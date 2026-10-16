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
package org.icepdf.fx.ri.prefs;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The extra font directories the viewer application's user added (preference
 * {@code fonts.directories}, one per line), for {@link org.icepdf.fx.fonts.FontSettings} to scan.
 */
public final class FontDirectories {

    /** Preference: extra font directories, one per line. */
    public static final String DIRECTORIES = "fonts.directories";

    private FontDirectories() {
    }

    public static List<String> of(ViewerPreferences preferences) {
        String value = preferences.get(DIRECTORIES, "");
        if (value.isEmpty()) return Collections.emptyList();
        List<String> directories = new ArrayList<>();
        for (String line : value.split("\n")) {
            if (!line.trim().isEmpty()) directories.add(line.trim());
        }
        return directories;
    }

    public static void set(ViewerPreferences preferences, List<String> directories) {
        preferences.put(DIRECTORIES, directories.isEmpty() ? null : String.join("\n", directories));
    }
}
