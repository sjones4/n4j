package com.eucalyptus.tests.suites;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;
import com.eucalyptus.tests.awssdk.*;

@RunWith(Suite.class)
@SuiteClasses({
    // suites
    Ec2ShortSuite.class,

    // tests
    TestEC2IAMConditionKeys.class,
    TestEC2InstanceBundle.class,
    TestEC2InstanceProfile.class,
    TestEC2InstanceTerminationProtection.class,
    TestEC2LongIdentifiers.class,
    TestEC2RunInstancesClientToken.class,
})
public class Ec2FullSuite {
  // junit test suite as defined by SuiteClasses annotation
}
