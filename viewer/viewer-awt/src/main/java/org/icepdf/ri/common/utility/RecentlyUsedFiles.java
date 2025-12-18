/**
 *
 */
package org.icepdf.ri.common.utility;

import org.icepdf.ri.util.ViewerPropertiesManager;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.StringTokenizer;
import java.util.prefs.Preferences;

import static org.icepdf.ri.util.ViewerPropertiesManager.*;

/**
 * Utility class for managing recently used file paths in the viewer application.
 */
public class RecentlyUsedFiles {

    public static RecentlyUsedFile[] getRecentlyUsedFilePaths() {
        Preferences preferences = ViewerPropertiesManager.getInstance().getPreferences();
        String recentFilesString = preferences.get(PROPERTY_RECENTLY_OPENED_FILES, "");
        StringTokenizer toker = new StringTokenizer(recentFilesString, PROPERTY_TOKEN_SEPARATOR);
        String fileName;
        RecentlyUsedFile[] filePaths = new RecentlyUsedFile[toker.countTokens() / 2];
        int count = 0;
        try {
            while (toker.hasMoreTokens()) {
                fileName = toker.nextToken();
                final String filePath = toker.nextToken();
                filePaths[count] = new RecentlyUsedFile(fileName, filePath);
                count++;
            }
        } catch (Exception e) {
            // clear the invalid previous values.
            preferences.put(PROPERTY_RECENTLY_OPENED_FILES, "");
        }
        return filePaths;
    }

    public static void addRecentlyUsedFilePath(Path path) {
        // get reference to the backing store.
        Preferences preferences = ViewerPropertiesManager.getInstance().getPreferences();
        int maxListSize = preferences.getInt(PROPERTY_RECENT_FILES_SIZE, 8);
        String recentFilesString = preferences.get(PROPERTY_RECENTLY_OPENED_FILES, "");
        StringTokenizer toker = new StringTokenizer(recentFilesString, PROPERTY_TOKEN_SEPARATOR);
        ArrayList<String> recentPaths = new ArrayList<>(maxListSize);
        String fileName, filePath;
        while (toker.hasMoreTokens()) {
            fileName = toker.nextToken().replaceAll("\\|", "\\\\|");
            filePath = toker.nextToken();
            recentPaths.add(fileName + PROPERTY_TOKEN_SEPARATOR + Paths.get(filePath));
        }
        // add our new path the start of the list, remove any existing file names.
        String newRecentFile = path.getFileName() + PROPERTY_TOKEN_SEPARATOR + path;
        if (recentPaths.contains(newRecentFile)) {
            recentPaths.remove(newRecentFile);
        }
        recentPaths.add(0, newRecentFile);
        // trim the list
        if (recentPaths.size() > maxListSize) {
            int size = recentPaths.size();
            if (size > maxListSize) {
                recentPaths.subList(maxListSize, size).clear();
            }
        }
        // put the list back in teh properties.
        StringBuilder stringBuilder = new StringBuilder();
        for (String recentPath : recentPaths) {
            stringBuilder.append(recentPath).append(PROPERTY_TOKEN_SEPARATOR);
        }
        preferences.put(PROPERTY_RECENTLY_OPENED_FILES, stringBuilder.toString());

    }

    public static class RecentlyUsedFile {
        private String name;
        private String path;

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
