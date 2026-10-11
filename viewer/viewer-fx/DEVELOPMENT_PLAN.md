# JavaFX Viewer Development Plan - ICEpdf

**Branch:** viewer-fx experiment  
**Goal:** Build a full-featured JavaFX PDF viewer working from UI inward to core rendering  
**Approach:** Stop short of the main page view (already started in PageViewWidget)

---

## Current State Analysis

### ✅ What Already Exists

**Core Architecture:**
- ✅ `Launcher.java` - JavaFX Application entry point
- ✅ `FxController.java` - MVC Controller (Model-View pattern)
- ✅ `ViewerModel.java` - Data model with JavaFX properties
- ✅ `ViewBuilder.java` - UI construction (Builder pattern)
- ✅ `Interactor.java` - Command coordination (Mediator pattern)
- ✅ `ViewerStageManager.java` - Window/Stage management
- ✅ `WindowManager.java` - Placeholder for window management

**View Components:**
- ✅ `DocumentViewPane.java` - Main document container with ScrollPane
- ✅ `PageViewWidget.java` - Individual page rendering (experimental, 313 lines)

**Commands:**
- ✅ `Command.java` - Command interface
- ✅ `OpenFileCommand.java` - File opening
- ✅ `ZoomInCommand.java` - Zoom in
- ✅ `ZoomOutCommand.java` - Zoom out

**Utilities:**
- ✅ `FontPropertiesManager.java` - Font configuration

**Listeners:**
- ✅ `DocumentChangeListener.java` - Document change handling
- ✅ `StageCloseRequestListener.java` - Window close handling

### 🔨 What Needs to Be Built

**From Swing Viewer (viewer-awt) - Key Features:**
1. Menu bar (File, Edit, View, Document, Window, Help)
2. Toolbar (comprehensive with icons)
3. Status bar (page info, zoom level, etc.)
4. Side panels:
   - Thumbnails panel
   - Outline/Bookmarks panel
   - Search panel
   - Layers panel
   - Annotations panel
   - Attachments panel
   - Signatures panel
5. Dialogs:
   - Document properties
   - Preferences/Settings
   - Search dialog
   - Print dialog
   - Annotation properties
6. Context menus
7. Keyboard shortcuts

---

## Development Plan - Phase by Phase

### Phase 1: Core UI Framework (Foundation)
**Goal:** Establish the main application structure and navigation  
**Duration:** 2-3 days

#### 1.1 Main Window Structure
- [ ] **MenuBarBuilder** - Create comprehensive menu bar
  - File menu (Open, Close, Save, Print, Exit)
  - Edit menu (Copy, Select All, Preferences)
  - View menu (Zoom, Rotation, Fit modes, Full screen)
  - Document menu (Info, Permissions, Fonts)
  - Window menu (Multiple windows, Cascade, Tile)
  - Help menu (About, Documentation)

- [ ] **ToolBarBuilder** - Create main toolbar
  - File operations (Open, Save, Print)
  - Navigation (First, Previous, Next, Last page)
  - Zoom controls (In, Out, Fit Width, Fit Page, Actual Size)
  - Rotation (90°, 180°, 270°)
  - View modes (Single, Continuous, Facing)
  - Search
  - Selection tools

- [ ] **StatusBarBuilder** - Create status bar
  - Current page / total pages
  - Zoom percentage display
  - Document title
  - File size
  - Loading progress indicator

- [ ] **MainWindowLayout** - BorderPane layout structure
  ```
  ┌─────────────────────────────────────┐
  │         Menu Bar                     │
  ├─────────────────────────────────────┤
  │         Tool Bar                     │
  ├──────┬────────────────────┬─────────┤
  │      │                    │         │
  │ Side │  Document View     │ Side    │
  │Panel │  (ScrollPane)      │ Panel   │
  │(Left)│                    │(Right)  │
  │      │                    │         │
  ├──────┴────────────────────┴─────────┤
  │         Status Bar                   │
  └─────────────────────────────────────┘
  ```

#### 1.2 Model Enhancement
- [ ] Extend `ViewerModel` with additional properties:
  - Current page number (IntegerProperty)
  - Total pages (IntegerProperty)
  - Zoom level (DoubleProperty)
  - Rotation angle (DoubleProperty)
  - View mode (ObjectProperty<ViewMode>)
  - Fit mode (ObjectProperty<FitMode>)
  - Document title (StringProperty)
  - Left panel visibility (BooleanProperty)
  - Right panel visibility (BooleanProperty)
  - Status message (StringProperty)
  - Loading progress (DoubleProperty)

#### 1.3 Command Pattern Expansion
- [ ] Create command package structure:
  ```
  commands/
  ├── document/
  │   ├── OpenDocumentCommand
  │   ├── CloseDocumentCommand
  │   ├── SaveDocumentCommand
  │   ├── PrintDocumentCommand
  │   └── DocumentPropertiesCommand
  ├── navigation/
  │   ├── FirstPageCommand
  │   ├── PreviousPageCommand
  │   ├── NextPageCommand
  │   ├── LastPageCommand
  │   └── GoToPageCommand
  ├── view/
  │   ├── ZoomInCommand (exists)
  │   ├── ZoomOutCommand (exists)
  │   ├── FitWidthCommand
  │   ├── FitPageCommand
  │   ├── ActualSizeCommand
  │   ├── RotateLeftCommand
  │   └── RotateRightCommand
  └── window/
      ├── NewWindowCommand
      ├── CloseWindowCommand
      └── MinimizeWindowCommand
  ```

---

### Phase 2: Side Panels (Left Panel - Primary)
**Goal:** Implement the main utility panels  
**Duration:** 3-4 days

#### 2.1 Side Panel Container
- [ ] **SidePanelContainer** - Tabbed or accordion container
  - Tab selection persistence
  - Collapsible behavior
  - Splitter with DocumentViewPane
  - Resize and hide functionality

#### 2.2 Thumbnails Panel
- [ ] **ThumbnailsPanel** - Page thumbnail grid
  - Lazy loading of thumbnails
  - Current page highlight
  - Click to navigate
  - Drag to reorder (future)
  - Context menu (delete, extract, etc.)
  - ThumbnailCell (custom ListCell or GridPane)
  - Thumbnail cache management

#### 2.3 Outline/Bookmarks Panel
- [ ] **OutlinePanel** - Document outline tree
  - TreeView with document bookmarks
  - Click to navigate to destination
  - Expand/collapse handling
  - Icons for different node types
  - Context menu
  - OutlineTreeCell (custom TreeCell)

#### 2.4 Search Panel
- [ ] **SearchPanel** - Document search interface
  - Search input field
  - Search options (case sensitive, whole word, regex)
  - Results list (ListView)
  - Highlight matches in document
  - Previous/Next match navigation
  - SearchResultCell (custom ListCell)
  - Background search task
  - Progress indicator

#### 2.5 Layers Panel
- [ ] **LayersPanel** - Optional content layers
  - TreeView or ListView for layers
  - Checkbox for visibility toggle
  - Layer groups support
  - Refresh on document change

#### 2.6 Attachments Panel
- [ ] **AttachmentsPanel** - Embedded files
  - TableView with attachments list
  - Columns: name, type, size, date
  - Save/Extract functionality
  - Open functionality
  - Context menu

#### 2.7 Annotations Panel
- [ ] **AnnotationsPanel** - Annotation summary
  - TableView or ListView with all annotations
  - Filter by type
  - Click to navigate to annotation
  - Edit/Delete functionality
  - Context menu
  - AnnotationCell (custom cell)

#### 2.8 Signatures Panel
- [ ] **SignaturesPanel** - Digital signatures
  - ListView of signatures
  - Validation status icons
  - Signature details dialog
  - Certificate chain viewer
  - Trust management

---

### Phase 3: Dialogs and Secondary Windows
**Goal:** Implement dialogs and popup windows  
**Duration:** 2-3 days

#### 3.1 Document Dialogs
- [ ] **DocumentPropertiesDialog** - Document metadata
  - General tab (title, author, subject, etc.)
  - Security tab (permissions, encryption)
  - Fonts tab (embedded fonts list)
  - TableView for fonts
  - Copy to clipboard functionality

- [ ] **PageInformationDialog** - Page-specific info
  - Page size, rotation
  - Media box, crop box, etc.
  - Content statistics

#### 3.2 Preferences Dialog
- [ ] **PreferencesDialog** - Application settings
  - TabPane with categories:
    - General preferences
    - Display preferences
    - Rendering preferences
    - Font preferences
    - Color preferences
    - Memory preferences
  - Apply/OK/Cancel buttons
  - Settings persistence via Properties

#### 3.3 Search Dialog
- [ ] **SearchDialog** - Advanced search
  - Floating or docked window
  - Search options panel
  - Results panel
  - Replace functionality (future)

#### 3.4 Print Dialog
- [ ] **PrintDialog** - Print configuration
  - Printer selection
  - Page range
  - Copies
  - Page scaling
  - Preview (optional)

#### 3.5 About Dialog
- [ ] **AboutDialog** - Application information
  - Version information
  - License information
  - Credits
  - System info

---

### Phase 4: Enhanced Toolbar and Menus
**Goal:** Rich, icon-based toolbar and complete menu system  
**Duration:** 2-3 days

#### 4.1 Icon Management
- [ ] **IconManager** - Icon loading and caching
  - Load icons from resources
  - Support for multiple icon sizes
  - Dark mode variants
  - SVG support (via external library or PNG fallback)

#### 4.2 Enhanced Toolbar
- [ ] **ComprehensiveToolBar** - Full-featured toolbar
  - Icon buttons with tooltips
  - Separators for grouping
  - Combo boxes (zoom levels, page selection)
  - Toggle buttons (selection mode, annotation mode)
  - Customizable toolbar layout
  - Show/hide specific tools

#### 4.3 Context Menus
- [ ] **DocumentContextMenu** - Right-click on document
  - Copy text
  - Select all
  - Zoom options
  - Rotation
  - Page operations

- [ ] **PageContextMenu** - Right-click on page
  - Extract page
  - Delete page
  - Rotate page
  - Page properties

---

### Phase 5: Advanced UI Components
**Goal:** Polish and advanced features  
**Duration:** 2-3 days

#### 5.1 Page Navigation
- [ ] **PageNavigationBar** - Quick page navigation
  - Page number TextField with validation
  - Previous/Next buttons
  - First/Last buttons
  - Go to page on Enter
  - Spinner control option

#### 5.2 Zoom Controls
- [ ] **ZoomControl** - Advanced zoom
  - ComboBox with preset zoom levels
  - TextField for custom zoom
  - Fit to width/height/page buttons
  - Zoom slider (optional)

#### 5.3 View Mode Switcher
- [ ] **ViewModeControl** - Layout selection
  - Single page
  - Continuous scrolling
  - Facing pages
  - Continuous facing
  - Toggle buttons with icons

#### 5.4 Recent Files Menu
- [ ] **RecentFilesMenu** - Recent documents
  - Dynamic menu items (0-10 files)
  - Click to open
  - Clear recent files option
  - File path tooltips

#### 5.5 Keyboard Shortcuts
- [ ] **KeyboardShortcutManager** - Global shortcuts
  - Ctrl+O: Open
  - Ctrl+W: Close
  - Ctrl+P: Print
  - Ctrl+F: Search
  - Ctrl++ / Ctrl+-: Zoom
  - Ctrl+0: Actual size
  - Page Up/Down: Navigate
  - Home/End: First/Last page
  - Configurable shortcuts

---

### Phase 6: Responsive UI and Themes
**Goal:** Modern, responsive UI with theme support  
**Duration:** 1-2 days

#### 6.1 CSS Styling
- [ ] **Style Sheets** - JavaFX CSS
  - Default light theme
  - Dark theme
  - High contrast theme
  - Custom color schemes
  - Consistent spacing and sizing

#### 6.2 Responsive Layout
- [ ] **ResponsiveLayout** - Adaptive UI
  - Window resize handling
  - Minimum window sizes
  - Panel collapse on small screens
  - Toolbar wrapping/overflow

#### 6.3 Theme Switcher
- [ ] **ThemeManager** - Theme management
  - Load CSS stylesheets
  - Switch themes dynamically
  - User preference persistence
  - System theme detection (optional)

---

### Phase 7: Progress and Feedback
**Goal:** User feedback during operations  
**Duration:** 1-2 days

#### 7.1 Progress Indicators
- [ ] **LoadingIndicator** - Document loading
  - ProgressBar in status bar
  - ProgressIndicator overlay
  - Cancelable operations
  - Estimated time remaining

#### 7.2 Notifications
- [ ] **NotificationSystem** - User notifications
  - Toast-style notifications
  - Error messages
  - Success confirmations
  - Warning dialogs

#### 7.3 Status Messages
- [ ] **StatusMessageManager** - Status bar messages
  - Temporary messages with timeout
  - Permanent status updates
  - Progress messages
  - Error/warning styling

---

## Detailed Implementation Steps

### Step 1: Project Setup Enhancement
**Files to create/modify:**

```
viewer/viewer-fx/
├── build.gradle (update dependencies)
├── src/main/java/org/icepdf/fx/ri/
│   ├── viewer/
│   │   ├── Launcher.java (exists - minimal changes)
│   │   ├── FxController.java (exists - enhance)
│   │   ├── ViewerModel.java (exists - extend)
│   │   ├── ViewBuilder.java (exists - major enhancement)
│   │   ├── Interactor.java (exists - expand)
│   │   └── WindowManager.java (implement)
│   ├── ui/
│   │   ├── menubar/
│   │   │   ├── MenuBarBuilder.java (NEW)
│   │   │   ├── FileMenu.java (NEW)
│   │   │   ├── EditMenu.java (NEW)
│   │   │   ├── ViewMenu.java (NEW)
│   │   │   ├── DocumentMenu.java (NEW)
│   │   │   ├── WindowMenu.java (NEW)
│   │   │   └── HelpMenu.java (NEW)
│   │   ├── toolbar/
│   │   │   ├── ToolBarBuilder.java (NEW)
│   │   │   ├── NavigationToolBar.java (NEW)
│   │   │   ├── ZoomToolBar.java (NEW)
│   │   │   └── ToolBarFactory.java (NEW)
│   │   ├── statusbar/
│   │   │   ├── StatusBarBuilder.java (NEW)
│   │   │   ├── PageInfoControl.java (NEW)
│   │   │   └── ZoomInfoControl.java (NEW)
│   │   ├── sidepanel/
│   │   │   ├── SidePanelContainer.java (NEW)
│   │   │   ├── ThumbnailsPanel.java (NEW)
│   │   │   ├── OutlinePanel.java (NEW)
│   │   │   ├── SearchPanel.java (NEW)
│   │   │   ├── LayersPanel.java (NEW)
│   │   │   ├── AttachmentsPanel.java (NEW)
│   │   │   ├── AnnotationsPanel.java (NEW)
│   │   │   └── SignaturesPanel.java (NEW)
│   │   └── dialogs/
│   │       ├── DocumentPropertiesDialog.java (NEW)
│   │       ├── PreferencesDialog.java (NEW)
│   │       ├── AboutDialog.java (NEW)
│   │       └── PrintDialog.java (NEW)
│   └── resources/
│       ├── icons/ (NEW - PNG/SVG icons)
│       ├── css/ (NEW - stylesheets)
│       │   ├── light-theme.css
│       │   ├── dark-theme.css
│       │   └── default.css
│       └── fxml/ (OPTIONAL - if using FXML)
```

---

### Step 2: Model Enhancement

**File:** `ViewerModel.java`

Add these properties:
```java
// Document properties
public final IntegerProperty currentPage = new SimpleIntegerProperty(0);
public final IntegerProperty totalPages = new SimpleIntegerProperty(0);
public final StringProperty documentTitle = new SimpleStringProperty("");
public final DoubleProperty documentSizeBytes = new SimpleDoubleProperty(0);

// View properties
public final DoubleProperty zoomLevel = new SimpleDoubleProperty(1.0);
public final DoubleProperty rotationAngle = new SimpleDoubleProperty(0.0);
public final ObjectProperty<ViewMode> viewMode = new SimpleObjectProperty<>(ViewMode.SINGLE_PAGE);
public final ObjectProperty<FitMode> fitMode = new SimpleObjectProperty<>(FitMode.NONE);

// UI state properties
public final BooleanProperty leftPanelVisible = new SimpleBooleanProperty(true);
public final BooleanProperty rightPanelVisible = new SimpleBooleanProperty(false);
public final BooleanProperty statusBarVisible = new SimpleBooleanProperty(true);
public final BooleanProperty toolBarVisible = new SimpleBooleanProperty(true);
public final IntegerProperty selectedSidePanelIndex = new SimpleIntegerProperty(0);

// Operation properties
public final StringProperty statusMessage = new SimpleStringProperty("");
public final DoubleProperty loadingProgress = new SimpleDoubleProperty(0.0);
public final BooleanProperty isLoading = new SimpleBooleanProperty(false);

// Selection properties
public final ObjectProperty<PageRange> selectedPages = new SimpleObjectProperty<>();
public final StringProperty selectedText = new SimpleStringProperty("");

// Enums
public enum ViewMode { SINGLE_PAGE, CONTINUOUS, FACING_PAGES, CONTINUOUS_FACING }
public enum FitMode { NONE, FIT_WIDTH, FIT_HEIGHT, FIT_PAGE, ACTUAL_SIZE }
```

---

### Step 3: ViewBuilder Enhancement

**File:** `ViewBuilder.java`

Transform into comprehensive UI builder:

```java
public class ViewBuilder implements Builder<Region> {
    
    private final ViewerModel model;
    private final MenuBarBuilder menuBarBuilder;
    private final ToolBarBuilder toolBarBuilder;
    private final StatusBarBuilder statusBarBuilder;
    private final SidePanelContainer leftPanelContainer;
    private final DocumentViewPane documentViewPane;
    
    @Override
    public Region build() {
        BorderPane root = new BorderPane();
        
        // Top: Menu + Toolbar
        VBox topContainer = new VBox();
        topContainer.getChildren().addAll(
            menuBarBuilder.build(),
            toolBarBuilder.build()
        );
        root.setTop(topContainer);
        
        // Center: Split pane with side panel + document view
        SplitPane centerPane = new SplitPane();
        centerPane.getItems().addAll(
            leftPanelContainer.build(),
            documentViewPane
        );
        centerPane.setDividerPositions(0.2);
        root.setCenter(centerPane);
        
        // Bottom: Status bar
        root.setBottom(statusBarBuilder.build());
        
        // Bind visibility properties
        topContainer.visibleProperty().bind(model.toolBarVisible);
        leftPanelContainer.visibleProperty().bind(model.leftPanelVisible);
        statusBarBuilder.visibleProperty().bind(model.statusBarVisible);
        
        return root;
    }
}
```

---

### Step 4: Component Specifications

#### 4.1 MenuBarBuilder

**Responsibilities:**
- Create menu bar with all menus
- Bind menu items to commands
- Enable/disable based on state
- Keyboard accelerators

**Structure:**
```java
public class MenuBarBuilder implements Builder<MenuBar> {
    private final ViewerModel model;
    private final Interactor interactor;
    
    @Override
    public MenuBar build() {
        MenuBar menuBar = new MenuBar();
        menuBar.getMenus().addAll(
            createFileMenu(),
            createEditMenu(),
            createViewMenu(),
            createDocumentMenu(),
            createWindowMenu(),
            createHelpMenu()
        );
        return menuBar;
    }
    
    private Menu createFileMenu() {
        MenuItem open = new MenuItem("Open...");
        open.setAccelerator(KeyCombination.keyCombination("Ctrl+O"));
        open.setOnAction(e -> interactor.executeCommand(new OpenDocumentCommand()));
        
        MenuItem close = new MenuItem("Close");
        close.setAccelerator(KeyCombination.keyCombination("Ctrl+W"));
        close.disableProperty().bind(model.document.isNull());
        
        MenuItem print = new MenuItem("Print...");
        print.setAccelerator(KeyCombination.keyCombination("Ctrl+P"));
        print.disableProperty().bind(model.document.isNull());
        
        Menu recentFiles = createRecentFilesMenu();
        
        MenuItem exit = new MenuItem("Exit");
        exit.setOnAction(e -> Platform.exit());
        
        return new Menu("File", null, 
            open, close, new SeparatorMenuItem(),
            print, new SeparatorMenuItem(),
            recentFiles, new SeparatorMenuItem(),
            exit
        );
    }
}
```

#### 4.2 ToolBarBuilder

**Responsibilities:**
- Create icon-based toolbar
- Group tools logically
- Tooltips for all buttons
- State binding

**Structure:**
```java
public class ToolBarBuilder implements Builder<ToolBar> {
    private final ViewerModel model;
    private final Interactor interactor;
    private final IconManager iconManager;
    
    @Override
    public ToolBar build() {
        ToolBar toolBar = new ToolBar();
        toolBar.getItems().addAll(
            createFileTools(),
            new Separator(),
            createNavigationTools(),
            new Separator(),
            createZoomTools(),
            new Separator(),
            createRotationTools(),
            new Separator(),
            createViewModeTools(),
            new Separator(),
            createSearchTools()
        );
        return toolBar;
    }
    
    private List<Node> createNavigationTools() {
        Button first = createButton("first-page", "First Page", 
            e -> new FirstPageCommand(model).execute());
        Button prev = createButton("previous-page", "Previous Page",
            e -> new PreviousPageCommand(model).execute());
        
        TextField pageField = new TextField();
        pageField.setPrefColumnCount(4);
        pageField.textProperty().bind(model.currentPage.asString());
        
        Label totalLabel = new Label();
        totalLabel.textProperty().bind(
            Bindings.concat(" / ", model.totalPages.asString())
        );
        
        Button next = createButton("next-page", "Next Page",
            e -> new NextPageCommand(model).execute());
        Button last = createButton("last-page", "Last Page",
            e -> new LastPageCommand(model).execute());
        
        // Disable when no document
        first.disableProperty().bind(model.document.isNull());
        prev.disableProperty().bind(model.document.isNull()
            .or(model.currentPage.isEqualTo(1)));
        next.disableProperty().bind(model.document.isNull()
            .or(model.currentPage.isEqualTo(model.totalPages)));
        last.disableProperty().bind(model.document.isNull());
        
        return Arrays.asList(first, prev, pageField, totalLabel, next, last);
    }
}
```

#### 4.3 StatusBarBuilder

**Responsibilities:**
- Display document status
- Show page information
- Display zoom level
- Progress indicator

**Structure:**
```java
public class StatusBarBuilder implements Builder<HBox> {
    private final ViewerModel model;
    
    @Override
    public HBox build() {
        HBox statusBar = new HBox(10);
        statusBar.setPadding(new Insets(5));
        statusBar.setStyle("-fx-background-color: -fx-background;");
        
        // Status message
        Label statusLabel = new Label();
        statusLabel.textProperty().bind(model.statusMessage);
        HBox.setHgrow(statusLabel, Priority.ALWAYS);
        
        // Page info
        Label pageInfo = new Label();
        pageInfo.textProperty().bind(Bindings.concat(
            "Page ", model.currentPage,
            " of ", model.totalPages
        ));
        
        // Zoom info
        Label zoomInfo = new Label();
        zoomInfo.textProperty().bind(Bindings.concat(
            model.zoomLevel.multiply(100).asString("%.0f"), "%"
        ));
        
        // Progress indicator
        ProgressBar progressBar = new ProgressBar();
        progressBar.progressProperty().bind(model.loadingProgress);
        progressBar.visibleProperty().bind(model.isLoading);
        
        statusBar.getChildren().addAll(
            statusLabel,
            new Separator(Orientation.VERTICAL),
            pageInfo,
            new Separator(Orientation.VERTICAL),
            zoomInfo,
            progressBar
        );
        
        return statusBar;
    }
}
```

#### 4.4 SidePanelContainer

**Responsibilities:**
- Host all side panels in tabs
- Tab selection and persistence
- Collapsible/expandable
- Splitter management

**Structure:**
```java
public class SidePanelContainer implements Builder<Region> {
    private final ViewerModel model;
    private TabPane tabPane;
    
    @Override
    public Region build() {
        tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        
        tabPane.getTabs().addAll(
            createTab("Thumbnails", new ThumbnailsPanel(model)),
            createTab("Outline", new OutlinePanel(model)),
            createTab("Search", new SearchPanel(model)),
            createTab("Layers", new LayersPanel(model)),
            createTab("Attachments", new AttachmentsPanel(model)),
            createTab("Annotations", new AnnotationsPanel(model)),
            createTab("Signatures", new SignaturesPanel(model))
        );
        
        // Bind selected tab
        tabPane.getSelectionModel().selectedIndexProperty()
            .bindBidirectional(model.selectedSidePanelIndex);
        
        return tabPane;
    }
    
    private Tab createTab(String title, Region content) {
        Tab tab = new Tab(title);
        tab.setContent(content);
        return tab;
    }
}
```

---

## Architecture Patterns to Follow

### 1. **MVC (Model-View-Controller)**
- Model: `ViewerModel` (already exists)
- View: Built by `ViewBuilder` and components
- Controller: `FxController` coordinates

### 2. **Command Pattern**
- All user actions as commands
- Undo/redo support (future)
- Macro recording (future)

### 3. **Builder Pattern**
- UI construction via builders
- Fluent interfaces
- Testable components

### 4. **Observer Pattern**
- JavaFX Properties for reactive updates
- Listeners for document changes
- Event-driven updates

### 5. **Dependency Injection**
- Pass `ViewerModel` to all components
- Pass `Interactor` for command execution
- Minimal coupling

---

## Key Design Decisions

### 1. **Pure JavaFX vs FXML**
**Recommendation:** Pure JavaFX (no FXML)

**Reasons:**
- More flexible and type-safe
- Easier refactoring
- Better IDE support
- Consistent with existing code
- No FXML loader overhead

### 2. **Icon Strategy**
**Recommendation:** PNG icons with IconManager cache

**Reasons:**
- Simple to implement
- Good browser support
- Easy to bundle
- Can add SVG later if needed

### 3. **Styling Approach**
**Recommendation:** External CSS files + programmatic styling

**Reasons:**
- Separation of concerns
- Easy theme switching
- Consistent look and feel
- Override-able by users

### 4. **Threading Model**
**Recommendation:** Background tasks for all I/O and heavy operations

**Approach:**
- Use `Task<V>` for async operations
- Update UI on JavaFX Application Thread
- Progress reporting via Task
- Cancelable operations

---

## Implementation Priority

### High Priority (Core Functionality)
1. ✅ MenuBarBuilder with File/View/Help menus
2. ✅ ToolBarBuilder with basic navigation/zoom
3. ✅ StatusBarBuilder with page/zoom info
4. ✅ ThumbnailsPanel (most used feature)
5. ✅ OutlinePanel (critical for navigation)
6. ✅ DocumentPropertiesDialog
7. ✅ PreferencesDialog

### Medium Priority (Enhanced UX)
8. SearchPanel with highlighting
9. Enhanced toolbar with icons
10. Keyboard shortcuts
11. Recent files menu
12. Context menus
13. Progress indicators

### Low Priority (Nice to Have)
14. AnnotationsPanel
15. AttachmentsPanel
16. SignaturesPanel
17. LayersPanel
18. Theme switcher
19. Print dialog customization

---

## Dependencies to Add

**File:** `viewer/viewer-fx/build.gradle`

```groovy
dependencies {
    implementation project(':core:core-awt')
    implementation project(':core:core-fonts')
    
    // BouncyCastle for signatures
    implementation "org.bouncycastle:bcprov-jdk18on:${BOUNCY_VERSION}"
    implementation "org.bouncycastle:bcpkix-jdk18on:${BOUNCY_VERSION}"
    
    // ControlsFX for enhanced JavaFX controls (optional but recommended)
    implementation 'org.controlsfx:controlsfx:11.2.1'
    
    // FontAwesomeFX for icons (optional)
    // implementation 'de.jensd:fontawesomefx-fontawesome:4.7.0-9.1.2'
    
    // Tests
    testImplementation(platform("org.junit:junit-bom:${JUNIT_BOM_VERSION}"))
    testImplementation('org.junit.jupiter:junit-jupiter')
    testRuntimeOnly('org.junit.platform:junit-platform-launcher')
    testImplementation('org.testfx:testfx-core:4.0.18')
    testImplementation('org.testfx:testfx-junit5:4.0.18')
}
```

---

## Development Timeline

| Phase | Component | Duration | Complexity |
|-------|-----------|----------|------------|
| 1 | Core UI Framework | 2-3 days | Medium |
| 2 | Side Panels | 3-4 days | Medium-High |
| 3 | Dialogs | 2-3 days | Low-Medium |
| 4 | Enhanced Toolbar/Menus | 2-3 days | Medium |
| 5 | Advanced UI | 2-3 days | Medium |
| 6 | Themes & Responsive | 1-2 days | Low |
| 7 | Progress & Feedback | 1-2 days | Low |
| **Total** | | **13-20 days** | |

---

## Testing Strategy

### Unit Tests
- Model property binding tests
- Command execution tests
- Builder pattern tests

### Integration Tests
- UI component interaction tests
- Document loading tests
- Navigation tests

### UI Tests (TestFX)
- User interaction simulation
- Click/keyboard tests
- Visual regression tests (optional)

---

## Migration from Swing Viewer

### Reference Implementation
Use `viewer-awt` as reference for:
- Feature completeness
- User workflows
- Edge case handling
- Configuration management

### Don't Port These (Not JavaFX-idiomatic)
- Swing-specific layouts (GridBagLayout, etc.)
- Swing event listeners
- Swing threading model
- AWT graphics operations

### Do Adapt These
- Feature set
- User workflows
- Keyboard shortcuts
- Configuration properties
- Command logic

---

## Quick Start Implementation Order

### Week 1: Foundation
1. Enhance `ViewerModel` with all properties
2. Create `MenuBarBuilder` with basic menus
3. Create `ToolBarBuilder` with basic tools
4. Create `StatusBarBuilder`
5. Update `ViewBuilder` to use new builders

### Week 2: Core Panels
6. Implement `SidePanelContainer` (TabPane)
7. Implement `ThumbnailsPanel` (basic)
8. Implement `OutlinePanel` (TreeView)
9. Implement `SearchPanel` (basic)

### Week 3: Polish & Dialogs
10. Implement `DocumentPropertiesDialog`
11. Implement `PreferencesDialog`
12. Add icon support (`IconManager`)
13. Add CSS themes
14. Implement recent files
15. Add keyboard shortcuts

### Week 4: Advanced Features
16. Enhanced thumbnails (lazy loading, caching)
17. Search highlighting
18. Remaining panels (Layers, Attachments, Annotations, Signatures)
19. Progress indicators
20. Polish and bug fixes

---

## Code Style Guidelines

### Naming Conventions
- Builders: `*Builder` suffix
- Panels: `*Panel` suffix
- Dialogs: `*Dialog` suffix
- Commands: `*Command` suffix
- Controls: `*Control` suffix

### Package Structure
```
org.icepdf.fx.ri.
├── viewer/           # Main application classes
├── ui/
│   ├── menubar/     # Menu components
│   ├── toolbar/     # Toolbar components
│   ├── statusbar/   # Status bar components
│   ├── sidepanel/   # Side panel components
│   ├── dialogs/     # Dialog windows
│   └── controls/    # Reusable controls
├── views/           # Document view components (exists)
├── commands/        # Command pattern implementations
├── util/            # Utilities
└── resources/       # Icons, CSS, FXML

```

### JavaFX Best Practices
- Use Properties for all mutable state
- Bind UI to model properties
- Use CSS for styling (not setStyle())
- Lazy instantiation where possible
- Keep UI updates on FX Application Thread
- Use Tasks for background work

---

## Next Steps - Getting Started

### Immediate Actions

1. **Create package structure:**
   ```bash
   mkdir -p viewer/viewer-fx/src/main/java/org/icepdf/fx/ri/ui/{menubar,toolbar,statusbar,sidepanel,dialogs,controls}
   mkdir -p viewer/viewer-fx/src/main/resources/{icons,css}
   ```

2. **Update ViewerModel.java** - Add all properties

3. **Create MenuBarBuilder.java** - Start with File menu

4. **Create ToolBarBuilder.java** - Basic navigation

5. **Create StatusBarBuilder.java** - Page/zoom info

6. **Update ViewBuilder.java** - Integrate new builders

7. **Test incrementally** - Build and test each component

---

## Success Criteria

### Phase 1 Complete When:
- ✅ Menu bar with all menus working
- ✅ Toolbar with basic navigation/zoom
- ✅ Status bar showing page/zoom info
- ✅ Main window layout complete
- ✅ File open/close works
- ✅ Zoom in/out works
- ✅ Page navigation works

### Full Application Complete When:
- ✅ All side panels implemented
- ✅ All dialogs working
- ✅ Keyboard shortcuts functional
- ✅ Recent files working
- ✅ Search working
- ✅ Themes switchable
- ✅ All Swing viewer features ported (except page rendering)

---

## Resources Needed

### Icons
- Download icon set (Material Design, Font Awesome, or similar)
- Sizes: 16x16, 24x24, 32x32
- Formats: PNG (or SVG with converter)

### CSS
- Base JavaFX stylesheets
- Theme definitions
- Color palettes

### Documentation
- JavaFX documentation
- ControlsFX documentation (if used)
- ICEpdf core API docs

---

## Risk Mitigation

### Technical Risks
- **JavaFX performance** - Use canvas caching, lazy loading
- **Memory usage** - Implement thumbnail cache with limits
- **Threading issues** - Strict Platform.runLater() usage

### Schedule Risks
- **Scope creep** - Stick to plan, defer nice-to-haves
- **Learning curve** - Reference JavaFX best practices
- **Integration issues** - Test early and often

---

## Summary

This plan provides a structured approach to building a full JavaFX PDF viewer by:

1. **Starting from the outside** (UI) and working inward
2. **Building incrementally** - Each phase deliverable
3. **Following established patterns** - Command, Builder, MVC
4. **Leveraging JavaFX strengths** - Properties, binding, CSS
5. **Stopping short of page rendering** - Use existing PageViewWidget

**Estimated Total Effort:** 13-20 days for a complete, polished JavaFX viewer application.

**Next Immediate Step:** Enhance `ViewerModel` with all required properties, then start building MenuBarBuilder.

---

**Created:** March 20, 2026  
**For:** ICEpdf viewer-fx Development  
**Status:** Ready to implement 🚀

