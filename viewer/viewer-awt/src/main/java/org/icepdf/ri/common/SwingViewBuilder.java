/*
 * Copyright 2006-2019 ICEsoft Technologies Canada Corp.
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

import org.icepdf.core.pobjects.Page;
import org.icepdf.core.pobjects.graphics.images.references.ImageReference;
import org.icepdf.core.pobjects.graphics.images.references.ImageReferenceFactory;
import org.icepdf.core.util.Library;
import org.icepdf.core.util.SystemProperties;
import org.icepdf.ri.common.utility.annotation.AnnotationPanel;
import org.icepdf.ri.common.utility.annotation.destinations.DestinationsPanel;
import org.icepdf.ri.common.utility.annotation.markup.MarkupAnnotationPanel;
import org.icepdf.ri.common.utility.attachment.AttachmentPanel;
import org.icepdf.ri.common.utility.layers.LayersPanel;
import org.icepdf.ri.common.utility.outline.OutlinesTree;
import org.icepdf.ri.common.utility.search.SearchPanel;
import org.icepdf.ri.common.utility.search.SearchToolBar;
import org.icepdf.ri.common.utility.signatures.SignaturesHandlerPanel;
import org.icepdf.ri.common.utility.thumbs.ThumbnailsPanel;
import org.icepdf.ri.common.views.AbstractDocumentView;
import org.icepdf.ri.common.views.DocumentViewController;
import org.icepdf.ri.common.views.DocumentViewControllerImpl;
import org.icepdf.ri.common.views.PageViewDecorator;
import org.icepdf.ri.images.Images;
import org.icepdf.ri.util.MacOSAdapter;
import org.icepdf.ri.util.ViewerPropertiesManager;

import javax.swing.*;
import java.awt.*;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;

/**
 * <p>The purpose of this class is to facilitate in the building of user interface components
 * that are used to view and interact with PDF Documents.</p>
 * <br>
 * <p>As such, there are three main scenarios that are covered.</p>
 * <ol>
 * <li>Building a standalone Viewer JFrame that will behave much as a full-featured PDF viewing application.</li>
 * <li>Building an embeddable PDF Viewer JPanel, which can easily be added into any existing client application,
 * augmenting its capabilities to include the capacity for viewing PDF Documents.</li>
 * <li>Building a subset of the above, using the various building block methods in this class to construct GUI
 * components that provide certain aspects of the overall functionality, and adding those into
 * your application as desired.</li>
 * </ol>
 * <br>
 * <h2>Building a standalone window</h2>
 * <p>This is the main entry point for building a JFrame containing all of the graphical user interface
 * elements necessary for viewing PDF files.</p>
 * <p>The hierarchy of the methods that are invoked to construct the complete GUI
 * is provided below for your reference.
 * You may choose to use any of the individual methods to construct a sub-set of the complete
 * GUI that meets your specific requirements. This also provides you flexibility in the containers
 * that you add the components into and the overall layout of the application, etc.</p>
 * <br>
 * <b>public JFrame buildViewerFrame()</b>
 * <ul>
 * <li>public JMenuBar buildCompleteMenuBar()
 * <ul>
 * <li>public JMenu buildFileMenu()
 * <ul>
 * <li>public JMenuItem buildOpenFileMenuItem()</li>
 * <li>public JMenuItem buildOpenURLMenuItem()</li>
 * <li>public JMenuItem buildCloseMenuItem()</li>
 * <li>public JMenuItem buildSaveAsFileMenuItem()</li>
 * <li>public JMenuItem buildExportTextMenuItem()</li>
 * <li>public JMenuItem buildExportSVGMenuItem()</li>
 * <li>public JMenuItem buildPermissionsMenuItem()</li>
 * <li>public JMenuItem buildInformationMenuItem()</li>
 * <li>public JMenuItem buildPrintSetupMenuItem()</li>
 * <li>public JMenuItem buildPrintMenuItem()</li>
 * <li>public JMenuItem buildExitMenuItem()</li>
 * </ul>
 * </li>  <!-- buildFileMenu() -->
 * <li>public JMenu buildViewMenu()
 * <ul>
 * <li>public JMenuItem buildFitActualSizeMenuItem()</li>
 * <li>public JMenuItem buildFitPageMenuItem()</li>
 * <li>public JMenuItem buildFitWidthMenuItem()</li>
 * <li>public JMenuItem buildZoomInMenuItem()</li>
 * <li>public JMenuItem buildZoomOutMenuItem()</li>
 * <li>public JMenuItem buildRotateLeftMenuItem()</li>
 * <li>public JMenuItem buildRotateRightMenuItem()</li>
 * <li>public JMenuItem buildShowHideToolBarMenuItem()</li>
 * <li>public JMenuItem buildShowHideUtilityPaneMenuItem()</li>
 * </ul>
 * </li>  <!-- buildViewMenu() -->
 * <li>public JMenu buildDocumentMenu()
 * <ul>
 * <li>public JMenuItem buildFirstPageMenuItem()</li>
 * <li>public JMenuItem buildPreviousPageMenuItem()</li>
 * <li>public JMenuItem buildNextPageMenuItem()</li>
 * <li>public JMenuItem buildLastPageMenuItem()</li>
 * <li>public JMenuItem buildSearchMenuItem()</li>
 * <li>public JMenuItem buildGoToPageMenuItem()</li>
 * </ul>
 * </li>  <!-- buildDocumentMenu() -->
 * <li>public JMenu buildWindowMenu()
 * <ul>
 * <li>public JMenuItem buildMinimiseAllMenuItem()</li>
 * <li>public JMenuItem buildBringAllToFrontMenuItem()</li>
 * <li>public void buildWindowListMenuItems(JMenu menu)</li>
 * </ul>
 * </li>  <!-- buildWindowMenu() -->
 * <li>public JMenu buildHelpMenu()
 * <ul>
 * <li>public JMenuItem buildAboutMenuItem()</li>
 * </ul>
 * </li>  <!-- buildHelpMenu() -->
 * </ul>
 * </li>  <!-- buildCompleteMenuBar() -->
 * <li>public void buildContents(Container cp, boolean embeddableComponent)
 * <ul>
 * <li>public JToolBar buildCompleteToolBar(boolean embeddableComponent)
 * <ul>
 * <li>public JToolBar buildUtilityToolBar(boolean embeddableComponent)
 * <ul>
 * <li>public JButton buildOpenFileButton()</li>
 * <li>public JButton buildSaveAsFileButton()</li>
 * <li>public JButton buildPrintButton()</li>
 * <li>public JButton buildSearchButton()</li>
 * <li>public JButton buildShowHideUtilityPaneButton()</li>
 * </ul>
 * </li>  <!-- buildUtilityToolBar(boolean embeddableComponent) -->
 * <li>public JToolBar buildPageNavigationToolBar()
 * <ul>
 * <li>public JButton buildFirstPageButton()</li>
 * <li>public JButton buildPreviousPageButton()</li>
 * <li>public JButton buildNextPageButton()</li>
 * <li>public JButton buildLastPageButton()</li>
 * <li>public JTextField buildCurrentPageNumberTextField()</li>
 * <li>public JLabel buildNumberOfPagesLabel()</li>
 * </ul>
 * </li>  <!-- buildPageNavigationToolBar() -->
 * <li>public JToolBar buildZoomToolBar()
 * <ul>
 * <li>public JButton buildZoomOutButton()</li>
 * <li>public JComboBox buildZoomCombBox()</li>
 * <li>public JButton buildZoomInButton()</li>
 * </ul>
 * </li>  <!-- buildZoomToolBar() -->
 * <li>public JToolBar buildFitToolBar()
 * <ul>
 * <li>public JToggleButton buildFitActualSizeButton()</li>
 * <li>public JToggleButton buildFitPageButton()</li>
 * <li>public JToggleButton buildFitWidthButton()</li>
 * </ul>
 * </li>  <!-- buildFitToolBar() -->
 * <li>public JToolBar buildRotateToolBar()
 * <ul>
 * <li>public JButton buildRotateLeftButton()</li>
 * <li>public JButton buildRotateRightButton()</li>
 * </ul>
 * </li>  <!-- buildRotateToolBar() -->
 * <li>public JToolBar buildToolToolBar()
 * <ul>
 * <li>public JToggleButton buildPanToolButton()</li>
 * <li>public JToggleButton buildZoomInToolButton()</li>
 * <li>public JToggleButton buildZoomOutToolButton()</li>
 * </ul>
 * </li>  <!-- buildToolToolBar() -->
 * </ul>
 * </li>  <!-- buildCompleteToolBar(boolean embeddableComponent) -->
 * <li>public JSplitPane buildUtilityAndDocumentSplitPane(boolean embeddableComponent)
 * <ul>
 * <li>public JTabbedPane buildUtilityTabbedPane()
 * <ul>
 * <li>public JComponent buildOutlineComponents()</li>
 * <li>public SearchPanel buildSearchPanel()</li>
 * </ul>
 * </li>  <!-- buildUtilityTabbedPane() -->
 * <li>public JScrollPane buildDocumentComponents(boolean embeddableComponent)</li>
 * </ul>
 * </li>  <!-- buildUtilityAndDocumentSplitPane(boolean embeddableComponent) -->
 * <li>public JLabel buildStatusPanel()</li>
 * </ul>
 * </li>  <!-- buildContents(Container cp, boolean embeddableComponent) -->
 * </ul>
 * <h2>Building an embeddable component</h2>
 * <p>This is the main entry point for building a JPanel containing all of the GUI elements
 * necessary for viewing PDF files. The main differences between this and buildViewerFrame() are:</p>
 * <ul>
 * <li>The buildViewerPanel method returns a JPanel which you may then embed anywhere into your GUI
 * <li>The JPanel will not contain a menu bar.
 * <li>The JPanel uses the sub-set of the GUI components available in buildViewerFrame that are
 * suited to an embedded component scenario.
 * </ul>
 * <p>The following hierarchy of methods that are invoked to construct the complete Component GUI
 * is provided for your reference.
 * You may choose to use any of the individual methods below to construct a sub-set of the complete
 * GUI that meets your specific requirements. This also provides you flexibility in the containers
 * that you add the components into and the overall layout of the application, etc.</p>
 * <b>public JPanel buildViewerPanel()</b>
 * <ul>
 * <li>public void buildContents(Container cp, boolean embeddableComponent)
 * <ul>
 * <li>public JToolBar buildCompleteToolBar(boolean embeddableComponent)
 * <ul>
 * <li>public JToolBar buildUtilityToolBar(boolean embeddableComponent)
 * <ul>
 * <li>public JButton buildSaveAsFileButton()</li>
 * <li>public JButton buildPrintButton()</li>
 * <li>public JButton buildSearchButton()</li>
 * <li>public JButton buildShowHideUtilityPaneButton()</li>
 * </ul>
 * </li>  <!-- buildUtilityToolBar(boolean embeddableComponent) -->
 * <li>public JToolBar buildPageNavigationToolBar()
 * <ul>
 * <li>public JButton buildFirstPageButton()</li>
 * <li>public JButton buildPreviousPageButton()</li>
 * <li>public JButton buildNextPageButton()</li>
 * <li>public JButton buildLastPageButton()</li>
 * <li>public JTextField buildCurrentPageNumberTextField()</li>
 * <li>public JLabel buildNumberOfPagesLabel()</li>
 * </ul>
 * </li>  <!-- buildPageNavigationToolBar() -->
 * <li>public JToolBar buildZoomToolBar()
 * <ul>
 * <li>public JButton buildZoomOutButton()</li>
 * <li>public JComboBox buildZoomCombBox()</li>
 * <li>public JButton buildZoomInButton()</li>
 * </ul>
 * </li>  <!-- buildZoomToolBar() -->
 * <li>public JToolBar buildFitToolBar()
 * <ul>
 * <li>public JToggleButton buildFitActualSizeButton()</li>
 * <li>public JToggleButton buildFitPageButton()</li>
 * <li>public JToggleButton buildFitWidthButton()</li>
 * </ul>
 * </li>  <!-- buildFitToolBar() -->
 * <li>public JToolBar buildRotateToolBar()
 * <ul>
 * <li>public JButton buildRotateLeftButton()</li>
 * <li>public JButton buildRotateRightButton()</li>
 * </ul>
 * </li>  <!-- buildRotateToolBar() -->
 * <li>public JToolBar buildToolToolBar()
 * <ul>
 * <li>public JToggleButton buildPanToolButton()</li>
 * <li>public JToggleButton buildZoomInToolButton()</li>
 * <li>public JToggleButton buildZoomOutToolButton()</li>
 * </ul>
 * </li>  <!-- buildToolToolBar() -->
 * </ul>
 * </li>  <!-- buildCompleteToolBar(boolean embeddableComponent) -->
 * <li>public JSplitPane buildUtilityAndDocumentSplitPane(boolean embeddableComponent)
 * <ul>
 * <li>public JTabbedPane buildUtilityTabbedPane()
 * <ul>
 * <li>public JComponent buildOutlineComponents()</li>
 * <li>public SearchPanel buildSearchPanel()</li>
 * </ul>
 * </li>  <!-- buildUtilityTabbedPane() -->
 * <li>public JScrollPane buildDocumentComponents(boolean embeddableComponent)</li>
 * </ul>
 * </li>  <!-- buildUtilityAndDocumentSplitPane(boolean embeddableComponent) -->
 * <li>public JLabel buildStatusPanel()</li>
 * </ul>
 * </li>  <!-- buildContents(Container cp, boolean embeddableComponent) -->
 * </ul>
 *
 * @author Mark Collette
 * @since 2.0
 */
public class SwingViewBuilder {

    private static final Logger logger =
            Logger.getLogger(SwingViewBuilder.class.toString());

    public static final int TOOL_BAR_STYLE_FIXED = 2;
    protected static final float[] DEFAULT_ZOOM_LEVELS = {
            0.05f, 0.10f, 0.25f, 0.50f, 0.75f,
            1.0f, 1.5f, 2.0f, 3.0f,
            4.0f, 8.0f, 16.0f, 24.0f, 32.0f, 64.0f};

    protected SwingController viewerController;
    protected Font buttonFont;
    protected boolean showButtonText;
    protected int toolbarStyle;
    protected float[] zoomLevels;
    protected boolean haveMadeAToolBar;
    protected int documentViewType;
    protected int documentPageFitMode;
    protected String iconSize;
    protected ResourceBundle messageBundle;
    protected static ViewerPropertiesManager propertiesManager;

    protected static boolean isMacOs;

    static {
        isMacOs = SystemProperties.OS_NAME.contains("OS X");
    }

    /**
     * Construct a SwingVewBuilder with all of the default settings
     *
     * @param c Controller that will interact with the GUI
     */
    public SwingViewBuilder(SwingController c) {
        // Use all the defaults
        this(c, null, null, false, SwingViewBuilder.TOOL_BAR_STYLE_FIXED, null,
                DocumentViewControllerImpl.ONE_PAGE_VIEW,
                DocumentViewController.PAGE_FIT_WINDOW_HEIGHT, 0);
    }

    /**
     * Constructor that accepts a different PropertiesManager and otherwise
     * defaults the remaining settings
     *
     * @param c          Controller that will interact with the GUI
     * @param properties PropertiesManager that can customize the UI
     */
    public SwingViewBuilder(SwingController c, ViewerPropertiesManager properties) {
        this(c, properties, null, false, SwingViewBuilder.TOOL_BAR_STYLE_FIXED, null,
                DocumentViewControllerImpl.ONE_PAGE_VIEW,
                DocumentViewController.PAGE_FIT_WINDOW_HEIGHT, 0);
    }

    /**
     * Construct a SwingVewBuilder with all of the default settings
     *
     * @param c                   Controller that will interact with the GUI
     * @param documentViewType    view type to build , single page, single column etc.
     * @param documentPageFitMode fit mode to initially load document with.
     */
    public SwingViewBuilder(SwingController c, int documentViewType,
                            int documentPageFitMode) {
        // Use all the defaults
        this(c, null, null, false, SwingViewBuilder.TOOL_BAR_STYLE_FIXED,
                null, documentViewType, documentPageFitMode, 0);
    }

    /**
     * Construct a SwingVewBuilder with all of the default settings
     *
     * @param c                   Controller that will interact with the GUI
     * @param documentViewType    view type to build , single page, single column etc.
     * @param documentPageFitMode fit mode to initially load document with.
     * @param rotation            default page rotation.
     */
    public SwingViewBuilder(SwingController c, int documentViewType,
                            int documentPageFitMode, float rotation) {
        // Use all the defaults
        this(c, null, null, false, SwingViewBuilder.TOOL_BAR_STYLE_FIXED,
                null, documentViewType, documentPageFitMode, rotation);
    }

    /**
     * Construct a SwingVewBuilder with whichever settings you desire
     *
     * @param c                   Controller that will interact with the GUI
     * @param bf                  button font.
     * @param bt                  show button text.
     * @param ts                  text size
     * @param zl                  zoom levels
     * @param documentViewType    default document view.
     * @param documentPageFitMode page fit mode
     */
    public SwingViewBuilder(SwingController c, Font bf, boolean bt, int ts,
                            float[] zl, final int documentViewType,
                            final int documentPageFitMode) {
        this(c, null, bf, bt, ts, zl, documentViewType, documentPageFitMode, 0);
    }

    /**
     * Construct a SwingVewBuilder with whichever settings you desire
     *
     * @param c                   Controller that will interact with the GUI
     * @param properties          properties manager
     * @param bf                  button font.
     * @param bt                  show button text.
     * @param ts                  text size
     * @param zl                  zoom levels
     * @param documentViewType    default document view.
     * @param documentPageFitMode page fit mode
     * @param rotation            rotation factor
     */
    public SwingViewBuilder(SwingController c, ViewerPropertiesManager properties,
                            Font bf, boolean bt, int ts,
                            float[] zl, final int documentViewType,
                            final int documentPageFitMode, final float rotation) {
        viewerController = c;

        messageBundle = viewerController.getMessageBundle();
        propertiesManager = properties;

        if (propertiesManager == null) {
            propertiesManager = ViewerPropertiesManager.getInstance();
        }
        viewerController.setPropertiesManager(propertiesManager);

        // Apply viewer preferences settings to various core system properties.
        overrideHighlightColor(propertiesManager);

        // update View Controller with previewer document page fit and view type info
        DocumentViewControllerImpl documentViewController = (DocumentViewControllerImpl) viewerController.getDocumentViewController();
        documentViewController.setDocumentViewType(documentViewType, documentPageFitMode);

        buttonFont = bf;
        if (buttonFont == null) {
            buttonFont = buildButtonFont();
        }
        showButtonText = bt;
        toolbarStyle = ts;
        zoomLevels = zl;
        if (zoomLevels == null) {
            zoomLevels = DEFAULT_ZOOM_LEVELS;
        }
        // set default doc view type, single page, facing page, etc.
        this.documentViewType = documentViewType;
        // set default view mode type, fit page, fit width, no-fit.
        this.documentPageFitMode = documentPageFitMode;
        // apply default button size
        iconSize = propertiesManager.getPreferences().get(ViewerPropertiesManager.PROPERTY_ICON_DEFAULT_SIZE, Images.SIZE_LARGE);
    }

    /**
     * This is a standard method for creating a standalone JFrame, that would
     * behave as a fully functional PDF Viewer application.
     *
     * @return a JFrame containing the PDF document's current page visualization,
     * menu bar, accelerator buttons, and document outline if available.
     * @see #buildViewerPanel
     */
    public JFrame buildViewerFrame() {
        JFrame viewer = new JFrame();
        viewer.setIconImage(new ImageIcon(Images.get("icepdf-app-icon-64x64.png")).getImage());
        viewer.setTitle(messageBundle.getString("viewer.window.title.default"));
        viewer.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        JMenuBar menuBar = buildCompleteMenuBar();
        if (menuBar != null)
            viewer.setJMenuBar(menuBar);
        Container contentPane = viewer.getContentPane();
        buildContents(contentPane, false);
        if (viewerController != null)
            viewerController.setViewerFrame(viewer);
        return viewer;
    }

    /**
     * Used by the Applet and Pilot RI code to create an embeddable panel
     * for viewing PDF files, as opposed to buildViewerFrame(), which makes a
     * standalone JFrame
     *
     * @return JPanel containing the PDF document's current page visualization,
     * menu bar, accelerator buttons, and document outline if available.
     * @see #buildViewerFrame
     */
    public JPanel buildViewerPanel() {
        JPanel panel = new JPanel();
        buildContents(panel, true);
        return panel;
    }

    /**
     * The Container will contain the PDF document's current page visualization
     * and document outline if available.
     *
     * @param embeddableComponent true if the component is to be used as an embedded component.
     * @param cp                  Container in which to put components for viewing PDF documents
     */
    public void buildContents(Container cp, boolean embeddableComponent) {
        cp.setLayout(new BorderLayout());
        JToolBar toolBar = buildCompleteToolBar(embeddableComponent);
        if (toolBar != null)
            cp.add(toolBar, BorderLayout.NORTH);
        // Builds the utility pane as well as the main document View, important
        // code entry point.
        JSplitPane utilAndDocSplit =
                buildUtilityAndDocumentSplitPane(embeddableComponent);
        if (utilAndDocSplit != null)
            cp.add(utilAndDocSplit, BorderLayout.CENTER);
        JPanel statusPanel = buildStatusPanel();
        if (statusPanel != null)
            cp.add(statusPanel, BorderLayout.SOUTH);
    }


    public JMenuBar buildCompleteMenuBar() {

        JMenuBar menuBar = new JMenuBar();
        addToMenuBar(menuBar, buildFileMenu());
        addToMenuBar(menuBar, buildEditMenu());
        addToMenuBar(menuBar, buildViewMenu());
        addToMenuBar(menuBar, buildDocumentMenu());
        addToMenuBar(menuBar, buildWindowMenu());
        addToMenuBar(menuBar, buildHelpMenu());

        // If running on MacOS, setup the native app. menu item handlers
        if (isMacOs) {
            try {
                // Generate and register the MacOSAdapter, passing it a hash of all the methods we wish to
                // use as delegates for various com.apple.eawt.ApplicationListener methods
                // for legacy OS X,  no longer works on macOS
                MacOSAdapter.setQuitHandler(viewerController, viewerController.getClass().getDeclaredMethod("exit", (Class[]) null));
                MacOSAdapter.setAboutHandler(viewerController, viewerController.getClass().getDeclaredMethod("showAboutDialog", (Class[]) null));
            } catch (Exception e) {
                logger.log(Level.FINE, "Error occurred while loading the MacOSAdapter:", e);
            }
        }

        return menuBar;
    }

    protected static KeyStroke buildKeyStroke(int keyCode, int modifiers) {
        return buildKeyStroke(keyCode, modifiers, false);
    }

    /**
     * Create and return a KeyStroke with the specified code and modifier
     * Note this will automatically return null if the PROPERTY_SHOW_KEYBOARD_SHORTCUTS
     * property is 'false'
     *
     * @param keyCode   to build
     * @param modifiers to build
     * @param onRelease to build
     * @return built KeyStroke
     */
    protected static KeyStroke buildKeyStroke(int keyCode, int modifiers, boolean onRelease) {
        doubleCheckPropertiesManager();

        if (propertiesManager.checkAndStoreBooleanProperty(
                ViewerPropertiesManager.PROPERTY_SHOW_KEYBOARD_SHORTCUTS,
                true)) {
            return KeyStroke.getKeyStroke(keyCode, modifiers, onRelease);
        }

        return null;
    }

    /**
     * Return a valid mnemonic for the passed character, unless the
     * PropertiesManager.PROPERTY_SHOW_KEYBOARD_SHORTCUTS property is 'false',
     * in which case we'll return -1
     *
     * @param mnemonic to build
     * @return built mnemonic
     */
    protected int buildMnemonic(char mnemonic) {
        doubleCheckPropertiesManager();

        if (propertiesManager.checkAndStoreBooleanProperty(
                ViewerPropertiesManager.PROPERTY_SHOW_KEYBOARD_SHORTCUTS,
                true)) {
            return mnemonic;
        }

        return -1;
    }

    public JMenu buildFileMenu() {
        JMenu fileMenu = new JMenu(messageBundle.getString("viewer.menu.file.label"));
        fileMenu.setMnemonic(buildMnemonic(messageBundle.getString("viewer.menu.file.mnemonic").charAt(0)));
        JMenuItem openFileMenuItem = buildOpenFileMenuItem();
        JMenuItem openURLMenuItem = buildOpenURLMenuItem();
        if (openFileMenuItem != null && openURLMenuItem != null) {
            JMenu openSubMenu = new JMenu(messageBundle.getString("viewer.menu.open.label"));
            openSubMenu.setIcon(new ImageIcon(Images.get("open_a_24.png")));
            openSubMenu.setDisabledIcon(new ImageIcon(Images.get("open_i_24.png")));
            openSubMenu.setRolloverIcon(new ImageIcon(Images.get("open_r_24.png")));
            addToMenu(openSubMenu, openFileMenuItem);
            addToMenu(openSubMenu, openURLMenuItem);
            addToMenu(fileMenu, openSubMenu);
        } else if (openFileMenuItem != null || openURLMenuItem != null) {
            addToMenu(fileMenu, openFileMenuItem);
            addToMenu(fileMenu, openURLMenuItem);
        }
        addToMenu(fileMenu, buildRecentFileMenuItem());
        fileMenu.addSeparator();
        addToMenu(fileMenu, buildCloseMenuItem());
        addToMenu(fileMenu, buildSaveAsFileMenuItem());
        addToMenu(fileMenu, buildExportTextMenuItem());
        fileMenu.addSeparator();
        addToMenu(fileMenu, buildPropertiesMenuItem());
//        addToMenu(fileMenu, buildPermissionsMenuItem());
//        addToMenu(fileMenu, buildInformationMenuItem());
//        addToMenu(fileMenu, buildFontInformationMenuItem());
        fileMenu.addSeparator();
        addToMenu(fileMenu, buildPrintSetupMenuItem());
        addToMenu(fileMenu, buildPrintMenuItem());
        if (!isMacOs) {
            // Not on a Mac, so create the Exit menu item.
            fileMenu.addSeparator();
            addToMenu(fileMenu, buildExitMenuItem());
        }
        return fileMenu;
    }

    public JMenu buildRecentFileMenuItem() {
        if (propertiesManager.checkAndStoreBooleanProperty(ViewerPropertiesManager.PROPERTY_SHOW_MENU_RECENT_FILES)) {
            JMenu recentFilesSubMenu = new JMenu(messageBundle.getString("viewer.menu.open.recentFiles.label"));
            viewerController.setRecentFilesSubMenu(recentFilesSubMenu);
            viewerController.refreshRecentFileMenuItem();
            return recentFilesSubMenu;
        } else {
            return null;
        }
    }

    public JMenuItem buildOpenFileMenuItem() {
        JMenuItem mi = makeMenuItem(
                messageBundle.getString("viewer.menu.open.file.label"),
                buildKeyStroke(KeyEventConstants.KEY_CODE_OPEN_FILE, KeyEventConstants.MODIFIER_OPEN_FILE));
        if (viewerController != null && mi != null)
            viewerController.setOpenFileMenuItem(mi);
        return mi;
    }

    public JMenuItem buildOpenURLMenuItem() {
        JMenuItem mi = makeMenuItem(
                messageBundle.getString("viewer.menu.open.URL.label"),
                buildKeyStroke(KeyEventConstants.KEY_CODE_OPEN_URL, KeyEventConstants.MODIFIER_OPEN_URL));
        if (viewerController != null && mi != null)
            viewerController.setOpenURLMenuItem(mi);
        return mi;
    }

    public JMenuItem buildCloseMenuItem() {
        JMenuItem mi = makeMenuItem(
                messageBundle.getString("viewer.menu.close.label"), null, null,
                buildKeyStroke(KeyEventConstants.KEY_CODE_CLOSE, KeyEventConstants.MODIFIER_CLOSE));
        if (viewerController != null && mi != null)
            viewerController.setCloseMenuItem(mi);
        return mi;
    }

    public JMenuItem buildSaveAsFileMenuItem() {
        JMenuItem mi = makeMenuItem(
                messageBundle.getString("viewer.menu.saveAs.label"), "save",
                Images.SIZE_SMALL,
                buildKeyStroke(KeyEventConstants.KEY_CODE_SAVE_AS, KeyEventConstants.MODIFIER_SAVE_AS, false));
        if (viewerController != null && mi != null)
            viewerController.setSaveAsFileMenuItem(mi);
        return mi;
    }

    public JMenuItem buildExportTextMenuItem() {
        JMenuItem mi = makeMenuItem(
                messageBundle.getString("viewer.menu.exportText.label"), null, null, null);
        if (viewerController != null && mi != null)
            viewerController.setExportTextMenuItem(mi);
        return mi;
    }

    public JMenuItem buildPropertiesMenuItem() {
        JMenuItem mi = makeMenuItem(
                messageBundle.getString("viewer.menu.documentProperties.label"), null, null,
                buildKeyStroke(KeyEventConstants.KEY_CODE_DOCUMENT_PROPERTIES,
                        KeyEventConstants.MODIFIER_DOCUMENT_PROPERTIES));
        if (viewerController != null && mi != null)
            viewerController.setPropertiesMenuItem(mi);
        return mi;
    }

    public JMenuItem buildPermissionsMenuItem() {
        JMenuItem mi = makeMenuItem(
                messageBundle.getString("viewer.menu.documentPermission.label"), null, null, null);
        if (viewerController != null && mi != null)
            viewerController.setPermissionsMenuItem(mi);
        return mi;
    }

    public JMenuItem buildInformationMenuItem() {
        JMenuItem mi = makeMenuItem(
                messageBundle.getString("viewer.menu.documentInformation.label"), null, null, null);
        if (viewerController != null && mi != null)
            viewerController.setInformationMenuItem(mi);
        return mi;
    }

    public JMenuItem buildFontInformationMenuItem() {
        JMenuItem mi = makeMenuItem(
                messageBundle.getString("viewer.menu.documentFonts.label"), null, null, null);
        if (viewerController != null && mi != null)
            viewerController.setFontInformationMenuItem(mi);
        return mi;
    }

    public JMenuItem buildPrintSetupMenuItem() {
        JMenuItem mi = makeMenuItem(
                messageBundle.getString("viewer.menu.printSetup.label"), null, null,
                buildKeyStroke(KeyEventConstants.KEY_CODE_PRINT_SETUP, KeyEventConstants.MODIFIER_PRINT_SETUP, false));
        if (viewerController != null && mi != null)
            viewerController.setPrintSetupMenuItem(mi);
        return mi;
    }

    public JMenuItem buildPrintMenuItem() {
        JMenuItem mi = makeMenuItem(
                messageBundle.getString("viewer.menu.print.label"), "print",
                Images.SIZE_SMALL,
                buildKeyStroke(KeyEventConstants.KEY_CODE_PRINT, KeyEventConstants.MODIFIER_PRINT));
        if (viewerController != null && mi != null)
            viewerController.setPrintMenuItem(mi);
        return mi;
    }

    public JMenuItem buildExitMenuItem() {
        JMenuItem mi = makeMenuItem(
                messageBundle.getString("viewer.menu.exit.label"), null, null,
                buildKeyStroke(KeyEventConstants.KEY_CODE_EXIT, KeyEventConstants.MODIFIER_EXIT));
        if (viewerController != null && mi != null)
            viewerController.setExitMenuItem(mi);
        return mi;
    }

    public JMenu buildEditMenu() {
        JMenu viewMenu = new JMenu(messageBundle.getString("viewer.menu.edit.label"));
        viewMenu.setMnemonic(buildMnemonic(messageBundle.getString("viewer.menu.edit.mnemonic").charAt(0)));
        addToMenu(viewMenu, buildUndoMenuItem());
        addToMenu(viewMenu, buildRedoMenuItem());
        viewMenu.addSeparator();
        addToMenu(viewMenu, buildCopyMenuItem());
        addToMenu(viewMenu, buildDeleteMenuItem());
        viewMenu.addSeparator();
        addToMenu(viewMenu, buildSelectAllMenuItem());
        addToMenu(viewMenu, buildDeselectAllMenuItem());
        viewMenu.addSeparator();
        addToMenu(viewMenu, buildPreferencesMenuItem());
        return viewMenu;
    }

    public JMenuItem buildUndoMenuItem() {
        JMenuItem mi = makeMenuItem(
                messageBundle.getString("viewer.menu.edit.undo.label"),
                null, null, buildKeyStroke(KeyEventConstants.KEY_CODE_UNDO,
                        KeyEventConstants.MODIFIER_UNDO));
        if (viewerController != null && mi != null)
            viewerController.setUndoMenuItem(mi);
        return mi;
    }

    public JMenuItem buildRedoMenuItem() {
        JMenuItem mi = makeMenuItem(
                messageBundle.getString("viewer.menu.edit.redo.label"),
                null, null, buildKeyStroke(KeyEventConstants.KEY_CODE_REDO,
                        KeyEventConstants.MODIFIER_REDO));
        if (viewerController != null && mi != null)
            viewerController.setReduMenuItem(mi);
        return mi;
    }

    public JMenuItem buildCopyMenuItem() {
        JMenuItem mi = makeMenuItem(
                messageBundle.getString("viewer.menu.edit.copy.label"),
                null, null, buildKeyStroke(KeyEventConstants.KEY_CODE_COPY,
                        KeyEventConstants.MODIFIER_COPY));
        if (viewerController != null && mi != null)
            viewerController.setCopyMenuItem(mi);
        return mi;
    }

    public JMenuItem buildDeleteMenuItem() {
        JMenuItem mi = makeMenuItem(
                messageBundle.getString("viewer.menu.edit.delete.label"),
                null, null, buildKeyStroke(KeyEventConstants.KEY_CODE_DELETE,
                        KeyEventConstants.MODIFIER_DELETE));
        if (viewerController != null && mi != null)
            viewerController.setDeleteMenuItem(mi);
        return mi;
    }

    public JMenuItem buildSelectAllMenuItem() {
        JMenuItem mi = makeMenuItem(
                messageBundle.getString("viewer.menu.edit.selectAll.label"),
                null, null, buildKeyStroke(KeyEventConstants.KEY_CODE_SELECT_ALL,
                        KeyEventConstants.MODIFIER_SELECT_ALL));
        if (viewerController != null && mi != null)
            viewerController.setSelectAllMenuItem(mi);
        return mi;
    }

    public JMenuItem buildDeselectAllMenuItem() {
        JMenuItem mi = makeMenuItem(
                messageBundle.getString("viewer.menu.edit.deselectAll.label"),
                null, null, buildKeyStroke(KeyEventConstants.KEY_CODE_DESELECT_ALL,
                        KeyEventConstants.MODIFIER_DESELECT_ALL));
        if (viewerController != null && mi != null)
            viewerController.setDeselectAllMenuItem(mi);
        return mi;
    }

    public JMenuItem buildPreferencesMenuItem() {
        JMenuItem mi = makeMenuItem(
                messageBundle.getString("viewer.menu.edit.preferences.label"),
                null, null, buildKeyStroke(KeyEventConstants.KEY_CODE_PREFERENCES,
                        KeyEventConstants.MODIFIER_PREFERENCES));
        if (viewerController != null && mi != null)
            viewerController.setPreferencesMenuItem(mi);
        return mi;
    }

    public JMenu buildViewMenu() {
        JMenu viewMenu = new JMenu(messageBundle.getString("viewer.menu.view.label"));
        viewMenu.setMnemonic(buildMnemonic(messageBundle.getString("viewer.menu.view.mnemonic").charAt(0)));
        addToMenu(viewMenu, buildFitActualSizeMenuItem());
        addToMenu(viewMenu, buildFitPageMenuItem());
        addToMenu(viewMenu, buildFitWidthMenuItem());
        viewMenu.addSeparator();
        addToMenu(viewMenu, buildFullScreenMenuItem());
        viewMenu.addSeparator();
        addToMenu(viewMenu, buildZoomInMenuItem());
        addToMenu(viewMenu, buildZoomOutMenuItem());
        viewMenu.addSeparator();
        addToMenu(viewMenu, buildRotateLeftMenuItem());
        addToMenu(viewMenu, buildRotateRightMenuItem());
        viewMenu.addSeparator();
        addToMenu(viewMenu, buildShowHideToolBarMenuItem());
        addToMenu(viewMenu, buildShowHideUtilityPaneMenuItem());
        return viewMenu;
    }

    public JMenuItem buildFitActualSizeMenuItem() {
        JMenuItem mi = makeMenuItem(
                messageBundle.getString("viewer.menu.view.actualSize.label"),
                "actual_size", Images.SIZE_SMALL,
                buildKeyStroke(KeyEventConstants.KEY_CODE_FIT_ACTUAL, KeyEventConstants.MODIFIER_FIT_ACTUAL));
        if (viewerController != null && mi != null)
            viewerController.setFitActualSizeMenuItem(mi);
        return mi;
    }

    public JMenuItem buildFitPageMenuItem() {
        JMenuItem mi = makeMenuItem(
                messageBundle.getString("viewer.menu.view.fitInWindow.label"),
                "fit_window", Images.SIZE_SMALL,
                buildKeyStroke(KeyEventConstants.KEY_CODE_FIT_PAGE, KeyEventConstants.MODIFIER_FIT_PAGE));
        if (viewerController != null && mi != null)
            viewerController.setFitPageMenuItem(mi);
        return mi;
    }

    public JMenuItem buildFitWidthMenuItem() {
        JMenuItem mi = makeMenuItem(
                messageBundle.getString("viewer.menu.view.fitWidth.label"),
                "fit_width", Images.SIZE_SMALL,
                buildKeyStroke(KeyEventConstants.KEY_CODE_FIT_WIDTH, KeyEventConstants.MODIFIER_FIT_WIDTH));
        if (viewerController != null && mi != null)
            viewerController.setFitWidthMenuItem(mi);
        return mi;
    }

    public JMenuItem buildFullScreenMenuItem() {
        JMenuItem mi = makeMenuItem(
                messageBundle.getString("viewer.menu.view.fullScreen.label"),
                "fullscreen", Images.SIZE_SMALL,
                buildKeyStroke(KeyEventConstants.KEY_CODE_FULL_SCREEN, KeyEventConstants.MODIFIER_FULL_SCREEN));
        if (viewerController != null && mi != null)
            viewerController.setFullScreenMenuItem(mi);
        return mi;
    }

    public JMenuItem buildZoomInMenuItem() {
        JMenuItem mi = makeMenuItem(
                messageBundle.getString("viewer.menu.view.zoomIn.label"),
                "zoom_in", Images.SIZE_SMALL,
                buildKeyStroke(KeyEventConstants.KEY_CODE_ZOOM_IN, KeyEventConstants.MODIFIER_ZOOM_IN, false));
        if (viewerController != null && mi != null)
            viewerController.setZoomInMenuItem(mi);
        return mi;
    }

    public JMenuItem buildZoomOutMenuItem() {
        JMenuItem mi = makeMenuItem(
                messageBundle.getString("viewer.menu.view.zoomOut.label"),
                "zoom_out", Images.SIZE_SMALL,
                buildKeyStroke(KeyEventConstants.KEY_CODE_ZOOM_OUT, KeyEventConstants.MODIFIER_ZOOM_OUT, false));
        if (viewerController != null && mi != null)
            viewerController.setZoomOutMenuItem(mi);
        return mi;
    }

    public JMenuItem buildRotateLeftMenuItem() {
        JMenuItem mi = makeMenuItem(
                messageBundle.getString("viewer.menu.view.rotateLeft.label"),
                "rotate_left", Images.SIZE_SMALL,
                buildKeyStroke(KeyEventConstants.KEY_CODE_ROTATE_LEFT, KeyEventConstants.MODIFIER_ROTATE_LEFT));
        if (viewerController != null && mi != null)
            viewerController.setRotateLeftMenuItem(mi);
        return mi;
    }

    public JMenuItem buildRotateRightMenuItem() {
        JMenuItem mi = makeMenuItem(
                messageBundle.getString("viewer.menu.view.rotateRight.label"),
                "rotate_right", Images.SIZE_SMALL,
                buildKeyStroke(KeyEventConstants.KEY_CODE_ROTATE_RIGHT, KeyEventConstants.MODIFIER_ROTATE_RIGHT));
        if (viewerController != null && mi != null)
            viewerController.setRotateRightMenuItem(mi);
        return mi;
    }

    public JMenuItem buildShowHideToolBarMenuItem() {
        JMenuItem mi = makeMenuItem("", null);
        if (viewerController != null && mi != null)
            viewerController.setShowHideToolBarMenuItem(mi);
        return mi;
    }

    public JMenuItem buildShowHideUtilityPaneMenuItem() {
        JMenuItem mi = makeMenuItem("", null);
        if (viewerController != null && mi != null)
            viewerController.setShowHideUtilityPaneMenuItem(mi);
        return mi;
    }

    public JMenu buildDocumentMenu() {
        JMenu documentMenu = new JMenu(messageBundle.getString("viewer.menu.document.label"));
        documentMenu.setMnemonic(buildMnemonic(messageBundle.getString("viewer.menu.document.mnemonic").charAt(0)));
        addToMenu(documentMenu, buildFirstPageMenuItem());
        addToMenu(documentMenu, buildPreviousPageMenuItem());
        addToMenu(documentMenu, buildNextPageMenuItem());
        addToMenu(documentMenu, buildLastPageMenuItem());
        documentMenu.addSeparator();
        addToMenu(documentMenu, buildSearchMenuItem());
        addToMenu(documentMenu, buildAdvancedSearchMenuItem());
        addToMenu(documentMenu, buildSearchNextMenuItem());
        addToMenu(documentMenu, buildSearchPreviousMenuItem());
        documentMenu.addSeparator();
        addToMenu(documentMenu, buildGoToPageMenuItem());
        return documentMenu;
    }

    public JMenuItem buildFirstPageMenuItem() {
        JMenuItem mi = makeMenuItem(
                messageBundle.getString("viewer.menu.document.firstPage.label"),
                "first", Images.SIZE_SMALL,
                buildKeyStroke(KeyEventConstants.KEY_CODE_FIRST_PAGE, KeyEventConstants.MODIFIER_FIRST_PAGE));
        if (viewerController != null && mi != null)
            viewerController.setFirstPageMenuItem(mi);
        return mi;
    }

    public JMenuItem buildPreviousPageMenuItem() {
        JMenuItem mi = makeMenuItem(
                messageBundle.getString("viewer.menu.document.previousPage.label"),
                "back", Images.SIZE_SMALL,
                buildKeyStroke(KeyEventConstants.KEY_CODE_PREVIOUS_PAGE, KeyEventConstants.MODIFIER_PREVIOUS_PAGE));
        if (viewerController != null && mi != null)
            viewerController.setPreviousPageMenuItem(mi);
        return mi;
    }

    public JMenuItem buildNextPageMenuItem() {
        JMenuItem mi = makeMenuItem(
                messageBundle.getString("viewer.menu.document.nextPage.label"),
                "forward", Images.SIZE_SMALL,
                buildKeyStroke(KeyEventConstants.KEY_CODE_NEXT_PAGE, KeyEventConstants.MODIFIER_NEXT_PAGE));
        if (viewerController != null && mi != null)
            viewerController.setNextPageMenuItem(mi);
        return mi;
    }

    public JMenuItem buildLastPageMenuItem() {
        JMenuItem mi = makeMenuItem(
                messageBundle.getString("viewer.menu.document.lastPage.label"),
                "last", Images.SIZE_SMALL,
                buildKeyStroke(KeyEventConstants.KEY_CODE_LAST_PAGE, KeyEventConstants.MODIFIER_LAST_PAGE));
        if (viewerController != null && mi != null)
            viewerController.setLastPageMenuItem(mi);
        return mi;
    }

    public JMenuItem buildSearchMenuItem() {
        JMenuItem mi = makeMenuItem(
                messageBundle.getString("viewer.menu.document.search.label"),
                buildKeyStroke(KeyEventConstants.KEY_CODE_SEARCH, KeyEventConstants.MODIFIER_SEARCH));
        if (viewerController != null && mi != null)
            viewerController.setSearchMenuItem(mi);
        return mi;
    }

    public JMenuItem buildAdvancedSearchMenuItem() {
        final JMenuItem mi = makeMenuItem(messageBundle.getString("viewer.toolbar.search.advanced.label"), buildKeyStroke(KeyEventConstants.KEY_CODE_SEARCH, KeyEventConstants.MODIFIER_ADVANCED_SEARCH));
        if (viewerController != null && mi != null) {
            viewerController.setAdvancedSearchMenuItem(mi);
        }
        return mi;
    }

    public JMenuItem buildSearchNextMenuItem() {
        JMenuItem mi = makeMenuItem(
                messageBundle.getString("viewer.menu.document.search.next.label"),
                buildKeyStroke(KeyEventConstants.KEY_CODE_SEARCH_NEXT, KeyEventConstants.MODIFIER_SEARCH_NEXT));
        if (viewerController != null && mi != null)
            viewerController.setSearchNextMenuItem(mi);
        return mi;
    }

    public JMenuItem buildSearchPreviousMenuItem() {
        JMenuItem mi = makeMenuItem(
                messageBundle.getString("viewer.menu.document.search.previous.label"),
                buildKeyStroke(KeyEventConstants.KEY_CODE_SEARCH_PREVIOUS, KeyEventConstants.MODIFIER_SEARCH_PREVIOUS));
        if (viewerController != null && mi != null)
            viewerController.setSearchPreviousMenuItem(mi);
        return mi;
    }

    public JMenuItem buildGoToPageMenuItem() {
        JMenuItem mi = makeMenuItem(
                messageBundle.getString("viewer.menu.document.gotToPage.label"),
                buildKeyStroke(KeyEventConstants.KEY_CODE_GOTO, KeyEventConstants.MODIFIER_GOTO));
        if (viewerController != null && mi != null)
            viewerController.setGoToPageMenuItem(mi);
        return mi;
    }

    public JMenu buildWindowMenu() {
        final JMenu windowMenu = new JMenu(messageBundle.getString("viewer.menu.window.label"));
        windowMenu.setMnemonic(buildMnemonic(messageBundle.getString("viewer.menu.window.mnemonic").charAt(0)));
        addToMenu(windowMenu, buildMinimiseAllMenuItem());
        addToMenu(windowMenu, buildBringAllToFrontMenuItem());
        windowMenu.addSeparator();
        addToMenu(windowMenu, buildShowAnnotationPreviewMenuItem());
        windowMenu.addSeparator();
        final int allowedCount = windowMenu.getItemCount();
        windowMenu.addMenuListener(new javax.swing.event.MenuListener() {
            public void menuCanceled(javax.swing.event.MenuEvent e) {
            }

            public void menuDeselected(javax.swing.event.MenuEvent e) {
            }

            public void menuSelected(javax.swing.event.MenuEvent e) {
                int count = windowMenu.getItemCount();
                while (count > allowedCount) {
                    windowMenu.remove(count - 1);
                    count--;
                }
                buildWindowListMenuItems(windowMenu);
            }
        });
        return windowMenu;
    }

    public JMenuItem buildMinimiseAllMenuItem() {
        JMenuItem mi = makeMenuItem(messageBundle.getString("viewer.menu.window.minAll.label"), null);
        mi.setMnemonic(buildMnemonic(messageBundle.getString("viewer.menu.window.minAll.mnemonic").charAt(0)));
        if (viewerController != null)
            viewerController.setMinimiseAllMenuItem(mi);
        return mi;
    }

    public JMenuItem buildBringAllToFrontMenuItem() {
        JMenuItem mi = makeMenuItem(messageBundle.getString("viewer.menu.window.frontAll.label"), null);
        mi.setMnemonic(buildMnemonic(messageBundle.getString("viewer.menu.window.frontAll.mnemonic").charAt(0)));
        if (viewerController != null)
            viewerController.setBringAllToFrontMenuItem(mi);
        return mi;
    }

    public JMenuItem buildShowAnnotationPreviewMenuItem() {
        JMenuItem mi = makeMenuItem(messageBundle.getString("viewer.menu.window.annotationPreview.label"), null);
        mi.setMnemonic(buildMnemonic(messageBundle.getString("viewer.menu.window.annotationPreview.mnemonic").charAt(0)));
        if (viewerController != null)
            viewerController.setAnnotationPreviewMenuItem(mi);
        return mi;
    }

    @SuppressWarnings("unchecked")
    public void buildWindowListMenuItems(JMenu menu) {
        if (viewerController != null &&
                viewerController.getWindowManagementCallback() != null) {
            WindowManagementCallback winMgr = viewerController.getWindowManagementCallback();
            List<Object> windowDocOriginList = (List<Object>) winMgr.getWindowDocumentOriginList(viewerController);

            // Get the current window index, if it's given, and remove it from the list
            int currWindowIndex = -1;
            int count = windowDocOriginList.size();
            if (count > 0 && windowDocOriginList.get(count - 1) instanceof Integer) {
                currWindowIndex = (Integer) windowDocOriginList.remove(--count);
            }

            shortenDocumentOrigins(windowDocOriginList);

            List<JMenuItem> windowListMenuItems =
                    new ArrayList<>(Math.max(count, 1));
            for (int i = 0; i < count; i++) {
                String number = Integer.toString(i + 1);
                String label = null;
                String mnemonic = null;
                try {
                    label = messageBundle.getString("viewer.menu.window." + number + ".label");
                    mnemonic = messageBundle.getString("viewer.menu.window." + number + ".mnemonic");
                } catch (Exception e) {
                    logger.log(Level.FINER,
                            "Error setting viewer window window title", e);
                }
                // Allows the user to have an arbitrary number of predefined entries
                String identifier = (String) windowDocOriginList.get(i);
                if (identifier == null)
                    identifier = "";
                String miText;
                if (label != null && label.length() > 0)
                    miText = number + "  " + identifier;
                else
                    miText = "    " + identifier;
                JMenuItem mi = new JMenuItem(miText);
                if (mnemonic != null && number.length() == 1)
                    mi.setMnemonic(buildMnemonic(number.charAt(0)));
                if (currWindowIndex == i)
                    mi.setEnabled(false);
                menu.add(mi);
                windowListMenuItems.add(mi);
            }
            viewerController.setWindowListMenuItems(windowListMenuItems);
        }
    }

    protected void shortenDocumentOrigins(List<Object> windowDocOriginList) {
        // At some point we should detect the same filename
        //   in different subdirectories, and keep some of the
        //   directory information, to help differentiate them
        for (int i = windowDocOriginList.size() - 1; i >= 0; i--) {
            String identifier = (String) windowDocOriginList.get(i);
            if (identifier == null)
                continue;
            int separatorIndex = identifier.lastIndexOf(java.io.File.separator);
            int forwardSlashIndex = identifier.lastIndexOf("/");
            int backwardSlashIndex = identifier.lastIndexOf("\\");
            int cutIndex = Math.max(separatorIndex, Math.max(forwardSlashIndex, backwardSlashIndex));
            if (cutIndex >= 0) {
                identifier = identifier.substring(cutIndex);
                windowDocOriginList.set(i, identifier);
            }
        }
    }

    public JMenu buildHelpMenu() {
        JMenu helpMenu = new JMenu(messageBundle.getString("viewer.menu.help.label"));
        helpMenu.setMnemonic(buildMnemonic(messageBundle.getString("viewer.menu.help.mnemonic").charAt(0)));

        if (!isMacOs) {
            // Not on a Mac, so create the About menu item.
            addToMenu(helpMenu, buildAboutMenuItem());
        }
        return helpMenu;
    }

    public JMenuItem buildAboutMenuItem() {

        JMenuItem mi = makeMenuItem(messageBundle.getString("viewer.menu.help.about.label"), null);
        if (viewerController != null && mi != null)
            viewerController.setAboutMenuItem(mi);
        return mi;
    }


    public JToolBar buildCompleteToolBar(boolean embeddableComponent) {
        JToolBar toolbar = new JToolBar();
        toolbar.setLayout(new ToolbarLayout(ToolbarLayout.LEFT, 0, 0));
        commonToolBarSetup(toolbar, true);

        // Attempt to get the properties manager so we can configure which toolbars are visible
        doubleCheckPropertiesManager();

        // Build the main set of toolbars based on the property file configuration
        if (propertiesManager.checkAndStoreBooleanProperty(ViewerPropertiesManager.PROPERTY_SHOW_TOOLBAR_UTILITY))
            addToToolBar(toolbar, buildUtilityToolBar(embeddableComponent, propertiesManager));
        if (propertiesManager.checkAndStoreBooleanProperty(ViewerPropertiesManager.PROPERTY_SHOW_TOOLBAR_PAGENAV))
            addToToolBar(toolbar, buildPageNavigationToolBar());
        if (propertiesManager.checkAndStoreBooleanProperty(ViewerPropertiesManager.PROPERTY_SHOW_TOOLBAR_ZOOM))
            addToToolBar(toolbar, buildZoomToolBar());
        if (propertiesManager.checkAndStoreBooleanProperty(ViewerPropertiesManager.PROPERTY_SHOW_TOOLBAR_FULL_SCREEN))
            addToToolBar(toolbar, buildFullScreenToolBar());
        if (propertiesManager.checkAndStoreBooleanProperty(ViewerPropertiesManager.PROPERTY_SHOW_TOOLBAR_FIT))
            addToToolBar(toolbar, buildFitToolBar());
        if (propertiesManager.checkAndStoreBooleanProperty(ViewerPropertiesManager.PROPERTY_SHOW_TOOLBAR_ROTATE))
            addToToolBar(toolbar, buildRotateToolBar());
        if (propertiesManager.checkAndStoreBooleanProperty(ViewerPropertiesManager.PROPERTY_SHOW_TOOLBAR_TOOL))
            addToToolBar(toolbar, buildToolToolBar());
        if (propertiesManager.checkAndStoreBooleanProperty(ViewerPropertiesManager.PROPERTY_SHOW_TOOLBAR_ANNOTATION))
            addToToolBar(toolbar, buildAnnotationlToolBar());
        if (propertiesManager.checkAndStoreBooleanProperty(ViewerPropertiesManager.PROPERTY_SHOW_TOOLBAR_FORMS))
            addToToolBar(toolbar, buildFormsToolBar());
        if (propertiesManager.checkAndStoreBooleanProperty(ViewerPropertiesManager.PROPERTY_SHOW_TOOLBAR_SEARCH))
            addToToolBar(toolbar, buildQuickSearchToolBar());

        // Set the toolbar back to null if no components were added
        // The result of this will properly disable the necessary menu items for controlling the toolbar
        if (toolbar.getComponentCount() == 0) {
            toolbar = null;
        }

        if ((viewerController != null) && (toolbar != null))
            viewerController.setCompleteToolBar(toolbar);

        return toolbar;
    }

    public JToolBar buildUtilityToolBar(boolean embeddableComponent) {
        return buildUtilityToolBar(embeddableComponent, null);
    }

    public JToolBar buildUtilityToolBar(boolean embeddableComponent, ViewerPropertiesManager propertiesManager) {
        JToolBar toolbar = new JToolBar();
        commonToolBarSetup(toolbar, false);
        // if embeddable component, we don't want to create the open dialog, as we
        // have no window manager for this case.
        if ((!embeddableComponent) &&
                (propertiesManager.checkAndStoreBooleanProperty(ViewerPropertiesManager.PROPERTY_SHOW_UTILITY_OPEN)))
            addToToolBar(toolbar, buildOpenFileButton());
        if (propertiesManager.checkAndStoreBooleanProperty(ViewerPropertiesManager.PROPERTY_SHOW_UTILITY_SAVE))
            addToToolBar(toolbar, buildSaveAsFileButton());
        if (propertiesManager.checkAndStoreBooleanProperty(ViewerPropertiesManager.PROPERTY_SHOW_UTILITY_PRINT))
            addToToolBar(toolbar, buildPrintButton());
        if (propertiesManager.checkAndStoreBooleanProperty(ViewerPropertiesManager.PROPERTY_SHOW_UTILITY_SEARCH))
            addToToolBar(toolbar, buildSearchButton());
        if (propertiesManager.checkAndStoreBooleanProperty(ViewerPropertiesManager.PROPERTY_SHOW_UTILITY_UPANE))
            addToToolBar(toolbar, buildShowHideUtilityPaneButton());

        // Don't bother with this toolbar if we don't have any visible buttons
        if (toolbar.getComponentCount() == 0) {
            return null;
        }

        return toolbar;
    }

    public JButton buildOpenFileButton() {
        JButton btn = makeToolbarButton(
                messageBundle.getString("viewer.toolbar.open.label"),
                messageBundle.getString("viewer.toolbar.open.tooltip"),
                "open", iconSize, buttonFont);
        if (viewerController != null && btn != null)
            viewerController.setOpenFileButton(btn);
        return btn;
    }

    public JButton buildSaveAsFileButton() {
        JButton btn = makeToolbarButton(
                messageBundle.getString("viewer.toolbar.saveAs.label"),
                messageBundle.getString("viewer.toolbar.saveAs.tooltip"),
                "save", iconSize,
                buttonFont);
        if (viewerController != null && btn != null)
            viewerController.setSaveAsFileButton(btn);
        return btn;
    }

    public JButton buildPrintButton() {
        JButton btn = makeToolbarButton(
                messageBundle.getString("viewer.toolbar.print.label"),
                messageBundle.getString("viewer.toolbar.print.tooltip"),
                "print", iconSize,
                buttonFont);
        if (viewerController != null && btn != null)
            viewerController.setPrintButton(btn);
        return btn;
    }

    public JButton buildSearchButton() {
        JButton btn = makeToolbarButton(
                messageBundle.getString("viewer.toolbar.search.label"),
                messageBundle.getString("viewer.toolbar.search.tooltip"),
                "search", iconSize, buttonFont);
        if (viewerController != null && btn != null)
            viewerController.setSearchButton(btn);
        return btn;
    }

    public JButton buildAnnotationPreviewButton(final String imageSize) {
        JButton btn = makeToolbarButton(
                messageBundle.getString("viewer.toolbar.tool.annotationPreview.label"),
                messageBundle.getString("viewer.toolbar.tool.annotationPreview.tooltip"),
                "annot_preview", imageSize, buttonFont);
        if (viewerController != null && btn != null)
            viewerController.setAnnotationSummaryButton(btn);
        return btn;
    }

    public JButton buildShowAnnotationUtilityButton(final String imageSize) {
        JButton btn = makeToolbarButton(
                messageBundle.getString("viewer.toolbar.tool.annotationUtility.label"),
                messageBundle.getString("viewer.toolbar.tool.annotationUtility.tooltip"),
                "utility_pane", imageSize, buttonFont);
        if (viewerController != null && btn != null)
            viewerController.setShowAnnotationUtilityPaneButton(btn);
        return btn;
    }

    public JToggleButton buildShowHideUtilityPaneButton() {
        JToggleButton btn = makeToolbarToggleButton(
                messageBundle.getString("viewer.toolbar.utilityPane.label"),
                messageBundle.getString("viewer.toolbar.utilityPane.tooltip"),
                "utility_pane", iconSize, buttonFont);
        if (viewerController != null && btn != null)
            viewerController.setShowHideUtilityPaneButton(btn);
        return btn;
    }

    public JToolBar buildPageNavigationToolBar() {
        JToolBar toolbar = new JToolBar();
        commonToolBarSetup(toolbar, false);
        addToToolBar(toolbar, buildFirstPageButton());
        addToToolBar(toolbar, buildPreviousPageButton());
        addToToolBar(toolbar, buildCurrentPageNumberTextField());
        addToToolBar(toolbar, buildNumberOfPagesLabel());
        addToToolBar(toolbar, buildNextPageButton());
        addToToolBar(toolbar, buildLastPageButton());
        return toolbar;
    }

    public JButton buildFirstPageButton() {
        JButton btn = makeToolbarButton(
                messageBundle.getString("viewer.toolbar.navigation.firstPage.label"),
                messageBundle.getString("viewer.toolbar.navigation.firstPage.tooltip"),
                "first", iconSize, buttonFont);
        if (viewerController != null && btn != null)
            viewerController.setFirstPageButton(btn);
        return btn;
    }

    public JButton buildPreviousPageButton() {
        JButton btn = makeToolbarButton(
                messageBundle.getString("viewer.toolbar.navigation.previousPage.label"),
                messageBundle.getString("viewer.toolbar.navigation.previousPage.tooltip"),
                "back", iconSize, buttonFont);
        if (viewerController != null && btn != null)
            viewerController.setPreviousPageButton(btn);
        return btn;
    }

    public JButton buildNextPageButton() {
        JButton btn = makeToolbarButton(
                messageBundle.getString("viewer.toolbar.navigation.nextPage.label"),
                messageBundle.getString("viewer.toolbar.navigation.nextPage.tooltip"),
                "forward", iconSize, buttonFont);
        if (viewerController != null && btn != null)
            viewerController.setNextPageButton(btn);
        return btn;
    }

    public JButton buildLastPageButton() {
        JButton btn = makeToolbarButton(
                messageBundle.getString("viewer.toolbar.navigation.lastPage.label"),
                messageBundle.getString("viewer.toolbar.navigation.lastPage.tooltip"),
                "last", iconSize, buttonFont);
        if (viewerController != null && btn != null)
            viewerController.setLastPageButton(btn);
        return btn;
    }

    public JTextField buildCurrentPageNumberTextField() {
        JTextField pageNumberTextField = new JTextField("", 3);
        pageNumberTextField.setToolTipText(messageBundle.getString("viewer.toolbar.navigation.current.tooltip"));
        pageNumberTextField.setInputVerifier(new PageNumberTextFieldInputVerifier());

        /*
         * Add a key listener and check to make sure the character entered
         * is a digit, period, the back_space or delete keys. If not the
         * invalid character is ignored and a system beep is triggered.
         */
        pageNumberTextField.addKeyListener(new PageNumberTextFieldKeyListener());
        if (viewerController != null)
            viewerController.setCurrentPageNumberTextField(pageNumberTextField);
        return pageNumberTextField;
    }

    public JLabel buildNumberOfPagesLabel() {
        JLabel lbl = new JLabel();
        lbl.setToolTipText(messageBundle.getString("viewer.toolbar.navigation.pages.tooltip"));
        if (viewerController != null)
            viewerController.setNumberOfPagesLabel(lbl);
        return lbl;
    }

    public JToolBar buildZoomToolBar() {
        JToolBar toolbar = new JToolBar();
        commonToolBarSetup(toolbar, false);
        addToToolBar(toolbar, buildZoomOutButton());
        addToToolBar(toolbar, buildZoomCombBox());
        addToToolBar(toolbar, buildZoomInButton());
        return toolbar;
    }

    public JButton buildZoomOutButton() {
        JButton btn = makeToolbarButton(
                messageBundle.getString("viewer.toolbar.zoom.out.label"),
                messageBundle.getString("viewer.toolbar.zoom.out.tooltip"),
                "zoom_out", iconSize, buttonFont);
        if (viewerController != null && btn != null)
            viewerController.setZoomOutButton(btn);
        return btn;
    }

    public JComboBox buildZoomCombBox() {
        // Get the properties manager in preparation for trying to get the zoom levels
        doubleCheckPropertiesManager();

        // Assign any different zoom ranges from the properties file if possible
        zoomLevels = ViewerPropertiesManager.getInstance().checkAndStoreFloatArrayProperty(
                ViewerPropertiesManager.PROPERTY_ZOOM_RANGES,
                zoomLevels);

        JComboBox<String> tmp = new JComboBox<>();
        tmp.setToolTipText(messageBundle.getString("viewer.toolbar.zoom.tooltip"));
        tmp.setPreferredSize(new Dimension(90, iconSize.equals(Images.SIZE_LARGE) ? 32 : 24));
        for (float zoomLevel : zoomLevels)
            tmp.addItem(NumberFormat.getPercentInstance().format(zoomLevel));
        tmp.setEditable(true);
        if (viewerController != null)
            viewerController.setZoomComboBox(tmp, zoomLevels);
        return tmp;
    }

    public JButton buildZoomInButton() {
        JButton btn = makeToolbarButton(
                messageBundle.getString("viewer.toolbar.zoom.in.label"),
                messageBundle.getString("viewer.toolbar.zoom.in.tooltip"),
                "zoom_in", iconSize, buttonFont);
        if (viewerController != null && btn != null)
            viewerController.setZoomInButton(btn);
        return btn;
    }

    public JComboBox buildAnnotationPermissionCombBox() {
        JComboBox<String> tmp = new JComboBox<>();
        tmp.setToolTipText(messageBundle.getString(
                "viewer.utilityPane.markupAnnotation.view.publicToggleButton.tooltip.label"));
        tmp.setPreferredSize(new Dimension(65, iconSize.equals(Images.SIZE_LARGE) ? 32 : 24));
        tmp.addItem(messageBundle.getString("viewer.utilityPane.markupAnnotation.view.publicToggleButton.label"));
        tmp.addItem(messageBundle.getString("viewer.utilityPane.markupAnnotation.view.privateToggleButton.label"));
        tmp.setEditable(true);
        if (viewerController != null)
            viewerController.setAnnotationPermissionComboBox(tmp);
        return tmp;
    }

    public JToolBar buildFitToolBar() {
        JToolBar toolbar = new JToolBar();
        commonToolBarSetup(toolbar, false);
        addToToolBar(toolbar, buildFitActualSizeButton());
        addToToolBar(toolbar, buildFitPageButton());
        addToToolBar(toolbar, buildFitWidthButton());
        return toolbar;
    }

    public JToolBar buildFullScreenToolBar() {
        JToolBar toolbar = new JToolBar();
        commonToolBarSetup(toolbar, false);
        addToToolBar(toolbar, buildFullScreenButton());
        return toolbar;
    }

    public JToggleButton buildFitActualSizeButton() {
        JToggleButton btn = makeToolbarToggleButton(
                messageBundle.getString("viewer.toolbar.pageFit.actualsize.label"),
                messageBundle.getString("viewer.toolbar.pageFit.actualsize.tooltip"),
                "actual_size", iconSize, buttonFont);
        if (viewerController != null && btn != null)
            viewerController.setFitActualSizeButton(btn);
        return btn;
    }

    public JToggleButton buildFitPageButton() {
        JToggleButton btn = makeToolbarToggleButton(
                messageBundle.getString("viewer.toolbar.pageFit.fitWindow.label"),
                messageBundle.getString("viewer.toolbar.pageFit.fitWindow.tooltip"),
                "fit_window", iconSize, buttonFont);
        if (viewerController != null && btn != null)
            viewerController.setFitHeightButton(btn);
        return btn;
    }

    public JToggleButton buildFitWidthButton() {
        JToggleButton btn = makeToolbarToggleButton(
                messageBundle.getString("viewer.toolbar.pageFit.fitWidth.label"),
                messageBundle.getString("viewer.toolbar.pageFit.fitWidth.tooltip"),
                "fit_width", iconSize, buttonFont);
        if (viewerController != null && btn != null)
            viewerController.setFitWidthButton(btn);
        return btn;
    }

    public JButton buildFullScreenButton() {
        JButton btn = makeToolbarButton(
                messageBundle.getString("viewer.toolbar.pageFit.fullscreen.label"),
                messageBundle.getString("viewer.toolbar.pageFit.fullscreen.tooltip"),
                "fullscreen", iconSize, buttonFont);
        if (viewerController != null && btn != null)
            viewerController.setFullScreenButton(btn);
        return btn;
    }

    public JToolBar buildRotateToolBar() {
        JToolBar toolbar = new JToolBar();
        commonToolBarSetup(toolbar, false);
        addToToolBar(toolbar, buildRotateRightButton());
        addToToolBar(toolbar, buildRotateLeftButton());
        return toolbar;
    }

    public JButton buildRotateLeftButton() {
        JButton btn = makeToolbarButton(
                messageBundle.getString("viewer.toolbar.rotation.left.label"),
                messageBundle.getString("viewer.toolbar.rotation.left.tooltip"),
                "rotate_left", iconSize, buttonFont);
        if (viewerController != null && btn != null)
            viewerController.setRotateLeftButton(btn);
        return btn;
    }

    public JButton buildRotateRightButton() {
        JButton btn = makeToolbarButton(
                messageBundle.getString("viewer.toolbar.rotation.right.label"),
                messageBundle.getString("viewer.toolbar.rotation.right.tooltip"),
                "rotate_right", iconSize, buttonFont);
        if (viewerController != null && btn != null)
            viewerController.setRotateRightButton(btn);
        return btn;
    }

    public JToolBar buildToolToolBar() {
        JToolBar toolbar = new JToolBar();
        commonToolBarSetup(toolbar, false);
        addToToolBar(toolbar, buildPanToolButton());
        addToToolBar(toolbar, buildTextSelectToolButton());
        addToToolBar(toolbar, buildZoomInToolButton());
        addToToolBar(toolbar, buildZoomOutToolButton());
        return toolbar;
    }

    public JToolBar buildAnnotationlToolBar() {
        JToolBar toolbar = new JToolBar();
        commonToolBarSetup(toolbar, false);
        if (propertiesManager.checkAndStoreBooleanProperty(
                ViewerPropertiesManager.PROPERTY_SHOW_TOOLBAR_ANNOTATION_SELECTION)) {
            addToToolBar(toolbar, buildSelectToolButton(iconSize));
        }
        if (propertiesManager.checkAndStoreBooleanProperty(
                ViewerPropertiesManager.PROPERTY_SHOW_TOOLBAR_ANNOTATION_HIGHLIGHT)) {
            addToToolBar(toolbar, buildHighlightAnnotationToolButton(iconSize));
        }
        if (propertiesManager.checkAndStoreBooleanProperty(
                ViewerPropertiesManager.PROPERTY_SHOW_TOOLBAR_ANNOTATION_UNDERLINE)) {
            addToToolBar(toolbar, buildUnderlineAnnotationToolButton(iconSize));
        }
        if (propertiesManager.checkAndStoreBooleanProperty(
                ViewerPropertiesManager.PROPERTY_SHOW_TOOLBAR_ANNOTATION_STRIKE_OUT)) {
            addToToolBar(toolbar, buildStrikeOutAnnotationToolButton(iconSize));
        }
        if (propertiesManager.checkAndStoreBooleanProperty(
                ViewerPropertiesManager.PROPERTY_SHOW_TOOLBAR_ANNOTATION_LINE)) {
            addToToolBar(toolbar, buildLineAnnotationToolButton(iconSize));
        }
        if (propertiesManager.checkAndStoreBooleanProperty(
                ViewerPropertiesManager.PROPERTY_SHOW_TOOLBAR_ANNOTATION_LINK)) {
            addToToolBar(toolbar, buildLinkAnnotationToolButton(iconSize));
        }
        if (propertiesManager.checkAndStoreBooleanProperty(
                ViewerPropertiesManager.PROPERTY_SHOW_TOOLBAR_ANNOTATION_ARROW)) {
            addToToolBar(toolbar, buildLineArrowAnnotationToolButton(iconSize));
        }
        if (propertiesManager.checkAndStoreBooleanProperty(
                ViewerPropertiesManager.PROPERTY_SHOW_TOOLBAR_ANNOTATION_RECTANGLE)) {
            addToToolBar(toolbar, buildSquareAnnotationToolButton(iconSize));
        }
        if (propertiesManager.checkAndStoreBooleanProperty(
                ViewerPropertiesManager.PROPERTY_SHOW_TOOLBAR_ANNOTATION_CIRCLE)) {
            addToToolBar(toolbar, buildCircleAnnotationToolButton(iconSize));
        }
        if (propertiesManager.checkAndStoreBooleanProperty(
                ViewerPropertiesManager.PROPERTY_SHOW_TOOLBAR_ANNOTATION_INK)) {
            addToToolBar(toolbar, buildInkAnnotationToolButton(iconSize));
        }
        if (propertiesManager.checkAndStoreBooleanProperty(
                ViewerPropertiesManager.PROPERTY_SHOW_TOOLBAR_ANNOTATION_FREE_TEXT)) {
            addToToolBar(toolbar, buildFreeTextAnnotationToolButton(iconSize));
        }
        if (propertiesManager.checkAndStoreBooleanProperty(
                ViewerPropertiesManager.PROPERTY_SHOW_TOOLBAR_ANNOTATION_TEXT)) {
            addToToolBar(toolbar, buildTextAnnotationToolButton(iconSize));
        }
        if (SystemProperties.PRIVATE_PROPERTY_ENABLED && propertiesManager.checkAndStoreBooleanProperty(
                ViewerPropertiesManager.PROPERTY_SHOW_TOOLBAR_ANNOTATION_PERMISSION)) {
            addToToolBar(toolbar, buildAnnotationPermissionCombBox());
        }
        if (propertiesManager.checkAndStoreBooleanProperty(
                ViewerPropertiesManager.PROPERTY_SHOW_TOOLBAR_ANNOTATION_UTILITY)) {
            addToToolBar(toolbar, buildShowAnnotationUtilityButton(iconSize));
        }
        if (propertiesManager.checkAndStoreBooleanProperty(
                ViewerPropertiesManager.PROPERTY_SHOW_TOOLBAR_ANNOTATION_PREVIEW)) {
            addToToolBar(toolbar, buildAnnotationPreviewButton(iconSize));
        }

        return toolbar;
    }

    public JToolBar buildFormsToolBar() {
        JToolBar toolbar = new JToolBar();
        commonToolBarSetup(toolbar, false);
        addToToolBar(toolbar, buildFormHighlightButton(iconSize));
        return toolbar;
    }

    public JToolBar buildQuickSearchToolBar() {
        JToolBar toolbar = new SearchToolBar(
                viewerController,
                messageBundle.getString("viewer.toolbar.tool.search.label"),
                makeToolbarButton(
                        messageBundle.getString("viewer.toolbar.tool.search.previous.label"),
                        messageBundle.getString("viewer.toolbar.tool.search.previous.tooltip"),
                        "back", iconSize, buttonFont),
                makeToolbarButton(
                        messageBundle.getString("viewer.toolbar.tool.search.next.label"),
                        messageBundle.getString("viewer.toolbar.tool.search.next.tooltip"),
                        "forward", iconSize, buttonFont));
        if (viewerController != null) {
            viewerController.setQuickSearchToolBar(toolbar);
        }
        return toolbar;
    }

    public JToolBar buildAnnotationPropertiesToolBar() {
        JToolBar toolbar = new JToolBar();
        commonToolBarSetup(toolbar, true);
        if (propertiesManager.checkAndStoreBooleanProperty(
                ViewerPropertiesManager.PROPERTY_ANNOTATION_PROPERTIES_HIGHLIGHT_ENABLED)) {
            addToToolBar(toolbar, buildHighlightAnnotationPropertiesToolButton(iconSize));
        }
        if (propertiesManager.checkAndStoreBooleanProperty(
                ViewerPropertiesManager.PROPERTY_ANNOTATION_PROPERTIES_STRIKE_OUT_ENABLED)) {
            addToToolBar(toolbar, buildStrikeOutAnnotationPropertiesToolButton(iconSize));
        }
        if (propertiesManager.checkAndStoreBooleanProperty(
                ViewerPropertiesManager.PROPERTY_ANNOTATION_PROPERTIES_UNDERLINE_ENABLED)) {
            addToToolBar(toolbar, buildUnderlineAnnotationPropertiesToolButton(iconSize));
        }
        if (propertiesManager.checkAndStoreBooleanProperty(
                ViewerPropertiesManager.PROPERTY_ANNOTATION_PROPERTIES_LINE_ENABLED)) {
            addToToolBar(toolbar, buildLineAnnotationPropertiesToolButton(iconSize));
        }
        if (propertiesManager.checkAndStoreBooleanProperty(
                ViewerPropertiesManager.PROPERTY_ANNOTATION_PROPERTIES_ARROW_ENABLED)) {
            addToToolBar(toolbar, buildLineArrowAnnotationPropertiesToolButton(iconSize));
        }
        if (propertiesManager.checkAndStoreBooleanProperty(
                ViewerPropertiesManager.PROPERTY_ANNOTATION_PROPERTIES_RECTANGLE_ENABLED)) {
            addToToolBar(toolbar, buildSquareAnnotationPropertiesToolButton(iconSize));
        }
        if (propertiesManager.checkAndStoreBooleanProperty(
                ViewerPropertiesManager.PROPERTY_ANNOTATION_PROPERTIES_CIRCLE_ENABLED)) {
            addToToolBar(toolbar, buildCircleAnnotationPropertiesToolButton(iconSize));
        }
        if (propertiesManager.checkAndStoreBooleanProperty(
                ViewerPropertiesManager.PROPERTY_ANNOTATION_PROPERTIES_INK_ENABLED)) {
            addToToolBar(toolbar, buildInkAnnotationPropertiesToolButton(iconSize));
        }
        if (propertiesManager.checkAndStoreBooleanProperty(
                ViewerPropertiesManager.PROPERTY_ANNOTATION_PROPERTIES_FREE_TEXT_ENABLED)) {
            addToToolBar(toolbar, buildFreeTextAnnotationPropertiesToolButton(iconSize));
        }
        if (propertiesManager.checkAndStoreBooleanProperty(
                ViewerPropertiesManager.PROPERTY_ANNOTATION_PROPERTIES_TEXT_ENABLED)) {
            addToToolBar(toolbar, buildTextAnnotationPropertiesToolButton(iconSize));
        }
        if (propertiesManager.checkAndStoreBooleanProperty(
                ViewerPropertiesManager.PROPERTY_ANNOTATION_PROPERTIES_LINK_ENABLED)) {
            addToToolBar(toolbar, buildLinkAnnotationPropertiesToolButton(iconSize));
        }
        if (propertiesManager.checkAndStoreBooleanProperty(
                ViewerPropertiesManager.PROPERTY_ANNOTATION_EDITING_MODE_ENABLED)) {
            toolbar.addSeparator();
            addToToolBar(toolbar, buildAnnotationEditingModeToolButton(iconSize));
        }
        return toolbar;
    }

    public JToggleButton buildPanToolButton() {
        JToggleButton btn = makeToolbarToggleButton(
                messageBundle.getString("viewer.toolbar.tool.pan.label"),
                messageBundle.getString("viewer.toolbar.tool.pan.tooltip"),
                "pan", iconSize, buttonFont);
        if (viewerController != null && btn != null)
            viewerController.setPanToolButton(btn);
        return btn;
    }

    public JToggleButton buildTextSelectToolButton() {
        JToggleButton btn = makeToolbarToggleButton(
                messageBundle.getString("viewer.toolbar.tool.text.label"),
                messageBundle.getString("viewer.toolbar.tool.text.tooltip"),
                "selection_text", iconSize, buttonFont);
        if (viewerController != null && btn != null)
            viewerController.setTextSelectToolButton(btn);
        return btn;
    }

    public JToggleButton buildSelectToolButton(final String imageSize) {
        JToggleButton btn = makeToolbarToggleButton(
                messageBundle.getString("viewer.toolbar.tool.select.label"),
                messageBundle.getString("viewer.toolbar.tool.select.tooltip"),
                "select", imageSize, buttonFont);
        if (viewerController != null && btn != null)
            viewerController.setSelectToolButton(btn);
        return btn;
    }

    public AbstractButton buildHighlightAnnotationToolButton(final String imageSize) {
        // put it all together for a dropdown button
        HighlightAnnotationToggleButton annotationColorButton = new HighlightAnnotationToggleButton(
                viewerController,
                messageBundle,
                messageBundle.getString("viewer.toolbar.tool.highlight.label"),
                messageBundle.getString("viewer.toolbar.tool.highlight.tooltip"),
                "highlight_annot_c", imageSize, buttonFont);
        if (viewerController != null) {
            viewerController.setHighlightAnnotationToolButton(annotationColorButton);
        }
        // put it all together for a dropdown button
        return annotationColorButton;
    }

    public JToggleButton buildStrikeOutAnnotationToolButton(final String imageSize) {
        JToggleButton btn = makeToolbarToggleButton(
                messageBundle.getString("viewer.toolbar.tool.strikeOut.label"),
                messageBundle.getString("viewer.toolbar.tool.strikeOut.tooltip"),
                "strikeout", imageSize, buttonFont);
        if (viewerController != null && btn != null)
            viewerController.setStrikeOutAnnotationToolButton(btn);
        return btn;
    }

    public JToggleButton buildUnderlineAnnotationToolButton(final String imageSize) {
        JToggleButton btn = makeToolbarToggleButton(
                messageBundle.getString("viewer.toolbar.tool.underline.label"),
                messageBundle.getString("viewer.toolbar.tool.underline.tooltip"),
                "underline", imageSize, buttonFont);
        if (viewerController != null && btn != null)
            viewerController.setUnderlineAnnotationToolButton(btn);
        return btn;
    }

    public JToggleButton buildLineAnnotationToolButton(final String imageSize) {
        JToggleButton btn = makeToolbarToggleButton(
                messageBundle.getString("viewer.toolbar.tool.line.label"),
                messageBundle.getString("viewer.toolbar.tool.line.tooltip"),
                "line", imageSize, buttonFont);
        if (viewerController != null && btn != null)
            viewerController.setLineAnnotationToolButton(btn);
        return btn;
    }

    public JToggleButton buildLinkAnnotationToolButton(final String imageSize) {
        JToggleButton btn = makeToolbarToggleButton(
                messageBundle.getString("viewer.toolbar.tool.link.label"),
                messageBundle.getString("viewer.toolbar.tool.link.tooltip"),
                "link_annot", imageSize, buttonFont);
        if (viewerController != null && btn != null)
            viewerController.setLinkAnnotationToolButton(btn);
        return btn;
    }

    public JToggleButton buildLineArrowAnnotationToolButton(final String imageSize) {
        JToggleButton btn = makeToolbarToggleButton(
                messageBundle.getString("viewer.toolbar.tool.lineArrow.label"),
                messageBundle.getString("viewer.toolbar.tool.lineArrow.tooltip"),
                "arrow", imageSize, buttonFont);
        if (viewerController != null && btn != null)
            viewerController.setLineArrowAnnotationToolButton(btn);
        return btn;
    }

    public JToggleButton buildSquareAnnotationToolButton(final String imageSize) {
        JToggleButton btn = makeToolbarToggleButton(
                messageBundle.getString("viewer.toolbar.tool.rectangle.label"),
                messageBundle.getString("viewer.toolbar.tool.rectangle.tooltip"),
                "square", imageSize, buttonFont);
        if (viewerController != null && btn != null)
            viewerController.setSquareAnnotationToolButton(btn);
        return btn;
    }

    public JToggleButton buildCircleAnnotationToolButton(final String imageSize) {
        JToggleButton btn = makeToolbarToggleButton(
                messageBundle.getString("viewer.toolbar.tool.circle.label"),
                messageBundle.getString("viewer.toolbar.tool.circle.tooltip"),
                "circle", imageSize, buttonFont);
        if (viewerController != null && btn != null)
            viewerController.setCircleAnnotationToolButton(btn);
        return btn;
    }

    public JToggleButton buildInkAnnotationToolButton(final String imageSize) {
        JToggleButton btn = makeToolbarToggleButton(
                messageBundle.getString("viewer.toolbar.tool.ink.label"),
                messageBundle.getString("viewer.toolbar.tool.ink.tooltip"),
                "ink", imageSize, buttonFont);
        if (viewerController != null && btn != null)
            viewerController.setInkAnnotationToolButton(btn);
        return btn;
    }

    public JToggleButton buildFreeTextAnnotationToolButton(final String imageSize) {
        JToggleButton btn = makeToolbarToggleButton(
                messageBundle.getString("viewer.toolbar.tool.freeText.label"),
                messageBundle.getString("viewer.toolbar.tool.freeText.tooltip"),
                "freetext_annot", imageSize, buttonFont);
        if (viewerController != null && btn != null)
            viewerController.setFreeTextAnnotationToolButton(btn);
        return btn;
    }

    public AbstractButton buildTextAnnotationToolButton(final String imageSize) {
        TextAnnotationToggleButton btn = new TextAnnotationToggleButton(
                viewerController,
                messageBundle,
                messageBundle.getString("viewer.toolbar.tool.textAnno.label"),
                messageBundle.getString("viewer.toolbar.tool.textAnno.tooltip"),
                "text_annot_c", imageSize, buttonFont);
        if (viewerController != null && btn != null)
            viewerController.setTextAnnotationToolButton(btn);
        return btn;
    }

    public JToggleButton buildFormHighlightButton(final String imageSize) {
        JToggleButton btn = makeToolbarToggleButton(
                messageBundle.getString("viewer.toolbar.tool.forms.highlight.label"),
                messageBundle.getString("viewer.toolbar.tool.forms.highlight.tooltip"),
                "form_highlight", imageSize, buttonFont);
        if (viewerController != null && btn != null)
            viewerController.setFormHighlightButton(btn);
        return btn;
    }


    public JToggleButton buildLinkAnnotationPropertiesToolButton(final String imageSize) {
        JToggleButton btn = makeToolbarToggleButtonSmall(
                messageBundle.getString("viewer.toolbar.tool.link.label"),
                messageBundle.getString("viewer.toolbar.tool.link.tooltip"),
                "link_annot", imageSize, buttonFont);
        if (viewerController != null && btn != null)
            viewerController.setLinkAnnotationPropertiesToolButton(btn);
        return btn;
    }

    public JToggleButton buildAnnotationEditingModeToolButton(final String imageSize) {
        JToggleButton btn = makeToolbarToggleButtonSmall(
                messageBundle.getString("viewer.toolbar.tool.annotationEditingMode.label"),
                messageBundle.getString("viewer.toolbar.tool.annotationEditingMode.tooltip"),
                "annot_tools", imageSize, buttonFont);
        if (viewerController != null && btn != null)
            viewerController.setAnnotationEditingModeToolButton(btn);
        return btn;
    }


    public JToggleButton buildHighlightAnnotationPropertiesToolButton(final String imageSize) {
        JToggleButton btn = makeToolbarToggleButtonSmall(
                messageBundle.getString("viewer.toolbar.tool.highlight.label"),
                messageBundle.getString("viewer.toolbar.tool.highlight.tooltip"),
                "highlight_annot", imageSize, buttonFont);
        if (viewerController != null && btn != null)
            viewerController.setHighlightAnnotationPropertiesToolButton(btn);
        return btn;
    }

    public JToggleButton buildStrikeOutAnnotationPropertiesToolButton(final String imageSize) {
        JToggleButton btn = makeToolbarToggleButtonSmall(
                messageBundle.getString("viewer.toolbar.tool.strikeOut.label"),
                messageBundle.getString("viewer.toolbar.tool.strikeOut.tooltip"),
                "strikeout", imageSize, buttonFont);
        if (viewerController != null && btn != null)
            viewerController.setStrikeOutAnnotationPropertiesToolButton(btn);
        return btn;
    }

    public JToggleButton buildUnderlineAnnotationPropertiesToolButton(final String imageSize) {
        JToggleButton btn = makeToolbarToggleButtonSmall(
                messageBundle.getString("viewer.toolbar.tool.underline.label"),
                messageBundle.getString("viewer.toolbar.tool.underline.tooltip"),
                "underline", imageSize, buttonFont);
        if (viewerController != null && btn != null)
            viewerController.setUnderlineAnnotationPropertiesToolButton(btn);
        return btn;
    }

    public JToggleButton buildLineAnnotationPropertiesToolButton(final String imageSize) {
        JToggleButton btn = makeToolbarToggleButtonSmall(
                messageBundle.getString("viewer.toolbar.tool.line.label"),
                messageBundle.getString("viewer.toolbar.tool.line.tooltip"),
                "line", imageSize, buttonFont);
        if (viewerController != null && btn != null)
            viewerController.setLineAnnotationPropertiesToolButton(btn);
        return btn;
    }

    public JToggleButton buildLineArrowAnnotationPropertiesToolButton(final String imageSize) {
        JToggleButton btn = makeToolbarToggleButtonSmall(
                messageBundle.getString("viewer.toolbar.tool.lineArrow.label"),
                messageBundle.getString("viewer.toolbar.tool.lineArrow.tooltip"),
                "arrow", imageSize, buttonFont);
        if (viewerController != null && btn != null)
            viewerController.setLineArrowAnnotationPropertiesToolButton(btn);
        return btn;
    }

    public JToggleButton buildSquareAnnotationPropertiesToolButton(final String imageSize) {
        JToggleButton btn = makeToolbarToggleButtonSmall(
                messageBundle.getString("viewer.toolbar.tool.rectangle.label"),
                messageBundle.getString("viewer.toolbar.tool.rectangle.tooltip"),
                "square", imageSize, buttonFont);
        if (viewerController != null && btn != null)
            viewerController.setSquareAnnotationPropertiesToolButton(btn);
        return btn;
    }

    public JToggleButton buildCircleAnnotationPropertiesToolButton(final String imageSize) {
        JToggleButton btn = makeToolbarToggleButtonSmall(
                messageBundle.getString("viewer.toolbar.tool.circle.label"),
                messageBundle.getString("viewer.toolbar.tool.circle.tooltip"),
                "circle", imageSize, buttonFont);
        if (viewerController != null && btn != null)
            viewerController.setCircleAnnotationPropertiesToolButton(btn);
        return btn;
    }

    public JToggleButton buildInkAnnotationPropertiesToolButton(final String imageSize) {
        JToggleButton btn = makeToolbarToggleButtonSmall(
                messageBundle.getString("viewer.toolbar.tool.ink.label"),
                messageBundle.getString("viewer.toolbar.tool.ink.tooltip"),
                "ink", imageSize, buttonFont);
        if (viewerController != null && btn != null)
            viewerController.setInkAnnotationPropertiesToolButton(btn);
        return btn;
    }

    public JToggleButton buildFreeTextAnnotationPropertiesToolButton(final String imageSize) {
        JToggleButton btn = makeToolbarToggleButtonSmall(
                messageBundle.getString("viewer.toolbar.tool.freeText.label"),
                messageBundle.getString("viewer.toolbar.tool.freeText.tooltip"),
                "freetext_annot", imageSize, buttonFont);
        if (viewerController != null && btn != null)
            viewerController.setFreeTextAnnotationPropertiesToolButton(btn);
        return btn;
    }

    public JToggleButton buildTextAnnotationPropertiesToolButton(final String imageSize) {
        JToggleButton btn = makeToolbarToggleButtonSmall(
                messageBundle.getString("viewer.toolbar.tool.textAnno.label"),
                messageBundle.getString("viewer.toolbar.tool.textAnno.tooltip"),
                "text_annot", imageSize, buttonFont);
        if (viewerController != null && btn != null)
            viewerController.setTextAnnotationPropertiesToolButton(btn);
        return btn;
    }

    public JToggleButton buildZoomInToolButton() {
        JToggleButton btn = makeToolbarToggleButton(
                messageBundle.getString("viewer.toolbar.tool.zoomMarquis.label"),
                messageBundle.getString("viewer.toolbar.tool.zoomMarquis.tooltip"),
                "zoom_marquis", iconSize, buttonFont);
        if (viewerController != null && btn != null)
            viewerController.setZoomInToolButton(btn);
        return btn;
    }

    public JToggleButton buildZoomOutToolButton() {
        JToggleButton btn = makeToolbarToggleButton(
                messageBundle.getString("viewer.toolbar.tool.zoomDynamic.label"),
                messageBundle.getString("viewer.toolbar.tool.zoomDynamic.tooltip"),
                "zoom_dynamic", iconSize, buttonFont);
        if (viewerController != null && btn != null)
            viewerController.setZoomDynamicToolButton(btn);
        return btn;
    }


    public JSplitPane buildUtilityAndDocumentSplitPane(boolean embeddableComponent) {
        JSplitPane splitpane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitpane.setOneTouchExpandable(false);
        splitpane.setDividerSize(8);
        splitpane.setContinuousLayout(true);
        // set the utility pane the left of the split pane
        splitpane.setLeftComponent(buildUtilityTabbedPane());

        // set the viewController embeddable flag.
        DocumentViewController viewController =
                viewerController.getDocumentViewController();
        // will add key event listeners
        viewerController.setIsEmbeddedComponent(embeddableComponent);

        // remove F6 focus management key from the splitpane
        splitpane.getActionMap().getParent().remove("toggleFocus");

        // add the viewControllers doc view container to the split pain
        splitpane.setRightComponent(viewController.getViewContainer());

        // apply previously set divider location, default is -1
        int dividerLocation = propertiesManager.getPreferences().getInt(
                ViewerPropertiesManager.PROPERTY_DIVIDER_LOCATION, 260);
        splitpane.setDividerLocation(dividerLocation);

        // Add the split pan component to the view controller so that it can
        // manipulate the divider via the controller, hide, show, etc. for
        // utility pane.
        if (viewerController != null)
            viewerController.setUtilityAndDocumentSplitPane(splitpane);
        return splitpane;
    }

    public JTabbedPane buildUtilityTabbedPane() {
        JTabbedPane utilityTabbedPane = new JTabbedPane();
        utilityTabbedPane.setPreferredSize(new Dimension(250, 400));

        // Get a properties manager that can be used to configure utility pane visibility
        doubleCheckPropertiesManager();

        // Build the main set of tabs based on the property file configuration
        if (propertiesManager.checkAndStoreBooleanProperty(
                ViewerPropertiesManager.PROPERTY_SHOW_UTILITYPANE_BOOKMARKS)) {
            utilityTabbedPane.add(
                    messageBundle.getString("viewer.utilityPane.bookmarks.tab.title"),
                    buildOutlineComponents());
        }
        if (propertiesManager.checkAndStoreBooleanProperty(
                ViewerPropertiesManager.PROPERTY_SHOW_UTILITYPANE_ATTACHMENTS)) {
            utilityTabbedPane.add(
                    messageBundle.getString("viewer.utilityPane.attachments.tab.title"),
                    buildAttachmentPanel());
        }
        if (propertiesManager.checkAndStoreBooleanProperty(
                ViewerPropertiesManager.PROPERTY_SHOW_UTILITYPANE_SEARCH)) {
            utilityTabbedPane.add(
                    messageBundle.getString("viewer.utilityPane.search.tab.title"),
                    buildSearchPanel());
        }
        if (propertiesManager.checkAndStoreBooleanProperty(
                ViewerPropertiesManager.PROPERTY_SHOW_UTILITYPANE_THUMBNAILS)) {
            utilityTabbedPane.add(
                    messageBundle.getString("viewer.utilityPane.thumbs.tab.title"),
                    buildThumbsPanel());
        }
        if (propertiesManager.checkAndStoreBooleanProperty(
                ViewerPropertiesManager.PROPERTY_SHOW_UTILITYPANE_LAYERS)) {
            utilityTabbedPane.add(
                    messageBundle.getString("viewer.utilityPane.layers.tab.title"),
                    buildLayersComponents());
        }
        if (propertiesManager.checkAndStoreBooleanProperty(
                ViewerPropertiesManager.PROPERTY_SHOW_UTILITYPANE_SIGNATURES)) {
            utilityTabbedPane.add(
                    messageBundle.getString("viewer.utilityPane.signatures.tab.title"),
                    buildSignatureComponents());
        }
        if (propertiesManager.checkAndStoreBooleanProperty(
                ViewerPropertiesManager.PROPERTY_SHOW_UTILITYPANE_ANNOTATION)) {
            utilityTabbedPane.add(
                    messageBundle.getString("viewer.utilityPane.annotation.tab.title"),
                    buildAnnotationPanel());
        }

        // Ensure something was added to the utility pane, otherwise reset it to null
        // By doing this we will stop the utility pane management buttons from displaying
        if (utilityTabbedPane.getComponentCount() == 0) {
            utilityTabbedPane = null;
        }

        if (viewerController != null)
            viewerController.setUtilityTabbedPane(utilityTabbedPane);

        return utilityTabbedPane;
    }

    public JComponent buildOutlineComponents() {
        JTree tree = new OutlinesTree();
        JScrollPane scroll = new JScrollPane(tree);
        if (viewerController != null)
            viewerController.setOutlineComponents(tree, scroll);
        return scroll;
    }

    public ThumbnailsPanel buildThumbsPanel() {
        ThumbnailsPanel thumbsPanel = new ThumbnailsPanel(viewerController,
                propertiesManager);
        if (viewerController != null) {
            viewerController.setThumbnailsPanel(thumbsPanel);
        }
        return thumbsPanel;
    }

    public LayersPanel buildLayersComponents() {
        LayersPanel layersPanel = new LayersPanel(viewerController);
        if (viewerController != null) {
            viewerController.setLayersPanel(layersPanel);
        }
        return layersPanel;
    }

    public JComponent buildSignatureComponents() {
        SignaturesHandlerPanel signaturesPanel = new SignaturesHandlerPanel(viewerController);
        if (viewerController != null) {
            viewerController.setSignaturesPanel(signaturesPanel);
        }
        return signaturesPanel;
    }

    public SearchPanel buildSearchPanel() {
        SearchPanel searchPanel = new SearchPanel(viewerController);
        if (viewerController != null)
            viewerController.setSearchPanel(searchPanel);
        return searchPanel;
    }

    public AttachmentPanel buildAttachmentPanel() {
        AttachmentPanel attachmentPanel = new AttachmentPanel(viewerController);
        if (viewerController != null)
            viewerController.setAttachmentPanel(attachmentPanel);
        return attachmentPanel;
    }

    public AnnotationPanel buildAnnotationPanel() {
        AnnotationPanel annotationPanel = new AnnotationPanel(viewerController);
        // build the comments panel
        if (propertiesManager.checkAndStoreBooleanProperty(
                ViewerPropertiesManager.PROPERTY_SHOW_UTILITYPANE_ANNOTATION_MARKUP)) {
            MarkupAnnotationPanel markupAnnotationPanel = buildMarkupAnnotationPanel();
            annotationPanel.addMarkupAnnotationPanel(markupAnnotationPanel,
                    messageBundle.getString("viewer.utilityPane.markupAnnotation.title"));
        }
        // build the destinations panel
        if (propertiesManager.checkAndStoreBooleanProperty(
                ViewerPropertiesManager.PROPERTY_SHOW_UTILITYPANE_ANNOTATION_DESTINATIONS)) {
            DestinationsPanel destinationPanel = buildDestinationsPanel();
            annotationPanel.addDestinationPanel(destinationPanel,
                    messageBundle.getString("viewer.utilityPane.destinations.title"));
        }

        if (viewerController != null)
            viewerController.setAnnotationPanel(annotationPanel);
        return annotationPanel;
    }

    public MarkupAnnotationPanel buildMarkupAnnotationPanel() {
        MarkupAnnotationPanel annotationPanel = new MarkupAnnotationPanel(viewerController);
        annotationPanel.setAnnotationUtilityToolbar(buildAnnotationPropertiesToolBar());
        return annotationPanel;
    }

    public DestinationsPanel buildDestinationsPanel() {
        DestinationsPanel destinationsPanel = new DestinationsPanel(viewerController, propertiesManager);
        return destinationsPanel;
    }

    /**
     * Builds the status bar panel containing a status label on the left and
     * view mode controls on the right.  The status bar can be shown or
     * hidden completely using the view property 'application.statusbar=true|false'
     * and the two child frame elements can be controlled using
     * 'application.statusbar.show.statuslabel=true|false' and
     * 'application.statusbar.show.viewmode=true|false'.  The default value
     * for all properties is 'true'.
     *
     * @return status panel JPanel if visible, null if the proeprty
     * 'application.statusbar=false' is set.
     */
    public JPanel buildStatusPanel() {
        // check to see if the status bars should be built.
        if (propertiesManager.checkAndStoreBooleanProperty(
                ViewerPropertiesManager.PROPERTY_SHOW_STATUSBAR)) {
            JPanel statusPanel = new JPanel(new BorderLayout());
            if (propertiesManager.checkAndStoreBooleanProperty(
                    ViewerPropertiesManager.PROPERTY_SHOW_STATUSBAR_STATUSLABEL)) {
                JPanel pgPanel = new JPanel();
                JLabel lbl = new JLabel(" ");
                lbl.setBorder(BorderFactory.createEmptyBorder(0, 5, 0, 0)); // So text isn't at the very edge
                pgPanel.add(lbl);
                statusPanel.add(pgPanel, BorderLayout.WEST);
                // set status label callback
                if (viewerController != null) {
                    viewerController.setStatusLabel(lbl);
                }
            }
            JPanel viewPanel = new JPanel();
            // Only add actual buttons to the view panel if requested by the properties file
            // Regardless we'll add the parent JPanel, to preserve the same layout behaviour
            if (propertiesManager.checkAndStoreBooleanProperty(
                    ViewerPropertiesManager.PROPERTY_SHOW_STATUSBAR_VIEWMODE)) {
                if (propertiesManager.checkAndStoreBooleanProperty(
                        ViewerPropertiesManager.PROPERTY_SHOW_STATUSBAR_VIEWMODE_SINGLE))
                    viewPanel.add(buildPageViewSinglePageNonConToggleButton());
                if (propertiesManager.checkAndStoreBooleanProperty(
                        ViewerPropertiesManager.PROPERTY_SHOW_STATUSBAR_VIEWMODE_SINGLE_CONTINUOUS))
                    viewPanel.add(buildPageViewSinglePageConToggleButton());
                if (propertiesManager.checkAndStoreBooleanProperty(
                        ViewerPropertiesManager.PROPERTY_SHOW_STATUSBAR_VIEWMODE_DOUBLE))
                    viewPanel.add(buildPageViewFacingPageNonConToggleButton());
                if (propertiesManager.checkAndStoreBooleanProperty(
                        ViewerPropertiesManager.PROPERTY_SHOW_STATUSBAR_VIEWMODE_DOUBLE_CONTINUOUS))
                    viewPanel.add(buildPageViewFacingPageConToggleButton());
            }
            statusPanel.add(viewPanel, BorderLayout.CENTER);
            viewPanel.setLayout(new ToolbarLayout(ToolbarLayout.RIGHT, 0, 1));

            JLabel lbl2 = new JLabel(" ");
            lbl2.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 5)); // So text isn't at the very edge
            statusPanel.add(lbl2, BorderLayout.EAST);

            return statusPanel;
        }
        return null;
    }

    public JToggleButton buildPageViewSinglePageConToggleButton() {
        JToggleButton btn = makeToolbarToggleButton(messageBundle.getString("viewer.toolbar.pageView.continuous.singlePage.label"),
                messageBundle.getString("viewer.toolbar.pageView.continuous.singlePage.tooltip"),
                "single_page_column", iconSize,
                buttonFont);
        if (viewerController != null && btn != null)
            viewerController.setPageViewSinglePageConButton(btn);
        return btn;
    }

    public JToggleButton buildPageViewFacingPageConToggleButton() {
        JToggleButton btn = makeToolbarToggleButton(messageBundle.getString("viewer.toolbar.pageView.continuous.facingPage.label"),
                messageBundle.getString("viewer.toolbar.pageView.continuous.facingPage.tooltip"),
                "two_page_column", iconSize,
                buttonFont);
        if (viewerController != null && btn != null)
            viewerController.setPageViewFacingPageConButton(btn);
        return btn;
    }

    public JToggleButton buildPageViewSinglePageNonConToggleButton() {
        JToggleButton btn = makeToolbarToggleButton(messageBundle.getString("viewer.toolbar.pageView.nonContinuous.singlePage.label"),
                messageBundle.getString("viewer.toolbar.pageView.nonContinuous.singlePage.tooltip"),
                "single_page", iconSize, buttonFont);
        if (viewerController != null && btn != null)
            viewerController.setPageViewSinglePageNonConButton(btn);
        return btn;
    }

    public JToggleButton buildPageViewFacingPageNonConToggleButton() {
        JToggleButton btn = makeToolbarToggleButton(messageBundle.getString("viewer.toolbar.pageView.nonContinuous.facingPage.label"),
                messageBundle.getString("viewer.toolbar.pageView.nonContinuous.facingPage.tooltip"),
                "two_page", iconSize, buttonFont);
        if (viewerController != null && btn != null)
            viewerController.setPageViewFacingPageNonConButton(btn);
        return btn;
    }


    /**
     * Utility method for creating a toolbar button.
     *
     * @param title     display text for the menu item
     * @param toolTip   tool tip text
     * @param imageName display image name
     * @param imageSize image size file extention constant
     * @param font      display font
     * @return a button with the specified characteristics.
     */
    protected JButton makeToolbarButton(
            String title, String toolTip, String imageName, final String imageSize,
            java.awt.Font font) {
        JButton tmp = new JButton(showButtonText ? title : "");
        tmp.setFont(font);
        tmp.setToolTipText(toolTip);
        setPreferredButtonSize(tmp, imageSize);
        try {
            tmp.setIcon(new ImageIcon(Images.get(imageName + "_a" + imageSize + ".png")));
            tmp.setPressedIcon(new ImageIcon(Images.get(imageName + "_i" + imageSize + ".png")));
            tmp.setRolloverIcon(new ImageIcon(Images.get(imageName + "_r" + imageSize + ".png")));
            tmp.setDisabledIcon(new ImageIcon(Images.get(imageName + "_i" + imageSize + ".png")));
        } catch (NullPointerException e) {
            logger.warning("Failed to load toolbar button images: " + imageName + "_i" + imageSize + ".png");
        }
        tmp.setRolloverEnabled(true);
        tmp.setBorderPainted(false);
        tmp.setContentAreaFilled(false);
        tmp.setFocusPainted(true);

        return tmp;
    }

    private void setPreferredButtonSize(Component comp, String imagesSize) {
        if (iconSize.equals(Images.SIZE_LARGE)) {
            comp.setPreferredSize(new Dimension(32, 32));
        } else if (iconSize.equals(Images.SIZE_SMALL)) {
            comp.setPreferredSize(new Dimension(24, 24));
        }
    }

    /**
     * Utility method for creating toggle buttons.
     *
     * @param title     display text for the menu item
     * @param toolTip   tool tip text
     * @param imageName display image name
     * @param font      display font
     * @param imageSize imageSize image size constant
     * @return a toggle button with the specified characteristics.
     */
    protected JToggleButton makeToolbarToggleButton(
            String title, String toolTip, String imageName,
            final String imageSize, java.awt.Font font) {
        JToggleButton tmp = new JToggleButton(showButtonText ? title : "");
        tmp.setFont(font);
        tmp.setToolTipText(toolTip);
        setPreferredButtonSize(tmp, imageSize);
        tmp.setRolloverEnabled(true);

        try {
            tmp.setIcon(new ImageIcon(Images.get(imageName + "_a" + imageSize + ".png")));
            tmp.setPressedIcon(new ImageIcon(Images.get(imageName + "_i" + imageSize + ".png")));
            tmp.setRolloverIcon(new ImageIcon(Images.get(imageName + "_r" + imageSize + ".png")));
            tmp.setDisabledIcon(new ImageIcon(Images.get(imageName + "_i" + imageSize + ".png")));
        } catch (NullPointerException e) {
            logger.warning("Failed to load toolbar toggle button images: " + imageName + "_i" + imageSize + ".png");
        }
        //tmp.setBorderPainted(false);
        tmp.setBorder(BorderFactory.createEmptyBorder());
        tmp.setContentAreaFilled(false);
        tmp.setFocusPainted(true);

        return tmp;
    }

    /**
     * Utility method for creating small toggle buttons (24x24) that also
     * have a selected icon state. .
     *
     * @param title     display text for the menu item
     * @param toolTip   tool tip text
     * @param imageName display image name
     * @param font      display font
     * @param imageSize imageSize image size constant
     * @return a toggle button with the specified characteristics.
     */
    protected JToggleButton makeToolbarToggleButtonSmall(
            String title, String toolTip, String imageName,
            final String imageSize, java.awt.Font font) {
        JToggleButton tmp = new JToggleButton(showButtonText ? title : "");
        tmp.setFont(font);
        tmp.setToolTipText(toolTip);
        setPreferredButtonSize(tmp, imageSize);
        try {
            tmp.setIcon(new ImageIcon(Images.get(imageName + "_a" + imageSize + ".png")));
            tmp.setPressedIcon(new ImageIcon(Images.get(imageName + "_i" + imageSize + ".png")));
//            tmp.setSelectedIcon(new ImageIcon(Images.get(imageName + "_s" + imageSize + ".png")));
            tmp.setRolloverIcon(new ImageIcon(Images.get(imageName + "_r" + imageSize + ".png")));
            tmp.setDisabledIcon(new ImageIcon(Images.get(imageName + "_i" + imageSize + ".png")));
        } catch (NullPointerException e) {
            logger.warning("Failed to load toolbar toggle images: " + imageName + "_i" + imageSize + ".png");
        }
        //tmp.setBorderPainted(false);
        tmp.setBorder(BorderFactory.createEmptyBorder());
        tmp.setContentAreaFilled(false);
        tmp.setRolloverEnabled(true);
        tmp.setFocusPainted(true);

        return tmp;
    }


    protected JToggleButton makeToolbarToggleButton(
            String title, String toolTip, java.awt.Font font) {
        JToggleButton tmp = new JToggleButton(showButtonText ? title : "");
        tmp.setFont(font);
        tmp.setToolTipText(toolTip);
        setPreferredButtonSize(tmp, iconSize);
        tmp.setText(title);
        tmp.setFocusPainted(true);
        return tmp;
    }


    protected JToggleButton makeToolbarToggleButton(
            String title, String toolTip, String imageName,
            int imageWidth, int imageHeight, java.awt.Font font) {
        JToggleButton tmp = new JToggleButton(showButtonText ? title : "");
        tmp.setFont(font);
        tmp.setToolTipText(toolTip);
        tmp.setRolloverEnabled(false);
        setPreferredButtonSize(tmp, iconSize);
        try {
            tmp.setIcon(new ImageIcon(Images.get(imageName + "_d.png")));
            tmp.setPressedIcon(new ImageIcon(Images.get(imageName + "_d.png")));
            tmp.setSelectedIcon(new ImageIcon(Images.get(imageName + "_n.png")));
            tmp.setDisabledIcon(new ImageIcon(Images.get(imageName + "_n.png")));
        } catch (NullPointerException e) {
            logger.warning("Failed to load toobar toggle button images: " + imageName + ".png");
        }
        tmp.setBorderPainted(false);
        tmp.setBorder(BorderFactory.createEmptyBorder());
        tmp.setContentAreaFilled(false);
        tmp.setFocusPainted(false);
        tmp.setPreferredSize(new Dimension(imageWidth, imageHeight));

        return tmp;
    }

    /**
     * Utility method for creating a menu item.
     *
     * @param text  display text for the menu item
     * @param accel accelerator key
     * @return menu item complete with text and action listener
     */
    protected static JMenuItem makeMenuItem(String text, KeyStroke accel) {
        JMenuItem jmi = new JMenuItem(text);
        if (accel != null)
            jmi.setAccelerator(accel);
        return jmi;
    }

    /**
     * Utility method for creating a menu item with an image.
     *
     * @param text      display text for the menu item
     * @param imageName display image for the menu item
     * @param imageSize size of the image.
     * @param accel     accelerator key
     * @return menu item complete with text, image and action listener
     */
    protected JMenuItem makeMenuItem(String text, String imageName,
                                     final String imageSize, KeyStroke accel) {
        JMenuItem jmi = new JMenuItem(text);
        if (imageName != null) {
            try {
                jmi.setIcon(new ImageIcon(Images.get(imageName + "_a" + imageSize + ".png")));
                jmi.setDisabledIcon(new ImageIcon(Images.get(imageName + "_i" + imageSize + ".png")));
                jmi.setRolloverIcon(new ImageIcon(Images.get(imageName + "_r" + imageSize + ".png")));
            } catch (NullPointerException e) {
                logger.warning("Failed to load menu images: " + imageName + "_a" + imageSize + ".png");
            }
        } else {
            jmi.setIcon(new ImageIcon(Images.get("menu_spacer.gif")));
            jmi.setDisabledIcon(new ImageIcon(Images.get("menu_spacer.gif")));
            jmi.setRolloverIcon(new ImageIcon(Images.get("menu_spacer.gif")));
        }
        jmi.setBorder(BorderFactory.createEmptyBorder());
        jmi.setContentAreaFilled(false);
        jmi.setFocusPainted(true);
        if (accel != null)
            jmi.setAccelerator(accel);

        return jmi;
    }

    protected void commonToolBarSetup(JToolBar toolbar, boolean isMainToolBar) {
        if (!isMainToolBar) {
            toolbar.requestFocus();
            toolbar.setRollover(true);
        }
        if (toolbarStyle == TOOL_BAR_STYLE_FIXED) {
            toolbar.setFloatable(false);
            if (!isMainToolBar) {
                if (haveMadeAToolBar)
                    toolbar.addSeparator();
                haveMadeAToolBar = true;
            }
        }
    }

    /**
     * Method to try to get the properties manager from the window management callback,
     * if we don't already have a propertiesManager object
     */
    protected static void doubleCheckPropertiesManager() {
        if (propertiesManager == null) {
            propertiesManager = ViewerPropertiesManager.getInstance();
        }
    }

    /**
     * Method to attempt to override the system properties with various values form the preferences class.
     *
     * @param propertiesManager current properties manager.
     */
    protected void overrideHighlightColor(ViewerPropertiesManager propertiesManager) {

        Preferences preferences = propertiesManager.getPreferences();

        // apply text selection and highlight colors from preferences.
        Page.highlightColor = new Color(preferences.getInt(
                ViewerPropertiesManager.PROPERTY_TEXT_HIGHLIGHT_COLOR, Page.highlightColor.getRGB()));
        Page.selectionColor = new Color(preferences.getInt(
                ViewerPropertiesManager.PROPERTY_TEXT_SELECTION_COLOR, Page.selectionColor.getRGB()));

        // page view settings.
        PageViewDecorator.pageShadowColor = new Color(preferences.getInt(
                ViewerPropertiesManager.PROPERTY_PAGE_VIEW_SHADOW_COLOR, PageViewDecorator.pageShadowColor.getRGB()));
        PageViewDecorator.pageColor = new Color(preferences.getInt(
                ViewerPropertiesManager.PROPERTY_PAGE_VIEW_PAPER_COLOR, PageViewDecorator.pageColor.getRGB()));
        PageViewDecorator.pageBorderColor = new Color(preferences.getInt(
                ViewerPropertiesManager.PROPERTY_PAGE_VIEW_BACKGROUND_COLOR, PageViewDecorator.pageBorderColor.getRGB()));
        AbstractDocumentView.backgroundColour = new Color(preferences.getInt(
                ViewerPropertiesManager.PROPERTY_PAGE_VIEW_BACKGROUND_COLOR, AbstractDocumentView.backgroundColour.getRGB()));

        // image reference type.
        ImageReferenceFactory.imageReferenceType = ImageReferenceFactory.getImageReferenceType(
                preferences.get(ViewerPropertiesManager.PROPERTY_IMAGING_REFERENCE_TYPE, "default"));

        // advanced reference types.
        Library.commonPoolThreads = preferences.getInt(ViewerPropertiesManager.PROPERTY_COMMON_THREAD_COUNT, Library.commonPoolThreads);
        Library.imagePoolThreads = preferences.getInt(ViewerPropertiesManager.PROPERTY_IMAGE_PROXY_THREAD_COUNT, Library.imagePoolThreads);
        ImageReference.useProxy = preferences.getBoolean(ViewerPropertiesManager.PROPERTY_IMAGE_PROXY_ENABLED, ImageReference.useProxy);

    }

    public static Font buildButtonFont() {
        return new java.awt.Font("Helvetica", java.awt.Font.PLAIN, 9);
    }

    protected void addToToolBar(JToolBar toolbar, JComponent comp) {
        if (comp != null)
            toolbar.add(comp);
    }

    protected void addToMenu(JMenu menu, JMenuItem mi) {
        if (mi != null)
            menu.add(mi);
    }

    protected void addToMenuBar(JMenuBar menuBar, JMenu menu) {
        if (menu != null)
            menuBar.add(menu);
    }
}
