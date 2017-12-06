package com.eucalyptus.tests.suites;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;
import com.eucalyptus.tests.awssdk.*;

/**
 * Suite to configure cloud for tests
 */
@RunWith(Suite.class)
@SuiteClasses({
    InitializationTest.class,
    TestEC2ImageRegistration.class
})
public class InitializationSuite {
  // junit test suite as defined by SuiteClasses annotation
}
