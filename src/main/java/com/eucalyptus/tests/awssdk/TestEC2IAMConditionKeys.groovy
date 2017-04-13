package com.eucalyptus.tests.awssdk

import com.amazonaws.AmazonServiceException
import com.amazonaws.auth.AWSCredentialsProvider
import com.amazonaws.auth.AWSStaticCredentialsProvider
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration
import com.amazonaws.services.ec2.AmazonEC2
import com.amazonaws.services.ec2.AmazonEC2Client
import com.amazonaws.services.ec2.model.AttachInternetGatewayRequest
import com.amazonaws.services.ec2.model.AttachVolumeRequest
import com.amazonaws.services.ec2.model.AuthorizeSecurityGroupEgressRequest
import com.amazonaws.services.ec2.model.AuthorizeSecurityGroupIngressRequest
import com.amazonaws.services.ec2.model.CreateDhcpOptionsRequest
import com.amazonaws.services.ec2.model.CreateNetworkAclEntryRequest
import com.amazonaws.services.ec2.model.CreateNetworkAclRequest
import com.amazonaws.services.ec2.model.CreateRouteRequest
import com.amazonaws.services.ec2.model.CreateRouteTableRequest
import com.amazonaws.services.ec2.model.CreateSecurityGroupRequest
import com.amazonaws.services.ec2.model.CreateTagsRequest
import com.amazonaws.services.ec2.model.CreateVolumeRequest
import com.amazonaws.services.ec2.model.CreateVpcRequest
import com.amazonaws.services.ec2.model.DeleteDhcpOptionsRequest
import com.amazonaws.services.ec2.model.DeleteInternetGatewayRequest
import com.amazonaws.services.ec2.model.DeleteNetworkAclEntryRequest
import com.amazonaws.services.ec2.model.DeleteNetworkAclRequest
import com.amazonaws.services.ec2.model.DeleteRouteRequest
import com.amazonaws.services.ec2.model.DeleteRouteTableRequest
import com.amazonaws.services.ec2.model.DeleteSecurityGroupRequest
import com.amazonaws.services.ec2.model.DeleteTagsRequest
import com.amazonaws.services.ec2.model.DeleteVolumeRequest
import com.amazonaws.services.ec2.model.DeleteVpcRequest
import com.amazonaws.services.ec2.model.DescribeImagesRequest
import com.amazonaws.services.ec2.model.DescribeInstancesRequest
import com.amazonaws.services.ec2.model.DescribeTagsRequest
import com.amazonaws.services.ec2.model.DescribeVolumesRequest
import com.amazonaws.services.ec2.model.DetachInternetGatewayRequest
import com.amazonaws.services.ec2.model.DetachVolumeRequest
import com.amazonaws.services.ec2.model.Filter
import com.amazonaws.services.ec2.model.IpPermission
import com.amazonaws.services.ec2.model.Placement
import com.amazonaws.services.ec2.model.RebootInstancesRequest
import com.amazonaws.services.ec2.model.RevokeSecurityGroupEgressRequest
import com.amazonaws.services.ec2.model.RevokeSecurityGroupIngressRequest
import com.amazonaws.services.ec2.model.RunInstancesRequest
import com.amazonaws.services.ec2.model.StartInstancesRequest
import com.amazonaws.services.ec2.model.StopInstancesRequest
import com.amazonaws.services.ec2.model.Tag
import com.amazonaws.services.ec2.model.TagSpecification
import com.amazonaws.services.ec2.model.TerminateInstancesRequest
import com.amazonaws.services.identitymanagement.AmazonIdentityManagement
import com.amazonaws.services.identitymanagement.AmazonIdentityManagementClient
import com.amazonaws.services.identitymanagement.model.PutUserPolicyRequest
import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClient
import com.amazonaws.services.securitytoken.model.GetCallerIdentityRequest
import org.testng.Assert
import org.testng.annotations.AfterClass
import org.testng.annotations.AfterMethod
import org.testng.annotations.BeforeClass
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test

import java.util.concurrent.TimeUnit

import static com.eucalyptus.tests.awssdk.N4j.EC2_ENDPOINT
import static com.eucalyptus.tests.awssdk.N4j.IAM_ENDPOINT
import static com.eucalyptus.tests.awssdk.N4j.TOKENS_ENDPOINT
import static com.eucalyptus.tests.awssdk.N4j.assumeThat
import static com.eucalyptus.tests.awssdk.N4j.createAccount
import static com.eucalyptus.tests.awssdk.N4j.createUser
import static com.eucalyptus.tests.awssdk.N4j.deleteAccount
import static com.eucalyptus.tests.awssdk.N4j.eucaUUID
import static com.eucalyptus.tests.awssdk.N4j.getCloudInfo
import static com.eucalyptus.tests.awssdk.N4j.getUserKeys
import static com.eucalyptus.tests.awssdk.N4j.print
import static com.eucalyptus.tests.awssdk.N4j.waitForInstances
import static com.eucalyptus.tests.awssdk.N4j.waitForVolumeAttachments

/**
 * Test for IAM policy condition keys used with EC2
 *
 * This provides coverage for:
 *   https://eucalyptus.atlassian.net/browse/EUCA-8962
 *   https://eucalyptus.atlassian.net/browse/EUCA-11770
 *   https://eucalyptus.atlassian.net/browse/EUCA-13332
 */
class TestEC2IAMConditionKeys {

  // for all tests
  private String account
  private String accountNumber
  private AWSCredentialsProvider accountCredentials
  private AmazonEC2 accountEc2
  private AmazonIdentityManagement accountIam
  private int tagWaitSeconds = 10

  // for each test
  private List<Runnable> cleanupTasks
  private String user
  private AWSCredentialsProvider userCredentials

  @BeforeClass
  void init( ) {
    print("### SETUP - ${getClass().simpleName}")
    getCloudInfo( )
    account = "${getClass().simpleName.toLowerCase( )}-${eucaUUID()}"
    createAccount( account )
    accountCredentials = new AWSStaticCredentialsProvider(
        getUserKeys( account, 'admin' ).with{ it -> new BasicAWSCredentials( it['ak'], it['sk'] ) } )
    accountEc2 = AmazonEC2Client.builder( )
        .withCredentials( accountCredentials )
        .withEndpointConfiguration( new EndpointConfiguration( EC2_ENDPOINT, "eucalyptus" ) )
        .build( )
    accountIam = AmazonIdentityManagementClient.builder( )
        .withCredentials( accountCredentials )
        .withEndpointConfiguration( new EndpointConfiguration( IAM_ENDPOINT, "eucalyptus" ) )
        .build( )
    accountNumber = AWSSecurityTokenServiceClient.builder( )
        .withCredentials( accountCredentials )
        .withEndpointConfiguration( new EndpointConfiguration( TOKENS_ENDPOINT, "eucalyptus" ) )
        .build( ).getCallerIdentity( new GetCallerIdentityRequest( ) ).account
  }

  @AfterClass
  void teardown( ) {
    print("### CLEANUP - ${getClass().simpleName}")
    if ( account ) {
      deleteAccount( account )
    }
  }

  @BeforeMethod
  void initTest(  ) {
    print( "Initializing user and clean up tasks" )
    cleanupTasks = [ ]
    user = "${getClass().simpleName.toLowerCase( )}-user-${eucaUUID()}"
    createUser( account, user ) // deleted with account
    userCredentials = new AWSStaticCredentialsProvider(
        getUserKeys( account, user ).with{ it -> new BasicAWSCredentials( it['ak'], it['sk'] ) } )
  }

  @AfterMethod
  void cleanup( ) {
    Collections.reverse(cleanupTasks)
    for (final Runnable cleanupTask : cleanupTasks) {
      try {
        cleanupTask.run()
      } catch (Exception e) {
        print("Unable to run clean up task: ${e}")
      }
    }
  }

  @Test
  void testTaggedResourceDeletion( ) {
    accountIam.with{
      putUserPolicy( new PutUserPolicyRequest(
          userName: this.user,
          policyName: 'ec2-delete-if-tagged',
          policyDocument: '''\
          {
              "Version": "2012-10-17",
              "Statement": [
                  {
                      "Effect": "Allow",
                      "Action": [ "ec2:DeleteSecurityGroup", "ec2:DeleteVolume" ],
                      "Resource": "*",
                      "Condition": {
                          "StringEquals": {
                              "ec2:ResourceTag/delete": "true"
                          }
                      }
                  }
              ]
          }
        '''.stripIndent( )
      ) )
    }

    String securityGroupId = null
    String volumeId = null
    accountEc2.with {
      // Find an AZ to use
      String availabilityZone = describeAvailabilityZones( ).with{
        availabilityZones?.getAt( 0 )?.zoneName
      }
      print( "Using availability zone: ${availabilityZone}" )
      Assert.assertNotNull( availabilityZone, "Expected availability zone" )

      print( 'Creating volume for delete test' )
      volumeId = createVolume( new CreateVolumeRequest(
          size: 1,
          availabilityZone: availabilityZone
      ) ).with {
        volume?.volumeId
      }
      print( "Created volume: ${volumeId}" )
      Assert.assertNotNull( volumeId, "Expected volumeId" )
      cleanupTasks.add{
        print( "Deleting volume ${volumeId}" )
        deleteVolume( new DeleteVolumeRequest( volumeId: volumeId ) )
      }

      print( 'Creating security group for delete test' )
      securityGroupId = createSecurityGroup( new CreateSecurityGroupRequest(
          groupName: 'tagged-resource-delete-test',
          description: 'Group for testing delete of tagged resources'
      ) ).with {
        groupId
      }
      print( "Created security group: ${securityGroupId}" )
      Assert.assertNotNull( securityGroupId, "Expected securityGroupId" )
      cleanupTasks.add{
        print( "Deleting security group ${securityGroupId}" )
        deleteSecurityGroup( new DeleteSecurityGroupRequest( groupId: securityGroupId ) )
      }
    }

    AmazonEC2 userEc2 = AmazonEC2Client.builder( )
        .withCredentials( userCredentials )
        .withEndpointConfiguration( new EndpointConfiguration( EC2_ENDPOINT, "eucalyptus" ) )
        .build( )
    userEc2.with {
      try {
        print( "Attempting delete of untagged security group ${securityGroupId} as user" )
        deleteSecurityGroup( new DeleteSecurityGroupRequest( groupId: securityGroupId ) )
        Assert.fail( 'Expected security group delete to fail' )
      } catch ( AmazonServiceException e ) {
        print( "Got expected exception: ${e}" )
      }

      try {
        print( "Attempting delete of untagged volume ${volumeId} as user" )
        deleteVolume( new DeleteVolumeRequest( volumeId: volumeId ) )
        Assert.fail( 'Expected volume delete to fail' )
      } catch ( AmazonServiceException e ) {
        print( "Got expected exception: ${e}" )
      }
      void
    }

    accountEc2.with {
      print( "Tagging security group ${securityGroupId} and volume ${volumeId} with delete=true" )
      createTags( new CreateTagsRequest(
          resources: [ securityGroupId, volumeId ],
          tags: [
              new Tag( key: 'delete', value: 'true' )
          ]
      ) )
    }

    print( "Sleeping to allow tags to apply" )
    N4j.sleep( tagWaitSeconds )

    userEc2.with {
      print( "Attempting delete of tagged security group ${securityGroupId} as user" )
      deleteSecurityGroup( new DeleteSecurityGroupRequest( groupId: securityGroupId ) )

      print( "Attempting delete of tagged volume ${volumeId} as user" )
      deleteVolume( new DeleteVolumeRequest( volumeId: volumeId ) )
    }
  }

  @Test
  void testTaggedVpcResourceDeletion( ) {
    accountIam.with{
      putUserPolicy( new PutUserPolicyRequest(
          userName: this.user,
          policyName: 'ec2-delete-if-tagged',
          policyDocument: '''\
          {
              "Version": "2012-10-17",
              "Statement": [
                  {
                      "Effect": "Allow",
                      "Action": [
                          "ec2:DeleteDhcpOptions",
                          "ec2:DeleteInternetGateway",
                          "ec2:DeleteNetworkAcl",
                          "ec2:DeleteNetworkAclEntry",
                          "ec2:DeleteRoute",
                          "ec2:DeleteRouteTable"
                      ],
                      "Resource": "*",
                      "Condition": {
                          "StringEquals": {
                              "ec2:ResourceTag/delete": "true"
                          }
                      }
                  }
              ]
          }
        '''.stripIndent( )
      ) )
    }

    String dhcpOptionsId = null
    String internetGatewayId = null
    String networkAclId = null
    String routeTableId = null
    accountEc2.with {
      print( 'Creating vpc to contain resources for delete' )
      String vpcId = createVpc( new CreateVpcRequest(
          cidrBlock: '10.0.0.0/16'
      ) ).with {
        vpc?.vpcId
      }
      print( "Created vpc: ${vpcId}" )
      Assert.assertNotNull( vpcId, "Expected vpcId" )
      cleanupTasks.add{
        print( "Deleting vpc ${vpcId}" )
        deleteVpc( new DeleteVpcRequest( vpcId: vpcId ) )
      }

      print( 'Creating dhcp options for delete test' )
      dhcpOptionsId = createDhcpOptions( new CreateDhcpOptionsRequest( ) ).with {
        dhcpOptions?.dhcpOptionsId
      }
      print( "Created dhcp options: ${dhcpOptionsId}" )
      Assert.assertNotNull( dhcpOptionsId, "Expected dhcpOptionsId" )
      cleanupTasks.add{
        print( "Deleting dhcp options ${dhcpOptionsId}" )
        deleteDhcpOptions( new DeleteDhcpOptionsRequest( dhcpOptionsId: dhcpOptionsId ) )
      }

      print( 'Creating internet gateway for delete test' )
      internetGatewayId = createInternetGateway( ).with {
        internetGateway?.internetGatewayId
      }
      print( "Created internet gateway: ${internetGatewayId}" )
      Assert.assertNotNull( internetGatewayId, "Expected internetGatewayId" )
      cleanupTasks.add{
        print( "Deleting internet gateway ${internetGatewayId}" )
        deleteInternetGateway( new DeleteInternetGatewayRequest( internetGatewayId: internetGatewayId ) )
      }

      print( 'Creating network acl for delete test' )
      networkAclId = createNetworkAcl( new CreateNetworkAclRequest( vpcId: vpcId ) ).with {
        networkAcl?.networkAclId
      }
      print( "Created network acl: ${networkAclId}" )
      Assert.assertNotNull( networkAclId, "Expected networkAclId" )
      cleanupTasks.add{
        print( "Deleting network acl ${networkAclId}" )
        deleteNetworkAcl( new DeleteNetworkAclRequest( networkAclId: networkAclId ) )
      }

      print( 'Creating network acl entry for delete test' )
      createNetworkAclEntry( new CreateNetworkAclEntryRequest(
          networkAclId: networkAclId,
          ruleNumber: 1000,
          ruleAction: 'allow',
          egress: false,
          cidrBlock: '0.0.0.0/0',
          protocol: -1
      ) )
      print( "Created network acl entry ${networkAclId}/rule#:1000" )

      print( 'Creating route table for delete test' )
      routeTableId = createRouteTable( new CreateRouteTableRequest( vpcId: vpcId ) ).with {
        routeTable?.routeTableId
      }
      print( "Created route table: ${routeTableId}" )
      Assert.assertNotNull( routeTableId, "Expected routeTableId" )
      cleanupTasks.add{
        print( "Deleting route table ${routeTableId}" )
        deleteRouteTable( new DeleteRouteTableRequest( routeTableId: routeTableId ) )
      }

      // attached briefly to allow route creation
      attachInternetGateway( new AttachInternetGatewayRequest( vpcId: vpcId, internetGatewayId: internetGatewayId ) )

      print( 'Creating route for delete test' )
      createRoute( new CreateRouteRequest(
          routeTableId: routeTableId,
          gatewayId: internetGatewayId,
          destinationCidrBlock: '1.1.1.1/32'
      ) )
      print( "Created route ${routeTableId}/1.1.1.1" )

      detachInternetGateway( new DetachInternetGatewayRequest( vpcId: vpcId, internetGatewayId: internetGatewayId ) )

      void
    }

    AmazonEC2 userEc2 = AmazonEC2Client.builder( )
        .withCredentials( userCredentials )
        .withEndpointConfiguration( new EndpointConfiguration( EC2_ENDPOINT, "eucalyptus" ) )
        .build( )
    userEc2.with {
      try {
        print( "Attempting delete of untagged dhcp options ${dhcpOptionsId} as user" )
        deleteDhcpOptions( new DeleteDhcpOptionsRequest( dhcpOptionsId: dhcpOptionsId ) )
        Assert.fail( 'Expected dhcp options delete to fail' )
      } catch ( AmazonServiceException e ) {
        print( "Got expected exception: ${e}" )
      }

      try {
        print( "Attempting delete of untagged internet gateway ${internetGatewayId} as user" )
        deleteInternetGateway( new DeleteInternetGatewayRequest( internetGatewayId: internetGatewayId ) )
        Assert.fail( 'Expected internet gateway delete to fail' )
      } catch ( AmazonServiceException e ) {
        print( "Got expected exception: ${e}" )
      }

      try {
        print( "Attempting delete of entry from untagged network acl ${networkAclId}/rule#:1000 as user" )
        deleteNetworkAclEntry( new DeleteNetworkAclEntryRequest(
            networkAclId: networkAclId,
            ruleNumber: 1000,
            egress: false
        ) )
        Assert.fail( 'Expected network acl entry delete to fail' )
      } catch ( AmazonServiceException e ) {
        print( "Got expected exception: ${e}" )
      }

      try {
        print( "Attempting delete of untagged network acl ${networkAclId} as user" )
        deleteNetworkAcl( new DeleteNetworkAclRequest( networkAclId: networkAclId ) )
        Assert.fail( 'Expected network acl delete to fail' )
      } catch ( AmazonServiceException e ) {
        print( "Got expected exception: ${e}" )
      }

      try {
        print( "Attempting delete of entry from untagged route table ${routeTableId}/1.1.1.1 as user" )
        deleteRoute( new DeleteRouteRequest(
            routeTableId: routeTableId,
            destinationCidrBlock: '1.1.1.1/32'
        ) )
        Assert.fail( 'Expected network acl entry delete to fail' )
      } catch ( AmazonServiceException e ) {
        print( "Got expected exception: ${e}" )
      }

      try {
        print( "Attempting delete of untagged route table ${routeTableId} as user" )
        deleteRouteTable( new DeleteRouteTableRequest( routeTableId: routeTableId ) )
        Assert.fail( 'Expected route table delete to fail' )
      } catch ( AmazonServiceException e ) {
        print( "Got expected exception: ${e}" )
      }

      void
    }

    accountEc2.with {
      print( "Tagging resources ${dhcpOptionsId}, ${internetGatewayId}, ${networkAclId}, ${routeTableId} with delete=true" )
      createTags( new CreateTagsRequest(
          resources: [ dhcpOptionsId, internetGatewayId, networkAclId, routeTableId ],
          tags: [
              new Tag( key: 'delete', value: 'true' )
          ]
      ) )
    }

    print( "Sleeping to allow tags to apply" )
    N4j.sleep( tagWaitSeconds )

    userEc2.with {
      print( "Attempting delete of tagged dhcp options ${dhcpOptionsId} as user" )
      deleteDhcpOptions( new DeleteDhcpOptionsRequest( dhcpOptionsId: dhcpOptionsId ) )

      print( "Attempting delete of tagged internet gateway ${internetGatewayId} as user" )
      deleteInternetGateway( new DeleteInternetGatewayRequest( internetGatewayId: internetGatewayId ) )

      print( "Attempting delete of entry from tagged network acl ${networkAclId}/rule#:1000 as user" )
      deleteNetworkAclEntry( new DeleteNetworkAclEntryRequest(
          networkAclId: networkAclId,
          ruleNumber: 1000,
          egress: false
      ) )

      print( "Attempting delete of tagged network acl ${networkAclId} as user" )
      deleteNetworkAcl( new DeleteNetworkAclRequest( networkAclId: networkAclId ) )

      print( "Attempting delete of entry from tagged route table ${routeTableId}/1.1.1.1 as user" )
      deleteRoute( new DeleteRouteRequest(
          routeTableId: routeTableId,
          destinationCidrBlock: '1.1.1.1/32'
      ) )

      print( "Attempting delete of tagged route table ${routeTableId} as user" )
      deleteRouteTable( new DeleteRouteTableRequest( routeTableId: routeTableId ) )
    }
  }

  @Test
  void testTaggedSecurityGroupUpdate( ) {
    accountIam.with{
      putUserPolicy( new PutUserPolicyRequest(
          userName: this.user,
          policyName: 'ec2-update-if-tagged',
          policyDocument: '''\
          {
              "Version": "2012-10-17",
              "Statement": [
                  {
                      "Effect": "Allow",
                      "Action": [ "ec2:AuthorizesecurityGroupIngress", "ec2:RevokeSecurityGroupIngress" ],
                      "Resource": "*",
                      "Condition": {
                          "StringEquals": {
                              "ec2:ResourceTag/update": "true"
                          }
                      }
                  }
              ]
          }
        '''.stripIndent( )
      ) )
    }

    String securityGroupId = null
    accountEc2.with {
      print( 'Creating security group for update test' )
      securityGroupId = createSecurityGroup( new CreateSecurityGroupRequest(
          groupName: 'tagged-resource-update-test',
          description: 'Group for testing update of tagged resources'
      ) ).with {
        groupId
      }
      print( "Created security group: ${securityGroupId}" )
      Assert.assertNotNull( securityGroupId, "Expected securityGroupId" )
      cleanupTasks.add{
        print( "Deleting security group ${securityGroupId}" )
        deleteSecurityGroup( new DeleteSecurityGroupRequest( groupId: securityGroupId ) )
      }

      authorizeSecurityGroupIngress( new AuthorizeSecurityGroupIngressRequest(
          groupId: securityGroupId,
          ipPermissions: [
              new IpPermission(
                  ipProtocol: 'tcp',
                  fromPort: 22,
                  toPort: 22,
                  ipRanges: [ '0.0.0.0/0' ]
              )
          ]
      ) )
      print( "Authorized tcp:22 ingress for ${securityGroupId}" )
    }

    AmazonEC2 userEc2 = AmazonEC2Client.builder( )
        .withCredentials( userCredentials )
        .withEndpointConfiguration( new EndpointConfiguration( EC2_ENDPOINT, "eucalyptus" ) )
        .build( )
    userEc2.with {
      try {
        print( "Attempting authorize of untagged security group ${securityGroupId} tcp:80 as user" )
        authorizeSecurityGroupIngress( new AuthorizeSecurityGroupIngressRequest(
            groupId: securityGroupId,
            ipPermissions: [
                new IpPermission(
                    ipProtocol: 'tcp',
                    fromPort: 80,
                    toPort: 80,
                    ipRanges: [ '0.0.0.0/0' ]
                )
            ]
        ) )
        Assert.fail( 'Expected security group authorize to fail' )
      } catch ( AmazonServiceException e ) {
        print( "Got expected exception: ${e}" )
      }

      try {
        print( "Attempting revoke of untagged security group ${securityGroupId} tcp:22 as user" )
        revokeSecurityGroupIngress( new RevokeSecurityGroupIngressRequest(
            groupId: securityGroupId,
            ipPermissions: [
                new IpPermission(
                    ipProtocol: 'tcp',
                    fromPort: 22,
                    toPort: 22,
                    ipRanges: [ '0.0.0.0/0' ]
                )
            ]
        ) )
        Assert.fail( 'Expected security group revoke to fail' )
      } catch ( AmazonServiceException e ) {
        print( "Got expected exception: ${e}" )
      }
      void
    }

    accountEc2.with {
      print( "Tagging security group ${securityGroupId} with update=true" )
      createTags( new CreateTagsRequest(
          resources: [ securityGroupId ],
          tags: [
              new Tag( key: 'update', value: 'true' )
          ]
      ) )
    }

    print( "Sleeping to allow tags to apply" )
    N4j.sleep( tagWaitSeconds )

    userEc2.with {
      print( "Attempting authorize of tagged security group ${securityGroupId} tcp:80 as user" )
      authorizeSecurityGroupIngress( new AuthorizeSecurityGroupIngressRequest(
          groupId: securityGroupId,
          ipPermissions: [
              new IpPermission(
                  ipProtocol: 'tcp',
                  fromPort: 80,
                  toPort: 80,
                  ipRanges: [ '0.0.0.0/0' ]
              )
          ]
      ) )

      print( "Attempting revoke of tagged security group ${securityGroupId} tcp:22 as user" )
      revokeSecurityGroupIngress( new RevokeSecurityGroupIngressRequest(
          groupId: securityGroupId,
          ipPermissions: [
              new IpPermission(
                  ipProtocol: 'tcp',
                  fromPort: 22,
                  toPort: 22,
                  ipRanges: [ '0.0.0.0/0' ]
              )
          ]
      ) )
    }
  }

  @Test
  void testTaggedVpcSecurityGroupUpdate( ) {
    accountIam.with{
      putUserPolicy( new PutUserPolicyRequest(
          userName: this.user,
          policyName: 'ec2-update-if-tagged',
          policyDocument: '''\
          {
              "Version": "2012-10-17",
              "Statement": [
                  {
                      "Effect": "Allow",
                      "Action": [
                          "ec2:AuthorizesecurityGroupEgress",
                          "ec2:AuthorizesecurityGroupIngress",
                          "ec2:RevokeSecurityGroupEgress",
                          "ec2:RevokeSecurityGroupIngress"
                      ],
                      "Resource": "*",
                      "Condition": {
                          "StringEquals": {
                              "ec2:ResourceTag/update": "true"
                          }
                      }
                  }
              ]
          }
        '''.stripIndent( )
      ) )
    }

    String securityGroupId = null
    accountEc2.with {
      print( 'Creating vpc to contain resources for update' )
      String vpcId = createVpc( new CreateVpcRequest(
          cidrBlock: '10.0.0.0/16'
      ) ).with {
        vpc?.vpcId
      }
      print( "Created vpc: ${vpcId}" )
      Assert.assertNotNull( vpcId, "Expected vpcId" )
      cleanupTasks.add{
        print( "Deleting vpc ${vpcId}" )
        deleteVpc( new DeleteVpcRequest( vpcId: vpcId ) )
      }

      print( 'Creating security group for update test' )
      securityGroupId = createSecurityGroup( new CreateSecurityGroupRequest(
          vpcId: vpcId,
          groupName: 'tagged-resource-update-test',
          description: 'Group for testing update of tagged resources'
      ) ).with {
        groupId
      }
      print( "Created security group: ${securityGroupId}" )
      Assert.assertNotNull( securityGroupId, "Expected securityGroupId" )
      cleanupTasks.add{
        print( "Deleting security group ${securityGroupId}" )
        deleteSecurityGroup( new DeleteSecurityGroupRequest( groupId: securityGroupId ) )
      }

      authorizeSecurityGroupIngress( new AuthorizeSecurityGroupIngressRequest(
          groupId: securityGroupId,
          ipPermissions: [
              new IpPermission(
                  ipProtocol: 'tcp',
                  fromPort: 22,
                  toPort: 22,
                  ipRanges: [ '0.0.0.0/0' ]
              )
          ]
      ) )
      print( "Authorized tcp:22 ingress for ${securityGroupId}" )

      authorizeSecurityGroupEgress( new AuthorizeSecurityGroupEgressRequest(
          groupId: securityGroupId,
          ipPermissions: [
              new IpPermission(
                  ipProtocol: 'tcp',
                  fromPort: 22,
                  toPort: 22,
                  ipRanges: [ '0.0.0.0/0' ]
              )
          ]
      ) )
      print( "Authorized tcp:22 egress for ${securityGroupId}" )
    }

    AmazonEC2 userEc2 = AmazonEC2Client.builder( )
        .withCredentials( userCredentials )
        .withEndpointConfiguration( new EndpointConfiguration( EC2_ENDPOINT, "eucalyptus" ) )
        .build( )
    userEc2.with {
      try {
        print( "Attempting authorize egress of untagged security group ${securityGroupId} tcp:80 as user" )
        authorizeSecurityGroupEgress( new AuthorizeSecurityGroupEgressRequest(
            groupId: securityGroupId,
            ipPermissions: [
                new IpPermission(
                    ipProtocol: 'tcp',
                    fromPort: 80,
                    toPort: 80,
                    ipRanges: [ '0.0.0.0/0' ]
                )
            ]
        ) )
        Assert.fail( 'Expected security group authorize egress to fail' )
      } catch ( AmazonServiceException e ) {
        print( "Got expected exception: ${e}" )
      }

      try {
        print( "Attempting authorize ingress of untagged security group ${securityGroupId} tcp:80 as user" )
        authorizeSecurityGroupIngress( new AuthorizeSecurityGroupIngressRequest(
            groupId: securityGroupId,
            ipPermissions: [
                new IpPermission(
                    ipProtocol: 'tcp',
                    fromPort: 80,
                    toPort: 80,
                    ipRanges: [ '0.0.0.0/0' ]
                )
            ]
        ) )
        Assert.fail( 'Expected security group authorize ingress to fail' )
      } catch ( AmazonServiceException e ) {
        print( "Got expected exception: ${e}" )
      }

      try {
        print( "Attempting revoke egress of untagged security group ${securityGroupId} tcp:22 as user" )
        revokeSecurityGroupEgress( new RevokeSecurityGroupEgressRequest(
            groupId: securityGroupId,
            ipPermissions: [
                new IpPermission(
                    ipProtocol: 'tcp',
                    fromPort: 22,
                    toPort: 22,
                    ipRanges: [ '0.0.0.0/0' ]
                )
            ]
        ) )
        Assert.fail( 'Expected security group revoke egress to fail' )
      } catch ( AmazonServiceException e ) {
        print( "Got expected exception: ${e}" )
      }

      try {
        print( "Attempting revoke ingress of untagged security group ${securityGroupId} tcp:22 as user" )
        revokeSecurityGroupIngress( new RevokeSecurityGroupIngressRequest(
            groupId: securityGroupId,
            ipPermissions: [
                new IpPermission(
                    ipProtocol: 'tcp',
                    fromPort: 22,
                    toPort: 22,
                    ipRanges: [ '0.0.0.0/0' ]
                )
            ]
        ) )
        Assert.fail( 'Expected security group revoke ingress to fail' )
      } catch ( AmazonServiceException e ) {
        print( "Got expected exception: ${e}" )
      }
      void
    }

    accountEc2.with {
      print( "Tagging security group ${securityGroupId} with update=true" )
      createTags( new CreateTagsRequest(
          resources: [ securityGroupId ],
          tags: [
              new Tag( key: 'update', value: 'true' )
          ]
      ) )
    }

    print( "Sleeping to allow tags to apply" )
    N4j.sleep( tagWaitSeconds )

    userEc2.with {
      print( "Attempting authorize egress of untagged security group ${securityGroupId} tcp:80 as user" )
      authorizeSecurityGroupEgress( new AuthorizeSecurityGroupEgressRequest(
          groupId: securityGroupId,
          ipPermissions: [
              new IpPermission(
                  ipProtocol: 'tcp',
                  fromPort: 80,
                  toPort: 80,
                  ipRanges: [ '0.0.0.0/0' ]
              )
          ]
      ) )

      print( "Attempting authorize ingress of tagged security group ${securityGroupId} tcp:80 as user" )
      authorizeSecurityGroupIngress( new AuthorizeSecurityGroupIngressRequest(
          groupId: securityGroupId,
          ipPermissions: [
              new IpPermission(
                  ipProtocol: 'tcp',
                  fromPort: 80,
                  toPort: 80,
                  ipRanges: [ '0.0.0.0/0' ]
              )
          ]
      ) )

      print( "Attempting revoke egress of untagged security group ${securityGroupId} tcp:22 as user" )
      revokeSecurityGroupEgress( new RevokeSecurityGroupEgressRequest(
          groupId: securityGroupId,
          ipPermissions: [
              new IpPermission(
                  ipProtocol: 'tcp',
                  fromPort: 22,
                  toPort: 22,
                  ipRanges: [ '0.0.0.0/0' ]
              )
          ]
      ) )

      print( "Attempting revoke ingress of tagged security group ${securityGroupId} tcp:22 as user" )
      revokeSecurityGroupIngress( new RevokeSecurityGroupIngressRequest(
          groupId: securityGroupId,
          ipPermissions: [
              new IpPermission(
                  ipProtocol: 'tcp',
                  fromPort: 22,
                  toPort: 22,
                  ipRanges: [ '0.0.0.0/0' ]
              )
          ]
      ) )
    }
  }

  @Test
  void testTaggedInstanceRebootAndTerminate( ) {
    accountIam.with{
      putUserPolicy( new PutUserPolicyRequest(
          userName: this.user,
          policyName: 'ec2-terminate-instances-if-tagged',
          policyDocument: '''\
          {
              "Version": "2012-10-17",
              "Statement": [
                  {
                      "Effect": "Allow",
                      "Action": [
                          "ec2:RebootInstances",
                          "ec2:TerminateInstances"
                      ],
                      "Resource": "*",
                      "Condition": {
                          "StringEquals": {
                              "ec2:ResourceTag/purpose": "test"
                          }
                      }
                  }
              ]
          }
        '''.stripIndent( )
      ) )
    }

    String instanceId = null
    accountEc2.with {
      // Find an image to use
      String imageId = describeImages( new DescribeImagesRequest(
          filters: [
              new Filter( name: 'image-type', values: ['machine'] ),
              new Filter( name: 'is-public', values: ['true'] ),
              new Filter( name: 'root-device-type', values: ['instance-store'] ),
          ]
      ) ).with {
        images?.getAt( 0 )?.imageId
      }
      Assert.assertNotNull( imageId, "Public instance-store image not found" )
      print( "Using image: ${imageId}" )

      // Run instance without any tags
      print( 'Launching instance for termination test' )
      instanceId = runInstances( new RunInstancesRequest(
          minCount: 1,
          maxCount: 1,
          imageId: imageId
      ) ).with {
        reservation?.instances?.getAt(0)?.instanceId
      }
      print( "Launched instance: ${instanceId}" )
      Assert.assertNotNull( instanceId, "Expected instance identifier" )

      cleanupTasks.add{
        print( "Terminating instance ${instanceId}" )
        terminateInstances( new TerminateInstancesRequest( instanceIds: [ instanceId ] ) )
      }
    }

    // Terminate without permission
    AmazonEC2 userEc2 = AmazonEC2Client.builder( )
        .withCredentials( userCredentials )
        .withEndpointConfiguration( new EndpointConfiguration( EC2_ENDPOINT, "eucalyptus" ) )
        .build( )
    userEc2.with {
      try {
        print( "Attempting reboot of untagged instance ${instanceId} as user" )
        rebootInstances( new RebootInstancesRequest( instanceIds: [instanceId ] ) )
        Assert.fail( 'Expected instance reboot to fail' )
      } catch ( AmazonServiceException e ) {
        print( "Got expected exception: ${e}" )
      }

      try {
        print( "Attempting terminate of untagged instance ${instanceId} as user" )
        terminateInstances( new TerminateInstancesRequest( instanceIds: [ instanceId ] ) )
        Assert.fail( 'Expected instance termination to fail' )
      } catch ( AmazonServiceException e ) {
        print( "Got expected exception: ${e}" )
      }
      void
    }

    accountEc2.with {
      print( "Tagging instance ${instanceId} with purpose=test" )
      createTags( new CreateTagsRequest(
          resources: [ instanceId ],
          tags: [
            new Tag( key: 'purpose', value: 'test' )
          ]
      ) )
    }

    print( "Sleeping to allow tags to apply" )
    N4j.sleep( tagWaitSeconds )

    userEc2.with {
      print( "Attempting reboot of tagged instance ${instanceId} as user" )
      rebootInstances( new RebootInstancesRequest( instanceIds: [instanceId ] ) )

      print( "Attempting terminate of tagged instance ${instanceId} as user" )
      terminateInstances( new TerminateInstancesRequest( instanceIds: [ instanceId ] ) )
    }
  }

  @Test
  void testCreateTags( ) {
    accountIam.with{
      print( "Putting policy for user ${this.user}, authorizing create and delete tags" )
      putUserPolicy( new PutUserPolicyRequest(
          userName: this.user,
          policyName: 'ec2-create-delete-some-tags',
          policyDocument: """\
            {
                "Version": "2012-10-17",
                "Statement": [
                    {
                        "Effect": "Allow",
                        "Action": "ec2:Describe*",
                        "Resource": "*"
                    },
                    {
                        "Effect": "Allow",
                        "Action": "ec2:CreateTags",
                        "Resource": "arn:aws:ec2::${accountNumber}:volume/*",
                        "Condition": {
                            "StringEquals": {
                                "ec2:CreateAction": "CreateTags"
                            },
                            "ForAllValues:StringEquals": {
                                "aws:TagKeys": ["environment","cost-center"]
                            }
                        }
                    },
                    {
                        "Effect": "Allow",
                        "Action": "ec2:DeleteTags",
                        "Resource": "arn:aws:ec2::${accountNumber}:volume/*",
                        "Condition": {
                            "ForAllValues:StringEquals": {
                                "aws:TagKeys": ["environment","cost-center"]
                            }
                        }
                    }
                ]
            }
          """.stripIndent( )
      ) )
    }

    String volumeId = null
    accountEc2.with {
      // Find an AZ to use
      String availabilityZone = describeAvailabilityZones( ).with{
        availabilityZones?.getAt( 0 )?.zoneName
      }
      print( "Using availability zone: ${availabilityZone}" )
      Assert.assertNotNull( availabilityZone, "Expected availability zone" )

      print( "Creating volume in availability zone: ${availabilityZone}" )
      volumeId = createVolume( new CreateVolumeRequest(
          size: 1,
          availabilityZone: availabilityZone,
          tagSpecifications: [
              new TagSpecification(
                  resourceType: 'volume',
                  tags: [
                      new Tag( key: 'restricted', value: 'value' ),
                  ]
              )
          ]
      ) ).with {
        volume?.volumeId
      }
      print( "Created volume ${volumeId}" )
      Assert.assertNotNull( volumeId, "Expected volumeId" )
      cleanupTasks.add{
        print( "Deleting volume ${volumeId}" )
        deleteVolume( new DeleteVolumeRequest( volumeId: volumeId ) )
      }
    }

    AmazonEC2 userEc2 = AmazonEC2Client.builder( )
        .withCredentials( userCredentials )
        .withEndpointConfiguration( new EndpointConfiguration( EC2_ENDPOINT, "eucalyptus" ) )
        .build( )
    userEc2.with {
      try {
        print( "Tagging volume ${volumeId} with non-permitted tags" )
        createTags( new CreateTagsRequest(
            resources: [ volumeId ],
            tags: [
                new Tag( key: 'meaning', value: '42' ),
            ]
        ) )
        print( "Describing tags for volume ${volumeId} to check if added" )
        describeTags( new DescribeTagsRequest(
            filters: [
                new Filter( name: 'resource-id', values: [ volumeId ] )
            ]
        ) ).with {
          print( "Volume tags: ${tags}" )
          Assert.assertEquals( tags.size(), 1, 'Volume tag count' )
        }
      } catch ( AmazonServiceException e ) {
        print( "Got expected exception: ${e}" )
        Assert.assertEquals( e.statusCode, 403, 'HTTP status code for error' )
      }

      print( "Creating volume tags ${volumeId}" )
      createTags( new CreateTagsRequest(
          resources: [ volumeId ],
          tags: [
              new Tag( key: 'cost-center', value: '5' ),
          ]
      ) )
      print( "Describing tags for volume ${volumeId} to verify added" )
      describeTags( new DescribeTagsRequest(
          filters: [
              new Filter( name: 'resource-id', values: [ volumeId ] )
          ]
      ) ).with {
        print( "Volume tags: ${tags}" )
        Assert.assertEquals( tags.size(), 2, 'Volume tag count' )
      }

      try {
        print( "Deleting volume tags ${volumeId} without permission (should fail)" )
        deleteTags( new DeleteTagsRequest(
            resources: [ volumeId ],
            tags: [
                new Tag( key: 'restricted' ),
            ]
        ) )
        print( "Describing tags for volume ${volumeId} to verify not deleted" )
        describeTags( new DescribeTagsRequest(
            filters: [
                new Filter( name: 'resource-id', values: [ volumeId ] )
            ]
        ) ).with {
          print( "Volume tags: ${tags}" )
          Assert.assertEquals( tags.size(), 2, 'Volume tag count' )
        }
      } catch ( AmazonServiceException e ) {
        print( "Got expected exception: ${e}" )
        Assert.assertEquals( e.statusCode, 403, 'HTTP status code for error' )
      }

      print( "Deleting volume tags ${volumeId}" )
      deleteTags( new DeleteTagsRequest(
          resources: [ volumeId ],
          tags: [
              new Tag( key: 'cost-center', value: '5' ),
          ]
      ) )
      print( "Describing tags for volume ${volumeId} to verify deleted" )
      describeTags( new DescribeTagsRequest(
          filters: [
              new Filter( name: 'resource-id', values: [ volumeId ] )
          ]
      ) ).with {
        print( "Volume tags: ${tags}" )
        Assert.assertEquals( tags.size(), 1, 'Volume tag count' )
      }

      void
    }
  }

  @Test
  void testCreateVolumeWithTags( ) {
    accountIam.with{
      print( "Putting policy for user ${this.user}, authorizing volume create with tags" )
      putUserPolicy( new PutUserPolicyRequest(
          userName: this.user,
          policyName: 'ec2-volume-create-with-required-tags',
          policyDocument: """\
            {
                "Version": "2012-10-17",
                "Statement": [
                    {
                        "Effect": "Allow",
                        "Action": "ec2:Describe*",
                        "Resource": "*"
                    },
                    {
                        "Effect": "Allow",
                        "Action": "ec2:CreateVolume",
                        "Resource": "*",
                        "Condition": {
                            "StringEquals": {
                                "aws:RequestTag/cost-center": "cc123"
                            },
                            "ForAllValues:StringEquals": {
                                "aws:TagKeys": ["environment","cost-center"]
                            }
                        }
                    },
                    {
                        "Effect": "Allow",
                        "Action": "ec2:CreateTags",
                        "Resource": "arn:aws:ec2::${accountNumber}:volume/*",
                        "Condition": {
                            "StringEquals": {
                                "ec2:CreateAction": "CreateVolume"
                            }
                        }
                    }
                ]
            }
          """.stripIndent( )
      ) )
    }

    AmazonEC2 userEc2 = AmazonEC2Client.builder( )
        .withCredentials( userCredentials )
        .withEndpointConfiguration( new EndpointConfiguration( EC2_ENDPOINT, "eucalyptus" ) )
        .build( )
    userEc2.with {
      // Find an AZ to use
      String availabilityZone = describeAvailabilityZones( ).with{
        availabilityZones?.getAt( 0 )?.zoneName
      }
      print( "Using availability zone: ${availabilityZone}" )
      Assert.assertNotNull( availabilityZone, "Expected availability zone" )

      print( "Creating volume in availability zone: ${availabilityZone}" )
      String volumeId = createVolume( new CreateVolumeRequest(
          size: 1,
          availabilityZone: availabilityZone,
          tagSpecifications: [
              new TagSpecification(
                  resourceType: 'volume',
                  tags: [
                      new Tag( key: 'cost-center', value: 'cc123' ),
                      new Tag( key: 'environment', value: '' ),
                  ]
              )
          ]
      ) ).with {
        String createdVolumeId = volume?.volumeId
        print( "Created volume ${createdVolumeId} with tags ${volume?.tags}" )
        Assert.assertNotNull( createdVolumeId, "Expected volumeId" )
        cleanupTasks.add{
          print( "Deleting volume ${createdVolumeId}" )
          deleteVolume( new DeleteVolumeRequest( volumeId: createdVolumeId ) )
        }
        Assert.assertEquals( volume?.tags?.size()?:0, 2 , 'create volume response tag count' )
        createdVolumeId
      }

      try {
        print( "Tagging volume ${volumeId}, after create, should fail due to ec2:CreateAction condition" )
        createTags( new CreateTagsRequest(
            resources: [ volumeId ],
            tags: [
                new Tag( key: 'meaning', value: '42' ),
            ]
        ) )
        print( "Describing tags for volume ${volumeId} to check if added" )
        describeTags( new DescribeTagsRequest(
            filters: [
                new Filter( name: 'resource-id', values: [ volumeId ] )
            ]
        ) ).with {
          print( "Volume tags: ${tags}" )
          Assert.assertEquals( tags.size(), 2 , 'Volume tag count' )
        }
      } catch ( AmazonServiceException e ) {
        print( "Got expected exception: ${e}" )
        Assert.assertEquals( e.statusCode, 403, 'HTTP status code for error' )
      }
      void
    }
  }

  @Test
  void testCreateVolumeWithTagsCreateTagsWrongResource( ) {
    accountIam.with {
      print( "Putting policy for user ${this.user}, authorizing volume create with wrong resource for tags" )
      // The resource arn for ec2:CreateTags is for instances, not volumes so creating tags
      // should not be permitted
      putUserPolicy(new PutUserPolicyRequest(
          userName: this.user,
          policyName: 'ec2-volume-create-with-required-tags-invalid',
          policyDocument: """\
            {
                "Version": "2012-10-17",
                "Statement": [
                    {
                        "Effect": "Allow",
                        "Action": "ec2:Describe*",
                        "Resource": "*"
                    },
                    {
                        "Effect": "Allow",
                        "Action": "ec2:CreateVolume",
                        "Resource": "*",
                        "Condition": {
                            "StringEquals": {
                                "aws:RequestTag/cost-center": "cc123"
                            },
                            "ForAllValues:StringEquals": {
                                "aws:TagKeys": ["environment","cost-center"]
                            }
                        }
                    },
                    {
                        "Effect": "Allow",
                        "Action": "ec2:CreateTags",
                        "Resource": "arn:aws:ec2::${accountNumber}:instance/*",
                        "Condition": {
                            "StringEquals": {
                                "ec2:CreateAction": "CreateVolume"
                            }
                        }
                    }
                ]
            }
          """.stripIndent()
      ))
    }

    AmazonEC2 userEc2 = AmazonEC2Client.builder()
        .withCredentials(userCredentials)
        .withEndpointConfiguration(new EndpointConfiguration(EC2_ENDPOINT, "eucalyptus"))
        .build()
    userEc2.with {
      // Find an AZ to use
      String availabilityZone = describeAvailabilityZones().with {
        availabilityZones?.getAt(0)?.zoneName
      }
      print("Using availability zone: ${availabilityZone}")
      Assert.assertNotNull(availabilityZone, "Expected availability zone")

      try {
        print( "Creating volume in availability zone: ${availabilityZone}, should fail" )
        String volumeId = createVolume(new CreateVolumeRequest(
            size: 1,
            availabilityZone: availabilityZone,
            tagSpecifications: [
                new TagSpecification(
                    resourceType: 'volume',
                    tags: [
                        new Tag(key: 'cost-center', value: 'cc123'),
                        new Tag(key: 'environment', value: ''),
                    ]
                )
            ]
        )).with {
          volume?.volumeId
        }

        print("Created volume: ${volumeId}")
        Assert.assertNotNull(volumeId, "Expected volumeId")
        cleanupTasks.add {
          print("Deleting volume ${volumeId}")
          deleteVolume(new DeleteVolumeRequest(volumeId: volumeId))
        }
        Assert.fail("Expected volume creation to fail with permission to tag only instance resources")
      } catch ( AmazonServiceException e ) {
        print( "Got expected exception: ${e}" )
        Assert.assertEquals( e.statusCode, 403, 'Unexpected HTTP status code for error' )
      }
    }
  }

  @Test
  void testCreateVolumeWithTagsNoCreateTagsPermission( ) {
    accountIam.with {
      print( "Putting policy for user ${this.user}, authorizing volume create but not create tags" )
      putUserPolicy(new PutUserPolicyRequest(
          userName: this.user,
          policyName: 'ec2-volume-create-with-required-tags-invalid',
          policyDocument: """\
            {
                "Version": "2012-10-17",
                "Statement": [
                    {
                        "Effect": "Allow",
                        "Action": "ec2:Describe*",
                        "Resource": "*"
                    },
                    {
                        "Effect": "Allow",
                        "Action": "ec2:CreateVolume",
                        "Resource": "*",
                        "Condition": {
                            "StringEquals": {
                                "aws:RequestTag/cost-center": "cc123"
                            },
                            "ForAllValues:StringEquals": {
                                "aws:TagKeys": ["environment","cost-center"]
                            }
                        }
                    }
                ]
            }
          """.stripIndent()
      ))
    }

    AmazonEC2 userEc2 = AmazonEC2Client.builder()
        .withCredentials(userCredentials)
        .withEndpointConfiguration(new EndpointConfiguration(EC2_ENDPOINT, "eucalyptus"))
        .build()
    userEc2.with {
      // Find an AZ to use
      String availabilityZone = describeAvailabilityZones().with {
        availabilityZones?.getAt(0)?.zoneName
      }
      print("Using availability zone: ${availabilityZone}")
      Assert.assertNotNull(availabilityZone, "Expected availability zone")

      try {
        print( "Creating volume in availability zone: ${availabilityZone}, should fail" )
        String volumeId = createVolume(new CreateVolumeRequest(
            size: 1,
            availabilityZone: availabilityZone,
            tagSpecifications: [
                new TagSpecification(
                    resourceType: 'volume',
                    tags: [
                        new Tag(key: 'cost-center', value: 'cc123'),
                        new Tag(key: 'environment', value: ''),
                    ]
                )
            ]
        )).with {
          volume?.volumeId
        }

        print("Created volume: ${volumeId}")
        Assert.assertNotNull(volumeId, "Expected volumeId")
        cleanupTasks.add {
          print("Deleting volume ${volumeId}")
          deleteVolume(new DeleteVolumeRequest(volumeId: volumeId))
        }
        Assert.fail("Expected volume creation to fail with permission to tag only instance resources")
      } catch ( AmazonServiceException e ) {
        print( "Got expected exception: ${e}" )
        Assert.assertEquals( e.statusCode, 403, 'Unexpected HTTP status code for error' )
      }
    }
  }

  @Test
  void testTaggedInstanceVolumeAttachDetach( ) {
    accountIam.with{
      putUserPolicy( new PutUserPolicyRequest(
          userName: this.user,
          policyName: 'ec2-instance-volume-attachment-if-tagged',
          policyDocument: """\
            {
                "Version": "2012-10-17",
                "Statement": [
                    {
                        "Effect": "Allow",
                        "Action": [
                            "ec2:AttachVolume",
                            "ec2:DetachVolume"
                        ],
                        "Resource": "arn:aws:ec2::${accountNumber}:instance/*",
                        "Condition": {
                            "StringEquals": {
                                "ec2:ResourceTag/department": "dev"
                            }
                        }
                    },
                    {
                        "Effect": "Allow",
                        "Action": [
                            "ec2:AttachVolume",
                            "ec2:DetachVolume"
                        ],
                        "Resource": "arn:aws:ec2::${accountNumber}:volume/*",
                        "Condition": {
                            "StringEquals": {
                                "ec2:ResourceTag/volume_user": "\${aws:username}"
                            }
                        }
                    }
                ]
            }
          """.stripIndent( )
      ) )
    }

    String instanceId = null
    String volumeId_1 = null
    String volumeId_2 = null
    accountEc2.with {
      // Find an image to use
      String imageId = describeImages( new DescribeImagesRequest(
          filters: [
              new Filter( name: 'image-type', values: ['machine'] ),
              new Filter( name: 'is-public', values: ['true'] ),
              new Filter( name: 'root-device-type', values: ['instance-store'] ),
          ]
      ) ).with {
        images?.getAt( 0 )?.imageId
      }
      Assert.assertNotNull( imageId, "Public instance-store image not found" )
      print( "Using image: ${imageId}" )

      // Find an AZ to use
      String availabilityZone = describeAvailabilityZones( ).with{
        availabilityZones?.getAt( 0 )?.zoneName
      }
      print( "Using availability zone: ${availabilityZone}" )
      Assert.assertNotNull( availabilityZone, "Expected availability zone" )

      def volumeCreate = { String desc ->
        print( "Creating volume ${desc} for delete test" )
        String volumeId = createVolume( new CreateVolumeRequest(
            size: 1,
            availabilityZone: availabilityZone
        ) ).with {
          volume?.volumeId
        }
        print( "Created volume ${desc}: ${volumeId}" )
        Assert.assertNotNull( volumeId, "Expected volumeId" )
        cleanupTasks.add{
          print( "Deleting volume ${desc} ${volumeId}" )
          deleteVolume( new DeleteVolumeRequest( volumeId: volumeId ) )
        }
        volumeId
      }
      volumeId_1 = volumeCreate( '1' )
      volumeId_2 = volumeCreate( '2' )

      // Run instance without any tags
      print( 'Launching instance for termination test' )
      instanceId = runInstances( new RunInstancesRequest(
          minCount: 1,
          maxCount: 1,
          imageId: imageId
      ) ).with {
        reservation?.instances?.getAt(0)?.instanceId
      }
      print( "Launched instance: ${instanceId}" )
      Assert.assertNotNull( instanceId, "Expected instance identifier" )
      cleanupTasks.add{
        print( "Terminating instance ${instanceId}" )
        terminateInstances( new TerminateInstancesRequest( instanceIds: [ instanceId ] ) )
      }
    }

    // Wait until running
    print( 'Waiting for instances to be running...' )
    waitForInstances( accountEc2, TimeUnit.MINUTES.toMillis( 2 ) )

    accountEc2.with {
      print( "Attaching volume ${volumeId_2} to instance ${instanceId} for detach testing" )
      attachVolume( new AttachVolumeRequest(
          instanceId: instanceId,
          volumeId: volumeId_2,
          device: '/dev/sdg'
      ) )

      print( 'Waiting for volumes to be attached' )
      waitForVolumeAttachments( accountEc2, TimeUnit.MINUTES.toMillis( 2 ) )
    }

    // Attach / detach without permission
    AmazonEC2 userEc2 = AmazonEC2Client.builder( )
        .withCredentials( userCredentials )
        .withEndpointConfiguration( new EndpointConfiguration( EC2_ENDPOINT, "eucalyptus" ) )
        .build( )
    userEc2.with {
      try {
        print( "Attempting attach of untagged volume ${volumeId_1} to untagged instance ${instanceId} as user" )
        attachVolume( new AttachVolumeRequest(
            instanceId: instanceId,
            volumeId: volumeId_1,
            device: '/dev/sdf'
        ) )
        Assert.fail( 'Expected instance volume attach to fail' )
      } catch ( AmazonServiceException e ) {
        print( "Got expected exception: ${e}" )
      }

      try {
        print( "Attempting detach of untagged volume ${volumeId_2} from untagged instance ${instanceId} as user" )
        detachVolume( new DetachVolumeRequest(
            instanceId: instanceId,
            volumeId: volumeId_2,
            device: '/dev/sdg'
        ) )
        Assert.fail( 'Expected instance volume detach to fail' )
      } catch ( AmazonServiceException e ) {
        print( "Got expected exception: ${e}" )
      }
      void
    }

    accountEc2.with {
      print( "Tagging instance ${instanceId} with department=dev" )
      createTags( new CreateTagsRequest(
          resources: [ instanceId ],
          tags: [
              new Tag( key: 'department', value: 'dev' )
          ]
      ) )
    }

    print( "Sleeping to allow tags to apply" )
    N4j.sleep( tagWaitSeconds )

    userEc2.with {
      try {
        print( "Attempting attach of untagged volume ${volumeId_1} to tagged instance ${instanceId} as user" )
        attachVolume( new AttachVolumeRequest(
            instanceId: instanceId,
            volumeId: volumeId_1,
            device: '/dev/sdf'
        ) )
        Assert.fail( 'Expected instance volume attach to fail' )
      } catch ( AmazonServiceException e ) {
        print( "Got expected exception: ${e}" )
      }

      try {
        print( "Attempting detach of untagged volume ${volumeId_2} from tagged instance ${instanceId} as user" )
        detachVolume( new DetachVolumeRequest(
            instanceId: instanceId,
            volumeId: volumeId_2,
            device: '/dev/sdg'
        ) )
        Assert.fail( 'Expected instance volume detach to fail' )
      } catch ( AmazonServiceException e ) {
        print( "Got expected exception: ${e}" )
      }
      void
    }

    accountEc2.with {
      print( "Tagging volumes ${volumeId_1} ${volumeId_2} with volume_user=${user}" )
      createTags( new CreateTagsRequest(
          resources: [ volumeId_1, volumeId_2 ],
          tags: [
              new Tag( key: 'volume_user', value: user )
          ]
      ) )
    }

    print( "Sleeping to allow tags to apply" )
    N4j.sleep( tagWaitSeconds )

    userEc2.with {
      print( "Attempting attach of tagged volume ${volumeId_1} to tagged instance ${instanceId} as user" )
      attachVolume( new AttachVolumeRequest(
          instanceId: instanceId,
          volumeId: volumeId_1,
          device: '/dev/sdf'
      ) )

      print( "Attempting detach of tagged volume ${volumeId_2} from tagged instance ${instanceId} as user" )
      detachVolume( new DetachVolumeRequest(
          instanceId: instanceId,
          volumeId: volumeId_2,
          device: '/dev/sdg'
      ) )
    }
  }

  @Test
  void testTaggedInstanceStartStop( ) {
    accountIam.with{
      putUserPolicy( new PutUserPolicyRequest(
          userName: this.user,
          policyName: 'ec2-instance-volume-attachment-if-tagged',
          policyDocument: """\
            {
                "Version": "2012-10-17",
                "Statement": [
                    {
                        "Effect": "Allow",
                        "Action": [
                            "ec2:StartInstances",
                            "ec2:StopInstances"
                        ],
                        "Resource": "arn:aws:ec2::${accountNumber}:instance/*",
                        "Condition": {
                            "StringEquals": {
                                "ec2:ResourceTag/ebs": "1"
                            }
                        }
                    },
                    {
                        "Effect": "Allow",
                        "Action": [
                            "ec2:StartInstances",
                            "ec2:StopInstances"
                        ],
                        "Resource": [
                            "arn:aws:ec2:::availabilityzone/*",
                            "arn:aws:ec2:::image/*",
                            "arn:aws:ec2:::keypair/*",
                            "arn:aws:ec2:::securitygroup/*",
                            "arn:aws:ec2:::vmtype/*"
                        ]
                    }
                ]
            }
          """.stripIndent( )
      ) )
    }

    String instanceId_1 = null
    String instanceId_2 = null
    accountEc2.with {
      // Find an image to use
      String imageId = describeImages( new DescribeImagesRequest(
          filters: [
              new Filter( name: 'image-type', values: ['machine'] ),
              new Filter( name: 'is-public', values: ['true'] ),
              new Filter( name: 'root-device-type', values: ['ebs'] ),
          ]
      ) ).with {
        images?.getAt( 0 )?.imageId
      }
      assumeThat( imageId != null, "Public ebs image not found" )
      print( "Using image: ${imageId}" )

      // Find an AZ to use
      String availabilityZone = describeAvailabilityZones( ).with{
        availabilityZones?.getAt( 0 )?.zoneName
      }
      print( "Using availability zone: ${availabilityZone}" )
      Assert.assertNotNull( availabilityZone, "Expected availability zone" )

      // Run instance without any tags
      def launchInstance = { String desc ->
        print( "Launching instance ${desc} for start/stop test" )
        String instanceId = runInstances( new RunInstancesRequest(
            minCount: 1,
            maxCount: 1,
            imageId: imageId
        ) ).with {
          reservation?.instances?.getAt(0)?.instanceId
        }
        print( "Launched instance ${desc}: ${instanceId}" )
        Assert.assertNotNull( instanceId, "Expected instance identifier" )
        cleanupTasks.add{
          print( "Terminating instance ${desc} ${instanceId}" )
          terminateInstances( new TerminateInstancesRequest( instanceIds: [ instanceId ] ) )
        }
        instanceId
      }
      instanceId_1 = launchInstance( '1' )
      instanceId_2 = launchInstance( '2' )
    }

    // Wait until running
    print( 'Waiting for instances to be running...' )
    waitForInstances( accountEc2, TimeUnit.MINUTES.toMillis( 2 ) )

    accountEc2.with {
      print( "Stopping instance ${instanceId_1} for start testing" )
      stopInstances( new StopInstancesRequest( instanceIds: [ instanceId_1 ]) )
    }

    // Wait until stopped
    print( 'Waiting for instance to be stopped...' )
    waitForInstances( accountEc2, TimeUnit.MINUTES.toMillis( 2 ) )

    // Start / stop without permission
    AmazonEC2 userEc2 = AmazonEC2Client.builder( )
        .withCredentials( userCredentials )
        .withEndpointConfiguration( new EndpointConfiguration( EC2_ENDPOINT, "eucalyptus" ) )
        .build( )
    userEc2.with {
      try {
        print( "Attempting start of untagged instance ${instanceId_1} as user" )
        startInstances( new StartInstancesRequest( instanceIds: [instanceId_1] ) )
        Assert.fail( 'Expected instance start to fail' )
      } catch ( AmazonServiceException e ) {
        print( "Got expected exception: ${e}" )
      }

      try {
        print( "Attempting stop of untagged instance ${instanceId_2} as user" )
        stopInstances( new StopInstancesRequest( instanceIds: [ instanceId_2 ] ) ).with {
          Assert.assertTrue( stoppingInstances.isEmpty( ), 'Expected instance stop to fail'  )
        }
      } catch ( AmazonServiceException e ) {
        print( "Got expected exception: ${e}" )
      }
      void
    }

    accountEc2.with {
      print( "Tagging instances ${instanceId_1} ${instanceId_2} with ebs=1" )
      createTags( new CreateTagsRequest(
          resources: [ instanceId_1, instanceId_2 ],
          tags: [
              new Tag( key: 'ebs', value: '1' )
          ]
      ) )
    }

    print( "Sleeping to allow tags to apply" )
    N4j.sleep( tagWaitSeconds )

    userEc2.with {
      print( "Attempting start of tagged instance ${instanceId_1} as user" )
      startInstances( new StartInstancesRequest( instanceIds: [instanceId_1] ) )

      print( "Attempting stop of tagged instance ${instanceId_2} as user" )
      stopInstances( new StopInstancesRequest( instanceIds: [ instanceId_2 ] ) ).with {
        Assert.assertFalse( stoppingInstances.isEmpty( ), 'Expected stopping instance'  )
      }
    }
  }

  @Test
  void testTaggedResourcesRunInstance( ) {
    accountIam.with{
      putUserPolicy( new PutUserPolicyRequest(
          userName: this.user,
          policyName: 'ec2-run-instance-if-resources-tagged',
          policyDocument: """\
            {
                "Version": "2012-10-17",
                "Statement": [
                    {
                        "Effect": "Allow",
                        "Action": "ec2:RunInstances",
                        "Resource": [
                            "arn:aws:ec2:::availabilityzone/*",
                            "arn:aws:ec2:::instance/*",
                            "arn:aws:ec2:::keypair/*",
                            "arn:aws:ec2:::vmtype/*"
                        ]
                    },
                    {
                        "Effect": "Allow",
                        "Action": "ec2:RunInstances",
                          "Resource": [
                            "arn:aws:ec2:::image/*",
                            "arn:aws:ec2:::securitygroup/*"
                        ],
                        "Condition": {
                            "StringEquals": {
                                "ec2:ResourceTag/runwithit": "youwill"
                            }
                        }
                    }
                ]
            }
          """.stripIndent( )
      ) )
    }

    String imageId = null
    String securityGroupId = null
    accountEc2.with {
      // Find an image to use
      imageId = describeImages(new DescribeImagesRequest(
          filters: [
              new Filter(name: 'image-type', values: ['machine']),
              new Filter(name: 'is-public', values: ['true']),
              new Filter(name: 'root-device-type', values: ['instance-store']),
          ]
      )).with {
        images?.getAt(0)?.imageId
      }
      Assert.assertNotNull(imageId, "Public instance-store image not found")
      print("Using image: ${imageId}")

      print( 'Creating security group for run instances test' )
      securityGroupId = createSecurityGroup( new CreateSecurityGroupRequest(
          groupName: 'tagged-resource-run-instances-test',
          description: 'Group for testing instance launch with tagged resources'
      ) ).with {
        groupId
      }
      print( "Created security group: ${securityGroupId}" )
      Assert.assertNotNull( securityGroupId, "Expected securityGroupId" )
      cleanupTasks.add{
        print( "Deleting security group ${securityGroupId}" )
        deleteSecurityGroup( new DeleteSecurityGroupRequest( groupId: securityGroupId ) )
      }
    }

    // Run using untagged resources
    AmazonEC2 userEc2 = AmazonEC2Client.builder( )
        .withCredentials( userCredentials )
        .withEndpointConfiguration( new EndpointConfiguration( EC2_ENDPOINT, "eucalyptus" ) )
        .build( )
    userEc2.with {
      try {
        print( "Attempting launch of instance using untagged image ${imageId} and group ${securityGroupId} as user" )
        String instanceId = runInstances( new RunInstancesRequest(
            minCount: 1,
            maxCount: 1,
            imageId: imageId,
            securityGroupIds: [ securityGroupId ]
        ) ).with {
          reservation?.instances?.getAt(0)?.instanceId
        }
        cleanupTasks.add{
          print( "Terminating instance ${instanceId}" )
          accountEc2.terminateInstances( new TerminateInstancesRequest( instanceIds: [ instanceId ] ) )
        }
        Assert.fail( 'Expected launch instance to fail' )
      } catch ( AmazonServiceException e ) {
        print( "Got expected exception: ${e}" )
      }

      void
    }

    accountEc2.with {
      print( "Tagging group ${securityGroupId} with runwithit=youwill" )
      createTags( new CreateTagsRequest(
          resources: [ securityGroupId ],
          tags: [
              new Tag( key: 'runwithit', value: 'youwill' )
          ]
      ) )
    }

    print( "Sleeping to allow tags to apply" )
    N4j.sleep( tagWaitSeconds )

    userEc2.with {
      try {
        print( "Attempting launch of instance using untagged image ${imageId} and tagged group ${securityGroupId} as user" )
        String instanceId = runInstances( new RunInstancesRequest(
            minCount: 1,
            maxCount: 1,
            imageId: imageId,
            securityGroupIds: [ securityGroupId ]
        ) ).with {
          reservation?.instances?.getAt(0)?.instanceId
        }
        cleanupTasks.add{
          print( "Terminating instance ${instanceId}" )
          accountEc2.terminateInstances( new TerminateInstancesRequest( instanceIds: [ instanceId ] ) )
        }
        Assert.fail( 'Expected launch instance to fail' )
      } catch ( AmazonServiceException e ) {
        print( "Got expected exception: ${e}" )
      }

      void
    }

    accountEc2.with {
      print( "Tagging image ${imageId} with runwithit=youwill" )
      createTags( new CreateTagsRequest(
          resources: [ imageId ],
          tags: [
              new Tag( key: 'runwithit', value: 'youwill' )
          ]
      ) )
    }

    print( "Sleeping to allow tags to apply" )
    N4j.sleep( tagWaitSeconds )

    userEc2.with {
      print( "Attempting launch of instance using tagged image ${imageId} and group ${securityGroupId} as user" )
      String instanceId = runInstances( new RunInstancesRequest(
          minCount: 1,
          maxCount: 1,
          imageId: imageId,
          securityGroupIds: [ securityGroupId ]
      ) ).with {
        reservation?.instances?.getAt(0)?.instanceId
      }
      cleanupTasks.add{
        print( "Terminating instance ${instanceId}" )
        accountEc2.terminateInstances( new TerminateInstancesRequest( instanceIds: [ instanceId ] ) )
      }
    }
  }

  @Test
  void testRunInstancesWithTags( ) {
    accountIam.with{
      putUserPolicy( new PutUserPolicyRequest(
          userName: this.user,
          policyName: 'ec2-instance-with-tags',
          policyDocument: """\
            {
                "Version": "2012-10-17",
                "Statement": [
                    {
                        "Effect": "Allow",
                        "Action": "ec2:RunInstances",
                        "Resource": [
                          "arn:aws:ec2::${accountNumber}:instance/*",
                          "arn:aws:ec2::${accountNumber}:volume/*"
                        ],
                        "Condition": {
                            "StringEquals": {
                                "aws:RequestTag/cost-center": "cc123"
                            },
                            "StringLike": {
                                "aws:RequestTag/environment": "*"
                            },
                            "ForAllValues:StringEquals": {
                                "aws:TagKeys": ["environment","cost-center"]
                            },
                            "ForAnyValue:StringEquals": {
                                "aws:TagKeys": ["environment"]
                            }
                        }
                    },
                    {
                        "Effect": "Allow",
                        "Action": "ec2:RunInstances",
                        "Resource": [
                            "arn:aws:ec2:::availabilityzone/*",
                            "arn:aws:ec2:::image/*",
                            "arn:aws:ec2:::keypair/*",
                            "arn:aws:ec2:::securitygroup/*",
                            "arn:aws:ec2:::vmtype/*"
                        ]
                    },                     {
                        "Effect": "Allow",
                        "Action": "ec2:CreateTags",
                        "Resource": "*",
                        "Condition": {
                            "StringEquals": {
                                "ec2:CreateAction": "RunInstances"
                            }
                        }
                    }
                ]
            }
          """.stripIndent( )
      ) )
    }

    String availabilityZone = null
    String imageId = null
    accountEc2.with {
      // Find an image to use
      imageId = describeImages( new DescribeImagesRequest(
          filters: [
              new Filter( name: 'image-type', values: ['machine'] ),
              new Filter( name: 'is-public', values: ['true'] ),
              new Filter( name: 'root-device-type', values: ['instance-store'] ),
          ]
      ) ).with {
        images?.getAt( 0 )?.imageId
      }
      print( "Using image: ${imageId}" )
      Assert.assertNotNull( imageId, "Expected public instance-store image" )

      // Find an AZ to use
      availabilityZone = describeAvailabilityZones( ).with{
        availabilityZones?.getAt( 0 )?.zoneName
      }
      print( "Using availability zone: ${availabilityZone}" )
      Assert.assertNotNull( availabilityZone, "Expected availability zone" )
    }

    String instanceId = null
    AmazonEC2 userEc2 = AmazonEC2Client.builder( )
        .withCredentials( userCredentials )
        .withEndpointConfiguration( new EndpointConfiguration( EC2_ENDPOINT, "eucalyptus" ) )
        .build( )
    userEc2.with {
      try {
        print( "Launching instance with non-permitted tags (should fail)" )
        runInstances( new RunInstancesRequest(
            minCount: 1,
            maxCount: 1,
            placement: new Placement(
                availabilityZone: availabilityZone
            ),
            imageId: imageId,
            tagSpecifications: [
                new TagSpecification(
                    resourceType: 'instance',
                    tags: [
                        new Tag( key: 'cost-center', value: 'cc123' ),
                        new Tag( key: 'environment', value: 'instance' ),
                        new Tag( key: 'extra', value: 'wrong' ),
                    ]
                )
            ]
        ) ).with {
          instanceId = reservation?.instances?.getAt(0)?.instanceId
        }
        print( "Launched instance: ${instanceId}" )
        if ( instanceId ) cleanupTasks.add{
          print( "Terminating instance ${instanceId}" )
          accountEc2.terminateInstances( new TerminateInstancesRequest( instanceIds: [ instanceId ] ) )
        }
        Assert.fail( "Expected run instances to fail due to extra tags specified" )
      } catch ( AmazonServiceException e ) {
        print( "Got expected exception: ${e}" )
      }

      print( "Launching instance with tags" )
      runInstances( new RunInstancesRequest(
          minCount: 1,
          maxCount: 1,
          placement: new Placement(
              availabilityZone: availabilityZone
          ),
          imageId: imageId,
          tagSpecifications: [
              new TagSpecification(
                  resourceType: 'instance',
                  tags: [
                      new Tag( key: 'cost-center', value: 'cc123' ),
                      new Tag( key: 'environment', value: 'instance' ),
                  ]
              )
          ]
      ) ).with {
        instanceId = reservation?.instances?.getAt(0)?.instanceId
      }

      print( "Launched instance: ${instanceId}" )
      Assert.assertNotNull( instanceId, "Expected instance identifier" )
      cleanupTasks.add{
        print( "Terminating instance ${instanceId}" )
        accountEc2.terminateInstances( new TerminateInstancesRequest( instanceIds: [ instanceId ] ) )
      }
    }

    // Wait until running
    print( 'Waiting for instances to be running...' )
    waitForInstances( accountEc2, TimeUnit.MINUTES.toMillis( 2 ) )

    accountEc2.with {
      print( "Describing instance ${instanceId} to check tags" )
      describeInstances( new DescribeInstancesRequest(
          instanceIds: [instanceId],
          filters: [
              new Filter( name: 'tag-value', values: [ 'instance' ] )
          ]
      ) ).with {
        reservations?.getAt( 0 )?.instances?.getAt( 0 )?.with {
          print( "Instance tags: ${tags}")
          Assert.assertNotNull( tags, 'instance tags' )
          Assert.assertEquals( tags.size(), 2 , 'instance tag count' )
        }
      }
    }
  }

  @Test
  void testRunInstancesWithInstanceAndVolumeTags( ) {
    accountIam.with{
      putUserPolicy( new PutUserPolicyRequest(
          userName: this.user,
          policyName: 'ec2-instance-with-tags',
          policyDocument: """\
            {
                "Version": "2012-10-17",
                "Statement": [
                    {
                        "Effect": "Allow",
                        "Action": "ec2:RunInstances",
                        "Resource": [
                          "arn:aws:ec2::${accountNumber}:instance/*"
                        ],
                        "Condition": {
                            "StringEquals": {
                                "aws:RequestTag/cost-center": "cc123",
                                "aws:RequestTag/environment": "instance"
                            },
                            "ForAllValues:StringEquals": {
                                "aws:TagKeys": ["environment","cost-center"]
                            }
                        }
                    },
                    {
                        "Effect": "Allow",
                        "Action": "ec2:RunInstances",
                        "Resource": [
                          "arn:aws:ec2::${accountNumber}:volume/*"
                        ],
                        "Condition": {
                            "StringEquals": {
                                "aws:RequestTag/cost-center": "cc123",
                                "aws:RequestTag/environment": "volume"
                            },
                            "ForAllValues:StringEquals": {
                                "aws:TagKeys": ["environment","cost-center"]
                            }
                        }
                    },
                    {
                        "Effect": "Allow",
                        "Action": "ec2:RunInstances",
                        "Resource": [
                            "arn:aws:ec2:::availabilityzone/*",
                            "arn:aws:ec2:::image/*",
                            "arn:aws:ec2:::keypair/*",
                            "arn:aws:ec2:::securitygroup/*",
                            "arn:aws:ec2:::vmtype/*"
                        ]
                    },
                    {
                        "Effect": "Allow",
                        "Action": "ec2:CreateTags",
                        "Resource": [
                          "arn:aws:ec2::${accountNumber}:instance/*",
                          "arn:aws:ec2::${accountNumber}:volume/*"
                        ],
                        "Condition": {
                            "StringEquals": {
                                "ec2:CreateAction": "RunInstances"
                            }
                        }
                    }
                ]
            }
          """.stripIndent( )
      ) )
    }

    String availabilityZone = null
    String imageId = null
    accountEc2.with {
      // Find an image to use
      imageId = describeImages( new DescribeImagesRequest(
          filters: [
              new Filter( name: 'image-type', values: ['machine'] ),
              new Filter( name: 'is-public', values: ['true'] ),
              new Filter( name: 'root-device-type', values: ['ebs'] ),
          ]
      ) ).with {
        images?.getAt( 0 )?.imageId
      }
      assumeThat( imageId != null, "Public ebs image not found" )
      print( "Using image: ${imageId}" )

      // Find an AZ to use
      availabilityZone = describeAvailabilityZones( ).with{
        availabilityZones?.getAt( 0 )?.zoneName
      }
      print( "Using availability zone: ${availabilityZone}" )
      Assert.assertNotNull( availabilityZone, "Expected availability zone" )
    }

    String instanceId = null
    AmazonEC2 userEc2 = AmazonEC2Client.builder( )
        .withCredentials( userCredentials )
        .withEndpointConfiguration( new EndpointConfiguration( EC2_ENDPOINT, "eucalyptus" ) )
        .build( )
    userEc2.with {
      try {
        print( "Launching instance without all required tags (should fail)" )
        runInstances( new RunInstancesRequest(
            minCount: 1,
            maxCount: 1,
            placement: new Placement(
                availabilityZone: availabilityZone
            ),
            imageId: imageId,
            tagSpecifications: [
                new TagSpecification(
                    resourceType: 'instance',
                    tags: [
                        new Tag( key: 'cost-center', value: 'cc123' )
                    ]
                ),
                new TagSpecification(
                    resourceType: 'volume',
                    tags: [
                        new Tag( key: 'cost-center', value: 'cc123' )
                    ]
                )
            ]
        ) ).with {
          instanceId = reservation?.instances?.getAt(0)?.instanceId
        }
        print( "Launched instance: ${instanceId}" )
        if ( instanceId ) cleanupTasks.add{
          print( "Terminating instance ${instanceId}" )
          accountEc2.terminateInstances( new TerminateInstancesRequest( instanceIds: [ instanceId ] ) )
        }
        Assert.fail( "Expected run instances to fail due to not all required tags specified" )
      } catch ( AmazonServiceException e ) {
        print( "Got expected exception: ${e}" )
      }

      print( "Launching instance with tags" )
      runInstances( new RunInstancesRequest(
          minCount: 1,
          maxCount: 1,
          placement: new Placement(
              availabilityZone: availabilityZone
          ),
          imageId: imageId,
          tagSpecifications: [
              new TagSpecification(
                  resourceType: 'instance',
                  tags: [
                      new Tag( key: 'cost-center', value: 'cc123' ),
                      new Tag( key: 'environment', value: 'instance' ),
                  ]
              ),
              new TagSpecification(
                  resourceType: 'volume',
                  tags: [
                      new Tag( key: 'cost-center', value: 'cc123' ),
                      new Tag( key: 'environment', value: 'volume' ),
                  ]
              )
          ]
      ) ).with {
        instanceId = reservation?.instances?.getAt(0)?.instanceId
      }

      print( "Launched instance: ${instanceId}" )
      Assert.assertNotNull( instanceId, "Expected instance identifier" )
      cleanupTasks.add{
        print( "Terminating instance ${instanceId}" )
        accountEc2.terminateInstances( new TerminateInstancesRequest( instanceIds: [ instanceId ] ) )
      }
    }

    // Wait until running
    print( 'Waiting for instances to be running...' )
    waitForInstances( accountEc2, TimeUnit.MINUTES.toMillis( 2 ) )

    accountEc2.with {
      print( "Describing instance ${instanceId} to check tags" )
      String volumeId = null
      describeInstances( new DescribeInstancesRequest(
          instanceIds: [instanceId],
          filters: [
              new Filter( name: 'tag-value', values: [ 'instance' ] )
          ]
      ) ).with {
        reservations?.getAt( 0 )?.instances?.getAt( 0 )?.with {
          print( "Instance tags: ${tags}")
          Assert.assertNotNull( tags, 'instance tags' )
          Assert.assertEquals( tags.size(), 2 , 'instance tag count' )
        }
        volumeId = reservations?.getAt(0)?.instances?.getAt(0)?.blockDeviceMappings?.getAt( 0 )?.ebs?.volumeId
      }
      Assert.assertNotNull( volumeId, "Expected volume identifier" )

      print( "Describing volume ${volumeId} to check tags" )
      describeVolumes( new DescribeVolumesRequest(
          volumeIds: [volumeId],
          filters: [
              new Filter( name: 'tag-value', values: [ 'volume' ] )
          ]
      ) ).with {
        volumes?.getAt( 0 )?.with {
          print( "Volume tags: ${tags}")
          Assert.assertNotNull( tags, 'volume tags' )
          Assert.assertEquals( tags.size(), 2 , 'volume tag count' )
        }
      }
    }
  }
}
