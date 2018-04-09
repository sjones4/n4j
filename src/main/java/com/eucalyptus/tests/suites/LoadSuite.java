package com.eucalyptus.tests.suites;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;
import com.eucalyptus.tests.load.InstanceChurnLoadTest;

@RunWith(Suite.class)
@SuiteClasses({
    InstanceChurnLoadTest.class,
})
public class LoadSuite {
  // junit test suite as defined by SuiteClasses annotation
}
