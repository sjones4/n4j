package com.eucalyptus.tests.awssdk

import com.amazonaws.AmazonServiceException
import com.amazonaws.auth.AWSCredentialsProvider
import com.amazonaws.internal.StaticCredentialsProvider
import com.amazonaws.services.elasticloadbalancing.AmazonElasticLoadBalancing
import com.amazonaws.services.elasticloadbalancing.AmazonElasticLoadBalancingClient
import com.amazonaws.services.elasticloadbalancing.model.*
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Test

import static com.eucalyptus.tests.awssdk.N4j.ELB_ENDPOINT
import static com.eucalyptus.tests.awssdk.N4j.NAME_PREFIX


/**
 * ELB API basic test.
 *
 * - tests delete success on invalid elb name
 * - tests describe of example policies
 */
class TestELBApi {
  private static String testAcct
  private static AWSCredentialsProvider testAcctAdminCredentials

  @BeforeClass
  static void init( ){
    N4j.getCloudInfo( )
    this.testAcct= "${NAME_PREFIX}test-acct"
    N4j.createAccount(testAcct)
    this.testAcctAdminCredentials = new StaticCredentialsProvider( N4j.getUserCreds(testAcct, 'admin') )
  }

  @AfterClass
  static void cleanup( ) {
    N4j.deleteAccount(testAcct)
  }

  private AmazonElasticLoadBalancing getELBClient( final AWSCredentialsProvider credentials ) {
    final AmazonElasticLoadBalancing elb = new AmazonElasticLoadBalancingClient( credentials )
    elb.setEndpoint( ELB_ENDPOINT )
    elb
  }

  @Test
  void testElbApi( ) throws Exception {
    final AmazonElasticLoadBalancing elb = getELBClient( testAcctAdminCredentials )
    final String elbInvalidName = 'invalid name for a load balancer'
    final List<Runnable> cleanupTasks = [] as List<Runnable>
    try {
      elb.with {
        N4j.print( 'Describing load balancers' )
        N4j.print( describeLoadBalancers( ).toString( ) )

        N4j.print( 'Describing load balancer tags' )
        try {
          N4j.print( describeTags( new DescribeTagsRequest( loadBalancerNames: [ elbInvalidName ] ) ).toString( ) )
        } catch ( AmazonServiceException e ) {
          N4j.print( "Got error for request with invalid name: ${e}" )
        }

        N4j.print( 'Describing load balancer attributes' )
        try {
          describeLoadBalancerAttributes( new DescribeLoadBalancerAttributesRequest(
              loadBalancerName: elbInvalidName
          ) )
        } catch ( AmazonServiceException e ) {
          N4j.print( "Got error for request with invalid name: ${e}" )
        }

        N4j.print( 'Deleting invalid load balancer' )
        deleteLoadBalancer( new DeleteLoadBalancerRequest(
            loadBalancerName: elbInvalidName
        ))

        N4j.print( 'Describing policy types' )
        describeLoadBalancerPolicyTypes( ).with {
          N4j.print( it.toString( ) )
          N4j.assertThat( policyTypeDescriptions != null, 'Expected policy type descriptions' )
          N4j.assertThat( !policyTypeDescriptions.isEmpty( ), "Expected policy type descriptions but was empty" )
          policyTypeDescriptions.each {
            N4j.assertThat( it.policyTypeName != null, 'Expected policy type name' )
            N4j.assertThat( it.description != null, 'Expected description' )
          }
        }

        N4j.print( 'Describing load balancer policies' )
        describeLoadBalancerPolicies( new DescribeLoadBalancerPoliciesRequest( ) ).with {
          N4j.print( it.toString( ) )
          N4j.assertThat( policyDescriptions != null, 'Expected policy descriptions' )
          N4j.assertThat( !policyDescriptions.isEmpty( ), "Expected policy descriptions but was empty" )
          policyDescriptions.each {
            N4j.assertThat( it.policyName != null, 'Expected policy name' )
            N4j.assertThat( it.policyName.startsWith( 'ELBSample-' ) || it.policyName.startsWith( 'ELBSecurityPolicy-' ),
                "Expected sample policy name to start with 'ELBSample-' or 'ELBSecurityPolicy-', but was ${it.policyName}" )
          }
        }

        void
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
