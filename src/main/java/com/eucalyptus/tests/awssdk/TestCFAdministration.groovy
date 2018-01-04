package com.eucalyptus.tests.awssdk

import com.amazonaws.Request
import com.amazonaws.auth.AWSCredentialsProvider
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.handlers.AbstractRequestHandler
import com.amazonaws.internal.StaticCredentialsProvider
import com.amazonaws.services.cloudformation.AmazonCloudFormationClient
import com.amazonaws.services.cloudformation.model.CreateStackRequest
import com.amazonaws.services.cloudformation.model.DeleteStackRequest
import com.amazonaws.services.cloudformation.model.DescribeStackEventsRequest
import com.amazonaws.services.cloudformation.model.DescribeStackResourcesRequest
import com.amazonaws.services.cloudformation.model.DescribeStacksRequest
import com.amazonaws.services.cloudformation.model.ListStackResourcesRequest
import com.amazonaws.services.cloudformation.model.ListStacksRequest
import com.amazonaws.services.identitymanagement.model.CreateAccessKeyRequest
import com.amazonaws.services.identitymanagement.model.CreateUserRequest
import com.amazonaws.services.identitymanagement.model.PutUserPolicyRequest
import com.amazonaws.services.simpleworkflow.model.DomainDeprecatedException
import com.amazonaws.services.simpleworkflow.model.TypeDeprecatedException
import com.github.sjones4.youcan.youare.YouAre
import com.github.sjones4.youcan.youare.model.CreateAccountRequest
import com.github.sjones4.youcan.youare.model.DeleteAccountRequest
import com.github.sjones4.youcan.youare.YouAreClient

import org.junit.Assert
import org.junit.BeforeClass
import org.junit.Test

/**
 * This application tests administration for CF resources.
 *
 * This is verification for the issue:
 *
 *   https://eucalyptus.atlassian.net/browse/EUCA-10215
 */
class TestCFAdministration {

  private static AWSCredentialsProvider credentials

  @BeforeClass
  static void init( ) {
    N4j.initEndpoints( )
    this.credentials = new StaticCredentialsProvider( new BasicAWSCredentials( N4j.ACCESS_KEY, N4j.SECRET_KEY ) )
  }

  private YouAreClient getYouAreClient( final AWSCredentialsProvider credentials  ) {
    final YouAreClient euare = new YouAreClient( credentials )
    euare.setEndpoint( N4j.IAM_ENDPOINT )
    euare
  }

  private AmazonCloudFormationClient getCloudFormationClient( final AWSCredentialsProvider credentials  ) {
    final AmazonCloudFormationClient cf = new AmazonCloudFormationClient( credentials )
    cf.setEndpoint( N4j.CF_ENDPOINT )
    cf
  }

  @Test
  void CFAdministrationTest( ) throws Exception {
    final String namePrefix = UUID.randomUUID().toString() + "-"
    N4j.print( "Using resource prefix for test: ${namePrefix}" )

    final List<Runnable> cleanupTasks = [] as List<Runnable>
    try {
      final String userName = "${namePrefix}cf-test-USER"
      AWSCredentialsProvider cfAccountCredentials = null
      AWSCredentialsProvider cfUserCredentials = null
      final YouAre youAre = getYouAreClient( credentials )
      youAre.with {
        final String accountName = "${namePrefix}cf-test-account"
        N4j.print( "Creating account for administration / IAM testing: ${accountName}" )
        String adminAccountNumber = createAccount( new CreateAccountRequest( accountName: accountName ) ).with {
          account?.accountId
        }
        Assert.assertTrue("Expected account number", adminAccountNumber != null)
        N4j.print( "Created admin account with number: ${adminAccountNumber}" )
        cleanupTasks.add {
          N4j.print( "Deleting admin account: ${accountName}" )
          deleteAccount( new DeleteAccountRequest( accountName: accountName, recursive: true ) )
        }

        YouAre adminIam = getYouAreClient( credentials )
        adminIam.addRequestHandler( new AbstractRequestHandler(){
          void beforeRequest(final Request<?> request) {
            request.addParameter( "DelegateAccount", accountName )
          }
        } )
        adminIam.with {
          N4j.print( "Creating access key for admin account: ${accountName}" )
          cfAccountCredentials = createAccessKey( new CreateAccessKeyRequest( userName: 'admin' ) ).with {
            accessKey?.with {
              new StaticCredentialsProvider( new BasicAWSCredentials( accessKeyId, secretAccessKey ) )
            }
          }

          Assert.assertTrue("Expected admin credentials", cfAccountCredentials != null)
          N4j.print( "Created cf account access key: ${cfAccountCredentials.credentials.AWSAccessKeyId}" )

          N4j.print( "Creating USER in admin account for policy testing: ${userName}" )
          final String userId = createUser( new CreateUserRequest( userName: userName, path: '/' ) ).with {
            user.userId
          }
          Assert.assertTrue("Expected USER ID", userId != null)
          N4j.print( "Created admin USER with number: ${userId}" )

          N4j.print( "Creating access key for admin USER: ${userName}" )
          cfUserCredentials = createAccessKey( new CreateAccessKeyRequest( userName: userName ) ).with {
            accessKey?.with {
              new StaticCredentialsProvider( new BasicAWSCredentials( accessKeyId, secretAccessKey ) )
            }
          }

          Assert.assertTrue("Expected USER credentials", cfUserCredentials != null)
          N4j.print( "Created cf USER access key: ${cfAccountCredentials.credentials.AWSAccessKeyId}" )

          void
        }

        void
      }

      final String template = """\
            {
              "AWSTemplateFormatVersion" : "2010-09-09",
              "Description" : "Security group stack",
              "Resources" : {
                "SecurityGroup" : {
                 "Type" : "AWS::EC2::SecurityGroup",
                 "Properties" : {
                     "GroupDescription" : "Test security group"
                 }
                }
              }
            }
            """.stripIndent( ) as String

      final String stackName1 = "a-${namePrefix}stack"
      final String stackName2 = "b-${namePrefix}stack"
      String stackId1 = null
      String stackId2 = null
      getCloudFormationClient( cfAccountCredentials ).with {
        N4j.print( "Creating test stack: ${stackName1}" )
        stackId1 = createStack( new CreateStackRequest(
                stackName: stackName1,
                templateBody:template
        ) ).stackId
        Assert.assertTrue("Expected stack ID", stackId1 != null)
        N4j.print( "Created stack with ID: ${stackId1}" )
        cleanupTasks.add{
          N4j.print( "Deleting stack: ${stackName1}" )
          deleteStack( new DeleteStackRequest( stackName: stackName1 ) )
        }

        N4j.print( "Creating test stack: ${stackName2}" )
        stackId2 = createStack( new CreateStackRequest(
                stackName: stackName2,
                templateBody:template
        ) ).stackId
        Assert.assertTrue("Expected stack ID", stackId2 != null)
        N4j.print( "Created stack with ID: ${stackId2}" )
        cleanupTasks.add{
          N4j.print( "Deleting stack: ${stackName2}" )
          deleteStack( new DeleteStackRequest( stackName: stackName2 ) )
        }

        N4j.print( "Waiting for stack ${stackId1} creation" )
        ( 1..25 ).find{
          sleep 5000
          N4j.print( "Waiting for stack ${stackId1} creation, waited ${it*5}s" )
          describeStacks( new DescribeStacksRequest(
                  stackName: stackId1
          ) ).with {
            stacks?.getAt( 0 )?.stackId == stackId1 && stacks?.getAt( 0 )?.stackStatus == 'CREATE_COMPLETE'
          }
        }

        N4j.print( "Waiting for stack ${stackId2} creation" )
        ( 1..25 ).find{
          sleep 5000
          N4j.print( "Waiting for stack ${stackId2} creation, waited ${it*5}s" )
          describeStacks( new DescribeStacksRequest(
                  stackName: stackId2
          ) ).with {
            stacks?.getAt( 0 )?.stackId == stackId2 && stacks?.getAt( 0 )?.stackStatus == 'CREATE_COMPLETE'
          }
        }
      }

      getYouAreClient( cfAccountCredentials ).with {
        N4j.print( "Creating policy with stack permissions" )
        putUserPolicy( new PutUserPolicyRequest(
                userName: userName,
                policyName: 'cf-policy',
                policyDocument: """\
              {
                "Statement": [
                  {
                    "Action": [
                      "cloudformation:*"
                    ],
                    "Effect": "Allow",
                    "Resource": "${stackId1}"
                  },
                  {
                    "Action": [
                      "ec2:*"
                    ],
                    "Effect": "Allow",
                    "Resource": "*"
                  }
                ]
              }
              """.stripIndent( ) as String
        ) )
      }

      getCloudFormationClient( credentials ).with {
        N4j.print( "Verifying cloud admin does not see other account stacks when describing by default" )
        int adminStackCount = describeStacks( ).with {
          Assert.assertTrue("Expected no stacks from other accounts",
              stacks.findAll { [stackId1, stackId2].contains(it.stackId) }.empty
          )
          stacks.size( )
        }

        N4j.print( "Verifying cloud admin does not see other account stacks when listing" )
        listStacks( new ListStacksRequest() ).with {
          Assert.assertTrue("Expected no stacks from other accounts",
              stackSummaries.findAll { [stackId1, stackId2].contains(it.stackId) }.empty
          )
        }

        N4j.print( "Verifying cloud admin sees other account stacks with verbose describe" )
        describeStacks( new DescribeStacksRequest( stackName: 'verbose' ) ).with {
          Assert.assertTrue("Expected to see other account stacks", stacks?.size() > adminStackCount)
        }

        N4j.print( "Verifying cloud admin sees other account stack with explicit describe" )
        describeStacks( new DescribeStacksRequest( stackName: stackId1 ) ).with {
          Assert.assertTrue("Expected 1 stack", 1 == stacks?.size())
        }

        N4j.print( "Verifying cloud admin can describe stack events" )
        describeStackEvents( new DescribeStackEventsRequest(
                stackName: stackId1
        ) ).with {
          Assert.assertTrue("Expected stack events", !stackEvents?.empty)
        }

        N4j.print( "Verifying cloud admin can describe stack resources" )
        describeStackResources( new DescribeStackResourcesRequest(
                stackName: stackId1
        ) ).with {
          Assert.assertTrue("Expected stack resources", !stackResources?.empty)
        }
      }

      getCloudFormationClient( cfUserCredentials ).with {
        N4j.print( "Verifying USER sees permitted stack when describing" )
        describeStacks( ).with {
          Assert.assertTrue("Expected 1 stack", 1 == stacks?.size())
        }

        N4j.print( "Verifying USER sees permitted stack when describing by name" )
        describeStacks( new DescribeStacksRequest( stackName: stackName1 ) ).with {
          Assert.assertTrue("Expected 1 stack", 1 == stacks?.size())
        }

        N4j.print( "Verifying USER sees permitted stack when listing" )
        listStacks( ).with {
          Assert.assertTrue("Expected 1 stack", 1 == stackSummaries?.size())
        }

        N4j.print( "Verifying USER can describe stack events" )
        describeStackEvents( new DescribeStackEventsRequest(
                stackName: stackName1
        ) ).with {
          Assert.assertTrue("Expected stack events", !stackEvents?.empty)
        }

        N4j.print( "Verifying USER can describe stack resources" )
        describeStackResources( new DescribeStackResourcesRequest(
                stackName: stackName1
        ) ).with {
          Assert.assertTrue("Expected stack resources", !stackResources?.empty)
        }

        N4j.print( "Verifying USER can list stack resources" )
        listStackResources( new ListStackResourcesRequest(
                stackName: stackName1
        ) ).with {
          Assert.assertTrue("Expected stack resources", !stackResourceSummaries?.empty)
        }

        N4j.print( "Verifying USER can delete stack ${stackName1}" )
        deleteStack( new DeleteStackRequest( stackName: stackName1 ) )

        N4j.print( "Waiting for stack ${stackName1} deletion" )
        ( 1..25 ).find{
          sleep 5000
          N4j.print( "Waiting for stack ${stackName1} deletion, waited ${it*5}s" )
          describeStacks( new DescribeStacksRequest(
                  stackName: stackName1
          ) ).with {
            stacks?.empty || ( stacks?.getAt( 0 )?.stackName == stackName1 && stacks?.getAt( 0 )?.stackStatus == 'DELETE_COMPLETE' )
          }
        }

        N4j.print( "Verifying stack deleted ${stackName1}" )
        describeStacks( new DescribeStacksRequest( stackName: stackName1 ) ).with {
          Assert.assertTrue("Expected stack ${stackName1} deleted", stacks?.empty || stacks?.getAt(0)?.stackStatus == 'DELETE_COMPLETE')
        }
      }

      getCloudFormationClient( credentials ).with {
        N4j.print( "Verifying cloud admin can delete other accounts stack" )
        deleteStack( new DeleteStackRequest( stackName: stackId2 ) )

        N4j.print( "Waiting for stack ${stackId2} deletion" )
        ( 1..25 ).find{
          sleep 5000
          N4j.print( "Waiting for stack ${stackId2} deletion, waited ${it*5}s" )
          describeStacks( new DescribeStacksRequest(
                  stackName: stackId2
          ) ).with {
            stacks?.empty || ( stacks?.getAt( 0 )?.stackId == stackId2 && stacks?.getAt( 0 )?.stackStatus == 'DELETE_COMPLETE' )
          }
        }

        N4j.print( "Verifying stack deleted ${stackId2}" )
        describeStacks( new DescribeStacksRequest( stackName: stackId2 ) ).with {
          Assert.assertTrue("Expected stack ${stackId2} deleted", stacks?.empty || stacks?.getAt(0)?.stackStatus == 'DELETE_COMPLETE')
        }
      }

      N4j.print( "Test complete" )
    } finally {
      // Attempt to clean up anything we created
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
