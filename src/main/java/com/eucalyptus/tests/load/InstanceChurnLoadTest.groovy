package com.eucalyptus.tests.load

import com.amazonaws.AmazonServiceException
import com.amazonaws.ClientConfiguration
import com.amazonaws.auth.AWSCredentialsProvider
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.internal.StaticCredentialsProvider
import com.amazonaws.services.ec2.model.Address
import com.amazonaws.services.ec2.model.DescribeAddressesRequest
import com.amazonaws.services.ec2.model.DescribeAvailabilityZonesResult
import com.amazonaws.services.ec2.model.DescribeImagesRequest
import com.amazonaws.services.ec2.model.DescribeInstancesRequest
import com.amazonaws.services.ec2.model.Filter
import com.amazonaws.services.ec2.model.Instance
import com.amazonaws.services.ec2.model.Placement
import com.amazonaws.services.ec2.model.Reservation
import com.amazonaws.services.ec2.model.RunInstancesRequest
import com.amazonaws.services.ec2.model.TerminateInstancesRequest
import com.eucalyptus.tests.awssdk.N4j
import com.github.sjones4.youcan.youserv.YouServ
import com.github.sjones4.youcan.youserv.YouServClient
import com.github.sjones4.youcan.youserv.model.DescribeServicesRequest
import com.github.sjones4.youcan.youserv.model.Filter as ServiceFilter
import com.github.sjones4.youcan.youtwo.YouTwo
import com.github.sjones4.youcan.youtwo.YouTwoClient
import com.github.sjones4.youcan.youtwo.model.DescribeInstanceTypesRequest
import com.github.sjones4.youcan.youtwo.model.InstanceType
import com.github.sjones4.youcan.youtwo.model.InstanceTypeZoneStatus
import org.junit.AfterClass
import org.junit.Assert
import org.junit.BeforeClass
import org.junit.Test

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

import static com.eucalyptus.tests.awssdk.N4j.ACCESS_KEY
import static com.eucalyptus.tests.awssdk.N4j.SECRET_KEY
import static com.eucalyptus.tests.awssdk.N4j.SERVICES_ENDPOINT
import static org.junit.Assert.assertNotNull
import static org.junit.Assert.assertTrue
import static org.junit.Assert.fail

/**
 *
 */
class InstanceChurnLoadTest {
  private static String testAcct
  private static AWSCredentialsProvider testAcctAdminCredentials
  private static AWSCredentialsProvider cloudAdminCredentials
  private static YouTwo ec2Client

  @BeforeClass
  static void init( ){
    N4j.testInfo( InstanceChurnLoadTest.simpleName )
    N4j.getCloudInfo( )
    testAcct = "${N4j.NAME_PREFIX}instance-churn-load"
    N4j.createAccount( testAcct )
    testAcctAdminCredentials = new StaticCredentialsProvider( N4j.getUserCreds( testAcct, 'admin' ) )
    ec2Client = getEC2Client( testAcctAdminCredentials )
    cloudAdminCredentials = new StaticCredentialsProvider( new BasicAWSCredentials(ACCESS_KEY, SECRET_KEY) )
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

  private static YouServ bootstrapClient( ) {
    YouServClient youServ = new YouServClient( new BasicAWSCredentials( ACCESS_KEY, SECRET_KEY ) )
    youServ.setEndpoint( SERVICES_ENDPOINT )
    youServ
  }

  private int getNodeControllerCount(String availabiltyZone) {
    bootstrapClient().with {
      describeServices( new DescribeServicesRequest(
          listAll: true,
          filters: [
              new ServiceFilter( name: 'partition', values: [availabiltyZone] ),
              new ServiceFilter( name: 'service-type', values: ['node'] ),
          ]
      ) ).with {
        serviceStatuses?.size( )?:1
      }
    }
  }

  @Test
  void test( ) {
    final YouTwo ec2 = ec2Client

    // Find an AZ to use
    final DescribeAvailabilityZonesResult azResult = ec2.describeAvailabilityZones()

    assertTrue( 'Availability zone not found', azResult.getAvailabilityZones().size() > 0 )

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
    assertNotNull( 'Image not found', imageId )
    N4j.print( "Using image: ${imageId}" )

    // Find instance type with max capacity
    String instanceType = null
    Integer availability = null
    getEC2Client( cloudAdminCredentials ).with {
      instanceType = describeInstanceTypes(
          new DescribeInstanceTypesRequest( availability: true )
      ).with {
        instanceTypes.inject( (InstanceType)null ) { InstanceType maxAvailability, InstanceType item ->
          def zoneClosure = {
            InstanceTypeZoneStatus max, InstanceTypeZoneStatus cur ->
              max != null && max.available > cur.available ? max : cur
          }
          InstanceTypeZoneStatus maxZoneStatus = maxAvailability?.availability?.inject( (InstanceTypeZoneStatus)null, zoneClosure )
          InstanceTypeZoneStatus itemZoneStatus = item.availability.inject( (InstanceTypeZoneStatus)null, zoneClosure )
          if ( maxZoneStatus != null && maxZoneStatus.available > itemZoneStatus.available ) {
            availability = maxZoneStatus.available
            maxAvailability
          } else {
            availability = itemZoneStatus.available
            item
          }
        }?.name
      }
      assertNotNull( 'Instance type not found', instanceType )
      assertNotNull( 'Availability zone not found', availabilityZone )
      assertNotNull( 'Availability not found', availability )
      N4j.print( "Using instance type ${instanceType} and availability zone ${availabilityZone} with availability ${availability}" )

      //
      describeAddresses( new DescribeAddressesRequest( publicIps: ['verbose' ] ) ).with {
        int addressCount = addresses.count { Address address ->
          'nobody' == address?.instanceId
        }
        if ( addressCount < availability ) {
          N4j.print( "WARNING: Insufficient addresses ${addressCount} for available instances ${availability}" )
          availability = addressCount
        }
        void
      }
    }

    // Find a key pair
    final String key = ec2.describeKeyPairs( ).with {
      keyPairs?.getAt( 0 )?.keyName
    }
    N4j.print( "Using key: ${key}" )

    final String namePrefix = UUID.randomUUID().toString().substring(0, 13) + "-"
    N4j.print( "Using resource prefix for test: " + namePrefix )

    final Map<String, Object> runParameters = [
        imageId: imageId,
        instanceType: instanceType,
        placement: new Placement(
            availabilityZone: availabilityZone
        ),
        keyName: key,
        minCount: 1,
        maxCount: 1
    ]

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

      final int warmupInstanceCount = getNodeControllerCount(availabilityZone)
      N4j.print( "Running ${warmupInstanceCount} instances for warmup" )
      final List<String> warmupInstanceIds = ( 1..warmupInstanceCount ).collect{ instanceNum ->
        Map<String, Object> parameters = [:]
        parameters << runParameters
        parameters << [ clientToken: "${namePrefix}warmup-${instanceNum}" ]
        ec2.runInstances( new RunInstancesRequest( parameters ) ).with {
          reservation?.instances?.getAt(0)?.instanceId
        }
      }
      N4j.waitForInstances( ec2, TimeUnit.MINUTES.toMillis( 5 ) )
      N4j.terminateInstances( ec2, warmupInstanceIds )
      N4j.waitForInstances( ec2, TimeUnit.MINUTES.toMillis( 3 ) )

      final AtomicInteger launchedCount = new AtomicInteger(0)
      final AtomicInteger runningCount = new AtomicInteger(0)
      final int threads = availability > 25 ? 25 : availability
      final int iterations = 80
      N4j.print( "Churning ${iterations} instances using ${threads} threads" )
      final CountDownLatch latch = new CountDownLatch( threads )
      ( 1..threads ).each { Integer thread ->
        Thread.start {
          ec2.with {
            try {
              (1..iterations).each { Integer count ->
                N4j.print( "[${thread}] Running instance ${count}" )
                String instanceId
                Map<String, Object> parameters = [:]
                parameters << runParameters
                parameters << [ clientToken: "${namePrefix}${thread}-${count}" ]
                for ( int i=0; i<12; i++ ) {
                  try {
                    runInstances(new RunInstancesRequest(parameters)).with {
                      reservation?.instances?.each { Instance instance ->
                        instanceId = instance.instanceId
                      }
                    }
                    break
                  } catch ( AmazonServiceException e ) {
                    if ( 'ServiceUnavailable' == e.errorCode && e.statusCode == 503 && e.message.contains('resource')) {
                      N4j.print("[${thread}] Service unavailable (${e.message}), will retry instance ${count} launch in 5s")
                      N4j.sleep( 5 )
                    } else {
                      N4j.print("[${thread}] Error running instance ${count}: ${e}")
                      throw e
                    }
                  }
                }
                launchedCount.incrementAndGet( )

                (1..120).find { Integer iter ->
                  sleep( 5000 )
                  N4j.print( "[${thread}] Waiting for instance ${count} ${instanceId} to be running (${5*iter}s)" )
                  String instanceState = describeInstances( new DescribeInstancesRequest(
                      filters: [
                          new Filter( name: 'instance-state-name', values: [ 'pending', 'running' ] ),
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
                    instanceState
                  } else if ( instanceState == 'pending' ) {
                    null
                  } else if ( instanceState == null && iter < 5 ) {
                    N4j.print( "[${thread}] Null instance state ${count} ${instanceId}, treating as pending" )
                    null
                  } else if ( instanceState == null ) {
                    N4j.print( "[${thread}] Null instance state ${count} ${instanceId}, treating as running (will attempt terminate)" )
                    'running'
                  } else {
                    fail( "Unexpected instance ${count} ${instanceId} state ${instanceState}"  )
                  }
                }

                N4j.print( "[${thread}] Terminating instance ${count} ${instanceId}" )
                terminateInstances( new TerminateInstancesRequest(
                    instanceIds: [ instanceId ]
                ) )

                (1..120).find { Integer iter ->
                  sleep( 5000 )
                  N4j.print( "[${thread}] Waiting for instance ${count} ${instanceId} to be terminated (${5*iter}s)" )
                  String instanceState = describeInstances( new DescribeInstancesRequest(
                      filters: [
                          new Filter( name: 'instance-state-name', values: [ 'shutting-down', 'terminated' ] ),
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
                    instanceState
                  } else if ( instanceState == 'shutting-down' ) {
                    null
                  } else if ( instanceState == null ) {
                    N4j.print( "[${thread}] Null instance state ${count} ${instanceId}, treating as terminated" )
                    'terminated'
                  } else {
                    N4j.print( "Unexpected instance ${count} ${instanceId} state ${instanceState}"  )
                    instanceState // try to continue?
                  }
                }
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
