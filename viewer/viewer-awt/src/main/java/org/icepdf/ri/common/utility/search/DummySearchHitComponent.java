package org.icepdf.ri.common.utility.search;

/**
 * Dummy search hit component not doing anything
 */
public class DummySearchHitComponent extends SearchHitComponent {
    protected DummySearchHitComponent(String text) {
        super(text);
    }

    @Override
    void doAction() {

    }

    @Override
    void showMenu() {

    }
}
