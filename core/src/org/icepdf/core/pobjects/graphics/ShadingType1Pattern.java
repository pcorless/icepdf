/*
 * Copyright 2006-2012 ICEsoft Technologies Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an "AS
 * IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either * express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */
package org.icepdf.core.pobjects.graphics;

import org.icepdf.core.pobjects.functions.Function;
import org.icepdf.core.util.Library;

import java.awt.*;
import java.util.Hashtable;
import java.util.Vector;

/**
 * <p>Currently not support, type 1 algorithm defines a colour for each
 * coordinate in the space which will be difficult to calculate efficiently.
 * However the background attribute could be used as a fill colour in some
 * circumstances.  Once we get a few test cases we can give it a try. </p>
 *
 * @author ICEsoft Technologies Inc.
 * @since 3.0
 */
public class ShadingType1Pattern extends ShadingPattern {

    // domain, optional, array of four numbers
    protected Vector domain;

    protected Vector matrix;

    protected Function function;

    public ShadingType1Pattern(Library library, Hashtable entries) {
        super(library, entries);
    }

    public synchronized void init() {

        // get type 3 specific data.
        inited = true;
    }

    /**
     * Not implemented
     *
     * @return will always return null;
     */
    public Paint getPaint() {
        return null;
    }


    public String toSting() {
        return super.toString() +
                "\n                    domain: " + domain +
                "\n                    matrix: " + matrix +
                "\n                 function: " + function;
    }
}
