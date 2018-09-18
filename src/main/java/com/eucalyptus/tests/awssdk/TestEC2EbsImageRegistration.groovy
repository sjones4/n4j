package com.eucalyptus.tests.awssdk

import com.amazonaws.auth.AWSCredentialsProvider
import com.amazonaws.services.ec2.AmazonEC2
import com.amazonaws.services.ec2.model.*
import org.junit.Assert
import org.junit.Test

import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

/**
 * This test covers creating a snapshot and registering an hvm ebs image.
 */
class TestEC2EbsImageRegistration {

  private AWSCredentialsProvider credentials
  private String imageLocation = System.getProperty(
      'n4j.image.hvm-url',
      'http://cloud.centos.org/centos/7/images/CentOS-7-x86_64-GenericCloud.raw.tar.gz' )

  TestEC2EbsImageRegistration( ){
    N4j.initEndpoints( )
    this.credentials = N4j.getAdminCredentialsProvider( )
  }

  AmazonEC2 getEC2Client( AWSCredentialsProvider clientCredentials = credentials ) {
    N4j.getEc2Client(clientCredentials, N4j.EC2_ENDPOINT )
  }

  @SuppressWarnings("ChangeToOperator")
  @Test
  void testEbsHvmImageRegistration( ) throws Exception {
    final List<Runnable> cleanupTasks = [] as List<Runnable>
    try {
      AmazonEC2 ec2 = getEC2Client()

      // Find a zone to use
      final DescribeAvailabilityZonesResult azResult = ec2.describeAvailabilityZones()
      Assert.assertTrue( 'Availability zone not found', azResult.getAvailabilityZones().size() > 0 )
      String availabilityZone = azResult.getAvailabilityZones().get( 0 ).getZoneName()

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
      Assert.assertNotNull( 'Image not found', imageId )
      N4j.print( "Using image: ${imageId}" )

      // ebs image
      String imageFileName = imageLocation.substring( imageLocation.lastIndexOf('/') + 1 )
      String imageName = imageFileName.substring( 0,
          Math.min( imageFileName.length(), imageFileName.indexOf('.' ) ) ).toLowerCase( ) + '-ebs'
      N4j.print("Using image name ${imageName} from ${imageFileName}")

      // creating volume for ebs image
      String volumeId = ec2.createVolume( new CreateVolumeRequest(
          size: 8,
          availabilityZone: availabilityZone
      ) ).with {
        volume?.volumeId
      }
      N4j.print("Created volume ${volumeId} for ebs image")
      cleanupTasks.add{
        N4j.print("Deleting volume ${volumeId}")
        ec2.deleteVolume(new DeleteVolumeRequest( volumeId: volumeId ))
      }
      N4j.print("Waiting for volume ${volumeId}")
      N4j.waitForVolumes(ec2, TimeUnit.MINUTES.toMillis(5))

      // run instance with userdata for image creation
      N4j.print("Running instance to write image to volume")
      String userDataText = """\
          #!/bin/bash
          while [ ! -e /dev/vdc ] ; do
            echo "Waiting for /dev/vdc"
            sleep 10
          done
          curl ${imageLocation} | tar -xzOf - CentOS-7-x86_64-GenericCloud-1801-01.raw > /dev/vdc
          """.stripIndent( ).trim( )
      String instanceId = ec2.runInstances(new RunInstancesRequest(
          minCount: 1,
          maxCount: 1,
          instanceType: 'm1.small',
          imageId: imageId,
          placement: new Placement(
              availabilityZone: availabilityZone
          ),
          userData: Base64.encoder.encodeToString( userDataText.getBytes( StandardCharsets.UTF_8 ) )
      )).with {
        reservation?.instances?.getAt(0)?.instanceId
      }
      N4j.print("Instance launched ${instanceId}, waiting for running")
      cleanupTasks.add{
        N4j.print("Terminating instance ${instanceId}")
        ec2.terminateInstances(new TerminateInstancesRequest(instanceIds: [instanceId]))
      }
      N4j.waitForInstances(ec2, TimeUnit.MINUTES.toMillis(5))

      // attach volume to instance
      ec2.attachVolume(new AttachVolumeRequest(volumeId: volumeId, instanceId: instanceId, device: '/dev/vdc'))

      // wait for volume write
      N4j.print("Waiting for image write to volume ${volumeId}")
      N4j.waitForIt( "Image write to volume", {
        ec2.getConsoleOutput(new GetConsoleOutputRequest(instanceId: instanceId)).with {
          getDecodedOutput( ).contains("SSH HOST KEY KEYS")
        }
      }, TimeUnit.MINUTES.toMillis(15) )

      // terminate instance
      N4j.print("Terminating instance ${instanceId}")
      ec2.terminateInstances(new TerminateInstancesRequest(instanceIds: [instanceId]))
      N4j.print("Waiting for instance ${instanceId} to terminate")
      N4j.waitForInstances(ec2, TimeUnit.MINUTES.toMillis(2))

      // create snapshot
      String snapshotId = ec2.createSnapshot(new CreateSnapshotRequest(volumeId: volumeId)).with {
        snapshot?.snapshotId
      }
      N4j.print("Created snapshot ${snapshotId} for volume ${volumeId}")
      N4j.print("Wating for snapshot ${snapshotId}")
      N4j.waitForSnapshots(ec2, TimeUnit.MINUTES.toMillis(15))

      // register image
      ec2.describeImages( new DescribeImagesRequest(
          filters: [
              new Filter( name: 'name', values: [ imageName ] )
          ]
      ) ).with {
        String emi = images?.getAt(0)?.imageId
        if ( emi ) {
          N4j.print( "Deregistering existing image ${emi} with name ${imageName}" )
          ec2.deregisterImage( new DeregisterImageRequest(
              imageId: emi
          ) )
        }
        null
      }
      N4j.print( "Registering image for snapshot ${snapshotId}" )
      String emi = ec2.registerImage( new RegisterImageRequest(
          name: imageName,
          architecture: 'x86_64',
          virtualizationType: 'hvm',
          rootDeviceName: '/dev/sda1',
          blockDeviceMappings: [
              new BlockDeviceMapping(
                  deviceName: '/dev/sda1',
                  ebs: new EbsBlockDevice(
                      snapshotId: snapshotId
                  )
              )
          ]
      ) ).with { result ->
        result.imageId
      }
      N4j.print( "Registered image id ${emi}" )

      // set-up image
      N4j.print( "Setting launch permissions for ${emi}" )
      ec2.modifyImageAttribute( new ModifyImageAttributeRequest(
          imageId: emi,
          //attribute: 'launchPermission',
          launchPermission: new LaunchPermissionModifications(
            add: [
                new LaunchPermission(
                    group: 'all'
                )
            ]
          )
      ) )

      N4j.print( "Test complete" )
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
