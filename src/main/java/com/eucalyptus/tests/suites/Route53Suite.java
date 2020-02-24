package com.eucalyptus.tests.suites;

/**
 *
 */

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;
import com.eucalyptus.tests.awssdk.TestRoute53Administration;
import com.eucalyptus.tests.awssdk.TestRoute53Api;
import com.eucalyptus.tests.awssdk.TestRoute53IamPolicy;

@RunWith(Suite.class)
@SuiteClasses({
    TestRoute53Administration.class,
    TestRoute53Api.class,
    TestRoute53IamPolicy.class,
})
public class Route53Suite {
  // junit test suite as defined by SuiteClasses annotation
}
