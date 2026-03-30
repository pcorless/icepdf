package org.icepdf.fx.ri.ui.menus;

import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.stage.Window;
import org.icepdf.fx.ri.viewer.ViewerModel;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;

/**
 * Manages recent files menu and persistence.
 * Stores recently opened files and provides quick access menu.
 */
public class RecentFilesManager {

    private static final int MAX_RECENT_FILES = 10;
    private static final String PREFS_KEY_PREFIX = "recentFile_";
    private static final String PREFS_NODE = "org.icepdf.fx.ri.viewer";

    private final ViewerModel model;
    private final Window window;
    private final Preferences prefs;
    private final List<String> recentFiles;

    public RecentFilesManager(ViewerModel model, Window window) {
        this.model = model;
        this.window = window;
        this.prefs = Preferences.userRoot().node(PREFS_NODE);
        this.recentFiles = new ArrayList<>();

        loadRecentFiles();
    }

    /**
     * Loads recent files from preferences.
     */
    private void loadRecentFiles() {
        recentFiles.clear();
        for (int i = 0; i < MAX_RECENT_FILES; i++) {
            String path = prefs.get(PREFS_KEY_PREFIX + i, null);
            if (path != null && !path.isEmpty()) {
                File file = new File(path);
                if (file.exists()) {
                    recentFiles.add(path);
                }
            }
        }
    }

    /**
     * Saves recent files to preferences.
     */
    private void saveRecentFiles() {
        // Clear old entries
        for (int i = 0; i < MAX_RECENT_FILES; i++) {
            prefs.remove(PREFS_KEY_PREFIX + i);
        }

        // Save current list
        for (int i = 0; i < recentFiles.size(); i++) {
            prefs.put(PREFS_KEY_PREFIX + i, recentFiles.get(i));
        }
    }

    /**
     * Adds a file to the recent files list.
     */
    public void addRecentFile(String filePath) {
        if (filePath == null || filePath.isEmpty()) {
            return;
        }

        // Remove if already in list
        recentFiles.remove(filePath);

        // Add to front
        recentFiles.add(0, filePath);

        // Limit size
        if (recentFiles.size() > MAX_RECENT_FILES) {
            recentFiles.remove(recentFiles.size() - 1);
        }

        saveRecentFiles();
    }

    /**
     * Builds the recent files menu.
     */
    public Menu buildRecentFilesMenu() {
        Menu menu = new Menu("Recent Files");

        if (recentFiles.isEmpty()) {
            MenuItem noFiles = new MenuItem("(No recent files)");
            noFiles.setDisable(true);
            menu.getItems().add(noFiles);
        } else {
            // Add menu items for each recent file
            for (int i = 0; i < recentFiles.size(); i++) {
                String path = recentFiles.get(i);
                File file = new File(path);

                MenuItem item = new MenuItem((i + 1) + ". " + file.getName());
                item.setOnAction(e -> openRecentFile(path));

                // Note: Tooltip not supported on MenuItem in JavaFX
                // Full path: path

                menu.getItems().add(item);
            }

            // Add separator and clear option
            menu.getItems().add(new SeparatorMenuItem());

            MenuItem clearItem = new MenuItem("Clear Recent Files");
            clearItem.setOnAction(e -> clearRecentFiles());
            menu.getItems().add(clearItem);
        }

        return menu;
    }

    /**
     * Opens a recent file.
     */
    private void openRecentFile(String filePath) {
        File file = new File(filePath);
        if (!file.exists()) {
            model.statusMessage.set("File not found: " + file.getName());
            recentFiles.remove(filePath);
            saveRecentFiles();
            return;
        }

        // Use OpenFileCommand to open the file
        // Note: OpenFileCommand currently uses FileChooser, so we'll set the path in model
        model.filePath.set(filePath);
        model.statusMessage.set("Opening " + file.getName() + "...");

        // TODO: Need to enhance OpenFileCommand to accept a file path
        // For now, just set status message
        model.statusMessage.set("Recent file selected: " + file.getName());
    }

    /**
     * Clears all recent files.
     */
    public void clearRecentFiles() {
        recentFiles.clear();
        saveRecentFiles();
        model.statusMessage.set("Recent files cleared");
    }

    /**
     * Gets the list of recent files.
     */
    public List<String> getRecentFiles() {
        return new ArrayList<>(recentFiles);
    }

    /**
     * Gets the maximum number of recent files.
     */
    public int getMaxRecentFiles() {
        return MAX_RECENT_FILES;
    }
}

