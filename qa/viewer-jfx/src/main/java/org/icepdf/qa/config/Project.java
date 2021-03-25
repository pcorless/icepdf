package org.icepdf.qa.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.As;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * QA Project declaration.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = As.WRAPPER_OBJECT)
public class Project {

    private String name;

    private CaptureSet captureSetA;
    private String captureSetAConfigFile;

    private CaptureSet captureSetB;
    private String captureSetBConfigFile;

    public enum Status {complete, running, pause, stopped, failed, incomplete}

    private Status status;

    private List<Result> results;

    private Path projectPath;

    @JsonCreator
    public Project(@JsonProperty("name") String name) {
        this.name = name;
        status = Status.incomplete;
        results = new ArrayList<>();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCaptureSetAConfigFile() {
        return captureSetAConfigFile;
    }

    public void setCaptureSetAConfigFile(String captureSetAConfigFile) {
        this.captureSetAConfigFile = captureSetAConfigFile;
    }

    public String getCaptureSetBConfigFile() {
        return captureSetBConfigFile;
    }

    public void setCaptureSetBConfigFile(String captureSetBConfigFile) {
        this.captureSetBConfigFile = captureSetBConfigFile;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public List<Result> getResults() {
        return results;
    }

    public void setResults(List<Result> results) {
        this.results = results;
    }

    public void add(Result result) {
        results.add(result);
    }

    public void clearResults() {
        results.clear();
    }

    @JsonIgnore
    public CaptureSet getCaptureSetA() {
        return captureSetA;
    }

    @JsonIgnore
    public void setCaptureSetA(CaptureSet captureSetA) {
        this.captureSetA = captureSetA;
    }

    @JsonIgnore
    public CaptureSet getCaptureSetB() {
        return captureSetB;
    }

    @JsonIgnore
    public void setCaptureSetB(CaptureSet captureSetB) {
        this.captureSetB = captureSetB;
    }

    @JsonIgnore
    public Path getProjectPath() {
        return projectPath;
    }

    @JsonIgnore
    public void setProjectPath(Path projectPath) {
        this.projectPath = projectPath;
    }
}
