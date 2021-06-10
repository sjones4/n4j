package com.eucalyptus.tests.awssdk

import com.amazonaws.AmazonServiceException
import com.amazonaws.auth.AWSCredentialsProvider
import com.amazonaws.auth.AWSStaticCredentialsProvider
import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration
import com.amazonaws.services.ec2.model.AccountAttribute
import com.amazonaws.services.elasticloadbalancingv2.AmazonElasticLoadBalancing
import com.amazonaws.services.elasticloadbalancingv2.AmazonElasticLoadBalancingClient
import com.amazonaws.services.elasticloadbalancingv2.model.DeleteLoadBalancerRequest
import com.amazonaws.services.elasticloadbalancingv2.model.DeleteTargetGroupRequest
import com.amazonaws.services.elasticloadbalancingv2.model.DescribeAccountLimitsRequest
import com.amazonaws.services.elasticloadbalancingv2.model.DescribeListenersRequest
import com.amazonaws.services.elasticloadbalancingv2.model.DescribeLoadBalancerAttributesRequest
import com.amazonaws.services.elasticloadbalancingv2.model.DescribeLoadBalancersRequest
import com.amazonaws.services.elasticloadbalancingv2.model.DescribeRulesRequest
import com.amazonaws.services.elasticloadbalancingv2.model.DescribeSSLPoliciesRequest
import com.amazonaws.services.elasticloadbalancingv2.model.DescribeTagsRequest
import com.amazonaws.services.elasticloadbalancingv2.model.DescribeTargetGroupAttributesRequest
import com.amazonaws.services.elasticloadbalancingv2.model.DescribeTargetGroupsRequest
import com.amazonaws.services.elasticloadbalancingv2.model.DescribeTargetHealthRequest
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Test

/**
 * ELBv2 API basic test
 */
class TestELBv2Api {
  private static String testAcct
  private static AWSCredentialsProvider testAcctAdminCredentials

  private static void assumeElbv2Available( ) {
    N4j.print( 'Checking for ELBv2 support' )
    N4j.assumeThat( N4j.isAtLeastEucalyptusVersion('6.0.0'), 'ELBv2 available' )
    N4j.print( 'Checking for VPC support' )
    final boolean vpcAvailable = N4j.ec2.describeAccountAttributes( ).with {
      accountAttributes.find{ AccountAttribute accountAttribute ->
        accountAttribute.attributeName == 'supported-platforms'
      }?.attributeValues*.attributeValue.contains( 'VPC' )
    }
    N4j.print( "VPC supported: ${vpcAvailable}" )
    N4j.assumeThat( vpcAvailable, 'VPC is a supported platform' )
  }

  @BeforeClass
  static void init( ){
    N4j.getCloudInfo( )
    testAcct= "${N4j.NAME_PREFIX}elbv2-test-acct"
    N4j.createAccount(testAcct)
    testAcctAdminCredentials = new AWSStaticCredentialsProvider( N4j.getUserCreds(testAcct, 'admin') )
    assumeElbv2Available( )
  }

  @AfterClass
  static void cleanup( ) {
    N4j.deleteAccount(testAcct)
  }

  private AmazonElasticLoadBalancing getELBClient( final AWSCredentialsProvider credentials ) {
    AmazonElasticLoadBalancingClient.builder()
            .withEndpointConfiguration( new EndpointConfiguration( N4j.ELB_ENDPOINT, 'eucalyptus' ) )
            .withCredentials( credentials )
            .build()
  }

  @Test
  void testElbApi( ) throws Exception {
    final AmazonElasticLoadBalancing elb = getELBClient( testAcctAdminCredentials )
    final String invalidArn = 'invalid arn'
    final List<Runnable> cleanupTasks = [] as List<Runnable>
    try {
      elb.with {
        N4j.print( 'Describing load balancers' )
        N4j.print( describeLoadBalancers( new DescribeLoadBalancersRequest() ).toString( ) )

        N4j.print( 'Describing load balancer attributes' )
        try {
          describeLoadBalancerAttributes( new DescribeLoadBalancerAttributesRequest(
              loadBalancerArn: invalidArn
          ) )
        } catch ( AmazonServiceException e ) {
          N4j.print( "Got error for request with invalid arn: ${e}" )
        }

        N4j.print( 'Deleting invalid load balancer' )
        try {
          deleteLoadBalancer( new DeleteLoadBalancerRequest(
              loadBalancerArn: invalidArn
          ))
        } catch ( AmazonServiceException e ) {
          N4j.print( "Got error for request with invalid arn: ${e}" )
        }

        N4j.print( 'Describing target groups' )
        N4j.print( describeTargetGroups( new DescribeTargetGroupsRequest() ).toString( ) )

        N4j.print( 'Describing target group attributes' )
        try {
          describeTargetGroupAttributes( new DescribeTargetGroupAttributesRequest(
              targetGroupArn: invalidArn
          ) )
        } catch ( AmazonServiceException e ) {
          N4j.print( "Got error for request with invalid arn: ${e}" )
        }

        N4j.print( 'Describing target health' )
        try {
          describeTargetHealth( new DescribeTargetHealthRequest(
                  targetGroupArn: invalidArn
          ) )
        } catch ( AmazonServiceException e ) {
          N4j.print( "Got error for request with invalid arn: ${e}" )
        }

        N4j.print( 'Deleting invalid target group' )
        try {
          deleteTargetGroup( new DeleteTargetGroupRequest(
              targetGroupArn: invalidArn
          ))
        } catch ( AmazonServiceException e ) {
          N4j.print( "Got error for request with invalid arn: ${e}" )
        }

        N4j.print( 'Describing load balancer listeners' )
        try {
          describeListeners( new DescribeListenersRequest(
                  loadBalancerArn: invalidArn
          ))
        } catch ( AmazonServiceException e ) {
          N4j.print( "Got error for request with invalid arn: ${e}" )
        }

        N4j.print( 'Describing load balancer listener rules' )
        try {
          describeRules( new DescribeRulesRequest(
                  listenerArn: invalidArn
          ))
        } catch ( AmazonServiceException e ) {
          N4j.print( "Got error for request with invalid arn: ${e}" )
        }

        N4j.print( 'Describing tags' )
        try {
          N4j.print( describeTags( new DescribeTagsRequest( resourceArns: [ invalidArn ] ) ).toString( ) )
        } catch ( AmazonServiceException e ) {
          N4j.print( "Got error for request with invalid arn: ${e}" )
        }

        N4j.print( 'Describing ssl policies' )
        N4j.print( describeSSLPolicies( new DescribeSSLPoliciesRequest( ) ).toString( ) )

        N4j.print( 'Describing account limits' )
        N4j.print( describeAccountLimits( new DescribeAccountLimitsRequest( ) ).toString( ) )

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
