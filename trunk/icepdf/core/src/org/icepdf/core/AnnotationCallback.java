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
package org.icepdf.core;

import org.icepdf.core.pobjects.Page;
import org.icepdf.core.pobjects.annotations.Annotation;
import org.icepdf.core.views.PageViewComponent;

import java.awt.*;

/**
 * <p>Annotation callback allows developers to control how Annotation and
 * their actions are executed.  Developers also have have the option to
 * change annotation visibility attributes such as border style, border color
 * and border stroke width before the annotation is painted.</p>
 *
 * @author ICEsoft Technologies, Inc.
 * @see org.icepdf.core.views.DocumentViewController#setAnnotationCallback(AnnotationCallback)
 * @since 2.6
 */
public interface AnnotationCallback {

    /**
     * <p>Implemented Annotation Callback method.  When an annotation is activated
     * in a PageViewComponent it passes the annotation to this method for
     * processing.  The PageViewComponent take care of drawing the annotation
     * states but it up to this method to process the annotation.</p>
     *
     * @param annotation annotation that was activated by a user via the
     *                   PageViewComponent.
     */
    public void proccessAnnotationAction(Annotation annotation);

    /**
     * <p>Implemented Annotation Callback method.  This method is called when a
     * pages annotations been initialized but before the page has been painted.
     * This method blocks the </p>
     *
     * @param page page that has been initialized.  The pages annotations are
     *             available via an accessor method.
     */
    public void pageAnnotationsInitialized(Page page);

    /**
     * New annotation created with view tool.
     * @param page page that annotation was added to.
     * @param rect new annotation bounds.
     */
    public void newAnnotation(PageViewComponent page, Rectangle rect);
    
}
