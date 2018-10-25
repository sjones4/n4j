package com.eucalyptus.tests.suites;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;
import com.eucalyptus.tests.awssdk.TestSQSCloudWatchMetrics;


/**
 *
 */
@RunWith(Suite.class)
@SuiteClasses({
    // suites
    SQSFullSuite.class, // includes short suite

    // tests
    TestSQSCloudWatchMetrics.class, // 10 min test too long for full suite
})
public class SQSAllSuite {
  // junit test suite as defined by SuiteClasses annotation       
}
