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
    TestEC2VPCDnsNames.class,
    TestEC2VPCElasticIPs.class,
    TestEC2VPCFilteringNonVPCResources.class,
    TestEC2VPCModifyInstanceSecurityGroups.class,
    TestEC2VPCNATGatewayManagement.class,
    TestEC2VPCNetworkInterfaceAttach.class,
    TestEC2VPCQuotasLimits.class,
    TestEC2VPCResourceConditionPolicy.class,
    TestEC2VPCRoutes.class,
    TestEC2VPCRunInstanceNetworkInterfaces.class,
    TestEC2VPCSecurityGroupEgressRules.class,
    TestEC2VPCSecurityGroups.class,
    TestEC2VPCSecurityGroupsInstancesAttributes.class,
    TestEC2VPCStartStop.class,
    TestEC2VPCTaggingFiltering.class,
})
public class Ec2VpcSuite {
  // junit test suite as defined by SuiteClasses annotation
}
