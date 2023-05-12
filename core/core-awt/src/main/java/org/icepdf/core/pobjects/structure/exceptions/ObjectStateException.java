package org.icepdf.core.pobjects.structure.exceptions;

public class ObjectStateException extends Exception {

    public ObjectStateException() {
    }

    public ObjectStateException(String message) {
        super(message);
    }

    public ObjectStateException(String message, Throwable cause) {
        super(message, cause);
    }

    public ObjectStateException(Throwable cause) {
        super(cause);
    }
}
