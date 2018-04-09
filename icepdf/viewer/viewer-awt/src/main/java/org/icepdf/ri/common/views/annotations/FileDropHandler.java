/*
 * Copyright 2006-2018 ICEsoft Technologies Canada Corp.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an "AS
 * IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */
package org.icepdf.ri.common.views.annotations;

import java.io.File;

/**
 * FileDropHandler is an abstract class for building custom drop handlers for a particular file extension.  An
 * implementing class needs to register a file extension and implement the execute behavior.
 *
 * @since 6.3.1
 */
public abstract class FileDropHandler {

    private String extension;

    public FileDropHandler(String fileExtension) {
        this.extension = fileExtension.toLowerCase();
    }

    public String getExtension() {
        return extension;
    }

    public abstract void execute(File file, PopupAnnotationComponent popupAnnotationComponent);
}
