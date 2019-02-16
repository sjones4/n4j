package com.eucalyptus.tests.suites;

import java.util.List;
import org.junit.BeforeClass;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;
import com.amazonaws.services.ec2.model.AccountAttribute;
import com.amazonaws.services.ec2.model.AccountAttributeValue;
import com.eucalyptus.tests.awssdk.N4j;
import com.eucalyptus.tests.awssdk.TestEC2VPCAssociationManagement;
import com.eucalyptus.tests.awssdk.TestEC2VPCAttributeManagement;
import com.eucalyptus.tests.awssdk.TestEC2VPCDefaultVPC;
import com.eucalyptus.tests.awssdk.TestEC2VPCElasticIPs;
import com.eucalyptus.tests.awssdk.TestEC2VPCManagement;
import com.eucalyptus.tests.awssdk.TestEC2VPCNetworkAclEntryManagement;
import com.eucalyptus.tests.awssdk.TestEC2VPCNetworkInterfaces;
import com.eucalyptus.tests.awssdk.TestEC2VPCQuotasLimits;
import com.eucalyptus.tests.awssdk.TestEC2VPCSecurityGroupEgressRules;
import com.eucalyptus.tests.awssdk.TestEC2VPCSecurityGroups;
import com.eucalyptus.tests.awssdk.TestEC2VPCSecurityGroupsInstancesAttributes;
import com.eucalyptus.tests.awssdk.TestEC2VPCSubnetAvailableAddresses;
import com.eucalyptus.tests.awssdk.TestEC2VPCTaggingFiltering;
import com.eucalyptus.tests.awssdk.TestEC2VPCValidation;

@RunWith(Suite.class)
@SuiteClasses({
    TestEC2VPCAssociationManagement.class,
    TestEC2VPCAttributeManagement.class,
    TestEC2VPCDefaultVPC.class,
    TestEC2VPCElasticIPs.class,
    TestEC2VPCManagement.class,
    TestEC2VPCNetworkAclEntryManagement.class,
    TestEC2VPCNetworkInterfaces.class,
    TestEC2VPCQuotasLimits.class,
    TestEC2VPCSecurityGroupEgressRules.class,
    TestEC2VPCSecurityGroups.class,
    TestEC2VPCSecurityGroupsInstancesAttributes.class,
    TestEC2VPCSubnetAvailableAddresses.class,
    TestEC2VPCTaggingFiltering.class,
    TestEC2VPCValidation.class,
})
public class Ec2VpcShortSuite {
  // junit test suite as defined by SuiteClasses annotation
  @BeforeClass
  public static void beforeClass( ) throws Exception {
    N4j.getCloudInfo( );
    final List<AccountAttribute> accountAttributes =
        N4j.ec2.describeAccountAttributes( ).getAccountAttributes( );
    boolean vpcAvailable = false;
    out:
    for ( final AccountAttribute accountAttribute : accountAttributes ) {
      if ( "supported-platforms".equals( accountAttribute.getAttributeName( ) ) ) {
        for ( final AccountAttributeValue value : accountAttribute.getAttributeValues( ) ) {
          vpcAvailable = "VPC".equals( value.getAttributeValue( ) );
          if ( vpcAvailable ) break out;
        }
      }
    }
    N4j.print( "VPC supported: " + vpcAvailable );
    N4j.assumeThat( vpcAvailable, "VPC is a supported platform" );
  }
}
