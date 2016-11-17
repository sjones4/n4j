package com.eucalyptus.tests.awssdk

import com.amazonaws.AmazonServiceException
import com.amazonaws.ClientConfiguration
import com.amazonaws.auth.AWSCredentialsProvider
import com.amazonaws.auth.AWSStaticCredentialsProvider
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.services.ec2.AmazonEC2
import com.amazonaws.services.ec2.AmazonEC2Client
import com.amazonaws.services.ec2.model.CreateSecurityGroupRequest
import com.amazonaws.services.ec2.model.CreateTagsRequest
import com.amazonaws.services.ec2.model.DeleteSecurityGroupRequest
import com.amazonaws.services.ec2.model.Tag as Ec2Tag
import com.github.sjones4.youcan.youbill.YouBill
import com.github.sjones4.youcan.youbill.YouBillClient
import com.github.sjones4.youcan.youbill.model.ModifyAccountRequest
import com.github.sjones4.youcan.youbill.model.ModifyBillingRequest
import com.github.sjones4.youcan.youbill.model.ViewAccountRequest
import com.github.sjones4.youcan.youbill.model.ViewBillingRequest
import org.testng.annotations.Test

import static N4j.minimalInit
import static N4j.CLC_IP
import static N4j.ACCESS_KEY
import static N4j.SECRET_KEY

/**
 * Tests billing service settings actions functionality.
 *
 * Related issues:
 *   https://eucalyptus.atlassian.net/browse/EUCA-12618
 */
class TestBillingSettings {

  private final String host;
  private final AWSCredentialsProvider credentials;

  public static void main( String[] args ) throws Exception {
    new TestBillingSettings( ).billingServiceSettingsTest( )
  }

  public TestBillingSettings( ) {
    minimalInit( )
    this.host = CLC_IP
    this.credentials = new AWSStaticCredentialsProvider( new BasicAWSCredentials( ACCESS_KEY, SECRET_KEY ) )
  }

  private String cloudUri( String servicePath ) {
    URI.create( "http://" + host + ":8773/" )
        .resolve( servicePath )
        .toString()
  }

  private AmazonEC2 getEC2Client( final AWSCredentialsProvider credentials ) {
    final AmazonEC2 ec2 = new AmazonEC2Client( credentials )
    ec2.setEndpoint( cloudUri( "/services/compute" ) )
    ec2
  }

  private YouBill getYouBillClient(final AWSCredentialsProvider credentials, String signerOverride = null ) {
    final YouBillClient bill = new YouBillClient(
        credentials,
        signerOverride ? new ClientConfiguration( signerOverride: signerOverride ) : new ClientConfiguration( )
    )
    bill.setEndpoint( cloudUri( '/services/Portal' ) )
    bill
  }

  @Test
  public void billingServiceSettingsTest( ) throws Exception {
    final AmazonEC2 ec2 = getEC2Client( credentials )

    final String namePrefix = UUID.randomUUID().toString().substring(0, 13) + "-";
    N4j.print( "Using resource prefix for test: " + namePrefix );

    final List<Runnable> cleanupTasks = [] as List<Runnable>

    // verify that signature v2 requests are rejected
    try {
      N4j.print( 'Making tag service request with unsupported signature version' )
      getYouBillClient(credentials, 'QueryStringSignerType').viewAccount( new ViewAccountRequest( ) )
      N4j.assertThat( false, 'Expected error due to request with unsupported signature version' )
    } catch ( AmazonServiceException e ) {
      N4j.print( "Exception for request with invalid signature version: ${e}" )
      N4j.assertThat(
          (e.message?:'').contains( 'ignature version not supported' ),
          'Expected failure due to signature version' )
    }

    try {
      // create security group with tag so we can ensure tags shown for view billing
      ec2.with {
        String securityGroupName = "${namePrefix}group1"
        N4j.print( "Creating security group: ${securityGroupName}" )
        String groupId = createSecurityGroup( new CreateSecurityGroupRequest(
            groupName: securityGroupName,
            description: 'tag test group'
        ) ).with {
          groupId
        }
        N4j.print( "Created security group: ${groupId}" )
        cleanupTasks.add{
          N4j.print( "Deleting security group: ${securityGroupName}/${groupId}" )
          deleteSecurityGroup( new DeleteSecurityGroupRequest( groupId: groupId ) )
        }
        N4j.print( "Tagging security group: ${groupId}" )
        createTags( new CreateTagsRequest(
            resources: [ groupId ],
            tags: [
              new Ec2Tag( key: 'bill-one', value: 'bill-1' ),
            ]
        ) )
      }

      getYouBillClient( credentials ).with {
        cleanupTasks.add{
          N4j.print( 'Restoring default account settings' )
          modifyAccount( new ModifyAccountRequest(
              userBillingAccess: false
          ) )
        }
        cleanupTasks.add{
          N4j.print( 'Restoring default billing settings' )
          modifyBilling( new ModifyBillingRequest(
              detailedBillingEnabled: false,
              activeCostAllocationTags: [ ]
          ) )
        }

        N4j.print( 'Getting account settings' )
        boolean userBillingAccessEnabled = false
        viewAccount( new ViewAccountRequest( ) ).with {
          N4j.print( "Account settings: ${it}" )
          N4j.assertThat( accountSettings?.userBillingAccess != null, "Expected userBillingAccess setting" )
          userBillingAccessEnabled = accountSettings?.userBillingAccess
        }

        N4j.print( 'Modifying account settings' )
        modifyAccount( new ModifyAccountRequest(
            userBillingAccess: !userBillingAccessEnabled
        ) ).with {
          N4j.print( "Modified account settings: ${it}" )
          N4j.assertThat(
              accountSettings?.userBillingAccess != userBillingAccessEnabled,
              "Expected userBillingAccess ${!userBillingAccessEnabled}" )
        }

        N4j.print( 'Getting account settings to verify updated' )
        viewAccount( new ViewAccountRequest( ) ).with {
          N4j.print( "Account settings: ${it}" )
          N4j.assertThat(
              accountSettings?.userBillingAccess != userBillingAccessEnabled,
              "Expected userBillingAccess ${!userBillingAccessEnabled}" )
        }


        N4j.print( 'Getting billing settings' )
        boolean detailedBillingReportsEnabled = false
        viewBilling( new ViewBillingRequest( ) ).with {
          N4j.print( "Billing settings: ${it}" )
          N4j.assertThat( billingSettings?.detailedBillingEnabled != null, "Expected detailedBillingEnabled setting" )
          N4j.assertThat(
              billingMetadata?.inactiveCostAllocationTags != null,
              "Expected inactiveCostAllocationTags setting" )
          N4j.assertThat(
              billingMetadata?.inactiveCostAllocationTags?.contains('bill-one'),
              "Expected inactiveCostAllocationTags 'bill-one'" )
          detailedBillingReportsEnabled = billingSettings?.detailedBillingEnabled
        }

        N4j.print( 'Modifying billing settings' )
        modifyBilling( new ModifyBillingRequest(
            detailedBillingEnabled: !detailedBillingReportsEnabled,
            activeCostAllocationTags: [ 'bill-one', 'bill-two' ]
        ) ).with {
          N4j.print( "Modified billing settings: ${it}" )
          N4j.assertThat(
              billingSettings?.detailedBillingEnabled != detailedBillingReportsEnabled,
              "Expected detailedBillingEnabled ${!detailedBillingReportsEnabled}" )
          N4j.assertThat(
              billingSettings?.activeCostAllocationTags != null,
              "Expected activeCostAllocationTags setting" )
          N4j.assertThat(
              billingSettings?.activeCostAllocationTags?.contains('bill-one'),
              "Expected activeCostAllocationTags 'bill-one'" )
          N4j.assertThat(
              billingSettings?.activeCostAllocationTags?.contains('bill-two'),
              "Expected activeCostAllocationTags 'bill-two'" )
          N4j.assertThat(
              2 == billingSettings?.activeCostAllocationTags?.size(),
              "Expected 2 activeCostAllocationTags" )
        }

        N4j.print( 'Getting billing settings to verify updated' )
        viewBilling( new ViewBillingRequest( ) ).with {
          N4j.print( "Billing settings: ${it}" )
          N4j.assertThat(
              billingSettings?.detailedBillingEnabled != detailedBillingReportsEnabled,
              "Expected detailedBillingEnabled ${!detailedBillingReportsEnabled}" )
          N4j.assertThat(
              billingSettings?.activeCostAllocationTags != null,
              "Expected activeCostAllocationTags setting" )
          N4j.assertThat(
              billingSettings?.activeCostAllocationTags?.contains('bill-one'),
              "Expected activeCostAllocationTags 'bill-one'" )
          N4j.assertThat(
              billingSettings?.activeCostAllocationTags?.contains('bill-two'),
              "Expected activeCostAllocationTags 'bill-two'" )
          N4j.assertThat(
              2 == billingSettings?.activeCostAllocationTags?.size(),
              "Expected 2 activeCostAllocationTags" )
          N4j.assertThat(
              !billingMetadata?.inactiveCostAllocationTags?.contains('bill-one'),
              "Unexpected inactiveCostAllocationTags 'bill-one'" )
        }
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
