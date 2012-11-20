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
package org.icepdf.core.views.swing;

import org.icepdf.core.pobjects.Page;
import org.icepdf.core.views.PageViewComponent;

import javax.swing.*;
import javax.swing.event.MouseInputListener;

/**
 * Abstract PageViewComponent.
 */
public abstract class AbstractPageViewComponent
        extends JComponent
        implements PageViewComponent, MouseInputListener {

    public abstract Page getPage();

}
