package com.eucalyptus.tests.suites;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;
import com.eucalyptus.tests.awssdk.*;

@RunWith(Suite.class)
@SuiteClasses({
    TestEC2VPCAssociationManagement.class,
    TestEC2VPCAttributeManagement.class,
    TestEC2VPCDefaultVPC.class,
    TestEC2VPCManagement.class,
    TestEC2VPCNetworkAclEntryManagement.class,
    TestEC2VPCNetworkInterfaces.class,
    TestEC2VPCSubnetAvailableAddresses.class,
    TestEC2VPCValidation.class,
    TestELBDefaultVPC.class,
    TestELBVPC.class,
})
public class Ec2VpcSuite {
  // junit test suite as defined by SuiteClasses annotation
}
