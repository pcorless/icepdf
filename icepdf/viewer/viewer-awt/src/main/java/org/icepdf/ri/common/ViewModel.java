/*
 * Copyright 2006-2018 ICEsoft Technologies Canada Corp.
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
package org.icepdf.ri.common;

import java.io.File;

/**
 * Data model for the current controller, which maintains state on how a Document is being
 * presented to the user.
 * <br>
 * The default value of isShrinkToPrintableArea is true.
 *
 * @author Mark Collette
 * @since 2.0
 */
public class ViewModel {

    // Store current directory path
    private static File defaultFile = null;

    // Store current URL path
    private static String defaultURL = null;

    // store for shrink to fit setting for Controller prints.
    private boolean isShrinkToPrintableArea = true;

    // number of copies to print
    private int printCopies = 1;

    private PrintHelper printHelper;

    private boolean isWidgetAnnotationHighlight;
    private boolean isAnnotationEditingMode;

    private boolean annotationPrivacyPublic;

    public static File getDefaultFile() {
        return defaultFile;
    }

    public static String getDefaultFilePath() {
        if (defaultFile == null)
            return null;
        return defaultFile.getAbsolutePath();
    }

    public static String getDefaultURL() {
        return defaultURL;
    }

    public static void setDefaultFile(File f) {
        defaultFile = f;
    }

    public static void setDefaultFilePath(String defFilePath) {
        if (defFilePath == null || defFilePath.length() == 0)
            defaultFile = null;
        else
            defaultFile = new File(defFilePath);
    }

    public static void setDefaultURL(String defURL) {
        if (defURL == null || defURL.length() == 0)
            defaultURL = null;
        else
            defaultURL = defURL;
    }

    public PrintHelper getPrintHelper() {
        return printHelper;
    }

    public void setPrintHelper(PrintHelper printHelper) {
        this.printHelper = printHelper;
    }

    /**
     * Indicates the currently stored state of the shrink to fit printable area
     * property.
     *
     * @return true, to enable shrink to fit printable area;
     * false, otherwise.
     */
    public boolean isShrinkToPrintableArea() {
        return isShrinkToPrintableArea;
    }

    /**
     * Can be set before a Controller.print() is called to enable/disable
     * shrink to fit printable area.
     *
     * @param shrinkToPrintableArea true, to enable shrink to fit printable area;
     *                              false, otherwise.
     */
    public void setShrinkToPrintableArea(boolean shrinkToPrintableArea) {
        isShrinkToPrintableArea = shrinkToPrintableArea;
    }

    /**
     * Number of copies to print
     *
     * @return number of copies to print
     */
    public int getPrintCopies() {
        return printCopies;
    }

    /**
     * Sets the number of print copies that should be make during the next
     * print.
     *
     * @param printCopies one or more copies
     */
    public void setPrintCopies(int printCopies) {
        this.printCopies = printCopies;
    }

    /**
     * Indicates that widget highlighting is enabled.
     *
     * @return true if enabled, otherwise false.
     */
    public boolean isWidgetAnnotationHighlight() {
        return isWidgetAnnotationHighlight;
    }

    /**
     * Indicates that annotation editign tools will be painted is enabled.
     *
     * @return true if enabled, otherwise false.
     */
    public boolean isAnnotationEditingMode() {
        return isAnnotationEditingMode;
    }

    /**
     * Sets the value of widgetAnnotation highlight model.
     *
     * @param isWidgetAnnotationHighlight true to enable highlight, otherwise false.
     */
    public void setIsWidgetAnnotationHighlight(boolean isWidgetAnnotationHighlight) {
        this.isWidgetAnnotationHighlight = isWidgetAnnotationHighlight;
    }

    /**
     * Sets the value of annotation edit mode flag.
     *
     * @param isAnnotationEditingMode true to enable tools, otherwise false.
     */
    public void setIsAnnotationEditingMode(boolean isAnnotationEditingMode) {
        this.isAnnotationEditingMode = isAnnotationEditingMode;
    }

    /**
     * Gets the annotation privacy flag value.
     *
     * @return true indicates a public annotation and false a private annotation state.
     */
    public boolean getAnnotationPrivacy() {
        return annotationPrivacyPublic;
    }

    /**
     * Sets the annotation privacy flag value
     *
     * @param annotationPrivacyPublic true for public, false for private.
     */
    public void setAnnotationPrivacy(boolean annotationPrivacyPublic) {
        this.annotationPrivacyPublic = annotationPrivacyPublic;
    }
}
