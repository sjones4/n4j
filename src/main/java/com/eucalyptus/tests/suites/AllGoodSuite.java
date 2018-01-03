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
    CloudFormationSuite.class,
    CloudWatchSuite.class,
    Ec2Suite.class,
    ElbFullSuite.class,
    IamSuite.class,
    S3Suite.class,
})
public class AllGoodSuite {
  // junit test suite as defined by SuiteClasses annotation
}