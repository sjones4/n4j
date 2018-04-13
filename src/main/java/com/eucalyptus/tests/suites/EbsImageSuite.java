package com.eucalyptus.tests.suites;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;
import com.eucalyptus.tests.awssdk.TestEC2EbsImageRegistration;
import com.eucalyptus.tests.awssdk.UpTest;

/**
 * Suite to register an ebs image
 */
@RunWith(Suite.class)
@SuiteClasses({
    UpTest.class,
    TestEC2EbsImageRegistration.class
})
public class EbsImageSuite {
  // junit test suite as defined by SuiteClasses annotation
}
