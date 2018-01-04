package com.eucalyptus.tests.awssdk

import com.amazonaws.AmazonServiceException
import com.amazonaws.auth.AWSCredentialsProvider
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.internal.StaticCredentialsProvider
import com.amazonaws.services.ec2.AmazonEC2
import com.amazonaws.services.ec2.AmazonEC2Client
import com.amazonaws.services.ec2.model.*

import org.junit.Assert
import org.junit.BeforeClass
import org.junit.Test


/**
 * Short test for ec2 ebs volumes and snapshots
 */
class TestEC2Ebs {

  private static AWSCredentialsProvider credentials

  @BeforeClass
  static void init( ){
    N4j.initEndpoints( )
    this.credentials = new StaticCredentialsProvider( new BasicAWSCredentials( N4j.ACCESS_KEY, N4j.SECRET_KEY ) )
  }

  private AmazonEC2Client getEC2Client( final AWSCredentialsProvider credentials ) {
    final AmazonEC2Client ec2 = new AmazonEC2Client( credentials )
    ec2.setEndpoint( N4j.EC2_ENDPOINT )
    ec2
  }

  private void waitSnaps(AmazonEC2 ec2, String volumeId ) {
    int pending = ec2.describeSnapshots( new DescribeSnapshotsRequest( filters: [
        new Filter( name: 'volume-id', values: [ volumeId ] ),
        new Filter( name: 'status', values: [ 'pending' ] ),
    ] ) ).with {
      snapshots?.size() ?:0
    }
    if ( pending > 0 ) {
      N4j.print( "Waiting for ${pending} snapshots from volume ${volumeId} to complete" )
      N4j.sleep( 5 )
      waitSnaps( ec2, volumeId )
    }
  }

  private void checkFailedSnaps(AmazonEC2 ec2, String volumeId ) {
    N4j.print( "Checking for failed snapshots of volume ${volumeId}" )
    int failed = ec2.describeSnapshots( new DescribeSnapshotsRequest( filters: [
        new Filter( name: 'volume-id', values: [ volumeId ] ),
        new Filter( name: 'status', values: [ 'failed' ] ),
    ] ) ).with {
      snapshots?.size() ?:0
    }
    Assert.assertTrue("Failed snapshots > 0 (${failed})", failed == 0)
  }

  private void waitVolumes(AmazonEC2 ec2, List<String> volumeIds ) {
    int creating = ec2.describeVolumes( new DescribeVolumesRequest( filters: [
        new Filter( name: 'volume-id', values: volumeIds ),
        new Filter( name: 'status', values: [ 'creating' ] ),
    ] ) ).with {
      volumes?.size() ?:0
    }
    if ( creating > 0 ) {
      N4j.print( "Waiting for ${creating} volumes to complete" )
      N4j.sleep( 5 )
      waitVolumes( ec2, volumeIds )
    }
  }

  private void checkFailedVolumes(AmazonEC2 ec2, List<String> volumeIds ) {
    N4j.print( "Checking for failed volumes" )
    int failed = ec2.describeVolumes( new DescribeVolumesRequest( filters: [
        new Filter( name: 'volume-id', values: volumeIds ),
        new Filter( name: 'status', values: [ 'error' ] ),
    ] ) ).with {
      volumes?.size() ?:0
    }
    Assert.assertTrue("Failed volumes > 0 (${failed})", failed == 0)
  }

  @Test
  void testVolumesAndSnapshots( ) throws Exception {
    final List<Runnable> cleanupTasks = [] as List<Runnable>
    try {
      getEC2Client( credentials ).with { ec2 ->

        // find a zone

        final String availabilityZone = describeAvailabilityZones( ).with{
          availabilityZones?.getAt( 0 )?.zoneName
        }
        Assert.assertTrue("Availability zone not found", availabilityZone != null)
        N4j.print( "Using availability zone: ${availabilityZone}" )

        // create resources

        N4j.print( "Creating volume" )
        String volumeId = createVolume( new CreateVolumeRequest(
            size: 1,
            availabilityZone: availabilityZone
        ) ).with{
          volume?.volumeId
        }
        N4j.print( "Created volume ${volumeId}" )
        cleanupTasks.add{
          N4j.print( "Deleting volume ${volumeId}" )
          deleteVolume( new DeleteVolumeRequest( volumeId: volumeId ) )
        }
        waitVolumes( ec2, [ volumeId ]  )
        checkFailedVolumes( ec2, [ volumeId ] )

        N4j.print( "Creating snapshot from volume ${volumeId}" )
        String snapshotId = createSnapshot( new CreateSnapshotRequest(
            volumeId: volumeId,
            description: 'description goes here'
        ) ).with{
          snapshot.snapshotId
        }
        N4j.print( "Created snapshot ${snapshotId}" )
        cleanupTasks.add{
          N4j.print( "Deleting snapshot ${snapshotId}" )
          deleteSnapshot( new DeleteSnapshotRequest( snapshotId: snapshotId ) )
        }
        waitSnaps( ec2, volumeId )
        checkFailedSnaps( ec2, volumeId )

        N4j.print( "Creating volume from snapshot ${snapshotId}" )
        String snapshotVolumeId = createVolume( new CreateVolumeRequest(
            availabilityZone: availabilityZone,
            snapshotId: snapshotId
        ) ).with {
          volume?.volumeId
        }
        N4j.print( "Created volume ${snapshotVolumeId}" )
        cleanupTasks.add{
          N4j.print( "Deleting volume ${snapshotVolumeId}" )
          deleteVolume( new DeleteVolumeRequest( volumeId: snapshotVolumeId ) )
        }
        waitVolumes( ec2, [ snapshotVolumeId ]  )
        checkFailedVolumes( ec2, [ snapshotVolumeId ] )

        // test describes

        [ // filters that match both volumes
            new Filter( name: 'availability-zone', values: [ availabilityZone ] ),
            new Filter( name: 'size', values: [ '1' ] ),
        ].each { Filter filter ->
          N4j.print( "Testing describe with filter ${filter.name}" )
          describeVolumes( new DescribeVolumesRequest(
              volumeIds: [ volumeId, snapshotVolumeId ],
              filters: [ filter ]
          ) ).with {
            Assert.assertEquals( 'Volume count', 2, volumes?.size() ?:0 )
          }
        }
        [ // filters that match one volume
            new Filter( name: 'snapshot-id', values: [ snapshotId ] ),
            new Filter( name: 'volume-id', values: [ snapshotVolumeId ] ),
        ].each { Filter filter ->
          N4j.print( "Testing describe with filter ${filter.name}" )
          describeVolumes( new DescribeVolumesRequest(
              volumeIds: [ volumeId, snapshotVolumeId ],
              filters: [ filter ]
          ) ).with {
            Assert.assertEquals( 'Volume count', 1, volumes?.size() ?:0 )
          }
        }
        [ // filters that do not match
            new Filter( name: 'availability-zone', values: [ 'not-a-valid-zone' ] ),
            new Filter( name: 'size', values: [ '10' ] ),
            new Filter( name: 'snapshot-id', values: [ 'snap-00000000' ] ),
            new Filter( name: 'volume-id', values: [ 'vol-00000000' ] ),
        ].each { Filter filter ->
          N4j.print( "Testing describe with filter ${filter.name}" )
          describeVolumes( new DescribeVolumesRequest(
              volumeIds: [ volumeId, snapshotVolumeId ],
              filters: [ filter ]
          ) ).with {
            Assert.assertEquals( 'Volume count', 0, volumes?.size() ?:0 )
          }
        }

        [ // filters that match one snapshot
          new Filter( name: 'description', values: [ 'description goes here' ] ),
          new Filter( name: 'owner-alias', values: [ 'eucalyptus' ] ),
          new Filter( name: 'owner-id', values: [ N4j.ACCOUNT_ID ] ),
          new Filter( name: 'progress', values: [ '100%' ] ),
          new Filter( name: 'snapshot-id', values: [ snapshotId ] ),
          new Filter( name: 'volume-id', values: [ volumeId ] ),
          new Filter( name: 'volume-size', values: [ '1' ] ),
        ].each { Filter filter ->
          N4j.print( "Testing describe with filter ${filter.name}" )
          describeSnapshots( new DescribeSnapshotsRequest(
              snapshotIds: [ snapshotId ],
              filters: [ filter ]
          ) ).with {
            Assert.assertEquals( 'Snapshot count', 1, snapshots?.size() ?:0 )
          }
        }
        [ // filters that do not match
          new Filter( name: 'description', values: [ 'does not match' ] ),
          new Filter( name: 'owner-alias', values: [ 'does-not-match' ] ),
          new Filter( name: 'owner-id', values: [ '000000000000' ] ),
          new Filter( name: 'progress', values: [ '10%' ] ),
          new Filter( name: 'snapshot-id', values: [ 'snap-00000000' ] ),
          new Filter( name: 'volume-id', values: [ 'vol-00000000' ] ),
          new Filter( name: 'volume-size', values: [ '10' ] ),
        ].each { Filter filter ->
          N4j.print( "Testing describe with filter ${filter.name}" )
          describeSnapshots( new DescribeSnapshotsRequest(
              snapshotIds: [ snapshotId ],
              filters: [ filter ]
          ) ).with {
            Assert.assertEquals( 'Snapshot count', 0, snapshots?.size() ?:0 )
          }
        }

        // snapshot attributes

        N4j.print( "Modifying create volume permission for snapshot ${snapshotId}" )
        modifySnapshotAttribute( new ModifySnapshotAttributeRequest(
            snapshotId: snapshotId,
            attribute: 'createVolumePermission',
            operationType: 'add',
            userIds: [ '123456789012' ]
        ) )

        N4j.print( "Describing create volume permission for snapshot ${snapshotId}" )
        describeSnapshotAttribute( new DescribeSnapshotAttributeRequest(
            snapshotId: snapshotId,
            attribute: 'createVolumePermission'
        ) ).with {
          Assert.assertEquals( 'Create volume users', [ '123456789012' ], createVolumePermissions.collect{ it.userId } )
        }

        // cleanup

        N4j.print( "Deleting volume ${snapshotVolumeId}" )
        deleteVolume( new DeleteVolumeRequest( volumeId: snapshotVolumeId ) )

        N4j.print( "Deleting volume ${volumeId}" )
        deleteVolume( new DeleteVolumeRequest( volumeId: volumeId ) )

        N4j.print( "Deleting snapshot ${snapshotId}" )
        deleteSnapshot( new DeleteSnapshotRequest( snapshotId: snapshotId ) )
      }

      N4j.print( "Test complete" )
    } finally {
      // Attempt to clean up anything we created
      cleanupTasks.reverseEach { Runnable cleanupTask ->
        try {
          cleanupTask.run()
        } catch ( AmazonServiceException e ) {
          N4j.print( "Error in cleanup task, code: ${e.errorCode}, message: ${e.errorMessage}" )
        } catch ( Exception e ) {
          e.printStackTrace()
        }
      }
    }
  }
}
