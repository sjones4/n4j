package com.eucalyptus.tests.awssdk

import com.amazonaws.AmazonServiceException
import com.amazonaws.auth.AWSCredentialsProvider
import com.amazonaws.auth.AWSStaticCredentialsProvider
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.services.ec2.AmazonEC2
import com.amazonaws.services.ec2.model.AuthorizeSecurityGroupIngressRequest
import com.amazonaws.services.ec2.model.CreateSecurityGroupRequest
import com.amazonaws.services.ec2.model.DeleteSecurityGroupRequest
import com.amazonaws.services.ec2.model.DescribeAvailabilityZonesResult
import com.amazonaws.services.ec2.model.DescribeImagesRequest
import com.amazonaws.services.ec2.model.DescribeInstancesRequest
import com.amazonaws.services.ec2.model.Filter
import com.amazonaws.services.ec2.model.IpPermission
import com.amazonaws.services.ec2.model.IpRange
import com.amazonaws.services.ec2.model.Placement
import com.amazonaws.services.ec2.model.RunInstancesRequest
import com.amazonaws.services.ec2.model.TerminateInstancesRequest
import com.amazonaws.services.ec2.model.UserIdGroupPair
import com.amazonaws.services.elasticloadbalancing.AmazonElasticLoadBalancing
import com.amazonaws.services.elasticloadbalancing.AmazonElasticLoadBalancingClient
import com.amazonaws.services.elasticloadbalancing.model.*
import com.amazonaws.services.identitymanagement.AmazonIdentityManagement
import com.amazonaws.services.identitymanagement.AmazonIdentityManagementClient
import com.amazonaws.services.identitymanagement.model.DeleteServerCertificateRequest
import com.amazonaws.services.identitymanagement.model.UploadServerCertificateRequest
import com.amazonaws.services.route53.AmazonRoute53
import com.github.sjones4.youcan.youserv.YouServ
import com.github.sjones4.youcan.youserv.YouServClient
import com.github.sjones4.youcan.youserv.model.DescribeServicesRequest
import com.google.common.io.ByteStreams
import org.junit.Before

import javax.naming.Context
import javax.naming.directory.Attributes
import javax.naming.directory.DirContext
import javax.naming.directory.InitialDirContext
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.KeyManager
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLEngine
import javax.net.ssl.SSLSession
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.X509ExtendedTrustManager
import javax.net.ssl.X509TrustManager
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.nio.charset.StandardCharsets
import org.junit.Assert
import org.junit.Test

import static com.eucalyptus.tests.awssdk.N4j.initEndpoints
import static com.eucalyptus.tests.awssdk.N4j.ACCESS_KEY
import static com.eucalyptus.tests.awssdk.N4j.SECRET_KEY
import static com.eucalyptus.tests.awssdk.N4j.EC2_ENDPOINT
import static com.eucalyptus.tests.awssdk.N4j.ELB_ENDPOINT
import static com.eucalyptus.tests.awssdk.N4j.IAM_ENDPOINT
import static com.eucalyptus.tests.awssdk.N4j.SERVICES_ENDPOINT

/**
 * Basic ELB functionality test.
 *
 * Simple load balanced request test for cloud with any network mode.
 */
class TestELBEC2Instance {

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

  private AWSCredentialsProvider credentials

  @Before
  void init( ) {
    initEndpoints( )
    this.credentials = new AWSStaticCredentialsProvider( new BasicAWSCredentials( ACCESS_KEY, SECRET_KEY ) )
  }

  private AmazonElasticLoadBalancing getELBClient( final AWSCredentialsProvider credentials ) {
    final AmazonElasticLoadBalancing elb = new AmazonElasticLoadBalancingClient( credentials )
    elb.setEndpoint( ELB_ENDPOINT )
    elb
  }

  private AmazonIdentityManagement getIamClient( final AWSCredentialsProvider credentials ) {
    final AmazonIdentityManagement iam = new AmazonIdentityManagementClient( credentials )
    iam.setEndpoint( IAM_ENDPOINT )
    iam
  }

  private YouServ getServicesClient( final AWSCredentialsProvider credentials ) {
    YouServClient youServ = new YouServClient( credentials )
    youServ.setEndpoint( SERVICES_ENDPOINT )
    youServ
  }

  private Set<String> getDnsHosts( final YouServ youServ ) {
    youServ.describeServices( new DescribeServicesRequest(
      filters: [
          new com.github.sjones4.youcan.youserv.model.Filter(
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

  private String lookup( String name, Set<String> dnsServers ) {
    final Hashtable<String,String> env = new Hashtable<>()
    env.put( Context.INITIAL_CONTEXT_FACTORY, 'com.sun.jndi.dns.DnsContextFactory' )
    env.put( Context.PROVIDER_URL, dnsServers.collect{ ip -> "dns://${ip}/" }.join( ' ' ) )
    env.put( Context.AUTHORITATIVE, 'true' )
    final DirContext ictx = new InitialDirContext( env )
    try {
      final Attributes attrs = ictx.getAttributes( name, ['A'] as String[] )
      final String ip = attrs.get('a')?.get( )
      return ip
    } finally {
      ictx.close()
    }
  }

  private HostnameVerifier hostnameVerifier() {
    new HostnameVerifier() {
      @Override boolean verify(String hostname, SSLSession session) { true }
    }
  }

  private SSLSocketFactory sslSocketFactory() {
    final X509TrustManager trustManager = new X509ExtendedTrustManager(){
      @Override X509Certificate[] getAcceptedIssuers() { return null; }
      @Override void checkClientTrusted(X509Certificate[] arg0, String arg1) {}
      @Override void checkServerTrusted(X509Certificate[] arg0, String arg1) {}
      @Override void checkClientTrusted(X509Certificate[] chain, String authType, Socket socket) {}
      @Override void checkServerTrusted(X509Certificate[] chain, String authType, Socket socket) {}
      @Override void checkClientTrusted(X509Certificate[] chain, String authType, SSLEngine engine) {}
      @Override void checkServerTrusted(X509Certificate[] chain, String authType, SSLEngine engine) {}
    }
    SSLContext ctx = SSLContext.getInstance("TLS")
    ctx.init([] as KeyManager[], [trustManager] as TrustManager[], new SecureRandom())
    ctx.socketFactory
  }

  @Test
  void testLoadBalancedRequest( ) throws Exception {
    final AmazonEC2 ec2 = N4j.getEc2Client( credentials, EC2_ENDPOINT )

    // Find an AZ to use
    final DescribeAvailabilityZonesResult azResult = ec2.describeAvailabilityZones()

    Assert.assertTrue("Availability zone not found", azResult.getAvailabilityZones().size() > 0)

    final String availabilityZone = azResult.getAvailabilityZones().get( 0 ).getZoneName()
    N4j.print( "Using availability zone: " + availabilityZone )

    final String availabilityZone2 = azResult.getAvailabilityZones().getAt( 1 )?.getZoneName( )

    final String namePrefix = UUID.randomUUID().toString().substring(0, 13) + "-"
    N4j.print( "Using resource prefix for test: " + namePrefix )

    final AmazonRoute53 route53 = N4j.getRoute53Client( credentials, N4j.ROUTE53_ENDPOINT )
    boolean route53Enabled = false
    try {
      route53.listHostedZones()
      route53Enabled = true;
      N4j.print( "Route53 enabled, will check hosted zone details for elb" );
    } catch ( AmazonServiceException e ) {
      N4j.print( "Error from route 53 service check (assuming not enabled): ${e}" )
    }

    final AmazonElasticLoadBalancing elb = getELBClient( credentials )
    final AmazonIdentityManagement iam = getIamClient( credentials )
    final List<Runnable> cleanupTasks = [] as List<Runnable>
    try {
      String certArn = ""
      iam.with{
        N4j.print( "Uploading server certificate" )
        String certName = "${namePrefix}elb"
        certArn = uploadServerCertificate( new UploadServerCertificateRequest(
            path: "/${namePrefix}0/",
            serverCertificateName: certName,
            certificateBody: ALICE_PEM,
            privateKey: ALICE_PK
        ) ).with {
          serverCertificateMetadata.arn
        }
        N4j.print( "Created server certificate ${certArn}" )
        cleanupTasks.add {
          N4j.print( "Deleting server certificate ${certArn}" )
          deleteServerCertificate( new DeleteServerCertificateRequest(
              serverCertificateName: certName,
          ) )
        }
      }

      elb.with {
        String loadBalancerName = "${namePrefix}balancer1"
        N4j.print( "Creating load balancer: ${loadBalancerName}" )
        createLoadBalancer( new CreateLoadBalancerRequest(
            loadBalancerName: loadBalancerName,
            listeners: [
                new Listener(
                    loadBalancerPort: 80,
                    protocol: 'HTTP',
                    instancePort: 9999,
                    instanceProtocol: 'HTTP'
                ),
                new Listener(
                        loadBalancerPort: 443,
                        protocol: 'HTTPS',
                        instancePort: 9999,
                        instanceProtocol: 'HTTP',
                        sSLCertificateId: certArn
                )
            ],
            availabilityZones: [ availabilityZone ]
        ) )
        cleanupTasks.add {
          N4j.print( "Deleting load balancer: ${loadBalancerName}" )
          deleteLoadBalancer( new DeleteLoadBalancerRequest( loadBalancerName: loadBalancerName ) )
        }

        N4j.print( "Created load balancer: ${loadBalancerName}" )
        String balancerHost = describeLoadBalancers( new DescribeLoadBalancersRequest( loadBalancerNames: [ loadBalancerName ] ) ).with {
          N4j.print( loadBalancerDescriptions.toString( ) )
          Assert.assertTrue("Expected one load balancer, but was: ${loadBalancerDescriptions.size()}", loadBalancerDescriptions.size() == 1)
          loadBalancerDescriptions.get( 0 ).with {
            Assert.assertTrue("Expected name ${loadBalancerName}, but was: ${it.loadBalancerName}", loadBalancerName == it.loadBalancerName)
            Assert.assertTrue("Expected scheme internet-facing, but was: ${scheme}", scheme == 'internet-facing')
            Assert.assertTrue("Expected zones [ ${availabilityZone} ], but was: ${availabilityZones}", availabilityZones == [availabilityZone])
            Assert.assertTrue("Expected source security group", sourceSecurityGroup != null)
            String vpcId = VPCId
            if (vpcId) {
              Assert.assertTrue("Expected vpc ${vpcId}, but was: ${VPCId}", VPCId == vpcId)
              Assert.assertTrue("Expected one subnet, but was: ${subnets}", subnets != null && subnets.size()==1)
              Assert.assertTrue("Expected one (VPC) security group, but was: ${securityGroups}", securityGroups != null && securityGroups.size()==1)
              ec2.with {
                String groupId = securityGroups.get(0)
                N4j.print("Ensuring ports open in elb security group: ${groupId}")
                authorizeSecurityGroupIngress( new AuthorizeSecurityGroupIngressRequest(
                        groupId: groupId,
                        ipPermissions: [
                                new IpPermission(
                                        ipProtocol: 'tcp',
                                        fromPort: 80,
                                        toPort: 80,
                                        ipv4Ranges: [new IpRange(cidrIp: '0.0.0.0/0')]
                                ),
                                new IpPermission(
                                        ipProtocol: 'tcp',
                                        fromPort: 443,
                                        toPort: 443,
                                        ipv4Ranges: [new IpRange(cidrIp: '0.0.0.0/0')]
                                )

                        ]
                ))
              }
            } else {
              Assert.assertTrue("Expected no vpc, but was: ${VPCId}", VPCId == null)
              Assert.assertTrue("Expected no subnets, but was: ${subnets}", subnets == null || subnets.isEmpty())
              Assert.assertTrue("Expected no (VPC) security groups, but was: ${securityGroups}", securityGroups == null || securityGroups.isEmpty())
            }
            if ( route53Enabled ) {
              N4j.print("Checking canonical hosted zone ${canonicalHostedZoneName}/${canonicalHostedZoneNameID}");
              Assert.assertNotNull("Expected CanonicalHostedZoneName", canonicalHostedZoneName )
              Assert.assertNotNull("Expected canonicalHostedZoneNameID", canonicalHostedZoneNameID )
            }
            sourceSecurityGroup.with {
              ec2.with {
                String authGroupName = "${namePrefix}elb-source-group-auth-test"
                N4j.print( "Creating security group to test elb source group authorization: ${authGroupName}" )
                String authGroupId = createSecurityGroup( new CreateSecurityGroupRequest(
                    groupName: authGroupName,
                    description: 'Test security group for validation of ELB source group authorization'
                ) ).with {
                  groupId
                }
                N4j.print( "Created security group ${authGroupName}, with id ${authGroupId}" )
                cleanupTasks.add{
                  N4j.print( "Deleting security group: ${authGroupName}/${authGroupId}" )
                  deleteSecurityGroup( new DeleteSecurityGroupRequest(
                      groupId: authGroupId
                  ) )
                }
                N4j.print( "Authorizing elb source group ${ownerAlias}/${groupName} for ${authGroupName}/${authGroupId}" )
                authorizeSecurityGroupIngress( new AuthorizeSecurityGroupIngressRequest(
                  groupId: authGroupId,
                  ipPermissions: [
                      new IpPermission(
                          ipProtocol: 'tcp',
                          fromPort: 9999,
                          toPort: 9999,
                          userIdGroupPairs: [
                              new UserIdGroupPair(
                                  userId: ownerAlias,
                                  groupName: groupName
                              )
                          ]
                      )
                  ]
                ))
              }
            }
            DNSName
          }
        }

        // test running an instance with an HTTP service
        ec2.with{
          // Find an image to use
          final String imageId = describeImages( new DescribeImagesRequest(
              filters: [
                  new Filter( name: "image-type", values: ["machine"] ),
                  new Filter( name: "is-public", values: ["true"] ),
                  new Filter( name: "root-device-type", values: ["instance-store"] ),
              ]
          ) ).with {
            images?.getAt( 0 )?.imageId
          }
          Assert.assertTrue("Image not found", imageId != null)
          N4j.print( "Using image: ${imageId}" )

          // Discover SSH key
          final String keyName = describeKeyPairs( ).with {
            keyPairs?.getAt(0)?.keyName
          }
          N4j.print( "Using key pair: " + keyName )

          String instanceSecurityGroup = "${namePrefix}instance-group"
          N4j.print( "Creating security group with name: ${instanceSecurityGroup}" )
          String instanceGroupId = createSecurityGroup( new CreateSecurityGroupRequest(
            groupName: instanceSecurityGroup,
            description: 'Test security group for instances'
          ) ).with {
            groupId
          }

          N4j.print( "Authorizing instance security group ${instanceSecurityGroup}/${instanceGroupId}" )
          authorizeSecurityGroupIngress( new AuthorizeSecurityGroupIngressRequest(
              groupId: instanceGroupId,
              ipPermissions: [
                  new IpPermission(
                      ipProtocol: 'tcp',
                      fromPort: 22,
                      toPort: 22,
                      ipRanges: [ '0.0.0.0/0' ]
                  ),
                  new IpPermission(
                      ipProtocol: 'tcp',
                      fromPort: 9999,
                      toPort: 9999,
                      ipRanges: [ '0.0.0.0/0' ]
                  ),
              ]
          ))

          String userDataText = '''
          #!/usr/bin/python -tt
          import SimpleHTTPServer, BaseHTTPServer

          class StaticHandler(SimpleHTTPServer.SimpleHTTPRequestHandler):
            def do_GET(self):
              self.send_response( 200 )
              self.send_header('Content-Type', 'text/plain; charset=utf-8')
              self.end_headers( )
              self.wfile.write("Hello");
              self.wfile.close( );

          BaseHTTPServer.HTTPServer( ("", 9999), StaticHandler ).serve_forever( )
          '''.stripIndent( ).trim( )

          N4j.print( "Running instance to access via load balancer ${loadBalancerName}" )
          String instanceId = runInstances( new RunInstancesRequest(
              minCount: 1,
              maxCount: 1,
              imageId: imageId,
              keyName: keyName,
              securityGroupIds: [ instanceGroupId ],
              placement: new Placement(
                  availabilityZone: availabilityZone
              ),
              userData: Base64.encoder.encodeToString( userDataText.getBytes( StandardCharsets.UTF_8 ) )
          )).with {
            reservation?.with {
              instances?.getAt( 0 )?.instanceId
            }
          }

          N4j.print( "Instance running with identifier ${instanceId}" )
          cleanupTasks.add{
            N4j.print( "Terminating instance ${instanceId}" )
            terminateInstances( new TerminateInstancesRequest( instanceIds: [ instanceId ] ) )

            N4j.print( "Waiting for instance ${instanceId} to terminate" )
            ( 1..24 ).find{
              sleep 5000
              N4j.print( "Waiting for instance ${instanceId} to terminate, waited ${it*5}s" )
              describeInstances( new DescribeInstancesRequest(
                  instanceIds: [ instanceId ],
                  filters: [ new Filter( name: "instance-state-name", values: [ "terminated" ] ) ]
              ) ).with {
                reservations?.getAt( 0 )?.instances?.getAt( 0 )?.instanceId == instanceId
              }
            }
          }

          String instancePublicIp
          N4j.print( "Waiting for instance ${instanceId} to start" )
          ( 1..60 ).find{
            sleep 5000
            N4j.print( "Waiting for instance ${instanceId} to start, waited ${it*5}s" )
            describeInstances( new DescribeInstancesRequest(
                instanceIds: [ instanceId ],
                filters: [ new Filter( name: "instance-state-name", values: [ "running" ] ) ]
            ) ).with {
              instancePublicIp = reservations?.getAt( 0 )?.instances?.getAt( 0 )?.publicIpAddress
              reservations?.getAt( 0 )?.instances?.getAt( 0 )?.instanceId == instanceId
            }
          }
          Assert.assertTrue("Expected instance public ip", instancePublicIp != null)

          N4j.print( "Registering instance ${instanceId} with load balancer ${loadBalancerName}" )
          registerInstancesWithLoadBalancer( new RegisterInstancesWithLoadBalancerRequest(
            loadBalancerName: loadBalancerName,
            instances: [ new Instance( instanceId ) ]
          ) )

          N4j.print( "Waiting for instance ${instanceId} to be healthy" )
          ( 1..60 ).find{
            sleep 5000
            N4j.print( "Waiting for instance ${instanceId} to be healthy, waited ${it*5}s" )
            describeInstanceHealth( new DescribeInstanceHealthRequest(
                loadBalancerName: loadBalancerName,
                instances: [ new Instance( instanceId ) ]
            ) ).with {
              'InService' == instanceStates?.getAt( 0 )?.state
            }
          }

          String instanceUrl = "http://${instancePublicIp}:9999/"
          N4j.print( "Accessing instance ${instanceUrl}" )
          String instanceResponse = new URL( instanceUrl ).
              getText( connectTimeout: 10000, readTimeout: 10000, useCaches: false, allowUserInteraction: false )
          Assert.assertTrue("Expected instance response Hello, but was: ${instanceResponse}", 'Hello' == instanceResponse)

          N4j.print( "Resolving load balancer host ${balancerHost}" )
          Set<String> dnsHosts = getDnsHosts(getServicesClient(credentials))
          String balancerIp = null
          ( 1..12 ).find {
            if ( it > 1 ) sleep 5000
            balancerIp = lookup(balancerHost, dnsHosts)
          }
          Assert.assertNotNull('Expected ip for load balancer', balancerIp)
          N4j.print( "Resolved load balancer host ${balancerHost} to ${balancerIp}" )
          String balancerUrl = "http://${balancerIp}/"
          N4j.print( "Accessing instance via load balancer ${balancerUrl}" )
          String balancerResponse = new URL( balancerUrl ).
              getText( connectTimeout: 10000, readTimeout: 10000, useCaches: false, allowUserInteraction: false )
          Assert.assertTrue("Expected balancer response Hello, but was: ${balancerResponse}", 'Hello' == balancerResponse)

          String balancerHttpsUrl = "https://${balancerIp}/"
          N4j.print( "Accessing instance via load balancer ${balancerHttpsUrl}" )
          HttpsURLConnection balancerHttpsConnection = new URL( balancerHttpsUrl ).openConnection() as HttpsURLConnection
          balancerHttpsConnection.with {
            setHostnameVerifier(hostnameVerifier())
            setSSLSocketFactory(sslSocketFactory())
            setConnectTimeout(10000)
            setReadTimeout(10000)
            setUseCaches(false)
            setAllowUserInteraction(false)
          }
          String balancerHttpsResponse = new String(
                  ByteStreams.toByteArray(balancerHttpsConnection.getContent() as InputStream),
                  StandardCharsets.UTF_8)
          Assert.assertTrue("Expected balancer response Hello, but was: ${balancerHttpsResponse}",
                            'Hello' == balancerHttpsResponse)
        }

        // test changing zones
        if ( availabilityZone2 ) {
          N4j.print( "Enabling availability zone for balancer ${loadBalancerName}" )
          enableAvailabilityZonesForLoadBalancer( new EnableAvailabilityZonesForLoadBalancerRequest(
            loadBalancerName: loadBalancerName,
            availabilityZones: [ availabilityZone2 ]
          ) ).with {
            N4j.print( "Availability zones now ${availabilityZones}" )
          }
          N4j.print( describeLoadBalancers( ).toString( ) )
        } else {
          N4j.print( 'Only one zone found, skipping multi-zone test' )
        }
      }

      N4j.print( "Test complete" )
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
