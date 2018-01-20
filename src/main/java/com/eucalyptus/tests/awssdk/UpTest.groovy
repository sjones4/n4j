package com.eucalyptus.tests.awssdk

import com.amazonaws.auth.BasicAWSCredentials
import com.github.sjones4.youcan.youserv.YouServ
import com.github.sjones4.youcan.youserv.YouServClient
import org.junit.Assert
import org.junit.Test

import java.util.concurrent.TimeUnit

import static com.eucalyptus.tests.awssdk.N4j.ACCESS_KEY
import static com.eucalyptus.tests.awssdk.N4j.SECRET_KEY
import static com.eucalyptus.tests.awssdk.N4j.SERVICES_ENDPOINT
import static com.eucalyptus.tests.awssdk.N4j.testInfo

/**
 * Wait 5 minutes for all services on a cloud to be up.
 */
class UpTest {

  private static final long TIMEOUT = TimeUnit.MINUTES.toMillis( 5 )
  private static final int RETRY_SECS = 15

  private YouServ bootstrapClient( ) {
    YouServClient youServ =
        new YouServClient( new BasicAWSCredentials( ACCESS_KEY, SECRET_KEY ) )
    youServ.setEndpoint( SERVICES_ENDPOINT )
    youServ
  }

  @Test
  void testUp() {
    testInfo("${this.getClass().simpleName}.testUp");

    long startTime = System.currentTimeMillis( )
    N4j.print( "Initializing endpoints" )
    boolean endpointsInitialized = false
    while ( (System.currentTimeMillis() - startTime) < TIMEOUT ) {
      try {
        N4j.initEndpoints( )
        N4j.print( "Endpoints initialized" )
        endpointsInitialized = true
        break
      } catch ( AssertionError e ) {
        N4j.print( "Could not initialize endpoints, will retry in ${RETRY_SECS}: ${e.message}" )
        N4j.sleep( RETRY_SECS )
      }
    }
    Assert.assertTrue( 'Endpoints initialized', endpointsInitialized )

    N4j.print( "Checking services" )
    bootstrapClient( ).with {
      while ( (System.currentTimeMillis() - startTime) < TIMEOUT ) {
        try {
          boolean servicesUp = true
          describeServices( ).with {
            Assert.assertNotNull( 'service statuses', serviceStatuses)
            Assert.assertNotEquals( 'services statuses size', 0, serviceStatuses.size( ) )
            N4j.print( "ENABLED types ${serviceStatuses.findAll{'ENABLED'==it.localState}.collect{it.serviceId.type}}" )
            N4j.print( "NOT ENABLED types ${serviceStatuses.findAll{'ENABLED'!=it.localState}.collect{it.serviceId.type}}" )
            servicesUp = serviceStatuses.findAll{'ENABLED'!=it.localState}.isEmpty( )
          }
          if ( servicesUp ) {
            N4j.print( "All services up, test complete" )
            return
          } else {
            N4j.print( "Not all services up, will retry in ${RETRY_SECS}" )
            N4j.sleep( RETRY_SECS )
          }
        } catch ( e ) {
          N4j.print( "Could not check services, will retry in ${RETRY_SECS}: ${e.message}" )
          N4j.sleep( RETRY_SECS )
        }
      }
      Assert.fail( 'Cloud not up after timeout' )
    }
  }
}