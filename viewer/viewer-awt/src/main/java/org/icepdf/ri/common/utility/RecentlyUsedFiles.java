/**
 *
 */
package org.icepdf.ri.common.utility;

import org.icepdf.ri.util.ViewerPropertiesManager;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
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
        ArrayList<RecentlyUsedFile> filePaths = new ArrayList<>();
        try {
            if (tokens.length % 2 != 0) {
                throw new Exception("Invalid recently used files format.");
            }
            for (int i = 0; i < tokens.length; i += 2) {
                // unescape any escaped pipe characters
                final String fileName = tokens[i].replaceAll("\\\\([|])", "$1");
                final String filePath = tokens[i + 1].replaceAll("\\\\([|])", "$1");
                if (doesPathExist(filePath)) {
                    filePaths.add(new RecentlyUsedFile(fileName, filePath));
                }
            }
        } catch (Exception e) {
            // clear the invalid previous values.
            preferences.put(PROPERTY_RECENTLY_OPENED_FILES, "");
        }
        return filePaths.toArray(RecentlyUsedFile[]::new);
    }

    public void addRecentlyUsedFilePath(Path path) {
        // get reference to the backing store.
        Preferences preferences = ViewerPropertiesManager.getInstance().getPreferences();
        int maxListSize = preferences.getInt(PROPERTY_RECENT_FILES_SIZE, 8);
        String recentFilesString = preferences.get(PROPERTY_RECENTLY_OPENED_FILES, "");
        String[] tokens = recentFilesString.split("(?<!\\\\)\\|");
        ArrayList<String> recentPaths = new ArrayList<>(maxListSize);
        String fileName, filePath;
        for (int i = 0; i < tokens.length - 1; i += 2) {
            // escape any pipe characters in file names and paths.
            fileName = tokens[i];
            filePath = tokens[i + 1];
            recentPaths.add(fileName + PROPERTY_TOKEN_SEPARATOR + Paths.get(filePath));
        }
        // add our new path the start of the list, remove any existing file names.
        fileName = path.getFileName().toString().replaceAll("\\|", "\\\\|");
        filePath = path.toString().replaceAll("\\|", "\\\\|");
        String newRecentFile = fileName + PROPERTY_TOKEN_SEPARATOR + filePath;
        recentPaths.remove(newRecentFile);
        recentPaths.add(0, newRecentFile);
        // trim the list
        if (recentPaths.size() > maxListSize) {
            int size = recentPaths.size();
            recentPaths.subList(maxListSize, size).clear();
        }
        // put the list back in teh properties.
        StringBuilder stringBuilder = new StringBuilder();
        for (String recentPath : recentPaths) {
            stringBuilder.append(recentPath).append(PROPERTY_TOKEN_SEPARATOR);
        }
        preferences.put(PROPERTY_RECENTLY_OPENED_FILES, stringBuilder.toString());

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

    public boolean doesPathExist(String filePath) {
        return java.nio.file.Files.exists(java.nio.file.Paths.get(filePath));
    }
}
