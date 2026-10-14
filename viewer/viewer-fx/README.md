# ICEpdf JavaFX viewer (`viewer-fx`)

`PdfView` is an embeddable JavaFX `Control` for viewing, annotating and filling PDFs with the
ICEpdf core. Pages are rendered by the core's Java2D engine into tiles. Each tile is shared with
JavaFX with no copy (`PixelBuffer` over the same `int[]`). Everything interactive is a native JavaFX
node: text selection, search highlights, annotation chrome and popups, and form field editors.

Module: `org.icepdf.fx` (requires `org.icepdf.core` and `javafx.controls`; the rendering engine is not exported).
Packages:
- `org.icepdf.fx.view`: the page view;
- `org.icepdf.fx.ri`: the assembled viewer, `PdfViewer`;
- `org.icepdf.fx.ri.actions`: the commands;
- `org.icepdf.fx.ri.document`: open, save, signing;
- `org.icepdf.fx.fonts`: system fonts.

The runnable example is `examples/javafx`.

## Run the viewer

`./gradlew :viewer:viewer-fx:run` starts the viewer application; Gradle builds the core from source.
`runShell --args="/path/file.pdf"` opens one `PdfViewer` window; add `-Pxmx=4g` for a bigger heap.

Maven resolves `icepdf-core` from the local repository, so install the current core first. Repeat
this after core changes. The coverage gate is skipped because tests are skipped, and the full
reactor still trips on the stale `qa` module:

    mvn -f core/core-awt/pom.xml install -DskipTests -Djacoco.skip=true
    mvn -f viewer/viewer-fx/pom.xml javafx:run

### The jars

`icepdf-viewer-fx-<version>.jar` (Maven `com.github.pcorless.icepdf:icepdf-viewer-fx`) holds the page
view, the viewer component and the viewer application. It needs `icepdf-core` and JavaFX 22 or
later, which your application brings. The `osDistZip` bundle has it in `libs/` with everything else
it needs except JavaFX, its sources in `libs-source/`, and its pom in `maven2/`.

A single runnable jar with JavaFX for the build platform inside:

    ./gradlew :viewer:viewer-fx:jarWithDependencies      # build/libs/
    mvn -P assembly -pl viewer/viewer-fx -am package      # target/
    java -jar icepdf-viewer-fx-<version>-jar-with-dependencies.jar [file.pdf]

## Embed it

Three shapes, from the most given to the most control. Whichever you pick, load the system fonts
once at start, for documents that don't embed theirs:

```java
FontSettings.apply();   // org.icepdf.fx.fonts: cached in ~/.icepdf/fx, scanned in the background the first time
```

`apply(cacheFile, extraDirectories)` keeps the cache where your application keeps its files.

### 1. The whole viewer

`PdfViewer` is a JavaFX node with the page view, header bar, tool rail, side panels, dialogs, printing
and signing. `examples/javafx` is this, runnable:

```java
PdfViewer viewer = PdfViewer.create();              // every tool and panel
viewer.setOnLink(getHostServices()::showDocument);  // links in the document open in the browser
stage.setScene(new Scene(viewer, 1200, 900));
viewer.open(Path.of("file.pdf"));                   // read in the background; a password is asked for
// ... and when the window closes:
viewer.dispose();
```

`ViewerFeatures` decides what a product offers; `UserLayout` keeps how the user arranged it:

```java
ViewerFeatures features = ViewerFeatures.builder(ViewerFeatures.Preset.ANNOTATING)  // READING, FORMS, FULL
        .add("signature.sign")
        .remove("document.save-as", "annotation.ink")
        .panels(SidePanel.THUMBNAILS, SidePanel.COMMENTS)
        .build();
PdfViewer viewer = PdfViewer.create(features, UserLayout.load(settingsDir.resolve("layout.properties")));
```

The same choices can come from a properties file (`ViewerFeatures.fromProperties`), or from a Swing
viewer's settings (`fromSwingProperties`).

### 2. The viewer without its chrome, in your own layout

Leave out the header, rail and panel strip, and drive the viewer from your own tool bar. Your
controls stay yours. `bind` runs the action, greys the control out when the action can't run, and
shows a toggle's state (the current tool, the view mode):

```java
PdfViewer viewer = PdfViewer.create(
        ViewerFeatures.builder(ViewerFeatures.Preset.FULL).chrome().build(),  // or chrome(Chrome.HEADER, ...)
        UserLayout.inMemory());
ActionControls actions = viewer.getActions();
Button previous = actions.bind(new Button("Prev"), "navigation.previous");
Button next = actions.bind(new Button("Next"), "navigation.next");
ToggleButton highlight = actions.bind(new ToggleButton("Highlight"), "annotation.highlight");
MenuItem print = actions.bind(new MenuItem("Print…"), "document.print");

// the side panels are still there, to place where you like.
SplitPane split = new SplitPane(viewer.getCurrentDocument().getOutline(), viewer);
```

In a properties file this is `features.chrome=none` (or `header, tool-rail, side-panels`). Commands
that need a missing part are left out: Find without the header, the side panel commands without the
strip.

### 3. The bare page view

`PdfView` alone, with your own tool bar, labels and dialogs. `DocumentSession` owns the document:
it opens it in the background, asks for a password, saves safely and disposes the old one. The actions
work through a context. `ViewerContext.of(view)` offers the view's own commands at once, and a host
command such as Find, Open or Save once you hand it a handler:

```java
PdfView view = new PdfView();
DocumentSession session = new DocumentSession(view);
TextField find = new TextField();
ActionControls actions = new ActionControls(StandardActions.registry(),
        ViewerContext.of(view).onFind(find::requestFocus));

actions.bind(previousButton, "navigation.previous");
actions.bind(nextButton, "navigation.next");
actions.bind(zoomInButton, "view.zoom-in");
find.setOnAction(e -> view.search(find.getText()));
actions.bind(nextHitButton, "search.next");

// your own widgets show the view's state: every bit of it is a JavaFX property.
pageLabel.textProperty().bind(Bindings.createStringBinding(
        () -> (view.getCurrentPageIndex() + 1) + " of " + view.getPageCount(),
        view.currentPageIndexProperty(), view.pageCountProperty()));
zoomLabel.textProperty().bind(view.zoomProperty().multiply(100).asString("%.0f%%"));

// opt in to what PdfViewer would do for you:
AnnotationContextMenu.install(view, null);   // right-click an annotation: colour, Properties…, Delete
SignatureFlow.install(session);              // click or draw an empty signature field to sign it
actions.installAccelerators(scene, "search.find", "edit.copy", "edit.undo", "edit.redo");

session.open(Path.of("file.pdf"));
```

Nothing goes onto your scene unless you ask for it. With the focus in the page, the view handles its
own keys: Ctrl+A, Ctrl+C, Ctrl+Z/Y, Delete, arrows, Page Up/Down. `installAccelerators` makes the
shortcuts you pick work from the rest of your window too. Your text fields keep their own Ctrl+C.

The view can also show a `Document` you manage yourself:

```java
Document document = new Document();
// encrypted documents: asks for the user password (three tries); see PasswordPrompt
document.setSecurityCallback(new PasswordPrompt(stage, "file.pdf"));
document.setFile("file.pdf");

view.setDocument(document);                 // you dispose it after setDocument(null)
view.setViewMode(ViewMode.CONTINUOUS);      // SINGLE_PAGE, CONTINUOUS, FACING, FACING_CONTINUOUS
view.setFitMode(FitMode.WIDTH);
view.setToolMode(ToolMode.TEXT_SELECT);     // PAN, HIGHLIGHT, NOTE, FREE_TEXT, INK, ...

// actions with outside effects are the application's call (URI, launch, SubmitForm, JavaScript)
view.setOnAnnotationAction(e -> { /* e.getAction() */ });
// form values: read, set, reset, and track changes (undo/redo included)
view.setOnFormFieldChanged(e -> System.out.println(e.getName() + " = " + e.getNewValue()));
view.setFieldValue("name", "Ada");
view.resetForm();
```

**Other capabilities:**
- view: zoom, rotation, view and fit modes, current page (the page you went to, until the user
  scrolls; then the page showing the most of itself);
- text: search (`search`, `nextSearchHit`), selection and copy (`selectAll`, `copySelection`);
- annotations: create, move, resize and delete annotations, with `undo`/`redo`;
- forms: Tab order between fields (`focusNextField`, `highlightFormFields`);
- printing: `showPrintDialog()` (a JavaFX dialog with a page preview) or `print(PrintSettings)`
  with no dialog; pages print as vectors through Java2D (`org.icepdf.fx.print`);
- signatures: checked when a document opens (off the FX thread); `getSignatures()` lists each
  field's `SignatureStatus` (valid / unknown identity / invalid, the checks, the certificate chain),
  signed fields get a validity badge, and a click opens `SignaturePropertiesDialog` or your
  `onSignatureClicked` handler (`org.icepdf.fx.signature`);
- signing: `ToolMode.SIGNATURE` draws a field. `SignatureFlow` runs the rest: `SignDialog` collects
  the keystore (.p12/.pfx or PKCS#11), certificate, details and appearance, then the signed copy is
  written and opened. The parts work on their own too. `DocumentSigning.prepare` + `saveSigned` sign
  as the document is saved (incremental update), headless as well;
- encrypted documents: the view follows the document's permissions. `copyAllowed`,
  `annotationEditingAllowed` and `formFillingAllowed` say what the user may do; bind toolbar
  buttons to them. Bouncy Castle (needed for AES and public-key encryption) comes with core.

## Measure and test

| Command | What it does |
|---|---|
| `./gradlew :viewer:viewer-fx:test` | Unit tests: layout, tile grid and cache, selection, search, annotation edits, form controller and field order. No display needed. |
| `./gradlew :viewer:viewer-fx:smoke -PsmokeArgs="file.pdf;outdir" -PsmokeJvmArgs="-Dsmoke.only=MODE"` | Scripted view runs with snapshots. Modes: `tools`, `annotations`, `annotation-ui`, `forms`, `forms-corpus`, `pan-fps`, `first-paint`, `reopen` (`a.pdf\|b.pdf`), `encryption` (the encryption corpus directory), `print` (prints to PostScript files in the output directory, never to a printer), `signatures` (the signature corpus directory), `signing` (a PDF to sign; `-Dsmoke.keystore=` a PKCS#12 test keystore). Separate JVM args with `;` (for example `-PsmokeXmx=512m`, `-Dglass.gtk.uiScale=2`). |
| `./gradlew :viewer:viewer-fx:embedSmoke -PsmokeArgs="outdir;file.pdf[;term]"` | An application's own tool bar driving a bare `PdfView` (pages, zoom, find, labels bound to properties), then `PdfViewer` without chrome. |
| `./gradlew :viewer:viewer-fx:embedInteractionSmoke -PsmokeArgs="outdir;file.pdf[;keystore.pfx]"` | The same host with copy/paste keys, undo/redo, the annotation menu and properties dialog, and signing. The document must allow copying, annotating and filling in. |
| `./gradlew :viewer:viewer-fx:shellSmoke -PsmokeArgs="outdir;file.pdf"` | `PdfViewer`: rail, side panel, themes, tabs, full screen. |
| `./gradlew :viewer:viewer-fx:runDemo --args="file.pdf"` | `PdfViewDemo` (test sources): a bare `PdfView` with every property on a tool bar. |
| `./gradlew :viewer:viewer-fx:bench` | RasterBench: the raster hand-off paths. |

Render jobs log their region and paint time at `FINE` on `org.icepdf.fx.view.TileRenderer`.

## Documents

- `RENDERING_ANALYSIS.md`: the design decision and its measurements. That covers hand-off, memory,
  first paint against Swing and panning frame times, plus the core fixes they turned up.
- `PROVENANCE.md`: licences and sources of the dependencies and of any adapted code (Apache 2.0
  rules).
