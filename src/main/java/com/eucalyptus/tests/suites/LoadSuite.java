package com.eucalyptus.tests.suites;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;
import com.eucalyptus.tests.load.EbsInstanceChurnLoadTest;
import com.eucalyptus.tests.load.InstanceChurnLoadTest;
import com.eucalyptus.tests.load.LoadBalancerChurnLoadTest;
import com.eucalyptus.tests.load.ObjectChurnLoadTest;
import com.eucalyptus.tests.load.StackChurnLoadTest;

@RunWith(Suite.class)
@SuiteClasses({
    // init
    InitializationSuite.class,
    EbsImageSuite.class,

    // load
    InstanceChurnLoadTest.class,
    LoadBalancerChurnLoadTest.class,
    ObjectChurnLoadTest.class,
    StackChurnLoadTest.class,
    EbsInstanceChurnLoadTest.class,
})
public class LoadSuite {
  // junit test suite as defined by SuiteClasses annotation
}
