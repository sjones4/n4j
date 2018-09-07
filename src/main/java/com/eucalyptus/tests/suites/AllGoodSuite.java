package com.eucalyptus.tests.suites;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;

/**
 * Tests that take too long for the short suite
 */
@RunWith(Suite.class)
@SuiteClasses({
    InitializationSuite.class,
    AutoScalingFullSuite.class,
    CloudFormationFullSuite.class,
    CloudWatchSuite.class,
    Ec2FullSuite.class,
    ElbFullSuite.class,
    IamSuite.class,
    S3FullSuite.class,
    SQSFullSuite.class,
})
public class AllGoodSuite {
  // junit test suite as defined by SuiteClasses annotation
}
