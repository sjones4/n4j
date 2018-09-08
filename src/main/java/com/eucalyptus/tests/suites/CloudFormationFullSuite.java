package com.eucalyptus.tests.suites;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;
import com.eucalyptus.tests.awssdk.TestCFTemplateLifecycle;
import com.eucalyptus.tests.awssdk.TestCFTemplatesFull;

@RunWith(Suite.class)
@SuiteClasses({
    // suites
    CloudFormationShortSuite.class,

    //tests
    TestCFTemplateLifecycle.class,
    TestCFTemplatesFull.class,
})
public class CloudFormationFullSuite {
  // junit test suite as defined by SuiteClasses annotation
}
