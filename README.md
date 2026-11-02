<p align="center">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="docs/assets/brand/icepdf-banner-dark.svg">
    <img alt="icepdf" src="docs/assets/brand/icepdf-banner-light.svg" width="440">
  </picture>
</p>

icepdf is a fast, pure-Java PDF library. It parses and renders PDF documents with nothing but Java, at speeds
that keep pace with native renderers. Embed it in a server to rasterize pages, or ship one of the two ready-made
viewers, JavaFX or Swing, with annotations, forms, search, redaction and digital signatures built in.

[Website](https://pcorless.github.io/icepdf/) · [Wiki](https://github.com/pcorless/icepdf/wiki) ·
[Releases](https://github.com/pcorless/icepdf/releases) · [Issues](https://github.com/pcorless/icepdf/issues)

## Get started

Core runs on Java 11. The JavaFX viewer needs Java 17 (it brings JavaFX 22 with it).

```xml
<dependency>
  <groupId>com.github.pcorless.icepdf</groupId>
  <artifactId>icepdf-core</artifactId>
  <version>8.0.0</version>
</dependency>
<!-- JavaFX viewer -->
<dependency>
  <groupId>com.github.pcorless.icepdf</groupId>
  <artifactId>icepdf-viewer-fx</artifactId>
  <version>8.0.0</version>
</dependency>
<!-- Swing viewer: icepdf-viewer. Optional bundled fonts: icepdf-fonts -->
```

```groovy
dependencies {
    implementation 'com.github.pcorless.icepdf:icepdf-core:8.0.0'
    implementation 'com.github.pcorless.icepdf:icepdf-viewer-fx:8.0.0'
    // Swing viewer: icepdf-viewer. Optional bundled fonts: icepdf-fonts
}
```

### Try the JavaFX viewer

From a clone of this repository (Gradle builds the core from source):

```
./gradlew :viewer:viewer-fx:run --args="/path/to/file.pdf"
```

### Embed the JavaFX viewer

`PdfViewer` is the full reference viewer: tabs, annotations, forms, signing, redaction, printing, high-contrast
themes and 13 languages. `PdfView` is the page view on its own, a control you drop into your own scene.

```java
public class Viewer extends Application {
    @Override
    public void start(Stage stage) {
        FontSettings.apply();                  // cached system font scan
        PdfViewer viewer = PdfViewer.create(); // every tool and panel
        viewer.setOnLink(getHostServices()::showDocument);

        stage.setScene(new Scene(viewer, 1200, 900));
        stage.show();
        viewer.open(Path.of("input.pdf"));
    }
}
```

More in [viewer/viewer-fx/README.md](viewer/viewer-fx/README.md) and the runnable
[JavaFX examples](examples/javafx), such as `./gradlew :examples:javafx:run --args="file.pdf"`.

### Render a page to PNG

Rendering goes through Java2D, so it works headless on a server.

```java
Document document = new Document();
document.setFile("input.pdf");

float scale = 2f, rotation = 0f;
Page page = document.getPageTree().getPage(0);
page.init();
PDimension size = page.getSize(Page.BOUNDARY_CROPBOX, rotation, scale);

BufferedImage image = new BufferedImage((int) size.getWidth(), (int) size.getHeight(),
        BufferedImage.TYPE_INT_RGB);
Graphics2D g = image.createGraphics();
page.paint(g, GraphicsRenderingHints.PRINT, Page.BOUNDARY_CROPBOX, rotation, scale);
g.dispose();

ImageIO.write(image, "png", new File("page-1.png"));
document.dispose();
```

### Swing viewer

The long-standing Swing viewer is built from `SwingController` and `SwingViewBuilder`. Use the whole window or
just the viewer panel.

```java
SwingUtilities.invokeLater(() -> {
    SwingController controller = new SwingController();
    SwingViewBuilder factory = new SwingViewBuilder(controller);

    JFrame frame = new JFrame("icepdf");
    frame.getContentPane().add(factory.buildViewerPanel());
    frame.pack();
    frame.setVisible(true);

    controller.openDocument("input.pdf");
});
```

## Getting the Code
To get a local copy of the current code, clone it using git:
```
$ git clone https://github.com/pcorless/icepdf.git
$ cd icepdf
```

### Building icepdf
The project builds with Gradle or Maven; pick whichever you prefer. Core and the Swing viewer target Java 11;
the JavaFX viewer targets Java 17.

#### Building With Gradle

```
# core jar
./gradlew :core:core-awt:assemble

# JavaFX viewer jar, and run it
./gradlew :viewer:viewer-fx:assemble
./gradlew :viewer:viewer-fx:run

# Swing viewer jar
./gradlew :viewer:viewer-awt:assemble

# an example, such as annotation creation
./gradlew :examples:annotation:creation:assemble

# distribution zip and tar archives
./gradlew osDistZip osDistTar
```

#### Building With Maven
```
# core module
mvn -pl :icepdf-core package

# JavaFX viewer: install the core first, then run the viewer
mvn -f core/core-awt/pom.xml install -DskipTests -Djacoco.skip=true
mvn -f viewer/viewer-fx/pom.xml javafx:run

# Swing viewer module, -am ensures dependencies are built
mvn -pl :icepdf-viewer -am package

# Swing viewer with all dependencies in one self-executing jar
mvn -pl :icepdf-viewer -am package -P assembly

# examples module, -am ensures dependencies are built
mvn -pl :png-capture -am package
```

## More capture formats

Because pages paint to any Java2D graphics context, other output formats follow the same pattern as the PNG
example above. Examples for PNG, SVG and TIFF are in the [examples/capture](examples/capture) folder. This one
saves a page as SVG with Apache Batik:
 
 ```java
String filePath = "somefilepath/myfile.pdf";
Document document = new Document();
document.setFile(filePath);

// Get a DOMImplementation
DOMImplementation domImpl = GenericDOMImplementation.getDOMImplementation();
// Create an instance of org.w3c.dom.Document
org.w3c.dom.Document svgDocument = domImpl.createDocument(null,"svg",null);
// Create an instance of the SVG Generator
SVGGraphics2D svgGenerator = new SVGGraphics2D(svgDocument);
float userRotation = 0;
float userZoom = 1;
int pageNumber = 0;

PDimension pdfDimension=document.getPageDimension(pageNumber,userRotation,userZoom);
svgGenerator.setSVGCanvasSize(pdfDimension.toDimension());

// paint the page to the Batik svgGenerator graphics context.
document.paintPage(pageNumber,svgGenerator,
     GraphicsRenderingHints.PRINT,
     Page.BOUNDARY_CROPBOX,
     userRotation,
     userZoom);

File file = new File("svgCapture_"+pageNumber+".svg");
// Finally, stream the SVG using UTF-8character byte encoding
Writer fileWriter = new OutputStreamWriter(new FileOutputStream(file),StandardCharsets.UTF_8);
// Enable SVG CSS style attribute
boolean SVG_CSS = true;
svgGenerator.stream(fileWriter,SVG_CSS);
```

Make sure to take a look at the [Wiki](https://github.com/pcorless/icepdf/wiki/Usage-Examples) for more examples of
extracting content.

## Learning
 
### Examples

There are a bunch of examples located in the root of the project grouped by common usage scenarios. Similarly, the
Wiki contains [example](https://github.com/pcorless/icepdf/wiki/Usage-Examples) information. 

 ### API Documentation
 
 Both the Gradle and Maven builds will generate the API documentation for the Core and Viewer libraries. 
 
