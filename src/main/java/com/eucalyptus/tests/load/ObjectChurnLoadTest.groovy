package com.eucalyptus.tests.load

import com.amazonaws.ClientConfiguration
import com.amazonaws.auth.AWSCredentialsProvider
import com.amazonaws.auth.AWSStaticCredentialsProvider
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.model.ObjectMetadata
import com.amazonaws.services.s3.model.PutObjectRequest
import com.eucalyptus.tests.awssdk.N4j
import com.google.common.collect.Iterators
import com.google.common.io.ByteSource
import com.google.common.io.ByteStreams
import org.junit.AfterClass
import org.junit.Assert
import org.junit.BeforeClass
import org.junit.Test

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

import static com.eucalyptus.tests.awssdk.N4j.ACCESS_KEY
import static com.eucalyptus.tests.awssdk.N4j.SECRET_KEY

/**
 *
 */
class ObjectChurnLoadTest {
  private static String testAcct
  private static AWSCredentialsProvider testAcctAdminCredentials
  private static AWSCredentialsProvider cloudAdminCredentials
  private static AmazonS3 s3Client

  @BeforeClass
  static void init( ){
    N4j.testInfo( ObjectChurnLoadTest.simpleName )
    N4j.getCloudInfo( )
    testAcct = "${N4j.NAME_PREFIX}object-churn-load"
    N4j.createAccount( testAcct )
    testAcctAdminCredentials = new AWSStaticCredentialsProvider( N4j.getUserCreds( testAcct, 'admin' ) )
    s3Client = getS3Client( testAcctAdminCredentials )
    cloudAdminCredentials = new AWSStaticCredentialsProvider( new BasicAWSCredentials(ACCESS_KEY, SECRET_KEY) )
  }

  @AfterClass
  static void cleanup( ) {
    if ( s3Client ) s3Client.shutdown( )
    N4j.deleteAccount( testAcct )
  }

  private static AmazonS3 getS3Client(final AWSCredentialsProvider credentials ) {
    final AmazonS3Client s3 = new AmazonS3Client( credentials, new ClientConfiguration(
        socketTimeout: TimeUnit.MINUTES.toMillis( 2 )
    )  )
    s3.setEndpoint( N4j.S3_ENDPOINT )
    s3
  }

  @Test
  void test( ) {
    final String namePrefix = UUID.randomUUID().toString().substring(0, 13) + "-"
    N4j.print( "Using resource prefix for test: " + namePrefix )

    final long startTime = System.currentTimeMillis( )
    final List<Runnable> cleanupTasks = [] as List<Runnable>
    try {
      String bucketName = "${namePrefix}bucket"
      N4j.print( "Creating bucket ${bucketName}" )
      s3Client.with {
        createBucket(bucketName)
        cleanupTasks.add {
          N4j.print("Deleting bucket ${bucketName}")
          deleteBucket(bucketName)
        }
      }

      final byte[] data = new byte[ 1024 * 1024 /*    1MiB */ ]
      final ByteSource byteSource = ByteSource.wrap( data )
      final List<List<Number>> churnOpts = [
          // object size,                #obj, #threads up, #down
          [ 1024 * 1024 * 10    /*    10MiB */, 75, 15, 30 ],
          [ 1024 * 1024 * 100   /*   100MiB */, 25, 10, 20 ],
          [ 1024 * 1024 * 1000  /*  1000MiB */, 10,  5,  5 ],
          [ 1024 * 1024 * 10000 /* 10000MiB */,  5,  5,  5 ],
      ]
      churnOpts.each { long size, int objects, int uploadThreads, int downloadThreads ->
        final int threads = uploadThreads
        final int iterations = ( objects / threads ) as Integer

        N4j.print("Churning ${iterations} object put/get/delete(s) on ${threads} threads")
        final CountDownLatch latch = new CountDownLatch(threads)
        final AtomicInteger successCount = new AtomicInteger(0)
        (1..threads).each { Integer thread ->
          Thread.start {
            try {
              getS3Client( testAcctAdminCredentials ).with {
                for (int i = 0; i < iterations; i++) {
                  String key = "${namePrefix}object-${thread}-${i}"
                  N4j.print("[${thread}] putting object ${key} length ${size} ${i+1}/${iterations}")
                  putObject(new PutObjectRequest(
                      bucketName,
                      key,
                      ByteSource.concat( Iterators.limit( Iterators.cycle( byteSource ), (size / data.length) as Integer ) ).openStream( ),
                      new ObjectMetadata(contentLength: size)
                  ))

                  N4j.print("[${thread}] getting object ${key} ${i+1}/${iterations}")
                  ByteStreams.copy(getObject(bucketName, key).getObjectContent(), ByteStreams.nullOutputStream())

                  N4j.print("[${thread}] deleting object ${key} ${i+1}/${iterations}")
                  deleteObject(bucketName, key)
                }
                shutdown()
                successCount.incrementAndGet()
              }
            } finally {
              latch.countDown()
            }
          }
        }
        latch.await()
        Assert.assertEquals( "Threads completed", threads, successCount.get( ) )

        int downloadCount = ( (objects * 10) / downloadThreads ) as Integer
        N4j.print("Performing ${downloadCount} object gets on ${downloadThreads} threads")
        final String key = "${namePrefix}object"
        N4j.print("Putting object ${key} length ${size}")
        getS3Client( testAcctAdminCredentials ).with {
          putObject(new PutObjectRequest(
              bucketName,
              key,
              ByteSource.concat( Iterators.limit( Iterators.cycle( byteSource ), (size / data.length) as Integer ) ).openStream( ),
              new ObjectMetadata(contentLength: size)
          ))
        }
        final CountDownLatch downLatch = new CountDownLatch(downloadThreads)
        final AtomicInteger downSuccessCount = new AtomicInteger(0)
        (1..downloadThreads).each { Integer thread ->
          Thread.start {
            try {
              getS3Client( testAcctAdminCredentials ).with {
                for (int i = 0; i < downloadCount; i++) {
                  N4j.print("[${thread}] getting object ${key} ${i+1}/${downloadCount}")
                  try {
                    ByteStreams.copy(getObject(bucketName, key).getObjectContent(), ByteStreams.nullOutputStream())
                  } catch( e ) {
                    N4j.print( "[${thread}] Error getting object  ${key} ${i+1}/${downloadCount}: ${e}" )
                    Assert.fail( "error" )
                  }
                }
                downSuccessCount.incrementAndGet()
              }
            } finally {
              downLatch.countDown()
            }
          }
        }
        downLatch.await()
        getS3Client( testAcctAdminCredentials ).with {
          N4j.print( "Deleting object ${key}" )
          deleteObject( bucketName, key )
        }
        Assert.assertEquals( "Download threads completed", downloadThreads, downSuccessCount.get( ) )
      }

      N4j.print( "Test complete in ${System.currentTimeMillis()-startTime}ms" )
    } finally {
      // Attempt to clean up anything we created
      N4j.print( "Running cleanup tasks" )
      final long cleanupStart = System.currentTimeMillis( )
      cleanupTasks.reverseEach { Runnable cleanupTask ->
        try {
          cleanupTask.run()
        } catch ( Exception e ) {
          e.printStackTrace( )
        }
      }
      N4j.print( "Completed cleanup tasks in ${System.currentTimeMillis()-cleanupStart}ms" )
    }
  }
}
