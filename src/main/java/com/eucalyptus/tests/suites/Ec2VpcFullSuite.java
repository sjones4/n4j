package com.eucalyptus.tests.suites;

import java.util.List;
import org.junit.BeforeClass;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;
import com.amazonaws.services.ec2.model.AccountAttribute;
import com.amazonaws.services.ec2.model.AccountAttributeValue;
import com.eucalyptus.tests.awssdk.*;

@RunWith(Suite.class) //TODO:STEVE: break into short/full/all suites
@SuiteClasses({
    // suites
    Ec2VpcShortSuite.class,

    // tests
    TestEC2VPCDnsNames.class,
    TestEC2VPCFilteringNonVPCResources.class,
    TestEC2VPCModifyInstanceSecurityGroups.class,
    TestEC2VPCNATGatewayManagement.class,
    TestEC2VPCNetworkInterfaceAttach.class,
    TestEC2VPCResourceConditionPolicy.class,
    TestEC2VPCRoutes.class,
    TestEC2VPCRunInstanceNetworkInterfaces.class,
    TestEC2VPCStartStop.class,
    TestELBDefaultVPC.class,
    TestELBVPC.class,
})
public class Ec2VpcFullSuite {
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
