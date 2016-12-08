package com.eucalyptus.tests.awssdk

import com.amazonaws.auth.AWSCredentialsProvider
import com.amazonaws.internal.StaticCredentialsProvider
import com.amazonaws.services.ec2.AmazonEC2
import com.amazonaws.services.ec2.AmazonEC2Client
import com.amazonaws.services.ec2.model.*
import com.amazonaws.services.securitytoken.AWSSecurityTokenService
import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClient
import com.amazonaws.services.securitytoken.model.GetCallerIdentityRequest
import org.testng.annotations.AfterClass
import org.testng.annotations.Test

import static N4j.*

/**
 * This application tests EC2 long identifier functionality.
 *
 * This is verification for the feature:
 *
 *   https://eucalyptus.atlassian.net/browse/EUCA-12995
 */
class TestEC2LongIdentifiers {

  private final String host
  private final AWSCredentialsProvider testAcctCredentials
  private final String testAcct

  TestEC2LongIdentifiers( ) {
    getCloudInfo( )
    this.host = CLC_IP
    this.testAcct= "${NAME_PREFIX}longid-test-acct"
    createAccount(testAcct)
    this.testAcctCredentials = new StaticCredentialsProvider( getUserCreds(testAcct, 'admin') )

  }


  @AfterClass
  void tearDownAfterClass() throws Exception {
    deleteAccount(testAcct)
  }

  private String cloudUri( String servicePath ) {
    URI.create( "http://" + host + ":8773/" )
            .resolve( servicePath )
            .toString()
  }

  private AmazonEC2Client getEC2Client( final AWSCredentialsProvider credentials ) {
    final AmazonEC2Client ec2 = new AmazonEC2Client( credentials )
    ec2.setEndpoint( cloudUri( '/services/compute' ) )
    ec2
  }

  private AWSSecurityTokenService getStsClient( final AWSCredentialsProvider credentials  ) {
    final AWSSecurityTokenService sts = new AWSSecurityTokenServiceClient( credentials )
    sts.setEndpoint( cloudUri( '/services/Tokens' ) )
    sts
  }

  private void assertThat( boolean condition,
                           String message ){
    N4j.assertThat( condition, message )
  }

  @Test
  void longIdentifiersTest( )  {
    final AmazonEC2 ec2 = getEC2Client( testAcctCredentials )

    // Find an AZ to use
    final String availabilityZone = ec2.describeAvailabilityZones( ).with{
      availabilityZones?.getAt( 0 )?.zoneName
    };
    assertThat( availabilityZone != null, "Availability zone not found" );
    print( "Using availability zone: ${availabilityZone}" );

    // Find an image to use
    final String imageId = ec2.describeImages( new DescribeImagesRequest(
        filters: [
            new Filter( name: "image-type", values: ["machine"] ),
            new Filter( name: "root-device-type", values: ["instance-store"] ),
            new Filter( name: "is-public", values: ["true"] ),
        ]
    ) ).with {
      images?.getAt( 0 )?.imageId
    }
    assertThat( imageId != null , "Image not found (public instance-store)" )
    print( "Using image: ${imageId}" )

    // Discover SSH key
    final String keyName = ec2.describeKeyPairs().with {
      keyPairs?.getAt(0)?.keyName
    }
    print( "Using key pair: ${keyName}" );

    // Discover account number
    final String accountNumber = getStsClient( testAcctCredentials ).getCallerIdentity( new GetCallerIdentityRequest( ) ).with {
      account
    }
    print( "Account for credentials is: ${accountNumber}" )

    final List<Runnable> cleanupTasks = [] as List<Runnable>
    try {
      ec2.with {
        final List<String> allResources = [ 'instance', 'reservation', 'snapshot', 'volume' ]
        String volumeId = null
        [ false, true ].each { value ->
          allResources.each { resource ->
            print( "Modifying id formats for account and resource: ${resource} to ${value}" )
            modifyIdentityIdFormat( new ModifyIdentityIdFormatRequest(
                principalArn: "arn:aws:iam::${accountNumber}:root",
                resource: resource,
                useLongIds: value
            ) )
          }

          print( 'Describing account identity id formats' )
          describeIdentityIdFormat( new DescribeIdentityIdFormatRequest(
              principalArn: "arn:aws:iam::${accountNumber}:root"
          ) ).with {
            print( it.toString( ) )
            assertThat( statuses != null, 'Expected statuses' )
            assertThat( !statuses.isEmpty( ), 'Expected statuses, but was empty' )
            statuses.each { IdFormat idFormat ->
              assertThat( idFormat.resource != null, 'Expected resource' )
              assertThat( allResources.contains(idFormat.resource), "Unexpected resource ${idFormat.resource}" )
              assertThat( idFormat.useLongIds != null, 'Expected useLongIds' )
              assertThat( idFormat.useLongIds == value, "Expected useLongIds ${value}" )
            }
          }

          if ( volumeId == null ) {
            print( 'Creating volume to check identifier format' )
            volumeId = createVolume( new CreateVolumeRequest( size: 1, availabilityZone: availabilityZone ) ).with {
              volume?.volumeId
            }
            print( "Created volume with id: ${volumeId}" )
            cleanupTasks.add{
              print( "Deleting volume ${volumeId}" )
              ec2.deleteVolume( new DeleteVolumeRequest( volumeId: volumeId ) )
            }
            assertThat( volumeId != null, 'Expected volume identifier')
            assertThat( volumeId.length( ) == 12, "Expected identifier length 12, but was: ${volumeId.length( )}" )
          } else {
            print( 'Creating snapshot to check identifier format' )
            String snapshotId = createSnapshot( new CreateSnapshotRequest( volumeId: volumeId) ).with {
              snapshot?.snapshotId
            }
            print( "Created snapshot with id: ${snapshotId}" )
            cleanupTasks.add{
              print( "Deleting snapshot ${snapshotId}" )
              ec2.deleteSnapshot( new DeleteSnapshotRequest( snapshotId: snapshotId ) )
            }
            assertThat( snapshotId != null, 'Expected snapshot identifier')
            assertThat( snapshotId.length( ) == 22, "Expected identifier length 22, but was: ${snapshotId.length( )}" )
          }
        }

        allResources.each { resource ->
          print( "Describing account identity id format for resource ${resource}" )
          describeIdentityIdFormat( new DescribeIdentityIdFormatRequest(
              principalArn: "arn:aws:iam::${accountNumber}:root",
              resource: resource
          ) ).with {
            print( it.toString( ) )
            assertThat( statuses != null, 'Expected statuses' )
            assertThat( statuses.size() == 1, 'Expected one statuses' )
            statuses.each { IdFormat idFormat ->
              assertThat( idFormat.resource != null, 'Expected resource' )
              assertThat( idFormat.resource == resource, "Unexpected resource ${idFormat.resource}" )
            }
          }
        }

        String instanceId = null
        [ false, true ].each { value ->
          allResources.each { resource ->
            print("Modifying id format for resource: ${resource}")
            modifyIdFormat(new ModifyIdFormatRequest(
                resource: resource,
                useLongIds: value
            ))
          }

          print('Describing id format')
          describeIdFormat( ).with {
            print(it.toString())
            assertThat( statuses != null, 'Expected statuses' )
            assertThat( !statuses.isEmpty( ), 'Expected statuses, but was empty' )
            statuses.each { IdFormat idFormat ->
              assertThat( idFormat.resource != null, 'Expected resource' )
              assertThat( allResources.contains(idFormat.resource), "Unexpected resource ${idFormat.resource}" )
              assertThat( idFormat.useLongIds != null, 'Expected useLongIds' )
              assertThat( idFormat.useLongIds == value, "Expected useLongIds ${value}" )
            }
          }

          if ( instanceId == null ) {
            print( 'Launching instance to check identifier format' )
            instanceId = runInstances( new RunInstancesRequest(
                minCount: 1,
                maxCount: 1,
                imageId: imageId,
                placement: new Placement( availabilityZone ) ) ).with {
              reservation?.instances?.getAt( 0 )?.instanceId
            }
            print( "Launched instance with id: ${instanceId}" )
            cleanupTasks.add{
              print( "Terminating instance ${instanceId}" )
              ec2.terminateInstances( new TerminateInstancesRequest( instanceIds: [ instanceId ] ) )
            }
            assertThat( instanceId != null, 'Expected volume identifier')
            assertThat( instanceId.length( ) == 10, "Expected identifier length 10, but was: ${instanceId.length( )}" )
          }
        }

        allResources.each { resource ->
          print( "Describing id format for resource ${resource}" )
          describeIdFormat( new DescribeIdFormatRequest(
              resource: resource
          ) ).with {
            print( it.toString( ) )
            assertThat( statuses != null, 'Expected statuses' )
            assertThat( statuses.size() == 1, 'Expected one statuses' )
            statuses.each { IdFormat idFormat ->
              assertThat( idFormat.resource != null, 'Expected resource' )
              assertThat( idFormat.resource == resource, "Unexpected resource ${idFormat.resource}" )
            }
          }
        }
      }

      print( "Test complete" )
    } finally {
      // Attempt to clean up anything we created
      cleanupTasks.reverseEach { Runnable cleanupTask ->
        try {
          cleanupTask.run()
        } catch ( Exception e ) {
          e.printStackTrace()
        }
      }
    }
  }
}
