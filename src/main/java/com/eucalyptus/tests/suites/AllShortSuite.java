package com.eucalyptus.tests.suites;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;

/**
 * Quick tests that should be run before merging.
 */
@RunWith(Suite.class)
@SuiteClasses({
    InitializationSuite.class,
    AutoScalingShortSuite.class,
    CloudFormationShortSuite.class,
    CloudWatchSuite.class,
    Ec2ShortSuite.class,
    ElbShortSuite.class,
    IamSuite.class,
    ServicesSuite.class,
    S3ShortSuite.class,
    SQSShortSuite.class,
})
public class AllShortSuite {
  // junit test suite as defined by SuiteClasses annotation
}
