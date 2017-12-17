package com.eucalyptus.tests.awssdk

import com.amazonaws.auth.AWSCredentialsProvider
import com.amazonaws.auth.AWSStaticCredentialsProvider
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.client.builder.AwsClientBuilder
import com.amazonaws.services.ec2.AmazonEC2
import com.amazonaws.services.ec2.AmazonEC2Client
import com.amazonaws.services.ec2.model.*

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import static N4j.minimalInit;
import static N4j.EC2_ENDPOINT;
import static N4j.ACCESS_KEY;
import static N4j.SECRET_KEY
import static com.eucalyptus.tests.awssdk.N4j.testInfo;

/**
 * This application tests management of resource associations for EC2 VPC.
 *
 * This is verification for the issues:
 *
 *   https://eucalyptus.atlassian.net/browse/EUCA-9715
 *   https://eucalyptus.atlassian.net/browse/EUCA-12076
 */
class TestEC2VPCAssociationManagement {

  private AWSCredentialsProvider credentials

  @BeforeClass
  static void init( ){
    minimalInit()
    this.credentials = new AWSStaticCredentialsProvider( new BasicAWSCredentials( ACCESS_KEY, SECRET_KEY ) )
  }

  private AmazonEC2 getEC2Client( final AWSCredentialsProvider credentials ) {
    AmazonEC2Client.builder( )
        .withCredentials( credentials )
        .withEndpointConfiguration( new AwsClientBuilder.EndpointConfiguration( EC2_ENDPOINT, 'eucalyptus' ) )
        .build( )
  }

  private boolean assertThat( boolean condition,
                              String message ){
    Assert.assertTrue( condition, message )
    true
  }

  @Test
  void associationManagementTest( ) throws Exception {
    testInfo( "${getClass().simpleName}.associationManagementTest" )
    final AmazonEC2 ec2 = getEC2Client( credentials )

    // Find an AZ to use
    final DescribeAvailabilityZonesResult azResult = ec2.describeAvailabilityZones()
    assertThat( azResult.getAvailabilityZones().size() > 0, "Availability zone not found" )
    final String availabilityZone = azResult.getAvailabilityZones().get( 0 ).getZoneName()
    N4j.print( "Using availability zone: " + availabilityZone )

    final List<Runnable> cleanupTasks = [] as List<Runnable>
    try {
      ec2.with{
        N4j.print( 'Creating DHCP options' )
        Map<String,List<String>> dhcpConfig = [
            'domain-name-servers': [ '8.8.8.8' ],
            'domain-name': [ 'eucalyptus.internal' ],
        ]
        String dhcpOptionsId = createDhcpOptions( new CreateDhcpOptionsRequest( dhcpConfigurations: dhcpConfig.collect { String key, ArrayList<String> values ->
          new DhcpConfiguration(key: key, values: values)
        } ) ).with {
          dhcpOptions.with {
            dhcpOptionsId
          }
        }
        cleanupTasks.add{
          N4j.print( "Deleting DHCP options ${dhcpOptionsId}" )
          deleteDhcpOptions( new DeleteDhcpOptionsRequest( dhcpOptionsId: dhcpOptionsId ) )
        }
        N4j.print( "Created DHCP options ${dhcpOptionsId}" )

        N4j.print( 'Creating internet gateway' )
        String internetGatewayId = createInternetGateway( new CreateInternetGatewayRequest( ) ).with {
          internetGateway.internetGatewayId
        }
        N4j.print( "Created internet gateway with id ${internetGatewayId}" )
        cleanupTasks.add{
          N4j.print( "Deleting internet gateway ${internetGatewayId}" )
          deleteInternetGateway( new DeleteInternetGatewayRequest( internetGatewayId: internetGatewayId ) )
        }

        N4j.print( 'Creating VPC' )
        String defaultDhcpOptionsId = null
        String vpcId = createVpc( new CreateVpcRequest( cidrBlock: '10.1.2.0/24' ) ).with {
          vpc.with {
            defaultDhcpOptionsId = dhcpOptionsId
            vpcId
          }
        }
        N4j.print( "Created VPC with id ${vpcId} and dhcp options id ${defaultDhcpOptionsId}" )
        cleanupTasks.add{
          N4j.print( "Deleting VPC ${vpcId}" )
          deleteVpc( new DeleteVpcRequest( vpcId: vpcId ) )
        }

        N4j.print( 'Creating subnet' )
        String subnetId = createSubnet( new CreateSubnetRequest( vpcId: vpcId, availabilityZone: availabilityZone, cidrBlock: '10.1.2.0/24' ) ).with {
          subnet.with {
            subnetId
          }
        }
        N4j.print( "Created subnet with id ${subnetId}" )
        cleanupTasks.add{
          N4j.print( "Deleting subnet ${subnetId}" )
          deleteSubnet( new DeleteSubnetRequest( subnetId: subnetId ) )
        }

        N4j.print( "Finding network ACL association for subnet ${subnetId}" )
        String networkAclAssociationId = describeNetworkAcls( new DescribeNetworkAclsRequest(
            filters: [
                new Filter( name: 'default', values: [ 'true' ])
            ]
        ) ).with {
          assertThat( networkAcls?.getAt( 0 )?.networkAclId != null, 'Expected network ACL identifier' )
          String assocationId = networkAcls.inject( '' ){ String associationId, NetworkAcl networkAcl -> associationId ? associationId : networkAcl?.associations?.getAt( 0 )?.subnetId == subnetId ? networkAcl?.associations?.getAt( 0 )?.networkAclAssociationId : null }
          assertThat( assocationId != null, 'Expected network ACL association identifier' )
          assocationId
        }
        N4j.print( "Found network ACL association ${networkAclAssociationId} for subnet ${subnetId}" )

        N4j.print( 'Creating route table' )
        String routeTableId = createRouteTable( new CreateRouteTableRequest( vpcId: vpcId ) ).with {
          routeTable.routeTableId
        }
        N4j.print( "Created route table with id ${routeTableId}" )
        cleanupTasks.add{
          N4j.print( "Deleting route table ${routeTableId}" )
          deleteRouteTable( new DeleteRouteTableRequest( routeTableId: routeTableId ) )
        }

        N4j.print( 'Creating second route table' )
        String secondRouteTableId = createRouteTable( new CreateRouteTableRequest( vpcId: vpcId ) ).with {
          routeTable.routeTableId
        }
        N4j.print( "Created second route table with id ${secondRouteTableId}" )
        cleanupTasks.add{
          N4j.print( "Deleting second route table ${secondRouteTableId}" )
          deleteRouteTable( new DeleteRouteTableRequest( routeTableId: secondRouteTableId ) )
        }

        N4j.print( 'Creating network acl' )
        String networkAclVpcId = vpcId
        String networkAclId = createNetworkAcl( new CreateNetworkAclRequest( vpcId: vpcId ) ).with {
          networkAcl.with {
            assertThat( vpcId == networkAclVpcId, "Expected vpcId ${networkAclVpcId}, but was: ${vpcId}" )
            assertThat( !isDefault, "Expected non-default network acl" )
            networkAclId
          }
        }
        N4j.print( "Created network acl with id ${networkAclId}" )
        cleanupTasks.add{
          N4j.print( "Deleting network acl ${networkAclId}" )
          deleteNetworkAcl( new DeleteNetworkAclRequest( networkAclId: networkAclId ) )
        }

        N4j.print( "Associating DHCP options ${dhcpOptionsId} with vpc ${vpcId}" )
        associateDhcpOptions( new AssociateDhcpOptionsRequest(
          vpcId: vpcId,
          dhcpOptionsId: dhcpOptionsId
        ) )
        N4j.print( "Verifying DHCP options ${dhcpOptionsId} associated with vpc ${vpcId}" )
        describeVpcs( new DescribeVpcsRequest( vpcIds: [ vpcId ] ) ).with {
          assertThat( vpcs != null && !vpcs.isEmpty( ), "Expected vpc"  )
          assertThat( dhcpOptionsId ==  vpcs.getAt( 0 )?.dhcpOptionsId, "Expected dhcp options ${dhcpOptionsId}, but was: ${vpcs.getAt( 0 )?.dhcpOptionsId}" )
        }

        N4j.print( "Associating default DHCP options with vpc ${vpcId}" )
        associateDhcpOptions( new AssociateDhcpOptionsRequest(
            vpcId: vpcId,
            dhcpOptionsId: 'default'
        ) )
        N4j.print( "Verifying default DHCP options ${dhcpOptionsId} associated with vpc ${vpcId}" )
        describeVpcs( new DescribeVpcsRequest( vpcIds: [ vpcId ] ) ).with {
          assertThat( vpcs != null && !vpcs.isEmpty( ), "Expected vpc"  )
          Assert.assertEquals( vpcs.getAt( 0 )?.dhcpOptionsId, 'default', "Expected default (no) dhcp options" )
        }

        N4j.print( "Associating route table ${routeTableId} with subnet ${subnetId}" )
        String routeTableAssociationId = associateRouteTable( new AssociateRouteTableRequest(
          subnetId: subnetId,
          routeTableId: routeTableId
        ) ).with {
          assertThat( associationId != null, "Expected route table association identifier" )
          associationId
        }
        N4j.print( "Verifying route table ${routeTableId} association with subnet ${subnetId}" )
        describeRouteTables( new DescribeRouteTablesRequest(
            routeTableIds: [ routeTableId ]
        ) ).with {
          assertThat( routeTables?.getAt( 0 )?.getAssociations( )?.getAt( 0 )?.subnetId == subnetId, "Association not found for subnet ${subnetId} and route table ${routeTableId}" )
        }

        N4j.print( "Replacing route table association with ${secondRouteTableId}" )
        String secondRouteTableAssociationId = replaceRouteTableAssociation( new ReplaceRouteTableAssociationRequest(
            associationId: routeTableAssociationId,
            routeTableId: secondRouteTableId
        ) ).with {
          newAssociationId
        }
        N4j.print( "Verifying route table ${secondRouteTableId} association with subnet ${subnetId}" )
        describeRouteTables( new DescribeRouteTablesRequest(
            routeTableIds: [ secondRouteTableId ]
        ) ).with {
          assertThat( routeTables?.getAt( 0 )?.getAssociations( )?.getAt( 0 )?.subnetId == subnetId, "Association not found for subnet ${subnetId} and route table ${routeTableId}" )
        }

        N4j.print( "Dissassociating route table ${secondRouteTableId}" )
        disassociateRouteTable( new DisassociateRouteTableRequest(
          associationId: secondRouteTableAssociationId
        ) )

        N4j.print( "Replacing network ACL assocation ${networkAclAssociationId} for network acl ${networkAclId}" )
        replaceNetworkAclAssociation( new ReplaceNetworkAclAssociationRequest(
          associationId: networkAclAssociationId,
          networkAclId: networkAclId
        ) ).with {
          newAssociationId
        }
        N4j.print( "Verifying network ACL ${networkAclId} association with subnet ${subnetId}" )
        describeNetworkAcls( new DescribeNetworkAclsRequest(
            networkAclIds: [ networkAclId ]
        ) ).with {
          assertThat( networkAcls?.getAt( 0 )?.getAssociations( )?.getAt( 0 )?.subnetId == subnetId, "Association not found for subnet ${subnetId} and network ACL ${networkAclId}" )
        }

        N4j.print( "Attaching internet gateway ${internetGatewayId} to vpc ${vpcId}" )
        attachInternetGateway( new AttachInternetGatewayRequest(
            vpcId: vpcId,
            internetGatewayId: internetGatewayId
        ) )
        N4j.print( "Verifying internet gateway ${internetGatewayId} attached to vpc ${vpcId}" )
        describeInternetGateways( new DescribeInternetGatewaysRequest(
            internetGatewayIds: [ internetGatewayId ]
        ) ).with {
          assertThat( internetGateways?.getAt( 0 )?.getAttachments( )?.getAt( 0 )?.vpcId == vpcId, "Attachment not found for vpc ${vpcId} and internet gateway ${internetGatewayId}" )
        }

        N4j.print( "Detaching internet gateway ${internetGatewayId} from vpc ${vpcId}" )
        detachInternetGateway( new DetachInternetGatewayRequest(
            vpcId: vpcId,
            internetGatewayId: internetGatewayId
        ) )
        N4j.print( "Verifying internet gateway ${internetGatewayId} not attached to vpc ${vpcId}" )
        describeInternetGateways( new DescribeInternetGatewaysRequest(
            internetGatewayIds: [ internetGatewayId ]
        ) ).with {
          assertThat( internetGateways?.getAt( 0 )?.getAttachments( )?.getAt( 0 )?.vpcId != vpcId, "Attachment found for vpc ${vpcId} and internet gateway ${internetGatewayId}" )
        }
      }

      N4j.print( "Test complete" )
    } finally {
      // Attempt to clean up anything we created
      cleanupTasks.reverseEach { Runnable cleanupTask ->
        try {
          cleanupTask.run()
        } catch ( Exception e ) {
          // Some not-found errors are expected here so may need to be suppressed
          e.printStackTrace()
        }
      }
    }
  }
}
