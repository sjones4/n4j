package com.eucalyptus.tests.suites;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;
import com.eucalyptus.tests.awssdk.Ec2CleanupTest;
import com.eucalyptus.tests.awssdk.S3CleanupTest;

@RunWith(Suite.class)
@SuiteClasses({
    Ec2CleanupTest.class,
    S3CleanupTest.class,
})
public class CleanupSuite {
  // junit test suite as defined by SuiteClasses annotation
}
