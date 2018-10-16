package com.eucalyptus.tests.awssdk

import com.amazonaws.AmazonServiceException
import com.amazonaws.auth.AWSCredentialsProvider
import com.amazonaws.auth.AWSStaticCredentialsProvider
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.client.builder.AwsClientBuilder
import com.amazonaws.services.ec2.AmazonEC2
import com.amazonaws.services.ec2.AmazonEC2Client
import com.amazonaws.services.ec2.model.AllocateHostsRequest
import com.amazonaws.services.ec2.model.AssignIpv6AddressesRequest
import com.amazonaws.services.ec2.model.AssociateSubnetCidrBlockRequest
import com.amazonaws.services.ec2.model.AssociateVpcCidrBlockRequest
import com.amazonaws.services.ec2.model.AuthorizeSecurityGroupIngressRequest
import com.amazonaws.services.ec2.model.CreateEgressOnlyInternetGatewayRequest
import com.amazonaws.services.ec2.model.CreateKeyPairRequest
import com.amazonaws.services.ec2.model.CreateSecurityGroupRequest
import com.amazonaws.services.ec2.model.DeleteEgressOnlyInternetGatewayRequest
import com.amazonaws.services.ec2.model.DeleteKeyPairRequest
import com.amazonaws.services.ec2.model.DeleteSecurityGroupRequest
import com.amazonaws.services.ec2.model.DescribeAggregateIdFormatRequest
import com.amazonaws.services.ec2.model.DescribeEgressOnlyInternetGatewaysRequest
import com.amazonaws.services.ec2.model.DescribeElasticGpusRequest
import com.amazonaws.services.ec2.model.DescribeFleetHistoryRequest
import com.amazonaws.services.ec2.model.DescribeFleetInstancesRequest
import com.amazonaws.services.ec2.model.DescribeFleetsRequest
import com.amazonaws.services.ec2.model.DescribeFpgaImagesRequest
import com.amazonaws.services.ec2.model.DescribeHostReservationOfferingsRequest
import com.amazonaws.services.ec2.model.DescribeHostReservationsRequest
import com.amazonaws.services.ec2.model.DescribeHostsRequest
import com.amazonaws.services.ec2.model.DescribeInstanceCreditSpecificationsRequest
import com.amazonaws.services.ec2.model.DescribeLaunchTemplateVersionsRequest
import com.amazonaws.services.ec2.model.DescribeLaunchTemplatesRequest
import com.amazonaws.services.ec2.model.DescribeNetworkInterfacePermissionsRequest
import com.amazonaws.services.ec2.model.DescribePrincipalIdFormatRequest
import com.amazonaws.services.ec2.model.DescribeScheduledInstanceAvailabilityRequest
import com.amazonaws.services.ec2.model.DescribeScheduledInstancesRequest
import com.amazonaws.services.ec2.model.DescribeSecurityGroupsRequest
import com.amazonaws.services.ec2.model.DescribeVolumesModificationsRequest
import com.amazonaws.services.ec2.model.DescribeVpcEndpointConnectionNotificationsRequest
import com.amazonaws.services.ec2.model.DescribeVpcEndpointConnectionsRequest
import com.amazonaws.services.ec2.model.DescribeVpcEndpointServiceConfigurationsRequest
import com.amazonaws.services.ec2.model.DescribeVpcEndpointServicePermissionsRequest
import com.amazonaws.services.ec2.model.DisassociateSubnetCidrBlockRequest
import com.amazonaws.services.ec2.model.DisassociateVpcCidrBlockRequest
import com.amazonaws.services.ec2.model.Filter
import com.amazonaws.services.ec2.model.GetHostReservationPurchasePreviewRequest
import com.amazonaws.services.ec2.model.ImportKeyPairRequest
import com.amazonaws.services.ec2.model.IpPermission
import com.amazonaws.services.ec2.model.IpRange
import com.amazonaws.services.ec2.model.ModifyHostsRequest
import com.amazonaws.services.ec2.model.PurchaseHostReservationRequest
import com.amazonaws.services.ec2.model.PurchaseRequest
import com.amazonaws.services.ec2.model.PurchaseScheduledInstancesRequest
import com.amazonaws.services.ec2.model.ReleaseAddressRequest
import com.amazonaws.services.ec2.model.ReleaseHostsRequest
import com.amazonaws.services.ec2.model.RevokeSecurityGroupIngressRequest
import com.amazonaws.services.ec2.model.RunScheduledInstancesRequest
import com.amazonaws.services.ec2.model.ScheduledInstanceRecurrenceRequest
import com.amazonaws.services.ec2.model.ScheduledInstancesLaunchSpecification
import com.amazonaws.services.ec2.model.SlotDateTimeRangeRequest
import com.amazonaws.services.ec2.model.SlotStartTimeRangeRequest
import com.amazonaws.services.ec2.model.UnassignIpv6AddressesRequest
import org.junit.AfterClass
import org.junit.Assert
import org.junit.BeforeClass
import org.junit.Ignore
import org.junit.Test

import java.util.concurrent.TimeUnit

/**
 * Test EC2 api basics
 */
class TestEC2Api {

  private static String testAcct
  private static AWSCredentialsProvider testAcctAdminCredentials
  private static AmazonEC2 ec2Client

  @BeforeClass
  static void init( ){
    N4j.testInfo( TestEC2Api.simpleName )
    N4j.getCloudInfo( )
    testAcct = "${N4j.NAME_PREFIX}ec2-api-test"
    N4j.createAccount( testAcct )
    testAcctAdminCredentials = new AWSStaticCredentialsProvider( N4j.getUserCreds( testAcct, 'admin' ) )
    ec2Client = N4j.getEc2Client( testAcctAdminCredentials, N4j.EC2_ENDPOINT )
  }

  @AfterClass
  static void cleanup( ) {
    if ( ec2Client ) ec2Client.shutdown( )
    N4j.deleteAccount( testAcct )
  }

  @Test
  void testElasticIpAllocateRelease( ){
    N4j.print( "Testing allocate and release for elastic ip" )
    ec2Client.with {
      String ip = allocateAddress( ).with {
        publicIp
      }
      N4j.print( "Allocated elastic ip ${ip}" )

      N4j.print( "Describing addresses to verify allocation" )
      describeAddresses( ).with {
        Assert.assertEquals( "Allocated address list", [ ip ], addresses?.collect{ it.publicIp } )
      }

      releaseAddress( new ReleaseAddressRequest(
        publicIp: ip
      ) )

      N4j.print( "Released elastic ip ${ip}" )

      N4j.print( "Describing addresses to verify release" )
      describeAddresses( ).with {
        Assert.assertEquals( "Allocated address list", [ ], addresses?.collect{ it.publicIp } )
      }
    }
  }

  @Test
  void testKeypairGenerate( ){
    N4j.print( "Testing keypair creation" )
    ec2Client.with {
      String name = 'key-name'
      N4j.print( "Creating key pair with name ${name}" )
      createKeyPair( new CreateKeyPairRequest(
          keyName: name
      ) ).with {
        Assert.assertNotNull( 'Key pair', keyPair )
        keyPair.with {
          Assert.assertNotNull( 'Key fingerprint', keyFingerprint )
          Assert.assertNotNull( 'Key material', keyMaterial )
          Assert.assertEquals( 'Key name', name, keyName)
        }
      }

      N4j.print( "Describing key pairs to verify created" )
      describeKeyPairs( ).with {
        Assert.assertEquals( "Keypair names", [ name ], keyPairs?.collect{ it.keyName } )
      }

      N4j.print( "Deletig key pair with name ${name}" )
      deleteKeyPair( new DeleteKeyPairRequest(
          keyName: name
      ) )

      N4j.print( "Describing key pairs to verify deleted" )
      describeKeyPairs( ).with {
        Assert.assertEquals( "Keypair names", [ ], keyPairs?.collect{ it.keyName } )
      }
    }
  }

  @Test
  void testKeypairImport( ){
    N4j.print( "Testing keypair import" )
    ec2Client.with {
      String name = 'imported-key-name'
      N4j.print( "Importing key pair with name ${name}" )
      importKeyPair( new ImportKeyPairRequest(
          keyName: name,
          publicKeyMaterial:
              'ssh-rsa AAAAB3NzaC1yc2EAAAADAQABAAABAQCderuZ4vY6leI+5xktpuRZHhmYItiafVsS+2kH3t' +
              'iikHcMAOo2P1DTFvfBzKWak1V3Ep9b1y8dMOOeGG1NXtxdMnX2EHi0mQB3vK+Lglx4Ei+2c6hs9X2+' +
              'WC61Dbl/T2lmLmC0AUAoh3JYAE1dIHD22TAxggMCsNLVXB7p2vUOE+XJGe71ox1o8HUXXqop26GSSg' +
              'g/yYkfVN1Rh10SGbHvLbDaRgguJIeywpfmLzIkAP4jOrjFDmjUgoFDbM9lNCGIiyaLRySD2yL8VONd' +
              '4Qn5l6o+eCytJdQseUfVUgaXEy5Eq/JrFrjYSOc1bO6+yTeMG/y3lFbL+qAVYSModbMH admin@n4j'
      ) ).with {
        Assert.assertNotNull( 'Key fingerprint', keyFingerprint )
        Assert.assertEquals( 'Key name', name, keyName)
      }

      N4j.print( "Describing key pairs to verify imported" )
      describeKeyPairs( ).with {
        Assert.assertEquals( "Keypair names", [ name ], keyPairs?.collect{ it.keyName } )
      }

      N4j.print( "Deletig key pair with name ${name}" )
      deleteKeyPair( new DeleteKeyPairRequest(
          keyName: name
      ) )

      N4j.print( "Describing key pairs to verify deleted" )
      describeKeyPairs( ).with {
        Assert.assertEquals( "Keypair names", [ ], keyPairs?.collect{ it.keyName } )
      }
    }
  }

  @Test
  void testSecurityGroupCreateDelete( ){
    N4j.print( "Testing security group create/delete" )
    ec2Client.with {
      final String name = 'group'
      N4j.print( "Creating security group named ${name}" )
      String groupId = createSecurityGroup( new CreateSecurityGroupRequest(
          groupName: name,
          description: 'description'
      ) ).with {
        groupId
      }
      Assert.assertNotNull( 'Security group id', groupId )
      N4j.print( "Created security group with id ${groupId}" )

      N4j.print( "Describing security groups named ${name}" )
      describeSecurityGroups( new DescribeSecurityGroupsRequest(
          groupNames: [ name ]
      ) ).with {
        Assert.assertEquals( 'Groups by name', [ name ], securityGroups.collect{ it.groupName } )
        Assert.assertEquals( 'Groups by id', [ groupId ], securityGroups.collect{ it.groupId } )
      }

      N4j.print( "Describing security groups with id ${groupId}" )
      describeSecurityGroups( new DescribeSecurityGroupsRequest(
          groupIds: [ groupId ]
      ) ).with {
        Assert.assertEquals( 'Groups by name', [ name ], securityGroups.collect{ it.groupName } )
        Assert.assertEquals( 'Groups by id', [ groupId ], securityGroups.collect{ it.groupId } )
      }

      N4j.print( "Deleting security group named ${name}" )
      deleteSecurityGroup( new DeleteSecurityGroupRequest(
          groupName: name
      ) )

      N4j.print( "Describing security groups named ${name} to verify deleted" )
      describeSecurityGroups( new DescribeSecurityGroupsRequest(
          groupNames: [ name ]
      ) ).with {
        Assert.assertEquals( 'Groups by name', [ ], securityGroups.collect{ it.groupName } )
      }
    }
  }

  @Test
  void testSecurityGroupAuthRevoke( ){
    N4j.print( "Testing security group auth/revoke" )
    ec2Client.with {
      final String name = 'auth-revoke-group'
      N4j.print( "Creating security group named ${name}" )
      String groupId = createSecurityGroup( new CreateSecurityGroupRequest(
          groupName: name,
          description: 'description'
      ) ).with {
        groupId
      }
      Assert.assertNotNull( 'Security group id', groupId )
      N4j.print( "Created security group with id ${groupId}" )

      N4j.print( "Authorizing tcp:22 for group ${name}" )
      authorizeSecurityGroupIngress( new AuthorizeSecurityGroupIngressRequest(
          groupName: name,
          ipPermissions: [
              new IpPermission(
                  fromPort: 22,
                  toPort: 22,
                  ipProtocol: 'tcp',
                  ipv4Ranges: [
                    new IpRange(
                        cidrIp: '0.0.0.0/0'
                    )
                  ]
              )
          ]
      ) )

      N4j.print( "Describing security group ${name} to verify ip permission added" )
      describeSecurityGroups( new DescribeSecurityGroupsRequest(
          groupNames: [ name ]
      ) ).with {
        Assert.assertEquals( 'Groups by name', [ name ], securityGroups.collect{ it.groupName } )
        Assert.assertEquals( 'Groups by id', [ groupId ], securityGroups.collect{ it.groupId } )
        securityGroups.get( 0 ).with {
          Assert.assertNotNull( 'ip permissions', ipPermissions )
          Assert.assertEquals( 'ip permissions size', 1, ipPermissions.size() )
          ipPermissions.get( 0 ).with {
            Assert.assertEquals( 'from port', 22, fromPort )
            Assert.assertEquals( 'to port', 22, toPort )
            Assert.assertEquals( 'protocol', 'tcp', ipProtocol )
          }
        }
      }

      N4j.print( "Revoking tcp:22 for group ${name}" )
      revokeSecurityGroupIngress( new RevokeSecurityGroupIngressRequest(
          groupName: name,
          ipPermissions: [
              new IpPermission(
                  fromPort: 22,
                  toPort: 22,
                  ipProtocol: 'tcp',
                  ipv4Ranges: [
                      new IpRange(
                          cidrIp: '0.0.0.0/0'
                      )
                  ]
              )
          ]
      ) )

      N4j.print( "Describing security group ${name} to verify ip permission revoked" )
      describeSecurityGroups( new DescribeSecurityGroupsRequest(
          groupNames: [ name ]
      ) ).with {
        Assert.assertEquals( 'Groups by name', [ name ], securityGroups.collect{ it.groupName } )
        Assert.assertEquals( 'Groups by id', [ groupId ], securityGroups.collect{ it.groupId } )
        securityGroups.get( 0 ).with {
          if ( ipPermissions != null ) {
            Assert.assertEquals( 'ip permissions size', 0, ipPermissions.size() )
          }
        }
      }

      N4j.print( "Deleting security group named ${name}" )
      deleteSecurityGroup( new DeleteSecurityGroupRequest(
          groupName: name
      ) )
    }
  }

  @Test
  void testAccountAttributes( ){
    N4j.print( "Describing account attributes" )
    ec2Client.with {
      describeAccountAttributes( ).with {
        N4j.print( Objects.toString( accountAttributes, '<NULL>' ) )
        Assert.assertNotNull( 'account attributes', accountAttributes )
        Assert.assertFalse( 'account attributes empty', accountAttributes.isEmpty( ) )
      }
    }
  }

  @Test
  void testDescribeRegions( ){
    N4j.print( "Describing regions" )
    ec2Client.with {
      describeRegions( ).with {
        N4j.print( Objects.toString( regions, '<NULL>' ) )
        Assert.assertNotNull( 'regions', regions )
        Assert.assertFalse( 'regions empty', regions.isEmpty( ) )
      }
    }
  }

  @Test
  void testDescribeAvailabilityZones( ){
    N4j.print( "Describing availability zones" )
    ec2Client.with {
      describeAvailabilityZones( ).with {
        N4j.print( Objects.toString( availabilityZones, '<NULL>' ) )
        Assert.assertNotNull( 'availability zones', availabilityZones )
        Assert.assertFalse( 'availability zones empty', availabilityZones.isEmpty( ) )
      }
    }
  }

  @Ignore
  @Test // Enable when functionality is fixed
  void testUnknownAccessKeyError( ) throws Exception {
    N4j.testInfo("${this.getClass().simpleName}.testUnknownAccessKeyError")

    try {
      final AmazonEC2 client = AmazonEC2Client.builder( )
          .withCredentials( new AWSStaticCredentialsProvider( new BasicAWSCredentials( 'AK' + 'I3P5V4A465MVY7N439', 'theseareinvalidcredentialsfVK1QwAGaRUDzE' ) ) )
          .withEndpointConfiguration( new AwsClientBuilder.EndpointConfiguration( EC2_ENDPOINT, 'eucalyptus' ) )
          .build( )
      client.describeAvailabilityZones( )
    } catch ( AmazonServiceException e ) {
      print( "Expected error: ${e}" )
      N4j.assertThat( 'AuthFailure' == e.errorCode, "Expected error code AuthFailure, but was: ${e.errorCode}" )
    }
  }

  @Test
  void testIpv6Stubs( ) throws Exception {
    N4j.testInfo( "${this.getClass().simpleName}.testIpv6Stubs" )

    ec2Client.with{
      try {
        assignIpv6Addresses( new AssignIpv6AddressesRequest(
            networkInterfaceId: 'eni-00000000',
            ipv6Addresses: [
                '2001:db8:1234:1a00::123',
                '2001:db8:1234:1a00::456'
            ]
        ) )
      } catch ( AmazonServiceException e ) {
        print( "Expected error: ${e}" )
        N4j.assertThat( 'InvalidNetworkInterfaceID.NotFound' == e.errorCode, "Expected error code InvalidNetworkInterfaceID.NotFound, but was: ${e.errorCode}" )
      }

      try {
        assignIpv6Addresses( new AssignIpv6AddressesRequest(
            networkInterfaceId: 'eni-00000000',
            ipv6AddressCount: 1
        ) )
      } catch ( AmazonServiceException e ) {
        print( "Expected error: ${e}" )
        N4j.assertThat( 'InvalidNetworkInterfaceID.NotFound' == e.errorCode, "Expected error code InvalidNetworkInterfaceID.NotFound, but was: ${e.errorCode}" )
      }

      try {
        associateSubnetCidrBlock( new AssociateSubnetCidrBlockRequest(
            subnetId: 'subnet-00000000',
            ipv6CidrBlock: '2001:db8:1234:1a00::/64'
        ) )
      } catch ( AmazonServiceException e ) {
        print( "Expected error: ${e}" )
        N4j.assertThat( 'InvalidSubnetID.NotFound' == e.errorCode, "Expected error code InvalidSubnetID.NotFound, but was: ${e.errorCode}" )
      }

      try {
        associateVpcCidrBlock( new AssociateVpcCidrBlockRequest(
            vpcId: 'vpc-00000000',
            amazonProvidedIpv6CidrBlock: true
        ) )
      } catch ( AmazonServiceException e ) {
        print( "Expected error: ${e}" )
        N4j.assertThat( 'InvalidVpcID.NotFound' == e.errorCode, "Expected error code InvalidVpcID.NotFound, but was: ${e.errorCode}" )
      }

      try {
        createEgressOnlyInternetGateway( new CreateEgressOnlyInternetGatewayRequest(
            vpcId: 'vpc-00000000',
            // clientToken: UUID.randomUUID( ).toString( ) // error on aws/ec2: ClientToken is not recognized
        ) )
      } catch ( AmazonServiceException e ) {
        print( "Expected error: ${e}" )
        N4j.assertThat( 'InvalidVpcID.NotFound' == e.errorCode, "Expected error code InvalidVpcID.NotFound, but was: ${e.errorCode}" )
      }

      try {
        deleteEgressOnlyInternetGateway( new DeleteEgressOnlyInternetGatewayRequest(
            egressOnlyInternetGatewayId: 'eigw-00000000000000000'
        ) )
      } catch ( AmazonServiceException e ) {
        print( "Expected error: ${e}" )
        N4j.assertThat( 'InvalidGatewayID.NotFound' == e.errorCode, "Expected error code InvalidGatewayID.NotFound, but was: ${e.errorCode}" )
      }

      try {
        describeEgressOnlyInternetGateways( new DescribeEgressOnlyInternetGatewaysRequest(
            egressOnlyInternetGatewayIds: [
                'eigw-00000000000000000'
            ],
            maxResults: 100,
            nextToken: 'invalid-next-token'
        ) )
      } catch ( AmazonServiceException e ) {
        print( "Expected error: ${e}" )
        N4j.assertThat( 'InvalidNextToken' == e.errorCode, "Expected error code InvalidNextToken, but was: ${e.errorCode}" )
      }

      try {
        disassociateSubnetCidrBlock( new DisassociateSubnetCidrBlockRequest(
            associationId: 'subnet-cidr-assoc-00000000'
        ) )
      } catch ( AmazonServiceException e ) {
        print( "Expected error: ${e}" )
        N4j.assertThat( 'InvalidSubnetCidrBlockAssociationID.NotFound' == e.errorCode, "Expected error code InvalidSubnetCidrBlockAssociationID.NotFound, but was: ${e.errorCode}" )
      }

      try {
        disassociateVpcCidrBlock( new DisassociateVpcCidrBlockRequest(
            associationId: 'vpc-cidr-assoc-00000000'
        ) )
      } catch ( AmazonServiceException e ) {
        print( "Expected error: ${e}" )
        N4j.assertThat( 'InvalidVpcCidrBlockAssociationID.NotFound' == e.errorCode, "Expected error code InvalidVpcCidrBlockAssociationID.NotFound, but was: ${e.errorCode}" )
      }

      try {
        unassignIpv6Addresses( new UnassignIpv6AddressesRequest(
            networkInterfaceId: 'eni-00000000',
            ipv6Addresses: [
                '2001:db8:1234:1a00::123',
                '2001:db8:1234:1a00::456'
            ]
        ) )
      } catch ( AmazonServiceException e ) {
        print( "Expected error: ${e}" )
        N4j.assertThat( 'InvalidNetworkInterfaceID.NotFound' == e.errorCode, "Expected error code InvalidNetworkInterfaceID.NotFound, but was: ${e.errorCode}" )
      }

      void
    }
  }

  @Test
  void testScheduledInstanceStubs( ) throws Exception {
    N4j.testInfo( "${this.getClass().simpleName}.testScheduledInstanceStubs" )

    ec2Client.with{
      try {
        print( 'Describing scheduled instance availability')
        print( describeScheduledInstanceAvailability( new DescribeScheduledInstanceAvailabilityRequest(
            recurrence: new ScheduledInstanceRecurrenceRequest(
                frequency: 'Monthly',
                interval: 1,
                occurrenceDays: [1, 3, 5, 7],
                occurrenceRelativeToEnd: false,
                occurrenceUnit: 'DayOfMonth'
            ),
            firstSlotStartTimeRange: new SlotDateTimeRangeRequest(
                earliestTime: new Date( System.currentTimeMillis( ) + TimeUnit.HOURS.toMillis( 6 ) ),
                latestTime: new Date( System.currentTimeMillis( ) + TimeUnit.HOURS.toMillis( 12 ) )
            ),
            minSlotDurationInHours: 1,
            maxSlotDurationInHours: 24,
            nextToken: 'invalid-token',
            maxResults: 100,
            filters: [
                new Filter( name: 'network-platform', values: [ 'EC2-VPC' ] )
            ]
        ) ).toString( ) )
      } catch ( AmazonServiceException e ) {
        print( "Expected error: ${e}" )
        N4j.assertThat( 'InvalidPaginationToken' == e.errorCode, "Expected error code InvalidPaginationToken, but was: ${e.errorCode}" )
      }

      try {
        print( 'Describing scheduled instances')
        print( describeScheduledInstances( new DescribeScheduledInstancesRequest(
            scheduledInstanceIds: [ 'sci-00000000000000000' ],
            slotStartTimeRange: new SlotStartTimeRangeRequest(
                earliestTime: new Date( ),
                latestTime: new Date( System.currentTimeMillis( ) + TimeUnit.DAYS.toMillis( 365 ) )
            ),
            nextToken: 'invalid-token',
            maxResults: 100,
            filters: [
                new Filter( name: 'a', values: [ 'b' ] )
            ]
        ) ).toString( ) )
      } catch ( AmazonServiceException e ) {
        print( "Expected error: ${e}" )
        N4j.assertThat( 'InvalidParameterValue' == e.errorCode, "Expected error code InvalidParameterValue, but was: ${e.errorCode}" )
      }

      try {
        purchaseScheduledInstances( new PurchaseScheduledInstancesRequest(
            clientToken: UUID.randomUUID( ).toString( ),
            purchaseRequests: [
                new PurchaseRequest(
                    purchaseToken: 'dGhpcyBpcyBub3QgYSB2YWxpZCB0b2tlbgo=',
                    instanceCount: 1
                )
            ]
        ) )
      } catch ( AmazonServiceException e ) {
        print( "Expected error: ${e}" )
        N4j.assertThat( 'InvalidPurchaseToken.Malformed' == e.errorCode, "Expected error code InvalidPurchaseToken.Malformed, but was: ${e.errorCode}" )
      }

      try {
        runScheduledInstances( new RunScheduledInstancesRequest(
            clientToken: UUID.randomUUID( ).toString( ),
            instanceCount: 1,
            scheduledInstanceId: 'sci-00000000000000000',
            launchSpecification: new ScheduledInstancesLaunchSpecification(
                imageId: 'emi-00000000',
                subnetId: 'subnet-00000000',
                keyName: 'my-key',
                securityGroupIds: [ 'sg-00000000' ]
            )
        ) )
      } catch ( AmazonServiceException e ) {
        print( "Expected error: ${e}" )
        N4j.assertThat( 'InvalidScheduledInstance' == e.errorCode, "Expected error code InvalidScheduledInstance, but was: ${e.errorCode}" )
      }

      void
    }
  }

  @Test
  void testHostsStubs( ) throws Exception {
    N4j.testInfo( "${this.getClass().simpleName}.testHostsStubs" )

    ec2Client.with{
      try {
        allocateHosts( new AllocateHostsRequest(
            autoPlacement: 'off',
            availabilityZone: 'some-zone',
            clientToken: UUID.randomUUID( ).toString( ),
            instanceType: N4j.INSTANCE_TYPE,
            quantity: 1
        ) )
      } catch ( AmazonServiceException e ) {
        print( "Expected error: ${e}" )
        N4j.assertThat( 'InvalidRequest' == e.errorCode, "Expected error code InvalidRequest, but was: ${e.errorCode}" )
      }

      try {
        print( 'Describing host reservation offering')
        print( describeHostReservationOfferings( new DescribeHostReservationOfferingsRequest(
            offeringId: 'hro-00000000',
            minDuration: 1,
            maxDuration: 1,
            filter: [
                new Filter( name: 'a', values: [ 'b' ] )
            ]
        ) ).toString( ) )
      } catch ( AmazonServiceException e ) {
        print( "Expected error: ${e}" )
        N4j.assertThat( 'InvalidHostReservationOfferingId.NotFound' == e.errorCode, "Expected error code InvalidHostReservationOfferingId.NotFound, but was: ${e.errorCode}" )
      }

      try {
        print( 'Describing host reservations')
        print( describeHostReservations( new DescribeHostReservationsRequest(
            hostReservationIdSet: [ 'hr-00000000' ],
            filter: [
                new Filter( name: 'a', values: [ 'b' ] )
            ]
        ) ).toString( ) )
      } catch ( AmazonServiceException e ) {
        print( "Expected error: ${e}" )
        N4j.assertThat( 'InvalidHostReservationId.NotFound' == e.errorCode, "Expected error code InvalidHostReservationId.NotFound, but was: ${e.errorCode}" )
      }

      try {
        print( 'Describing hosts')
        print( describeHosts( new DescribeHostsRequest(
            hostIds: [ 'h-00000000' ],
            filter: [
                new Filter( name: 'a', values: [ 'b' ] )
            ]
        ) ).toString( ) )
      } catch ( AmazonServiceException e ) {
        print( "Expected error: ${e}" )
        N4j.assertThat( 'InvalidHostID.NotFound' == e.errorCode, "Expected error code InvalidHostID.NotFound, but was: ${e.errorCode}" )
      }

      try {
        getHostReservationPurchasePreview( new GetHostReservationPurchasePreviewRequest(
            offeringId: 'hro-00000000',
            hostIdSet: [ 'h-00000000' ]
        ) )
      } catch ( AmazonServiceException e ) {
        print( "Expected error: ${e}" )
        N4j.assertThat( 'InvalidHostReservationOfferingId.NotFound' == e.errorCode, "Expected error code InvalidHostReservationOfferingId.NotFound, but was: ${e.errorCode}" )
      }

      print( 'Modifying hosts')
      print( modifyHosts( new ModifyHostsRequest(
          autoPlacement: 'off',
          hostIds: [ 'h-00000000' ]
      ) ).toString( ) )

      try {
        purchaseHostReservation( new PurchaseHostReservationRequest(
            offeringId: 'hro-00000000',
            hostIdSet: [ 'h-00000000' ],
            limitPrice: '1.00',
            currencyCode: 'USD',
            clientToken: UUID.randomUUID( ).toString( )
        ) )
      } catch ( AmazonServiceException e ) {
        print( "Expected error: ${e}" )
        N4j.assertThat( 'InvalidParameter' == e.errorCode, "Expected error code InvalidParameter, but was: ${e.errorCode}" )

      }

      print( 'Releasing hosts')
      print( releaseHosts( new ReleaseHostsRequest(
          hostIds: [ 'h-00000000' ]
      ) ).toString( ) )

      void
    }
  }

  @Test
  void testFleetStubs( ) throws Exception {
    N4j.testInfo("${this.getClass().simpleName}.testFleetStubs")

    ec2Client.with {
      describeFleets( new DescribeFleetsRequest(
          filters: [ new Filter(
              name: 'fleet-state',
              values: ['active']
          ) ],
          fleetIds: [ 'fleet-00000000000000000' ]
      ) )
      describeFleetHistory( new DescribeFleetHistoryRequest(
          eventType: 'fleet-change',
          fleetId: 'fleet-00000000000000000',
          startTime: new Date()
      ) )
      describeFleetInstances( new DescribeFleetInstancesRequest(
          filters: [ new Filter(
              name: 'instance-type',
              values: ['t2-*']
          ) ],
          fleetId: 'fleet-00000000000000000'
      ) )
    }
  }

  @Test
  void testGeneralStubs( ) {
    N4j.testInfo("${this.getClass().simpleName}.testGeneralStubs")

    ec2Client.with {
      describeAggregateIdFormat(new DescribeAggregateIdFormatRequest())
      describePrincipalIdFormat(new DescribePrincipalIdFormatRequest())

      describeElasticGpus(new DescribeElasticGpusRequest(
          filters: [
              new Filter( name: 'instance-id', values: [ 'i-00000000000000000' ] )
          ],
          elasticGpuIds: [ 'egpu-00000000000000000' ]
      ))

      describeFpgaImages(new DescribeFpgaImagesRequest(
          filters: [
              new Filter( name: 'fpga-image-id', values: [ 'afi-00000000000000000' ] )
          ],
          fpgaImageIds: [ 'afi-00000000000000000' ],
          owners: [ 'self' ]
      ))

      describeInstanceCreditSpecifications(new DescribeInstanceCreditSpecificationsRequest(
          filters: [
              new Filter( name: 'instance-id', values: [ 'i-00000000000000000' ] )
          ],
          instanceIds: [ 'i-00000000000000000' ]
      ))

      describeLaunchTemplates(new DescribeLaunchTemplatesRequest(
          filters: [
              new Filter( name: 'launch-template-name', values: [ '*' ] )
          ],
          launchTemplateIds: [ 'lt-00000000000000000' ],
          launchTemplateNames: [ 'LaunchTemplate1' ]
      ))

      describeLaunchTemplateVersions(new DescribeLaunchTemplateVersionsRequest(
          filters: [
              new Filter( name: 'image-id', values: [ 'ami-00000000000000000' ] )
          ],
          launchTemplateId: 'lt-00000000000000000',
          versions: [ '1' ]
      ))

      describeNetworkInterfacePermissions(new DescribeNetworkInterfacePermissionsRequest(
          filters: [
              new Filter(
                  name: 'network-interface-permission.network-interface-id',
                  values: [ 'eni-00000000000000000' ]
              )
          ],
          networkInterfacePermissionIds: [ 'eni-perm-00000000000000000' ]
      ))

      describeVolumesModifications(new DescribeVolumesModificationsRequest(
          filters: [
              new Filter( name: 'volume-id', values: [ 'vol-00000000000000000' ] )
          ],
          volumeIds: [ 'vol-00000000000000000' ]
      ))
    }
  }

  @Test
  void testVpcStubs( ) throws Exception {
    N4j.testInfo("${this.getClass().simpleName}.testVpcStubs")

    ec2Client.with {
      describeVpcEndpointConnectionNotifications( new DescribeVpcEndpointConnectionNotificationsRequest(
          filters: [
              new Filter( name: 'connection-notification-id', values: [ '*' ] )
          ]
      ) )

      describeVpcEndpointConnections( new DescribeVpcEndpointConnectionsRequest(
          filters: [
              new Filter( name: 'vpc-endpoint-id', values: [ '*' ] )
          ]
      ) )

      describeVpcEndpointServiceConfigurations(new DescribeVpcEndpointServiceConfigurationsRequest(
          filters: [
              new Filter( name: 'service-name', values: [ '*' ] )
          ],
          serviceIds: [ 'vpce-svc-00000000000000000' ]
      ))

      describeVpcEndpointServicePermissions(new DescribeVpcEndpointServicePermissionsRequest(
          filters: [
              new Filter( name: 'principal', values: [ '*' ] )
          ],
          serviceId: 'vpce-svc-03d5ebb7d9579a123'
      ))
    }
  }
}
