package org.icepdf.qa.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.icepdf.qa.viewer.common.PreferencesController;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Content set for testing against certain types of common files.
 */

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.WRAPPER_OBJECT)
public class ContentSet {

    private Path contentSetPath;

    private String name;

    private String path;

    private List<String> fileNames;

    @JsonCreator
    public ContentSet(@JsonProperty("name") String name, @JsonProperty("path") String path) {
        this.name = name;
        this.path = path;
        fileNames = new ArrayList<>();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<String> getFileNames() {
        return fileNames;
    }

    public void setFileNames(List<String> fileNames) {
        this.fileNames = fileNames;
    }

    public String getPath() {
        return path;
    }

    @JsonIgnore
    public Path getFilePath() {
        return Paths.get(PreferencesController.getContentSetFilesDiretory(), path);
    }

    public void setPath(String path) {
        this.path = path;
    }

    @JsonIgnore
    public Path getContentSetPath() {
        return contentSetPath;
    }

    @JsonIgnore
    public void setContentSetPath(Path contentSetPath) {
        this.contentSetPath = contentSetPath;
    }

    public void refreshFiles() {
        if (fileNames != null) {
            fileNames.clear();

        }
        Path contentPath = Paths.get(PreferencesController.getContentSetFilesDiretory(), path);
        if (Files.isDirectory(contentPath)) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(contentPath)) {
                for (Path entry : stream) {
                    if (entry.getFileName().toString().toLowerCase().endsWith(".pdf")) {
                        fileNames.add(entry.getFileName().toString());
                    }
                }
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }

    @Override
    public String toString() {
        return name;
    }
}
