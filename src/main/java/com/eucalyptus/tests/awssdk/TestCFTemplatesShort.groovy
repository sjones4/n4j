package com.eucalyptus.tests.awssdk

import com.amazonaws.auth.AWSCredentialsProvider
import com.amazonaws.internal.StaticCredentialsProvider
import com.amazonaws.services.cloudformation.AmazonCloudFormationClient
import com.amazonaws.services.cloudformation.model.CreateStackRequest
import com.amazonaws.services.cloudformation.model.DeleteStackRequest
import com.amazonaws.services.cloudformation.model.DescribeStackEventsRequest
import com.amazonaws.services.cloudformation.model.DescribeStacksRequest
import com.amazonaws.services.cloudformation.model.Parameter
import com.amazonaws.services.cloudformation.model.Stack
import com.amazonaws.services.simpleworkflow.model.DomainDeprecatedException
import com.amazonaws.services.simpleworkflow.model.TypeDeprecatedException
import org.junit.AfterClass
import org.junit.Assert
import org.junit.BeforeClass
import org.junit.Test

/**
 *
 */
class TestCFTemplatesShort {

  private static String testAcct
  private static AWSCredentialsProvider testAcctAdminCredentials
  private static AmazonCloudFormationClient cfClient

  private static AmazonCloudFormationClient getCloudFormationClient( final AWSCredentialsProvider credentials ) {
    final AmazonCloudFormationClient cf = new AmazonCloudFormationClient( credentials )
    cf.setEndpoint( N4j.CF_ENDPOINT )
    cf
  }

  @BeforeClass
  static void init( ){
    N4j.testInfo( TestCFTemplatesShort.simpleName )
    N4j.getCloudInfo( )
    testAcct = "${N4j.NAME_PREFIX}cf-templates"
    N4j.createAccount( testAcct )
    testAcctAdminCredentials = new StaticCredentialsProvider( N4j.getUserCreds( testAcct, 'admin' ) )
    cfClient = getCloudFormationClient( testAcctAdminCredentials )
  }

  @AfterClass
  static void cleanup( ) {
    if ( cfClient ) cfClient.shutdown( )
    N4j.deleteAccount( testAcct )
  }

  @Test
  void testAutoScalingTemplate( ) {
    String asTemplate = '''\
    {
      "AWSTemplateFormatVersion" : "2010-09-09",
      "Description" : "Auto scaling group template",
      "Parameters" : {
        "InstanceType" : {
          "Description" : "EC2 instance type",
          "Type" : "String",
          "Default" : "m1.small",
          "AllowedValues" : [ "t1.micro", "m1.small", "m1.medium" ],
          "ConstraintDescription" : "must be a valid EC2 instance type."
        },
        "Image" : {
          "Description" : "EC2 image",
          "Type" : "String",
          "AllowedPattern" : "[ae]mi-[0-9a-fA-F]{8}",
          "ConstraintDescription" : "must be a valid EC2 image identifier."
        }
      },
      "Resources" : {
        "SecurityGroup" : {
          "Type" : "AWS::EC2::SecurityGroup",
          "Properties" : {
            "GroupDescription" : "Security group",
            "SecurityGroupIngress" : [
              { "IpProtocol" : "tcp", "FromPort" : "22", "ToPort" : "22" }
            ],
            "Tags": [ {"Key" : "Application", "Value" : { "Ref" : "AWS::StackId"}} ]
          }
        },
        "LaunchConfiguration"  : {
          "Type" : "AWS::AutoScaling::LaunchConfiguration",
          "Properties" : {
            "ImageId"        : { "Ref" : "Image" },
            "SecurityGroups" : [ { "Ref" : "SecurityGroup" } ],
            "InstanceType"   : { "Ref" : "InstanceType" }
          }
        },
        "AutoScalingGroup" : {
          "Type" : "AWS::AutoScaling::AutoScalingGroup",
          "Properties" : {
            "AvailabilityZones" : [ {
              "Fn::Select" : [ "0", { "Fn::GetAZs" : "" } ]
            } ],
            "LaunchConfigurationName" : { "Ref" : "LaunchConfiguration"  },
            "MinSize" : "0",
            "MaxSize" : "0",
            "DesiredCapacity" : "0",
            "Tags": [
                { "Key": "key-1", "Value": "value-1", "PropagateAtLaunch": true },
                { "Key": "key-2", "Value": "value-2", "PropagateAtLaunch": false }
            ]
          }
        }
      }
    }
    '''.stripIndent( )
    stackCreateDelete( 'as-stack', asTemplate, [ ], [ 'Image': N4j.IMAGE_ID ] )
  }

  @Test
  void testCloudWatchTemplate( ) {
    String cwTemplate = '''\
    {
      "AWSTemplateFormatVersion": "2010-09-09",
      "Resources": {
        "Alarm": {
          "Type": "AWS::CloudWatch::Alarm",
          "Properties" : {
            "AlarmName" : "alarm",
            "AlarmDescription" : "description",
            "AlarmActions" : [ "arn:aws:automate:eucalyptus:ec2:terminate" ],
            "MetricName" : "CPUUtilization",
            "Namespace" : "EC2",
            "ComparisonOperator" : "GreaterThanOrEqualToThreshold",
            "EvaluationPeriods" : "2",
            "Period" : "300",
            "Statistic" : "Average",
            "Threshold" : "90",
            "Dimensions": [ {
                "Name": "InstanceId",
                "Value": "i-00000000"
            } ]
          }
        }
      }
    }
    '''.stripIndent( )
    stackCreateDelete( 'cw-stack', cwTemplate )
  }

  @Test
  void testEc2Template( ) {
    String ec2Template = '''\
    {
      "AWSTemplateFormatVersion": "2010-09-09",
      "Resources": {
        "Address": {
          "Type": "AWS::EC2::EIP"
        },
        "SecurityGroup": {
          "Type": "AWS::EC2::SecurityGroup",
          "Properties": {
            "GroupDescription": "description"
          }
        },
        "SecurityGroupIngress": {
          "Type": "AWS::EC2::SecurityGroupIngress",
          "Properties": {
            "CidrIp": "0.0.0.0/0",
            "FromPort": "22",
            "ToPort": "22",
            "GroupId": {
              "Fn::GetAtt": [ "SecurityGroup", "GroupId" ]
            },
            "IpProtocol": "tcp"
          }
        }
      }
    }
    '''.stripIndent( )
    stackCreateDelete( 'ec2-stack', ec2Template )
  }

  @Test
  void testIamTemplate( ) {
    String iamTemplate = '''\
    {
      "AWSTemplateFormatVersion" : "2010-09-09",
      "Resources" : {
        "Group" : {
          "Type" : "AWS::IAM::Group"
        },
        "User" : {
          "Type" : "AWS::IAM::User"
        },
        "GroupToUsers": {
          "Type": "AWS::IAM::UserToGroupAddition",
          "Properties": {
            "GroupName": {
              "Ref": "Group"
            },
            "Users": [ {
              "Ref": "User"
            } ]
          }
        },
        "Policy" : {
          "Type" : "AWS::IAM::Policy",
          "Properties" : {
            "PolicyName" : "policy",
            "PolicyDocument" : {
              "Statement": [
                {
                  "Effect": "Allow",
                  "Action": "*",
                  "Resource": "*"
                }
              ]
            },
            "Users" : [ {
              "Ref": "User"
            } ]
          }
        }
      }
    }
    '''.stripIndent( )
    stackCreateDelete( 'iam-stack', iamTemplate, [ 'CAPABILITY_IAM' ] )
  }

  @Test
  void testS3Template( ) {
    String s3Template = '''\
    {
        "AWSTemplateFormatVersion" : "2010-09-09",
        "Description" : "S3 bucket template.",
        "Resources" : {
            "bucket" : {
                "Type" : "AWS::S3::Bucket",
                "Properties" : {
                    "BucketName" : "bucket-1"
                }
            }
        }
    }
    '''.stripIndent( )
    stackCreateDelete( 's3-bucket-stack', s3Template )
  }

  @Test
  void testS3YamlTemplate( ) {
    String s3Template = '''\
    AWSTemplateFormatVersion: 2010-09-09
    Description: S3 bucket template.
    Resources:
      bucket:
        Type: AWS::S3::Bucket
        Properties:
          BucketName: bucket-2
    '''.stripIndent( )
    stackCreateDelete( 's3-bucket-yaml-stack', s3Template )
  }

  private void stackCreateDelete(
      final String stackName,
      final String stackTemplate,
      final List<String> capabilities = [ ],
      final Map<String,String> parameters = [:]
  ) {
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
        ( 1..25 ).find{
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
                  N4j.print( "Unexpected status ${lastStatus}, dumping stack events" )
                  describeStackEvents( new DescribeStackEventsRequest( stackName: stackId ) ).with {
                    stackEvents?.each {
                      N4j.print( it.toString( ) )
                    }
                  }
                  Assert.fail( "Unexpected status ${lastStatus}" )
                }
              }
            }
          }
        }
        Assert.assertEquals( 'Stack status', 'CREATE_COMPLETE', lastStatus )

        N4j.print( "Deleting stack ${stackName}[${stackId}]" )
        deleteStack( new DeleteStackRequest(
          stackName: stackId
        ) )

        N4j.print( "Waiting for stack ${stackName} deletion" )
        ( 1..25 ).find{
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
                } else if ( lastStatus == 'DELETE_IN_PROGRESS' || lastStatus == 'CREATE_COMPLETE' ) {
                  null
                } else {
                  Assert.fail( "Unexpected status ${lastStatus}" )
                }
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
