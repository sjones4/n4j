package com.eucalyptus.tests.suites;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;

@RunWith(Suite.class)
@SuiteClasses({
    AutoScalingShortSuite.class,
    CloudFormationSuite.class,
    CloudWatchSuite.class,
    Ec2Suite.class,
    ElbSuite.class,
    IamSuite.class,
    S3Suite.class,
})
public class AllShortSuite {
  // junit test suite as defined by SuiteClasses annotation
}
