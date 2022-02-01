package org.icepdf.qa.tests;

import org.icepdf.qa.viewer.common.Mediator;

/**
 *
 */
public class TestFactory {

    private static TestFactory testFactory;

    private TestFactory() {
    }

    public static TestFactory getInstance() {
        if (testFactory == null) {
            testFactory = new TestFactory();
        }
        return testFactory;
    }

    public AbstractTestTask createTestInstance(Mediator mediator) {
        return new ImageCompareTask(mediator);
    }
}
