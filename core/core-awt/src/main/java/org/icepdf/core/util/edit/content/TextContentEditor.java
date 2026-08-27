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
        // When the run's own font cannot write the replacement - which is the ordinary case for a
        // subsetted composite font, since it holds the characters the document already used and no
        // others - the text is written in a substitute instead. Refusing would be the alternative,
        // and for the small corrections this exists for it is the worse one.
        SubstituteFont substitute = null;
        if (TextEditCapability.requiresSubstitution(page, textBounds, newText)) {
            substitute = SubstituteFont.forText(page, TextEditCapability.fontAt(page, textBounds), newText);
        }
        ContentStreamTextEditorCallback contentStreamCallback =
                new ContentStreamTextEditorCallback(library, text, textBounds, newText, substitute);
        page.init(contentStreamCallback);
        contentStreamCallback.endContentStream();
    }
}
