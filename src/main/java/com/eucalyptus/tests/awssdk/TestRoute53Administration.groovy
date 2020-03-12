package com.eucalyptus.tests.awssdk

import com.amazonaws.AmazonServiceException
import com.amazonaws.auth.AWSCredentialsProvider
import com.amazonaws.auth.AWSStaticCredentialsProvider
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.services.route53.AmazonRoute53
import com.amazonaws.services.route53.model.CreateHostedZoneRequest
import com.amazonaws.services.route53.model.DeleteHostedZoneRequest
import com.amazonaws.services.route53.model.HostedZone
import com.amazonaws.services.route53.model.ListHostedZonesRequest
import org.junit.AfterClass
import org.junit.Assert
import org.junit.BeforeClass
import org.junit.Test

/**
 * Test Route53 administrative functionality:
 *
 * - listing of hosted zones for all accounts
 * - deletion of hosted zone by identifier
 */
class TestRoute53Administration {
  private static AWSCredentialsProvider credentials
  private static String testAcct
  private static AWSCredentialsProvider testAcctAdminCredentials

  @BeforeClass
  static void setupBeforeClass( ) {
    N4j.getCloudInfo( )
    credentials = new AWSStaticCredentialsProvider( new BasicAWSCredentials( N4j.ACCESS_KEY, N4j.SECRET_KEY ) )

    testAcct= "${N4j.NAME_PREFIX}route53a-test-acct"
    N4j.createAccount(testAcct)
    testAcctAdminCredentials = new AWSStaticCredentialsProvider( N4j.getUserCreds(testAcct, 'admin') )
  }

  @AfterClass
  static void tearDownAfterClass( ) {
    N4j.deleteAccount(testAcct)
  }

  private AmazonRoute53 getRoute53Client(final AWSCredentialsProvider credentials ) {
    N4j.getRoute53Client( credentials, N4j.ROUTE53_ENDPOINT )
  }

  @Test
  void testRoute53Admin( ) throws Exception {
    final AmazonRoute53 route53Admin = getRoute53Client( credentials )
    final AmazonRoute53 route53User = getRoute53Client( testAcctAdminCredentials )

    final List<Runnable> cleanupTasks = [] as List<Runnable>
    try {
      String userHostedZoneId = route53User.with {
        N4j.print( "Creating public hosted zone for user" )
        String hostedZoneId = createHostedZone(new CreateHostedZoneRequest(
            callerReference: "${N4j.NAME_PREFIX}testRoute53Admin",
            name: 'example.com.'
        )).with {
          hostedZone.id
        }
        cleanupTasks.add {
          N4j.print( "Deleting hosted zone as user: ${hostedZoneId}" )
          deleteHostedZone(new DeleteHostedZoneRequest(id: hostedZoneId))
        }

        N4j.print("Created hosted zone for user ${hostedZoneId}")
        hostedZoneId
      }

      route53Admin.with {
        N4j.print( 'Describing hosted zones as admin using verbose' )
        listHostedZones(new ListHostedZonesRequest(delegationSetId: 'verbose')).with {
          Assert.assertNotNull('HostedZones', hostedZones)
          N4j.print("Got hosted zones as admin : ${hostedZones}")
          Assert.assertNotEquals('HostedZones.size', 0, hostedZones.size())
          HostedZone userZone = hostedZones.find { zone -> (userHostedZoneId == zone.id) }
          Assert.assertNotNull('userZone', userZone)
        }

        N4j.print( "Deleting hosted zone as admin ${userHostedZoneId}" )
        deleteHostedZone(new DeleteHostedZoneRequest(id: userHostedZoneId))

        N4j.print( 'Describing hosted zones as admin using verbose to ensure deleted' )
        listHostedZones(new ListHostedZonesRequest(delegationSetId: 'verbose')).with {
          N4j.print("Got hosted zones as admin : ${hostedZones}")
          if (hostedZones) {
            HostedZone userZone = hostedZones.find { zone -> (userHostedZoneId == zone.id) }
            Assert.assertNull('userZone', userZone)
          }
        }
        void
      }

      N4j.print( "Test complete" )
    } finally {
      // Attempt to clean up anything we created
      cleanupTasks.reverseEach { Runnable cleanupTask ->
        try {
          cleanupTask.run()
        } catch ( AmazonServiceException e ) {
          N4j.print( "Service error during cleanup; code: ${e.errorCode}, message: ${e.message}" )
        } catch ( Exception e ) {
          e.printStackTrace()
        }
      }
    }
  }
}
