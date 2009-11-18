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
            for (int i = statePointer; i < MAX_HISTORY_SIZE; i++){
                annotationStateHistory.set(i, null);
            }
        }
        // add the new state to the list
        annotationStateHistory.add(annotationState);
        statePointer = annotationStateHistory.size()-1;
    }
}
