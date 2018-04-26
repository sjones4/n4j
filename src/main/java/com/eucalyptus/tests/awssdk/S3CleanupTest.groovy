package com.eucalyptus.tests.awssdk

import com.amazonaws.services.s3.model.Bucket
import com.amazonaws.services.s3.model.ObjectListing
import com.amazonaws.services.s3.model.S3ObjectSummary
import com.github.sjones4.youcan.youare.model.Account
import org.junit.Test

import java.util.concurrent.BlockingQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Test that cleans up all s3 resources for non-system accounts
 */
class S3CleanupTest {

  private static final int CLEANUP_THREADS = 20

  @Test
  void cleanResources( ) {
    N4j.testInfo( getClass( ).simpleName )
    N4j.initEndpoints( )

    List<String> accountNames = N4j.getYouAreClient( N4j.ACCESS_KEY, N4j.SECRET_KEY, N4j.IAM_ENDPOINT ).with {
      listAccounts( ).with {
        accounts.collect{ Account account -> account.accountName }
      }
    }

    List<String> nonSystemAccounts = accountNames.findAll{ String name ->
      name != 'eucalyptus' && !name.startsWith('(eucalyptus)')
    }

    SynchronousQueue<Runnable> listingsQueue = new SynchronousQueue<Runnable>()
    AtomicBoolean listingsCompleted = new AtomicBoolean(false)
    CountDownLatch listingsLatch = new CountDownLatch(CLEANUP_THREADS)
    try {
      ( 1..CLEANUP_THREADS ).each {
        Thread.start( "${S3CleanupTest.simpleName}-cleanup-${it}" ) {
          N4j.print( "Cleanup thread started: ${Thread.currentThread().name}" )
          try {
            while (!listingsCompleted.get()) {
              Runnable runnable = listingsQueue.poll( 2, TimeUnit.SECONDS )
              if ( runnable ) {
                runnable.run( )
              }
            }
          } finally {
            N4j.print( "Cleanup thread exiting: ${Thread.currentThread().name}" )
            listingsLatch.countDown()
          }
        }
      }

      N4j.print( "Accounts : ${nonSystemAccounts}" )
      nonSystemAccounts.each { accountName ->
        N4j.print( "Cleaning account: ${accountName}" )
        N4j.getS3Client( N4j.getUserCreds( accountName, 'admin' ), N4j.S3_ENDPOINT ).with {
          listBuckets( ).each { Bucket bucket ->
            listObjects( bucket.name ).with { ObjectListing listing ->
              def listingCleaner = { ObjectListing objectListing ->
                listingsQueue.put( {
                  objectListing.objectSummaries.each { S3ObjectSummary objectSummary ->
                    N4j.print( "Cleaning account ${accountName} bucket ${bucket.name} object ${objectSummary.key}" )
                    deleteObject( bucket.name, objectSummary.key )
                  }
                } )
              }
              listingCleaner( listing )
              while ( listing.truncated ) {
                listing = listNextBatchOfObjects( listing ).with { nextListing ->
                  listingCleaner( nextListing )
                  nextListing
                }
              }
            }
          }
        }
      }
    } finally {
      listingsCompleted.set(true)
    }

    N4j.print( "Waiting for object cleanup to complete before deleting buckets" )
    listingsLatch.await()
    nonSystemAccounts.each { accountName ->
      N4j.print( "Cleaning account: ${accountName}" )
      N4j.getS3Client( N4j.getUserCreds( accountName, 'admin' ), N4j.S3_ENDPOINT ).with {
        listBuckets( ).each { Bucket bucket ->
          N4j.print( "Cleaning account ${accountName} bucket ${bucket.name}" )
          deleteBucket( bucket.name )
        }
      }
    }
  }
}
