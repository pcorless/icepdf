package com.icepdf.fx.util;

import com.icepdf.core.util.FontPropertiesManager;
import com.icepdf.core.util.PropertiesManager;
import javafx.concurrent.Task;

import java.util.ResourceBundle;

/**
 *
 */
public class SettingsLoaderTask extends Task<Object> {

    @Override
    protected Object call() throws Exception {
        ResourceBundle messageBundle = ResourceBundle.getBundle(
                PropertiesManager.DEFAULT_MESSAGE_BUNDLE);
        FontPropertiesManager fontPropertiesManager = FontPropertiesManager.getInstance();

        if (fontPropertiesManager.isPropertiesEmpty()) {
            updateMessage(messageBundle.getString(
                    "icepdf.fx.common.utility.SettingsLoaderTask.loadingFonts.label"));
            updateProgress(-1, 10);
            fontPropertiesManager.readDefaultProperties();
            fontPropertiesManager.updateProperties();
            updateProgress(10, 10);
            updateMessage(messageBundle.getString(
                    "icepdf.fx.common.utility.SettingsLoaderTask.loadingFontsComplete.label"));
        } else {
            updateMessage(messageBundle.getString(
                    "icepdf.fx.common.utility.SettingsLoaderTask.loadingSettings.label"));
            fontPropertiesManager.loadProperties();
            for (int i = 0, max = 10; i < 10; i++) {
                updateProgress(i + 1, max);
                Thread.sleep(10);
            }
//            fontPropertiesManager.clearProperties();
            updateMessage(messageBundle.getString(
                    "icepdf.fx.common.utility.SettingsLoaderTask.loadingSettingsComplete.label"));
        }
        return null;
    }
}
