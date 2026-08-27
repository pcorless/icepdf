# ICEpdf Core – AWT Rendering Library

ICEpdf Core is the foundation of the ICEpdf project, providing a pure‑Java implementation
that parses and renders PDF documents. The module builds on top of Java 2D (AWT) and is
intended to be used either as a headless rendering engine or as the backend for
Swing/FX viewers.

## Features

- Full support for PDF 1.6+ (including PDF/A, PDF/X, interactive forms)
- Annotation rendering – free‑text, highlight, link, signature fields
- Render pages to `BufferedImage` and export to PNG/JPEG/TIFF/SVG
- Digital signatures via Bouncy Castle
- Embedded font support with Apache PDFBox FontBox
- Optional image format extensions (TIFF, JPEG2000, JBIG2) – included as optional Maven dependencies
- Multi‑threaded rendering; the library exposes a thread pool that can be configured

## Maven Coordinates

```xml
<dependency>
  <groupId>com.github.pcorless.icepdf</groupId>
  <artifactId>icepdf-core</artifactId>
  <version>7.5.0-SNAPSHOT</version>   <!-- or the latest release -->
</dependency>
```

## Gradle

```kotlin
implementation('com.github.pcorless.icepdf:icepdf-core:7.5.0-SNAPSHOT')
```

## Building the Library

The project uses both Maven and Gradle; either can build the core JAR.

### With Maven (build only this module)

```bash
mvn -pl :icepdf-core package
# or for a clean build:
mvn -pl :icepdf-core clean package
```

The resulting `icepdf-core.jar` is in  
`core/core-awt/target/`.

### With Gradle

```bash
gradle :core:core-awt:assemble
```

## Quick Usage Example

```java
import org.icepdf.core.pobjects.Document;
import org.icepdf.core.util.PDFBoxUtil;

public class RenderExample {
    public static void main(String[] args) throws Exception {
        // Load a PDF
        Document doc = new Document();
        doc.setFile("sample.pdf");

        // Render the first page to an image
        int pageIndex = 0;
        java.awt.image.BufferedImage image =
            PDFBoxUtil.renderPage(doc, pageIndex, 1.0f);

        // Save the image
        javax.imageio.ImageIO.write(image, "png", new java.io.File("page1.png"));
    }
}
```

Advanced examples – annotation handling, form filling, and signature verification – are in the `examples` directory.

## API Documentation

Generate Javadoc with:

```bash
mvn javadoc:javadoc   # Maven
# or
gradle :core:core-awt:javadoc  # Gradle
```

The docs live under  
`core/core-awt/target/site/apidocs`.

## Contributing

See [CONTRIBUTING.md](../../CONTRIBUTING.md). Contributions are welcome! Make sure to run the test suite (`mvn test`) before submitting a PR.

## License

ICEpdf Core is licensed under the Apache License 2.0 – see [LICENSE](../../LICENSE).

---

**Note:** The core module contains no UI components; use `icepdf-viewer` for Swing/FX viewers.