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
import java.util.ArrayList;
import java.util.List;

/**
 * <p>AnnotationFileDropHandler provides a callback for processing files that are dragged on a PopupAnnotationComponent.
 * The callback allows a users to register a FileDropHandler for each type of file they wish to support for drag and
 * drop.  When a file is dragged on the PopupAnnotationComponent it's file extension is compared against the registered
 * FileDropHandler's and executed if a match is found.</p>
 * <p>The main use for this call back is converting file content to ASCII for insertion into the respective
 * PopupAnnotationComponent's annotation object. </p>
 *
 * @since 6.3.1
 */
public final class AnnotationFileDropHandler {

    private static AnnotationFileDropHandler instance;

    private List<FileDropHandler> fileDropHandlers;

    private AnnotationFileDropHandler() {
        fileDropHandlers = new ArrayList<>();
    }

    /**
     * Gets an instance of the singleton AnnotationFileDropHandler object.
     *
     * @return singleton instance of the AnnotationFileDropHandler.
     */
    public static AnnotationFileDropHandler getInstance() {
        if (instance == null) {
            instance = new AnnotationFileDropHandler();
        }
        return instance;
    }

    /**
     * Register a FileDropHandler implementation with the AnnotationFileDropHandler.
     *
     * @param fileDropHandler FileDropHandler to register.
     */
    public void addFileDropHandler(FileDropHandler fileDropHandler) {
        fileDropHandlers.add(fileDropHandler);
    }

    /**
     * Takes the given file and checks for the FileDropHandler that can handle the extension. If a FileDropHandler
     * match is found the the FileDropHandler.execute() method is called to process the file and annotation.
     *
     * @param file                     file that was dragged on the the popup annotation component.
     * @param popupAnnotationComponent annotation component that should be processed by the FileDropHandler.
     */
    public void handlePopupAnnotationFileDrop(File file, PopupAnnotationComponent popupAnnotationComponent) {
        for (FileDropHandler fileDropHandler : fileDropHandlers) {
            if (file != null && file.getName().toLowerCase().endsWith(fileDropHandler.getExtension())) {
                fileDropHandler.execute(file, popupAnnotationComponent);
            }
        }
    }

}
