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

    /**
     * Retrieves the list of recently used files from the viewer preferences.
     * <p>
     * The list is stored as a single string of name/path pairs separated by
     * {@link org.icepdf.ri.util.ViewerPropertiesManager#PROPERTY_TOKEN_SEPARATOR}.
     * This method parses that string, unescapes any escaped separator characters,
     * and constructs an array of {@link RecentlyUsedFile} objects for entries
     * whose paths currently exist in the file system.
     * </p>
     * <p>
     * If the stored data is malformed (for example, an odd number of tokens that
     * cannot be grouped into name/path pairs), the underlying preference value
     * for {@link org.icepdf.ri.util.ViewerPropertiesManager#PROPERTY_RECENTLY_OPENED_FILES}
     * is cleared and this method returns an empty array.
     * </p>
     *
     * @return an array of {@link RecentlyUsedFile} representing the valid, existing
     * recently used files; the array may be empty if no valid entries exist
     * or if the stored data was malformed.
     */

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

    /**
     * Adds a new file path to the list of recently used files in the viewer preferences.
     * <p>
     * If the file path already exists in the list, it is moved to the front.
     * The list is capped at a maximum size defined by
     * {@link org.icepdf.ri.util.ViewerPropertiesManager#PROPERTY_RECENT_FILES_SIZE}.
     * If adding the new file exceeds this size, the oldest entries are removed.
     * </p>
     *
     * @param path the file system path to add to the recently used files list
     */
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

    /**
     * Checks whether a file system path exists.
     *
     * @param filePath the string representation of the file system path to check
     * @return {@code true} if a file or directory exists at the given path; {@code false} otherwise
     */
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
