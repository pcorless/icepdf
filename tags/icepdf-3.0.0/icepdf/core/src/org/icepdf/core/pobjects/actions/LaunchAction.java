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
package org.icepdf.core.pobjects.actions;

import org.icepdf.core.pobjects.StringObject;
import org.icepdf.core.util.Library;

import java.util.Hashtable;

/**
 * <p>The launch action launches an applicaiton or opens or prints a
 * document.</p>
 * <p/>
 * <p>There are optional Win which allow for platform specific parameters for
 * launching the designated application. </p>
 *
 * @author ICEsoft Technologies, Inc.
 * @since 2.6
 */
public class LaunchAction extends Action {

    // path to external file, see section 3.10.1 for more details on
    // resolving paths
    private String externalFile;
    private FileSpecification fileSpecification;

    // new window?
    private Boolean isNewWindow;

    // launch parameters specific to Windows.
    private WindowsLaunchParameters winLaunchParameters;

    // windows specific entries.

    /**
     * Creates a new instance of a Action.
     *
     * @param l document library.
     * @param h Action dictionary entries.
     */
    public LaunchAction(Library l, Hashtable h) {
        super(l, h);

        if (library.getObject(entries, "F") instanceof Hashtable) {
            fileSpecification = new FileSpecification(library,
                    library.getDictionary(entries, "F"));
        } else if (library.getObject(entries, "F") instanceof StringObject) {
            externalFile =
                    ((StringObject) library.getObject(entries, "F"))
                            .getDecryptedLiteralString(
                                    library.getSecurityManager());
        }

        isNewWindow = library.getBoolean(entries, "NewWindow");

        winLaunchParameters = new WindowsLaunchParameters();
    }

    /**
     * Gets the applicaiton to be launched or the document to be opened or
     * printed.
     *
     * @return file specification
     */
    public String getExternalFile() {
        return externalFile;
    }

    /**
     * Specifies whether or not ta new window should be opend.
     *
     * @return true indicates a new window should be used, false otherwise.
     */
    public Boolean getNewWindow() {
        return isNewWindow;
    }

    /**
     * Gets an object which hold the windows-specific launch parameters.
     *
     * @return window specific launch parameters.
     */
    public WindowsLaunchParameters getWinLaunchParameters() {
        return winLaunchParameters;
    }

    /**
     * Gets the file specification of the destination file.  This objects should
     * be interigated to deside what should be done
     *
     * @return file specification, maybe nukll if external file was specified.
     */
    public FileSpecification getFileSpecification() {
        return fileSpecification;
    }

    /**
     * <p>Paramaters specific to launching files on windows.  These parameters
     * specify what application should load the file as well what any special
     * load commands.</p>
     *
     * @since 2.6
     */
    public class WindowsLaunchParameters {

        private FileSpecification launchFileSpecification;

        private String launchFile;

        // default directory in standard dos syntax
        private String defaultDirectory;

        // open or print
        private String operation;

        // launch parameters
        private String parameters;

        /**
         * Creates a new instance of a Action.
         */
        public WindowsLaunchParameters() {

//            Hashtable winLaunch = library.getDictionary(entries, "Win");

            if (library.getObject(entries, "F") instanceof Hashtable) {
                launchFileSpecification = new FileSpecification(library,
                        library.getDictionary(entries, "F"));
            } else if (library.getObject(entries, "F") instanceof StringObject) {
                launchFile =
                        ((StringObject) library.getObject(entries, "F"))
                                .getDecryptedLiteralString(library.getSecurityManager());
            }

            if (library.getObject(entries, "D") instanceof StringObject) {
                defaultDirectory = ((StringObject) library.getObject(entries, "D"))
                        .getDecryptedLiteralString(library.getSecurityManager());
            }

            if (library.getObject(entries, "O") instanceof StringObject) {
                operation = ((StringObject) library.getObject(entries, "O"))
                        .getDecryptedLiteralString(library.getSecurityManager());
            }

            if (library.getObject(entries, "P") instanceof StringObject) {
                parameters =
                        ((StringObject) library.getObject(entries, "P"))
                                .getLiteralString();
            }
        }

        /**
         * Gets the file name of the application to be launched or the document
         * to be opened or printed, in standard Windows pathname format.
         *
         * @return fiel or application to launch
         */
        public String getLaunchFile() {
            return launchFile;
        }

        /**
         * Gets a string specifying the default directory in standard DOS
         * syntax(Optional).
         *
         * @return default directory.
         */
        public String getDefaultDirectory() {
            return defaultDirectory;
        }

        /**
         * Indicates the operation to perform (Optional).
         *
         * @return opertation to perform, either "open" or "print".
         */
        public String getOperation() {
            return operation;
        }

        /**
         * Gets a parameter string to be passed to the application designated by
         * the fileName entry.(Optional).
         *
         * @return paramater string associated with this action
         */
        public String getParameters() {
            return parameters;
        }

        /**
         * Gets the file specification of the destination file.  This objects should
         * be interigated to deside what should be done
         *
         * @return file specification, maybe nukll if external file was specified.
         */
        public FileSpecification getLaunchFileSpecification() {
            return launchFileSpecification;
        }
    }
}
