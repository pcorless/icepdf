package com.icepdf.fx.scene.control.behavior;

import com.icepdf.fx.scene.control.DocumentView;
import com.sun.javafx.scene.control.behavior.BehaviorBase;
import com.sun.javafx.scene.control.behavior.KeyBinding;

import java.util.ArrayList;
import java.util.List;

import static javafx.scene.input.KeyCode.PAGE_DOWN;
import static javafx.scene.input.KeyCode.PAGE_UP;

/**
 *
 */
public class DocumentViewBehavior extends BehaviorBase<DocumentView> {

    protected static final List<KeyBinding> DOCUMENT_VIEW_BINDINGS = new ArrayList<KeyBinding>();

    static {
        DOCUMENT_VIEW_BINDINGS.add(new KeyBinding(PAGE_UP, "ScrollUp"));
        DOCUMENT_VIEW_BINDINGS.add(new KeyBinding(PAGE_DOWN, "ScrollDown"));
    }

    public DocumentViewBehavior(DocumentView control) {
        super(control, DOCUMENT_VIEW_BINDINGS);
    }
}
