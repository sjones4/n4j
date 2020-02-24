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
    Ec2VpcFullSuite.class,
    ElbFullSuite.class,
    IamSuite.class,
    Route53Suite.class,
    S3FullSuite.class,
    SQSFullSuite.class,
})
public class AllGoodSuite {
  // junit test suite as defined by SuiteClasses annotation
}
