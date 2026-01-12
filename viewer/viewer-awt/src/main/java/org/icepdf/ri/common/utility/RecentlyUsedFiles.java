/*
 * Copyright 20025 Patrick Corless
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an "AS
 * IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */
package org.icepdf.ri.common.utility;

import org.icepdf.ri.util.ViewerPropertiesManager;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;

import static org.icepdf.ri.util.ViewerPropertiesManager.*;

/**
 * Utility class for managing recently used file paths in the viewer application.
 */
public class RecentlyUsedFiles {

    public RecentlyUsedFile[] getRecentlyUsedFilePaths() {
        Preferences preferences = ViewerPropertiesManager.getInstance().getPreferences();
        String recentFilesString = preferences.get(PROPERTY_RECENTLY_OPENED_FILES, "");
        // split on unescaped pipe characters
        String[] tokens = recentFilesString.split("(?<!\\\\)\\|");
        List<RecentlyUsedFile> filePaths = new ArrayList<>();

        if (tokens.length % 2 != 0) {
            preferences.put(PROPERTY_RECENTLY_OPENED_FILES, "");
            return new RecentlyUsedFile[0];
        }

        for (int i = 0; i < tokens.length; i += 2) {
            String fileName = tokens[i].replaceAll("\\\\([|])", "$1");
            String filePath = tokens[i + 1].replaceAll("\\\\([|])", "$1");
            if (doesPathExist(filePath)) {
                filePaths.add(new RecentlyUsedFile(fileName, filePath));
            }
        }
        return filePaths.toArray(new RecentlyUsedFile[0]);
    }

    public void addRecentlyUsedFilePath(Path path) {
        Preferences preferences = ViewerPropertiesManager.getInstance().getPreferences();
        int maxListSize = preferences.getInt(PROPERTY_RECENT_FILES_SIZE, 8);
        String recentFilesString = preferences.get(PROPERTY_RECENTLY_OPENED_FILES, "");
        String[] tokens = recentFilesString.split("(?<!\\\\)\\|");
        List<String> recentPaths = new ArrayList<>();

        for (int i = 0; i < tokens.length - 1; i += 2) {
            recentPaths.add(tokens[i] + PROPERTY_TOKEN_SEPARATOR + tokens[i + 1]);
        }

        String fileName = path.getFileName().toString().replaceAll("\\|", "\\\\|");
        String filePath = path.toString().replaceAll("\\|", "\\\\|");
        String newRecentFile = fileName + PROPERTY_TOKEN_SEPARATOR + filePath;

        recentPaths.remove(newRecentFile);
        recentPaths.add(0, newRecentFile);

        if (recentPaths.size() > maxListSize) {
            recentPaths = recentPaths.subList(0, maxListSize);
        }

        preferences.put(PROPERTY_RECENTLY_OPENED_FILES, String.join(PROPERTY_TOKEN_SEPARATOR, recentPaths));
    }

    public boolean doesPathExist(String filePath) {
        return Files.exists(Path.of(filePath));
    }

    public static class RecentlyUsedFile {
        private final String name;
        private final String path;

        public RecentlyUsedFile(String name, String path) {
            this.name = name;
            this.path = path;
        }

        public String getName() {
            return name;
        }

        public String getPath() {
            return path;
        }
    }
}
