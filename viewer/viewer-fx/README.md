# ICEpdf JavaFX viewer (`viewer-fx`)

`PdfView` is an embeddable JavaFX `Control` for viewing, annotating and filling PDFs with the
ICEpdf core. Pages are rendered by the core's Java2D engine into tiles. Each tile is shared with
JavaFX with no copy (`PixelBuffer` over the same `int[]`). Everything interactive is a native JavaFX
node: text selection, search highlights, annotation chrome and popups, and form field editors.

Module: `org.icepdf.fx` (requires `org.icepdf.core` and `javafx.controls`; the rendering engine is not exported).
Packages, in two layers. The components, for an application that builds its own window:
- `org.icepdf.fx.view`: the page view, `PdfView`, and its events;
- `org.icepdf.fx.panels`: side panels for a `PdfView` (thumbnails, bookmarks, comments,
  attachments, layers, signatures, search, document properties);
- `org.icepdf.fx.print`, `org.icepdf.fx.signature`: printing, signature checks and signing, each
  with its dialog;
- `org.icepdf.fx.fonts`: system fonts.

Behind `PdfView`, not exported and not API: `org.icepdf.fx.view.internal.raster` (tiles, render
jobs, cache), `.annot` (annotation editing, popups), `.form` (form filling), `.text` (text
loading, selection, search) and `.tool` (input routing and the tool handlers, which reach the view only through
`ToolContext`).

The reference viewer built from them:
- `org.icepdf.fx.ri`: the assembled viewer, `PdfViewer`, and its configuration;
- `org.icepdf.fx.ri.actions`: the commands;
- `org.icepdf.fx.ri.document`: open, save, passwords, signing;
- `org.icepdf.fx.ri.prefs`: the stand-alone viewer's settings and preferences dialog;
- `org.icepdf.fx.ri.icons`: the replaceable icon set.

The runnable example is `examples/javafx`.

## Run the viewer

`./gradlew :viewer:viewer-fx:run` starts the viewer application; Gradle builds the core from source.
Add `--args="/path/file.pdf"` to open a file, and `-Pxmx=4g` for a bigger heap.

Ctrl+Shift+I (⌘⌥I on macOS) opens the render devtools window: the shared render pool's threads and
queue, each view's cache and jobs, and the heap, live. `-Dorg.icepdf.fx.devtools=true` (`-Pdevtools`
on `run`) opens it at start.

Maven resolves `icepdf-core` from the local repository, so install the current core first. Repeat
this after core changes. The coverage gate is skipped because tests are skipped, and the full
reactor still trips on the stale `qa` module:

    mvn -f core/core-awt/pom.xml install -DskipTests -Djacoco.skip=true
    mvn -f viewer/viewer-fx/pom.xml javafx:run

### The jars

`icepdf-viewer-fx-<version>.jar` (Maven `com.github.pcorless.icepdf:icepdf-viewer-fx`) holds the page
view, the viewer component and the viewer application. It needs `icepdf-core` and JavaFX 22 or
later (see [Java and JavaFX versions](#java-and-javafx-versions)). The `osDistZip` bundle has it in `libs/` with everything else
it needs except JavaFX, its sources in `libs-source/`, and its pom in `maven2/`.

### Java and JavaFX versions

`viewer-fx` is compiled for **Java 17** against **JavaFX 22**, the newest JavaFX that runs on Java 17.
It uses `Platform.Preferences` (JavaFX 22) to follow the system's light/dark setting, so it can't use
JavaFX 21. Core and the Swing viewer still run on Java 11.

The JavaFX 22 line no longer gets updates. Run on a newer JavaFX when your JDK allows:

| Your JDK | JavaFX to use |
|---|---|
| 17 to 22 | 22.0.2 (the default this module brings) |
| 23 or later | **25 LTS**, still updated (25.0.5 at the time of writing) |

Each JavaFX release needs a newer JDK: 23 needs Java 21, 24 needs 22, 25 needs 23, 26 needs 24 and
27 needs 25. JavaFX 23 and 24 are also out of updates, so on Java 21 or 22 you can stay on 22.0.2.
`viewer-fx` runs unchanged on a newer JavaFX (its 189 tests pass on JavaFX 25.0.5 with JDK 26). To
move up, declare JavaFX yourself. In Maven, your own declaration wins over the one `viewer-fx` brings:

```xml
<dependency>
    <groupId>org.openjfx</groupId>
    <artifactId>javafx-controls</artifactId>
    <version>25.0.5</version>
</dependency>
```

In Gradle, the newer version wins on its own: `implementation 'org.openjfx:javafx-controls:25.0.5'`, or
`javafx { version = '25.0.5' }` with the OpenJFX plugin. The jar with dependencies below bundles 22.0.2;
build it with a newer JavaFX the same way.

### Runnable jar

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
// encrypted documents: asks for the user password (three tries); see ri.document.PasswordPrompt
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
- printing: `PdfPrintDialog.show(view, null)` (a JavaFX dialog with a page preview) or
  `view.print(PrintSettings)` with no dialog; pages print as vectors through Java2D
  (`org.icepdf.fx.print`);
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

**Render threads.** Every `PdfView` and thumbnail strip in the process paints on one shared pool
of low-priority daemon threads, which exit when idle. The views in the focused window go first;
then visible tiles, annotation layers, page text, previews and thumbnails, in that order. The pool
is sized from the machine like core's own pools: half the cores, at least 2 and at most 8.
`-Dorg.icepdf.fx.render.threads=N` sets the count and `-Dorg.icepdf.fx.render.threadPoolCap=N`
the cap. Core decodes images on its own pools (`org.icepdf.core.library.threadPoolSize`,
`imageThreadPoolSize`, `threadPoolCap`).

## Measure and test

| Command | What it does |
|---|---|
| `./gradlew :viewer:viewer-fx:test` | Unit tests: layout, tile grid and cache, selection, search, annotation edits, form controller and field order. Also the UI tests (below), which are skipped without a display. |
| `./gradlew :viewer:viewer-fx:smoke -PsmokeArgs="file.pdf;outdir" -PsmokeJvmArgs="-Dsmoke.only=MODE"` | Scripted view runs with snapshots. Modes: `tools`, `annotations`, `annotation-ui`, `forms`, `forms-corpus`, `pan-fps`, `first-paint`, `reopen` (`a.pdf\|b.pdf`), `encryption` (the encryption corpus directory), `print` (prints to PostScript files in the output directory, never to a printer), `signatures` (the signature corpus directory), `signing` (a PDF to sign; `-Dsmoke.keystore=` a PKCS#12 test keystore). Separate JVM args with `;` (for example `-PsmokeXmx=512m`, `-Dglass.gtk.uiScale=2`). |
| `./gradlew :viewer:viewer-fx:embedSmoke -PsmokeArgs="outdir;file.pdf[;term]"` | An application's own tool bar driving a bare `PdfView` (pages, zoom, find, labels bound to properties), then `PdfViewer` without chrome. |
| `./gradlew :viewer:viewer-fx:embedInteractionSmoke -PsmokeArgs="outdir;file.pdf[;keystore.pfx]"` | The same host with copy/paste keys, undo/redo, the annotation menu and properties dialog, and signing. The document must allow copying, annotating and filling in. |
| `./gradlew :viewer:viewer-fx:shellSmoke -PsmokeArgs="outdir;file.pdf"` | `PdfViewer`: rail, side panel, themes, tabs, full screen. |
| `./gradlew :viewer:viewer-fx:runDemo --args="file.pdf"` | `PdfViewDemo` (test sources): a bare `PdfView` with every property on a tool bar. |
| `./gradlew :viewer:viewer-fx:bench` | RasterBench: the raster hand-off paths. |

Render jobs log their region and paint time at `FINE` on `org.icepdf.fx.view.internal.raster.TileRenderer`.

### UI tests

These are JUnit tests that drive the viewer as a user would, part of `test` above. They build
the viewer in a scene that is never shown and fire key events straight at its nodes. No Robot is
used, so nothing appears on screen, focus is never taken, and they work on Wayland, where Robot
input doesn't arrive. Every file chooser and question is answered by a script through
`PdfViewer.setDialogs`, so no modal dialog can block a run.

| Class | What it covers |
|---|---|
| `ri.ui.ErrorCardTest` | The error card: ×, Esc, Copy, a newer error replacing it, a message showing below it. |
| `ri.ViewerWindowTest` | Save As replace prompt and other-tab refusal, the unsaved-redactions prompt, redacted export, busy refusals (save, Ctrl+S, Ctrl+W, open, window close), Ctrl+S and Ctrl+W while typing in a form field, the Esc order (editor, field, error card), drops, attachments. |
| `ri.app.LinkFollowingTest` | GoToR, Launch, web links, `file:` and `smb:` links (asked first), relative links. |
| `signature.SignDialogKeystoreTest` | The sign dialog's keystore status: "Opening…", a wrong password, the right one. |

Run one class, or all four:

```
./gradlew :viewer:viewer-fx:test --tests '*ViewerWindowTest'
./gradlew :viewer:viewer-fx:test --tests '*ErrorCardTest' --tests '*ViewerWindowTest' --tests '*LinkFollowingTest' --tests '*SignDialogKeystoreTest'
```

The JavaFX toolkit needs a display to start (`DISPLAY` or `WAYLAND_DISPLAY`). Without one, as on
a headless CI machine, these tests are reported as skipped rather than failed. To run them there,
start a virtual display, for example `xvfb-run ./gradlew :viewer:viewer-fx:test`.

To write one, use the helpers in `org.icepdf.fx.testing` (test sources):

- `Fx`: `start()` in `@BeforeAll`; `call`/`run` to work on the FX thread; `waitFor` for background
  work (opening, saving, export); `scene`/`layout` in place of a pulse; `press`/`type` for keys.
- `ScriptedDialogs`: queue the files choosers pick (`choose`) and the questions' answers (`answer`).
  An unanswered question is cancelled. Each question asked is recorded in `asked`.
- `TinyPdf`: one-page PDFs written in the test, with links, embedded files or anything else in the
  catalog or page dictionary.

Build the viewer with `ViewerFeatures...multipleDocuments(true)` when the test needs tabs, since
`PdfViewer.create()` has none.

## Documents

- `RENDERING_ANALYSIS.md`: the design decision and its measurements. That covers hand-off, memory,
  first paint against Swing and panning frame times, plus the core fixes they turned up.
- `PROVENANCE.md`: licences and sources of the dependencies and of any adapted code (Apache 2.0
  rules).
