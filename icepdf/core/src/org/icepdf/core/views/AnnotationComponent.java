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
 * 2004-2011 ICEsoft Technologies Canada, Corp. All Rights Reserved.
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
package org.icepdf.core.views;

import org.icepdf.core.pobjects.annotations.Annotation;
import org.icepdf.core.pobjects.Document;

/**
 * AnnotationComponent interfaces.  Oulines two main methods needed for
 * management and state saving but avoids having to load the Swing/awt libraries
 * unless necessary.
 *
 * @since 4.0
 */
public interface AnnotationComponent {

    /**
     * Gets wrapped annotation object.
     *
     * @return annotation that this component wraps.
     */
    public Annotation getAnnotation();

    /**
     * Refreshs the annotations bounds rectangle.  This method insures that
     * the bounds have been correctly adjusted for the current page transformation
     * In a none visual representation this method may not have to do anything.
     */
    public void refreshDirtyBounds();

    /**
     * Refreshed the annotation rectangle by inverting the components current
     * bounds with the current page transformation.
     */
    public void refreshAnnotationRect();

    /**
     * Component has focus.
     *
     * @return true if has focus, false otherwise.
     */
    public boolean hasFocus();

     public boolean isEditable() ;

    public boolean isRollover();

    public boolean isLinkAnnot();

    public boolean isBorderStyle() ;

    public boolean isSelected();

    public Document getDocument();

    public int getPageIndex();

    public PageViewComponent getParentPageView();

}
