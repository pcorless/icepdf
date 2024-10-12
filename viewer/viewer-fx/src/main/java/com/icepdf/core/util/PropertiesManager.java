package com.icepdf.core.util;

import java.util.logging.Logger;

/**
 *
 */
public class PropertiesManager {

    private static final Logger logger = Logger.getLogger(PropertiesManager.class.toString());

    public static final String DEFAULT_MESSAGE_BUNDLE = "com.icepdf.fx.resources.MessageBundle";
    public static final String DEFAULT_SPLASH_CSS = "/com/icepdf/fx/css/splash.css";

    private static PropertiesManager propertiesManager;

    private PropertiesManager() {
    }

    public static PropertiesManager getInstance() {
        if (propertiesManager == null) {
            propertiesManager = new PropertiesManager();
        }
        return propertiesManager;
    }
}
