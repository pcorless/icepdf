package org.icepdf.core.util.updater.callbacks;

import org.icepdf.core.pobjects.Stream;

public class ContentStreamRedactorWriter {

    private Stream currentStream;
    private int lastTokenPosition;

    public void startContentStream(Stream stream) {
        currentStream = stream;
        System.out.println("start");
        // assign stream so we write to it later
    }

    public void endContentStream() {
        System.out.println("end");
        // assign accumulated byte[] to the stream

        // should just be the raw bytes like we do for annotation content stream,  writers take care of the details.

        currentStream = null;
    }

    public void setLastTokenPosition(int position) {
        lastTokenPosition = position;
    }

    public void redact() {
        //

        System.out.println("got some text");
    }
}
