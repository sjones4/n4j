package com.eucalyptus.tests.awssdk

import com.amazonaws.auth.AWSCredentialsProvider
import com.amazonaws.auth.AWSStaticCredentialsProvider
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.services.ec2.AmazonEC2
import com.amazonaws.services.ec2.model.*
import org.junit.Test

import static com.eucalyptus.tests.awssdk.N4j.ACCESS_KEY
import static com.eucalyptus.tests.awssdk.N4j.EC2_ENDPOINT
import static com.eucalyptus.tests.awssdk.N4j.SECRET_KEY
import static com.eucalyptus.tests.awssdk.N4j.minimalInit
import static com.eucalyptus.tests.awssdk.N4j.isVPC

/**
 * This application tests VPC start/stop for ebs instances in a VPC.
 *
 * This is verification for the story:
 *
 *   https://eucalyptus.atlassian.net/browse/EUCA-9824
 */
class TestEC2VPCStartStop {

  private final AWSCredentialsProvider credentials
  private final String cidrPrefix = '172.26.64'

  TestEC2VPCStartStop( ) {
    minimalInit()
    this.credentials = new AWSStaticCredentialsProvider( new BasicAWSCredentials( ACCESS_KEY, SECRET_KEY ) )
  }

  private AmazonEC2 getEC2Client( final AWSCredentialsProvider credentials ) {
    N4j.getEc2Client(credentials, EC2_ENDPOINT)
  }

  private void assertThat( boolean condition,
                           String message ){
    N4j.assertThat(condition, message)
  }

  @Test
  void EC2VPCStartStopTest( ) throws Exception {
    final AmazonEC2 ec2 = getEC2Client( credentials )

    if ( !isVPC(ec2) ) {
      N4j.print("Unsupported networking mode. VPC required.")
      return
    }

    // Find an image to use
    final String imageId = ec2.describeImages( new DescribeImagesRequest(
        filters: [
            new Filter( name: "image-type", values: ["machine"] ),
            new Filter( name: "root-device-type", values: ["ebs"] ),
        ]
    ) ).with {
      images?.getAt( 0 )?.imageId
    }
    assertThat( imageId != null , "Image not found (ebs)" )
    N4j.print( "Using image: ${imageId}" )

    // Discover SSH key
    final String keyName = ec2.describeKeyPairs().with {
      keyPairs?.getAt(0)?.keyName
    }
    N4j.print( "Using key pair: " + keyName )

    final List<Runnable> cleanupTasks = [] as List<Runnable>
    try {
      ec2.with {
        N4j.print('Creating internet gateway')
        String internetGatewayId = createInternetGateway(new CreateInternetGatewayRequest()).with {
          internetGateway.internetGatewayId
        }
        N4j.print("Created internet gateway with id ${internetGatewayId}")
        cleanupTasks.add {
          N4j.print("Deleting internet gateway ${internetGatewayId}")
          deleteInternetGateway(new DeleteInternetGatewayRequest(internetGatewayId: internetGatewayId))
        }

        N4j.print('Creating VPC')
        String defaultDhcpOptionsId = null
        String vpcId = createVpc(new CreateVpcRequest(cidrBlock: "${cidrPrefix}.0/24")).with {
          vpc.with {
            defaultDhcpOptionsId = dhcpOptionsId
            vpcId
          }
        }
        N4j.print("Created VPC with id ${vpcId} and dhcp options id ${defaultDhcpOptionsId}")
        cleanupTasks.add {
          N4j.print("Deleting VPC ${vpcId}")
          deleteVpc(new DeleteVpcRequest(vpcId: vpcId))
        }

        N4j.print("Attaching internet gateway ${internetGatewayId} to VPC ${vpcId}")
        attachInternetGateway(new AttachInternetGatewayRequest(internetGatewayId: internetGatewayId, vpcId: vpcId))
        cleanupTasks.add {
          N4j.print("Detaching internet gateway ${internetGatewayId} from VPC ${vpcId}")
          detachInternetGateway(new DetachInternetGatewayRequest(internetGatewayId: internetGatewayId, vpcId: vpcId))
        }

        N4j.print('Creating subnet')
        String subnetId = createSubnet(new CreateSubnetRequest(vpcId: vpcId, cidrBlock: "${cidrPrefix}.0/24")).with {
          subnet.with {
            subnetId
          }
        }
        N4j.print("Created subnet with id ${subnetId}")
        cleanupTasks.add {
          N4j.print("Deleting subnet ${subnetId}")
          deleteSubnet(new DeleteSubnetRequest(subnetId: subnetId))
        }

        N4j.print( "Allocating address" )
        String allocationPublicIp = ''
        String allocationId = allocateAddress( new AllocateAddressRequest( domain: 'vpc' )).with {
          allocationPublicIp = publicIp
          allocationId
        }
        N4j.print( "Allocated address ${allocationId}" )
        cleanupTasks.add{
          N4j.print( "Releasing address ${allocationId}" )
          releaseAddress( new ReleaseAddressRequest( allocationId: allocationId ))
        }

        N4j.print( "Running instance in subnet ${subnetId}" )
        String expectedPrivateIp = "${cidrPrefix}.100"
        String instanceId = runInstances( new RunInstancesRequest(
            minCount: 1,
            maxCount: 1,
            imageId: imageId,
            keyName: keyName,
            subnetId: subnetId,
            privateIpAddress: expectedPrivateIp
        )).with {
          reservation?.with {
            instances?.getAt( 0 )?.with{
              instanceId
            }
          }
        }

        N4j.print( "Instance running with identifier ${instanceId}" )
        cleanupTasks.add{
          N4j.print( "Terminating instance ${instanceId}" )
          terminateInstances( new TerminateInstancesRequest( instanceIds: [ instanceId ] ) )

          N4j.print( "Waiting for instance ${instanceId} to terminate" )
          ( 1..25 ).find{
            sleep 5000
            N4j.print( "Waiting for instance ${instanceId} to terminate, waited ${it*5}s" )
            describeInstances( new DescribeInstancesRequest(
                instanceIds: [ instanceId ],
                filters: [ new Filter( name: "instance-state-name", values: [ "terminated" ] ) ]
            ) ).with {
              reservations?.getAt( 0 )?.instances?.getAt( 0 )?.instanceId == instanceId
            }
          }
        }

        N4j.print( "Waiting for instance ${instanceId} to start" )
        ( 1..25 ).find{
          sleep 5000
          N4j.print( "Waiting for instance ${instanceId} to start, waited ${it*5}s" )
          describeInstances( new DescribeInstancesRequest(
              instanceIds: [ instanceId ],
              filters: [ new Filter( name: "instance-state-name", values: [ "running" ] ) ]
          ) ).with {
            reservations?.getAt( 0 )?.instances?.getAt( 0 )?.instanceId == instanceId
          }
        }

        N4j.print( "Describing instance ${instanceId} to get ENI identifier" )
        String networkInterfaceId = describeInstances( new DescribeInstancesRequest(
            instanceIds: [ instanceId ]
        ) ).with {
          reservations?.getAt( 0 )?.instances?.getAt( 0 )?.networkInterfaces?.getAt( 0 )?.with{
            networkInterfaceId
          }
        }

        N4j.print( "Associating IP ${allocationPublicIp} with instance ${instanceId} network interface ${networkInterfaceId}" )
        associateAddress( new AssociateAddressRequest(
            allocationId: allocationId,
            networkInterfaceId: networkInterfaceId
        ) )

        N4j.print( "Verifying instance details" )
        describeInstances( new DescribeInstancesRequest(
            instanceIds: [ instanceId ]
        ) ).with {
          reservations?.getAt( 0 )?.instances?.getAt( 0 )?.with {
            assertThat( instanceId == it.instanceId, "Expected instance id ${instanceId}, but was: ${it.instanceId}" )
            networkInterfaces?.getAt( 0 )?.with {
              assertThat( networkInterfaceId == it.networkInterfaceId, "Expected network interface id ${networkInterfaceId}, but was: ${it.networkInterfaceId}" )
            }
            assertThat( expectedPrivateIp == privateIpAddress, "Expected private IP ${expectedPrivateIp}, but was: ${privateIpAddress}" )
            assertThat( allocationPublicIp == publicIpAddress, "Expected public IP ${allocationPublicIp}, but was: ${publicIpAddress}" )
          }
        }

        N4j.print( "Stopping instance ${instanceId}" )
        stopInstances( new StopInstancesRequest( instanceIds: [ instanceId ] ) )

        N4j.print( "Waiting for instance ${instanceId} to stop" )
        ( 1..25 ).find{
          sleep 5000
          N4j.print( "Waiting for instance ${instanceId} to stop, waited ${it*5}s" )
          describeInstances( new DescribeInstancesRequest(
              instanceIds: [ instanceId ],
              filters: [ new Filter( name: "instance-state-name", values: [ "stopped" ] ) ]
          ) ).with {
            reservations?.getAt( 0 )?.instances?.getAt( 0 )?.instanceId == instanceId
          }
        }

        N4j.print( "Starting instance ${instanceId}" )
        startInstances( new StartInstancesRequest( instanceIds: [ instanceId ] ) )

        N4j.print( "Waiting for instance ${instanceId} to start" )
        ( 1..25 ).find{
          sleep 5000
          N4j.print( "Waiting for instance ${instanceId} to start, waited ${it*5}s" )
          describeInstances( new DescribeInstancesRequest(
              instanceIds: [ instanceId ],
              filters: [ new Filter( name: "instance-state-name", values: [ "running" ] ) ]
          ) ).with {
            reservations?.getAt( 0 )?.instances?.getAt( 0 )?.instanceId == instanceId
          }
        }

        N4j.print( "Verifying instance details" )
        describeInstances( new DescribeInstancesRequest(
            instanceIds: [ instanceId ]
        ) ).with {
          reservations?.getAt( 0 )?.instances?.getAt( 0 )?.with {
            assertThat( instanceId == it.instanceId, "Expected instance id ${instanceId}, but was: ${it.instanceId}" )
            networkInterfaces?.getAt( 0 )?.with {
              assertThat( networkInterfaceId == it.networkInterfaceId, "Expected network interface id ${networkInterfaceId}, but was: ${it.networkInterfaceId}" )
            }
            assertThat( expectedPrivateIp == privateIpAddress, "Expected private IP ${expectedPrivateIp}, but was: ${privateIpAddress}" )
            assertThat( allocationPublicIp == publicIpAddress, "Expected public IP ${allocationPublicIp}, but was: ${publicIpAddress}" )
          }
        }

      }

      N4j.print( "Test complete" )
    } finally {
      // Attempt to clean up anything we created
      cleanupTasks.reverseEach { Runnable cleanupTask ->
        try {
          cleanupTask.run()
        } catch ( Exception e ) {
          // Some not-found errors are expected here so may need to be suppressed
          e.printStackTrace()
        }
      }
    }
  }
}
