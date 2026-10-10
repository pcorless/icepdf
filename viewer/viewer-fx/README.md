# ICEpdf JavaFX viewer (`viewer-fx`)

`PdfView` is an embeddable JavaFX `Control` for viewing, annotating and filling PDFs with the
ICEpdf core. Pages are rendered by the core's Java2D engine into tiles. Each tile is shared with
JavaFX with no copy (`PixelBuffer` over the same `int[]`). Everything interactive is a native JavaFX
node: text selection, search highlights, annotation chrome and popups, and form field editors.

Package: `org.icepdf.fx.view`. Demo: `org.icepdf.fx.demo.PdfViewDemo`.

## Run the demo

Gradle builds the core from source:

    ./gradlew :viewer:viewer-fx:run

Maven resolves `icepdf-core` from the local repository, so install the current core first. Repeat
this after core changes. The coverage gate is skipped because tests are skipped, and the full
reactor still trips on the stale `qa` module:

    mvn -f core/core-awt/pom.xml install -DskipTests -Djacoco.skip=true
    mvn -f viewer/viewer-fx/pom.xml javafx:run

## Embed it

```java
Document document = new Document();
// encrypted documents: asks for the user password (three tries); see PasswordPrompt
document.setSecurityCallback(new PasswordPrompt(stage, "file.pdf"));
document.setFile("file.pdf");

PdfView view = new PdfView();
view.setDocument(document);
view.setViewMode(ViewMode.CONTINUOUS);      // SINGLE_PAGE, CONTINUOUS, FACING, FACING_CONTINUOUS
view.setFitMode(FitMode.WIDTH);
view.setToolMode(ToolMode.TEXT_SELECT);     // PAN, HIGHLIGHT, NOTE, FREE_TEXT, INK, ...

// actions with outside effects are the application's call (URI, launch, SubmitForm, JavaScript)
view.setOnAnnotationAction(e -> { /* e.action() */ });
// form values: read, set, reset, and track changes (undo/redo included)
view.setOnFormFieldChanged(e -> System.out.println(e.name() + " = " + e.newValue()));
view.setFieldValue("name", "Ada");
view.resetForm();

parent.getChildren().add(view);
```

**Other capabilities:**
- view: zoom, rotation, current page, view and fit modes;
- text: search (`search`, `nextSearchHit`), selection and copy (`selectAll`, `copySelection`);
- annotations: create, move, resize and delete annotations, with `undo`/`redo`;
- forms: Tab order between fields (`focusNextField`, `highlightFormFields`);
- printing: `showPrintDialog()` (a JavaFX dialog with a page preview) or `print(PrintSettings)`
  with no dialog; pages print as vectors through Java2D (`org.icepdf.fx.print`);
- signatures: checked when a document opens (off the FX thread); `getSignatures()` lists each
  field's `SignatureStatus` (valid / unknown identity / invalid, the checks, the certificate chain),
  signed fields get a validity badge, and a click opens `SignaturePropertiesDialog` or your
  `onSignatureClicked` handler (`org.icepdf.fx.signature`);
- signing: `ToolMode.SIGNATURE` draws a field, `SignDialog` collects the keystore (.p12/.pfx),
  certificate, details and appearance, `DocumentSigning.prepare` + `saveSigned` sign as the document
  is saved (incremental update); `DocumentSigning` works headless too;
- encrypted documents: the view follows the document's permissions. `copyAllowed`,
  `annotationEditingAllowed` and `formFillingAllowed` say what the user may do; bind toolbar
  buttons to them. Bouncy Castle (needed for AES and public-key encryption) comes with core.

## Measure and test

| Command | What it does |
|---|---|
| `./gradlew :viewer:viewer-fx:test` | Unit tests: layout, tile grid and cache, selection, search, annotation edits, form controller and field order. No display needed. |
| `./gradlew :viewer:viewer-fx:smoke -PsmokeArgs="file.pdf;outdir" -PsmokeJvmArgs="-Dsmoke.only=MODE"` | Scripted view runs with snapshots. Modes: `tools`, `annotations`, `annotation-ui`, `forms`, `forms-corpus`, `pan-fps`, `first-paint`, `reopen` (`a.pdf\|b.pdf`), `encryption` (the encryption corpus directory), `print` (prints to PostScript files in the output directory, never to a printer), `signatures` (the signature corpus directory), `signing` (a PDF to sign; `-Dsmoke.keystore=` a PKCS#12 test keystore). Separate JVM args with `;` (for example `-PsmokeXmx=512m`, `-Dglass.gtk.uiScale=2`). |
| `./gradlew :viewer:viewer-fx:bench` | RasterBench: the raster hand-off paths. |

Render jobs log their region and paint time at `FINE` on `org.icepdf.fx.view.TileRenderer`.

## Documents

- `RENDERING_ANALYSIS.md`: the design decision and its measurements. That covers hand-off, memory,
  first paint against Swing and panning frame times, plus the core fixes they turned up.
- `PROVENANCE.md`: licences and sources of the dependencies and of any adapted code (Apache 2.0
  rules).
