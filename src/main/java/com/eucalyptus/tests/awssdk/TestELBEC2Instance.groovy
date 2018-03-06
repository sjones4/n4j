package com.eucalyptus.tests.awssdk

import com.amazonaws.auth.AWSCredentialsProvider
import com.amazonaws.auth.AWSStaticCredentialsProvider
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.services.ec2.AmazonEC2
import com.amazonaws.services.ec2.AmazonEC2Client
import com.amazonaws.services.ec2.model.AuthorizeSecurityGroupIngressRequest
import com.amazonaws.services.ec2.model.CreateSecurityGroupRequest
import com.amazonaws.services.ec2.model.DeleteSecurityGroupRequest
import com.amazonaws.services.ec2.model.DescribeAvailabilityZonesResult
import com.amazonaws.services.ec2.model.DescribeImagesRequest
import com.amazonaws.services.ec2.model.DescribeInstancesRequest
import com.amazonaws.services.ec2.model.Filter
import com.amazonaws.services.ec2.model.IpPermission
import com.amazonaws.services.ec2.model.RunInstancesRequest
import com.amazonaws.services.ec2.model.TerminateInstancesRequest
import com.amazonaws.services.ec2.model.UserIdGroupPair
import com.amazonaws.services.elasticloadbalancing.AmazonElasticLoadBalancing
import com.amazonaws.services.elasticloadbalancing.AmazonElasticLoadBalancingClient
import com.amazonaws.services.elasticloadbalancing.model.*
import com.github.sjones4.youcan.youserv.YouServ
import com.github.sjones4.youcan.youserv.YouServClient
import com.github.sjones4.youcan.youserv.model.DescribeServicesRequest
import org.junit.Before

import javax.naming.Context
import javax.naming.directory.Attributes
import javax.naming.directory.DirContext
import javax.naming.directory.InitialDirContext
import java.nio.charset.StandardCharsets
import org.junit.Assert
import org.junit.Test

import static N4j.initEndpoints
import static N4j.ACCESS_KEY
import static N4j.SECRET_KEY
import static com.eucalyptus.tests.awssdk.N4j.EC2_ENDPOINT
import static com.eucalyptus.tests.awssdk.N4j.ELB_ENDPOINT
import static com.eucalyptus.tests.awssdk.N4j.SERVICES_ENDPOINT

/**
 * Basic ELB functionality test.
 *
 * Simple load balanced request test for cloud with any network mode.
 */
class TestELBEC2Instance {

  private AWSCredentialsProvider credentials

  @Before
  void init( ) {
    initEndpoints( )
    this.credentials = new AWSStaticCredentialsProvider( new BasicAWSCredentials( ACCESS_KEY, SECRET_KEY ) )
  }

  private AmazonEC2 getEC2Client( final AWSCredentialsProvider credentials ) {
    final AmazonEC2 ec2 = new AmazonEC2Client( credentials )
    ec2.setEndpoint( EC2_ENDPOINT )
    ec2
  }

  private AmazonElasticLoadBalancing getELBClient( final AWSCredentialsProvider credentials ) {
    final AmazonElasticLoadBalancing elb = new AmazonElasticLoadBalancingClient( credentials )
    elb.setEndpoint( ELB_ENDPOINT )
    elb
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

  @Test
  void testLoadBalancedRequest( ) throws Exception {
    final AmazonEC2 ec2 = getEC2Client( credentials )

    // Find an AZ to use
    final DescribeAvailabilityZonesResult azResult = ec2.describeAvailabilityZones()

    Assert.assertTrue("Availability zone not found", azResult.getAvailabilityZones().size() > 0)

    final String availabilityZone = azResult.getAvailabilityZones().get( 0 ).getZoneName()
    N4j.print( "Using availability zone: " + availabilityZone )

    final String availabilityZone2 = azResult.getAvailabilityZones().getAt( 1 )?.getZoneName( )

    final String namePrefix = UUID.randomUUID().toString().substring(0, 13) + "-"
    N4j.print( "Using resource prefix for test: " + namePrefix )

    final AmazonElasticLoadBalancing elb = getELBClient( credentials )
    final List<Runnable> cleanupTasks = [] as List<Runnable>
    try {
      elb.with {
        String loadBalancerName = "${namePrefix}balancer1"
        N4j.print( "Creating load balancer: ${loadBalancerName}" )
        createLoadBalancer( new CreateLoadBalancerRequest(
            loadBalancerName: loadBalancerName,
            listeners: [ new Listener(
                loadBalancerPort: 9999,
                protocol: 'HTTP',
                instancePort: 9999,
                instanceProtocol: 'HTTP'
            ) ],
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
            } else {
              Assert.assertTrue("Expected no vpc, but was: ${VPCId}", VPCId == null)
              Assert.assertTrue("Expected no subnets, but was: ${subnets}", subnets == null || subnets.isEmpty())
              Assert.assertTrue("Expected no (VPC) security groups, but was: ${securityGroups}", securityGroups == null || securityGroups.isEmpty())
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
            ( 1..25 ).find{
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
          ( 1..25 ).find{
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
          ( 1..50 ).find{
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
          String balancerUrl = "http://${balancerIp}:9999/"
          N4j.print( "Accessing instance via load balancer ${balancerUrl}" )
          String balancerResponse = new URL( balancerUrl ).
              getText( connectTimeout: 10000, readTimeout: 10000, useCaches: false, allowUserInteraction: false )
          Assert.assertTrue("Expected balancer response Hello, but was: ${balancerResponse}", 'Hello' == balancerResponse)
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
