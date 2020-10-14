package com.eucalyptus.tests.suites;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;
import com.eucalyptus.tests.awssdk.TestDns;

/**
 * Suite for general DNS tests
 */
@RunWith(Suite.class)
@SuiteClasses({
    TestDns.class
})
public class DnsSuite {
  // junit test suite as defined by SuiteClasses annotation
}
