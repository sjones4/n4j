package com.eucalyptus.tests.awssdk

import com.amazonaws.AmazonServiceException
import com.amazonaws.Request
import com.amazonaws.auth.AWSCredentialsProvider
import com.amazonaws.auth.AWSStaticCredentialsProvider
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.handlers.RequestHandler2
import com.amazonaws.services.identitymanagement.model.*
import com.github.sjones4.youcan.youare.YouAreClient
import com.github.sjones4.youcan.youare.model.CreateAccountRequest
import com.github.sjones4.youcan.youare.model.DeleteAccountRequest

import org.junit.Assert
import org.junit.Test

/**
 * Tests IAM policy variables.
 *
 * Covers user managing their own credentials.
 *
 * Related issues:
 *   https://eucalyptus.atlassian.net/browse/EUCA-8582
 *
 * Related AWS doc:
 *   http://docs.aws.amazon.com/IAM/latest/UserGuide/reference_policies_variables.html
 *   http://docs.aws.amazon.com/IAM/latest/UserGuide/id_credentials_delegate-permissions_examples.html
 */
class TestIAMPolicyVariables {

  TestIAMPolicyVariables( ) {
    N4j.initEndpoints( )
  }

  private YouAreClient getYouAreClient( ) {
    getYouAreClient( new AWSStaticCredentialsProvider( new BasicAWSCredentials( N4j.ACCESS_KEY, N4j.SECRET_KEY ) ) )
  }

  private YouAreClient getYouAreClient( final AWSCredentialsProvider credentials ) {
    final YouAreClient euare = new YouAreClient( credentials )
    euare.setEndpoint( N4j.IAM_ENDPOINT )
    euare
  }

  @Test
  void testIAMPolicyVariables( ) throws Exception {
    N4j.testInfo(this.getClass().getSimpleName())
    final String namePrefix = UUID.randomUUID().toString().substring(0,8) + "-"
    N4j.print( "Using resource prefix for test: ${namePrefix}" )

    final List<Runnable> cleanupTasks = [] as List<Runnable>
    final String accountName = "${namePrefix}account1"
    final String userName = "${namePrefix}user1"
    try {
      AWSCredentialsProvider adminCredentials = getYouAreClient( ).with {
        N4j.print("Creating test account: ${accountName}")
        String adminAccountNumber = createAccount(new CreateAccountRequest(accountName: accountName)).with {
          account?.accountId
        }
        Assert.assertTrue("Expected account number", adminAccountNumber != null)
        N4j.print( "Created test account with number: ${adminAccountNumber}" )
        cleanupTasks.add {
          N4j.print("Deleting test account: ${accountName}")
          deleteAccount(new DeleteAccountRequest(accountName: accountName, recursive: true))
        }

        N4j.print("Creating access key for test account admin user: ${accountName}")
        getYouAreClient( ).with {
          addRequestHandler(new RequestHandler2() {
            void beforeRequest(final Request<?> request) {
              request.addParameter("DelegateAccount", accountName)
            }
          })
          createAccessKey(new CreateAccessKeyRequest(userName: "admin")).with {
            accessKey?.with {
              new AWSStaticCredentialsProvider( new BasicAWSCredentials( accessKeyId, secretAccessKey ) )
            }
          }
        }
      }

      AWSCredentialsProvider userCredentials = getYouAreClient( adminCredentials ).with {
        String accountNumber = getUser( ).with {
          user.getArn( ).split(":")[4]
        }
        N4j.print( "Detected account number ${accountNumber}" )

        cleanupTasks.add{
          N4j.print( "Deleting user ${userName}" )
          deleteUser( new DeleteUserRequest(
              userName: userName
          ) )
        }
        N4j.print( "Creating user ${userName}" )
        String userId = createUser( new CreateUserRequest(
            userName: userName,
            path: '/'
        ) ).with {
          user?.userId
        }
        N4j.print( "Created user with id ${userId}" )

        String policyName = "${namePrefix}policy1"
        N4j.print( "Creating user policy ${policyName}" )
        putUserPolicy( new PutUserPolicyRequest(
            userName: userName,
            policyName: policyName,
            policyDocument: """\
              {
                "Version": "2012-10-17",
                "Statement": [{
                  "Action": [
                    "iam:*AccessKey*",
                    "iam:*LoginProfile"
                  ],
                  "Effect": "Allow",
                  "Resource": ["arn:aws:iam::${accountNumber}:user/\${aws:username}"]
                }]
              }
              """.stripIndent( )
        ) )
        cleanupTasks.add{
          N4j.print( "Deleting user policy ${policyName}" )
          deleteUserPolicy( new DeleteUserPolicyRequest(
              userName: userName,
              policyName: policyName
          ) )
        }

        String policyName2 = "${namePrefix}policy2"
        N4j.print( "Creating user policy ${policyName}" )
        putUserPolicy( new PutUserPolicyRequest(
            userName: userName,
            policyName: policyName2,
            policyDocument: """\
              {
                "Version": "2012-10-17",
                "Statement": {
                  "Action": "iam:ListUsers",
                  "Effect": "Allow",
                  "Resource": "*",
                  "Condition": {
                    "StringEquals": {
                      "aws:username": "${userName}",
                      "aws:userid": "${userId}",
                      "aws:PrincipalType": "User"
                    }
                  }
                }
              }
              """.stripIndent( )
        ) )
        cleanupTasks.add{
          N4j.print( "Deleting user policy ${policyName2}" )
          deleteUserPolicy( new DeleteUserPolicyRequest(
              userName: userName,
              policyName: policyName2
          ) )
        }

        N4j.print( "Creating access key for user ${userName}" )
        AWSCredentialsProvider userCredentials = createAccessKey( new CreateAccessKeyRequest(
            userName: userName
        ) ).with {
          accessKey.with {
            new AWSStaticCredentialsProvider( new BasicAWSCredentials( accessKeyId, secretAccessKey ) )
          }
        }

        cleanupTasks.add {
          N4j.print( "Deleting access key for user ${userName}" )
          deleteAccessKey( new DeleteAccessKeyRequest(
              userName: userName,
              accessKeyId: userCredentials.credentials.AWSAccessKeyId
          ) )
        }

        userCredentials
      }

      getYouAreClient( userCredentials ).with {
        N4j.print( "Creating access key using users credentials" )
        String keyId = createAccessKey( new CreateAccessKeyRequest( ) ).with {
          accessKey.accessKeyId
        }
        N4j.print( "Created access key: ${keyId}" )

        N4j.print( "Listing access keys using user credentials" )
        listAccessKeys( ).with {
          Assert.assertTrue("Expected access key", !accessKeyMetadata.isEmpty())
          accessKeyMetadata.each { AccessKeyMetadata key ->
            N4j.print( "Listed access key: ${key.accessKeyId}" )
          }
        }

        N4j.print( "Deleting access key ${keyId} using users credentials" )
        deleteAccessKey( new DeleteAccessKeyRequest(
            accessKeyId: keyId
        ) )

        try {
          N4j.print( "Creating access key for admin using users credentials, should fail" )
          createAccessKey( new CreateAccessKeyRequest( userName: 'admin' ) )
          Assert.assertTrue("Expected key creation to fail for admin user due to permissions", false)
        } catch ( AmazonServiceException e ) {
          N4j.print( "Expected error creating key without permission: ${e}" )
        }

        N4j.print( "Creating login profile using users credentials" )
        createLoginProfile( new CreateLoginProfileRequest( userName: userName, password: "p@55w0Rd!" ) )

        N4j.print( "Getting login profile using users credentials" )
        getLoginProfile( new GetLoginProfileRequest( userName: userName ) ).with {
          N4j.print( "Login profile create date: ${loginProfile.createDate}" )
        }

        N4j.print( "Updating login profile using users credentials" )
        updateLoginProfile( new UpdateLoginProfileRequest( userName: userName, password: "Upd@T3d_p@55w0Rd" ) )

        N4j.print( "Deleting login profile using users credentials" )
        deleteLoginProfile( new DeleteLoginProfileRequest( userName: userName ) )

        try {
          N4j.print( "Creating login profile for admin using users credentials, should fail" )
          createLoginProfile( new CreateLoginProfileRequest( userName: 'admin', password: "p@55w0Rd!" ) )
          Assert.assertTrue("Expected login profile creation to fail for admin user due to permissions", false)
        } catch ( AmazonServiceException e ) {
          N4j.print( "Expected error creating login profile without permission: ${e}" )
        }

        N4j.print( "Listing users to verify policy variables as condition key" )
        listUsers( ).with {
          N4j.print( "Listed ${users.size()} users" )
          N4j.assertThat( !users.isEmpty( ), "Expected users" )
        }

        void
      }

      N4j.print( "Test complete" )
    } finally {
      // Attempt to clean up anything we created
      cleanupTasks.reverseEach { Runnable cleanupTask ->
        try {
          cleanupTask.run()
        } catch ( NoSuchEntityException e ) {
          N4j.print( "Entity not found during cleanup." )
        } catch ( AmazonServiceException e ) {
          N4j.print( "Service error during cleanup; code: ${e.errorCode}, message: ${e.message}" )
        } catch ( Exception e ) {
          e.printStackTrace()
        }
      }
    }
  }
}
