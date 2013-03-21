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

import org.icepdf.core.pobjects.Name;
import org.icepdf.core.pobjects.functions.Function;
import org.icepdf.core.util.Library;

import java.awt.*;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

/**
 * DeviceN colour spaces shall be defined in a similar way to Separation colour
 * spaces-in fact, a Separationcolour space can be defined as a DeviceN colour
 * space with only one component.
 * <p/>
 * A DeviceN colour space shall be specified as follows:
 * [/DeviceN names alternateSpace tintTransform]
 * or
 * [/DeviceN names alternateSpace tintTransform attributes]
 * <p/>
 * It is a four- or five-element array whose first element shall be the colour
 * space family name DeviceN. The remaining elements shall be parameters that a
 * DeviceN colour space requires.
 */
public class DeviceN extends PColorSpace {

    public static final Name DEVICEN_KEY = new Name("DeviceN");
    public static final Name COLORANTS_KEY = new Name("Colorants");

    List<Name> names;
    PColorSpace alternate;
    Function tintTransform;
    HashMap<Object, Object> colorants = new HashMap<Object, Object>();
    PColorSpace colorspaces[];

    boolean foundCMYK;

    DeviceN(Library l, HashMap h, Object o1, Object o2, Object o3, Object o4) {
        super(l, h);
        names = (java.util.List) o1;
        alternate = getColorSpace(l, o2);
        tintTransform = Function.getFunction(l, l.getObject(o3));
        if (o4 != null) {
            HashMap h1 = (HashMap) library.getObject(o4);
            HashMap h2 = (HashMap) library.getObject(h1, COLORANTS_KEY);
            if (h2 != null) {
                Set e = h2.keySet();
                Object oo;
                for (Object o : e) {
                    oo = h2.get(o);
                    colorants.put(o, getColorSpace(library, library.getObject(oo)));
                }
            }
        }
        colorspaces = new PColorSpace[names.size()];
        for (int i = 0; i < colorspaces.length; i++) {
            colorspaces[i] = (PColorSpace) colorants.get(names.get(i).toString());
        }
        // check to see if cymk is specified int the names, if so we can
        // uses the cmyk colour space directly, otherwise we fallback to the alternative
        // and hope it was setup correctly.
        if (names.size() == 4) {
            int cmykCount = 0;
            for (Name name : names) {
                if (name.getName().toLowerCase().startsWith("c")) {
                    cmykCount++;
                } else if (name.getName().toLowerCase().startsWith("m")) {
                    cmykCount++;
                } else if (name.getName().toLowerCase().startsWith("y")) {
                    cmykCount++;
                } else if (name.getName().toLowerCase().startsWith("b")) {
                    cmykCount++;
                }
                if (cmykCount == 4) {
                    foundCMYK = true;
                }
            }
        }
    }

    public int getNumComponents() {
        return names.size();
    }

    public Color getColor(float[] f) {
        if (foundCMYK) {
            return new DeviceCMYK(null, null).getColor(f);
        } else {
            float y[] = tintTransform.calculate(f);
            return alternate.getColor(reverse(y));
        }
    }
}



