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

package org.icepdf.core.pobjects.actions;

import org.icepdf.core.util.Library;

import java.util.HashMap;

/**
 * @since 5.1
 */
public class SubmitFormAction extends Action implements FormAction {

    public int INCLUDE_EXCLUDE_BIT = 0X0000001;

    public SubmitFormAction(Library l, HashMap h) {
        super(l, h);
    }

    public int executeFormAction() {
        return 0;
    }
}
