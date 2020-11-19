package com.eucalyptus.tests.awssdk

import com.amazonaws.auth.AWSCredentialsProvider
import com.amazonaws.auth.AWSStaticCredentialsProvider
import com.amazonaws.services.route53.AmazonRoute53
import com.amazonaws.services.route53.model.AliasTarget
import com.amazonaws.services.route53.model.Change
import com.amazonaws.services.route53.model.ChangeBatch
import com.amazonaws.services.route53.model.ChangeResourceRecordSetsRequest
import com.amazonaws.services.route53.model.ChangeTagsForResourceRequest
import com.amazonaws.services.route53.model.CreateHostedZoneRequest
import com.amazonaws.services.route53.model.DeleteHostedZoneRequest
import com.amazonaws.services.route53.model.GetHostedZoneLimitRequest
import com.amazonaws.services.route53.model.GetHostedZoneRequest
import com.amazonaws.services.route53.model.HostedZoneConfig
import com.amazonaws.services.route53.model.ListTagsForResourceRequest
import com.amazonaws.services.route53.model.ResourceRecord
import com.amazonaws.services.route53.model.ResourceRecordSet
import com.amazonaws.services.route53.model.Tag
import com.amazonaws.services.route53.model.UpdateHostedZoneCommentRequest
import org.junit.AfterClass
import org.junit.Assert
import org.junit.BeforeClass
import org.junit.Test

/**
 * Test Route53 api basics
 */
class TestRoute53Api {


  private static String testAcct
  private static AWSCredentialsProvider testAcctAdminCredentials
  private static AmazonRoute53 route53Client

  @BeforeClass
  static void init( ){
    N4j.testInfo( TestRoute53Api.simpleName )
    N4j.getCloudInfo( )
    testAcct = "${N4j.NAME_PREFIX}route53-api-test"
    N4j.createAccount( testAcct )
    testAcctAdminCredentials = new AWSStaticCredentialsProvider( N4j.getUserCreds( testAcct, 'admin' ) )
    route53Client = N4j.getRoute53Client( testAcctAdminCredentials, N4j.ROUTE53_ENDPOINT )
  }

  @AfterClass
  static void cleanup( ) {
    if ( route53Client ) route53Client.shutdown( )
    N4j.deleteAccount( testAcct )
  }

  @Test
  void testCreatePublicHostedZone() {
    N4j.print( "Testing create/delete for public hosted zone" )
    route53Client.with {
      String callerRef = "${N4j.NAME_PREFIX}testCreatePublicHostedZone"
      String hostedZoneId = createHostedZone(new CreateHostedZoneRequest(
          callerReference: callerRef,
          name: 'example.com.'
      )).with {
        Assert.assertNotNull('HostedZone', hostedZone)
        Assert.assertNotNull('HostedZone.id', hostedZone.id)
        Assert.assertEquals('HostedZone.name', 'example.com.', hostedZone.name)
        Assert.assertEquals('HostedZone.callerReference', callerRef, hostedZone.callerReference)
        Assert.assertEquals('HostedZone.resourceRecordSetCount', 2L, hostedZone.resourceRecordSetCount)
        Assert.assertNotNull('HostedZone.config', hostedZone.config)
        Assert.assertNotNull('HostedZone.config.privateZone', hostedZone.config.privateZone)
        Assert.assertFalse('HostedZone.config.privateZone', hostedZone.config.privateZone)
        hostedZone.id
      }

      N4j.print( "Deleting public hosted zone ${hostedZoneId}" )
      deleteHostedZone(new DeleteHostedZoneRequest(id: hostedZoneId))
    }
  }

  @Test
  void testGetHostedZone() {
    N4j.print( "Testing create/get/delete for public hosted zone" )
    route53Client.with {
      String callerRef = "${N4j.NAME_PREFIX}testGetHostedZone"
      String comment = 'HostedZone comment here'
      String hostedZoneId = createHostedZone(new CreateHostedZoneRequest(
          callerReference: callerRef,
          name: 'example.com.',
          hostedZoneConfig: new HostedZoneConfig(
              comment: comment,
              privateZone: false
          )
      )).with {
        hostedZone.id
      }

      N4j.print( "Getting public hosted zone ${hostedZoneId}" )
      getHostedZone(new GetHostedZoneRequest(id: hostedZoneId)).with {
        Assert.assertNotNull('HostedZone', hostedZone)
        Assert.assertEquals('HostedZone.id', hostedZoneId, hostedZone.id)
        Assert.assertEquals('HostedZone.name', 'example.com.', hostedZone.name)
        Assert.assertEquals('HostedZone.callerReference', callerRef, hostedZone.callerReference)
        Assert.assertEquals('HostedZone.resourceRecordSetCount', 2L, hostedZone.resourceRecordSetCount)
        Assert.assertNotNull('HostedZone.config', hostedZone.config)
        Assert.assertNotNull('HostedZone.config.privateZone', hostedZone.config.privateZone)
        Assert.assertFalse('HostedZone.config.privateZone', hostedZone.config.privateZone)
        Assert.assertEquals('HostedZone.config.comment', comment, hostedZone.config.comment)
      }

      N4j.print( "Deleting public hosted zone ${hostedZoneId}" )
      deleteHostedZone(new DeleteHostedZoneRequest(id: hostedZoneId))
    }
  }

  @Test
  void testGetHostedZoneCount() {
    N4j.print( "Testing create/get count/delete for public hosted zone" )
    route53Client.with {
      String callerRef = "${N4j.NAME_PREFIX}testGetHostedZoneCount"
      String hostedZoneId = createHostedZone(new CreateHostedZoneRequest(
          callerReference: callerRef,
          name: 'example.com.'
      )).with {
        hostedZone.id
      }

      N4j.print( "Getting public hosted zone count ${hostedZoneId}" )
      getHostedZoneCount().with {
        Assert.assertNotNull('hostedZoneCount', hostedZoneCount)
        Assert.assertEquals('hostedZoneCount', 1L, hostedZoneCount)
      }

      N4j.print( "Deleting public hosted zone ${hostedZoneId}" )
      deleteHostedZone(new DeleteHostedZoneRequest(id: hostedZoneId))
    }
  }

  @Test
  void testGetHostedZoneLimits() {
    N4j.print( "Testing create/get limits/delete for public hosted zone" )
    route53Client.with {
      String callerRef = "${N4j.NAME_PREFIX}testGetHostedZoneLimits"
      String hostedZoneId = createHostedZone(new CreateHostedZoneRequest(
          callerReference: callerRef,
          name: 'example.com.'
      )).with {
        hostedZone.id
      }

      [
          'MAX_RRSETS_BY_ZONE',
          'MAX_VPCS_ASSOCIATED_BY_ZONE'
      ].each { requestLimit ->
        N4j.print( "Getting hosted zone limit ${requestLimit}" )
        getHostedZoneLimit(new GetHostedZoneLimitRequest(
            hostedZoneId: hostedZoneId,
            type: requestLimit
        )).with {
          Assert.assertNotNull('Count', count)
          Assert.assertNotNull('limit', limit)
          Assert.assertEquals('limit.type', requestLimit, limit.type)
        }
      }

      N4j.print( "Deleting public hosted zone ${hostedZoneId}" )
      deleteHostedZone(new DeleteHostedZoneRequest(id: hostedZoneId))
    }
  }

  @Test
  void testUpdateHostedZoneComment() {
    N4j.print( "Testing create/update comment/get/delete for public hosted zone" )
    route53Client.with {
      String callerRef = "${N4j.NAME_PREFIX}testUpdateHostedZoneComment"
      String comment1 = 'HostedZone comment one'
      String comment2 = 'HostedZone comment two'
      String hostedZoneId = createHostedZone(new CreateHostedZoneRequest(
          callerReference: callerRef,
          name: 'example.com.',
          hostedZoneConfig: new HostedZoneConfig(
              comment: comment1,
              privateZone: false
          )
      )).with {
        hostedZone.id
      }

      N4j.print( "Updating comment for public hosted zone ${hostedZoneId}" )
      updateHostedZoneComment(new UpdateHostedZoneCommentRequest(
          id: hostedZoneId,
          comment: comment2
      ))

      N4j.print( "Getting public hosted zone ${hostedZoneId}" )
      getHostedZone(new GetHostedZoneRequest(id: hostedZoneId)).with {
        Assert.assertNotNull('HostedZone', hostedZone)
        Assert.assertEquals('HostedZone.id', hostedZoneId, hostedZone.id)
        Assert.assertNotNull('HostedZone.config', hostedZone.config)
        Assert.assertEquals('HostedZone.config.comment', comment2, hostedZone.config.comment)
      }

      N4j.print( "Removing comment for public hosted zone ${hostedZoneId}" )
      updateHostedZoneComment(new UpdateHostedZoneCommentRequest(
          id: hostedZoneId
      ))

      N4j.print( "Getting public hosted zone ${hostedZoneId}" )
      getHostedZone(new GetHostedZoneRequest(id: hostedZoneId)).with {
        Assert.assertNotNull('HostedZone', hostedZone)
        Assert.assertEquals('HostedZone.id', hostedZoneId, hostedZone.id)
        Assert.assertNotNull('HostedZone.config', hostedZone.config)
        Assert.assertNull('HostedZone.config.comment', hostedZone.config.comment)
      }

      N4j.print( "Deleting public hosted zone ${hostedZoneId}" )
      deleteHostedZone(new DeleteHostedZoneRequest(id: hostedZoneId))
    }
  }

  @Test
  void testHostedZoneTagging() {
    N4j.print( "Testing create/tag/delete for public hosted zone" )
    route53Client.with {
      String callerRef = "${N4j.NAME_PREFIX}testHostedZoneTagging"
      String hostedZoneId = createHostedZone(new CreateHostedZoneRequest(
          callerReference: callerRef,
          name: 'example.com.'
      )).with {
        hostedZone.id
      }

      N4j.print( "Tagging hosted zone with single large tag ${hostedZoneId}" )
      changeTagsForResource( new ChangeTagsForResourceRequest(
          resourceType: 'hostedzone',
          resourceId: hostedZoneId,
          addTags: [
            new Tag(key: 'k1', value: 'v' * 256)
          ]
      ) )

      N4j.print( "Tagging hosted zone with too large tag ${hostedZoneId} (should fail)" )
      try {
        changeTagsForResource( new ChangeTagsForResourceRequest(
            resourceType: 'hostedzone',
            resourceId: hostedZoneId,
            addTags: [
                new Tag(key: 'k1', value: 'v' * 257)
            ]
        ) )
        Assert.fail('Expected failure for large tag value')
      } catch(Exception e) {
        N4j.print("Got expected failure for large tag value: ${e}")
      }

      N4j.print( "Tagging hosted zone with ten tags ${hostedZoneId}" )
      changeTagsForResource( new ChangeTagsForResourceRequest(
          resourceType: 'hostedzone',
          resourceId: hostedZoneId,
          addTags: [
              new Tag(key: 'k0', value: 'v0'),
              new Tag(key: 'k1', value: 'v1'),
              new Tag(key: 'k2', value: 'v2'),
              new Tag(key: 'k3', value: 'v3'),
              new Tag(key: 'k4', value: 'v4'),
              new Tag(key: 'k5', value: 'v5'),
              new Tag(key: 'k6', value: 'v6'),
              new Tag(key: 'k7', value: 'v7'),
              new Tag(key: 'k8', value: 'v8'),
              new Tag(key: 'k9', value: 'v9'),
          ]
      ) )

      N4j.print( "Listing hosted zone to verify tags ${hostedZoneId}" )
      listTagsForResource( new ListTagsForResourceRequest(
          resourceType: 'hostedzone',
          resourceId: hostedZoneId,
      )).with {
        N4j.print("Tags: ${resourceTagSet}")
        Assert.assertNotNull('ResourceTagSet', resourceTagSet)
        Assert.assertEquals('Resource type', 'hostedzone', resourceTagSet.resourceType)
        Assert.assertEquals('Resource id', hostedZoneId, resourceTagSet.resourceId)
        Assert.assertNotNull('ResourceTagSet', resourceTagSet.tags)
        Assert.assertEquals('resource tag count', 10, resourceTagSet.tags.size())
        (0..9).each { index ->
          Assert.assertEquals("resource tag key[${index}]", "k${index}" as String, resourceTagSet.tags.get(index).key)
          Assert.assertEquals("resource tag value[${index}]", "v${index}" as String, resourceTagSet.tags.get(index).value)
        }
      }

      N4j.print( "Tagging hosted zone with ten updated tags ${hostedZoneId}" )
      changeTagsForResource( new ChangeTagsForResourceRequest(
          resourceType: 'hostedzone',
          resourceId: hostedZoneId,
          addTags: [
              new Tag(key: 'k0', value: 'v0a'),
              new Tag(key: 'k1', value: 'v1a'),
              new Tag(key: 'k2', value: 'v2a'),
              new Tag(key: 'k3', value: 'v3a'),
              new Tag(key: 'k4', value: 'v4a'),
              new Tag(key: 'k5', value: 'v5a'),
              new Tag(key: 'k6', value: 'v6a'),
              new Tag(key: 'k7', value: 'v7a'),
              new Tag(key: 'k8', value: 'v8a'),
              new Tag(key: 'k9', value: 'v9a'),
          ]
      ) )

      N4j.print( "Listing hosted zone to verify tags ${hostedZoneId}" )
      listTagsForResource( new ListTagsForResourceRequest(
          resourceType: 'hostedzone',
          resourceId: hostedZoneId,
      )).with {
        N4j.print("Tags: ${resourceTagSet}")
        Assert.assertNotNull('ResourceTagSet', resourceTagSet)
        Assert.assertEquals('Resource type', 'hostedzone', resourceTagSet.resourceType)
        Assert.assertEquals('Resource id', hostedZoneId, resourceTagSet.resourceId)
        Assert.assertNotNull('ResourceTagSet', resourceTagSet.tags)
        Assert.assertEquals('resource tag count', 10, resourceTagSet.tags.size())
        (0..9).each { index ->
          Assert.assertEquals("resource tag key[${index}]", "k${index}" as String, resourceTagSet.tags.get(index).key)
          Assert.assertEquals("resource tag value[${index}]", "v${index}a" as String, resourceTagSet.tags.get(index).value)
        }
      }

      N4j.print( "Deleting hosted zone single tags ${hostedZoneId}" )
      changeTagsForResource( new ChangeTagsForResourceRequest(
          resourceType: 'hostedzone',
          resourceId: hostedZoneId,
          removeTagKeys: ['k4']
      ) )

      N4j.print( "Listing hosted zone to verify tag deleted ${hostedZoneId}" )
      listTagsForResource( new ListTagsForResourceRequest(
          resourceType: 'hostedzone',
          resourceId: hostedZoneId,
      )).with {
        Assert.assertNotNull('ResourceTagSet', resourceTagSet)
        Assert.assertEquals('Resource type', 'hostedzone', resourceTagSet.resourceType)
        Assert.assertEquals('Resource id', hostedZoneId, resourceTagSet.resourceId)
        Assert.assertNotNull('ResourceTagSet', resourceTagSet.tags)
        Assert.assertEquals('resource tag count', 9, resourceTagSet.tags.size())
      }

      N4j.print( "Deleting hosted zone all tags ${hostedZoneId}" )
      changeTagsForResource( new ChangeTagsForResourceRequest(
          resourceType: 'hostedzone',
          resourceId: hostedZoneId,
          removeTagKeys: ['k0', 'k1', 'k2', 'k3', 'k4', 'k5', 'k6', 'k7', 'k8', 'k9']
      ) )

      N4j.print( "Listing hosted zone to verify no tags ${hostedZoneId}" )
      listTagsForResource( new ListTagsForResourceRequest(
          resourceType: 'hostedzone',
          resourceId: hostedZoneId,
      )).with {
        Assert.assertNotNull('ResourceTagSet', resourceTagSet)
        Assert.assertEquals('Resource type', 'hostedzone', resourceTagSet.resourceType)
        Assert.assertEquals('Resource id', hostedZoneId, resourceTagSet.resourceId)
        if ( resourceTagSet.tags != null ) {
          Assert.assertEquals('resource tag count', 0, resourceTagSet.tags.size())
        }
      }

      N4j.print( "Deleting public hosted zone ${hostedZoneId}" )
      deleteHostedZone(new DeleteHostedZoneRequest(id: hostedZoneId))
    }
  }

  @Test
  void testListHostedZones() {
    N4j.print( "Testing create/list/delete for public hosted zone" )
    route53Client.with {
      String callerRef = "${N4j.NAME_PREFIX}testListHostedZones"
      String hostedZoneId = createHostedZone(new CreateHostedZoneRequest(
          callerReference: callerRef,
          name: 'example.com.'
      )).with {
        hostedZone.id
      }

      N4j.print( "Listing hosted zones" )
      listHostedZones().with {
        Assert.assertNotNull('HostedZones', hostedZones)
        Assert.assertEquals('HostedZones count', 1, hostedZones.size())
        Assert.assertEquals('HostedZones[0].id', hostedZoneId, hostedZones.get(0).id)
        Assert.assertEquals('HostedZones[0].name', 'example.com.', hostedZones.get(0).name)
        Assert.assertNotNull('HostedZones[0].config', hostedZones.get(0).config)
        Assert.assertNotNull('HostedZones[0].config.privatezone', hostedZones.get(0).config.privateZone)
        Assert.assertFalse('HostedZones[0].config.privatezone', hostedZones.get(0).config.privateZone)
      }

      N4j.print( "Deleting public hosted zone ${hostedZoneId}" )
      deleteHostedZone(new DeleteHostedZoneRequest(id: hostedZoneId))
    }
  }

  @Test
  void testListHostedZonesByName() {
    N4j.print( "Testing create/list by name/delete for public hosted zone" )
    route53Client.with {
      String callerRef = "${N4j.NAME_PREFIX}testListHostedZonesByName"
      String hostedZoneId = createHostedZone(new CreateHostedZoneRequest(
          callerReference: callerRef,
          name: 'example.com.'
      )).with {
        hostedZone.id
      }

      N4j.print( "Listing hosted zones by name" )
      listHostedZonesByName().with {
        Assert.assertNotNull('HostedZones', hostedZones)
        Assert.assertEquals('HostedZones count', 1, hostedZones.size())
        Assert.assertEquals('HostedZones[0].id', "/hostedzone/${hostedZoneId}" as String, hostedZones.get(0).id)
        Assert.assertEquals('HostedZones[0].name', 'example.com.', hostedZones.get(0).name)
        Assert.assertNotNull('HostedZones[0].config', hostedZones.get(0).config)
        Assert.assertNotNull('HostedZones[0].config.privatezone', hostedZones.get(0).config.privateZone)
        Assert.assertFalse('HostedZones[0].config.privatezone', hostedZones.get(0).config.privateZone)
      }

      N4j.print( "Deleting public hosted zone ${hostedZoneId}" )
      deleteHostedZone(new DeleteHostedZoneRequest(id: hostedZoneId))
    }
  }

  @Test
  void testResourceRecordSets() {
    N4j.print( "Testing create/change rrsets/delete for public hosted zone" )
    route53Client.with {
      String callerRef = "${N4j.NAME_PREFIX}testResourceRecordSets"
      String hostedZoneId = createHostedZone(new CreateHostedZoneRequest(
          callerReference: callerRef,
          name: 'example.com.'
      )).with {
        hostedZone.id
      }

      N4j.print( "Changing resource records for public hosted zone ${hostedZoneId}" )
      changeResourceRecordSets(new ChangeResourceRecordSetsRequest(
          hostedZoneId: hostedZoneId,
          changeBatch: new ChangeBatch(
              comment: 'Change resource records comment here',
              changes: [
                  new Change(
                    action: 'CREATE',
                    resourceRecordSet: new ResourceRecordSet(
                        name: 'name.example.com.',
                        type: 'A',
                        TTL: 600,
                        resourceRecords: [
                            new ResourceRecord(value: '1.1.1.1'),
                            new ResourceRecord(value: '2.2.2.2')
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

      N4j.print( "Changing resource record with repeated create for public hosted zone ${hostedZoneId} (should fail)" )
      try {
        changeResourceRecordSets(new ChangeResourceRecordSetsRequest(
            hostedZoneId: hostedZoneId,
            changeBatch: new ChangeBatch(
                comment: 'Change resource records comment here',
                changes: [
                    new Change(
                        action: 'CREATE',
                        resourceRecordSet: new ResourceRecordSet(
                            name: 'name.example.com.',
                            type: 'A',
                            TTL: 600,
                            resourceRecords: [
                                new ResourceRecord(value: '1.1.1.1'),
                                new ResourceRecord(value: '2.2.2.2')
                            ]
                        )
                    )
                ]
            )
        ))
        Assert.fail("Expected failure for duplicate CREATE action")
      } catch (Exception e) {
        N4j.print("Got expected exception for duplicate CREATE: ${e}")
      }

      N4j.print( "Changing resource record with upsert for public hosted zone ${hostedZoneId}" )
      changeResourceRecordSets(new ChangeResourceRecordSetsRequest(
          hostedZoneId: hostedZoneId,
          changeBatch: new ChangeBatch(
              comment: 'Change resource records upsert comment here',
              changes: [
                  new Change(
                      action: 'UPSERT',
                      resourceRecordSet: new ResourceRecordSet(
                          name: 'name.example.com.',
                          type: 'A',
                          TTL: 900,
                          resourceRecords: [
                              new ResourceRecord(value: '3.3.3.3')
                          ]
                      )
                  )
              ]
          )
      )).with {
        Assert.assertNotNull('ChangeInfo', changeInfo)
        Assert.assertEquals('ChangeInfo.comment',
            'Change resource records upsert comment here', changeInfo.comment)
      }

      N4j.print( "Deleting hosted zone with non-default resource records present (should fail)" )
      try {
        deleteHostedZone(new DeleteHostedZoneRequest(id: hostedZoneId))
        Assert.fail("Expected failure for non-empty hosted zone delete")
      } catch (Exception e) {
        N4j.print("Got expected exception for non-empty hosted zone delete: ${e}")
      }

      N4j.print( "Changing resource record with delete for public hosted zone ${hostedZoneId}" )
      changeResourceRecordSets(new ChangeResourceRecordSetsRequest(
          hostedZoneId: hostedZoneId,
          changeBatch: new ChangeBatch(
              comment: 'Change resource records delete comment here',
              changes: [
                  new Change(
                      action: 'DELETE',
                      resourceRecordSet: new ResourceRecordSet(
                          name: 'name.example.com.',
                          type: 'A',
                          TTL: 900,
                          resourceRecords: [
                              new ResourceRecord(value: '3.3.3.3')
                          ]
                      )
                  )
              ]
          )
      )).with {
        Assert.assertNotNull('ChangeInfo', changeInfo)
        Assert.assertEquals('ChangeInfo.comment',
            'Change resource records delete comment here', changeInfo.comment)
      }

      N4j.print( "Deleting public hosted zone ${hostedZoneId}" )
      deleteHostedZone(new DeleteHostedZoneRequest(id: hostedZoneId))
    }
  }

  @Test
  void testResourceRecordSetTypes() {
    N4j.print( "Testing create/create rrsets/delete for public hosted zone" )
    route53Client.with {
      String callerRef = "${N4j.NAME_PREFIX}testResourceRecordSetTypes"
      String hostedZoneId = createHostedZone(new CreateHostedZoneRequest(
          callerReference: callerRef,
          name: 'example.com.'
      )).with {
        hostedZone.id
      }

      N4j.print( "Changing resource records for public hosted zone ${hostedZoneId}" )
      changeResourceRecordSets(new ChangeResourceRecordSetsRequest(
          hostedZoneId: hostedZoneId,
          changeBatch: new ChangeBatch(
              comment: 'Change resource records comment here',
              changes: [
                  new Change(
                      action: 'UPSERT',
                      resourceRecordSet: new ResourceRecordSet(
                          name: 'example.com.',
                          type: 'SOA',
                          TTL: 3600,
                          resourceRecords: [
                              new ResourceRecord(value: 'ns1.example.com. root.example.com. 1 7200 900 1209600 86400')
                          ]
                      )
                  ),
                  new Change(
                      action: 'UPSERT',
                      resourceRecordSet: new ResourceRecordSet(
                          name: 'example.com.',
                          type: 'NS',
                          TTL: 600,
                          resourceRecords: [
                              new ResourceRecord(value: 'ns1.example.com')
                          ]
                      )
                  ),
                  new Change(
                      action: 'CREATE',
                      resourceRecordSet: new ResourceRecordSet(
                          name: 'ns1.example.com.',
                          type: 'A',
                          TTL: 600,
                          resourceRecords: [
                              new ResourceRecord(value: '1.1.1.1')
                          ]
                      )
                  ),
                  new Change(
                      action: 'CREATE',
                      resourceRecordSet: new ResourceRecordSet(
                          name: 'cname.example.com.',
                          type: 'CNAME',
                          TTL: 600,
                          resourceRecords: [
                              new ResourceRecord(value: 'ns1.example.com')
                          ]
                      )
                  ),
                  new Change(
                      action: 'CREATE',
                      resourceRecordSet: new ResourceRecordSet(
                          name: 'ns2.example.com.',
                          type: 'A',
                          aliasTarget: new AliasTarget(
                              DNSName: 'ns1.example.com',
                              hostedZoneId: hostedZoneId,
                              evaluateTargetHealth: false
                          )
                      )
                  ),
                  new Change(
                      action: 'CREATE',
                      resourceRecordSet: new ResourceRecordSet(
                          name: 'txt.example.com.',
                          type: 'TXT',
                          TTL: 600,
                          resourceRecords: [
                              new ResourceRecord(value: '"textcontent"')
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

      N4j.print( "Changing resource record with delete for public hosted zone ${hostedZoneId}" )
      changeResourceRecordSets(new ChangeResourceRecordSetsRequest(
          hostedZoneId: hostedZoneId,
          changeBatch: new ChangeBatch(
              comment: 'Change resource records delete comment here',
              changes: [
                  new Change(
                      action: 'DELETE',
                      resourceRecordSet: new ResourceRecordSet(
                          name: 'example.com.',
                          type: 'SOA',
                          TTL: 3600,
                          resourceRecords: [
                              new ResourceRecord(value: 'ns1.example.com. root.example.com. 1 7200 900 1209600 86400')
                          ]
                      )
                  ),
                  new Change(
                      action: 'DELETE',
                      resourceRecordSet: new ResourceRecordSet(
                          name: 'example.com.',
                          type: 'NS',
                          TTL: 600,
                          resourceRecords: [
                              new ResourceRecord(value: 'ns1.example.com')
                          ]
                      )
                  ),
                  new Change(
                      action: 'DELETE',
                      resourceRecordSet: new ResourceRecordSet(
                          name: 'ns1.example.com.',
                          type: 'A',
                          TTL: 600,
                          resourceRecords: [
                              new ResourceRecord(value: '1.1.1.1')
                          ]
                      )
                  ),
                  new Change(
                      action: 'DELETE',
                      resourceRecordSet: new ResourceRecordSet(
                          name: 'cname.example.com.',
                          type: 'CNAME',
                          TTL: 600,
                          resourceRecords: [
                              new ResourceRecord(value: 'ns1.example.com')
                          ]
                      )
                  ),
                  new Change(
                      action: 'DELETE',
                      resourceRecordSet: new ResourceRecordSet(
                          name: 'ns2.example.com.',
                          type: 'A',
                          aliasTarget: new AliasTarget(
                              DNSName: 'ns1.example.com',
                              hostedZoneId: hostedZoneId,
                              evaluateTargetHealth: false
                          )
                      )
                  ),
                  new Change(
                      action: 'DELETE',
                      resourceRecordSet: new ResourceRecordSet(
                          name: 'txt.example.com.',
                          type: 'TXT',
                          TTL: 600,
                          resourceRecords: [
                              new ResourceRecord(value: '"textcontent"')
                          ]
                      )
                  )
              ]
          )
      )).with {
        Assert.assertNotNull('ChangeInfo', changeInfo)
        Assert.assertEquals('ChangeInfo.comment',
            'Change resource records delete comment here', changeInfo.comment)
      }

      N4j.print( "Deleting public hosted zone ${hostedZoneId}" )
      deleteHostedZone(new DeleteHostedZoneRequest(id: hostedZoneId))
    }
  }
}
