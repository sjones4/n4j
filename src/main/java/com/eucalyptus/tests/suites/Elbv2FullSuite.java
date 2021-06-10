package com.eucalyptus.tests.suites;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;
import com.eucalyptus.tests.awssdk.*;

@RunWith(Suite.class)
@SuiteClasses({
    // suites
    Elbv2ShortSuite.class,

    // tests
})
public class Elbv2FullSuite {
  // junit test suite as defined by SuiteClasses annotation
}
