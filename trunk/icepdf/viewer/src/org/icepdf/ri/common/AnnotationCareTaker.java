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
package org.icepdf.ri.common;

import org.icepdf.core.pobjects.annotations.AnnotationState;

import java.util.ArrayList;

/**
 * Annotation manipulation undo mechanism.
 * todo: work in progress.
 *
 * @since 4.0
 */
public class AnnotationCareTaker {

    private static final int MAX_HISTORY_SIZE = 15;

    private ArrayList<AnnotationState> annotationStateHistory;
    private int statePointer;

    public AnnotationCareTaker() {
        annotationStateHistory = new ArrayList<AnnotationState>(MAX_HISTORY_SIZE);
        statePointer = 0;
    }

    public void undo(){
        if (isUndo()){
            AnnotationState tmp = annotationStateHistory.get(statePointer);
            // restore the old state
            tmp.restore();
            // move the point reference
            if (statePointer - 1 >= 0){
                statePointer = statePointer - 1;
            }
        }
    }

    public boolean isUndo(){
        return annotationStateHistory.size() > 0;
    }

    public void redo(){
        if (isRedo()){
            AnnotationState tmp = annotationStateHistory.get(statePointer + 1);
            // restore the old state
            tmp.restore();
            // move the pointer
            statePointer = statePointer + 1;
        }
    }

    public boolean isRedo(){
        // check for at least one history state in the next index.
        return statePointer + 1  < annotationStateHistory.size();
    }

    public void addState(AnnotationState annotationState){
        // first check history bounds, if we are in an none
        if (statePointer + 1 >= MAX_HISTORY_SIZE){
            // get rid of first index.
            annotationStateHistory.remove(0);
        }
        // check to see if we are in a possible redo state, if so we clear
        // all states from the current pointer.
        if (isRedo()){
            for (int i = statePointer+1; i < annotationStateHistory.size(); i++){
                annotationStateHistory.remove(i);
            }
        }
        // add the new state to the list
        annotationStateHistory.add(annotationState);
        statePointer = annotationStateHistory.size()-1;
    }
}
