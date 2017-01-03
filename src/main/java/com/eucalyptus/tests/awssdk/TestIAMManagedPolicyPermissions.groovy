package com.eucalyptus.tests.awssdk

import com.amazonaws.AmazonServiceException
import com.amazonaws.auth.AWSCredentials
import com.amazonaws.auth.AWSCredentialsProvider
import com.amazonaws.auth.BasicSessionCredentials
import com.amazonaws.internal.StaticCredentialsProvider
import com.amazonaws.services.ec2.AmazonEC2
import com.amazonaws.services.ec2.AmazonEC2Client
import com.amazonaws.services.ec2.model.DescribeImagesRequest
import com.amazonaws.services.ec2.model.DescribeSecurityGroupsRequest
import com.amazonaws.services.identitymanagement.AmazonIdentityManagement
import com.amazonaws.services.identitymanagement.model.*
import com.amazonaws.services.securitytoken.AWSSecurityTokenService
import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClient
import com.amazonaws.services.securitytoken.model.AssumeRoleRequest
import org.testng.annotations.AfterClass
import org.testng.annotations.Test

import static com.eucalyptus.tests.awssdk.N4j.*

/**
 * Tests policy enforcement for IAM managed policies.
 *
 * Related issues:
 *   https://eucalyptus.atlassian.net/browse/EUCA-10773
 *
 * Related AWS doc:
 *   http://docs.aws.amazon.com/IAM/latest/UserGuide/policies-managed-vs-inline.html
 */
class TestIAMManagedPolicyPermissions {

  private final AWSCredentialsProvider testAcctAdminCredentials
  private final String testAcct
  private final AWSCredentialsProvider testAcctUserCredentials
  private final String testUser

  TestIAMManagedPolicyPermissions( ) {
    getCloudInfo( )
    this.testAcct = "${NAME_PREFIX}man-pol-per-test-acct"
    this.testUser = "${NAME_PREFIX}user"
    createAccount(testAcct)
    this.testAcctAdminCredentials = new StaticCredentialsProvider( getUserCreds(testAcct, 'admin') )
    createUser(testAcct, testUser)
    this.testAcctUserCredentials = new StaticCredentialsProvider( getUserCreds(testAcct, testUser) )
  }

  @AfterClass
  void tearDownAfterClass( ) {
    deleteAccount( testAcct )
  }

  private AmazonIdentityManagement getIamClient(
      AWSCredentialsProvider credentialsProvider = testAcctAdminCredentials
  ) {
    AWSCredentials creds = credentialsProvider.getCredentials( )
    getYouAreClient( creds.AWSAccessKeyId, creds.AWSSecretKey, IAM_ENDPOINT )
  }

  private AWSSecurityTokenService getStsClient(final AWSCredentialsProvider credentialsProvider) {
    AWSSecurityTokenServiceClient sts = new AWSSecurityTokenServiceClient(credentialsProvider)
    sts.setEndpoint(TOKENS_ENDPOINT)
    sts
  }

  private AmazonEC2 getEc2Client(
      AWSCredentialsProvider credentialsProvider = testAcctAdminCredentials
  ) {
    AmazonEC2 ec2 = new AmazonEC2Client(credentialsProvider)
    ec2.setEndpoint(EC2_ENDPOINT)
    ec2
  }

  @Test
  void iamManagedPolicyTest( ) {
    testInfo( this.getClass( ).getSimpleName( ) )

    final List<Runnable> cleanupTasks = [] as List<Runnable>
    try {
      getIamClient( ).with {  iam ->
        String secGroupPolicyName = "${NAME_PREFIX}-sec-group-policy"
        print( "Creating managed policy ${secGroupPolicyName}" )
        String secGroupPolicyArn = createPolicy( new CreatePolicyRequest(
            policyName: secGroupPolicyName,
            path: '/',
            description: "Policy management test policy ${secGroupPolicyName}",
            policyDocument: '''\
            {
              "Version": "2012-10-17",
              "Statement":[
                  {
                    "Effect": "Allow",
                    "Action": "ec2:DescribeSecurityGroups",
                    "Resource": "*"
                  }
              ]
            }
            '''.stripIndent( )
        ) ).with {
          assertThat( policy != null, "Expected policy" )
          print( "Created policy with details ${policy}" )
          policy.with {
            assertThat( arn != null, "Expected policy arn")
            arn
          }
        }
        print( "Created managed policy with arn ${secGroupPolicyArn}" )

        String imagePolicyName = "${NAME_PREFIX}-image-policy"
        print( "Creating managed policy ${imagePolicyName}" )
        String imagePolicyArn = createPolicy( new CreatePolicyRequest(
            policyName: imagePolicyName,
            path: '/',
            description: "Policy management test policy ${imagePolicyName}",
            policyDocument: '''\
            {
              "Version": "2012-10-17",
              "Statement":[
                  {
                    "Effect": "Allow",
                    "Action": "ec2:DescribeImages",
                    "Resource": "*"
                  }
              ]
            }
            '''.stripIndent( )
        ) ).with {
          assertThat( policy != null, "Expected policy" )
          print( "Created policy with details ${policy}" )
          policy.with {
            assertThat( arn != null, "Expected policy arn")
            arn
          }
        }
        print( "Created managed policy with arn ${imagePolicyArn}" )


        String groupName = "${NAME_PREFIX}group"
        String roleName = "${NAME_PREFIX}role"
        print( "Creating group for policy attachment testing ${groupName}" )
        createGroup( new CreateGroupRequest(
            groupName: groupName
        ))
        print( "Adding user ${testUser} to group ${groupName}" )
        addUserToGroup( new AddUserToGroupRequest(
            userName: testUser,
            groupName: groupName
        ) )
        print( "Creating role for policy attachment testing ${roleName}" )
        String roleArn = createRole( new CreateRoleRequest(
            roleName: roleName,
            assumeRolePolicyDocument: '''\
            {"Statement":[{"Effect":"Allow","Principal":{"Service":["ec2.amazonaws.com"]},"Action":["sts:AssumeRole"]}]}
            '''.stripIndent()
        )).with {
          role?.arn
        }

        print( "Attaching policy ${imagePolicyArn} to group ${groupName}" )
        attachGroupPolicy( new AttachGroupPolicyRequest(
            groupName: groupName,
            policyArn: imagePolicyArn
        ) )

        print( "Attaching policy ${secGroupPolicyArn} to role ${roleName}" )
        attachRolePolicy( new AttachRolePolicyRequest(
            roleName: roleName,
            policyArn: secGroupPolicyArn
        ) )

        print( "Attaching policy ${secGroupPolicyArn} to user ${testUser}" )
        attachUserPolicy( new AttachUserPolicyRequest(
            userName: testUser,
            policyArn: secGroupPolicyArn
        ) )

        // test user/group attached policy
        getEc2Client( testAcctUserCredentials ).with {
          print( "Describing groups with test account user credentials to test user attached policy" )
          describeSecurityGroups( new DescribeSecurityGroupsRequest( ) ).with {
            print( "Got security groups: ${securityGroups}" )
            assertThat( !securityGroups.isEmpty( ), 'Expected security groups' )
          }

          print( "Describing images with test account user credentials to test group attached policy" )
          describeImages( new DescribeImagesRequest( ) ).with {
            print( "Got images: ${images}" )
            assertThat( !images.isEmpty( ), 'Expected images' )
          }
        }

        // test role attached policy
        final AWSCredentialsProvider roleCredentialsProvider = new AWSCredentialsProvider() {
          AWSCredentials awsCredentials = null
          @Override
          public AWSCredentials getCredentials( ) {
            if ( awsCredentials == null ) {
              N4j.print "Getting credentials using assume role"
              awsCredentials = getStsClient( testAcctAdminCredentials ).with {
                assumeRole( new AssumeRoleRequest(
                    roleArn: roleArn,
                    roleSessionName: 'session-name-here'
                ) ).with {
                  assertThat(credentials != null, "Expected credentials")
                  assertThat(credentials.accessKeyId != null, "Expected credentials access key")
                  assertThat(credentials.secretAccessKey != null, "Expected credentials secret key")
                  assertThat(credentials.sessionToken != null, "Expected credentials session token")
                  new BasicSessionCredentials(
                      credentials.accessKeyId,
                      credentials.secretAccessKey,
                      credentials.sessionToken
                  )
                }
              }
            }
            awsCredentials
          }

          @Override
          public void refresh( ) {
            awsCredentials = null
          }
        }
        getEc2Client( roleCredentialsProvider ).with {
          print( "Describing groups with role to test role attached policy" )
          describeSecurityGroups( new DescribeSecurityGroupsRequest( ) ).with {
            print( "Got security groups: ${securityGroups}" )
            assertThat( !securityGroups.isEmpty( ), 'Expected security groups' )
          }
        }
      }

      print( 'Test complete' )
    } finally {
      // Attempt to clean up anything we created
      cleanupTasks.reverseEach { Runnable cleanupTask ->
        try {
          cleanupTask.run()
        } catch ( NoSuchEntityException e ) {
          print( 'Entity not found during cleanup.' )
        } catch ( AmazonServiceException e ) {
          print( "Service error during cleanup; code: ${e.errorCode}, message: ${e.message}" )
        } catch ( Exception e ) {
          e.printStackTrace()
        }
      }
    }
  }
}
