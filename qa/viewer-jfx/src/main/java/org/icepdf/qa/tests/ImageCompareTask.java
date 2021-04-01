package org.icepdf.qa.tests;

import javafx.application.Platform;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.PixelReader;
import org.icepdf.qa.config.*;
import org.icepdf.qa.tests.exceptions.AnalyzeException;
import org.icepdf.qa.tests.exceptions.ConfigurationException;
import org.icepdf.qa.tests.exceptions.TestException;
import org.icepdf.qa.tests.exceptions.ValidationException;
import org.icepdf.qa.utilities.TimeTestWatcher;
import org.icepdf.qa.viewer.common.Mediator;
import org.icepdf.qa.viewer.common.PreferencesController;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 *
 */
public class ImageCompareTask extends AbstractTestTask {

    private static final int THREAD_EXECUTOR_SIZE = 8;

    // page capture test
    public static final String PAGE_CAPTURE_CLASS = "org.icepdf.ri.util.qa.PageCapture";
    public static final String PAGE_CAPTURE_SETUP_METHOD = "load";
    public static final String PAGE_CAPTURE_METHOD = "capture";
    public static final String PAGE_CAPTURE_DISPOSE_METHOD = "dispose";

    private Mediator mediator;

    private Project project;
    private CaptureSet captureSetA;
    private CaptureSet captureSetB;
    private List<String> commonContentSets;
    private int commonPageCount;

    private ArrayList<Path> filePaths;

    protected static ExecutorService executorService;

    public ImageCompareTask(Mediator mediator) {
        this.mediator = mediator;
    }

    @Override
    protected List<Result> call() throws TestException, AnalyzeException, ConfigurationException, ValidationException {
        try {
            TimeTestWatcher timeTestWatcher = new TimeTestWatcher();
            timeTestWatcher.starting("Image Compare");
            setup();
            validate();
            config();
            testAndAnalyze();
            timeTestWatcher.finished();

        } catch (ValidationException e) {
            System.out.println("There was a validation error: " + e.getMessage());
        } catch (ConfigurationException e) {
            System.out.println("There was a configuration error: " + e.getMessage());
        } catch (TestException e) {
            System.out.println("There was a test error: " + e.getMessage());
        } finally {
            teardown();
        }
        return null;
    }

    @Override
    protected void succeeded() {
        super.succeeded();
        updateMessage("Done");
    }

    @Override
    protected void cancelled() {
        super.cancelled();
        updateMessage("Cancelled");
    }

    @Override
    protected void failed() {
        super.failed();
        updateMessage("Failed");
    }

    @Override
    public void setup() {

        // disable project level config controls.
        Platform.runLater(() -> mediator.setStartTestTaskGuiState());

        // setup pointers to project and each capture set.
        project = mediator.getCurrentProject();
        captureSetA = project.getCaptureSetA();
        captureSetB = project.getCaptureSetB();

        executorService = Executors.newFixedThreadPool(THREAD_EXECUTOR_SIZE);
    }

    @Override
    public void validate() throws ValidationException {
        System.out.println("---- Validation started -----");
        // validate project, make sure we captures sets that are of the same type.
        if (captureSetA.getType() != captureSetB.getType()) {
            throw new ValidationException("Capture set types must match.");
        }
        // throw exception if content sets have not intersection of content
        List<String> contentSetsA = captureSetA.getContentSets();
        List<String> contentSetsB = captureSetB.getContentSets();
        if (contentSetsA.size() == 0 || contentSetsB.size() == 0) {
            throw new ValidationException("Content sets must contain at least one item.");
        }

        // check for intersection of at least one common set.
        commonContentSets = new ArrayList<>();
        for (String contentNameA : contentSetsA) {
            for (String contentNameB : contentSetsB) {
                if (contentNameA.equals(contentNameB)) {
                    commonContentSets.add(contentNameA);
                }
            }
        }
        if (commonContentSets.size() == 0) {
            throw new ValidationException("Content sets must have at least one set in common.");
        }
    }

    @Override
    public void config() throws ConfigurationException {
        System.out.println("---- Configuration started -----");
        // create union of content sets that will be used in the compare
        if (captureSetA.getCapturePageCount() < captureSetB.getCapturePageCount()) {
            commonPageCount = captureSetA.getCapturePageCount();
        } else {
            commonPageCount = captureSetB.getCapturePageCount();
        }
        if (commonPageCount == 0) {
            throw new ConfigurationException("Capture page count must be at least one.");
        }

        // create new urlClass loaders for each capture set as part of this work and validate that the class loader
        // have valid classes.
        try {
            getTestInstance(captureSetA);
        } catch (Throwable e) {
            e.printStackTrace();
            throw new ConfigurationException("Capture set A classpath does not contain ICEpdf or is invalid.");
        }
        try {
            getTestInstance(captureSetB);
        } catch (Throwable e) {
            throw new ConfigurationException("Capture set B classpath does not contain ICEpdf or is invalid.");
        }

        // load the content set files and assign to hash, name -> ContentSet.
        Map<String, ContentSet> contentSetMap = new HashMap<>();
        List<ContentSet> contentSets = ConfigSerializer.retrieveAllContentSets();
        for (ContentSet contentSet : contentSets) {
            contentSetMap.put(contentSet.getName(), contentSet);
        }

        // build out the actual list of files that needs to be captured
        String contentSetFilesDirectory = PreferencesController.getContentSetFilesDiretory();
        filePaths = new ArrayList<>();
        for (String contentSetName : commonContentSets) {
            // pull form the content set hash an get fileset
            ContentSet contentSet = contentSetMap.get(contentSetName);
            List<String> contentFileNames = contentSet.getFileNames();
            for (String contentFileName : contentFileNames) {
                filePaths.add(Paths.get(contentSetFilesDirectory, contentSet.getPath(), contentFileName));
            }
        }

        // create the capture set results  folder, where we save the image captures.
        try {
            Path resultsFolder = Paths.get(PreferencesController.getResultsPathDirectory(),
                    captureSetA.getCaptureSetPath().getFileName().toString());
            if (!Files.exists(resultsFolder)) {
                Files.createDirectories(resultsFolder);
            }
            resultsFolder = Paths.get(PreferencesController.getResultsPathDirectory(),
                    captureSetB.getCaptureSetPath().getFileName().toString());
            if (!Files.exists(resultsFolder)) {
                Files.createDirectories(resultsFolder);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.out.format("Common content set has been defined: %d files.%n", filePaths.size());
    }

    @Override
    public void testAndAnalyze() throws TestException {
        System.out.println("---- Test and Analyze started -----");
        try {
            List<Result> results = new ArrayList<>(commonContentSets.size() * commonPageCount);
            Platform.runLater(() -> mediator.resetProjectResults());

            int testSize = filePaths.size();
            int i = 1;
            for (Path filePath : filePaths) {
                if (isCancelled()) {
                    updateMessage("Cancelled");
                    break;
                }
                // update the UI
                updateProgress(i, testSize);
                // setup what needs to be captured.
                if (!captureSetA.isComplete() && !captureSetB.isComplete()) {
                    capturePages(filePath, i, captureSetA, captureSetB);
                } else if (!captureSetA.isComplete()) {
                    capturePages(filePath, i, captureSetA);
                } else if (!captureSetB.isComplete()) {
                    capturePages(filePath, i, captureSetB);
                }
                // compare output
                results.addAll(comparePages(i, filePath, captureSetA, captureSetB));

                i++;
            }
            // mark each content set as complete.
            if (!captureSetA.isComplete() && !captureSetB.isComplete()) {
                if (!isCancelled()) {
                    captureSetA.setComplete(true);
                    captureSetB.setComplete(true);
                }
                ConfigSerializer.save(captureSetA);
                ConfigSerializer.save(captureSetB);
            } else if (!captureSetA.isComplete()) {
                if (!isCancelled()) {
                    captureSetA.setComplete(true);
                }
                ConfigSerializer.save(captureSetA);
            } else if (!captureSetB.isComplete()) {
                if (!isCancelled()) {
                    captureSetB.setComplete(true);
                }
                ConfigSerializer.save(captureSetB);
            }

            //save teh projects state.
            project.setResults(results);
            ConfigSerializer.save(project);
        } catch (InterruptedException e) {
            if (isCancelled()) {
                updateMessage("Cancelled");
            }
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    @Override
    public void teardown() {
        // do and cleanup.
        Platform.runLater(() -> mediator.setStopTestTaskGuiState());
        executorService.shutdownNow();
    }

    private Object getTestInstance(CaptureSet captureSet) {
        // create our new class loader and create new pageCaptureTest instance.
        try {

            URLClassLoader classLoader;
            if (captureSet.getClassLoader() == null) {
                // build out the class path
                Path baseDir = Paths.get(captureSet.getClassPath());
                ArrayList<URL> classPath = new ArrayList<>(baseDir.getNameCount());
                System.out.println("Loading class path:");
                try (DirectoryStream<Path> stream = Files.newDirectoryStream(baseDir, "*.{class,jar}")) {
                    for (Path file : stream) {
                        System.out.println("\t" + file.toUri());
                        classPath.add(file.normalize().toUri().toURL());
                    }
                } catch (IOException | DirectoryIteratorException x) {
                    // IOException can never be thrown by the iteration.
                    // In this snippet, it can only be thrown by newDirectoryStream.
                    System.err.println(x);
                }
                ClassLoader parent = String.class.getClassLoader();
                URL[] urls = classPath.toArray(new URL[0]);
                classLoader = URLClassLoader.newInstance(urls, parent);
                captureSet.setClassLoader(classLoader);
            } else {
                classLoader = captureSet.getClassLoader();
            }

            // open the file
            Class pageCaptureClass = classLoader.loadClass(PAGE_CAPTURE_CLASS);
            Constructor fontClassConstructor = pageCaptureClass.getDeclaredConstructor();
            Object pageCaptureObject = fontClassConstructor.newInstance();

            return pageCaptureObject;
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (InstantiationException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }
        return null;
    }

    private int loadTestInstance(CaptureSet captureSet, Object testInstance, Path filePath, int documentIndex, int captureSetTotal) {
        if (filePath != null) {
            System.err.format("File [%d/%d]=%s\n", documentIndex, captureSetTotal, filePath.toString());

            // call setup on the test which returns total number of pages.
            try {
                URLClassLoader classLoader = captureSet.getClassLoader();
                Class pageCaptureClass = classLoader.loadClass(PAGE_CAPTURE_CLASS);
                Method setFileMethod = pageCaptureClass.getMethod(PAGE_CAPTURE_SETUP_METHOD, Path.class);
                Integer pageCount = (Integer) setFileMethod.invoke(testInstance, filePath);
                return pageCount;
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            } catch (NoSuchMethodException e) {
                e.printStackTrace();
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            } catch (InvocationTargetException e) {
                e.printStackTrace();
            }
        }
        return 0;
    }

    private void capturePages(Path filePath, int documentIndex, CaptureSet... captureSet) throws InterruptedException, ExecutionException {
        // capture pageCaptureTest
        Object[] testInstances;
        List<Callable<Void>> callables = new ArrayList<>(commonPageCount * 2);
        testInstances = new Object[captureSet.length];
        for (int i = 0; i < captureSet.length; i++) {
            Object testInstance = getTestInstance(captureSet[i]);

            int numberOfPages = loadTestInstance(captureSet[i], testInstance, filePath, documentIndex, filePaths.size());

            for (int pageNumber = 0; pageNumber < commonPageCount; pageNumber++) {
                callables.add(new CapturePage(testInstance, captureSet[i], filePath, pageNumber, numberOfPages));
            }
            testInstances[i] = testInstance;
        }
        // run the pageCaptureTest capture
        executorService.invokeAll(callables);
        // close
        for (int i = 0; i < captureSet.length; i++) {
            executorService.submit(new DocumentCloser(captureSet[i], testInstances[i])).get();
        }
    }

    private List<Result> comparePages(int documentIndex, Path filePath, CaptureSet captureSetA, CaptureSet captureSetB)
            throws InterruptedException, ExecutionException {
        List<Result> results = new ArrayList<>();
        for (int i = 0; i < commonPageCount; i++) {
            Result result = executorService.submit(new DocumentImageCompare(filePath, i, captureSetA, captureSetB)).get();
            if (result != null) {
                results.add(result);
                if (result.getDifference() < 100) {
                    System.err.format("File [%d/%d|%.2f%%]=%s%n", documentIndex, filePaths.size(), result.getDifference(), filePath.toString());
                    System.out.print('~');
                } else {
                    System.out.print('=');
                }
            }
        }
        Platform.runLater(() -> mediator.addProjectResults(results));

        return results;
    }

    public class CapturePage implements Callable<Void> {
        private Object pageCaptureTest;
        private int pageNumber;
        private float scale = 1f;
        private float rotation = 0f;
        private String fileName;
        private int numberOfPage;

        private CaptureSet captureSet;

        private CapturePage(Object pageCaptureTest, CaptureSet captureSet, Path filePath, int pageNumber, int numberOfPage) {
            this.pageCaptureTest = pageCaptureTest;
            this.pageNumber = pageNumber;
            this.captureSet = captureSet;
            this.numberOfPage = numberOfPage;
            this.fileName = filePath.getFileName().toString();
        }

        public Void call() {
            try {
                URLClassLoader classLoader = captureSet.getClassLoader();
                Class pageCaptureClass = classLoader.loadClass(PAGE_CAPTURE_CLASS);

                if (pageNumber < numberOfPage) {

                    Path imageCapture = Paths.get(PreferencesController.getResultsPathDirectory(),
                            captureSet.getCaptureSetPath().getFileName().toString(),
                            fileName + "_" + pageNumber + ".png");

                    if (!Files.exists(imageCapture)) {
                        System.out.print("|");

                        // paint the page.
                        Method captureMethod = pageCaptureClass.getMethod(PAGE_CAPTURE_METHOD,
                                int.class, int.class, int.class, float.class, float.class);
                        BufferedImage image = (BufferedImage) captureMethod.invoke(pageCaptureTest, pageNumber, 2, 2, rotation, scale);

                        File file = Files.createFile(imageCapture).toFile();
                        ImageIO.write(image, "png", file);
                        image.flush();
                    }
                }
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            } catch (InvocationTargetException e) {
                e.printStackTrace();
            } catch (NoSuchMethodException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            }
            return null;
        }
    }


    /**
     * closes the two documents and compares, followed by adding a new results ot the results tab.
     */
    public class DocumentImageCompare implements Callable<Result> {

        private Path fileName;
        private int pageNumber;
        private CaptureSet[] captureSets;

        private DocumentImageCompare(Path filePath, int pageNumber, CaptureSet... captureSets) {
            this.captureSets = captureSets;
            this.fileName = filePath;
            this.pageNumber = pageNumber;
        }

        public Result call() {
            try {

                Path imageCaptureA = Paths.get(PreferencesController.getResultsPathDirectory(),
                        captureSets[0].getCaptureSetPath().getFileName().toString(),
                        fileName.getFileName() + "_" + pageNumber + ".png");
                Path imageCaptureB = Paths.get(PreferencesController.getResultsPathDirectory(),
                        captureSets[1].getCaptureSetPath().getFileName().toString(),
                        fileName.getFileName() + "_" + pageNumber + ".png");

                FileInputStream imageCaptureAFileStream = new FileInputStream(imageCaptureA.toFile());
                FileInputStream imageCaptureBFileStream = new FileInputStream(imageCaptureB.toFile());

                javafx.scene.image.Image imageA = new javafx.scene.image.Image(imageCaptureAFileStream);
                javafx.scene.image.Image imageB = new javafx.scene.image.Image(imageCaptureBFileStream);

                double diff = percentageCompare(imageA, imageB);

                imageCaptureAFileStream.close();
                imageCaptureBFileStream.close();

                return new Result(
                        fileName.toString(),
                        imageCaptureA.toString(),
                        imageCaptureB.toString(), diff);

            } catch (FileNotFoundException e) {
                // silently move on if the page wasn't created.
            } catch (IOException e) {
                e.printStackTrace();
            } catch (Throwable e) {
                e.printStackTrace();
            }
            return null;
        }
    }

    /**
     * Disposes the pageCaptureTest.
     */
    public class DocumentCloser implements Callable<Void> {
        private Object pageCaptureTest;
        private CaptureSet captureSet;

        private DocumentCloser(CaptureSet captureSet, Object pageCaptureTest) {
            this.pageCaptureTest = pageCaptureTest;
            this.captureSet = captureSet;
        }

        public Void call() {
            try {
                URLClassLoader classLoader = captureSet.getClassLoader();
                Class pageCaptureClass = classLoader.loadClass(PAGE_CAPTURE_CLASS);
                Method disposeMethod = pageCaptureClass.getMethod(PAGE_CAPTURE_DISPOSE_METHOD);
                disposeMethod.invoke(pageCaptureTest);

            } catch (IllegalAccessException e) {
                e.printStackTrace();
            } catch (InvocationTargetException e) {
                e.printStackTrace();
            } catch (NoSuchMethodException e) {
                e.printStackTrace();
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            } catch (Throwable e) {
                e.printStackTrace();
            }
            return null;
        }
    }

    public class UpdateResultsTab implements Callable<Void> {


        private UpdateResultsTab(List<Result> results) {
            // pass the results off to the mediator so we can update the UI.
            Platform.runLater(() -> mediator.addProjectResults(results));
        }

        public Void call() {

            return null;
        }
    }

    private static double percentageCompare(javafx.scene.image.Image image1, javafx.scene.image.Image image2) {

        assert (image1.getWidth() == image2.getWidth() && image2.getHeight() == image2.getHeight());

        PixelReader pixelReader1 = image1.getPixelReader();
        PixelReader pixelReader2 = image2.getPixelReader();

        int width = (int) image1.getWidth();
        int height = (int) image1.getHeight();

        int[] buffer1 = new int[width];
        int[] buffer2 = new int[width];

        int badPixels = 0;
        for (int y = 0; y < height; y++) {
            pixelReader1.getPixels(0, y, width, 1, PixelFormat.getIntArgbInstance(), buffer1, 0, 1);
            pixelReader2.getPixels(0, y, width, 1, PixelFormat.getIntArgbInstance(), buffer2, 0, 1);
            for (int x = 0; x < width; x++) {
                if (buffer1[x] != buffer2[x]) {
                    badPixels++;
                }
            }
        }

        int totalPixels = width * height;
        int goodPixels = totalPixels - badPixels;

        return 100d * goodPixels / totalPixels;
    }


}
