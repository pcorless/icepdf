package org.icepdf.core.application;

import org.icepdf.core.pobjects.Document;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Provide a means to applications, such as the RI, for example, to query
 * if ICEpdf Pro features are available.
 *
 * @since 4.0
 */
public class Capabilities {
    private static final Logger logger =
            Logger.getLogger(Capabilities.class.getName());

    public static boolean isIncrementalUpdatingAvailable() {
        Constructor fontClassConstructor = null;
        try {
            Class incUpdateClass = Class.forName(
                    "org.icepdf.core.util.IncrementalUpdater");
            fontClassConstructor =
                    incUpdateClass.getConstructor();
            logger.log(Level.FINE, "Incremental updates supported");
        }
        catch (ClassNotFoundException e) {
            logger.log(Level.FINE, "Incremental updates not supported");
        }
        catch (NoSuchMethodException e) {
            logger.log(Level.SEVERE,
                    "Incremental updates not supported due to API mismatch", e);
        }
        return (fontClassConstructor != null);
    }

    public static long appendIncrementalUpdate(
            Document document, OutputStream out, long documentLength)
            throws IOException {

        long ret = 0;
        if (isIncrementalUpdatingAvailable()) {
            try {
                Class incUpdateClass = Class.forName(
                        "org.icepdf.core.util.IncrementalUpdater");

                Object incrementalUpdate =
                        incUpdateClass.getConstructor((Class[]) null).newInstance((Object[]) null);

                Method appendIncrementalUpdate =
                        incrementalUpdate.getClass().getMethod(
                                "appendIncrementalUpdate",
                                Document.class, OutputStream.class, Long.TYPE);

                ret = (Long) appendIncrementalUpdate.invoke(
                        incrementalUpdate, document, out, documentLength);
            }
            catch (IllegalAccessException e) {
                logger.log(Level.SEVERE,
                        "Problem with incremental update feature", e);
            }
            catch (InvocationTargetException e) {
                logger.log(Level.SEVERE,
                        "Problem with incremental update feature", e);
            }
            catch (ClassNotFoundException e) {
                logger.log(Level.FINE, "Incremental updates not supported");
            }
            catch (NoSuchMethodException e) {
                logger.log(Level.SEVERE,
                        "Incremental updates not supported due to API mismatch", e);
            } catch (InstantiationException e) {
                logger.log(Level.SEVERE,
                        "Problem with incremental update feature", e);
            }
        }
        return ret;
    }

}
