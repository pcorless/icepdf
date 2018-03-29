package org.icepdf.ri.common.views.annotations;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public final class AnnotationFileDropHandler {

    private static AnnotationFileDropHandler instance;

    private List<FileDropHandler> fileDropHandlers;

    private AnnotationFileDropHandler() {
        fileDropHandlers = new ArrayList<>();
    }

    public static AnnotationFileDropHandler getInstance() {
        if (instance == null) {
            instance = new AnnotationFileDropHandler();
        }
        return instance;
    }

    public void addFileDropHandler(FileDropHandler fileDropHandler) {
        fileDropHandlers.add(fileDropHandler);
    }

    public void handlePopupAnnotationFileDrop(File file, PopupAnnotationComponent popupAnnotationComponent) {
        for (FileDropHandler fileDropHandler : fileDropHandlers) {
            if (file != null && file.getName().toLowerCase().endsWith(fileDropHandler.getExtension())) {
                fileDropHandler.execute(file, popupAnnotationComponent);
            }
        }
    }


}
