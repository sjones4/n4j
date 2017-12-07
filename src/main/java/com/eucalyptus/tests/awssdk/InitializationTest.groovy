package com.eucalyptus.tests.awssdk

import com.amazonaws.auth.AWSCredentialsProvider
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.internal.StaticCredentialsProvider
import com.github.sjones4.youcan.youprop.YouProp
import com.github.sjones4.youcan.youprop.YouPropClient
import com.github.sjones4.youcan.youprop.model.ModifyPropertyValueRequest
import com.github.sjones4.youcan.youtwo.YouTwo
import com.github.sjones4.youcan.youtwo.YouTwoClient
import com.github.sjones4.youcan.youtwo.model.ModifyInstanceTypeAttributeRequest
import org.junit.Test

import static com.eucalyptus.tests.awssdk.N4j.ACCESS_KEY
import static com.eucalyptus.tests.awssdk.N4j.EC2_ENDPOINT
import static com.eucalyptus.tests.awssdk.N4j.SECRET_KEY
import static com.eucalyptus.tests.awssdk.N4j.initEndpoints

/**
 * Configures cloud properties for test runs
 */
class InitializationTest {

  private AWSCredentialsProvider credentials

  InitializationTest() {
    initEndpoints( )
    this.credentials = new StaticCredentialsProvider( new BasicAWSCredentials( ACCESS_KEY, SECRET_KEY ) )
  }

  YouProp getPropertiesClient(AWSCredentialsProvider clientCredentials = credentials ) {
    YouPropClient youProp = new YouPropClient( clientCredentials )
    youProp.setEndpoint( deriveEndpoint( EC2_ENDPOINT, '/services/Properties' ) )
    youProp
  }

  YouTwo getComputeClient(AWSCredentialsProvider clientCredentials = credentials ) {
    YouTwoClient youTwo = new YouTwoClient( clientCredentials )
    youTwo.setEndpoint( EC2_ENDPOINT )
    youTwo
  }

  String deriveEndpoint( String baseUri, String servicePath ) {
    final URI uri = URI.create( baseUri )
    final String host = uri.getHost()
    final int port = uri.getPort( )
    final String endpoint = uri.getScheme( ) + "://" +
        InetAddress.getByName( host ).getHostAddress( ) +
        (port > 0 ? ":" + port : "")
    return "${endpoint}${servicePath}"
  }

  @Test
  void configureCloudProperties( ) {
    Map<String,String> props = [
        'authentication.access_keys_limit': '100',
    ]
    getPropertiesClient( ).with{
      props.forEach{ key, value ->
        N4j.print( "Setting cloud property ${key} to ${value}" )
        modifyPropertyValue( new ModifyPropertyValueRequest(
            name: key,
            value: value
        ) )
      }
    }
  }

  /**
   * Increase m1.small disk size to 10 to allow running default image
   */
  @Test
  void configureVmTypes( ) {
    getComputeClient( ).with{
      modifyInstanceTypeAttribute( new ModifyInstanceTypeAttributeRequest(
          name: 'm1.small',
          disk: 11
      ) ).with {
        N4j.print( "Modified m1.small disk, now ${instanceType}" )
      }
    }
  }
}
