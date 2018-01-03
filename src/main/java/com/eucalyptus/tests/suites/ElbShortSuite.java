package com.eucalyptus.tests.suites;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;
import com.eucalyptus.tests.awssdk.*;

@RunWith(Suite.class)
@SuiteClasses({
    TestELBAttributes.class,
    TestELBIAMResource.class,
    TestELBTagging.class,
})
public class ElbShortSuite {
  // junit test suite as defined by SuiteClasses annotation
}
