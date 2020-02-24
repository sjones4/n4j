package com.eucalyptus.tests.awssdk

import com.amazonaws.AmazonServiceException
import com.amazonaws.auth.AWSCredentialsProvider
import com.amazonaws.auth.AWSStaticCredentialsProvider
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.services.route53.AmazonRoute53
import com.amazonaws.services.route53.model.Change
import com.amazonaws.services.route53.model.ChangeBatch
import com.amazonaws.services.route53.model.ChangeResourceRecordSetsRequest
import com.amazonaws.services.route53.model.CreateHostedZoneRequest
import com.amazonaws.services.route53.model.DeleteHostedZoneRequest
import com.amazonaws.services.route53.model.GetHostedZoneRequest
import com.amazonaws.services.route53.model.ResourceRecord
import com.amazonaws.services.route53.model.ResourceRecordSet
import org.junit.AfterClass
import org.junit.Assert
import org.junit.BeforeClass
import org.junit.Test

/**
 * Test iam policy functionality for route53:
 *
 * - quota for hosted zones
 * - read only access using resource wildcard and get/list action wildcards
 * - no access without any route53 permission
 * - update of hosted zone resource records using hosted zone arn
 */
class TestRoute53IamPolicy {
  private static final String testUser = "user"
  private static final String testZoneUser = "user-zone"
  private static final String testNoAccessUser = "user-noperm"
  private static final String testReadonlyUser = "user-readonly"
  private static final String testUserQuotaPolicy = """\
  {
    "Version": "2011-04-01",
    "Statement": [ {
      "Effect": "Limit",
      "Action": "route53:CreateHostedZone",
      "Resource": "*",
      "Condition": {
        "NumericLessThanEquals": {
          "route53:quota-hostedzonenumber": "1"
        }
      }
    } ]
  }
  """.stripIndent()
  private static final String testUserAuthPolicy = """\
  {
    "Version": "2011-04-01",
    "Statement": [ {
      "Effect": "Allow",
      "Action": "route53:*",
      "Resource": "*"
    } ]
  }
  """.stripIndent()
  private static final String testReadonlyUserAuthPolicy = """\
  {
    "Version": "2011-04-01",
    "Statement": [ {
      "Effect": "Allow",
      "Action": ["route53:Get*", "route53:List*"],
      "Resource": "*"
    } ]
  }
  """.stripIndent()
  private static String testAcct
  private static AWSCredentialsProvider credentials
  private static AWSCredentialsProvider testAcctAdminCredentials
  private static AWSCredentialsProvider testAcctUserCredentials
  private static AWSCredentialsProvider testAcctReadonlyUserCredentials
  private static AWSCredentialsProvider testAcctNoAccessUserCredentials
  private static AWSCredentialsProvider testAcctZoneUserCredentials

  @BeforeClass
  static void setupBeforeClass( ) {
    N4j.getCloudInfo( )
    credentials = new AWSStaticCredentialsProvider( new BasicAWSCredentials( N4j.ACCESS_KEY, N4j.SECRET_KEY ) )

    testAcct = "${N4j.NAME_PREFIX}route53iam-test-acct"
    N4j.createAccount(testAcct)
    testAcctAdminCredentials = new AWSStaticCredentialsProvider( N4j.getUserCreds(testAcct, 'admin') )

    N4j.createUser(testAcct, testUser)
    N4j.createIAMPolicy(testAcct, testUser, "quota-policy", testUserQuotaPolicy)
    N4j.createIAMPolicy(testAcct, testUser, "auth-policy", testUserAuthPolicy)
    testAcctUserCredentials = new AWSStaticCredentialsProvider( N4j.getUserCreds(testAcct, testUser) )

    N4j.createUser(testAcct, testReadonlyUser)
    N4j.createIAMPolicy(testAcct, testReadonlyUser, "auth-policy-readonly", testReadonlyUserAuthPolicy)
    testAcctReadonlyUserCredentials = new AWSStaticCredentialsProvider( N4j.getUserCreds(testAcct, testReadonlyUser) )

    N4j.createUser(testAcct, testNoAccessUser)
    testAcctNoAccessUserCredentials = new AWSStaticCredentialsProvider( N4j.getUserCreds(testAcct, testNoAccessUser) )

    N4j.createUser(testAcct, testZoneUser)
    testAcctZoneUserCredentials = new AWSStaticCredentialsProvider( N4j.getUserCreds(testAcct, testZoneUser) )
  }

  @AfterClass
  static void tearDownAfterClass( ) {
    N4j.deleteAccount(testAcct)
  }

  private AmazonRoute53 getRoute53Client(final AWSCredentialsProvider credentials ) {
    N4j.getRoute53Client( credentials, N4j.ROUTE53_ENDPOINT )
  }

  @Test
  void testHostedZoneQuota() {
    N4j.print( "Testing create/delete with quota for public hosted zone" )
    getRoute53Client(testAcctUserCredentials).with {
      N4j.print( "Creating public hosted zone 1" )
      String hostedZoneId1 = createHostedZone(new CreateHostedZoneRequest(
          callerReference: "${N4j.NAME_PREFIX}testHostedZoneQuota1",
          name: 'quota1.example.com.'
      )).with {
        hostedZone.id
      }

      String hostedZoneId2 = null;
      try {
        N4j.print( "Creating public hosted zone 2 (should fail due to limit)" )
        hostedZoneId2 = createHostedZone(new CreateHostedZoneRequest(
            callerReference: "${N4j.NAME_PREFIX}testHostedZoneQuota2",
            name: 'quota2.example.com.'
        )).with {
          hostedZone.id
        }
        Assert.fail('Expected create failure due to quota exceeded')
      } catch ( AmazonServiceException e ) {
        N4j.print( "Expected error during zone create; code: ${e.errorCode}, message: ${e.message}" )
      }

      N4j.print( "Deleting public hosted zone ${hostedZoneId1}" )
      deleteHostedZone(new DeleteHostedZoneRequest(id: hostedZoneId1))

      if (hostedZoneId2) {
        N4j.print( "Deleting public hosted zone ${hostedZoneId2}" )
        deleteHostedZone(new DeleteHostedZoneRequest(id: hostedZoneId2))
      }

      null
    }
  }

  @Test
  void testHostedZoneReadAccess() {
    N4j.print( "Testing get/list with readonly policy for public hosted zone" )
    String hostedZoneId = getRoute53Client(testAcctAdminCredentials).with {
      N4j.print( "Creating public hosted zone" )
      createHostedZone(new CreateHostedZoneRequest(
          callerReference: "${N4j.NAME_PREFIX}testHostedZoneReadAccess1",
          name: 'readaccess1.example.com.'
      )).with {
        hostedZone.id
      }
    }

    getRoute53Client(testAcctReadonlyUserCredentials).with {
      String hostedZoneId2 = null;
      try {
        N4j.print( "Creating public hosted zone 2 (should fail due to read only access)" )
        hostedZoneId2 = createHostedZone(new CreateHostedZoneRequest(
            callerReference: "${N4j.NAME_PREFIX}testHostedZoneReadAccess2",
            name: 'readaccess2.example.com.'
        )).with {
          hostedZone.id
        }
        Assert.fail('Expected create failure due to no access')
      } catch ( AmazonServiceException e ) {
        N4j.print( "Expected error during zone create; code: ${e.errorCode}, message: ${e.message}" )
      }

      if (hostedZoneId2) {
        N4j.print( "Deleting public hosted zone ${hostedZoneId2}" )
        deleteHostedZone(new DeleteHostedZoneRequest(id: hostedZoneId2))
      }

      N4j.print( "Getting hosted zone with read only access ${hostedZoneId}" )
      getHostedZone(new GetHostedZoneRequest(id: hostedZoneId)).with {
        Assert.assertNotNull("HostedZone", hostedZone)
        Assert.assertEquals("HostedZone.id", hostedZoneId, hostedZone.id)
      }

      N4j.print( "Listing hosted zone with read only access ${hostedZoneId}" )
      listHostedZones().with {
        Assert.assertNotNull("HostedZones", hostedZones)
        Assert.assertEquals("HostedZones size", 1, hostedZones.size())
      }

      null
    }

    getRoute53Client(testAcctAdminCredentials).with {
      N4j.print("Deleting public hosted zone ${hostedZoneId}")
      deleteHostedZone(new DeleteHostedZoneRequest(id: hostedZoneId))
    }
  }

  @Test
  void testHostedZoneNoAccess() {
    N4j.print( "Testing access denied for public hosted zone" )
    String hostedZoneId = getRoute53Client(testAcctAdminCredentials).with {
      N4j.print( "Creating public hosted zone" )
      createHostedZone(new CreateHostedZoneRequest(
          callerReference: "${N4j.NAME_PREFIX}testHostedZoneNoAccess",
          name: 'noaccess.example.com.'
      )).with {
        hostedZone.id
      }
    }

    try {
      getRoute53Client(testAcctNoAccessUserCredentials).with {
        listHostedZones().with {
          Assert.fail("Expected failure listing zones without permission")
        }

        null
      }
    } catch ( AmazonServiceException e ) {
      N4j.print( "Expected error during zone listing; code: ${e.errorCode}, message: ${e.message}" )
    }

    getRoute53Client(testAcctAdminCredentials).with {
      N4j.print("Deleting public hosted zone ${hostedZoneId}")
      deleteHostedZone(new DeleteHostedZoneRequest(id: hostedZoneId))
    }
  }

  @Test
  void testHostedZoneChangeResourceRecords() {
    N4j.print( "Testing resource records update policy for public hosted zone" )
    String hostedZoneId1 = getRoute53Client(testAcctAdminCredentials).with {
      N4j.print( "Creating public hosted zone 1" )
      createHostedZone(new CreateHostedZoneRequest(
          callerReference: "${N4j.NAME_PREFIX}testHostedZoneChangeResourceRecords1",
          name: 'change1.example.com.'
      )).with {
        hostedZone.id
      }
    }
    N4j.print( "Created hosted zone ${hostedZoneId1}" )

    String hostedZoneId2 = getRoute53Client(testAcctAdminCredentials).with {
      N4j.print( "Creating public hosted zone 2" )
      createHostedZone(new CreateHostedZoneRequest(
          callerReference: "${N4j.NAME_PREFIX}testHostedZoneChangeResourceRecords2",
          name: 'change2.example.com.'
      )).with {
        hostedZone.id
      }
    }
    N4j.print( "Created hosted zone ${hostedZoneId2}" )

    String zoneRrChangePolicy = """\
    {
      "Version": "2011-04-01",
      "Statement": [ {
        "Effect": "Allow",
        "Action": "route53:ChangeResourceRecordSets",
        "Resource": "arn:aws:route53:::hostedzone/${hostedZoneId1}"
      } ]
    }
    """.stripIndent()
    N4j.createIAMPolicy(testAcct, testZoneUser, "auth-policy-zone", zoneRrChangePolicy)

    getRoute53Client(testAcctZoneUserCredentials).with {
      N4j.print( "Creating resource record set for public hosted zone ${hostedZoneId1}" )
      changeResourceRecordSets(new ChangeResourceRecordSetsRequest(
          hostedZoneId: hostedZoneId1,
          changeBatch: new ChangeBatch(
              comment: 'Change resource records comment here',
              changes: [
                  new Change(
                      action: 'CREATE',
                      resourceRecordSet: new ResourceRecordSet(
                          name: 'name.change1.example.com.',
                          type: 'A',
                          TTL: 600,
                          resourceRecords: [
                              new ResourceRecord(value: '1.1.1.1')
                          ]
                      )
                  )
              ]
          )
      )).with {
        Assert.assertNotNull('ChangeInfo', changeInfo)
        Assert.assertEquals('ChangeInfo.comment',
            'Change resource records comment here', changeInfo.comment)
      }
      N4j.print( "Deleting resource record set for public hosted zone ${hostedZoneId1}" )
      changeResourceRecordSets(new ChangeResourceRecordSetsRequest(
          hostedZoneId: hostedZoneId1,
          changeBatch: new ChangeBatch(
              comment: 'Change resource records comment here',
              changes: [
                  new Change(
                      action: 'DELETE',
                      resourceRecordSet: new ResourceRecordSet(
                          name: 'name.change1.example.com.',
                          type: 'A',
                          TTL: 600,
                          resourceRecords: [
                              new ResourceRecord(value: '1.1.1.1')
                          ]
                      )
                  )
              ]
          )
      )).with {
        Assert.assertNotNull('ChangeInfo', changeInfo)
        Assert.assertEquals('ChangeInfo.comment',
            'Change resource records comment here', changeInfo.comment)
      }
      null
    }

    try {
      getRoute53Client(testAcctZoneUserCredentials).with {
        N4j.print( "Changing resource records for public hosted zone ${hostedZoneId2}" )
        changeResourceRecordSets(new ChangeResourceRecordSetsRequest(
            hostedZoneId: hostedZoneId2,
            changeBatch: new ChangeBatch(
                comment: 'Change resource records comment here',
                changes: [
                    new Change(
                        action: 'CREATE',
                        resourceRecordSet: new ResourceRecordSet(
                            name: 'name.change.example.com.',
                            type: 'A',
                            TTL: 600,
                            resourceRecords: [
                                new ResourceRecord(value: '1.1.1.1')
                            ]
                        )
                    )
                ]
            )
        )).with {
          Assert.assertNotNull('ChangeInfo', changeInfo)
          Assert.assertEquals('ChangeInfo.comment',
              'Change resource records comment here', changeInfo.comment)
        }
        null
      }
      Assert.fail("Expected failure when updating hosted zone without permission")
    } catch ( AmazonServiceException e ) {
      N4j.print( "Expected error during zone rrset change; code: ${e.errorCode}, message: ${e.message}" )
    }

    getRoute53Client(testAcctAdminCredentials).with {
      N4j.print("Deleting public hosted zone ${hostedZoneId1}")
      deleteHostedZone(new DeleteHostedZoneRequest(id: hostedZoneId1))

      N4j.print("Deleting public hosted zone ${hostedZoneId2}")
      deleteHostedZone(new DeleteHostedZoneRequest(id: hostedZoneId2))
    }
  }
}
