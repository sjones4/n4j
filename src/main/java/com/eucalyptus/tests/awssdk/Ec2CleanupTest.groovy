package com.eucalyptus.tests.awssdk

import com.amazonaws.services.ec2.model.DeleteSnapshotRequest
import com.amazonaws.services.ec2.model.DeleteVolumeRequest
import com.amazonaws.services.ec2.model.DeregisterImageRequest
import com.amazonaws.services.ec2.model.DescribeImagesRequest
import com.amazonaws.services.ec2.model.TerminateInstancesRequest
import org.junit.Test

/**
 * Test that cleans up all ec2 resources for non-system accounts
 */
class Ec2CleanupTest {

  @Test
  void cleanResources( ) {
    N4j.testInfo( getClass( ).simpleName )
    N4j.initEndpoints( )

    List<String> accountNames = N4j.getYouAreClient( N4j.ACCESS_KEY, N4j.SECRET_KEY, N4j.IAM_ENDPOINT ).with {
      listAccounts( ).with {
        accounts.collect{ account -> account.accountName }
      }
    }

    List<String> nonSystemAccounts = accountNames.findAll{ name ->
      name != 'eucalyptus' && !name.startsWith('(eucalyptus)')
    }

    N4j.print( "Accounts : ${nonSystemAccounts}" )
    nonSystemAccounts.each { accountName ->
      N4j.print( "Cleaning account: ${accountName}" )
      N4j.getEc2Client( N4j.getUserCreds( accountName, 'admin' ), N4j.EC2_ENDPOINT ).with {
        describeImages( new DescribeImagesRequest( owners: ['self'] ) ).with {
          images.each { image ->
            N4j.print( "Cleaning account ${accountName} image ${image.imageId}" )
            deregisterImage( new DeregisterImageRequest( imageId: image.imageId ) )
          }
        }

        describeInstances( ).with {
          reservations.each { reservation ->
            reservation.instances.each { instance ->
              N4j.print( "Cleaning account ${accountName} instance ${instance.instanceId}" )
              terminateInstances( new TerminateInstancesRequest( instanceIds: [ instance.instanceId ] ))
            }
          }
        }

        describeVolumes( ).with {
          volumes.each { volume ->
            N4j.print( "Cleaning account ${accountName} volume ${volume.volumeId}" )
            deleteVolume( new DeleteVolumeRequest( volumeId: volume.volumeId ) )
          }
        }

        describeSnapshots( ).with {
          snapshots.each { snapshot ->
            N4j.print( "Cleaning account ${accountName} snapshot ${snapshot.snapshotId}" )
            deleteSnapshot( new DeleteSnapshotRequest( snapshotId: snapshot.snapshotId ) )
          }
        }
      }
    }
  }
}
