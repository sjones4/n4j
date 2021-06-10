package com.eucalyptus.tests.awssdk

import com.amazonaws.auth.AWSCredentialsProvider
import com.amazonaws.auth.AWSStaticCredentialsProvider
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.client.builder.AwsClientBuilder
import com.amazonaws.services.ec2.AmazonEC2
import com.amazonaws.services.ec2.model.AccountAttribute
import com.amazonaws.services.ec2.model.DescribeSecurityGroupsRequest
import com.amazonaws.services.ec2.model.DescribeSecurityGroupsResult
import com.amazonaws.services.ec2.model.DescribeSubnetsRequest
import com.amazonaws.services.ec2.model.DescribeSubnetsResult
import com.amazonaws.services.ec2.model.Filter
import com.amazonaws.services.elasticloadbalancingv2.AmazonElasticLoadBalancing
import com.amazonaws.services.elasticloadbalancingv2.AmazonElasticLoadBalancingClient
import com.amazonaws.services.elasticloadbalancingv2.model.AddTagsRequest
import com.amazonaws.services.elasticloadbalancingv2.model.CreateLoadBalancerRequest
import com.amazonaws.services.elasticloadbalancingv2.model.CreateTargetGroupRequest
import com.amazonaws.services.elasticloadbalancingv2.model.DeleteLoadBalancerRequest
import com.amazonaws.services.elasticloadbalancingv2.model.DeleteTargetGroupRequest
import com.amazonaws.services.elasticloadbalancingv2.model.DescribeLoadBalancersRequest
import com.amazonaws.services.elasticloadbalancingv2.model.DescribeLoadBalancersResult
import com.amazonaws.services.elasticloadbalancingv2.model.DescribeTagsRequest
import com.amazonaws.services.elasticloadbalancingv2.model.DescribeTargetGroupsRequest
import com.amazonaws.services.elasticloadbalancingv2.model.DescribeTargetGroupsResult
import com.amazonaws.services.elasticloadbalancingv2.model.RemoveTagsRequest
import com.amazonaws.services.elasticloadbalancingv2.model.Tag
import org.junit.BeforeClass
import org.junit.Test

import static com.eucalyptus.tests.awssdk.N4j.ACCESS_KEY
import static com.eucalyptus.tests.awssdk.N4j.SECRET_KEY

/**
 *
 */
class TestELBv2Tagging {

  private static AWSCredentialsProvider credentials

  private static void assumeElbv2Available() {
    N4j.print('Checking for ELBv2 support')
    N4j.assumeThat(N4j.isAtLeastEucalyptusVersion('6.0.0'), 'ELBv2 available')
    N4j.print('Checking for VPC support')
    final boolean vpcAvailable = N4j.ec2.describeAccountAttributes().with {
      accountAttributes.find { AccountAttribute accountAttribute ->
        accountAttribute.attributeName == 'supported-platforms'
      }?.attributeValues*.attributeValue.contains('VPC')
    }
    N4j.print("VPC supported: ${vpcAvailable}")
    N4j.assumeThat(vpcAvailable, 'VPC is a supported platform')
  }

  @BeforeClass
  static void init() {
    N4j.minimalInit()
    credentials = new AWSStaticCredentialsProvider(new BasicAWSCredentials(ACCESS_KEY, SECRET_KEY))
    assumeElbv2Available()
  }

  private AmazonElasticLoadBalancing getELBClient(final AWSCredentialsProvider credentials) {
    AmazonElasticLoadBalancingClient.
            builder().
            withEndpointConfiguration(
                    new AwsClientBuilder.EndpointConfiguration(N4j.ELB_ENDPOINT, 'eucalyptus')).
            withCredentials(credentials).
            build()
  }

  private void assertThat(boolean condition, String message) {
    N4j.assertThat(condition, message)
  }

  private void print(String text) {
    N4j.print(text)
  }

  @Test
  void testLoadBalancerTags() {
    final AmazonEC2 ec2 = N4j.getEc2Client(credentials, N4j.EC2_ENDPOINT)

    // Find an AZ to use
    final DescribeSubnetsResult subnetsResult = ec2.describeSubnets(new DescribeSubnetsRequest(
            filters: [new Filter(name: 'default-for-az', values: ['true'])]
    ))
    assertThat(subnetsResult.getSubnets().size() > 0, "Subnet not found")

    final String vpcId = subnetsResult.getSubnets().get(0).getVpcId()
    final String subnetId = subnetsResult.getSubnets().get(0).getSubnetId()
    final String availabilityZone = subnetsResult.getSubnets().get(0).getAvailabilityZone()
    print("Using vpc              : " + vpcId)
    print("Using subnet           : " + subnetId)
    print("Using availability zone: " + availabilityZone)

    // Find a security group to use
    final DescribeSecurityGroupsResult groupsResult = ec2.
            describeSecurityGroups(new DescribeSecurityGroupsRequest(
                    filters: [
                            new Filter(name: 'vpc-id', values: [vpcId]),
                            new Filter(name: 'group-name', values: ['default'])
                    ]
            ))
    assertThat(groupsResult.getSecurityGroups().size() > 0, "Security group not found for vpc")
    final String securityGroupId = groupsResult.getSecurityGroups().get(0).getGroupId()
    print("Using security group: " + securityGroupId)

    final String namePrefix = UUID.randomUUID().toString().substring(0, 13) + "-"
    print("Using resource prefix for test: " + namePrefix)

    final AmazonElasticLoadBalancing elb = getELBClient(credentials)
    final List<Runnable> cleanupTasks = [] as List<Runnable>
    try {
      elb.with {
        String loadBalancerName = "${namePrefix}balancer-1"
        print("Creating load balancer: ${loadBalancerName}")
        final String loadBalancerArn = createLoadBalancer(new CreateLoadBalancerRequest(
                name: loadBalancerName,
                securityGroups: [securityGroupId],
                subnets: [subnetId],
                tags: [
                        new Tag(key: 'one', value: '1'),
                        new Tag(key: 'two', value: '2'),
                        new Tag(key: 'three', value: '3'),
                ]
        )).with {
          loadBalancers?.getAt(0)?.loadBalancerArn
        }
        print("Created load balancer: ${loadBalancerArn}")
        cleanupTasks.add {
          print("Deleting load balancer: ${loadBalancerName}")
          deleteLoadBalancer(new DeleteLoadBalancerRequest(loadBalancerArn: loadBalancerArn))
        }

        final DescribeLoadBalancersResult loadBalancersResult = describeLoadBalancers(
                new DescribeLoadBalancersRequest(
                        loadBalancerArns: [loadBalancerArn]
                ))
        print(loadBalancersResult.toString())

        print("Describing tags for load balancer: ${loadBalancerArn}")
        describeTags(new DescribeTagsRequest(resourceArns: [loadBalancerArn])).with {
          println(tagDescriptions.toString())
          assertThat(tagDescriptions.size() == 1,
                  "Expected one load balancer, but was: ${tagDescriptions.size()}")
          tagDescriptions.get(0).with {
            assertThat(loadBalancerArn == getResourceArn(),
                    "Unexpected resource arn: ${getResourceArn()}")
            assertThat(tags.size() == 3, "Expected three tags, but was: ${tags.size()}")
          }
        }

        print("Adding tags for load balancer: ${loadBalancerArn}")
        addTags(new AddTagsRequest(
                resourceArns: [loadBalancerArn],
                tags: [
                        new Tag(key: 'four', value: '4'),
                        new Tag(key: 'five', value: '5'),
                ]
        ))

        print("Describing tags for load balancer: ${loadBalancerArn}")
        describeTags(new DescribeTagsRequest(resourceArns: [loadBalancerArn])).with {
          println(tagDescriptions.toString())
          assertThat(tagDescriptions.size() == 1,
                  "Expected one load balancer, but was: ${tagDescriptions.size()}")
          tagDescriptions.get(0).with {
            assertThat(loadBalancerArn == getResourceArn(),
                    "Unexpected resource arn: ${getResourceArn()}")
            assertThat(tags.size() == 5, "Expected five tags, but was: ${tags.size()}")
          }
        }

        print("Removing tags for load balancer: ${loadBalancerArn}")
        removeTags(new RemoveTagsRequest(
                resourceArns: [loadBalancerArn],
                tagKeys: ['one', 'three', 'five']
        ))

        print("Describing tags for load balancer: ${loadBalancerArn}")
        describeTags(new DescribeTagsRequest(resourceArns: [loadBalancerArn])).with {
          println(tagDescriptions.toString())
          assertThat(tagDescriptions.size() == 1,
                  "Expected one load balancer, but was: ${tagDescriptions.size()}")
          tagDescriptions.get(0).with {
            assertThat(loadBalancerArn == getResourceArn(),
                    "Unexpected resource arn: ${getResourceArn()}")
            assertThat(tags.size() == 2, "Expected two tags, but was: ${tags.size()}")
          }
        }
      }

      print("Test complete")
    } finally {
      // Attempt to clean up anything we created
      cleanupTasks.reverseEach { Runnable cleanupTask ->
        try {
          cleanupTask.run()
        } catch (Exception e) {
          e.printStackTrace()
        }
      }
    }
  }

  @Test
  void testTargetGroupTags() {
    final AmazonEC2 ec2 = N4j.getEc2Client(credentials, N4j.EC2_ENDPOINT)

    // Find a VPC to use
    final DescribeSubnetsResult subnetsResult = ec2.describeSubnets(new DescribeSubnetsRequest(
            filters: [new Filter(name: 'default-for-az', values: ['true'])]
    ))
    assertThat(subnetsResult.getSubnets().size() > 0, "Subnet not found")

    final String vpcId = subnetsResult.getSubnets().get(0).getVpcId()
    print("Using vpc: " + vpcId)

    final String namePrefix = UUID.randomUUID().toString().substring(0, 13) + "-"
    print("Using resource prefix for test: " + namePrefix)

    final AmazonElasticLoadBalancing elb = getELBClient(credentials)
    final List<Runnable> cleanupTasks = [] as List<Runnable>
    try {
      elb.with {
        String targetGroupName = "${namePrefix}target-group-1"
        print("Creating target group: ${targetGroupName}")
        final String targetGroupArn = createTargetGroup(new CreateTargetGroupRequest(
                name: targetGroupName,
                protocol: 'HTTP',
                port: 80,
                vpcId: vpcId,
                tags: [
                        new Tag(key: 'one', value: '1'),
                        new Tag(key: 'two', value: '2'),
                        new Tag(key: 'three', value: '3'),
                ]
        )).with {
          targetGroups?.getAt(0)?.targetGroupArn
        }
        print("Created target group: ${targetGroupArn}")
        cleanupTasks.add {
          print("Deleting target group: ${targetGroupName}")
          deleteTargetGroup(new DeleteTargetGroupRequest(targetGroupArn: targetGroupArn))
        }

        final DescribeTargetGroupsResult targetGroupsResult = describeTargetGroups(
                new DescribeTargetGroupsRequest(
                        targetGroupArns: [targetGroupArn]
                ))
        print(targetGroupsResult.toString())

        print("Describing tags for target group: ${targetGroupArn}")
        describeTags(new DescribeTagsRequest(resourceArns: [targetGroupArn])).with {
          println(tagDescriptions.toString())
          assertThat(tagDescriptions.size() == 1,
                  "Expected one target group, but was: ${tagDescriptions.size()}")
          tagDescriptions.get(0).with {
            assertThat(targetGroupArn == getResourceArn(),
                    "Unexpected resource arn: ${getResourceArn()}")
            assertThat(tags.size() == 3, "Expected three tags, but was: ${tags.size()}")
          }
        }

        print("Adding tags for target group: ${targetGroupArn}")
        addTags(new AddTagsRequest(
                resourceArns: [targetGroupArn],
                tags: [
                        new Tag(key: 'four', value: '4'),
                        new Tag(key: 'five', value: '5'),
                ]
        ))

        print("Describing tags for target group: ${targetGroupArn}")
        describeTags(new DescribeTagsRequest(resourceArns: [targetGroupArn])).with {
          println(tagDescriptions.toString())
          assertThat(tagDescriptions.size() == 1,
                  "Expected one target group, but was: ${tagDescriptions.size()}")
          tagDescriptions.get(0).with {
            assertThat(targetGroupArn == getResourceArn(),
                    "Unexpected resource arn: ${getResourceArn()}")
            assertThat(tags.size() == 5, "Expected five tags, but was: ${tags.size()}")
          }
        }

        print("Removing tags for target group: ${targetGroupArn}")
        removeTags(new RemoveTagsRequest(
                resourceArns: [targetGroupArn],
                tagKeys: ['one', 'three', 'five']
        ))

        print("Describing tags for target group: ${targetGroupArn}")
        describeTags(new DescribeTagsRequest(resourceArns: [targetGroupArn])).with {
          println(tagDescriptions.toString())
          assertThat(tagDescriptions.size() == 1,
                  "Expected one target group, but was: ${tagDescriptions.size()}")
          tagDescriptions.get(0).with {
            assertThat(targetGroupArn == getResourceArn(),
                    "Unexpected resource arn: ${getResourceArn()}")
            assertThat(tags.size() == 2, "Expected two tags, but was: ${tags.size()}")
          }
        }
      }

      print("Test complete")
    } finally {
      // Attempt to clean up anything we created
      cleanupTasks.reverseEach { Runnable cleanupTask ->
        try {
          cleanupTask.run()
        } catch (Exception e) {
          e.printStackTrace()
        }
      }
    }
  }
}
