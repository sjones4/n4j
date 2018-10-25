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
 * This application tests EC2 instance termination protection.
 *
 * This is verification for the feature:
 *
 *   https://eucalyptus.atlassian.net/browse/EUCA-2056
 */
class TestEC2InstanceTerminationProtection {

  private static AWSCredentialsProvider credentials

  @BeforeClass
  static void init( ){
    N4j.initEndpoints()
    this.credentials = new StaticCredentialsProvider( new BasicAWSCredentials( N4j.ACCESS_KEY, N4j.SECRET_KEY ) )
  }

  private AmazonEC2Client getEC2Client( final AWSCredentialsProvider credentials ) {
    final AmazonEC2Client ec2 = new AmazonEC2Client( credentials )
    ec2.setEndpoint( N4j.EC2_ENDPOINT )
    ec2
  }

  @Test
  void EC2InstanceTerminationProtectionTest( ) throws Exception {
    final AmazonEC2 ec2 = getEC2Client( credentials )

    // Find an AZ to use
    final String availabilityZone = ec2.describeAvailabilityZones( ).with{
      availabilityZones?.getAt( 0 )?.zoneName
    }
    Assert.assertTrue("Availability zone not found", availabilityZone != null)
    N4j.print( "Using availability zone: ${availabilityZone}" )

    // Find an image to use
    final String imageId = ec2.describeImages( new DescribeImagesRequest(
        filters: [
            new Filter( name: "image-type", values: ["machine"] ),
            new Filter( name: "root-device-type", values: ["instance-store"] ),
        ]
    ) ).with {
      images?.getAt( 0 )?.imageId
    }
    Assert.assertTrue("Image not found (instance-store)", imageId != null)
    N4j.print( "Using image: ${imageId}" )

    // Discover SSH key
    final String keyName = ec2.describeKeyPairs().with {
      keyPairs?.getAt(0)?.keyName
    }
    N4j.print( "Using key pair: " + keyName )

    final List<Runnable> cleanupTasks = [] as List<Runnable>
    try {
      ec2.with {
        N4j.print( "Running instance without termination protection" )
        String instanceId = runInstances( new RunInstancesRequest(
            imageId: imageId,
            keyName: keyName,
            minCount: 1,
            maxCount: 1,
            placement: new Placement(
                availabilityZone: availabilityZone
            )
        ) ).with {
          reservation?.instances?.getAt(0)?.instanceId
        }
        cleanupTasks.add{
          N4j.print( "Terminating instance: ${instanceId}" )
          terminateInstances( new TerminateInstancesRequest(
              instanceIds: [ instanceId ]
          ) )
        }

        N4j.print( "Describing attributes for instance ${instanceId} to check termination protection disabled" )
        final Boolean disableApiTermination = describeInstanceAttribute( new DescribeInstanceAttributeRequest(
            instanceId: instanceId,
            attribute: 'disableApiTermination'
        ) ).with {
          instanceAttribute?.disableApiTermination
        }
        Assert.assertTrue("Expected false == disableApiTermination, but was: ${disableApiTermination}", Boolean.FALSE.equals(disableApiTermination))

        N4j.print( "Terminating instance ${instanceId}" )
        terminateInstances( new TerminateInstancesRequest(
            instanceIds: [ instanceId ]
        ) )
      }

      ec2.with {
        N4j.print( "Running instance with termination protection" )
        String instanceId = runInstances( new RunInstancesRequest(
            imageId: imageId,
            keyName: keyName,
            minCount: 1,
            maxCount: 1,
            placement: new Placement(
                availabilityZone: availabilityZone
            ),
            disableApiTermination: true
        ) ).with {
          reservation?.instances?.getAt(0)?.instanceId
        }
        cleanupTasks.add{
          N4j.print( "Disabling termination protection for instance ${instanceId}" )
          modifyInstanceAttribute( new ModifyInstanceAttributeRequest(
              instanceId: instanceId,
              disableApiTermination: false
          ) )
          N4j.print( "Terminating instance ${instanceId}" )
          terminateInstances( new TerminateInstancesRequest(
              instanceIds: [ instanceId ]
          ) )
        }

        N4j.print( "Describing attributes for instance ${instanceId} to check termination protection enabled" )
        final Boolean disableApiTermination = describeInstanceAttribute( new DescribeInstanceAttributeRequest(
            instanceId: instanceId,
            attribute: 'disableApiTermination'
        ) ).with {
          instanceAttribute?.disableApiTermination
        }
        Assert.assertTrue("Expected true == disableApiTermination, but was: ${disableApiTermination}", Boolean.TRUE.equals(disableApiTermination))

        N4j.print( "Terminating instance ${instanceId} (should fail)" )
        try {
          terminateInstances( new TerminateInstancesRequest(
              instanceIds: [ instanceId ]
          ) )
          Assert.assertTrue("Expected termination to fail for instance ${instanceId} with termination protection enabled", false)
        } catch ( AmazonServiceException e ) {
          N4j.print( "Expected termination failure: ${e}" )
          Assert.assertTrue("Expected error code OperationNotPermitted, but was: ${e.errorCode}", 'OperationNotPermitted' == e.errorCode)
        }

        N4j.print( "Disabling termination protection for instance ${instanceId}" )
        modifyInstanceAttribute( new ModifyInstanceAttributeRequest(
            instanceId: instanceId,
            disableApiTermination: false
        ) )

        N4j.print( "Describing attributes for instance ${instanceId} to check termination protection disabled" )
        final Boolean disableApiTermination2 = describeInstanceAttribute( new DescribeInstanceAttributeRequest(
            instanceId: instanceId,
            attribute: 'disableApiTermination'
        ) ).with {
          instanceAttribute?.disableApiTermination
        }
        Assert.assertTrue("Expected false == disableApiTermination, but was: ${disableApiTermination2}", Boolean.FALSE.equals(disableApiTermination2))

        N4j.print( "Terminating instance ${instanceId}" )
        terminateInstances( new TerminateInstancesRequest(
            instanceIds: [ instanceId ]
        ) )
      }

      ec2.with {
        N4j.print( "Running instance without termination protection" )
        String instanceId = runInstances( new RunInstancesRequest(
            imageId: imageId,
            keyName: keyName,
            minCount: 1,
            maxCount: 1,
            placement: new Placement(
                availabilityZone: availabilityZone
            )
        ) ).with {
          reservation?.instances?.getAt(0)?.instanceId
        }
        cleanupTasks.add{
          N4j.print( "Terminating instance: ${instanceId}" )
          terminateInstances( new TerminateInstancesRequest(
              instanceIds: [ instanceId ]
          ) )
        }

        N4j.print( "Enabling termination protection for instance ${instanceId}" )
        modifyInstanceAttribute( new ModifyInstanceAttributeRequest(
            instanceId: instanceId,
            disableApiTermination: true
        ) )

        N4j.print( "Describing attributes for instance ${instanceId} to check termination protection enabled" )
        final Boolean disableApiTermination = describeInstanceAttribute( new DescribeInstanceAttributeRequest(
            instanceId: instanceId,
            attribute: 'disableApiTermination'
        ) ).with {
          instanceAttribute?.disableApiTermination
        }
        Assert.assertTrue("Expected true == disableApiTermination, but was: ${disableApiTermination}", Boolean.TRUE.equals(disableApiTermination))

        N4j.print( "Terminating instance ${instanceId} (should fail)" )
        try {
          terminateInstances( new TerminateInstancesRequest(
              instanceIds: [ instanceId ]
          ) )
          Assert.assertTrue("Expected termination to fail for instance ${instanceId} with termination protection enabled", false)
        } catch ( AmazonServiceException e ) {
          N4j.print( "Expected termination failure: ${e}" )
          Assert.assertTrue("Expected error code OperationNotPermitted, but was: ${e.errorCode}", 'OperationNotPermitted' == e.errorCode)
        }

        N4j.print( "Disabling termination protection for instance ${instanceId}" )
        modifyInstanceAttribute( new ModifyInstanceAttributeRequest(
            instanceId: instanceId,
            disableApiTermination: false
        ) )

        N4j.print( "Terminating instance ${instanceId}" )
        terminateInstances( new TerminateInstancesRequest(
            instanceIds: [ instanceId ]
        ) )
      }

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
