package org.icepdf.qa.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.icepdf.qa.viewer.common.PreferencesController;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Serialize one of the jackson objects to the relative locations as defined by the properties objet.
 */
public class ConfigSerializer {

    private static final ObjectMapper mapper = new ObjectMapper();

    static {
        mapper.enable(SerializationFeature.WRITE_ENUMS_USING_TO_STRING);
        mapper.enable(DeserializationFeature.READ_ENUMS_USING_TO_STRING);
    }

    public static Path save(Object object) {
        try {
            if (object instanceof Project) {
                Project project = (Project) object;
                String filePath;
                Path path = project.getProjectPath();
                if (path == null) {
                    filePath = PreferencesController.getProjectDirectory() + generateFileName(project.getName());
                    path = Paths.get(filePath);
                }
                if (!Files.exists(path.getParent())) {
                    Files.createDirectories(path.getParent());
                }
                // check if file all ready exist.
                mapper.writerWithDefaultPrettyPrinter().writeValue(new FileWriter(path.toFile()), project);
                return path;
            } else if (object instanceof CaptureSet) {
                CaptureSet captureSet = (CaptureSet) object;
                String filePath;
                Path path = captureSet.getCaptureSetPath();
                if (path == null) {
                    filePath = PreferencesController.getCaptureSetDirectory() + generateFileName(captureSet.getName());
                    path = Paths.get(filePath);
                }
                if (!Files.exists(path.getParent())) {
                    Files.createDirectories(path.getParent());
                }
                // check if file all ready exist.
                mapper.writerWithDefaultPrettyPrinter().writeValue(new FileWriter(path.toFile()), captureSet);
                return path;
            } else if (object instanceof ContentSet) {
                ContentSet contentSet = (ContentSet) object;
                String filePath;
                Path path = contentSet.getContentSetPath();
                if (path == null) {
                    filePath = PreferencesController.getContentSetDirectory() + generateFileName(contentSet.getName());
                    path = Paths.get(filePath);
                }
                if (!Files.exists(path.getParent())) {
                    Files.createDirectories(path.getParent());
                }
                // check if file all ready exist.
                mapper.writerWithDefaultPrettyPrinter().writeValue(new FileWriter(path.toFile()), contentSet);
                return path;
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static Project retrieveProject(Path projectPath) {
        try {
            Project project = mapper.readValue(new FileReader(projectPath.toFile()), Project.class);
            project.setProjectPath(projectPath);
            return project;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static CaptureSet retrieveCaptureSet(String captureSetFileName) {
        try {
            Path contentSetPath = Paths.get(PreferencesController.getCaptureSetDirectory(), captureSetFileName);
            CaptureSet captureSet = mapper.readValue(new FileReader(contentSetPath.toFile()), CaptureSet.class);
            captureSet.setCaptureSetPath(contentSetPath.toAbsolutePath());
            return captureSet;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static ContentSet retrieveContentSet(Path contentSetPath) {
        try {
            ContentSet contentSet = mapper.readValue(new FileReader(contentSetPath.toFile()), ContentSet.class);
            contentSet.setContentSetPath(contentSetPath);
            return contentSet;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static List<CaptureSet> retrieveAllCaptureSets() {
        try {
            Path contentSetPath = Paths.get(PreferencesController.getCaptureSetDirectory());
            if (!Files.exists(contentSetPath)) {
                Files.createDirectories(contentSetPath);
            }
            List<CaptureSet> contentSets = new ArrayList<>();
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(contentSetPath)) {
                for (Path entry : stream) {
                    CaptureSet captureSet = mapper.readValue(new FileReader(entry.toFile()), CaptureSet.class);
                    captureSet.setCaptureSetPath(entry.toAbsolutePath());
                    contentSets.add(captureSet);
                }
            }
            return contentSets;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static List<ContentSet> retrieveAllContentSets() {
        try {
            Path contentSetPath = Paths.get(PreferencesController.getContentSetDirectory());
            if (!Files.exists(contentSetPath)) {
                Files.createDirectories(contentSetPath);
            }
            List<ContentSet> contentSets = new ArrayList<>();
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(contentSetPath)) {
                for (Path entry : stream) {
                    ContentSet contentSet = mapper.readValue(new FileReader(entry.toFile()), ContentSet.class);
                    contentSet.setContentSetPath(entry.toAbsolutePath());
                    contentSets.add(contentSet);
                }
            }
            return contentSets;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static boolean exists(String parentPath, String fileName) {
        String filePath = parentPath + generateFileName(fileName);
        return Files.exists(Paths.get(filePath));
    }

    /**
     * Utility to convert a name to a file name with a json extension.
     * Converts to lowercase and replaces spaces with underscores.
     *
     * @param name file name to clean.
     * @return cleaned file name
     */
    private static String generateFileName(String name) {
        return name.toLowerCase().trim().replaceAll("( )+", "_") + ".json";
    }

}
