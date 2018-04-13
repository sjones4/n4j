package com.eucalyptus.tests.load

import com.amazonaws.ClientConfiguration
import com.amazonaws.auth.AWSCredentialsProvider
import com.amazonaws.auth.AWSStaticCredentialsProvider
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.services.ec2.model.Address
import com.amazonaws.services.ec2.model.AuthorizeSecurityGroupIngressRequest
import com.amazonaws.services.ec2.model.CreateSecurityGroupRequest
import com.amazonaws.services.ec2.model.DescribeAddressesRequest
import com.amazonaws.services.ec2.model.DescribeAvailabilityZonesResult
import com.amazonaws.services.ec2.model.DescribeImagesRequest
import com.amazonaws.services.ec2.model.DescribeInstancesRequest
import com.amazonaws.services.ec2.model.Filter
import com.amazonaws.services.ec2.model.IpPermission
import com.amazonaws.services.ec2.model.RunInstancesRequest
import com.amazonaws.services.ec2.model.TerminateInstancesRequest
import com.amazonaws.services.elasticloadbalancing.AmazonElasticLoadBalancing
import com.amazonaws.services.elasticloadbalancing.AmazonElasticLoadBalancingClient
import com.amazonaws.services.elasticloadbalancing.model.ConfigureHealthCheckRequest
import com.amazonaws.services.elasticloadbalancing.model.CreateLoadBalancerRequest
import com.amazonaws.services.elasticloadbalancing.model.DeleteLoadBalancerRequest
import com.amazonaws.services.elasticloadbalancing.model.DescribeInstanceHealthRequest
import com.amazonaws.services.elasticloadbalancing.model.DescribeLoadBalancersRequest
import com.amazonaws.services.elasticloadbalancing.model.HealthCheck
import com.amazonaws.services.elasticloadbalancing.model.Instance
import com.amazonaws.services.elasticloadbalancing.model.Listener
import com.amazonaws.services.elasticloadbalancing.model.RegisterInstancesWithLoadBalancerRequest
import com.eucalyptus.tests.awssdk.N4j
import com.github.sjones4.youcan.youprop.YouProp
import com.github.sjones4.youcan.youprop.YouPropClient
import com.github.sjones4.youcan.youprop.model.DescribePropertiesRequest
import com.github.sjones4.youcan.youserv.YouServ
import com.github.sjones4.youcan.youserv.YouServClient
import com.github.sjones4.youcan.youserv.model.DescribeServicesRequest
import com.github.sjones4.youcan.youserv.model.Filter as ServiceFilter
import com.github.sjones4.youcan.youtwo.YouTwo
import com.github.sjones4.youcan.youtwo.YouTwoClient
import com.github.sjones4.youcan.youtwo.model.DescribeInstanceTypesRequest
import org.junit.AfterClass
import org.junit.Assert
import org.junit.BeforeClass
import org.junit.Test

import javax.naming.Context
import javax.naming.directory.Attributes
import javax.naming.directory.DirContext
import javax.naming.directory.InitialDirContext
import java.nio.charset.StandardCharsets
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

import static com.eucalyptus.tests.awssdk.N4j.ACCESS_KEY
import static com.eucalyptus.tests.awssdk.N4j.SECRET_KEY

/**
 *
 */
class LoadBalancerChurnLoadTest {

  private static String testAcct
  private static AWSCredentialsProvider testAcctAdminCredentials
  private static AWSCredentialsProvider cloudAdminCredentials

  @BeforeClass
  static void init( ){
    N4j.testInfo( ObjectChurnLoadTest.simpleName )
    N4j.getCloudInfo( )
    testAcct = "${N4j.NAME_PREFIX}loadbalancer-churn-load"
    N4j.createAccount( testAcct )
    testAcctAdminCredentials = new AWSStaticCredentialsProvider( N4j.getUserCreds( testAcct, 'admin' ) )
    cloudAdminCredentials = new AWSStaticCredentialsProvider( new BasicAWSCredentials(ACCESS_KEY, SECRET_KEY) )
  }

  @AfterClass
  static void cleanup( ) {
    N4j.deleteAccount( testAcct )
  }

  private static YouTwo getEC2Client( final AWSCredentialsProvider credentials ) {
    final YouTwo ec2 = new YouTwoClient( credentials, new ClientConfiguration(
        socketTimeout: TimeUnit.MINUTES.toMillis( 2 )
    ) )
    ec2.setEndpoint( N4j.EC2_ENDPOINT )
    ec2
  }

  private static AmazonElasticLoadBalancing getELBClient( final AWSCredentialsProvider credentials ) {
    final AmazonElasticLoadBalancing elb = new AmazonElasticLoadBalancingClient( credentials, new ClientConfiguration(
        socketTimeout: TimeUnit.MINUTES.toMillis( 2 )
    )  )
    elb.setEndpoint( N4j.ELB_ENDPOINT )
    elb
  }

  private YouProp getPropertiesClient(final AWSCredentialsProvider credentials ) {
    YouPropClient youProp = new YouPropClient( credentials )
    youProp.setEndpoint( N4j.PROPERTIES_ENDPOINT )
    youProp
  }

  private YouServ getServicesClient(final AWSCredentialsProvider credentials ) {
    YouServClient youServ = new YouServClient( credentials )
    youServ.setEndpoint( N4j.SERVICES_ENDPOINT )
    youServ
  }

  private Set<String> getDnsHosts( final YouServ youServ ) {
    youServ.describeServices( new DescribeServicesRequest(
        filters: [
            new ServiceFilter( name: 'service-type', values: [ 'dns' ] )
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

  @Test
  void test( ) {
    final YouTwo ec2 = getEC2Client( cloudAdminCredentials )

    // Find an AZ to use
    final DescribeAvailabilityZonesResult azResult = ec2.describeAvailabilityZones()

    Assert.assertTrue( 'Availability zone not found', azResult.getAvailabilityZones().size() > 0 )

    String availabilityZone = azResult.getAvailabilityZones().get( 0 ).getZoneName()

    // Find an image to use
    final String imageId = ec2.describeImages( new DescribeImagesRequest(
        filters: [
            new Filter( name: "image-type", values: ["machine"] ),
            new Filter( name: "root-device-type", values: ["instance-store"] ),
            new Filter( name: "is-public", values: ["true"] ),
        ]
    ) ).with {
      images?.getAt( 0 )?.imageId
    }
    Assert.assertNotNull( 'Image not found', imageId )
    N4j.print( "Using image: ${imageId}" )

    // Find available addresses
    final int addressCount = ec2.describeAddresses( new DescribeAddressesRequest( publicIps: ['verbose' ] ) ).with {
      addresses.count { Address address ->
        'nobody' == address?.instanceId
      }
    }
    Assert.assertTrue( "Address count ${addressCount} > 10", addressCount > 10 )
    N4j.print( "Found available public addresses: ${addressCount}" )

    // Find elb instance type for use determining capacity
    String elbInstanceType = getPropertiesClient( cloudAdminCredentials ).with {
      describeProperties( new DescribePropertiesRequest(
          properties: [ 'services.loadbalancing.worker.instance_type' ]
      ) )?.with {
        properties?.getAt( 0 )?.getValue( )
      }
    }
    Assert.assertNotNull( 'Load balancer instance type not found', elbInstanceType )
    N4j.print( "Found elb instance type: ${elbInstanceType}" )

    // Find dns hosts
    Set<String> dnsHosts = getDnsHosts(getServicesClient(cloudAdminCredentials))
    N4j.print( "Using dns endpoints: ${dnsHosts}" )

    final String namePrefix = UUID.randomUUID().toString().substring(0, 13) + "-"
    N4j.print( "Using resource prefix for test: " + namePrefix )

    final long startTime = System.currentTimeMillis( )
    final List<Runnable> cleanupTasks = [] as List<Runnable>
    try {
      // test running an instance with an HTTP service
      N4j.print("Launching target instance")
      String instancePublicIp = null
      String instanceGroupId = null
      String instanceId = null
      getEC2Client( testAcctAdminCredentials ).with {
        String instanceSecurityGroup = "${namePrefix}instance-group"
        N4j.print("Creating security group with name: ${instanceSecurityGroup}")
        instanceGroupId = createSecurityGroup(new CreateSecurityGroupRequest(
            groupName: instanceSecurityGroup,
            description: 'Test security group for instances'
        )).with {
          groupId
        }

        N4j.print("Authorizing instance security group ${instanceSecurityGroup}/${instanceGroupId}")
        authorizeSecurityGroupIngress(new AuthorizeSecurityGroupIngressRequest(
            groupId: instanceGroupId,
            ipPermissions: [
                new IpPermission(
                    ipProtocol: 'tcp',
                    fromPort: 22,
                    toPort: 22,
                    ipRanges: ['0.0.0.0/0']
                ),
                new IpPermission(
                    ipProtocol: 'tcp',
                    fromPort: 9999,
                    toPort: 9999,
                    ipRanges: ['0.0.0.0/0']
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
          '''.stripIndent().trim()

        N4j.print("Running instance to access via load balancers")
        instanceId = runInstances(new RunInstancesRequest(
            minCount: 1,
            maxCount: 1,
            imageId: imageId,
            securityGroupIds: [instanceGroupId],
            userData: Base64.encoder.encodeToString(userDataText.getBytes(StandardCharsets.UTF_8))
        )).with {
          reservation?.with {
            instances?.getAt(0)?.instanceId
          }
        }

        N4j.print("Instance running with identifier ${instanceId}")
        cleanupTasks.add {
          N4j.print("Terminating instance ${instanceId}")
          terminateInstances(new TerminateInstancesRequest(instanceIds: [instanceId]))

          N4j.print("Waiting for instance ${instanceId} to terminate")
          (1..25).find {
            sleep 5000
            N4j.print("Waiting for instance ${instanceId} to terminate, waited ${it * 5}s")
            describeInstances(new DescribeInstancesRequest(
                instanceIds: [instanceId],
                filters: [new Filter(name: "instance-state-name", values: ["terminated"])]
            )).with {
              reservations?.getAt(0)?.instances?.getAt(0)?.instanceId == instanceId
            }
          }
        }

        N4j.print("Waiting for instance ${instanceId} to start")
        (1..25).find {
          sleep 5000
          N4j.print("Waiting for instance ${instanceId} to start, waited ${it * 5}s")
          describeInstances(new DescribeInstancesRequest(
              instanceIds: [instanceId],
              filters: [new Filter(name: "instance-state-name", values: ["running"])]
          )).with {
            instancePublicIp = reservations?.getAt(0)?.instances?.getAt(0)?.publicIpAddress
            reservations?.getAt(0)?.instances?.getAt(0)?.instanceId == instanceId
          }
        }
        Assert.assertTrue("Expected instance public ip", instancePublicIp != null)
      }

      // Find elb instance type capacity
      Integer availability = ec2.describeInstanceTypes( new DescribeInstanceTypesRequest(
          availability: true,
          instanceTypes: [ elbInstanceType ]
      ) ).with {
        instanceTypes?.getAt( 0 )?.getAvailability( )?.find{ it.zoneName == availabilityZone }?.getAvailable( )
      }
      Assert.assertNotNull( 'Availability not found', availability )
      N4j.print( "Using instance type ${elbInstanceType} and availability zone ${availabilityZone} with availability ${availability}" )

      final int elbIterations = 10
      final int elbThreads = Math.min( 20, Math.min( availability - 1, addressCount -1 ) )
      final CountDownLatch latch = new CountDownLatch( elbThreads )
      final AtomicInteger successCount = new AtomicInteger(0)
      N4j.print( "Churning ${elbIterations} load balancers on ${elbThreads} thread(s)" )
      ( 1..elbThreads ).each { Integer thread ->
        Thread.start {
          try {
            getELBClient(testAcctAdminCredentials).with {
              ( 1..elbIterations ).each { Integer count ->
                String loadBalancerName = "${namePrefix}balancer-${thread}-${count}"
                N4j.print("[${thread}] Creating load balancer ${count}/${elbIterations}: ${loadBalancerName}")
                for( int i=0; i<12; i++ ) {
                  try {
                    createLoadBalancer(new CreateLoadBalancerRequest(
                        loadBalancerName: loadBalancerName,
                        listeners: [new Listener(
                            loadBalancerPort: 9999,
                            protocol: 'HTTP',
                            instancePort: 9999,
                            instanceProtocol: 'HTTP'
                        )],
                        availabilityZones: [availabilityZone]
                    ))
                    break
                  } catch( e ) {
                    if ( e.message.contains( 'Not enough resources' ) ) {
                      N4j.print("[${thread}] Insufficient resources, will retry in 5s creating load balancer ${count}/${elbIterations}: ${loadBalancerName}")
                      sleep 5000
                    } else {
                      throw e
                    }
                  }
                }

                try {
                  String balancerHost = describeLoadBalancers(new DescribeLoadBalancersRequest(loadBalancerNames: [loadBalancerName])).with {
                    loadBalancerDescriptions.get(0).with {
                      DNSName
                    }
                  }

                  N4j.print("[${thread}] Configuring health checks for load balancer ${loadBalancerName}/${balancerHost}")
                  configureHealthCheck( new ConfigureHealthCheckRequest(
                      loadBalancerName: loadBalancerName,
                      healthCheck: new HealthCheck(
                          target: 'HTTP:9999/',
                          healthyThreshold: 2,
                          unhealthyThreshold: 6,
                          interval: 10,
                          timeout: 5
                      )
                  ))

                  N4j.print("[${thread}] Registering instance ${instanceId} with load balancer ${loadBalancerName}/${balancerHost}")
                  registerInstancesWithLoadBalancer(new RegisterInstancesWithLoadBalancerRequest(
                      loadBalancerName: loadBalancerName,
                      instances: [new Instance(instanceId)]
                  ))

                  N4j.print("[${thread}] Waiting for load balancer ${count}/${elbIterations} instance ${instanceId} to be healthy")
                  (1..60).find {
                    sleep 15000
                    N4j.print("[${thread}] Waiting for load balancer ${count}/${elbIterations} instance ${instanceId} to be healthy, waited ${it * 15}s")
                    describeInstanceHealth(new DescribeInstanceHealthRequest(
                        loadBalancerName: loadBalancerName,
                        instances: [new Instance(instanceId)]
                    )).with {
                      'InService' == instanceStates?.getAt(0)?.state
                    }
                  }

                  String instanceUrl = "http://${instancePublicIp}:9999/"
                  N4j.print("[${thread}] Accessing instance ${instanceId} ${instanceUrl}")
                  String instanceResponse = new URL(instanceUrl).
                      getText(connectTimeout: 10000, readTimeout: 10000, useCaches: false, allowUserInteraction: false)
                  Assert.assertTrue("[${thread}] Expected instance ${instanceId} response Hello, but was: ${instanceResponse}", 'Hello' == instanceResponse)
                  N4j.print("[${thread}] Response from instance ${instanceId} verified")

                  N4j.print("[${thread}] Resolving load balancer ${count}/${elbIterations} host ${balancerHost}")
                  String balancerIp = null
                  (1..12).find {
                    if (it > 1) sleep 5000
                    balancerIp = lookup(balancerHost, dnsHosts)
                  }
                  Assert.assertNotNull("[${thread}] Expected ip for load balancer ${count}/${elbIterations}", balancerIp)
                  N4j.print("[${thread}] Resolved load balancer ${count}/${elbIterations} host ${balancerHost} to ${balancerIp}")
                  String balancerUrl = "http://${balancerIp}:9999/"
                  N4j.print("[${thread}] Accessing instance ${instanceId} via load balancer ${count}/${elbIterations} ${balancerUrl}")
                  for ( int i=0; i<12; i++ ) {
                    try {
                      String balancerResponse = new URL(balancerUrl).
                          getText(connectTimeout: 10000, readTimeout: 10000, useCaches: false, allowUserInteraction: false)
                      Assert.assertTrue("[${thread}] Expected balancer ${count}/${elbIterations} response Hello, but was: ${balancerResponse}", 'Hello' == balancerResponse)
                      N4j.print("[${thread}] Response from load balancer ${count}/${elbIterations} host ${balancerHost} verified")
                      break
                    } catch ( e ) {
                      if ( e.message.contains( '503' ) ) { // check for 503 http status code and retry
                        N4j.print("[${thread}] Service unavailable, will retry in 5s accessing instance ${instanceId} via load balancer ${count}/${elbIterations} ${balancerUrl}")
                        sleep 5000
                      } else if ( e.message.contains('Connection refused' ) ) { // retry
                        N4j.print("[${thread}] Connection refused, will retry in 5s accessing instance ${instanceId} via load balancer ${count}/${elbIterations} ${balancerUrl}")
                        sleep 5000
                      } else {
                        throw e
                      }
                    }
                  }
                } finally {
                  N4j.print("[${thread}] Deleting load balancer ${count}/${elbIterations} ${loadBalancerName}")
                  for ( int i=0; i<12; i++ ) {
                    try {
                      deleteLoadBalancer(new DeleteLoadBalancerRequest(loadBalancerName: loadBalancerName))
                      break
                    } catch ( e ) {
                      if ( e.message.contains( 'Failed to delete' ) ) {
                        N4j.print("[${thread}] Delete failed, will retry in 5s deleting load balancer ${count}/${elbIterations} ${loadBalancerName}")
                        sleep 5000
                      } else {
                        throw e
                      }
                    }
                  }
                }
              }
            }

            successCount.incrementAndGet( )
          } finally {
            latch.countDown( )
            N4j.print("[${thread}] Thread completed")
          }
        }
      }
      latch.await( )
      Assert.assertEquals( "All threads successful", elbThreads, successCount.get( ) )

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
