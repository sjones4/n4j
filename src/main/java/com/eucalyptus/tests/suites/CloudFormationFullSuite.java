package com.eucalyptus.tests.suites;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;
import com.eucalyptus.tests.awssdk.TestCFAdministration;
import com.eucalyptus.tests.awssdk.TestCFTemplatesFull;
import com.eucalyptus.tests.awssdk.TestCFTemplatesShort;

@RunWith(Suite.class)
@SuiteClasses({
    // suites
    CloudFormationShortSuite.class,

    //tests
    TestCFTemplatesFull.class,
})
public class CloudFormationFullSuite {
  // junit test suite as defined by SuiteClasses annotation
}
