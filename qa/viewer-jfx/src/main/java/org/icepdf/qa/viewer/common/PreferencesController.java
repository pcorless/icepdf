package org.icepdf.qa.viewer.common;

import org.icepdf.qa.viewer.comparitors.ImageComparePane;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.prefs.Preferences;

/**
 * Stores application specific data such as window position and relative project paths.
 */
public class PreferencesController {

    public static final String WINDOW_LOCATION_X_KEY = "windowLocationX";
    public static final String WINDOW_LOCATION_Y_KEY = "windowLocationY";

    public static final String WINDOW_LOCATION_WIDTH_KEY = "windowLocationWidth";
    public static final String WINDOW_LOCATION_HEIGHT_KEY = "windowLocationHeight";

    public static final String VIEW_DIVIDER_LOCATION_KEY = "dividerLocation";

    public static final String PROJECT_BASE_PATH_KEY = "projectBasePath";
    public static final String CAPTURE_RESULTS_BASE_PATH_KEY = "captureResultBasePath";
    public static final String CONTENT_SET_BASE_PATH_KEY = "contentSetBasePath";
    public static final String IMAGE_COMPARE_DEFAULT_VIEW_KEY = "imageCompareDefaultView";
    public static final String IMAGE_COMPARE_DEFAULT_VIEW_VALUE = ImageComparePane.SINGLE_COMPARE_VIEW;
    public static final String IMAGE_COMPARE_BLENDING_MODE_KEY = "imageCompareBlendingMode";
    public static final String IMAGE_COMPARE_BLENDING_MODE_VALUE = ImageComparePane.DIFFERENCE_BLENDING_MODE;
    public static final String IMAGE_COMPARE_DIFF_ENABLED_KEY = "imageCompareDiffEnabled";
    public static final boolean IMAGE_COMPARE_DIFF_ENABLED_VALUE = true;
    public static final String LAST_CONTENT_SET_KEY = "contentSetBasePath";
    public static final String APPLICATION_HOME_PATH_KEY = "applicationHomePath";

    public static final String LAST_PROJECT_KEY = "lastProjectName";

    private static final String HOME = System.getProperties().getProperty("user.home");
    private static String defaultApplicationHome = HOME + "/.icepdf/icepdf-qa/";
    private static String captureSetBasePath = defaultApplicationHome + "captures/";
    private static String contentSetBasePath = defaultApplicationHome + "contentSets/";
    private static String projectBasePath = defaultApplicationHome + "projects/";
    private static String lastContentSetFilesDirectory = HOME + "/dev/pdf-qa/metrics/";
    private static String productClassPathDirectory = HOME + "/dev/products/";
    // folder for each content set file name.
    private static String resultsPathDirectory = HOME + "/dev/pdf-qa/results/";

    private static Preferences prefs = Preferences.userNodeForPackage(PreferencesController.class);

    private static PreferencesController preferencesController;

    static {
        // setup default directory's for storing project data.
        defaultApplicationHome = prefs.get(APPLICATION_HOME_PATH_KEY, defaultApplicationHome);
    }

    public static Path getLastUsedProjectPath() {
        String lastProjectName = prefs.get(LAST_PROJECT_KEY, null);
        if (lastProjectName != null) {
            Path projectPath = Paths.get(projectBasePath, lastProjectName);
            if (Files.exists(projectPath)) {
                return projectPath;
            }
        }
        return null;
    }

    public static void saveLastUedProject(Path filePath) {
        prefs.put(LAST_PROJECT_KEY, filePath.getFileName().toString());
    }

    public static Path getLastUsedContentSetPath() {
        String lastContentSetPath = prefs.get(LAST_CONTENT_SET_KEY, lastContentSetFilesDirectory);
        if (lastContentSetPath != null) {
            Path projectPath = Paths.get(lastContentSetPath);
            if (Files.exists(projectPath)) {
                return projectPath;
            }
        }
        return null;
    }

    public static String getLastUsedImageCompareView() {
        return prefs.get(IMAGE_COMPARE_DEFAULT_VIEW_KEY, IMAGE_COMPARE_DEFAULT_VIEW_VALUE);
    }

    public static void saveLastUsedImageCompareView(String lastImageCompareName) {
        prefs.put(IMAGE_COMPARE_DEFAULT_VIEW_KEY, lastImageCompareName);
    }

    public static String getLastUsedImageCompareBlendingMode() {
        return prefs.get(IMAGE_COMPARE_BLENDING_MODE_KEY, IMAGE_COMPARE_BLENDING_MODE_VALUE);
    }

    public static void saveLastUsedImageCompareBlendingMode(String lastImageCompareName) {
        prefs.put(IMAGE_COMPARE_BLENDING_MODE_KEY, lastImageCompareName);
    }

    public static boolean getLastUsedImageCompareDiffEnabled() {
        return prefs.getBoolean(IMAGE_COMPARE_DIFF_ENABLED_KEY, IMAGE_COMPARE_DIFF_ENABLED_VALUE);
    }

    public static void saveLastUsedImageCompareDiffEnabled(boolean lastImageCompareName) {
        prefs.putBoolean(IMAGE_COMPARE_DIFF_ENABLED_KEY, lastImageCompareName);
    }

    public static void saveLastUsedContentSetPath(Path directoryPath) {
        prefs.put(LAST_CONTENT_SET_KEY, directoryPath.getFileName().toString());
    }

    /**
     * Path locations
     */
    public static String getProjectDirectory() {
        return projectBasePath;
    }

    public static String getCaptureSetDirectory() {
        return captureSetBasePath;
    }

    public static String getContentSetDirectory() {
        return contentSetBasePath;
    }

    public static String getContentSetFilesDiretory() {
        return lastContentSetFilesDirectory;
    }

    public static String getProductClassPathDirectory() {
        return productClassPathDirectory;
    }

    public static String getResultsPathDirectory() {
        return resultsPathDirectory;
    }

    /**
     * Viewer preferences.
     */

    public void setWindowLocation(int x, int y) {

    }

    public void setWindowSize(int width, int height) {

    }

    public void getWindowLocation() {

    }

    public void getWindowDimension() {

    }
}
