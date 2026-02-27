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

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

/**
 * Jackson serialization tests.
 */
public class ConfigWriteTests {

    public static void main(String[] args) throws IOException {

        // jackson object mapper.
        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.WRITE_ENUMS_USING_TO_STRING);
        mapper.enable(DeserializationFeature.READ_ENUMS_USING_TO_STRING);

        // project testAndAnalyze
        Project project = new Project("image capture");
        project.setCaptureSetAConfigFile("pro_6.1.3_capture");
        project.setCaptureSetBConfigFile("pro_6.2.2_capture");
        project.setStatus(Project.Status.complete);
//        project.add(new Result("testAndAnalyze-file_1.pdf", "image_1_0.png", 98.9));
//        project.add(new Result("testAndAnalyze-file_2.pdf","image_1_0.png", 93.9));
//        project.add(new Result("testAndAnalyze-file_2.pdf", "image_1_0.png",99.9));

        mapper.writerWithDefaultPrettyPrinter().writeValue(new FileWriter("project-capture.json"), project);
        project = mapper.readValue(FileUtils.readFileToByteArray(new File("project-capture.json")), Project.class);
        System.out.println(project);

        // content set testAndAnalyze.
        ContentSet contentSet = new ContentSet("Full Monty", "c://");
        contentSet.getFileNames().add("test_1.pdf");
        contentSet.getFileNames().add("test_2.pdf");
        mapper.writerWithDefaultPrettyPrinter().writeValue(new FileWriter("contentSet-full-monty.json"), contentSet);

        // Capture set testAndAnalyze.
        CaptureSet captureSet = new CaptureSet("v6.2.2", CaptureSet.Type.capture);
        captureSet.setClassPath("d:/products/testAndAnalyze/PDF");
        captureSet.getContentSets().add("annotations.jon");
        captureSet.getContentSets().add("fonts-cid.json");
        mapper.writerWithDefaultPrettyPrinter().writeValue(new FileWriter("captureSet.json"), captureSet);
    }
}
