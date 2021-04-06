package org.icepdf.qa.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Capture set details, mainly keeps track of the content sets being used  and some meta data.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.WRAPPER_OBJECT)
public class CaptureSet {

    public enum Type {

        capture {
            public String toString() {
                return "Capture";
            }
        },
        metric {
            public String toString() {
                return "Metric";
            }
        },
        textExtraction {
            public String toString() {
                return "Text Extraction";
            }
        }
    }

    private Path captureSetPath;

    private String name;
    private Type type;

    private String version;
    private String classPath;
    private URLClassLoader classLoader;
    private int capturePageCount;

    private String jdkVersion;
    private String systemProperties;

    private List<String> contentSets;

    private Path contentSetPath;

    private boolean complete;

    @JsonCreator
    public CaptureSet(@JsonProperty("name") String name,
                      @JsonProperty("type") Type type) {
        this.name = name;
        this.type = type;
        contentSets = new ArrayList<>();
    }

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getClassPath() {
        return classPath;
    }

    public void setClassPath(String classPath) {
        this.classPath = classPath;
    }

    public int getCapturePageCount() {
        return capturePageCount;
    }

    public void setCapturePageCount(int capturePageCount) {
        this.capturePageCount = capturePageCount;
    }

    public String getJdkVersion() {
        return jdkVersion;
    }

    public void setJdkVersion(String jdkVersion) {
        this.jdkVersion = jdkVersion;
    }

    public String getSystemProperties() {
        return systemProperties;
    }

    public void setSystemProperties(String systemProperties) {
        this.systemProperties = systemProperties;
    }

    public List<String> getContentSets() {
        return contentSets;
    }

    public void setContentSets(List<String> contentSets) {
        this.contentSets = contentSets;
    }

    public boolean isComplete() {
        return complete;
    }

    public void setComplete(boolean complete) {
        this.complete = complete;
    }

    @Override
    public String toString() {
        return name;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj != null)
            return this.getName().equals(((CaptureSet) obj).getName());
        else {
            return super.equals(obj);
        }
    }

    @JsonIgnore
    public Path getCaptureSetPath() {
        return captureSetPath;
    }

    @JsonIgnore
    public void setCaptureSetPath(Path captureSetPath) {
        this.captureSetPath = captureSetPath;
    }

    @JsonIgnore
    public URLClassLoader getClassLoader() {
        return classLoader;
    }

    @JsonIgnore
    public void setClassLoader(URLClassLoader classLoader) {
        this.classLoader = classLoader;
    }
}
