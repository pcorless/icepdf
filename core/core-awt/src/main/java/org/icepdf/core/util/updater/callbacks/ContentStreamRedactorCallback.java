package org.icepdf.core.util.updater.callbacks;

import org.icepdf.core.pobjects.Stream;

public class ContentStreamRedactorCallback {

    private Stream currentStream;
    private int lastTokenPosition;

    // todo constructor that passes in the current pages redaction annots.

    public void startContentStream(Stream stream) {
        // if stream isn't null close it off push the new state to the StateManager
        currentStream = stream;
        System.out.println("start");
        // create new byte[] to start chunking content stream bytes to from the original stream
    }

    public void endContentStream() {
        // probably don't need this callback
        System.out.println("end");
        // assign accumulated byte[] to the stream

        // should just be the raw bytes like we do for annotation content stream,  writers take care of the details.

        currentStream = null;
    }

    public void setLastTokenPosition(int position) {
        lastTokenPosition = position;
    }

    // pass in current text states, so we can calculate offset of text should go if something before it was removed
    public void redact() {
        // check for intersection with annotation bounds.

        System.out.println("got some text @ " + lastTokenPosition);
    }
}
