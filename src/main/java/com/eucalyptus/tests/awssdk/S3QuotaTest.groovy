package com.eucalyptus.tests.awssdk

import com.amazonaws.AmazonServiceException
import com.amazonaws.services.s3.AmazonS3
import com.github.sjones4.youcan.youare.model.PutAccountPolicyRequest
import org.hamcrest.CoreMatchers
import org.junit.After
import org.junit.AfterClass
import org.junit.Assert
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test

/**
 *
 */
class S3QuotaTest {

  private static List<Runnable> cleanupTasks = null
  private static AmazonS3 s3 = null
  private static String account = null

  @BeforeClass
  static void init() throws Exception {
    N4j.print("### PRE SUITE SETUP - ${S3QuotaTest.simpleName}")
    try {
      N4j.getCloudInfo()
      account = S3QuotaTest.simpleName.toLowerCase()
      s3 = N4j.initS3ClientWithNewAccount(account, "admin")
      N4j.youAre.putAccountPolicy( new PutAccountPolicyRequest(
          accountName: account,
          policyName: 's3-quota',
          policyDocument: '''\
            {
              "Statement": [
                {
                  "Action": "*",
                  "Resource": "*",
                  "Effect": "Limit",
                  "Condition": {
                    "NumericLessThanEquals": {
                      "s3:quota-bucketnumber": "1"
                    }
                  }
                },
                {
                  "Action": "*",
                  "Resource": "*",
                  "Effect": "Limit",
                  "Condition": {
                    "NumericLessThanEquals": {
                      "s3:quota-buckettotalsize": "1"
                    }
                  }
                }
              ]
            }
          '''.stripIndent()
      ) )
    } catch (Exception e) {
      try {
        teardown()
      } catch (Exception ignore) {}
      throw e
    }
  }

  @AfterClass
  static void teardown() throws Exception {
    N4j.print("### POST SUITE CLEANUP - ${S3QuotaTest.simpleName}")
    N4j.deleteAccount(account)
    s3 = null
  }

  @Before
  void setup() throws Exception {
    N4j.print("Initializing bucket name and clean up tasks")
    cleanupTasks = new ArrayList<Runnable>()
  }

  @After
  void cleanup() throws Exception {
    Collections.reverse(cleanupTasks)
    for (final Runnable cleanupTask : cleanupTasks) {
      try {
        cleanupTask.run()
      } catch (Exception e) {
        N4j.print("Unable to run clean up task: " + e)
      }
    }
  }

  @Test
  void testBucketCreatePermitted( ) {
    N4j.testInfo("${S3QuotaTest.simpleName} - testBucketCreatePermitted")

    String bucketName = "bucketquota-${N4j.eucaUUID()}"

    cleanupTasks.add{
      N4j.print( "Deleting bucket ${bucketName}" )
      s3.deleteBucket(bucketName)
    }

    N4j.print( "Creating bucket ${bucketName}, should be within quota" )
    s3.createBucket(bucketName)
  }

  @Test
  void testBucketCreateDenied( ) {
    N4j.testInfo("${S3QuotaTest.simpleName} - testBucketCreateDenied")

    String bucketName1 = "bucketquota-${N4j.eucaUUID()}"
    String bucketName2 = "bucketquota-${N4j.eucaUUID()}"

    cleanupTasks.add{
      N4j.print( "Deleting bucket ${bucketName1}" )
      s3.deleteBucket(bucketName1)
    }

    cleanupTasks.add{
      N4j.print( "Deleting bucket ${bucketName2}" )
      s3.deleteBucket(bucketName2)
    }

    N4j.print( "Creating bucket ${bucketName1}, should be within quota" )
    s3.createBucket(bucketName1)

    N4j.print( "Creating bucket ${bucketName2}, should exceed quota" )
    try {
      s3.createBucket(bucketName2)
      Assert.fail("Expected bucket creation failure due to quota exceeded")
    } catch ( AmazonServiceException e ) {
      N4j.print(e.toString())
      Assert.assertEquals('Error code for quota exceeded','AccessDenied', e.errorCode )
      Assert.assertEquals('HTTP status code for quota exceeded',403, e.statusCode )
      if ( N4j.isAtLeastEucalyptusVersion("4.4.4") ) {
        N4j.print( "Detected v4.4.4 or higher (${N4j.EUCALYPTUS_VERSION}), testing quota exceeded error message" )
        Assert.assertThat('Error message for quota exceeded mentions limit',
            e.errorMessage, CoreMatchers.containsString("limit") )
      }
    }
  }

  @Test
  void testSmallObjectPutPermitted( ) {
    N4j.testInfo("${S3QuotaTest.simpleName} - testSmallObjectPutPermitted")

    String bucketName = "bucketquota-${N4j.eucaUUID()}"
    String objectName = 'foo.txt'

    cleanupTasks.add{
      N4j.print( "Deleting bucket ${bucketName}" )
      s3.deleteBucket(bucketName)
    }
    cleanupTasks.add{
      N4j.print( "Deleting object ${bucketName}/${objectName}" )
      s3.deleteObject(bucketName, objectName)
    }

    N4j.print( "Creating bucket ${bucketName}, should be within quota" )
    s3.createBucket(bucketName)

    N4j.print( "Creating object ${bucketName}/${objectName}, should be within quota" )
    s3.putObject(bucketName, objectName, 'foo content')
  }

  @Test
  void testLargeObjectPutDenied( ) {
    N4j.testInfo("${S3QuotaTest.simpleName} - testLargeObjectPutDenied")

    String bucketName = "bucketquota-${N4j.eucaUUID()}"
    String objectName = 'bigfoo.txt'

    cleanupTasks.add{
      N4j.print( "Deleting bucket ${bucketName}" )
      s3.deleteBucket(bucketName)
    }
    cleanupTasks.add{
      N4j.print( "Deleting object ${bucketName}/${objectName}" )
      s3.deleteObject(bucketName, objectName)
    }

    N4j.print( "Creating bucket ${bucketName}, should be within quota" )
    s3.createBucket(bucketName)

    N4j.print( "Creating object ${bucketName}/${objectName}, should exceed quota" )
    try {
      s3.putObject(bucketName, objectName, 'foo content foo content foo content foo content foo content\n' * 50000)
      Assert.fail("Expected object creation failure due to quota exceeded")
    } catch ( AmazonServiceException e ) {
      N4j.print(e.toString())
      Assert.assertEquals('Error code for quota exceeded','AccessDenied', e.errorCode )
      Assert.assertEquals('HTTP status code for quota exceeded',403, e.statusCode )
      if ( N4j.isAtLeastEucalyptusVersion("4.4.4") ) {
        N4j.print( "Detected v4.4.4 or higher (${N4j.EUCALYPTUS_VERSION}), testing quota exceeded error message" )
        Assert.assertThat('Error message for quota exceeded mentions limit',
            e.errorMessage, CoreMatchers.containsString("limit") )
      }
    }
  }

}
