package org.icepdf.core.util.edit.content;

import org.icepdf.core.pobjects.Page;
import org.icepdf.core.util.Library;
import org.icepdf.core.util.updater.callbacks.ContentStreamTextEditorCallback;

import java.awt.*;
import java.io.IOException;

/**
 * TextContentEditor is a utility class that provides methods to update text content in a PDF page.
 */
public class TextContentEditor {

    /**
     * Updates the text content of a PDF page.
     *
     * @param page       the PDF page to update
     * @param text       text to be replaced
     * @param textBounds the bounds of the text to be replaced
     * @param newText    the new text to replace the old text
     * @throws InterruptedException page init can be interrupted
     * @throws IOException          if an error occurs while writing the content stream
     */
    public static void updateText(Page page, String text, Rectangle textBounds, String newText) throws InterruptedException,
            IOException {
        Library library = page.getLibrary();
        ContentStreamTextEditorCallback contentStreamCallback =
                new ContentStreamTextEditorCallback(library, text, textBounds, newText);
        page.init(contentStreamCallback);
        contentStreamCallback.endContentStream();
    }
}
