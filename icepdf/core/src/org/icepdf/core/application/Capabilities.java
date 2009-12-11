/*
 * Version: MPL 1.1/GPL 2.0/LGPL 2.1
 *
 * "The contents of this file are subject to the Mozilla Public License
 * Version 1.1 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
 * License for the specific language governing rights and limitations under
 * the License.
 *
 * The Original Code is ICEpdf 3.0 open source software code, released
 * May 1st, 2009. The Initial Developer of the Original Code is ICEsoft
 * Technologies Canada, Corp. Portions created by ICEsoft are Copyright (C)
 * 2004-2009 ICEsoft Technologies Canada, Corp. All Rights Reserved.
 *
 * Contributor(s): _____________________.
 *
 * Alternatively, the contents of this file may be used under the terms of
 * the GNU Lesser General Public License Version 2.1 or later (the "LGPL"
 * License), in which case the provisions of the LGPL License are
 * applicable instead of those above. If you wish to allow use of your
 * version of this file only under the terms of the LGPL License and not to
 * allow others to use your version of this file under the MPL, indicate
 * your decision by deleting the provisions above and replace them with
 * the notice and other provisions required by the LGPL License. If you do
 * not delete the provisions above, a recipient may use your version of
 * this file under either the MPL or the LGPL License."
 *
 */
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
