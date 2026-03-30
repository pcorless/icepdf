package org.icepdf.fx.ri.ui.icons;

import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Manages loading and caching of icons for the application.
 * Supports multiple icon sizes and provides fallback for missing icons.
 */
public class IconManager {

    private static final Logger logger = Logger.getLogger(IconManager.class.getName());

    // Singleton instance
    private static IconManager instance;

    // Icon cache: key = "name_size", value = Image
    private final Map<String, Image> iconCache;

    // Default icon sizes
    public static final int SIZE_16 = 16;
    public static final int SIZE_24 = 24;
    public static final int SIZE_32 = 32;
    public static final int SIZE_48 = 48;

    // Icon base path in resources
    private static final String ICON_BASE_PATH = "/org/icepdf/fx/ri/images/";

    private IconManager() {
        this.iconCache = new HashMap<>();
    }

    /**
     * Gets the singleton instance of IconManager.
     */
    public static synchronized IconManager getInstance() {
        if (instance == null) {
            instance = new IconManager();
        }
        return instance;
    }

    /**
     * Loads an icon with the specified name and size.
     *
     * @param iconName The name of the icon (without extension)
     * @param size     The desired size (16, 24, 32, 48)
     * @return ImageView with the loaded icon, or a default icon if not found
     */
    public ImageView getIcon(String iconName, int size) {
        Image image = loadIcon(iconName, size);
        if (image != null) {
            ImageView imageView = new ImageView(image);
            imageView.setFitWidth(size);
            imageView.setFitHeight(size);
            imageView.setPreserveRatio(true);
            return imageView;
        }
        return createDefaultIcon(size);
    }

    /**
     * Loads an icon image with caching.
     */
    private Image loadIcon(String iconName, int size) {
        String cacheKey = iconName + "_" + size;

        // Check cache first
        if (iconCache.containsKey(cacheKey)) {
            return iconCache.get(cacheKey);
        }

        // Try to load icon
        Image image = tryLoadIcon(iconName, size);

        if (image != null) {
            iconCache.put(cacheKey, image);
            return image;
        }

        // Try default size if specific size not found
        if (size != SIZE_24) {
            image = tryLoadIcon(iconName, SIZE_24);
            if (image != null) {
                iconCache.put(cacheKey, image);
                return image;
            }
        }

        logger.log(Level.WARNING, "Icon not found: {0} (size: {1})", new Object[]{iconName, size});
        return null;
    }

    /**
     * Attempts to load an icon from various possible locations.
     */
    private Image tryLoadIcon(String iconName, int size) {
        // Try different file patterns
        String[] patterns = {
                ICON_BASE_PATH + iconName + "_" + size + ".png",
                ICON_BASE_PATH + iconName + ".png",
                ICON_BASE_PATH + size + "/" + iconName + ".png",
                ICON_BASE_PATH + "icons/" + iconName + "_" + size + ".png",
                ICON_BASE_PATH + "icons/" + iconName + ".png"
        };

        for (String path : patterns) {
            try {
                InputStream is = getClass().getResourceAsStream(path);
                if (is != null) {
                    Image image = new Image(is, size, size, true, true);
                    is.close();
                    logger.log(Level.FINE, "Loaded icon: {0} from {1}", new Object[]{iconName, path});
                    return image;
                }
            } catch (Exception e) {
                // Try next pattern
            }
        }

        return null;
    }

    /**
     * Creates a default placeholder icon.
     */
    private ImageView createDefaultIcon(int size) {
        // Create a simple colored rectangle as placeholder
        javafx.scene.canvas.Canvas canvas = new javafx.scene.canvas.Canvas(size, size);
        javafx.scene.canvas.GraphicsContext gc = canvas.getGraphicsContext2D();

        gc.setFill(javafx.scene.paint.Color.LIGHTGRAY);
        gc.fillRect(0, 0, size, size);

        gc.setStroke(javafx.scene.paint.Color.DARKGRAY);
        gc.strokeRect(0, 0, size, size);

        javafx.scene.image.WritableImage wi = new javafx.scene.image.WritableImage(size, size);
        canvas.snapshot(null, wi);

        ImageView imageView = new ImageView(wi);
        imageView.setFitWidth(size);
        imageView.setFitHeight(size);
        return imageView;
    }

    /**
     * Pre-loads commonly used icons.
     */
    public void preloadCommonIcons() {
        String[] commonIcons = {
                "open", "save", "print", "close",
                "zoom-in", "zoom-out", "zoom-fit",
                "first-page", "previous-page", "next-page", "last-page",
                "rotate-left", "rotate-right",
                "search", "properties", "settings"
        };

        for (String iconName : commonIcons) {
            loadIcon(iconName, SIZE_24);
        }

        logger.info("Pre-loaded common icons");
    }

    /**
     * Clears the icon cache.
     */
    public void clearCache() {
        iconCache.clear();
        logger.info("Icon cache cleared");
    }

    /**
     * Gets cache statistics.
     */
    public String getCacheStats() {
        return String.format("Icon cache: %d icons loaded", iconCache.size());
    }

    // Convenience methods for common icons

    public ImageView getOpenIcon() {
        return getIcon("open", SIZE_24);
    }

    public ImageView getSaveIcon() {
        return getIcon("save", SIZE_24);
    }

    public ImageView getPrintIcon() {
        return getIcon("print", SIZE_24);
    }

    public ImageView getSearchIcon() {
        return getIcon("search", SIZE_24);
    }

    public ImageView getZoomInIcon() {
        return getIcon("zoom-in", SIZE_24);
    }

    public ImageView getZoomOutIcon() {
        return getIcon("zoom-out", SIZE_24);
    }

    public ImageView getFirstPageIcon() {
        return getIcon("first-page", SIZE_24);
    }

    public ImageView getPreviousPageIcon() {
        return getIcon("previous-page", SIZE_24);
    }

    public ImageView getNextPageIcon() {
        return getIcon("next-page", SIZE_24);
    }

    public ImageView getLastPageIcon() {
        return getIcon("last-page", SIZE_24);
    }

    public ImageView getRotateLeftIcon() {
        return getIcon("rotate-left", SIZE_24);
    }

    public ImageView getRotateRightIcon() {
        return getIcon("rotate-right", SIZE_24);
    }

    public ImageView getPropertiesIcon() {
        return getIcon("properties", SIZE_24);
    }

    public ImageView getSettingsIcon() {
        return getIcon("settings", SIZE_24);
    }
}

