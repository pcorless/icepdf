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
 * The Original Code is ICEpdf 4.1 open source software code, released
 * May 1st, 2009. The Initial Developer of the Original Code is ICEsoft
 * Technologies Canada, Corp. Portions created by ICEsoft are Copyright (C)
 * 2004-2010 ICEsoft Technologies Canada, Corp. All Rights Reserved.
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
package org.icepdf.ri.util;

import java.io.*;
import java.net.ConnectException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;

/**
 * This utility class simplifies the process of loading URLs.
 *
 * @since 1.3
 */
public class URLAccess {
    /**
     * Simple utility method to check if a URL is valid.
     * If it is invalid, then urlAccess.errorMessage will say why.
     * If it is valid, then urlAccess.url will be a valid URL object,
     * and urlAccess.inputStream will be opened to access the data from the URL.
     *
     * @param urlLocation
     * @return URLAccess urlAccess
     */
    public static URLAccess doURLAccess(String urlLocation) {
        URLAccess res = new URLAccess();
        res.urlLocation = urlLocation;
        try {
            res.url = new URL(urlLocation);
            // check to make sure stream is good
            // If url is http, then we might just get a 404 web page
            PushbackInputStream in = new PushbackInputStream(
                    new BufferedInputStream(res.url.openStream()),
                    1);
            int b = in.read();
            in.unread(b);
            res.inputStream = in;
        } catch (MalformedURLException e) {
            res.errorMessage = "Malformed URL";
        } catch (FileNotFoundException e) {
            res.errorMessage = "File Not Found";
        } catch (UnknownHostException e) {
            res.errorMessage = "Unknown Host";
        } catch (ConnectException e) {
            res.errorMessage = "Connection Timed Out";
        } catch (IOException e) {
            res.errorMessage = "IO exception";
        }
        return res;
    }


    /**
     * The given URL string given to doURLAccess().
     */
    public String urlLocation;

    /**
     * The resolved URL, if urlLocation was valid.
     */
    public URL url;

    /**
     * Access to the data at the URL, if the URL was valid.
     */
    public InputStream inputStream;

    /**
     * The reason why the URL was invalid.
     */
    public String errorMessage;

    private URLAccess() {
    }

    /**
     * Free up any resources used, immediately.
     */
    public void dispose() {
        urlLocation = null;
        url = null;
        errorMessage = null;
        closeConnection();
    }

    /**
     * Close the connection, but keep all the String
     * information about the connection.
     */
    public void closeConnection() {
        if (inputStream != null) {
            try {
                inputStream.close();
            }
            catch (Exception e) {
            }
            inputStream = null;
        }
    }
}
