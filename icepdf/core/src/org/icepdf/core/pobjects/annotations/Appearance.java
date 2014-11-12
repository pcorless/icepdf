/*
 * Copyright 2006-2014 ICEsoft Technologies Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an "AS
 * IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */
package org.icepdf.core.pobjects.annotations;

import org.icepdf.core.io.SeekableInputConstrainedWrapper;
import org.icepdf.core.pobjects.Stream;
import org.icepdf.core.util.Library;

import java.util.HashMap;

/**
 * <h2>Refer to: 8.4.4 Appearance Streams</h2>
 * <p/>
 * <br>
 * An annotation can define as many as three separate appearances:
 * <ul>
 * <li> The normal appearance is used when the annotation is not interacting with the
 * user. This appearance is also used for printing the annotation.</li>
 * <li> The rollover appearance is used when the user moves the cursor into the annotation's
 * active area without pressing the mouse button.</li>
 * <li> The down appearance is used when the mouse button is pressed or held down
 * within the annotation's active area.</li>
 * </ul>
 * <p/>
 * <table border=1>
 * <tr>
 * <td>Key</td>
 * <td>Type</td>
 * <td>Value</td>
 * </tr>
 * <tr>
 * <td><b>N</b></td>
 * <td>stream or dictionary</td>
 * <td><i>(Required)</i> The annotation's normal appearance</td>
 * </tr>
 * <tr>
 * <td><b>R</b></td>
 * <td>stream or dictionary</td>
 * <td><i>(Optional)</i> The annotation's rollover appearance. Default value: the value of
 * the <b>N</b> entry.</td>
 * </tr>
 * <tr>
 * <td><b>D</b></td>
 * <td>stream or dictionary</td>
 * <td><i>(Optional)</i> The annotation's down appearance. Default value: the value of the
 * <b>N</b> entry.</td>
 * </tr>
 * </table>
 *
 * @author Mark Collette
 * @since 2.5
 */
public class Appearance extends Stream {
    /**
     * Create a new instance of an Appearance stream.
     *
     * @param l                  library containing a hash of all document objects
     * @param h                  HashMap of parameters specific to the Stream object.
     * @param streamInputWrapper Accessor to stream byte data
     */
    public Appearance(Library l, HashMap h, SeekableInputConstrainedWrapper streamInputWrapper) {
        super(l, h, streamInputWrapper);
    }
}
