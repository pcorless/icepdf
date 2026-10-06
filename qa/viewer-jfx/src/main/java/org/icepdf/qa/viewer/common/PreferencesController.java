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
    // per-channel colour tolerance (0-255) used by the compare engine and the interactive fuzz slider.
    public static final String IMAGE_COMPARE_FUZZ_KEY = "imageCompareFuzz";
    public static final int IMAGE_COMPARE_FUZZ_VALUE = org.icepdf.qa.utilities.ImageCompare.DEFAULT_FUZZ;
    // ink-weighted similarity (%) at or below which a result is flagged as a regression in the results tab.
    public static final String IMAGE_COMPARE_THRESHOLD_KEY = "imageCompareThreshold";
    public static final double IMAGE_COMPARE_THRESHOLD_VALUE = 99.99d;

    // Capture concurrency (see ImageCompareTask). P: number of documents captured/compared at once.
    // Default is deliberately conservative because peak heap scales with P x versions x page rasters.
    public static final String CAPTURE_CONCURRENT_DOCUMENTS_KEY = "captureConcurrentDocuments";
    public static final int CAPTURE_CONCURRENT_DOCUMENTS_VALUE =
            Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors() / 2));
    // Q: threads that render the pages of a single document. Floored at 2 - rendering pages of the
    // same document concurrently is the stress condition that surfaces ICEpdf sync bugs.
    public static final String CAPTURE_RENDER_THREADS_KEY = "captureRenderThreadsPerDocument";
    public static final int CAPTURE_RENDER_THREADS_VALUE = 4;
    public static final int CAPTURE_RENDER_THREADS_MIN = 2;
    // Passthrough to each capture set's ICEpdf -Dorg.icepdf.core.library.imageThreadPoolSize. Only the
    // image-proxy decode path uses it, and it is read once when that version's Library class loads, so
    // a change takes effect only for a freshly created capture-set class loader.
    public static final String CAPTURE_IMAGE_THREAD_POOL_KEY = "captureImageThreadPoolSize";
    public static final int CAPTURE_IMAGE_THREAD_POOL_VALUE = 2;
    public static final String LAST_CONTENT_SET_KEY = "contentSetBasePath";
    public static final String APPLICATION_HOME_PATH_KEY = "applicationHomePath";

    public static final String LAST_PROJECT_KEY = "lastProjectName";

    private static final String HOME = System.getProperties().getProperty("user.home");
    private static String defaultApplicationHome = HOME + "/.icepdf/icepdf-qa/";
    private static final String captureSetBasePath = defaultApplicationHome + "captures/";
    private static final String contentSetBasePath = defaultApplicationHome + "contentSets/";
    private static final String projectBasePath = defaultApplicationHome + "projects/";
    private static final String lastContentSetFilesDirectory = HOME + "/dev/pdf-qa/metrics/";
    private static final String productClassPathDirectory = HOME + "/dev/products/";
    // folder for each content set file name.
    private static final String resultsPathDirectory = HOME + "/dev/pdf-qa/results/";

    private static final Preferences prefs = Preferences.userNodeForPackage(PreferencesController.class);

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

    public static int getImageCompareFuzz() {
        return prefs.getInt(IMAGE_COMPARE_FUZZ_KEY, IMAGE_COMPARE_FUZZ_VALUE);
    }

    public static void saveImageCompareFuzz(int fuzz) {
        prefs.putInt(IMAGE_COMPARE_FUZZ_KEY, fuzz);
    }

    public static double getImageCompareThreshold() {
        return prefs.getDouble(IMAGE_COMPARE_THRESHOLD_KEY, IMAGE_COMPARE_THRESHOLD_VALUE);
    }

    public static void saveImageCompareThreshold(double threshold) {
        prefs.putDouble(IMAGE_COMPARE_THRESHOLD_KEY, threshold);
    }

    public static int getCaptureConcurrentDocuments() {
        return Math.max(1, prefs.getInt(CAPTURE_CONCURRENT_DOCUMENTS_KEY, CAPTURE_CONCURRENT_DOCUMENTS_VALUE));
    }

    public static void saveCaptureConcurrentDocuments(int concurrentDocuments) {
        prefs.putInt(CAPTURE_CONCURRENT_DOCUMENTS_KEY, Math.max(1, concurrentDocuments));
    }

    public static int getCaptureRenderThreadsPerDocument() {
        return Math.max(CAPTURE_RENDER_THREADS_MIN,
                prefs.getInt(CAPTURE_RENDER_THREADS_KEY, CAPTURE_RENDER_THREADS_VALUE));
    }

    public static void saveCaptureRenderThreadsPerDocument(int renderThreadsPerDocument) {
        prefs.putInt(CAPTURE_RENDER_THREADS_KEY, Math.max(CAPTURE_RENDER_THREADS_MIN, renderThreadsPerDocument));
    }

    public static int getCaptureImageThreadPoolSize() {
        return Math.max(1, prefs.getInt(CAPTURE_IMAGE_THREAD_POOL_KEY, CAPTURE_IMAGE_THREAD_POOL_VALUE));
    }

    public static void saveCaptureImageThreadPoolSize(int imageThreadPoolSize) {
        prefs.putInt(CAPTURE_IMAGE_THREAD_POOL_KEY, Math.max(1, imageThreadPoolSize));
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
