package com.eucalyptus.tests.load

import com.amazonaws.ClientConfiguration
import com.amazonaws.auth.AWSCredentialsProvider
import com.amazonaws.auth.AWSStaticCredentialsProvider
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.services.cloudformation.AmazonCloudFormationClient
import com.amazonaws.services.cloudformation.model.CreateStackRequest
import com.amazonaws.services.cloudformation.model.DeleteStackRequest
import com.amazonaws.services.cloudformation.model.DescribeStacksRequest
import com.eucalyptus.tests.awssdk.N4j
import org.junit.AfterClass
import org.junit.Assert
import org.junit.BeforeClass
import org.junit.Test

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

import static com.eucalyptus.tests.awssdk.N4j.ACCESS_KEY
import static com.eucalyptus.tests.awssdk.N4j.SECRET_KEY
import static org.junit.Assert.fail

/**
 *
 */
class StackChurnLoadTest {

  private final String TEMPLATE = '''\
  {
    "AWSTemplateFormatVersion": "2010-09-09",
    "Description": "Scale test stack with 10 security groups and rules",
    "Resources": {
      "ServerSecurityGroup1": {
        "Type": "AWS::EC2::SecurityGroup",
        "Properties": {
          "GroupDescription": " scale test group",
          "SecurityGroupIngress": [
            {
              "IpProtocol": "tcp",
              "FromPort": "80",
              "ToPort": "80",
              "CidrIp": "0.0.0.0/0"
            },
            {
              "IpProtocol": "tcp",
              "FromPort": "22",
              "ToPort": "22",
              "CidrIp": "192.168.1.1/32"
            }
          ]
        }
      },
      "ServerSecurityGroup2": {
        "Type": "AWS::EC2::SecurityGroup",
        "Properties": {
          "GroupDescription": " scale test group",
          "SecurityGroupIngress": [
            {
              "IpProtocol": "tcp",
              "FromPort": "80",
              "ToPort": "80",
              "CidrIp": "0.0.0.0/0"
            },
            {
              "IpProtocol": "tcp",
              "FromPort": "22",
              "ToPort": "22",
              "CidrIp": "192.168.1.1/32"
            }
          ]
        }
      },
      "ServerSecurityGroup3": {
        "Type": "AWS::EC2::SecurityGroup",
        "Properties": {
          "GroupDescription": " scale test group",
          "SecurityGroupIngress": [
            {
              "IpProtocol": "tcp",
              "FromPort": "80",
              "ToPort": "80",
              "CidrIp": "0.0.0.0/0"
            },
            {
              "IpProtocol": "tcp",
              "FromPort": "22",
              "ToPort": "22",
              "CidrIp": "192.168.1.1/32"
            }
          ]
        }
      },
      "ServerSecurityGroup4": {
        "Type": "AWS::EC2::SecurityGroup",
        "Properties": {
          "GroupDescription": " scale test group",
          "SecurityGroupIngress": [
            {
              "IpProtocol": "tcp",
              "FromPort": "80",
              "ToPort": "80",
              "CidrIp": "0.0.0.0/0"
            },
            {
              "IpProtocol": "tcp",
              "FromPort": "22",
              "ToPort": "22",
              "CidrIp": "192.168.1.1/32"
            }
          ]
        }
      },
      "ServerSecurityGroup5": {
        "Type": "AWS::EC2::SecurityGroup",
        "Properties": {
          "GroupDescription": " scale test group",
          "SecurityGroupIngress": [
            {
              "IpProtocol": "tcp",
              "FromPort": "80",
              "ToPort": "80",
              "CidrIp": "0.0.0.0/0"
            },
            {
              "IpProtocol": "tcp",
              "FromPort": "22",
              "ToPort": "22",
              "CidrIp": "192.168.1.1/32"
            }
          ]
        }
      },
      "ServerSecurityGroup6": {
        "Type": "AWS::EC2::SecurityGroup",
        "Properties": {
          "GroupDescription": " scale test group",
          "SecurityGroupIngress": [
            {
              "IpProtocol": "tcp",
              "FromPort": "80",
              "ToPort": "80",
              "CidrIp": "0.0.0.0/0"
            },
            {
              "IpProtocol": "tcp",
              "FromPort": "22",
              "ToPort": "22",
              "CidrIp": "192.168.1.1/32"
            }
          ]
        }
      },
      "ServerSecurityGroup7": {
        "Type": "AWS::EC2::SecurityGroup",
        "Properties": {
          "GroupDescription": " scale test group",
          "SecurityGroupIngress": [
            {
              "IpProtocol": "tcp",
              "FromPort": "80",
              "ToPort": "80",
              "CidrIp": "0.0.0.0/0"
            },
            {
              "IpProtocol": "tcp",
              "FromPort": "22",
              "ToPort": "22",
              "CidrIp": "192.168.1.1/32"
            }
          ]
        }
      },
      "ServerSecurityGroup8": {
        "Type": "AWS::EC2::SecurityGroup",
        "Properties": {
          "GroupDescription": " scale test group",
          "SecurityGroupIngress": [
            {
              "IpProtocol": "tcp",
              "FromPort": "80",
              "ToPort": "80",
              "CidrIp": "0.0.0.0/0"
            },
            {
              "IpProtocol": "tcp",
              "FromPort": "22",
              "ToPort": "22",
              "CidrIp": "192.168.1.1/32"
            }
          ]
        }
      },
      "ServerSecurityGroup9": {
        "Type": "AWS::EC2::SecurityGroup",
        "Properties": {
          "GroupDescription": " scale test group",
          "SecurityGroupIngress": [
            {
              "IpProtocol": "tcp",
              "FromPort": "80",
              "ToPort": "80",
              "CidrIp": "0.0.0.0/0"
            },
            {
              "IpProtocol": "tcp",
              "FromPort": "22",
              "ToPort": "22",
              "CidrIp": "192.168.1.1/32"
            }
          ]
        }
      },
      "ServerSecurityGroup10": {
        "Type": "AWS::EC2::SecurityGroup",
        "Properties": {
          "GroupDescription": " scale test group",
          "SecurityGroupIngress": [
            {
              "IpProtocol": "tcp",
              "FromPort": "80",
              "ToPort": "80",
              "CidrIp": "0.0.0.0/0"
            },
            {
              "IpProtocol": "tcp",
              "FromPort": "22",
              "ToPort": "22",
              "CidrIp": "192.168.1.1/32"
            }
          ]
        }
      }
    }
  }
  '''.stripIndent( )

  private static String testAcct
  private static AWSCredentialsProvider testAcctAdminCredentials
  private static AWSCredentialsProvider cloudAdminCredentials

  @BeforeClass
  static void init( ){
    N4j.testInfo( ObjectChurnLoadTest.simpleName )
    N4j.getCloudInfo( )
    testAcct = "${N4j.NAME_PREFIX}stack-churn-load"
    N4j.createAccount( testAcct )
    testAcctAdminCredentials = new AWSStaticCredentialsProvider( N4j.getUserCreds( testAcct, 'admin' ) )
    cloudAdminCredentials = new AWSStaticCredentialsProvider( new BasicAWSCredentials(ACCESS_KEY, SECRET_KEY) )
  }

  @AfterClass
  static void cleanup( ) {
    N4j.deleteAccount( testAcct )
  }

  private static AmazonCloudFormationClient getCloudFormationClient( final AWSCredentialsProvider credentials ) {
    final AmazonCloudFormationClient cf = new AmazonCloudFormationClient( credentials, new ClientConfiguration(
        socketTimeout: TimeUnit.MINUTES.toMillis( 2 )
    ) )
    cf.setEndpoint(N4j.CF_ENDPOINT )
    cf
  }

  @Test
  void test( ) {
    final String namePrefix = "x-${UUID.randomUUID().toString().substring(0, 13)}-"
    N4j.print( "Using resource prefix for test: " + namePrefix )

    final long startTime = System.currentTimeMillis( )
    final List<List<Runnable>> allCleanupTasks = new ArrayList<>( )
    try {
      final int threads = 20
      final int stacks  = 25
      N4j.print( "Creating ${stacks} stacks in ${threads} threads" )
      final AtomicInteger successCount = new AtomicInteger(0)
      final CountDownLatch latch = new CountDownLatch( threads )
      ( 1..threads ).each { Integer thread ->
        final List<Runnable> cleanupTasks = [] as List<Runnable>
        allCleanupTasks << cleanupTasks
        Thread.start {
          try {
            final AmazonCloudFormationClient cf = getCloudFormationClient( testAcctAdminCredentials )
            ( 1..stacks ).each { Integer stack ->
              String stackName = "${namePrefix}stack-${thread}-${stack}"
              N4j.print( "[${thread}] Creating stack ${stackName} ${stack}/${stacks}" )
              cf.createStack(new CreateStackRequest(stackName: stackName, templateBody: TEMPLATE))
              (1..100).find { Integer iter ->
                sleep(10000)
                N4j.print("[${thread}] Waiting for stack ${stackName} ${stack}/${stacks} to be created (${5 * iter}s)")
                String stackStatus = cf.describeStacks(new DescribeStacksRequest(stackName: stackName)).with { res ->
                  res?.stacks?.getAt(0)?.stackStatus
                }
                if (stackStatus == 'CREATE_COMPLETE') {
                  stackStatus
                } else if (stackStatus == 'CREATE_IN_PROGRESS') {
                  null
                } else {
                  fail("[${thread}] Unexpected stack ${stackName} staus ${stackStatus}")
                }
              }

              N4j.print("[${thread}] Deleting stack ${stackName} ${stack}/${stacks}")
              cf.deleteStack(new DeleteStackRequest(stackName: stackName))
              (1..100).find { Integer iter ->
                sleep(10000)
                N4j.print("[${thread}] Waiting for stack ${stackName} ${stack}/${stacks} to be deleted (${5 * iter}s)")
                String stackStatus = cf.describeStacks(new DescribeStacksRequest(stackName: stackName)).with { res ->
                  res?.stacks?.getAt(0)?.stackStatus
                }
                if (stackStatus == null || stackStatus == 'DELETE_COMPLETE') {
                  'DELETE_COMPLETE'
                } else if (stackStatus == 'DELETE_IN_PROGRESS') {
                  null
                } else if (stackStatus == 'CREATE_COMPLETE') {
                  N4j.print("[${thread}] Stack delete not in progress, retrying")
                  cf.deleteStack(new DeleteStackRequest(stackName: stackName))
                  null
                } else {
                  fail("[${thread}] Unexpected stack ${stackName} status ${stackStatus} for ${stack}/${stacks}")
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
      Assert.assertEquals( "All threads successful", threads, successCount.get( ) )

      N4j.print( "Test complete in ${System.currentTimeMillis()-startTime}ms" )
    } finally {
      // Attempt to clean up anything we created
      N4j.print( "Running cleanup tasks" )
      final long cleanupStart = System.currentTimeMillis( )
      final CountDownLatch cleanupLatch = new CountDownLatch( allCleanupTasks.size( ) )
      allCleanupTasks.each { List<Runnable> cleanupTasks ->
        Thread.start {
          try {
            cleanupTasks.reverseEach { Runnable cleanupTask ->
              try {
                cleanupTask.run()
              } catch ( Exception e ) {
                e.printStackTrace( )
              }
            }
          } finally {
            cleanupLatch.countDown( )
          }
        }
      }
      cleanupLatch.await( )
      N4j.print( "Completed cleanup tasks in ${System.currentTimeMillis()-cleanupStart}ms" )
    }
  }
}
