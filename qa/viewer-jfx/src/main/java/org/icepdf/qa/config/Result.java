/*
 * Copyright 2026 Patrick Corless
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.icepdf.qa.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;

import java.nio.file.Paths;

/**
 * Base result class
 * Further reading on polymorphism here, http://www.studytrails.com/java/json/java-jackson-Serialization-polymorphism/
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NONE, include = JsonTypeInfo.As.WRAPPER_OBJECT)
public class Result {

    private final SimpleStringProperty documentName;

    private final SimpleStringProperty captureNameA;
    private final SimpleStringProperty captureNameB;

    // headline similarity (%) between captureOne and captureTwo; ink-weighted so missing content scores low.
    private final SimpleDoubleProperty difference;
    // fuzz-tolerant absolute-error similarity (%) over the whole page.
    private final SimpleDoubleProperty aeSimilarity;
    // mean windowed structural similarity (%).
    private final SimpleDoubleProperty structuralSimilarity;

    // can be extended for other properties.

    @JsonCreator
    public Result(@JsonProperty("documentName") String documentName,
                  @JsonProperty("fileNameA") String fileNameA,
                  @JsonProperty("fileName") String fileNameB,
                  @JsonProperty("difference") double difference,
                  @JsonProperty("aeSimilarity") double aeSimilarity,
                  @JsonProperty("structuralSimilarity") double structuralSimilarity) {
        // relative path to content set location
        this.documentName = new SimpleStringProperty(documentName);
        // relative path to capture set location
        this.captureNameA = new SimpleStringProperty(fileNameA);
        this.captureNameB = new SimpleStringProperty(fileNameB);
        this.difference = new SimpleDoubleProperty(difference);
        this.aeSimilarity = new SimpleDoubleProperty(aeSimilarity);
        this.structuralSimilarity = new SimpleDoubleProperty(structuralSimilarity);
    }

    /**
     * Backward-compatible constructor for callers that only have a single
     * similarity score (e.g. the text compare task or older saved projects).
     */
    public Result(String documentName, String fileNameA, String fileNameB, double difference) {
        this(documentName, fileNameA, fileNameB, difference, difference, difference);
    }

    public String getDocumentName() {
        return documentName.get();
    }

    @JsonIgnore
    public String getDocumentFileName() {
        return Paths.get(documentName.get()).getFileName().toString();
    }

    public void setDocumentName(String documentName) {
        this.documentName.set(documentName);
    }

    public String getCaptureNameA() {
        return captureNameA.get();
    }

    @JsonIgnore
    public String getFileNameA() {
        return Paths.get(captureNameA.get()).getFileName().toString();
    }

    public void setCaptureNameA(String captureNameA) {
        this.captureNameA.set(captureNameA);
    }

    public String getCaptureNameB() {
        return captureNameB.get();
    }

    public void setCaptureNameB(String captureNameB) {
        this.captureNameB.set(captureNameB);
    }

    public double getDifference() {
        return Math.round(difference.get() * 100.0) / 100.0;
    }

    public void setDifference(double difference) {
        this.difference.set(difference);
    }

    public double getAeSimilarity() {
        return Math.round(aeSimilarity.get() * 100.0) / 100.0;
    }

    public void setAeSimilarity(double aeSimilarity) {
        this.aeSimilarity.set(aeSimilarity);
    }

    public double getStructuralSimilarity() {
        return Math.round(structuralSimilarity.get() * 100.0) / 100.0;
    }

    public void setStructuralSimilarity(double structuralSimilarity) {
        this.structuralSimilarity.set(structuralSimilarity);
    }
}
