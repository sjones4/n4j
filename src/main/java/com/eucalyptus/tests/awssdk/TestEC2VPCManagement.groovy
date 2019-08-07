package com.eucalyptus.tests.awssdk

import com.amazonaws.AmazonClientException
import com.amazonaws.AmazonServiceException
import com.amazonaws.services.ec2.model.AttachInternetGatewayRequest
import com.amazonaws.services.ec2.model.CreateDhcpOptionsRequest
import com.amazonaws.services.ec2.model.CreateInternetGatewayRequest
import com.amazonaws.services.ec2.model.CreateNetworkAclEntryRequest
import com.amazonaws.services.ec2.model.CreateNetworkAclRequest
import com.amazonaws.services.ec2.model.CreateNetworkInterfaceRequest
import com.amazonaws.services.ec2.model.CreateRouteRequest
import com.amazonaws.services.ec2.model.CreateRouteTableRequest
import com.amazonaws.services.ec2.model.CreateSubnetRequest
import com.amazonaws.services.ec2.model.CreateVpcRequest
import com.amazonaws.services.ec2.model.DeleteDhcpOptionsRequest
import com.amazonaws.services.ec2.model.DeleteInternetGatewayRequest
import com.amazonaws.services.ec2.model.DeleteNetworkAclRequest
import com.amazonaws.services.ec2.model.DeleteNetworkInterfaceRequest
import com.amazonaws.services.ec2.model.DeleteRouteRequest
import com.amazonaws.services.ec2.model.DeleteRouteTableRequest
import com.amazonaws.services.ec2.model.DeleteSubnetRequest
import com.amazonaws.services.ec2.model.DeleteVpcRequest
import com.amazonaws.services.ec2.model.DescribeAvailabilityZonesResult
import com.amazonaws.services.ec2.model.DescribeDhcpOptionsRequest
import com.amazonaws.services.ec2.model.DescribeInternetGatewaysRequest
import com.amazonaws.services.ec2.model.DescribeNetworkAclsRequest
import com.amazonaws.services.ec2.model.DescribeNetworkInterfacesRequest
import com.amazonaws.services.ec2.model.DescribeRouteTablesRequest
import com.amazonaws.services.ec2.model.DescribeSubnetsRequest
import com.amazonaws.services.ec2.model.DescribeVpcsRequest
import com.amazonaws.services.ec2.model.DetachInternetGatewayRequest
import com.amazonaws.services.ec2.model.DhcpConfiguration
import com.amazonaws.services.ec2.model.NetworkAclEntry
import com.amazonaws.services.ec2.model.Route

import org.junit.Assert
import org.junit.After
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test

import static com.eucalyptus.tests.awssdk.N4j.*


/**
 * This application tests management of resources for EC2 VPC.
 *
 * This test covers the issues:
 *
 *   https://eucalyptus.atlassian.net/browse/EUCA-9604
 *   https://eucalyptus.atlassian.net/browse/EUCA-13182
 */
class TestEC2VPCManagement {

  // for each test
  private List<Runnable> cleanupTasks

  @BeforeClass
  static void init( ) {
    print("### SETUP - ${getClass().simpleName}")
    getCloudInfo()
  }

  @Before
  void initTest(  ) {
    print( "Initializing clean up tasks" )
    cleanupTasks = [ ]
  }

  @After
  void cleanup( ) {
    print( "Running clean up tasks" )
    Collections.reverse(cleanupTasks)
    for (final Runnable cleanupTask : cleanupTasks) {
      try {
        cleanupTask.run()
      } catch (Exception e) {
        print("Unable to run clean up task: ${e}")
      }
    }
  }

  private void assertTrue( final boolean condition, final String message ) {
    Assert.assertTrue( message, condition )
  }

  @Test
  void testBasicManagement( ) throws Exception {
    testInfo("${this.getClass().simpleName}.testBasicManagement")

    // Find an AZ to use
    final DescribeAvailabilityZonesResult azResult = ec2.describeAvailabilityZones()

    assertTrue( azResult.getAvailabilityZones().size() > 0, "Availability zone not found" )

    final String availabilityZone = azResult.getAvailabilityZones().get( 0 ).getZoneName()
    print( "Using availability zone: " + availabilityZone )

    ec2.with{
      print( 'Creating DHCP options' )
      Map<String,List<String>> dhcpConfig = [
          'domain-name-servers': [ '1.1.1.1','2.2.2.2' ],
          'domain-name': [ 'test.internal' ],
          'ntp-servers': [ '3.3.3.3', '4.4.4.4'  ],
          'netbios-name-servers': [ '5.5.5.5' ],
          'netbios-node-type': [ '2' ]
      ]
      String dhcpOptionsId = createDhcpOptions( new CreateDhcpOptionsRequest( dhcpConfigurations: dhcpConfig.collect { String key, List<String> values ->
        new DhcpConfiguration(key: key, values: values)
      } ) ).with {
        dhcpOptions.with {
          print( dhcpConfigurations.toString( ) )
          assertTrue( dhcpConfigurations.size()==5, "Expected five configuration settings, got: ${dhcpConfigurations.size()}")
          dhcpConfigurations.each { DhcpConfiguration configuration ->
            assertTrue( configuration.values==dhcpConfig.get(configuration.key), "Unexpected configuration value ${configuration.key}=${configuration.values}" )
          }
          dhcpOptionsId
        }
      }
      cleanupTasks.add{
        print( "Deleting DHCP options ${dhcpOptionsId}" )
        deleteDhcpOptions( new DeleteDhcpOptionsRequest( dhcpOptionsId: dhcpOptionsId ) )
      }
      print( "Created DHCP options ${dhcpOptionsId}" )
      print( "Describing DHCP options ${dhcpOptionsId}" )
      describeDhcpOptions( new DescribeDhcpOptionsRequest( dhcpOptionsIds: [ dhcpOptionsId ] ) ).with {
        assertTrue( dhcpOptions.size()==1, "Expected one dhcp options" )
        dhcpOptions.get( 0 ).with {
          print( dhcpConfigurations.toString( ) )
          assertTrue( dhcpConfigurations.size()==5, "Expected five configuration settings, got: ${dhcpConfigurations.size()}")
          dhcpConfigurations.each { DhcpConfiguration configuration ->
            assertTrue( configuration.values==dhcpConfig.get(configuration.key), "Unexpected configuration value ${configuration.key}=${configuration.values}" )
          }
        }
      }
      print( "Deleting DHCP options ${dhcpOptionsId}" )
      deleteDhcpOptions( new DeleteDhcpOptionsRequest( dhcpOptionsId: dhcpOptionsId ) )
      try {
        describeDhcpOptions( new DescribeDhcpOptionsRequest( dhcpOptionsIds: [ dhcpOptionsId ] ) ).with {
          assertTrue( dhcpOptions.size() == 0 , "Expected no dhcp options")
        }
      } catch ( AmazonClientException e ) {
        // OK
      }

      print( 'Creating internet gateway' )
      String internetGatewayId = createInternetGateway( new CreateInternetGatewayRequest( ) ).with {
        internetGateway.internetGatewayId
      }
      print( "Created internet gateway with id ${internetGatewayId}" )
      cleanupTasks.add{
        print( "Deleting internet gateway ${internetGatewayId}" )
        deleteInternetGateway( new DeleteInternetGatewayRequest( internetGatewayId: internetGatewayId ) )
      }
      print( "Describing internet gateway ${internetGatewayId}" )
      describeInternetGateways( new DescribeInternetGatewaysRequest( internetGatewayIds: [ internetGatewayId ]) ).with {
        assertTrue( internetGateways.size()==1, "Expected one internet gateway" )
      }

      print( 'Creating VPC' )
      String defaultDhcpOptionsId = null
      String vpcId = createVpc( new CreateVpcRequest( cidrBlock: '10.1.2.0/24' ) ).with {
        vpc.with {
          assertTrue( 'available' == state || 'pending' == state, "Expected available or pending state, but was: ${state}" )
          assertTrue( '10.1.2.0/24' == cidrBlock, "Expected cidr 10.1.2.0/24, but was: ${cidrBlock}" )
          assertTrue( !isDefault, "Expected non-default vpc" )
          defaultDhcpOptionsId = dhcpOptionsId
          vpcId
        }
      }
      print( "Created VPC with id ${vpcId} and dhcp options id ${defaultDhcpOptionsId}" )
      cleanupTasks.add{
        print( "Deleting VPC ${vpcId}" )
        deleteVpc( new DeleteVpcRequest( vpcId: vpcId ) )
      }
      print( "Describing VPC ${vpcId}" )
      describeVpcs( new DescribeVpcsRequest( vpcIds: [ vpcId ] ) ).with {
        assertTrue( vpcs.size()==1, "Expected one vpc" )
        vpcs.get( 0 ).with {
          assertTrue( 'available' == state || 'pending' == state, "Expected available or pending state, but was: ${state}" )
          assertTrue( '10.1.2.0/24' == cidrBlock, "Expected cidr 10.1.2.0/24, but was: ${cidrBlock}" )
          assertTrue( !isDefault, "Expected non-default vpc" )
        }
      }
      print( "Attaching internet gateway ${internetGatewayId} to VPC ${vpcId}" )
      attachInternetGateway( new AttachInternetGatewayRequest(
          internetGatewayId: internetGatewayId,
          vpcId: vpcId
      ) )
      cleanupTasks.add{
        print( "Detaching internet gateway ${internetGatewayId} from VPC ${vpcId}" )
        try {
          detachInternetGateway( new DetachInternetGatewayRequest(
              internetGatewayId: internetGatewayId,
              vpcId: vpcId
          ) )
        } catch( AmazonServiceException e ) {
          print( e.toString( ) )
        }
      }

      print( 'Creating subnet' )
      String subnetVpcId = vpcId
      String subnetAvailabilityZone = availabilityZone
      String subnetId = createSubnet( new CreateSubnetRequest( vpcId: vpcId, availabilityZone: availabilityZone, cidrBlock: '10.1.2.0/24' ) ).with {
        subnet.with {
          assertTrue( vpcId == subnetVpcId, "Expected vpcId ${subnetVpcId}, but was ${vpcId}"  )
          assertTrue( cidrBlock == '10.1.2.0/24', "Expected cidr 10.1.2.0/24, but was ${cidrBlock}" )
          assertTrue( availabilityZone == subnetAvailabilityZone, "Expected zone ${availabilityZone}, but was ${subnetAvailabilityZone}"  )
          assertTrue( 'available' == state || 'pending' == state, "Expected available or pending state, but was: ${state}" )
          assertTrue( availableIpAddressCount == 251, "Expected 251 IP addresses for subnet, but was: ${availableIpAddressCount}")
          assertTrue( !defaultForAz, 'Expected non-default subnet' )
          assertTrue( !mapPublicIpOnLaunch, 'Expected public ip not mapped on launch' )
          subnetId
        }
      }
      print( "Created subnet with id ${subnetId}" )
      cleanupTasks.add{
        print( "Deleting subnet ${subnetId}" )
        deleteSubnet( new DeleteSubnetRequest( subnetId: subnetId ) )
      }
      print( "Describing subnet ${subnetId}" )
      describeSubnets( new DescribeSubnetsRequest( subnetIds: [ subnetId ] ) ).with {
        assertTrue( subnets.size()==1, "Expected one subnet" )
        subnets.get( 0 ).with {
          assertTrue( vpcId == subnetVpcId, "Expected vpcId ${subnetVpcId}, but was ${vpcId}"  )
          assertTrue( cidrBlock == '10.1.2.0/24', "Expected cidr 10.1.2.0/24, but was ${cidrBlock}" )
          assertTrue( availabilityZone == subnetAvailabilityZone, "Expected zone ${availabilityZone}, but was ${subnetAvailabilityZone}"  )
          assertTrue( 'available' == state || 'pending' == state, "Expected available or pending state, but was: ${state}" )
          assertTrue( availableIpAddressCount == 251, "Expected 251 IP addresses for subnet, but was: ${availableIpAddressCount}")
          assertTrue( !defaultForAz, 'Expected non-default subnet' )
          assertTrue( !mapPublicIpOnLaunch, 'Expected public ip not mapped on launch' )
        }
      }

      print( 'Creating route table' )
      String routeTableVpcId = vpcId
      String routeTableId = createRouteTable( new CreateRouteTableRequest( vpcId: vpcId ) ).with {
        routeTable.with {
          assertTrue( vpcId == routeTableVpcId, "Expected vpcId ${routeTableVpcId}, but was: ${vpcId}" )
          assertTrue( routes.size( ) == 1, "Expected one (local) route" )
          routes.get( 0 ).with {
            assertTrue( gatewayId == 'local', "Expected local gatewayId, but was: ${gatewayId}")
            assertTrue( destinationCidrBlock == '10.1.2.0/24', "Expected 10.1.2.0/24 destination cidr, but was: ${destinationCidrBlock}")
            assertTrue( state == 'active', "Expected active state, but was: ${state}")
          }
          routeTableId
        }
      }
      print( "Created route table with id ${routeTableId}" )
      cleanupTasks.add{
        print( "Deleting route table ${routeTableId}" )
        deleteRouteTable( new DeleteRouteTableRequest( routeTableId: routeTableId ) )
      }
      print( "Describing route table ${routeTableId}" )
      describeRouteTables( new DescribeRouteTablesRequest( routeTableIds: [ routeTableId ] )).with {
        assertTrue( routeTables.size()==1, "Expected one route table" )
        routeTables.get( 0 ).with {
          assertTrue( vpcId == routeTableVpcId, "Expected vpcId ${routeTableVpcId}, but was: ${vpcId}" )
          assertTrue( routes.size( ) == 1, "Expected one (local) route" )
          routes.get( 0 ).with {
            assertTrue( gatewayId == 'local', "Expected local gatewayId, but was: ${gatewayId}")
            assertTrue( destinationCidrBlock == '10.1.2.0/24', "Expected 10.1.2.0/24 destination cidr, but was: ${destinationCidrBlock}")
            assertTrue( state == 'active', "Expected active state, but was: ${state}")
          }
        }
      }

      print( 'Creating route' )
      createRoute( new CreateRouteRequest( routeTableId: routeTableId, destinationCidrBlock: '0.0.0.0/0', gatewayId: internetGatewayId ) )
      print( "Describing route table ${routeTableId}" )
      describeRouteTables( new DescribeRouteTablesRequest( routeTableIds: [ routeTableId ] )).with {
        assertTrue( routeTables.size()==1, "Expected one route table" )
        routeTables.get( 0 ).with {
          print( routes.toString( ) )
          assertTrue( routes.size( ) == 2, "Expected two routes" )
        }
      }

      print( 'Deleting route' )
      deleteRoute( new DeleteRouteRequest( routeTableId: routeTableId, destinationCidrBlock: '0.0.0.0/0' ) )
      print( "Describing route table ${routeTableId}" )
      describeRouteTables( new DescribeRouteTablesRequest( routeTableIds: [ routeTableId ] )).with {
        assertTrue( routeTables.size()==1, "Expected one route table" )
        routeTables.get( 0 ).with {
          print( routes.toString( ) )
          assertTrue( routes.size( ) == 1, "Expected one route" )
        }
      }

      print( 'Creating network acl' )
      String networkAclVpcId = vpcId
      String networkAclId = createNetworkAcl( new CreateNetworkAclRequest( vpcId: vpcId ) ).with {
        networkAcl.with {
          assertTrue( vpcId == networkAclVpcId, "Expected vpcId ${networkAclVpcId}, but was: ${vpcId}" )
          assertTrue( !isDefault, "Expected non-default network acl" )
          networkAclId
        }
      }
      print( "Created network acl with id ${networkAclId}" )
      cleanupTasks.add{
        print( "Deleting network acl ${networkAclId}" )
        deleteNetworkAcl( new DeleteNetworkAclRequest( networkAclId: networkAclId ) )
      }
      print( "Describing network acl with id ${networkAclId}" )
      describeNetworkAcls( new DescribeNetworkAclsRequest( networkAclIds: [ networkAclId ] ) ).with {
        assertTrue( networkAcls.size()==1, "Expected one network acl" )
        networkAcls.get( 0 ).with {
          assertTrue( vpcId == networkAclVpcId, "Expected vpcId ${networkAclVpcId}, but was: ${vpcId}" )
          assertTrue( !isDefault, "Expected non-default network acl" )
        }
      }

      print( 'Creating network interface' )
      String networkInterfaceVpcId = vpcId
      String networkInterfaceSubnetId = subnetId
      String networkInterfaceZone = availabilityZone
      String networkInterfaceId = createNetworkInterface( new CreateNetworkInterfaceRequest( subnetId: subnetId, description: 'a network interface', privateIpAddress: '10.1.2.10' ) ).with {
        networkInterface.with {
          assertTrue( subnetId == networkInterfaceSubnetId, "Expected subnetId ${networkInterfaceSubnetId}, but was: ${subnetId}" )
          assertTrue( vpcId == networkInterfaceVpcId, "Expected vpcId ${networkInterfaceVpcId}, but was: ${vpcId}" )
          assertTrue( availabilityZone == networkInterfaceZone, "Expected availabilityZone ${networkInterfaceZone}, but was: ${availabilityZone}" )
          assertTrue( description == 'a network interface', "Expected 'a network interface', but was: ${description}" )
          assertTrue( ownerId.length()==12, "Expected owner id length 12, but was: ${ownerId}" )
          assertTrue( !requesterManaged, "Expected not requester managed" )
          assertTrue( status == 'available', "Expected status 'available', but was: ${status}" )
          assertTrue( macAddress.length() == 17, "Expected mac address length 17, but was ${macAddress}" )
          assertTrue( privateIpAddress == '10.1.2.10', "Expected private ip '10.1.2.10', but was: ${privateIpAddress}" )
          assertTrue( sourceDestCheck, "Expected source dest check true" )
          networkInterfaceId
        }
      }
      print( "Created network interface with id ${networkInterfaceId}" )
      cleanupTasks.add{
        print( "Deleting network interface ${networkInterfaceId}" )
        deleteNetworkInterface( new DeleteNetworkInterfaceRequest( networkInterfaceId: networkInterfaceId ) )
      }
      print( "Describing network interface with id ${networkInterfaceId}" )
      describeNetworkInterfaces( new DescribeNetworkInterfacesRequest( networkInterfaceIds: [ networkInterfaceId ] ) ).with {
        assertTrue( networkInterfaces.size()==1, "Expected one network interface" )
        networkInterfaces.get( 0 ).with {
          assertTrue( subnetId == networkInterfaceSubnetId, "Expected subnetId ${networkInterfaceSubnetId}, but was: ${subnetId}" )
          assertTrue( vpcId == networkInterfaceVpcId, "Expected vpcId ${networkInterfaceVpcId}, but was: ${vpcId}" )
          assertTrue( availabilityZone == networkInterfaceZone, "Expected availabilityZone ${networkInterfaceZone}, but was: ${availabilityZone}" )
          assertTrue( description == 'a network interface', "Expected 'a network interface', but was: ${description}" )
          assertTrue( ownerId.length()==12, "Expected owner id length 12, but was: ${ownerId}" )
          assertTrue( !requesterManaged, "Expected not requester managed" )
          assertTrue( status == 'available', "Expected status 'available', but was: ${status}" )
          assertTrue( macAddress.length() == 17, "Expected mac address length 17, but was ${macAddress}" )
          assertTrue( privateIpAddress == '10.1.2.10', "Expected private ip '10.1.2.10', but was: ${privateIpAddress}" )
          assertTrue( sourceDestCheck, "Expected source dest check true" )
        }
      }

      print( "Deleting network interface ${networkInterfaceId}" )
      deleteNetworkInterface( new DeleteNetworkInterfaceRequest( networkInterfaceId: networkInterfaceId ) )
      try {
        describeNetworkInterfaces( new DescribeNetworkInterfacesRequest( networkInterfaceIds: [ networkInterfaceId ] ) ).with {
          assertTrue( networkInterfaces.size()==0, "Expected no network interfaces" )
        }
      } catch ( AmazonClientException e ) {
        // OK
      }

      print( "Deleting network acl ${networkAclId}" )
      deleteNetworkAcl( new DeleteNetworkAclRequest( networkAclId: networkAclId ) )
      try {
        describeNetworkAcls( new DescribeNetworkAclsRequest( networkAclIds: [ networkAclId ] ) ).with {
          assertTrue( networkAcls.size()==0, "Expected no network acls" )
        }
      } catch ( AmazonClientException e ) {
        // OK
      }

      print( "Deleting route table ${routeTableId}" )
      deleteRouteTable( new DeleteRouteTableRequest( routeTableId: routeTableId ) )
      try {
        describeRouteTables( new DescribeRouteTablesRequest( routeTableIds: [ routeTableId ] ) ).with {
          assertTrue( routeTables.size()==0, "Expected no route tables" )
        }
      } catch ( AmazonClientException e ) {
        // OK
      }

      print( "Deleting subnet ${subnetId}" )
      deleteSubnet( new DeleteSubnetRequest( subnetId: subnetId ) )
      try {
        describeSubnets( new DescribeSubnetsRequest( subnetIds: [ subnetId ] ) ).with {
          assertTrue( subnets.size()==0, "Expected no subnets" )
        }
      } catch ( AmazonClientException e ) {
        // OK
      }

      print( "Detaching internet gateway ${internetGatewayId} from VPC ${vpcId}" )
      detachInternetGateway( new DetachInternetGatewayRequest(
          internetGatewayId: internetGatewayId,
          vpcId: vpcId
      ) )

      print( "Deleting vpc ${vpcId}" )
      deleteVpc( new DeleteVpcRequest( vpcId: vpcId ) )
      try {
        describeVpcs( new DescribeVpcsRequest( vpcIds: [ vpcId ] ) ).with {
          assertTrue( vpcs.size()==0, "Expected no vpcs" )
        }
      } catch ( AmazonClientException e ) {
        // OK
      }

      print( "Deleting internet gateway ${internetGatewayId}" )
      deleteInternetGateway( new DeleteInternetGatewayRequest( internetGatewayId: internetGatewayId ) )
      try {
        describeInternetGateways( new DescribeInternetGatewaysRequest( internetGatewayIds: [ internetGatewayId ]) ).with {
          assertTrue( internetGateways.size()==0, "Expected no internet gateway" )
        }
      } catch ( AmazonClientException e ) {
        // OK
      }

      void
    }

    print( "Test complete" )
  }

  /**
   * Test that we allow pattern/prefix mismatched cidrs such as 1.1.1.1/24, but correct them, e.g. 1.1.1.0/24
   */
  @Test
  void testManagementWithIncorrectCidrs( ) throws Exception {
    testInfo("${this.getClass().simpleName}.testManagementWithIncorrectCidrs")

    // Find an AZ to use
    final DescribeAvailabilityZonesResult azResult = ec2.describeAvailabilityZones()

    assertTrue(azResult.getAvailabilityZones().size() > 0, "Availability zone not found")

    final String availabilityZone = azResult.getAvailabilityZones().get( 0 ).getZoneName()
    print( "Using availability zone: " + availabilityZone )

    ec2.with{
      print( 'Creating internet gateway' )
      String internetGatewayId = createInternetGateway( new CreateInternetGatewayRequest( ) ).with {
        internetGateway.internetGatewayId
      }
      print( "Created internet gateway with id ${internetGatewayId}" )
      cleanupTasks.add{
        print( "Deleting internet gateway ${internetGatewayId}" )
        deleteInternetGateway( new DeleteInternetGatewayRequest( internetGatewayId: internetGatewayId ) )
      }
      print( "Describing internet gateway ${internetGatewayId}" )
      describeInternetGateways( new DescribeInternetGatewaysRequest( internetGatewayIds: [ internetGatewayId ]) ).with {
        assertTrue( internetGateways.size()==1, "Expected one internet gateway" )
      }

      print( 'Creating VPC' )
      String defaultDhcpOptionsId = null
      String vpcId = createVpc( new CreateVpcRequest( cidrBlock: '10.1.2.123/24' ) ).with {
        vpc.with {
          assertTrue( '10.1.2.0/24' == cidrBlock, "Expected cidr 10.1.2.0/24, but was: ${cidrBlock}" )
          defaultDhcpOptionsId = dhcpOptionsId
          vpcId
        }
      }
      print( "Created VPC with id ${vpcId} and dhcp options id ${defaultDhcpOptionsId}" )
      cleanupTasks.add{
        print( "Deleting VPC ${vpcId}" )
        deleteVpc( new DeleteVpcRequest( vpcId: vpcId ) )
      }
      print( "Describing VPC ${vpcId}" )
      describeVpcs( new DescribeVpcsRequest( vpcIds: [ vpcId ] ) ).with {
        assertTrue( vpcs.size()==1, "Expected one vpc" )
        vpcs.get( 0 ).with {
          assertTrue( '10.1.2.0/24' == cidrBlock, "Expected cidr 10.1.2.0/24, but was: ${cidrBlock}" )
        }
      }
      print( "Attaching internet gateway ${internetGatewayId} to VPC ${vpcId}" )
      attachInternetGateway( new AttachInternetGatewayRequest(
          internetGatewayId: internetGatewayId,
          vpcId: vpcId
      ) )
      cleanupTasks.add{
        print( "Detaching internet gateway ${internetGatewayId} from VPC ${vpcId}" )
        try {
          detachInternetGateway( new DetachInternetGatewayRequest(
              internetGatewayId: internetGatewayId,
              vpcId: vpcId
          ) )
        } catch( AmazonServiceException e ) {
          print( e.toString( ) )
        }
      }

      print( 'Creating subnet' )
      String subnetId = createSubnet( new CreateSubnetRequest( vpcId: vpcId, availabilityZone: availabilityZone, cidrBlock: '10.1.2.123/24' ) ).with {
        subnet.with {
          assertTrue( cidrBlock == '10.1.2.0/24', "Expected cidr 10.1.2.0/24, but was ${cidrBlock}" )
          subnetId
        }
      }
      print( "Created subnet with id ${subnetId}" )
      cleanupTasks.add{
        print( "Deleting subnet ${subnetId}" )
        deleteSubnet( new DeleteSubnetRequest( subnetId: subnetId ) )
      }
      print( "Describing subnet ${subnetId}" )
      describeSubnets( new DescribeSubnetsRequest( subnetIds: [ subnetId ] ) ).with {
        assertTrue( subnets.size()==1, "Expected one subnet" )
        subnets.get( 0 ).with {
          assertTrue( cidrBlock == '10.1.2.0/24', "Expected cidr 10.1.2.0/24, but was ${cidrBlock}" )
        }
      }

      print( 'Creating route table' )
      String routeTableId = createRouteTable( new CreateRouteTableRequest( vpcId: vpcId ) ).with {
        routeTable.with {
          assertTrue( routes.size( ) == 1, "Expected one (local) route" )
          routes.get( 0 ).with {
            assertTrue( gatewayId == 'local', "Expected local gatewayId, but was: ${gatewayId}")
            assertTrue( destinationCidrBlock == '10.1.2.0/24', "Expected 10.1.2.0/24 destination cidr, but was: ${destinationCidrBlock}")
          }
          routeTableId
        }
      }
      print( "Created route table with id ${routeTableId}" )
      cleanupTasks.add{
        print( "Deleting route table ${routeTableId}" )
        deleteRouteTable( new DeleteRouteTableRequest( routeTableId: routeTableId ) )
      }

      print( 'Creating route' )
      createRoute( new CreateRouteRequest( routeTableId: routeTableId, destinationCidrBlock: '10.10.10.10/0', gatewayId: internetGatewayId ) )

      print( "Describing route table ${routeTableId}" )
      describeRouteTables( new DescribeRouteTablesRequest( routeTableIds: [ routeTableId ] )).with {
        assertTrue( routeTables.size()==1, "Expected one route table" )
        routeTables.get( 0 ).with {
          print( routes.toString( ) )
          assertTrue( routes.size( ) == 2, "Expected two routes" )
          routes.each { Route route ->
            if ( route.gatewayId == internetGatewayId ) {
              Assert.assertEquals( 'Invalid destination cidr', '0.0.0.0/0', route.destinationCidrBlock )
            } else if ( route.gatewayId != 'local' ) {
              Assert.fail( "Unexpected route: ${route}" )
            }
          }
        }
      }

      print( 'Deleting route' )
      deleteRoute( new DeleteRouteRequest( routeTableId: routeTableId, destinationCidrBlock: '10.10.10.10/0' ) )
      print( "Describing route table ${routeTableId}" )
      describeRouteTables( new DescribeRouteTablesRequest( routeTableIds: [ routeTableId ] )).with {
        assertTrue( routeTables.size()==1, "Expected one route table" )
        routeTables.get( 0 ).with {
          print( routes.toString( ) )
          assertTrue( routes.size( ) == 1, "Expected one route" )
        }
      }

      print( 'Creating network acl' )
      String networkAclId = createNetworkAcl( new CreateNetworkAclRequest( vpcId: vpcId ) ).with {
        networkAcl?.networkAclId
      }
      print( "Created network acl with id ${networkAclId}" )
      cleanupTasks.add{
        print( "Deleting network acl ${networkAclId}" )
        deleteNetworkAcl( new DeleteNetworkAclRequest( networkAclId: networkAclId ) )
      }

      print( 'Creating network acl entry' )
      createNetworkAclEntry( new CreateNetworkAclEntryRequest(
          networkAclId: networkAclId,
          ruleNumber: 42,
          protocol: '-1',
          ruleAction: 'allow',
          egress: true,
          cidrBlock: '10.10.10.10/8'
      ) )

      print( "Describing network acl ${networkAclId}" )
      describeNetworkAcls( new DescribeNetworkAclsRequest( networkAclIds: [ networkAclId ] )).with {
        assertTrue( networkAcls.size()==1, "Expected one network acl" )
        networkAcls.get( 0 ).with {
          print( entries.toString( ) )
          assertTrue( entries.size( ) == 3, "Expected two network acl entries" )
          entries.each { NetworkAclEntry entry ->
            if ( entry.ruleNumber == 42 ) {
              Assert.assertEquals( 'Invalid cidr','10.0.0.0/8', entry.cidrBlock)
            } else if ( entry.ruleNumber != 100 && entry.ruleNumber != 32767 ) {
              Assert.fail( "Unexpected entry: ${entry}" )
            }
          }
        }
      }

      print( "Deleting network acl ${networkAclId}" )
      deleteNetworkAcl( new DeleteNetworkAclRequest( networkAclId: networkAclId ) )
      try {
        describeNetworkAcls( new DescribeNetworkAclsRequest( networkAclIds: [ networkAclId ] ) ).with {
          assertTrue( networkAcls.size()==0, "Expected no network acls" )
        }
      } catch ( AmazonClientException e ) {
        // OK
      }

      print( "Deleting route table ${routeTableId}" )
      deleteRouteTable( new DeleteRouteTableRequest( routeTableId: routeTableId ) )
      try {
        describeRouteTables( new DescribeRouteTablesRequest( routeTableIds: [ routeTableId ] ) ).with {
          assertTrue( routeTables.size()==0, "Expected no route tables" )
        }
      } catch ( AmazonClientException e ) {
        // OK
      }

      print( "Deleting subnet ${subnetId}" )
      deleteSubnet( new DeleteSubnetRequest( subnetId: subnetId ) )
      try {
        describeSubnets( new DescribeSubnetsRequest( subnetIds: [ subnetId ] ) ).with {
          assertTrue( subnets.size()==0, "Expected no subnets" )
        }
      } catch ( AmazonClientException e ) {
        // OK
      }

      print( "Detaching internet gateway ${internetGatewayId} from VPC ${vpcId}" )
      detachInternetGateway( new DetachInternetGatewayRequest(
          internetGatewayId: internetGatewayId,
          vpcId: vpcId
      ) )

      print( "Deleting vpc ${vpcId}" )
      deleteVpc( new DeleteVpcRequest( vpcId: vpcId ) )
      try {
        describeVpcs( new DescribeVpcsRequest( vpcIds: [ vpcId ] ) ).with {
          assertTrue( vpcs.size()==0, "Expected no vpcs" )
        }
      } catch ( AmazonClientException e ) {
        // OK
      }

      print( "Deleting internet gateway ${internetGatewayId}" )
      deleteInternetGateway( new DeleteInternetGatewayRequest( internetGatewayId: internetGatewayId ) )
      try {
        describeInternetGateways( new DescribeInternetGatewaysRequest( internetGatewayIds: [ internetGatewayId ]) ).with {
          assertTrue( internetGateways.size()==0, "Expected no internet gateway" )
        }
      } catch ( AmazonClientException e ) {
        // OK
      }

      void
    }

    print( "Test complete" )
  }
}
