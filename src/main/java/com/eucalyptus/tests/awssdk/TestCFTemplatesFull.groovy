package com.eucalyptus.tests.awssdk

import com.amazonaws.auth.AWSCredentialsProvider
import com.amazonaws.auth.AWSStaticCredentialsProvider
import com.amazonaws.client.builder.AwsClientBuilder
import com.amazonaws.services.cloudformation.AmazonCloudFormation
import com.amazonaws.services.cloudformation.AmazonCloudFormationClient
import com.amazonaws.services.cloudformation.model.AmazonCloudFormationException
import com.amazonaws.services.cloudformation.model.CreateStackRequest
import com.amazonaws.services.cloudformation.model.DeleteStackRequest
import com.amazonaws.services.cloudformation.model.DescribeStackEventsRequest
import com.amazonaws.services.cloudformation.model.DescribeStacksRequest
import com.amazonaws.services.cloudformation.model.Parameter
import com.amazonaws.services.cloudformation.model.Stack
import com.amazonaws.services.simpleworkflow.model.DomainDeprecatedException
import com.amazonaws.services.simpleworkflow.model.TypeDeprecatedException
import com.github.sjones4.youcan.youserv.YouServ
import com.github.sjones4.youcan.youserv.YouServClient
import com.github.sjones4.youcan.youserv.model.DescribeServicesRequest
import com.github.sjones4.youcan.youserv.model.Filter
import org.junit.AfterClass
import org.junit.Assert
import org.junit.BeforeClass
import org.junit.Test

import javax.naming.Context
import javax.naming.directory.Attributes
import javax.naming.directory.DirContext
import javax.naming.directory.InitialDirContext

import static com.eucalyptus.tests.awssdk.N4j.SERVICES_ENDPOINT

/**
 *
 */
class TestCFTemplatesFull {

  private static String testAcct
  private static AWSCredentialsProvider testAcctAdminCredentials
  private static AmazonCloudFormation cfClient

  private static AmazonCloudFormation getCloudFormationClient( final AWSCredentialsProvider credentials ) {
    AmazonCloudFormationClient.builder( )
        .withCredentials( credentials )
        .withEndpointConfiguration( new AwsClientBuilder.EndpointConfiguration( N4j.CF_ENDPOINT, 'eucalyptus' ) )
        .build()
  }

  @BeforeClass
  static void init( ){
    N4j.testInfo( TestCFTemplatesFull.simpleName )
    N4j.getCloudInfo( )
    testAcct = "${N4j.NAME_PREFIX}cf-templates"
    N4j.createAccount( testAcct )
    testAcctAdminCredentials = new AWSStaticCredentialsProvider( N4j.getUserCreds( testAcct, 'admin' ) )
    cfClient = getCloudFormationClient( testAcctAdminCredentials )
  }

  @AfterClass
  static void cleanup( ) {
    if ( cfClient ) cfClient.shutdown( )
    N4j.deleteAccount( testAcct )
  }

  private YouServ getServicesClient(final AWSCredentialsProvider credentials ) {
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

  /**
   * Test for ec2 metadata (no instances)
   */
  @Test
  void testEc2MetadataTemplate( ) {
    stackCreateDelete( 'ec2_meta' )
  }

  /**
   * Test for ec2 cfn-signal
   */
  @Test
  void testEc2SignalResourceTemplate( ) {
    stackCreateDelete( 'ec2_signal_resource', [ ], [ 'ImageId': N4j.IMAGE_ID ] )
  }

  /**
   * Test basic IAM resources
   */
  @Test
  void testIamTemplate( ) {
    stackCreateDelete( 'iam', ['CAPABILITY_IAM'] )
    try {
      stackCreateDelete( 'iam_names' )
      Assert.fail('Expected failure due to missing CAPABILITY_IAM')
    } catch( AmazonCloudFormationException cfe ) {
      N4j.print( "Create failed with exception, verifying error code/status: ${cfe}" )
      Assert.assertEquals('Error code', 'InsufficientCapabilities', cfe.errorCode )
      Assert.assertEquals('Status code', 400, cfe.statusCode )
    }
  }

  /**
   * Test IAM resources with specified names
   */
  @Test
  void testIamNamesTemplate( ) {
    // CAPABILITY_IAM is not needed here but should be allowed
    stackCreateDelete( 'iam_names', ['CAPABILITY_IAM', 'CAPABILITY_NAMED_IAM'] )
    try {
      stackCreateDelete( 'iam_names', ['CAPABILITY_IAM'] )
      Assert.fail('Expected failure due to missing CAPABILITY_NAMED_IAM')
    } catch( AmazonCloudFormationException cfe ) {
      N4j.print( "Create failed with exception, verifying error code/status: ${cfe}" )
      Assert.assertEquals('Error code', 'InsufficientCapabilities', cfe.errorCode )
      Assert.assertEquals('Status code', 400, cfe.statusCode )
    }
  }

  /**
   * Test IAM template with managed policies / policy attachment
   */
  @Test
  void testIamManagedPoliciesTemplate( ) {
    // CAPABILITY_NAMED_IAM not needed but used in place of CAPABILITY_IAM
    //TODO:STEVE: add name support for managed policy resource : "ManagedPolicyName": "managedpolicy1",
    stackCreateDelete( 'iam_managed_policies', ['CAPABILITY_NAMED_IAM'] )
  }

  /**
   * Test for instance with many attached volumes
   */
  @Test
  void testInstanceVolumesTemplate( ) {
    stackCreateDelete( 'instance_volumes', [ ], [ 'ImageId': N4j.IMAGE_ID ] )
  }

  /**
   * Test for s3 buckets
   */
  @Test
  void testS3BucketsTemplate( ) {
    stackCreateDelete( 's3_buckets' )
  }

  /**
   * Test for s3 bucket policy
   */
  @Test
  void testS3BucketPolicyTemplate( ) {
    stackCreateDelete( 's3_bucket_policy' )
  }

  /**
   * Test for sqs resources
   */
  @Test
  void testSQSTemplate( ) {
    stackCreateDelete( 'sqs' )
  }

  /**
   * Test for autoscaling, cloudwatch, and elb
   */
  @Test
  void testTrinityTemplate( ) {
    stackCreateDelete( 'trinity', [ ], [ 'Image': N4j.IMAGE_ID ] ) { Stack stack ->
      String urlText = stack?.outputs?.getAt( 0 )?.outputValue
      Assert.assertNotNull( 'stack url output', urlText )

      URL url = new URL( urlText )
      String balancerHost = url.host

      N4j.print( "Resolving load balancer host ${balancerHost}" )
      Set<String> dnsHosts = getDnsHosts(getServicesClient(testAcctAdminCredentials))
      String balancerIp = null
      ( 1..60 ).find {
        if ( it > 1 ) sleep 5000
        balancerIp = lookup(balancerHost, dnsHosts)
      }
      Assert.assertNotNull('Expected ip for load balancer', balancerIp)
      url = new URL( urlText.replace( balancerHost, balancerIp ) )
      N4j.print( "Resolved load balancer host ${balancerHost} to ${balancerIp}, url is ${url}" )

      ( 1..60 ).find{
        if ( it > 1 ) N4j.sleep 5
        N4j.print( "Attempting request via elb ${it}" )
        try {
          String balancerResponse =
              url.getText( connectTimeout: 1000, readTimeout: 1000, useCaches: false, allowUserInteraction: false )
          Assert.assertTrue(
              "Expected balancer response Hello, but was: ${balancerResponse}",
              'Hello' == balancerResponse)
          balancerResponse
        } catch ( e ) {
          N4j.print( e.toString( ) )
          null
        }
      }
    }
  }

  /**
   * Test for vpc metadata (no instances so good with any network mode)
   */
  @Test
  void testVpcMetadataTemplate( ) {
    stackCreateDelete( 'vpc_meta' )
  }

  private void stackCreateDelete(
      final String stackTemplateId,
      final List<String> capabilities = [ ],
      final Map<String,String> parameters = [:],
      final Closure<Object> createdCallback = null
  ) {
    final String stackName = stackTemplateId.replace('_','-')
    final String stackTemplate =
        TestCFTemplatesFull.getResource( "cf_template_${stackTemplateId}.json" ).getText("utf-8")
    final List<Runnable> cleanupTasks = [] as List<Runnable>
    try {
      cfClient.with{
        N4j.print( "Creating test stack: ${stackName}" )
        String stackId = createStack( new CreateStackRequest(
            stackName: stackName,
            capabilities: capabilities,
            parameters: parameters.collect{ new Parameter( parameterKey: it.key, parameterValue: it.value ) },
            templateBody: stackTemplate
        ) ).stackId
        Assert.assertTrue("Expected stack id", stackId != null)
        N4j.print( "Created stack with id: ${stackId}" )
        cleanupTasks.add{
          N4j.print( "Deleting stack: ${stackName}" )
          deleteStack( new DeleteStackRequest( stackName: stackName ) )
        }

        N4j.print( "Waiting for stack ${stackName} creation" )
        String lastStatus = ''
        ( 1..120 ).find{
          N4j.sleep 5
          N4j.print( "Waiting for stack ${stackName} creation, waited ${it*5}s" )
          describeStacks( new DescribeStacksRequest(
              stackName: stackId
          ) ).with {
            stacks?.getAt( 0 )?.with { Stack stack ->
              if ( stack.stackId == stackId ) {
                lastStatus = stack.stackStatus
                if ( lastStatus == 'CREATE_COMPLETE' ) {
                  lastStatus
                } else if ( lastStatus == 'CREATE_IN_PROGRESS' ) {
                  null
                } else {
                  describeStackEvents( new DescribeStackEventsRequest(
                      stackName: stackId
                  ) ).with {
                    stackEvents.each { event ->
                      N4j.print( event.toString( ) )
                    }
                  }
                  Assert.fail( "Unexpected status ${lastStatus}" )
                  null
                }
              } else {
                Assert.fail( "Unexpected stack ${stack.stackId}" )
                null
              }
            }
          }
        }
        Assert.assertEquals( 'Stack status', 'CREATE_COMPLETE', lastStatus )

        N4j.print( "Stack outputs ${stackName}[${stackId}]" )
        describeStacks( new DescribeStacksRequest(
            stackName: stackId
        ) ).with {
          stacks?.getAt( 0 )?.with { stack ->
            outputs?.each { output ->
              N4j.print( output.toString( ) )
            }
            createdCallback?.call( stack )
          }
        }

        N4j.print( "Deleting stack ${stackName}[${stackId}]" )
        deleteStack( new DeleteStackRequest(
            stackName: stackId
        ) )

        N4j.print( "Waiting for stack ${stackName} deletion" )
        ( 1..120 ).find{
          N4j.sleep 5
          N4j.print( "Waiting for stack ${stackName} deletion, waited ${it*5}s" )
          describeStacks( new DescribeStacksRequest(
              stackName: stackId
          ) ).with {
            stacks?.getAt( 0 )?.with { Stack stack ->
              if ( stack.stackId == stackId ) {
                lastStatus = stack.stackStatus
                if ( lastStatus == 'DELETE_COMPLETE' ) {
                  lastStatus
                } else if ( lastStatus == 'CREATE_COMPLETE' ) {
                  N4j.print( "Retrying delete stack ${stackName}[${stackId}]" )
                  deleteStack( new DeleteStackRequest(
                      stackName: stackId
                  ) )
                  null
                } else if ( lastStatus == 'DELETE_IN_PROGRESS' ) {
                  null
                } else {
                  describeStackEvents( new DescribeStackEventsRequest(
                    stackName: stackId
                  ) ).with {
                    stackEvents.each { event ->
                      N4j.print( event.toString( ) )
                    }
                  }
                  Assert.fail( "Unexpected status ${lastStatus}" )
                  null
                }
              } else {
                Assert.fail( "Unexpected stack ${stack.stackId}" )
                null
              }
            }
          }
        }
        Assert.assertEquals( 'Stack status', 'DELETE_COMPLETE', lastStatus )
      }
    } finally {
      // Attempt to clean up anything we created
      N4j.print( "Running clean up tasks for ${stackName}" )
      cleanupTasks.reverseEach { Runnable cleanupTask ->
        try {
          cleanupTask.run()
        } catch ( DomainDeprecatedException e ) {
          N4j.print( e.message )
        } catch ( TypeDeprecatedException e ) {
          N4j.print( e.message )
        } catch ( Exception e ) {
          e.printStackTrace()
        }
      }
    }
  }
}
