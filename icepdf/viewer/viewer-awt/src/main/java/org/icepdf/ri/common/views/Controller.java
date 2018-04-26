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
package org.icepdf.ri.common.views;

import org.icepdf.core.pobjects.Document;
import org.icepdf.core.search.DocumentSearchController;
import org.icepdf.ri.common.NameTreeNode;
import org.icepdf.ri.common.ViewModel;
import org.icepdf.ri.common.WindowManagementCallback;
import org.icepdf.ri.common.utility.outline.OutlineItemTreeNode;
import org.icepdf.ri.util.PropertiesManager;

import java.awt.*;
import java.beans.PropertyChangeListener;
import java.io.InputStream;
import java.net.URL;
import java.util.ResourceBundle;

/**
 * A Controller is the glue between the model and view components.
 * These methods allow the different parts of the view to remain
 * in lock-step with each other and have access to the model,
 * as necessary
 *
 * @since 2.0
 */
public interface Controller extends PropertyChangeListener {
    /**
     * A Document is the root of the object hierarchy, giving access
     * to the contents of a PDF file.
     * Significantly, getDocument().getCatalog().getPageTree().getPage(int pageNumber)
     * gives access to each Page, so that it might be drawn.
     *
     * @return Document root of the PDF file.
     */
    Document getDocument();

    /**
     * Gets the model for the controller.  Most data is actually stored in the documentViewModel.
     *
     * @return controller model
     */
    ViewModel getViewModel();

    /**
     * When viewing a PDF file, one or more pages may be viewed at
     * a single time, but this page is the single page which is most
     * predominantly being displayed.
     *
     * @return The zero-based index of the current Page being displayed
     */
    int getCurrentPageNumber();

    /**
     * Gets controller responsible for Page view UI interaction.
     *
     * @return document view controller.
     */
    DocumentViewController getDocumentViewController();

    /**
     * Gets controller responsible for the document text searches.
     *
     * @return page view controller.
     */
    DocumentSearchController getDocumentSearchController();

    /**
     * Sets the tool mode used for the controller view. Tools such as
     * text selection, panning and annotation selection can be used.
     *
     * @param toolType tool mode constants defined in DocumentViewModel
     */
    void setDocumentToolMode(final int toolType);

    /**
     * Gets the message bundle used by this class.  Message bundle resources
     * are loaded via the JVM default locale.
     *
     * @return message bundle used by this class.
     */
    ResourceBundle getMessageBundle();

    /**
     * Gets the properties manager used to build a dynamically created UI.
     *
     * @return currently properties manager instance.
     */
    PropertiesManager getPropertiesManager();

    /**
     * Show tabbed pane interface for annotation properties.
     *
     * @param annotationComponent annotation to show properties of.
     */
    void showAnnotationProperties(AnnotationComponent annotationComponent);

    /**
     * Sets visibility of the form highlight functionality ot hte opposite of what it was.
     *
     * @param enabled true to enable mode; otherwise, false;
     */
    void setAnnotationEditMode(boolean enabled);

    /**
     * Show tabbed pane interface for annotation properties.
     *
     * @param annotationComponent annotation to show properties of.
     * @param frame               parent frame for centering dialog.
     */
    void showAnnotationProperties(AnnotationComponent annotationComponent, Frame frame);

    /**
     * Not all uses of Controller would result in there existing a Viewer Frame,
     * so this may well return null.
     *
     * @return parent frame if one.
     */
    Frame getViewerFrame();

    /**
     * Adds delta to the ViewerModel's current page index, and updates the display
     * to show the newly selected page. A positive delta indicates moving to later pages,
     * and a negative delta would move to a previous page
     *
     * @param delta Signed integer that's added to the current page index
     * @see org.icepdf.ri.common.views.DocumentViewControllerImpl#getCurrentPageIndex
     * @see org.icepdf.ri.common.views.DocumentViewControllerImpl#setCurrentPageIndex
     */
    void goToDeltaPage(int delta);

    /**
     * Show tabbed pane interface for viewer preferences,  info, security and fonts.
     *
     * @param selectedTab tab to select, PropertiesManager.PROPERTY_SHOW_PREFERENCES_GENERAL
     */
    void showViewerPreferences(String selectedTab);

    /**
     * Check to see if document has permission to extract content.
     *
     * @return true if content extraction should be limited.
     */
    boolean havePermissionToExtractContent();

    /**
     * Check to see if document has permission to be printed.
     *
     * @return true if content printing should be allowed.
     */
    boolean havePermissionToPrint();

    /**
     * Check to see if document can be modified.
     *
     * @return true if content editing is  allowed.
     */
    boolean havePermissionToModifyDocument();

    /**
     * Print the given document
     *
     * @param showDialog If true show a print dialog before starting to print
     */
    void print(boolean showDialog);

    /**
     * Save the file
     */
    void saveFile();

    /**
     * Dispose the controller and all associated resources.
     */
    void dispose();

    /**
     * Opens a Document via the specified byte array.
     *
     * @param data        Byte array containing a valid PDF document.
     * @param offset      the index into the byte array where the PDF data begins
     * @param length      the number of bytes in the byte array belonging to the PDF data
     * @param description When in the GUI for describing this document.
     * @param pathOrURL   Either a file path, or file name, or URL, describing the
     *                    origin of the PDF file. This is typically null. If non-null, it is
     *                    used to populate the default file name in the File..Save a Copy
     *                    dialog summoned in saveFile()
     */
    void openDocument(byte[] data, int offset, int length, String description, String pathOrURL);

    /**
     * Opens a Document via the specified InputStream. This method is a convenience method provided for
     * backwards compatibility.
     * <br>
     * <p><b>Note:</b> This method is less efficient than
     * {@link #openDocument(String)} or {@link #openDocument(URL)}  as it
     * may have to do intermediary data copying, using more memory.
     *
     * @param inputStream InputStream containing a valid PDF document.
     * @param description When in the GUI for describing this document.
     * @param pathOrURL   Either a file path, or file name, or URL, describing the
     *                    origin of the PDF file. This is typically null. If non-null, it is
     *                    used to populate the default file name in the File..Save a Copy
     *                    dialog summoned in saveFile()
     */
    void openDocument(InputStream inputStream, String description, String pathOrURL);

    /**
     * Open a file specified by the given path name.
     *
     * @param pathname String representing a valid file path
     */
    void openDocument(String pathname);

    /**
     * Open a URL specified by the location variable.
     *
     * @param location location of a valid PDF document
     */
    void openDocument(final URL location);

    /**
     * Load the specified file in a new Viewer RI window.
     *
     * @param embeddedDocument document to load in ne window
     * @param fileName         file name of the document in question
     */
    void openDocument(Document embeddedDocument, String fileName);

    /**
     * Opens the specified file in a new window if the window manager is present.  If not the
     * current document is closed and this path opened.
     *
     * @param filename file name to open.
     */
    void openFileInSomeViewer(String filename);

    /**
     * Set the window manager callback for multi window support.
     *
     * @param wm windows callback
     */
    void setWindowManagementCallback(WindowManagementCallback wm);

    /**
     * Gets the window manager callback.
     *
     * @return current window manager call back, can be null.
     */
    WindowManagementCallback getWindowManagementCallback();

    /**
     * Interprets the OutlineItemTreeNode loading ans displaying any associated Destination values.
     *
     * @param node node to interpret and navigate to.
     */
    void followOutlineItem(OutlineItemTreeNode node);

    /**
     * Interprets the NameTreeNode loading ans displaying any associated Destination values.
     *
     * @param node node to interpret and navigate to.
     */
    void followDestinationItem(NameTreeNode node);
}
