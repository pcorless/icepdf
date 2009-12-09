package org.icepdf.core.application;

import org.icepdf.core.pobjects.StateManager;
import org.icepdf.core.pobjects.PTrailer;
import org.icepdf.core.pobjects.Document;

import java.io.OutputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * Provide a means to applications, such as the RI, for example, to query
 * if ICEpdf Pro features are available.
 *
 * @since 4.0
 */
public class Capabilities {
    private static final Logger logger =
        Logger.getLogger(Capabilities.class.getName());

    private static Method IncrementalUpdate_appendIncrementalUpdate = null;

    static {
        try {
            Class incUpdateClass = Class.forName(
                "org.icepdf.core.util.IncrementalUpdater");
            IncrementalUpdate_appendIncrementalUpdate =
                incUpdateClass.getMethod(
                    "appendIncrementalUpdate",
                    new Class[] {Document.class, OutputStream.class, Long.TYPE});
            logger.log(Level.FINE, "Incremental updates supported");
        }
        catch(ClassNotFoundException e) {
            logger.log(Level.FINE, "Incremental updates not supported");
        }
        catch(NoSuchMethodException e) {
            logger.log(Level.SEVERE,
                "Incremental updates not supported due to API mismatch", e);
        }
    }

    public static boolean isIncrementalUpdatingAvailable() {
        return (IncrementalUpdate_appendIncrementalUpdate != null);
    }

    public static long appendIncrementalUpdate(
        Document document, OutputStream out, long documentLength)
            throws IOException {
        long ret = 0;
        if (IncrementalUpdate_appendIncrementalUpdate != null) {
            try {
                ret = (Long) IncrementalUpdate_appendIncrementalUpdate.invoke(
                    null, document, out, documentLength);
            }
            catch(IllegalAccessException e) {
                logger.log(Level.SEVERE,
                    "Problem with incremental update feature", e);
            }
            catch(InvocationTargetException e) {
                logger.log(Level.SEVERE,
                    "Problem with incremental update feature", e);
            }
        }
        return ret;
    }

}
