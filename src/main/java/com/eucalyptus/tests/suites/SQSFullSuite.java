package com.eucalyptus.tests.suites;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;
import com.eucalyptus.tests.awssdk.TestSQSCloudWatchMetrics;
import com.eucalyptus.tests.awssdk.TestSQSCrossAccountStackPolicies;
import com.eucalyptus.tests.awssdk.TestSQSDeadLetterQueue;
import com.eucalyptus.tests.awssdk.TestSQSIAMPolicies;
import com.eucalyptus.tests.awssdk.TestSQSLongPolling;


/**
 *
 */
@RunWith(Suite.class)
@SuiteClasses({
    // suites
    SQSShortSuite.class,

    // tests
    TestSQSCloudWatchMetrics.class,
    TestSQSCrossAccountStackPolicies.class,
    TestSQSDeadLetterQueue.class,
    TestSQSIAMPolicies.class,
    TestSQSLongPolling.class,
})
public class SQSFullSuite {
  // junit test suite as defined by SuiteClasses annotation       
}
