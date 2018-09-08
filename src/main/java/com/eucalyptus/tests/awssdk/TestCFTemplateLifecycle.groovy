package com.eucalyptus.tests.awssdk

import com.amazonaws.auth.AWSCredentialsProvider
import com.amazonaws.auth.AWSStaticCredentialsProvider
import com.amazonaws.client.builder.AwsClientBuilder
import com.amazonaws.services.cloudformation.AmazonCloudFormation
import com.amazonaws.services.cloudformation.AmazonCloudFormationClient
import com.amazonaws.services.cloudformation.model.*
import com.amazonaws.services.identitymanagement.AmazonIdentityManagement
import com.amazonaws.services.identitymanagement.AmazonIdentityManagementClient
import com.amazonaws.services.identitymanagement.model.AddUserToGroupRequest
import com.amazonaws.services.identitymanagement.model.CreateGroupRequest
import com.amazonaws.services.identitymanagement.model.DeleteGroupRequest
import com.amazonaws.services.identitymanagement.model.RemoveUserFromGroupRequest
import com.amazonaws.services.simpleworkflow.model.DomainDeprecatedException
import com.amazonaws.services.simpleworkflow.model.TypeDeprecatedException
import org.junit.After
import org.junit.AfterClass
import org.junit.Assert
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test

/**
 *
 */
class TestCFTemplateLifecycle {

  private static String testAcct
  private static AWSCredentialsProvider testAcctAdminCredentials
  private static AmazonCloudFormation cfClient
  private static AmazonIdentityManagement iamClient

  private List<Runnable> cleanupTasks

  private static AmazonCloudFormation getCloudFormationClient( final AWSCredentialsProvider credentials ) {
    AmazonCloudFormationClient.builder( )
        .withCredentials( credentials )
        .withEndpointConfiguration( new AwsClientBuilder.EndpointConfiguration( N4j.CF_ENDPOINT, 'eucalyptus' ) )
        .build( )
  }

  private static AmazonIdentityManagement getIdentityManagementClient( final AWSCredentialsProvider credentials ) {
    AmazonIdentityManagementClient.builder( )
        .withCredentials( credentials )
        .withEndpointConfiguration( new AwsClientBuilder.EndpointConfiguration( N4j.IAM_ENDPOINT, 'eucalyptus' ) )
        .build( )
  }

  @BeforeClass
  static void init( ){
    N4j.testInfo( TestCFTemplateLifecycle.simpleName )
    N4j.getCloudInfo( )
    testAcct = "${N4j.NAME_PREFIX}cf-templates"
    N4j.createAccount( testAcct )
    testAcctAdminCredentials = new AWSStaticCredentialsProvider( N4j.getUserCreds( testAcct, 'admin' ) )
    cfClient = getCloudFormationClient( testAcctAdminCredentials )
    iamClient = getIdentityManagementClient( testAcctAdminCredentials )
  }

  @AfterClass
  static void cleanup( ) {
    if ( cfClient ) cfClient.shutdown( )
    if ( iamClient ) iamClient.shutdown( )
    N4j.deleteAccount( testAcct )
  }

  @Before
  void initTest( ) {
    cleanupTasks = [] as List<Runnable>
  }

  @After
  void cleanupTest( ) {
    N4j.print( "Running clean up tasks for test" )
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

  @Test
  void testRetainResourcesOnDelete( ) {
    String iamTemplate = '''\
    {
      "AWSTemplateFormatVersion" : "2010-09-09",
      "Resources" : {
        "User" : {
          "Type" : "AWS::IAM::User",
          "Properties" : {
            "UserName" : "user1"
          }
        }
      }
    }
    '''.stripIndent( )
    stackCreate( 'iam-stack', iamTemplate, [ 'CAPABILITY_NAMED_IAM' ] )

    N4j.print( 'Creating group and adding stack created user' )
    iamClient.createGroup( new CreateGroupRequest( groupName: 'group1' ) )
    iamClient.addUserToGroup( new AddUserToGroupRequest(
        groupName: 'group1',
        userName: 'user1'
    ) )
    cleanupTasks.add{
      N4j.print( 'Deleting group' )
      iamClient.removeUserFromGroup( new RemoveUserFromGroupRequest(
          groupName: 'group1',
          userName: 'user1'
      ) )
      iamClient.deleteGroup( new DeleteGroupRequest( groupName: 'group1' ) )
    }

    N4j.print( 'Deleting stack, should fail due to user1 in use' )
    String status = deleteStack( 'iam-stack' )
    N4j.print( "Stack deletion status ${status}" )
    Assert.assertEquals('Stack deletion status', 'DELETE_FAILED', status)

    N4j.print( 'Deleting stack, retaining user resource' )
    status = deleteStack( 'iam-stack', [ 'User' ] )
    N4j.print( "Stack deletion status ${status}" )
    Assert.assertEquals('Stack deletion status', 'DELETE_COMPLETE', status)
  }

  private void stackCreate(
      final String stackName,
      final String stackTemplate,
      final List<String> capabilities = [ ],
      final Map<String,String> parameters = [:]
  ) {
    cfClient.with {
      N4j.print("Creating test stack: ${stackName}")
      String stackId = createStack(new CreateStackRequest(
          stackName: stackName,
          capabilities: capabilities,
          parameters: parameters.collect { new Parameter(parameterKey: it.key, parameterValue: it.value) },
          templateBody: stackTemplate
      )).stackId
      Assert.assertTrue("Expected stack id", stackId != null)
      N4j.print("Created stack with id: ${stackId}")
      cleanupTasks.add {
        N4j.print("Deleting stack: ${stackName}")
        deleteStack(new DeleteStackRequest(stackName: stackName))
      }

      N4j.print("Waiting for stack ${stackName} creation")
      String lastStatus = ''
      (1..25).find {
        N4j.sleep 5
        N4j.print("Waiting for stack ${stackName} creation, waited ${it * 5}s")
        describeStacks(new DescribeStacksRequest(
            stackName: stackId
        )).with {
          stacks?.getAt(0)?.with { Stack stack ->
            if (stack.stackId == stackId) {
              lastStatus = stack.stackStatus
              if (lastStatus == 'CREATE_COMPLETE') {
                lastStatus
              } else if (lastStatus == 'CREATE_IN_PROGRESS') {
                null
              } else {
                N4j.print("Unexpected status ${lastStatus}, dumping stack events")
                describeStackEvents(new DescribeStackEventsRequest(stackName: stackId)).with {
                  stackEvents?.each {
                    N4j.print(it.toString())
                  }
                }
                Assert.fail("Unexpected status ${lastStatus}")
                null
              }
            } else {
              Assert.fail( "Unexpected stack ${stack.stackId}" )
              null
            }
          }
        }
      }
      Assert.assertEquals('Stack status', 'CREATE_COMPLETE', lastStatus)

      cleanupTasks.add {
        lastStatus = deleteStack( stackName )
        Assert.assertEquals('Stack status', 'DELETE_COMPLETE', lastStatus)
      }
    }
  }

  private String deleteStack(
      final String stackName,
      final List<String> retainResources = [ ]
  ) {
    cfClient.with {
      N4j.print("Deleting stack ${stackName}")
      deleteStack(new DeleteStackRequest(
          stackName: stackName,
          retainResources: retainResources
      ))

      N4j.print("Waiting for stack ${stackName} deletion")
      String lastStatus = ''
      (1..25).find {
        N4j.sleep 5
        N4j.print("Waiting for stack ${stackName} deletion, waited ${it * 5}s")
        describeStacks(new DescribeStacksRequest(
            stackName: stackName
        )).with {
          if ( stacks?.empty ) {
            lastStatus = 'DELETE_COMPLETE'
          } else {
            stacks?.getAt(0)?.with { Stack stack ->
              if (stack.stackName == stackName) {
                lastStatus = stack.stackStatus
                if (lastStatus == 'DELETE_COMPLETE' || lastStatus == 'DELETE_FAILED') {
                  lastStatus
                } else if (lastStatus == 'DELETE_IN_PROGRESS' || lastStatus == 'CREATE_COMPLETE') {
                  null
                } else {
                  Assert.fail("Unexpected status ${lastStatus}")
                  null
                }
              } else {
                Assert.fail("Unexpected stack ${stack.stackId}")
                null
              }
            }
          }
        }
      }

      N4j.sleep 10 // sleep to ensure deletion operation completes
      lastStatus
    }
  }
}
