package com.eucalyptus.tests.suites;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;
import com.eucalyptus.tests.awssdk.*;

@RunWith(Suite.class)
@SuiteClasses({
    TestEC2Api.class,
    TestEC2DescribeInstanceStatus.class,
    TestEC2Ebs.class,
})
public class Ec2ShortSuite {
  // junit test suite as defined by SuiteClasses annotation
}
