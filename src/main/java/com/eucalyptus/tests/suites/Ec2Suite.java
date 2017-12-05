package com.eucalyptus.tests.suites;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;
import com.eucalyptus.tests.awssdk.*;

@RunWith(Suite.class)
@SuiteClasses({
    TestEC2DescribeInstanceStatus.class,
})
public class Ec2Suite {
  // junit test suite as defined by SuiteClasses annotation
}
