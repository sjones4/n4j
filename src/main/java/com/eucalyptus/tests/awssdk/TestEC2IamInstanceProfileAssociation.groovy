package com.eucalyptus.tests.awssdk

import com.amazonaws.auth.AWSCredentialsProvider
import com.amazonaws.services.ec2.AmazonEC2
import com.amazonaws.services.ec2.model.*
import com.amazonaws.services.identitymanagement.model.AddRoleToInstanceProfileRequest
import com.amazonaws.services.identitymanagement.model.CreateInstanceProfileRequest
import com.amazonaws.services.identitymanagement.model.CreateRoleRequest
import com.amazonaws.services.identitymanagement.model.DeleteInstanceProfileRequest
import com.amazonaws.services.identitymanagement.model.DeleteRoleRequest
import com.github.sjones4.youcan.youare.YouAre
import org.junit.Assert
import org.junit.BeforeClass
import org.junit.Test

/**
 * This application tests EC2 IAM instance profiles
 */
class TestEC2IamInstanceProfileAssociation {

  private static AWSCredentialsProvider credentials

  @BeforeClass
  static void init( ){
    N4j.initEndpoints()
    credentials = N4j.getAdminCredentialsProvider( )
  }

  @Test
  void EC2InstanceProfileAssociationTest( ) throws Exception {
    final AmazonEC2 ec2 = N4j.getEc2Client( credentials, N4j.EC2_ENDPOINT )
    final YouAre iam = N4j.getYouAreClient( credentials, N4j.IAM_ENDPOINT )

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

    final String namePrefix = UUID.randomUUID().toString() + "-"
    N4j.print("Using prefix for test: " + namePrefix)

    final List<Runnable> cleanupTasks = [] as List<Runnable>
    try {
      String instanceProfileName = null
      String instanceProfileArn = null
      String instanceProfileNameTwo = null
      String instanceProfileArnTwo = null
      iam.with {
        final String roleName = "${namePrefix}ProfileTestRole"
        N4j.print("Creating role: ${roleName}")
        createRole( new CreateRoleRequest(
            roleName: roleName,
            path: '/path',
            assumeRolePolicyDocument: """\
              {
                "Statement": [ {
                  "Effect": "Allow",
                  "Principal": {
                     "Service": [ "ec2.amazonaws.com" ]
                  },
                  "Action": [ "sts:AssumeRole" ]
                } ]
              }
            """.stripIndent()
        ) )
        cleanupTasks.add{
          N4j.print("Deleting role: ${roleName}")
          deleteRole(new DeleteRoleRequest(
              roleName: roleName
          ))
        }

        // Create instance profile
        instanceProfileName = "${namePrefix}ProfileTest"
        N4j.print("Creating instance profile: ${instanceProfileName}")
        instanceProfileArn = createInstanceProfile(new CreateInstanceProfileRequest(
            instanceProfileName: instanceProfileName,
            path: '/path'
        ) ).with {
          instanceProfile?.arn
        }
        cleanupTasks.add{
          N4j.print("Deleting instance profile: ${instanceProfileName}")
          deleteInstanceProfile(new DeleteInstanceProfileRequest(
              instanceProfileName: instanceProfileName
          ))
        }
        addRoleToInstanceProfile(new AddRoleToInstanceProfileRequest(
            roleName: roleName,
            instanceProfileName: instanceProfileName
        ))
        N4j.print("Created instance profile with ARN: ${instanceProfileArn}")

        // Create instance profile 2
        instanceProfileNameTwo = "${namePrefix}ProfileTest2"
        N4j.print("Creating instance profile: ${instanceProfileNameTwo}")
        instanceProfileArnTwo = createInstanceProfile(new CreateInstanceProfileRequest(
            instanceProfileName: instanceProfileNameTwo,
            path: '/path'
        ) ).with {
          instanceProfile?.arn
        }
        cleanupTasks.add{
          N4j.print("Deleting instance profile: ${instanceProfileNameTwo}")
          deleteInstanceProfile(new DeleteInstanceProfileRequest(
              instanceProfileName: instanceProfileNameTwo
          ))
        }
        addRoleToInstanceProfile(new AddRoleToInstanceProfileRequest(
            roleName: roleName,
            instanceProfileName: instanceProfileNameTwo
        ))
        N4j.print("Created instance profile with ARN: ${instanceProfileArnTwo}")
      }

      ec2.with {
        N4j.print( "Running instance" )
        String instanceId = runInstances( new RunInstancesRequest(
            imageId: imageId,
            keyName: keyName,
            minCount: 1,
            maxCount: 1,
            placement: new Placement(
                availabilityZone: availabilityZone
            ),
            iamInstanceProfile: new IamInstanceProfileSpecification(
                arn: instanceProfileArn
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

        N4j.print( "Describing instance profile association" )
        String associationId = describeIamInstanceProfileAssociations( new DescribeIamInstanceProfileAssociationsRequest(
            filters: [ new Filter( name: 'instance-id', values: [ instanceId ] ) ]
        ) ).with {
          N4j.print( "Got instance profile associations: ${iamInstanceProfileAssociations}" )
          Assert.assertEquals('Association count', 1, iamInstanceProfileAssociations?.size()?:0)
          iamInstanceProfileAssociations?.getAt(0)?.associationId
        }

        N4j.print( "Replacing instance profile association" )
        replaceIamInstanceProfileAssociation( new ReplaceIamInstanceProfileAssociationRequest(
            associationId: associationId,
            iamInstanceProfile: new IamInstanceProfileSpecification(
                arn: instanceProfileArnTwo
            )
        ) )

        N4j.print( "Describing instance profile association to verify replace" )
        associationId = describeIamInstanceProfileAssociations( new DescribeIamInstanceProfileAssociationsRequest(
            filters: [ new Filter( name: 'instance-id', values: [ instanceId ] ) ]
        ) ).with {
          N4j.print( "Got instance profile associations: ${iamInstanceProfileAssociations}" )
          Assert.assertEquals('Association count', 1, iamInstanceProfileAssociations?.size()?:0)
          Assert.assertEquals('Profile arn', instanceProfileArnTwo,
              iamInstanceProfileAssociations?.getAt(0)?.iamInstanceProfile?.arn)
          iamInstanceProfileAssociations?.getAt(0)?.associationId
        }

        N4j.print( "Disassociating instance profile" )
        disassociateIamInstanceProfile( new DisassociateIamInstanceProfileRequest(
            associationId: associationId
        ) )

        N4j.print( "Describing instance profile association to verify disassociated" )
        describeIamInstanceProfileAssociations( new DescribeIamInstanceProfileAssociationsRequest(
            filters: [ new Filter( name: 'instance-id', values: [ instanceId ] ) ]
        ) ).with {
          N4j.print( "Got instance profile associations: ${iamInstanceProfileAssociations}" )
          Assert.assertEquals('Association count', 0, iamInstanceProfileAssociations?.size()?:0)
        }

        N4j.print( "Associating instance profile" )
        associateIamInstanceProfile( new AssociateIamInstanceProfileRequest(
            instanceId: instanceId,
            iamInstanceProfile: new IamInstanceProfileSpecification(
                arn: instanceProfileArn
            )
        ) )

        N4j.print( "Describing instance profile association" )
        describeIamInstanceProfileAssociations( new DescribeIamInstanceProfileAssociationsRequest(
            filters: [ new Filter( name: 'instance-id', values: [ instanceId ] ) ]
        ) ).with {
          N4j.print( "Got instance profile associations: ${iamInstanceProfileAssociations}" )
          Assert.assertEquals('Association count', 1, iamInstanceProfileAssociations?.size()?:0)
          iamInstanceProfileAssociations?.getAt(0)?.associationId
          Assert.assertEquals('Profile arn', instanceProfileArn,
              iamInstanceProfileAssociations?.getAt(0)?.iamInstanceProfile?.arn)
        }

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
