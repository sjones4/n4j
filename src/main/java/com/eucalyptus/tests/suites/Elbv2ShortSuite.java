package com.eucalyptus.tests.suites;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;
import com.eucalyptus.tests.awssdk.*;

@RunWith(Suite.class)
@SuiteClasses({
    TestELBv2Api.class,
    TestELBv2Attributes.class,
    TestELBv2Tagging.class,
})
public class Elbv2ShortSuite {
  // junit test suite as defined by SuiteClasses annotation
}
