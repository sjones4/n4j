package com.eucalyptus.tests.load

import com.amazonaws.AmazonServiceException
import com.amazonaws.ClientConfiguration
import com.amazonaws.auth.AWSCredentialsProvider
import com.amazonaws.auth.AWSStaticCredentialsProvider
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.services.ec2.model.BlockDeviceMapping
import com.amazonaws.services.ec2.model.CreateSnapshotRequest
import com.amazonaws.services.ec2.model.DeleteSnapshotRequest
import com.amazonaws.services.ec2.model.DeleteVolumeRequest
import com.amazonaws.services.ec2.model.DeregisterImageRequest
import com.amazonaws.services.ec2.model.DescribeAvailabilityZonesResult
import com.amazonaws.services.ec2.model.DescribeImagesRequest
import com.amazonaws.services.ec2.model.DescribeInstancesRequest
import com.amazonaws.services.ec2.model.DescribeSnapshotsRequest
import com.amazonaws.services.ec2.model.EbsBlockDevice
import com.amazonaws.services.ec2.model.EbsInstanceBlockDeviceSpecification
import com.amazonaws.services.ec2.model.Filter
import com.amazonaws.services.ec2.model.Instance
import com.amazonaws.services.ec2.model.InstanceBlockDeviceMappingSpecification
import com.amazonaws.services.ec2.model.ModifyInstanceAttributeRequest
import com.amazonaws.services.ec2.model.Placement
import com.amazonaws.services.ec2.model.RegisterImageRequest
import com.amazonaws.services.ec2.model.Reservation
import com.amazonaws.services.ec2.model.RunInstancesRequest
import com.amazonaws.services.ec2.model.TerminateInstancesRequest
import com.eucalyptus.tests.awssdk.N4j
import com.github.sjones4.youcan.youtwo.YouTwo
import com.github.sjones4.youcan.youtwo.YouTwoClient
import org.junit.AfterClass
import org.junit.Assert
import org.junit.BeforeClass
import org.junit.Test

import java.nio.charset.StandardCharsets
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

import static com.eucalyptus.tests.awssdk.N4j.ACCESS_KEY
import static com.eucalyptus.tests.awssdk.N4j.SECRET_KEY
import static org.junit.Assert.assertNotNull
import static org.junit.Assert.assertTrue
import static org.junit.Assert.fail

/**
 *
 */
class EbsInstanceChurnLoadTest {
  private static String testAcct
  private static AWSCredentialsProvider testAcctAdminCredentials
  private static AWSCredentialsProvider cloudAdminCredentials
  private static YouTwo ec2Client

  @BeforeClass
  static void init( ){
    N4j.testInfo( InstanceChurnLoadTest.simpleName )
    N4j.getCloudInfo( )
    testAcct = "${N4j.NAME_PREFIX}ebs-instance-churn-load"
    N4j.createAccount( testAcct )
    testAcctAdminCredentials = new AWSStaticCredentialsProvider( N4j.getUserCreds( testAcct, 'admin' ) )
    ec2Client = getEC2Client( testAcctAdminCredentials )
    cloudAdminCredentials = new AWSStaticCredentialsProvider( new BasicAWSCredentials(ACCESS_KEY, SECRET_KEY) )
  }

  @AfterClass
  static void cleanup( ) {
    if ( ec2Client ) ec2Client.shutdown( )
    N4j.deleteAccount( testAcct )
  }

  private static YouTwo getEC2Client( final AWSCredentialsProvider credentials ) {
    YouTwo ec2 = new YouTwoClient( credentials, new ClientConfiguration(
        socketTimeout: TimeUnit.MINUTES.toMillis( 2 )
    ) )
    ec2.setEndpoint( N4j.EC2_ENDPOINT )
    ec2
  }

  @Test
  void test( ) {
    final YouTwo ec2 = ec2Client

    // Find an AZ to use
    final DescribeAvailabilityZonesResult azResult = ec2.describeAvailabilityZones()
    assertTrue( 'Availability zone not found', azResult.availabilityZones.size() > 0 )
    List<String> availabilityZones = azResult.availabilityZones*.zoneName

    // Find an image to use
    final String imageId = ec2.describeImages( new DescribeImagesRequest(
        filters: [
            new Filter( name: "image-type", values: ["machine"] ),
            new Filter( name: "root-device-type", values: ["ebs"] ),
            new Filter( name: "is-public", values: ["true"] ),
        ]
    ) ).with {
      images?.getAt( 0 )?.imageId
    }
    assertNotNull( 'Public ebs image not found', imageId )
    N4j.print( "Using image: ${imageId}" )

    // prefix
    final String namePrefix = UUID.randomUUID().toString().substring(0, 13) + "-"
    N4j.print( "Using resource prefix for test: " + namePrefix )

    final long startTime = System.currentTimeMillis( )
    final List<Runnable> cleanupTasks = new ArrayList<>( )
    try {
      cleanupTasks.add{
        N4j.print( "Terminating all pending/running instances for test account" )
        ec2.with{
          List<String> instanceIds = describeInstances( new DescribeInstancesRequest(
              filters: [
                  new Filter( name: 'instance-state-name', values: [ 'pending', 'running' ] )
              ]
          ) ).with{
            reservations.collectMany{ reservation ->
              reservation.instances.collect{ instance ->
                instance.instanceId
              }
            }
          }

          instanceIds.collate( 25 ).each{ instanceIdBatch ->
            N4j.print( "Terminating instances: ${instanceIdBatch}" )
            terminateInstances( new TerminateInstancesRequest(
                instanceIds: instanceIdBatch
            ) )
          }
        }
      }
      cleanupTasks.add {
        N4j.print("Deregistering all ebs images for test account")
        ec2.with {
          describeImages( new DescribeImagesRequest(
              filters: [
                  new Filter( name: "image-type", values: ["machine"] ),
                  new Filter( name: "root-device-type", values: ["ebs"] ),
                  new Filter( name: "is-public", values: ["true"] ),
                  new Filter( name: "name", values: ["*ebs-churn-snap-*"] ),
              ]
          ) ).with {
            images.each { image ->
              N4j.print("Deregistering ebs image ${image.imageId}")
              deregisterImage( new DeregisterImageRequest( imageId: image.imageId ) )
            }
          }
        }
      }
      cleanupTasks.add {
        N4j.print("Deleting all snapshots for test account")
        ec2.with {
          describeSnapshots( ).with {
            snapshots.each { snapshot ->
              N4j.print("Deleting snapshot ${snapshot.snapshotId}")
              deleteSnapshot( new DeleteSnapshotRequest( snapshotId: snapshot.snapshotId ) )
            }
          }
        }
      }
      cleanupTasks.add {
        N4j.print("Deleting all volumes for test account")
        ec2.with {
          describeVolumes( ).with {
            volumes.each { volume ->
              N4j.print("Deleting volume ${volume.volumeId}")
              deleteVolume( new DeleteVolumeRequest( volumeId: volume.volumeId ) )
            }
          }
        }
      }
      final String userDataText = '''\
        #!/bin/bash
        # Write 100m random data file to modify volume
        head --bytes=104857600 /dev/urandom > random-100m.dat
        '''.stripIndent()

      final AtomicInteger launchedCount = new AtomicInteger(0)
      final AtomicInteger runningCount = new AtomicInteger(0)
      final int threads = 5
      final int iterations = 5
      N4j.print( "Churning ${iterations} ebs instances using ${threads} threads" )
      final CountDownLatch latch = new CountDownLatch( threads )
      ( 1..threads ).each { Integer thread ->
        Thread.start {
          Random random = Random.newInstance()
          String lastRegisteredImageId = null
          String currentImageId = imageId
          ec2.with {
            try {
              (1..iterations).each { Integer count ->
                String cleanupImageId = null
                if ( count % 10 == 0 ) {
                  N4j.print( "[${thread}] Reverting to original image ${imageId} for next instance" )
                  cleanupImageId = currentImageId
                  currentImageId = imageId
                } else if ( lastRegisteredImageId ) {
                  N4j.print( "[${thread}] Using latest registered image ${lastRegisteredImageId} for next instance" )
                  cleanupImageId = currentImageId
                  currentImageId = lastRegisteredImageId
                }
                if ( cleanupImageId && cleanupImageId != imageId ) {
                  N4j.print( "[${thread}] Deregistering image ${cleanupImageId}" )
                  List<String> snapshotIds = describeImages(new DescribeImagesRequest(imageIds: [cleanupImageId])).with {
                    images?.getAt(0)?.blockDeviceMappings?.findAll{ it?.ebs?.snapshotId }*.ebs?.snapshotId
                  }
                  deregisterImage(new DeregisterImageRequest(imageId: cleanupImageId))
                  N4j.print( "[${thread}] Deleting snapshots ${snapshotIds} for image ${cleanupImageId}" )
                  snapshotIds.each { snapshotId ->
                    deleteSnapshot( new DeleteSnapshotRequest( snapshotId: snapshotId ) )
                  }
                }

                String availabilityZone = availabilityZones.get( random.nextInt() % availabilityZones.size( ) )
                N4j.print( "[${thread}] Running ebs instance ${count}/${iterations} in zone ${availabilityZone}" )
                String instanceId = null
                runInstances( new RunInstancesRequest(
                    minCount: 1,
                    maxCount: 1,
                    imageId: currentImageId,
                    placement: new Placement(
                        availabilityZone: availabilityZone
                    ),
                    clientToken: "${namePrefix}${thread}-${count}",
                    userData: Base64.encoder.encodeToString( userDataText.getBytes( StandardCharsets.UTF_8 ) )
                ) ).with {
                  reservation?.instances?.each{ Instance instance ->
                    instanceId = instance.instanceId
                  }
                }
                launchedCount.incrementAndGet( )

                N4j.print("[${thread}] Waiting for instance ${count}/${iterations} ${instanceId} to be running")
                N4j.waitForIt( "Instance launch", { time ->
                  String instanceState = describeInstances( new DescribeInstancesRequest(
                      filters: [
                          new Filter( name: 'instance-id', values: [ instanceId ] ),
                      ]
                  ) ).with {
                    String resState = null
                    reservations?.each{ Reservation reservation ->
                      reservation?.instances?.each { Instance instance ->
                        if ( instance.instanceId == instanceId ) {
                          resState = instance?.state?.name
                        }
                      }
                    }
                    resState
                  }
                  if ( instanceState == 'running' ) {
                    runningCount.incrementAndGet()
                    true
                  } else if ( instanceState == 'pending' ) {
                    false
                  } else if ( instanceState == null && TimeUnit.MILLISECONDS.toMinutes(time) < 2 ) {
                    N4j.print( "[${thread}] Null instance state ${count}/${iterations} ${instanceId}, treating as pending" )
                    false
                  } else {
                    fail( "[${thread}] Unexpected instance ${count}/${iterations} ${instanceId} state ${instanceState}"  )
                  }
                }, TimeUnit.MINUTES.toMillis(15) )

                N4j.print( "[${thread}] Getting volume for instance ${count}/${iterations} ${instanceId}" )
                String volumeId = describeInstances( new DescribeInstancesRequest( instanceIds: [instanceId] )).with {
                  reservations?.getAt(0)?.instances?.getAt(0)?.blockDeviceMappings?.find{ it?.ebs?.volumeId }?.ebs?.volumeId
                }

                N4j.print( "[${thread}] Modifying volume ${volumeId} delete on terminate for instance ${count}/${iterations} ${instanceId}" )
                modifyInstanceAttribute(new ModifyInstanceAttributeRequest(
                    instanceId: instanceId,
                    blockDeviceMappings: [
                        new InstanceBlockDeviceMappingSpecification(
                            deviceName: '/dev/sda',
                            ebs: new EbsInstanceBlockDeviceSpecification(
                                deleteOnTermination: false,
                                volumeId: volumeId
                            )
                        )
                    ]
                ))

                N4j.print( "[${thread}] Terminating instance ${count}/${iterations} ${instanceId}" )
                terminateInstances( new TerminateInstancesRequest(
                    instanceIds: [ instanceId ]
                ) )

                N4j.print( "[${thread}] Waiting for instance ${count}/${iterations} ${instanceId} to be terminated" )
                N4j.waitForIt( "Instance terminate", { time ->
                  String instanceState = describeInstances( new DescribeInstancesRequest(
                      filters: [
                          new Filter( name: 'instance-id', values: [ instanceId ] ),
                      ]
                  ) ).with {
                    String resState = null
                    reservations?.each{ Reservation reservation ->
                      reservation?.instances?.each { Instance instance ->
                        if ( instance.instanceId == instanceId ) {
                          resState = instance?.state?.name
                        }
                      }
                    }
                    resState
                  }
                  if ( instanceState == 'terminated' ) {
                    true
                  } else if ( instanceState == 'stopped' ) {
                    N4j.print( "[${thread}] Instance stopped ${count}/${iterations} ${instanceId} terminating"  )
                    terminateInstances( new TerminateInstancesRequest(
                        instanceIds: [ instanceId ]
                    ) )
                    true
                  } else if ( instanceState == 'shutting-down' ) {
                    false
                  } else if ( instanceState == 'running' && TimeUnit.MILLISECONDS.toMinutes(time) < 2 ) {
                    false
                  } else if ( instanceState == 'running' ) {
                    N4j.print( "[${thread}] Instance in running state ${count}/${iterations} ${instanceId}, terminating again" )
                    terminateInstances( new TerminateInstancesRequest(
                        instanceIds: [ instanceId ]
                    ) )
                    false
                  } else if ( instanceState == null && TimeUnit.MILLISECONDS.toMinutes(time) < 2 ) {
                    N4j.print( "[${thread}] Null instance state ${count}/${iterations} ${instanceId}, treating as shutting-down" )
                    false
                  } else if ( instanceState == null ) {
                    N4j.print( "[${thread}] Null instance state ${count}/${iterations} ${instanceId}, treating as terminated" )
                    true
                  } else {
                    N4j.print( "[${thread}] Unexpected instance ${count}/${iterations} ${instanceId} state ${instanceState}"  )
                    false
                  }
                }, TimeUnit.MINUTES.toMillis(3) )

                N4j.print( "[${thread}] Creating snapshot for volume ${volumeId} ${count}/${iterations}" )
                String snapshotId = createSnapshot( new CreateSnapshotRequest( volumeId: volumeId ) ).with {
                  snapshot?.snapshotId
                }
                N4j.print( "[${thread}] Waiting for snapshot ${snapshotId} creation from volume ${volumeId} ${count}/${iterations}" )
                N4j.waitForIt( "Snapshot ${snapshotId} creation", {
                  describeSnapshots( new DescribeSnapshotsRequest( snapshotIds: [snapshotId] ) ).with {
                    'completed' == snapshots?.getAt(0)?.state
                  }
                }, TimeUnit.MINUTES.toMillis(30) )

                (1..5).find { attemptNumber ->
                  N4j.print( "[${thread}] Deleting volume ${volumeId} attempt ${attemptNumber} ${count}/${iterations}" )
                  try {
                    deleteVolume( new DeleteVolumeRequest( volumeId: volumeId ) )
                    true
                  } catch ( AmazonServiceException e ) {
                    if ( attemptNumber == 5 ) throw e
                    N4j.print( "${e.serviceName}/${e.errorCode}: ${e.errorMessage}" )
                    N4j.print( "[${thread}] Sleeping before retry of delete volume ${volumeId} attempt ${attemptNumber} ${count}/${iterations}" )
                    N4j.sleep( 2 )
                    false
                  }
                }

                N4j.print( "[${thread}] Registering image for snapshot ${snapshotId} ${count}/${iterations}" )
                lastRegisteredImageId = registerImage( new RegisterImageRequest(
                    name: "ebs-churn-${snapshotId}",
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
                ) ).with { registered ->
                  registered.imageId
                }
                N4j.print( "[${thread}] Registered image id ${lastRegisteredImageId} ${count}/${iterations}" )
              }
            } finally {
              latch.countDown( )
            }
          }
        }
      }
      latch.await( )

      N4j.print( "Test complete in ${System.currentTimeMillis()-startTime}ms [running/launched/target:${runningCount.get()}/${launchedCount.get()}/${threads*iterations}]" )
      Assert.assertEquals( 'Launched instance count matches running', launchedCount.get(), runningCount.get() )
      Assert.assertEquals( 'Target instance count matches running', (threads*iterations), runningCount.get() )
    } finally {
      N4j.print( "Running cleanup tasks" )
      final long cleanupStart = System.currentTimeMillis( )
      cleanupTasks.reverseEach { Runnable cleanupTask ->
        try {
          cleanupTask.run()
        } catch ( AmazonServiceException e ) {
          N4j.print( "${e.serviceName}/${e.errorCode}: ${e.errorMessage}" )
        } catch ( Exception e ) {
          e.printStackTrace( )
        }
      }
      N4j.print( "Completed cleanup tasks in ${System.currentTimeMillis()-cleanupStart}ms" )
    }
  }
}
