package com.eucalyptus.tests.awssdk

import com.amazonaws.auth.AWSCredentialsProvider
import com.amazonaws.internal.StaticCredentialsProvider
import com.amazonaws.services.ec2.AmazonEC2Client
import com.amazonaws.services.ec2.model.AuthorizeSecurityGroupIngressRequest
import com.amazonaws.services.ec2.model.CreateKeyPairRequest
import com.amazonaws.services.ec2.model.CreateSecurityGroupRequest
import com.amazonaws.services.ec2.model.DeleteKeyPairRequest
import com.amazonaws.services.ec2.model.DeleteSecurityGroupRequest
import com.amazonaws.services.ec2.model.DescribeSecurityGroupsRequest
import com.amazonaws.services.ec2.model.ImportKeyPairRequest
import com.amazonaws.services.ec2.model.IpPermission
import com.amazonaws.services.ec2.model.IpRange
import com.amazonaws.services.ec2.model.ReleaseAddressRequest
import com.amazonaws.services.ec2.model.RevokeSecurityGroupIngressRequest
import org.junit.AfterClass
import org.junit.Assert
import org.junit.BeforeClass
import org.junit.Test

/**
 * Test EC2 api basics
 */
class TestEC2Api {

  private static String testAcct
  private static AWSCredentialsProvider testAcctAdminCredentials
  private static AmazonEC2Client ec2Client

  private static AmazonEC2Client getEC2Client( final AWSCredentialsProvider credentials ) {
    final AmazonEC2Client ec2 = new AmazonEC2Client( credentials )
    ec2.setEndpoint( N4j.EC2_ENDPOINT )
    ec2
  }

  @BeforeClass
  static void init( ){
    N4j.testInfo( TestEC2Api.simpleName )
    N4j.getCloudInfo( )
    this.testAcct = "${N4j.NAME_PREFIX}ec2-api-test"
    N4j.createAccount( testAcct )
    this.testAcctAdminCredentials = new StaticCredentialsProvider( N4j.getUserCreds( testAcct, 'admin' ) )
    this.ec2Client = getEC2Client( testAcctAdminCredentials )
  }

  @AfterClass
  static void cleanup( ) {
    if ( ec2Client ) ec2Client.shutdown( )
    N4j.deleteAccount( testAcct )
  }

  @Test
  void testElasticIpAllocateRelease( ){
    N4j.print( "Testing allocate and release for elastic ip" )
    ec2Client.with {
      String ip = allocateAddress( ).with {
        publicIp
      }
      N4j.print( "Allocated elastic ip ${ip}" )

      N4j.print( "Describing addresses to verify allocation" )
      describeAddresses( ).with {
        Assert.assertEquals( "Allocated address list", [ ip ], addresses?.collect{ it.publicIp } )
      }

      releaseAddress( new ReleaseAddressRequest(
        publicIp: ip
      ) )

      N4j.print( "Released elastic ip ${ip}" )

      N4j.print( "Describing addresses to verify release" )
      describeAddresses( ).with {
        Assert.assertEquals( "Allocated address list", [ ], addresses?.collect{ it.publicIp } )
      }
    }
  }

  @Test
  void testKeypairGenerate( ){
    N4j.print( "Testing keypair creation" )
    ec2Client.with {
      String name = 'key-name'
      N4j.print( "Creating key pair with name ${name}" )
      createKeyPair( new CreateKeyPairRequest(
          keyName: name
      ) ).with {
        Assert.assertNotNull( 'Key pair', keyPair )
        keyPair.with {
          Assert.assertNotNull( 'Key fingerprint', keyFingerprint )
          Assert.assertNotNull( 'Key material', keyMaterial )
          Assert.assertEquals( 'Key name', name, keyName)
        }
      }

      N4j.print( "Describing key pairs to verify created" )
      describeKeyPairs( ).with {
        Assert.assertEquals( "Keypair names", [ name ], keyPairs?.collect{ it.keyName } )
      }

      N4j.print( "Deletig key pair with name ${name}" )
      deleteKeyPair( new DeleteKeyPairRequest(
          keyName: name
      ) )

      N4j.print( "Describing key pairs to verify deleted" )
      describeKeyPairs( ).with {
        Assert.assertEquals( "Keypair names", [ ], keyPairs?.collect{ it.keyName } )
      }
    }
  }

  @Test
  void testKeypairImport( ){
    N4j.print( "Testing keypair import" )
    ec2Client.with {
      String name = 'imported-key-name'
      N4j.print( "Importing key pair with name ${name}" )
      importKeyPair( new ImportKeyPairRequest(
          keyName: name,
          publicKeyMaterial:
              'ssh-rsa AAAAB3NzaC1yc2EAAAADAQABAAABAQCderuZ4vY6leI+5xktpuRZHhmYItiafVsS+2kH3t' +
              'iikHcMAOo2P1DTFvfBzKWak1V3Ep9b1y8dMOOeGG1NXtxdMnX2EHi0mQB3vK+Lglx4Ei+2c6hs9X2+' +
              'WC61Dbl/T2lmLmC0AUAoh3JYAE1dIHD22TAxggMCsNLVXB7p2vUOE+XJGe71ox1o8HUXXqop26GSSg' +
              'g/yYkfVN1Rh10SGbHvLbDaRgguJIeywpfmLzIkAP4jOrjFDmjUgoFDbM9lNCGIiyaLRySD2yL8VONd' +
              '4Qn5l6o+eCytJdQseUfVUgaXEy5Eq/JrFrjYSOc1bO6+yTeMG/y3lFbL+qAVYSModbMH admin@n4j'
      ) ).with {
        Assert.assertNotNull( 'Key fingerprint', keyFingerprint )
        Assert.assertEquals( 'Key name', name, keyName)
      }

      N4j.print( "Describing key pairs to verify imported" )
      describeKeyPairs( ).with {
        Assert.assertEquals( "Keypair names", [ name ], keyPairs?.collect{ it.keyName } )
      }

      N4j.print( "Deletig key pair with name ${name}" )
      deleteKeyPair( new DeleteKeyPairRequest(
          keyName: name
      ) )

      N4j.print( "Describing key pairs to verify deleted" )
      describeKeyPairs( ).with {
        Assert.assertEquals( "Keypair names", [ ], keyPairs?.collect{ it.keyName } )
      }
    }
  }

  @Test
  void testSecurityGroupCreateDelete( ){
    N4j.print( "Testing security group create/delete" )
    ec2Client.with {
      final String name = 'group'
      N4j.print( "Creating security group named ${name}" )
      String groupId = createSecurityGroup( new CreateSecurityGroupRequest(
          groupName: name,
          description: 'description'
      ) ).with {
        groupId
      }
      Assert.assertNotNull( 'Security group id', groupId )
      N4j.print( "Created security group with id ${groupId}" )

      N4j.print( "Describing security groups named ${name}" )
      describeSecurityGroups( new DescribeSecurityGroupsRequest(
          groupNames: [ name ]
      ) ).with {
        Assert.assertEquals( 'Groups by name', [ name ], securityGroups.collect{ it.groupName } )
        Assert.assertEquals( 'Groups by id', [ groupId ], securityGroups.collect{ it.groupId } )
      }

      N4j.print( "Describing security groups with id ${groupId}" )
      describeSecurityGroups( new DescribeSecurityGroupsRequest(
          groupIds: [ groupId ]
      ) ).with {
        Assert.assertEquals( 'Groups by name', [ name ], securityGroups.collect{ it.groupName } )
        Assert.assertEquals( 'Groups by id', [ groupId ], securityGroups.collect{ it.groupId } )
      }

      N4j.print( "Deleting security group named ${name}" )
      deleteSecurityGroup( new DeleteSecurityGroupRequest(
          groupName: name
      ) )

      N4j.print( "Describing security groups named ${name} to verify deleted" )
      describeSecurityGroups( new DescribeSecurityGroupsRequest(
          groupNames: [ name ]
      ) ).with {
        Assert.assertEquals( 'Groups by name', [ ], securityGroups.collect{ it.groupName } )
      }
    }
  }

  @Test
  void testSecurityGroupAuthRevoke( ){
    N4j.print( "Testing security group auth/revoke" )
    ec2Client.with {
      final String name = 'auth-revoke-group'
      N4j.print( "Creating security group named ${name}" )
      String groupId = createSecurityGroup( new CreateSecurityGroupRequest(
          groupName: name,
          description: 'description'
      ) ).with {
        groupId
      }
      Assert.assertNotNull( 'Security group id', groupId )
      N4j.print( "Created security group with id ${groupId}" )

      N4j.print( "Authorizing tcp:22 for group ${name}" )
      authorizeSecurityGroupIngress( new AuthorizeSecurityGroupIngressRequest(
          groupName: name,
          ipPermissions: [
              new IpPermission(
                  fromPort: 22,
                  toPort: 22,
                  ipProtocol: 'tcp',
                  ipv4Ranges: [
                    new IpRange(
                        cidrIp: '0.0.0.0/0'
                    )
                  ]
              )
          ]
      ) )

      N4j.print( "Describing security group ${name} to verify ip permission added" )
      describeSecurityGroups( new DescribeSecurityGroupsRequest(
          groupNames: [ name ]
      ) ).with {
        Assert.assertEquals( 'Groups by name', [ name ], securityGroups.collect{ it.groupName } )
        Assert.assertEquals( 'Groups by id', [ groupId ], securityGroups.collect{ it.groupId } )
        securityGroups.get( 0 ).with {
          Assert.assertNotNull( 'ip permissions', ipPermissions )
          Assert.assertEquals( 'ip permissions size', 1, ipPermissions.size() )
          ipPermissions.get( 0 ).with {
            Assert.assertEquals( 'from port', 22, fromPort )
            Assert.assertEquals( 'to port', 22, toPort )
            Assert.assertEquals( 'protocol', 'tcp', ipProtocol )
          }
        }
      }

      N4j.print( "Revoking tcp:22 for group ${name}" )
      revokeSecurityGroupIngress( new RevokeSecurityGroupIngressRequest(
          groupName: name,
          ipPermissions: [
              new IpPermission(
                  fromPort: 22,
                  toPort: 22,
                  ipProtocol: 'tcp',
                  ipv4Ranges: [
                      new IpRange(
                          cidrIp: '0.0.0.0/0'
                      )
                  ]
              )
          ]
      ) )

      N4j.print( "Describing security group ${name} to verify ip permission revoked" )
      describeSecurityGroups( new DescribeSecurityGroupsRequest(
          groupNames: [ name ]
      ) ).with {
        Assert.assertEquals( 'Groups by name', [ name ], securityGroups.collect{ it.groupName } )
        Assert.assertEquals( 'Groups by id', [ groupId ], securityGroups.collect{ it.groupId } )
        securityGroups.get( 0 ).with {
          if ( ipPermissions != null ) {
            Assert.assertEquals( 'ip permissions size', 0, ipPermissions.size() )
          }
        }
      }

      N4j.print( "Deleting security group named ${name}" )
      deleteSecurityGroup( new DeleteSecurityGroupRequest(
          groupName: name
      ) )
    }
  }

  @Test
  void testAccountAttributes( ){
    N4j.print( "Describing account attributes" )
    ec2Client.with {
      describeAccountAttributes( ).with {
        N4j.print( Objects.toString( accountAttributes, '<NULL>' ) )
        Assert.assertNotNull( 'account attributes', accountAttributes )
        Assert.assertFalse( 'account attributes empty', accountAttributes.isEmpty( ) )
      }
    }
  }

  @Test
  void testDescribeRegions( ){
    N4j.print( "Describing regions" )
    ec2Client.with {
      describeRegions( ).with {
        N4j.print( Objects.toString( regions, '<NULL>' ) )
        Assert.assertNotNull( 'regions', regions )
        Assert.assertFalse( 'regions empty', regions.isEmpty( ) )
      }
    }
  }

  @Test
  void testDescribeAvailabilityZones( ){
    N4j.print( "Describing availability zones" )
    ec2Client.with {
      describeAvailabilityZones( ).with {
        N4j.print( Objects.toString( availabilityZones, '<NULL>' ) )
        Assert.assertNotNull( 'availability zones', availabilityZones )
        Assert.assertFalse( 'availability zones empty', availabilityZones.isEmpty( ) )
      }
    }
  }
}
