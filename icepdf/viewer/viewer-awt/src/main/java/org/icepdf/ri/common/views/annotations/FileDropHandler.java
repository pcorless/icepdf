package org.icepdf.ri.common.views.annotations;

import java.io.File;

public abstract class FileDropHandler {

    private String extension;

    public FileDropHandler(String extension) {
        this.extension = extension.toLowerCase();
    }

    public String getExtension() {
        return extension;
    }

    public abstract void execute(File file, PopupAnnotationComponent popupAnnotationComponent);
}
