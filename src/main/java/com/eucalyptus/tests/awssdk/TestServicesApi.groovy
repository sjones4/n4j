package com.eucalyptus.tests.awssdk

import com.amazonaws.auth.BasicAWSCredentials
import com.github.sjones4.youcan.youserv.YouServ
import com.github.sjones4.youcan.youserv.YouServClient
import org.junit.Assert
import org.junit.BeforeClass
import org.junit.Test

import static com.eucalyptus.tests.awssdk.N4j.testInfo
import static com.eucalyptus.tests.awssdk.N4j.ACCESS_KEY
import static com.eucalyptus.tests.awssdk.N4j.SECRET_KEY
import static com.eucalyptus.tests.awssdk.N4j.SERVICES_ENDPOINT

/**
 *
 */
class TestServicesApi {

  @BeforeClass
  static void init() {
    N4j.initEndpoints( )
  }

  private YouServ bootstrapClient( ) {
    YouServClient youServ =
        new YouServClient( new BasicAWSCredentials( ACCESS_KEY, SECRET_KEY ) )
    youServ.setEndpoint( SERVICES_ENDPOINT )
    youServ
  }

  @Test
  void testDescribeServices( ) {
    testInfo( "${this.getClass().simpleName}.testDescribeServices" );

    bootstrapClient( ).with {
      describeServices( ).with {
        Assert.assertNotNull( 'service statuses', serviceStatuses)
        Assert.assertNotEquals( 'services statuses size', 0, serviceStatuses.size( ) )
        serviceStatuses.each { status ->
          N4j.print( String.valueOf( status ) )
        }
        N4j.print( "ENABLED types ${serviceStatuses.findAll{'ENABLED'==it.localState}.collect{it.serviceId.type}}" )
        N4j.print( "NOT ENABLED types ${serviceStatuses.findAll{'ENABLED'!=it.localState}.collect{it.serviceId.type}}" )
      }
    }
  }
  
  @Test
  void testDescribeServiceCertificates( ) {
    testInfo( "${this.getClass().simpleName}.testDescribeServiceCertificates" );

    bootstrapClient( ).with {
      describeServiceCertificates( ).with {
        Assert.assertNotNull( 'service certificates', serviceCertificates)
        Assert.assertNotEquals( 'services certificates size', 0, serviceCertificates.size( ) )
        serviceCertificates.each { certificate ->
          N4j.print( String.valueOf( certificate ) )
        }
      }
    }
  }
}
