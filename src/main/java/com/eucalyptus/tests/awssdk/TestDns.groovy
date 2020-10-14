package com.eucalyptus.tests.awssdk

import com.amazonaws.auth.AWSCredentialsProvider
import com.amazonaws.auth.AWSStaticCredentialsProvider
import com.amazonaws.auth.BasicAWSCredentials
import com.github.sjones4.youcan.youserv.YouServ
import com.github.sjones4.youcan.youserv.YouServClient
import com.github.sjones4.youcan.youserv.model.DescribeServicesRequest
import com.github.sjones4.youcan.youserv.model.Filter
import org.junit.Before

import javax.naming.Context
import javax.naming.directory.Attributes
import javax.naming.directory.DirContext
import javax.naming.directory.InitialDirContext
import org.junit.Assert
import org.junit.Test

import static com.eucalyptus.tests.awssdk.N4j.initEndpoints
import static com.eucalyptus.tests.awssdk.N4j.ACCESS_KEY
import static com.eucalyptus.tests.awssdk.N4j.SECRET_KEY
import static com.eucalyptus.tests.awssdk.N4j.SERVICES_ENDPOINT
import static com.eucalyptus.tests.awssdk.N4j.S3_ENDPOINT

/**
 * Basic DNS functionality test.
 */
class TestDns {

  private AWSCredentialsProvider credentials

  @Before
  void init( ) {
    initEndpoints( )
    this.credentials = new AWSStaticCredentialsProvider( new BasicAWSCredentials( ACCESS_KEY, SECRET_KEY ) )
  }

  private YouServ getServicesClient( final AWSCredentialsProvider credentials ) {
    YouServClient youServ = new YouServClient( credentials )
    youServ.setEndpoint( SERVICES_ENDPOINT )
    youServ
  }

  private Set<String> getDnsHosts( final YouServ youServ ) {
    youServ.describeServices( new DescribeServicesRequest(
        filters: [
            new Filter(
                name: 'service-type',
                values: [ 'dns' ]
            )
        ]
    ) ).with{
      serviceStatuses.collect{ serviceStatus ->
        URI.create( serviceStatus.serviceId.uri ).host
      } as Set<String>
    }
  }

  private List<String> lookup( String name, String type, Set<String> dnsServers ) {
    final Hashtable<String,String> env = new Hashtable<>()
    env.put( Context.INITIAL_CONTEXT_FACTORY, 'com.sun.jndi.dns.DnsContextFactory' )
    env.put( Context.PROVIDER_URL, dnsServers.collect{ ip -> "dns://${ip}/" }.join( ' ' ) )
    env.put( Context.AUTHORITATIVE, 'true' )
    final DirContext ictx = new InitialDirContext( env )
    try {
      final Attributes attrs = ictx.getAttributes( name, [type.toUpperCase()] as String[] )
      final List<String> values = Collections.list(attrs.get(type.toLowerCase())?.getAll( ))
      return values
    } finally {
      ictx.close()
    }
  }

  private String domain() {
    String domain = S3_ENDPOINT.replace("s3.","").replace("https://", "").replace("http://", "")
    if ( domain.contains(':') ) {
      domain = domain.substring(0, domain.indexOf(':'))
    }
    domain
  }

  @Test
  void testNameserverRecords( ) throws Exception {
    Set<String> dnsHosts = getDnsHosts(getServicesClient(credentials))
    String domain = domain()
    N4j.print( "Getting nameservers for ${domain} using servers ${dnsHosts}" )

    List<String> nameservers = lookup(domain, 'NS', dnsHosts)
    N4j.print( "Got nameservers ${nameservers} for ${domain}" )

    nameservers.forEach({ nameserver ->
      N4j.print( "Getting ips for nameserver ${nameserver}" )
      List<String> ips = lookup(nameserver, 'A', dnsHosts)
      N4j.print( "Got ips ${ips} for nameserver ${nameserver}" )
      Assert.assertNotNull('Expected ip for nameserver', ips)
      Assert.assertFalse('Expected ip for nameserver', ips.isEmpty())
    })

    N4j.print( "Test complete" )
  }
}
