/*
 * Copyright 2006-2019 ICEsoft Technologies Canada Corp.
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
package org.icepdf.core.pobjects.graphics;

import org.icepdf.core.pobjects.Name;
import org.icepdf.core.pobjects.functions.Function;
import org.icepdf.core.util.Library;

import java.awt.*;
import java.util.HashMap;
import java.util.List;

/**
 * DeviceN colour spaces shall be defined in a similar way to Separation colour
 * spaces-in fact, a Separationcolour space can be defined as a DeviceN colour
 * space with only one component.
 * <br>
 * A DeviceN colour space shall be specified as follows:
 * [/DeviceN names alternateSpace tintTransform]
 * or
 * [/DeviceN names alternateSpace tintTransform attributes]
 * <br>
 * It is a four- or five-element array whose first element shall be the colour
 * space family name DeviceN. The remaining elements shall be parameters that a
 * DeviceN colour space requires.
 */
public class DeviceN extends PColorSpace {

    static final Name DEVICEN_KEY = new Name("DeviceN");
    static final Name COLORANTS_KEY = new Name("Colorants");
    public static final Name PROCESS_KEY = new Name("Process");
    public static final Name SUBTYPE_KEY = new Name("Subtype");
    public static final Name DEVICEN_SUB_TYPE_KEY = new Name("DeviceN");
    public static final Name NCHANNEL_SUB_TYPE_KEY = new Name("NChannel");

    private static DeviceCMYK deviceCMYK = new DeviceCMYK(null, null);
    private List<Name> names;
    private PColorSpace alternate;
    private Function tintTransform;
    // for debugging purposes, not currently used.
    private HashMap attributesDictionary;
    private HashMap processDictionary;

    private boolean foundCMYKColorants;

    @SuppressWarnings("unchecked")
    DeviceN(Library l, HashMap h, Object names, Object alternativeSpace, Object tintTransform, Object attributes) {
        super(l, h);
        this.names = (java.util.List) names;
        alternate = getColorSpace(l, alternativeSpace);
        this.tintTransform = Function.getFunction(l, l.getObject(tintTransform));
        int cmykCount = 0;
        for (Name name : this.names) {
            if (name.getName().startsWith("Cyan") ||
                    name.getName().startsWith("Magenta") ||
                    name.getName().startsWith("Yellow") ||
                    name.getName().startsWith("Black")) {
                cmykCount++;
            }
        }
        if (cmykCount == 4) {
            foundCMYKColorants = true;
        }

        // attributes are required for defining NChannel
        if (attributes != null) {
            attributesDictionary = (HashMap) library.getObject(attributes);
            // setup process
            processDictionary = (HashMap) library.getObject(attributesDictionary, PROCESS_KEY);
        }
    }

    public int getNumComponents() {
        return names.size();
    }

    private float[] assignCMYK(float[] f) {
        float[] f2 = new float[4];
        Name name;
        for (int i = 0, max = names.size(); i < max; i++) {
            name = names.get(i);
            if (name.getName().toLowerCase().startsWith("c")) {
                f2[0] = i < f.length ? f[i] : 0;
            } else if (name.getName().toLowerCase().startsWith("m")) {
                f2[1] = i < f.length ? f[i] : 0;
            } else if (name.getName().toLowerCase().startsWith("y")) {
                f2[2] = i < f.length ? f[i] : 0;
            } else if (name.getName().toLowerCase().startsWith("b") ||
                    name.getName().toLowerCase().startsWith("k")) {
                f2[3] = i < f.length ? f[i] : 0;
            }
        }
        return f2;
    }

    public Color getColor(float[] f, boolean fillAndStroke) {
        // calculate cmyk color
        if (foundCMYKColorants && f.length == 4) {
            f = assignCMYK(f);
            return deviceCMYK.getColor(f);
        } else {
            // trim color to match colourant length.
            int size = names.size();
            if (f.length != names.size()) {
                float[] tmp = new float[size];
                System.arraycopy(f, 0, tmp, 0, size);
                f = tmp;
            }
            float[] y = tintTransform.calculate(f);
            return alternate.getColor(y);
        }
    }
}