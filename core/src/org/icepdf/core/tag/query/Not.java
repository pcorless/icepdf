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
package org.icepdf.core.tag.query;

import org.icepdf.core.tag.TaggedDocument;
import org.icepdf.core.tag.TaggedImage;

/**
 * @author mcollette
 * @since 4.0
 */
public class Not extends Operator {
    public int getArgumentCount() {
        return 1;
    }

    public boolean matches(TaggedDocument td, TaggedImage ti, String tag) {
        return !childExpressions[0].matches(td, ti, tag);
    }
}
