package com.eucalyptus.tests.suites;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;
import com.eucalyptus.tests.awssdk.*;

@RunWith(Suite.class)
@SuiteClasses({
    TestAdminRoles.class,
    TestCannedRoles.class,
    TestIAMInstanceProfileManagement.class,
    TestIAMInstanceProfiles.class,
    TestIAMRoleManagement.class,
    TestSTSAssumeRole.class,
    TestSTSGetAccessToken.class,
    TestSTSGetImpersonationToken.class,
})
public class IamSuite {
  // junit test suite as defined by SuiteClasses annotation
}
