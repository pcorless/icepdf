# ICEpdf Swing Viewer

ICEpdf Swing Viewer is a lightweight Java Swing application that demonstrates how to use ICEpdf Core to render PDF documents in a desktop environment.

The viewer provides navigation, search, bookmarks and zoom controls and can be run as an executable JAR or launched via Gradle/Maven. It serves both as a showcase for the core engine and as a reference implementation for building your own Swing‑based PDF viewers.

---

## Features
- Open PDFs in a resizable window.
- Navigate pages (next/prev, first/last, page number input).
- Zoom in/out with percentage presets.
- Search text across the document (incremental results).
- View and edit annotations (free‑text, highlight, link, form fields).
- Print and export pages as PNG/JPEG/SVG/TIFF.
- Headless rendering support for automated tasks.

---

## Build & Run with Gradle
The viewer is built as a separate Gradle subproject under `viewer/viewer-awt`.

```bash
# Assemble the fat JAR that contains all dependencies:
./gradlew :viewer:viewer-awt:assemble
```

After assembly, the executable JAR can be found at:

```
viewer/viewer-awt/build/libs/icepdf-viewer-${VERSION}-jar-with-dependencies.jar
```

### Running the Viewer
```bash
java -jar viewer/viewer-awt/build/libs/icepdf-viewer-${VERSION}-jar-with-dependencies.jar [path/to/document.pdf]
```
If a file path is supplied, the viewer opens that document immediately; otherwise it starts with an empty window.

You can also run the application directly from Gradle:
```bash
./gradlew :viewer:viewer-awt:run --args="[path/to/document.pdf]"
```
(The `--args` flag passes command‑line arguments to the main class.)

---

## Build & Run with Maven
The viewer module is also available via Maven as part of the multi‑module build. To package it:
```bash
mvn -pl :icepdf-viewer -am package
```
Then run the JAR (replace `<version>` with the actual version from `pom.xml`):
```bash
java -jar viewer/viewer-awt/target/icepdf-viewer-${VERSION}-jar-with-dependencies.jar [path]
```
---

## Quick Usage Example (Java API)
Below is a minimal program that launches the SwingViewer directly using the ICEpdf APIs.

```java
import org.icepdf.viewer.swt.SwingController;
import org.icepdf.viewer.swt.swing.SwingViewBuilder;

public class LaunchDemo {
    public static void main(String[] args) throws Exception {
        // Create controller and view builder
        SwingController controller = new SwingController();
        SwingViewBuilder builder   = new SwingViewBuilder(controller);

        // Build the viewer frame
        javax.swing.JFrame frame = builder.buildFrame("ICEpdf Demo");
        frame.setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
        frame.pack();
        frame.setVisible(true);

        // Open a PDF if a path was supplied
        if (args.length > 0) {
            controller.openFile(args[0]);
        }
    }
}
```
The code above is functionally equivalent to running the bundled JAR, but it shows how you can embed ICEpdf Swing in your own Java applications.

---

## API Documentation
ICEpdf’s public API for the viewer consists mainly of `SwingController` and `SwingViewBuilder`. Javadoc can be generated from the module sources:
```bash
./gradlew :viewer:viewer-awt:javadoc
```
Docs are available under:
```
viewer/viewer-awt/build/docs/javadoc
```
---

## Contributing
The viewer is an example application. Contributions that add useful features, improve UX, or fix bugs are welcome.
See the repository root `CONTRIBUTING.md` for guidelines.

---

## License
ICEpdf Swing Viewer is licensed under the Apache License 2.0 – see [LICENSE](../../LICENSE).