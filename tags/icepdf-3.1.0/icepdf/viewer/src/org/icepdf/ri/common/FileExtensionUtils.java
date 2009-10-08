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
package org.icepdf.ri.common;

import javax.swing.filechooser.FileFilter;
import java.io.File;

/**
 * <p>Utility class for creating file extension file filters.
 *
 * @since 2.0
 */
public class FileExtensionUtils {
    public final static String pdf = "pdf";
    public final static String svg = "svg";
    public final static String ps = "ps";
    public final static String txt = "txt";

    public static FileFilter getPDFFileFilter() {
        return new ExtensionFileFilter("Adobe PDF Files (*.pdf)", pdf);
    }

    public static FileFilter getTextFileFilter() {
        return new ExtensionFileFilter("Text Files (*.txt)", txt);
    }

    public static FileFilter getSVGFileFilter() {
        return new ExtensionFileFilter("SVG Files (*.svg)", svg);
    }

    public static String getExtension(File f) {
        String ext = null;
        String s = f.getName();
        int i = s.lastIndexOf('.');

        if (i > 0 && i < s.length() - 1) {
            ext = s.substring(i + 1).toLowerCase();
        }
        return ext;
    }


    private static class ExtensionFileFilter extends FileFilter {
        private String description;
        private String extension;

        ExtensionFileFilter(String desc, String ext) {
            description = desc;
            extension = ext;
        }

        //Accept all directories and all files with extension
        public boolean accept(File f) {
            if (f.isDirectory())
                return true;

            String ext = FileExtensionUtils.getExtension(f);
            if (ext != null) {
                if (ext.equals(extension))
                    return true;
                else
                    return false;
            }
            return false;
        }

        //The description of this filter
        public String getDescription() {
            return description;
        }
    }
}
