# ICEpdf

ICEpdf is a pure Java PDF document rendering and viewing solution. ICEpdf can parse and render documents based on the 
latest PDF standards (Portable Document Format v1.6/Adobe® Acrobat® 7).

ICEpdf is a community-driven project with the goal of supporting and enhancing the ICEpdf library.  

## Contributing
ICEpdf is an open source project and is always looking for more contributors.  To get involved, visit:

 - [Issue Reporting Guide](https://github.com/pcorless/icepdf/wiki/Issue-Reporting-Guide)
 - [Good Beginner Bugs](https://github.com/pcorless/icepdf/issues?q=is%3Aopen+is%3Aissue+label%3A%22good+first+issue%22)
 - [Projects](https://github.com/pcorless/icepdf/projects)
 - [Frequently Asked Questions](https://github.com/pcorless/icepdf/wiki/Frequently-Asked-Questions)
   <!-- Code Contribution Guide --> 
 ## Getting Started
 Whether you are long time user of the API or a new user, there ton of information on the 
 [Wiki](https://github.com/pcorless/icepdf/wiki) pages.  Create a pull requests and use the issue tracker, the more 
 help and feedback we get the better we an make the project. 
 
 ## Getting the Code
 To get a local copy of the current code, clone it using git:
 ```
$ git clone https://github.com/pcorless/icepdf.git
$ cd icepdf
```
 
 ### Building ICEpdf
 In order to use the library you need to build at least the Core library and if you intend you use the Viewer
 component you'll also need to build the Viewer library.  The project can be built with Gradle or Maven, we have 
 no preference,  pick which ever one makes you more happy. 
 
 Builds as they are currently written work best with Java 8 but they can also be easily configured to work with JDK 11+. 
 
#### Building With Gradle

Build the core jar using the following Gradle command

```~$ gradle :core:core-awt:assemble ```

Build the viewer jar using the following Gradle command

```~$ gradle :viewer:viewer-awt:assemble``` 

Build the annotation creation example using the following Gradle command

```~$  gradle :examples:annotation:creation:assemble```

Build the distribution zip and tar archives

```
# defaultTasks allows for a call to just gradle 
~$ gradle
# or one can use the full task list 
~$ gradle projectReport, sourcesJar, genPomFileForCoreJarPub, genPomFileForViewerJarPub, osDistZip, osDistTar
```

#### Building With Maven
```
# core module
~$ mvn -pl :icepdf-core package

# viewer module, -am insures dependencies are build 
~$ mvn -pl :icepdf-viewer -am package

# examples module, -am insures dependencies are build 
~$ mvn -pl :png-capture -am package
# or with full group id. 
~$ mvn -pl org.icepdf.os.examples:png-capture -am package

# Whole project hierarchy can be built with or with full group id. 
~$ mvn package

 ```

 ## Using ICEpdf Viewer Component
 The `org.icepdf.core.ri.common.SwingController` class provides convenience methods for the most common UI actions, 
 such as rotating the document, setting the zoom level, etc. The `org.icepdf.core.ri.common.SwingViewBuilder` class is 
 responsible for creating the PDF Viewer component panel populated with Swing components configured to work with the 
 SwingController.
 
 When using the SwingViewBuilder and SwingController classes, it is usually not necessary to use the Document object 
 directly. The SwingController class does this for us.
 
 The following code snippet illustrates how to build a PDF Viewer component:
 ```java
String filePath = "somefilepath/myfile.pdf";

// build a controller
SwingController controller = new SwingController();

// Build a SwingViewFactory configured with the controller
SwingViewBuilder factory = new SwingViewBuilder(controller);

// Use the factory to build a JPanel that is pre-configured
//with a complete, active Viewer UI.
JPanel viewerComponentPanel = factory.buildViewerPanel();

// add copy keyboard command
ComponentKeyBinding.install(controller, viewerComponentPanel);

// add interactive mouse link annotation support via callback
controller.getDocumentViewController().setAnnotationCallback(
      new org.icepdf.ri.common.MyAnnotationCallback(
             controller.getDocumentViewController()));

// Create a JFrame to display the panel in
JFrame window = new JFrame("Using the Viewer Component");
window.getContentPane().add(viewerComponentPanel);
window.pack();
window.setVisible(true);

// Open a PDF document to view
controller.openDocument(filePath);
```
 ## Page Captures
 
 The Document class provides functionality for rendering PDF content into other formats via a Java2D graphics context.
 As a result, rendering PDF content to other formats is a relatively simple process with very powerful results. ICEpdf 
 also supports Java headless mode when rending PDF content, which can be useful for server side solutions.
 
 An example of how to extract PDF document content to SVG is available in the SVG class found in the package 
 org.icepdf.ri.util. The following is an example of how to save page captures in PNG format
 
 ```java
String filePath = "somefilepath/myfile.pdf";

// build a controller
SwingController controller = new SwingController();

// Build a SwingViewFactory configured with the controller
SwingViewBuilder factory = new SwingViewBuilder(controller);

// Use the factory to build a JPanel that is pre-configured
//with a complete, active Viewer UI.
JPanel viewerComponentPanel = factory.buildViewerPanel();

// add copy keyboard command
ComponentKeyBinding.install(controller, viewerComponentPanel);

// add interactive mouse link annotation support via callback
controller.getDocumentViewController().setAnnotationCallback(
      new org.icepdf.ri.common.MyAnnotationCallback(
             controller.getDocumentViewController()));

// Create a JFrame to display the panel in
JFrame window = new JFrame("Using the Viewer Component");
window.getContentPane().add(viewerComponentPanel);
window.pack();
window.setVisible(true);

// Open a PDF document to view
controller.openDocument(filePath);
```
 
 Make sure to take a look at the [Wiki](https://github.com/pcorless/icepdf/wiki/Examples) for more examples of extracting content.  
 
 ## Learning
  
 ### Examples

There are bunch of examples located in the root of the project grouped by common usage scenarios.  Similarly the 
Wiki contains [example](https://github.com/pcorless/icepdf/wiki/Examples) information. 

 ### API Documentation
 
 Both the Gradle and Maven builds will generate the API documentation for the Core and Viewer libraries. 
 
