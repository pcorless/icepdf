package org.icepdf.qa.tests;

import org.icepdf.qa.tests.exceptions.ConfigurationException;
import org.icepdf.qa.tests.exceptions.TestException;
import org.icepdf.qa.tests.exceptions.ValidationException;

/**
 *
 */
public interface TestInterface {

    void setup();

    void validate() throws ValidationException;

    void config() throws ConfigurationException;

    void testAndAnalyze() throws TestException;

    void teardown();
}
