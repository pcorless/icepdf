package org.icepdf.core.util.edit.content;

import org.icepdf.core.pobjects.Page;
import org.icepdf.core.util.Library;
import org.icepdf.core.util.updater.callbacks.ContentStreamTextEditorCallback;

import java.awt.*;
import java.io.IOException;

public class TextContentEditor {

    public static void updateText(Page page, Rectangle textBounds, String newText) throws InterruptedException,
            IOException {
        Library library = page.getLibrary();
        ContentStreamTextEditorCallback contentStreamCallback =
                new ContentStreamTextEditorCallback(library, textBounds, newText);
        page.init(contentStreamCallback);
        contentStreamCallback.endContentStream();
    }
}
