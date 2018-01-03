package com.eucalyptus.tests.awssdk

import com.amazonaws.AmazonServiceException
import com.amazonaws.services.ec2.AmazonEC2
import com.amazonaws.services.ec2.model.*
import com.amazonaws.services.s3.AmazonS3
import org.junit.BeforeClass
import org.junit.Test

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.temporal.ChronoUnit

import static com.eucalyptus.tests.awssdk.N4j.ACCESS_KEY
import static com.eucalyptus.tests.awssdk.N4j.SECRET_KEY
import static com.eucalyptus.tests.awssdk.N4j.EC2_ENDPOINT
import static com.eucalyptus.tests.awssdk.N4j.S3_ENDPOINT
import static com.eucalyptus.tests.awssdk.N4j.initEndpoints
import static com.eucalyptus.tests.awssdk.N4j.print
import static org.junit.Assert.*

/**
 * Test bundling of an ec2 instance
 */
class TestEC2InstanceBundle {

  private static AmazonEC2 ec2
  private static AmazonS3 s3

  @BeforeClass
  static void init( ) {
    print("### SETUP - ${TestEC2InstanceBundle.class.simpleName}")
    initEndpoints( )
    ec2 = N4j.getEc2Client( ACCESS_KEY, SECRET_KEY, EC2_ENDPOINT )
    s3 = N4j.getS3Client( ACCESS_KEY, SECRET_KEY, S3_ENDPOINT )
  }

  private AmazonEC2 getEC2Client( ) {
    ec2
  }

  private AmazonS3 getS3Client( ) {
    s3
  }

  @Test
  void testEc2InstanceBundle( ) {
    // Find an AZ to use
    final DescribeAvailabilityZonesResult azResult = ec2.describeAvailabilityZones()

    assertTrue( 'Availability zone not found', azResult.getAvailabilityZones().size() > 0 )

    String availabilityZone = azResult.getAvailabilityZones().get( 0 ).getZoneName()
    assertNotNull( 'Availability zone not found', availabilityZone )

    // Find an image to use
    final String imageId = ec2.describeImages( new DescribeImagesRequest(
        filters: [
            new Filter( name: 'image-type', values: ['machine'] ),
            new Filter( name: 'root-device-type', values: ['instance-store'] ),
            new Filter( name: 'is-public', values: ['true'] ),
        ]
    ) ).with {
      images?.getAt( 0 )?.imageId
    }
    assertNotNull( 'Image not found', imageId )
    print( "Using image: ${imageId}" )

    String instanceType = 'm1.small'
    print( "Using instance type ${instanceType} and availability zone ${availabilityZone}" )

    // Find a key pair
    final String key = ec2.describeKeyPairs( ).with {
      keyPairs?.getAt( 0 )?.keyName
    }
    print( "Using key: ${key}" )

    final String namePrefix = UUID.randomUUID().toString().substring(0, 13) + "-"
    print( "Using resource prefix for test: " + namePrefix )

    final List<Runnable> cleanupTasks = [] as List<Runnable>
    try {
      getEC2Client().with {
        getS3Client().with {
          def getInstanceState = { String instanceId ->
            describeInstances(new DescribeInstancesRequest(
                filters: [
                    new Filter(name: 'instance-id', values: [instanceId]),
                ]
            )).with {
              String resState = null
              reservations?.each { Reservation reservation ->
                reservation?.instances?.each { Instance instance ->
                  if (instance.instanceId == instanceId) {
                    resState = instance?.state?.name
                  }
                }
              }
              resState
            }
          }

          String bucket = "${namePrefix}bundling"
          print("Creating bucket ${bucket}")
          createBucket( bucket )
          cleanupTasks.add{
            print("Deleting bucket ${bucket}")
            deleteBucket( bucket )
          }

          print("Running instance")
          String instanceId
          runInstances(new RunInstancesRequest(
              imageId: imageId,
              instanceType: instanceType,
              placement: new Placement(
                  availabilityZone: availabilityZone
              ),
              keyName: key,
              minCount: 1,
              maxCount: 1,
              clientToken: "${namePrefix}client-token-0"
          )).with {
            reservation?.instances?.each { Instance instance ->
              cleanupTasks.add {
                print("Terminating instance ${instance.instanceId}")
                terminateInstances(new TerminateInstancesRequest(
                    instanceIds: [instance.instanceId]
                ))
              }
              instanceId = instance.instanceId
            }
          }

          (1..100).find { Integer iter ->
            N4j.sleep(5)
            print("Waiting for instance ${instanceId} to be running (${5 * iter}s)")
            String instanceState = getInstanceState(instanceId)
            if (instanceState == 'running') {
              instanceState
            } else if (instanceState == 'pending') {
              null
            } else {
              fail("Unexpected instance ${instanceId} state ${instanceState}")
            }
          }
          assertEquals( "Expected running state for instance ${instanceId}", 'running', getInstanceState(instanceId) )

          print("Bundle instance ${instanceId}")
          String expiry = Instant.now( ).plus( 1, ChronoUnit.DAYS ).toString( )
          String prefix = "bundle"
          String uploadPolicy = """{"expiration":"${expiry}","conditions": [{"acl": "ec2-bundle-read"},{"bucket":"${bucket}"},["starts-with","\$key","${prefix}"]]}"""
          String encodedUploadPolicy = Base64.encoder.encodeToString( uploadPolicy.getBytes(StandardCharsets.UTF_8) )
          Mac digest = Mac.getInstance('HmacSHA1')
          digest.init( new SecretKeySpec( SECRET_KEY.getBytes( StandardCharsets.UTF_8 ), 'HmacSHA1' ) )
          String uploadPolicySignature = Base64.encoder.encodeToString( digest.doFinal( encodedUploadPolicy.getBytes(StandardCharsets.UTF_8) ) )

          String taskId = bundleInstance(new BundleInstanceRequest(instanceId: instanceId, storage: new Storage(
              s3: new S3Storage(
                  bucket: bucket,
                  prefix: prefix,
                  aWSAccessKeyId: ACCESS_KEY,
                  uploadPolicy: encodedUploadPolicy,
                  uploadPolicySignature: uploadPolicySignature,
              )
          ))).with{
            bundleTask?.bundleId
          }
          assertNotNull( 'Expected bundle id', taskId )
          print("Bundling instance ${instanceId} with bundle task ${taskId}")

          String lastState = 'pending'
          (1..100).find { Integer iter ->
            N4j.sleep(5)
            print("Waiting for instance ${instanceId} bundling task ${taskId} to complete[${lastState}] (${5 * iter}s)")
            describeBundleTasks(new DescribeBundleTasksRequest(bundleIds: [taskId])).with {
              String bundlingState = bundleTasks?.getAt( 0 )?.state
              lastState = bundlingState
              if ( bundlingState == 'complete' ) {
                bundlingState
              } else if ( bundlingState == 'pending' || bundlingState == 'bundling' || bundlingState == 'storing' ) {
                null
              } else {
                fail("Unexpected instance ${instanceId} bundling task ${taskId} state ${bundlingState}")
              }
            }
          }
          assertEquals( "Unexpected state for instance ${instanceId} bundling task ${taskId}", 'complete', lastState )

          print("Terminating instance ${instanceId}")
          terminateInstances(new TerminateInstancesRequest(
              instanceIds: [instanceId]
          ))

          String bundledInstanceImageManifest = "${bucket}/${prefix}.manifest.xml";
          print("Registering image for bundle manifest ${bundledInstanceImageManifest}")
          String bundledInstanceImageId = registerImage( new RegisterImageRequest(
              name: "${namePrefix}bundled-image",
              imageLocation: bundledInstanceImageManifest,
              virtualizationType: 'hvm'
          ) ).with { result ->
            result?.imageId
          }
          print("Registered image with identifier ${bundledInstanceImageId}")
          cleanupTasks.add{
            print( "Deregistering image ${bundledInstanceImageId}" )
            deregisterImage( new DeregisterImageRequest(
                imageId: bundledInstanceImageId
            ) )
          }

          print("Running instance from bundled image ${bundledInstanceImageId}")
          String bundleInstanceId
          runInstances(new RunInstancesRequest(
              imageId: bundledInstanceImageId,
              instanceType: instanceType,
              placement: new Placement(
                  availabilityZone: availabilityZone
              ),
              keyName: key,
              minCount: 1,
              maxCount: 1,
              clientToken: "${namePrefix}client-token-1"
          )).with {
            reservation?.instances?.each { Instance instance ->
              cleanupTasks.add {
                print("Terminating instance ${instance.instanceId}")
                terminateInstances(new TerminateInstancesRequest(
                    instanceIds: [instance.instanceId]
                ))
              }
              bundleInstanceId = instance.instanceId
            }
          }

          (1..100).find { Integer iter ->
            N4j.sleep(5)
            print("Waiting for instance ${bundleInstanceId} to be running (${5 * iter}s)")
            String instanceState = getInstanceState(bundleInstanceId)
            if (instanceState == 'running') {
              instanceState
            } else if (instanceState == 'pending') {
              null
            } else {
              fail("Unexpected instance ${bundleInstanceId} state ${instanceState}")
            }
          }
          assertEquals( "Expected running state for instance ${bundleInstanceId}", 'running', getInstanceState(bundleInstanceId) )

          print("Terminating instance ${bundleInstanceId}")
          terminateInstances(new TerminateInstancesRequest(
              instanceIds: [bundleInstanceId]
          ))

          (1..100).find { Integer iter ->
            N4j.sleep(5)
            print("Waiting for instance ${bundleInstanceId} to be terminated (${5 * iter}s)")
            String instanceState = getInstanceState(bundleInstanceId)
            if (instanceState == 'terminated') {
              instanceState
            } else if (instanceState == 'shutting-down') {
              null
            } else {
              println("Unexpected instance ${bundleInstanceId} state ${instanceState}")
              instanceState // try to continue?
            }
          }

          (1..100).find { Integer iter ->
            N4j.sleep(5)
            print("Waiting for instance ${instanceId} to be terminated (${5 * iter}s)")
            String instanceState = getInstanceState(instanceId)
            if (instanceState == 'terminated') {
              instanceState
            } else if (instanceState == 'shutting-down') {
              null
            } else {
              println("Unexpected instance ${instanceId} state ${instanceState}")
              instanceState // try to continue?
            }
          }

          print("Deleting bundle artifacts for instance ${instanceId}")
          try {
            listObjects( bucket ).with {
              objectSummaries.each{ summary ->
                print("Deleting bundle artifact ${summary.key} from ${bucket} for ${instanceId}")
                deleteObject( bucket, summary.key )
              }
            }
          } catch ( AmazonServiceException e ) {
            if ( 'NoSuchBucket'.equals( e.getErrorCode( ) ) ) {
              print("Bucket ${bucket} was deleted for ${instanceId}")
            } else {
              throw e
            }
          }
        }
      }

      print( 'Test complete' )
    } finally {
      // Attempt to clean up anything we created
      print( "Running cleanup tasks" )
      final long cleanupStart = System.currentTimeMillis( )
      cleanupTasks.reverseEach { Runnable cleanupTask ->
        try {
          cleanupTask.run()
        } catch ( AmazonServiceException e ) {
          print( "${e.serviceName}/${e.errorCode}: ${e.errorMessage}" )
        } catch ( Exception e ) {
          e.printStackTrace( )
        }
      }
      print( "Completed cleanup tasks in ${System.currentTimeMillis()-cleanupStart}ms" )
    }
  }
}
