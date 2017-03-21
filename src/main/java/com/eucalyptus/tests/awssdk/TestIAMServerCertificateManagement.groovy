package com.eucalyptus.tests.awssdk

import com.amazonaws.services.identitymanagement.AmazonIdentityManagement
import com.amazonaws.services.identitymanagement.model.DeleteServerCertificateRequest
import com.amazonaws.services.identitymanagement.model.GetServerCertificateRequest
import com.amazonaws.services.identitymanagement.model.ListServerCertificatesRequest
import com.amazonaws.services.identitymanagement.model.UploadServerCertificateRequest
import org.testng.annotations.BeforeClass
import org.testng.annotations.Test

/**
 * Tests management of IAM server certificates.
 * <p/>
 * This test covers the issues:
 * <p/>
 * https://eucalyptus.atlassian.net/browse/EUCA-13285
 */
class TestIAMServerCertificateManagement {

  private static final String ALICE_PEM = '''\
    -----BEGIN CERTIFICATE-----
    MIIDDDCCAfSgAwIBAgIQM6YEf7FVYx/tZyEXgVComTANBgkqhkiG9w0BAQUFADAw
    MQ4wDAYDVQQKDAVPQVNJUzEeMBwGA1UEAwwVT0FTSVMgSW50ZXJvcCBUZXN0IENB
    MB4XDTA1MDMxOTAwMDAwMFoXDTE4MDMxOTIzNTk1OVowQjEOMAwGA1UECgwFT0FT
    SVMxIDAeBgNVBAsMF09BU0lTIEludGVyb3AgVGVzdCBDZXJ0MQ4wDAYDVQQDDAVB
    bGljZTCBnzANBgkqhkiG9w0BAQEFAAOBjQAwgYkCgYEAoqi99By1VYo0aHrkKCNT
    4DkIgPL/SgahbeKdGhrbu3K2XG7arfD9tqIBIKMfrX4Gp90NJa85AV1yiNsEyvq+
    mUnMpNcKnLXLOjkTmMCqDYbbkehJlXPnaWLzve+mW0pJdPxtf3rbD4PS/cBQIvtp
    jmrDAU8VsZKT8DN5Kyz+EZsCAwEAAaOBkzCBkDAJBgNVHRMEAjAAMDMGA1UdHwQs
    MCowKKImhiRodHRwOi8vaW50ZXJvcC5iYnRlc3QubmV0L2NybC9jYS5jcmwwDgYD
    VR0PAQH/BAQDAgSwMB0GA1UdDgQWBBQK4l0TUHZ1QV3V2QtlLNDm+PoxiDAfBgNV
    HSMEGDAWgBTAnSj8wes1oR3WqqqgHBpNwkkPDzANBgkqhkiG9w0BAQUFAAOCAQEA
    BTqpOpvW+6yrLXyUlP2xJbEkohXHI5OWwKWleOb9hlkhWntUalfcFOJAgUyH30TT
    pHldzx1+vK2LPzhoUFKYHE1IyQvokBN2JjFO64BQukCKnZhldLRPxGhfkTdxQgdf
    5rCK/wh3xVsZCNTfuMNmlAM6lOAg8QduDah3WFZpEA0s2nwQaCNQTNMjJC8tav1C
    Br6+E5FAmwPXP7pJxn9Fw9OXRyqbRA4v2y7YpbGkG2GI9UvOHw6SGvf4FRSthMMO
    35YbpikGsLix3vAsXWWi4rwfVOYzQK0OFPNi9RMCUdSH06m9uLWckiCxjos0FQOD
    ZE9l4ATGy9s9hNVwryOJTw==
    -----END CERTIFICATE-----
  '''.stripIndent( )

  private static final String ALICE_PK = '''\
    -----BEGIN PRIVATE KEY-----
    MIICdgIBADANBgkqhkiG9w0BAQEFAASCAmAwggJcAgEAAoGBAKKovfQctVWKNGh6
    5CgjU+A5CIDy/0oGoW3inRoa27tytlxu2q3w/baiASCjH61+BqfdDSWvOQFdcojb
    BMr6vplJzKTXCpy1yzo5E5jAqg2G25HoSZVz52li873vpltKSXT8bX962w+D0v3A
    UCL7aY5qwwFPFbGSk/AzeSss/hGbAgMBAAECgYBj59rMHfnusTVhWuHaGWDCHqWv
    dhEBKbNrJ74ws4B00I9blKbyIUvkKfshTa/+QqLZ5bbWh5ou0XOwxT1bYsk/qHfd
    xo9wyv/UXfCyIdEIFJmJEuCuhievInalZGoHyvr+PWYIrM2SjHCylRLW08UhPTIk
    Hgv9tAYwi+egzi/loQJBANfaEEpFuxBhKAvFmarH8okbv6tymNhcFzO1w7T2wCwn
    60l7sU8qgoHUIb8paqPwpCfr1uVfGEpENOms9kuJTtECQQDA6d9H53Atuc0ZO5p+
    +9tYTV3QIJrdgaigKusKrgB/sPmO/1NlcPHI4hmwfNAflTjt72G3Ym9iWVtOUyGK
    sqyrAkEApvIBp3BHPmPmlTQ/pdb/vwu3MuNvU+fmChiLRWuTNpOpZyxD9vbp+YAY
    mcFuuV1lmXrOupjSMJ6QTit4UvPgAQJADtgwQUUy4aHhgWaPveO9fi794A0SPadD
    hYen7Ht1OF4y5ekJzs2BHXcgiO8hyLxf1BdOiqD9dzDvELje5OBY3wJAXanha6U1
    rpTrizHIrWAFY6vqTDHo9ZTb0DLow8M8Ak87ziUOBvl2ULVIYT7EdTM883oThgUj
    8QJrA4c2sAMSwQ==
    -----END PRIVATE KEY-----
  '''.stripIndent( )

  @BeforeClass
  void init( ) {
    N4j.getCloudInfo( )
  }

  @Test
  void IAMServerCertificateManagementTest( ) throws Exception {
    final AmazonIdentityManagement iam = N4j.youAre

    final String namePrefix = UUID.randomUUID().toString().substring(0, 13) + "-";
    print( "Using resource prefix for test: " + namePrefix );

    final List<Runnable> cleanupTasks = [] as List<Runnable>
    try {
      iam.with {
        N4j.print( "Uploading server certificate" )
        String certName = "${namePrefix}cert-1"
        String certId = uploadServerCertificate( new UploadServerCertificateRequest(
            path: "/${namePrefix}/",
            serverCertificateName: certName,
            certificateBody: ALICE_PEM,
            privateKey: ALICE_PK
        ) ).with {
          N4j.assertThat( serverCertificateMetadata.expiration != null,
              "Expected expiration for certificate ${serverCertificateMetadata.serverCertificateName}" )
          serverCertificateMetadata.serverCertificateId
        }
        N4j.print( "Created server certificate ${certName}/${certId}" )
        cleanupTasks.add {
          N4j.print( "Deleting server certificate ${certName}/${certId}" )
          deleteServerCertificate( new DeleteServerCertificateRequest(
              serverCertificateName: certName,
          ) )
        }

        N4j.print( "Listing server certificates" )
        listServerCertificates( new ListServerCertificatesRequest(
            pathPrefix: "/${namePrefix}"
        ) ).with {
          N4j.print( serverCertificateMetadataList.toString() )
          N4j.assertThat( serverCertificateMetadataList?.size() == 1,
              "Expected 1 server certificate with path prefix but was: ${serverCertificateMetadataList?.size()}" )
          N4j.assertThat( serverCertificateMetadataList.getAt(0).expiration != null,
              "Expected expiration for certificate ${serverCertificateMetadataList.getAt(0).serverCertificateName}" )
        }

        N4j.print( "Getting server certificate" )
        getServerCertificate( new GetServerCertificateRequest(
            serverCertificateName: certName
        ) ).with {
          N4j.print( serverCertificate.toString( ) )
          N4j.assertThat( serverCertificate != null, 'Expected serverCertificate' )
          N4j.assertThat( serverCertificate.serverCertificateMetadata != null,
              'Expected serverCertificate.serverCertificateMetadata' )
          N4j.assertThat( serverCertificate.serverCertificateMetadata.expiration != null,
              "Expected expiration for certificate ${serverCertificate.serverCertificateMetadata.serverCertificateName}" )
        }
      }

      print( "Test complete" )
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
