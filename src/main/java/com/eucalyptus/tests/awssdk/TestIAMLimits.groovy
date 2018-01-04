/*************************************************************************
 * Copyright 2009-2015 Eucalyptus Systems, Inc.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see http://www.gnu.org/licenses/.
 *
 * Please contact Eucalyptus Systems, Inc., 6755 Hollister Ave., Goleta
 * CA 93117, USA or visit http://www.eucalyptus.com/licenses/ if you need
 * additional information or have any questions.
 ************************************************************************/
package com.eucalyptus.tests.awssdk

import com.amazonaws.AmazonServiceException
import com.amazonaws.auth.AWSCredentialsProvider
import com.amazonaws.internal.StaticCredentialsProvider
import com.amazonaws.services.identitymanagement.model.CreateAccountAliasRequest
import com.amazonaws.services.identitymanagement.model.CreateGroupRequest
import com.amazonaws.services.identitymanagement.model.CreateInstanceProfileRequest
import com.amazonaws.services.identitymanagement.model.CreateLoginProfileRequest
import com.amazonaws.services.identitymanagement.model.CreateRoleRequest
import com.amazonaws.services.identitymanagement.model.CreateUserRequest
import com.amazonaws.services.identitymanagement.model.DeleteGroupRequest
import com.amazonaws.services.identitymanagement.model.DeleteInstanceProfileRequest
import com.amazonaws.services.identitymanagement.model.DeleteLoginProfileRequest
import com.amazonaws.services.identitymanagement.model.DeleteRoleRequest
import com.amazonaws.services.identitymanagement.model.DeleteUserPolicyRequest
import com.amazonaws.services.identitymanagement.model.DeleteUserRequest
import com.amazonaws.services.identitymanagement.model.NoSuchEntityException
import com.amazonaws.services.identitymanagement.model.PutUserPolicyRequest
import com.github.sjones4.youcan.youare.YouAre
import com.github.sjones4.youcan.youare.YouAreClient

import static org.junit.Assert.assertTrue
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Test

/**
 * Tests name limits for IAM resources.
 *
 * Related issue:
 *   https://eucalyptus.atlassian.net/browse/EUCA-8971
 *
 * Related AWS doc:
 *   http://docs.aws.amazon.com/IAM/latest/UserGuide/reference_iam-limits.html
 *   
 */
class TestIAMLimits {
  private static String testAcct
  private static AWSCredentialsProvider testAcctAdminCredentials

  @BeforeClass
  static void init( ){
    N4j.testInfo(TestIAMLimits.simpleName)
    N4j.getCloudInfo( )
    this.testAcct= "${N4j.NAME_PREFIX}iamlimit-test"
    N4j.createAccount(testAcct)
    this.testAcctAdminCredentials = new StaticCredentialsProvider( N4j.getUserCreds(testAcct, 'admin') )
  }

  @AfterClass
  static void cleanup( ) {
    N4j.deleteAccount(testAcct)
  }

  private YouAre getYouAre(final AWSCredentialsProvider credentials ) {
    final YouAre youAre = new YouAreClient( credentials )
    youAre.setEndpoint( N4j.IAM_ENDPOINT )
    youAre
  }

  @Test
  void test() throws Exception {
    final String namePrefix = N4j.NAME_PREFIX
    N4j.print( "Using resource prefix for test: ${namePrefix}" )

    final List<Runnable> cleanupTasks = [] as List<Runnable>
    try {
      getYouAre( testAcctAdminCredentials ).with {
        String userName = "${namePrefix}user1"
        cleanupTasks.add{
          N4j.print( "Deleting user ${userName}" )
          deleteUser( new DeleteUserRequest(
              userName: userName
          ) )
        }
        N4j.print( "Creating user ${userName} with invalid (long) path" )
        try {
          createUser( new CreateUserRequest(
              userName: userName,
              path: "/${('a' * 512)}/"
          ) )
          assertTrue('Expected user creation failure due to invalid path', false)
        } catch ( AmazonServiceException e ) {
          N4j.print( "Exception for invalid path : ${e}" )
          assertTrue("Expected ValidationError, but was ${e.errorCode}", 'ValidationError' == e.errorCode)
        }

        String userNameLong = "${userName}${('a' * 64)}"
        N4j.print( "Creating user ${userNameLong} with invalid (long) name" )
        try {
          createUser( new CreateUserRequest(
              userName: userNameLong,
              path: '/'
          ) )
          cleanupTasks.add{
            N4j.print( "Deleting user ${userNameLong}" )
            deleteUser( new DeleteUserRequest(
                userName: userNameLong
            ) )
          }
          assertTrue('Expected user creation failure due to invalid name', false)
        } catch ( AmazonServiceException e ) {
          N4j.print( "Exception for invalid user name : ${e}" )
          assertTrue("Expected ValidationError, but was ${e.errorCode}", 'ValidationError' == e.errorCode)
        }

        N4j.print( "Creating user ${userName}" )
        createUser( new CreateUserRequest(
            userName: userName,
            path: '/'
        ) )

        String groupName = "${namePrefix}group1"
        String groupNameLong = "${groupName}${('a' * 128)}"
        N4j.print( "Creating group ${groupNameLong} with invalid (long) name" )
        try {
          createGroup( new CreateGroupRequest(
              groupName: groupNameLong,
              path: '/'
          ) )
          cleanupTasks.add{
            N4j.print( "Deleting group ${groupNameLong}" )
            deleteGroup( new DeleteGroupRequest(
                groupName: groupNameLong
            ) )
          }
          assertTrue('Expected group creation failure due to invalid name', false)
        } catch ( AmazonServiceException e ) {
          N4j.print( "Exception for invalid group name : ${e}" )
          assertTrue("Expected ValidationError, but was ${e.errorCode}", 'ValidationError' == e.errorCode)
        }

        N4j.print( "Creating group ${groupName}" )
        createGroup( new CreateGroupRequest(
            groupName: groupName,
            path: '/'
        ) )
        cleanupTasks.add{
          N4j.print( "Deleting group ${groupName}" )
          deleteGroup( new DeleteGroupRequest(
              groupName: groupName
          ) )
        }

        String roleName = "${namePrefix}role1"
        String roleNameLong = "${roleName}${('a' * 64)}"
        N4j.print( "Creating role ${roleNameLong} with invalid (long) name" )
        try {
          createRole( new CreateRoleRequest(
              roleName: roleNameLong,
              path: '/',
              assumeRolePolicyDocument: """\
                {
                    "Statement": [ {
                      "Effect": "Allow",
                      "Principal": {
                         "Service": [ "ec2.amazonaws.com" ]
                      },
                      "Action": [ "sts:AssumeRole" ]
                    } ]
                }
                """.stripIndent( )
          ) )
          cleanupTasks.add{
            N4j.print( "Deleting group ${groupNameLong}" )
            deleteGroup( new DeleteGroupRequest(
                groupName: groupNameLong
            ) )
          }
          assertTrue('Expected group creation failure due to invalid name', false)
        } catch ( AmazonServiceException e ) {
          N4j.print( "Exception for invalid group name : ${e}" )
          assertTrue("Expected ValidationError, but was ${e.errorCode}", 'ValidationError' == e.errorCode)
        }        
        
        N4j.print( "Creating role ${roleName}" )
        createRole( new CreateRoleRequest(
            roleName: roleName,
            path: '/',
            assumeRolePolicyDocument: """\
            {
                "Statement": [ {
                  "Effect": "Allow",
                  "Principal": {
                     "Service": [ "ec2.amazonaws.com" ]
                  },
                  "Action": [ "sts:AssumeRole" ]
                } ]
            }
            """.stripIndent( )
        ) )
        cleanupTasks.add{
          N4j.print( "Deleting role ${roleName}" )
          deleteRole( new DeleteRoleRequest(
              roleName: roleName
          ) )
        }

        String instanceProfileName = "${namePrefix}instance-profile1"
        String instanceProfileNameLong = "${instanceProfileName}${('a' * 128)}"
        N4j.print( "Creating instance profile ${instanceProfileNameLong} with invalid (long) name" )
        try {
          createInstanceProfile( new CreateInstanceProfileRequest(
              instanceProfileName: instanceProfileNameLong,
              path: '/'
          ) )
          cleanupTasks.add{
            N4j.print( "Deleting instance profile ${instanceProfileNameLong}" )
            deleteInstanceProfile( new DeleteInstanceProfileRequest(
                instanceProfileName: instanceProfileNameLong
            ) )
          }
          assertTrue('Expected instance profile creation failure due to invalid name', false)
        } catch ( AmazonServiceException e ) {
          N4j.print( "Exception for invalid instance profile name : ${e}" )
          assertTrue("Expected ValidationError, but was ${e.errorCode}", 'ValidationError' == e.errorCode)
        }
        
        N4j.print( "Creating instance profile ${instanceProfileName}" )
        createInstanceProfile( new CreateInstanceProfileRequest(
            instanceProfileName: instanceProfileName,
            path: '/'
        ) )
        cleanupTasks.add{
          N4j.print( "Deleting instance profile ${instanceProfileName}" )
          deleteInstanceProfile( new DeleteInstanceProfileRequest(
              instanceProfileName: instanceProfileName
          ) )
        }

        String policyName = "${namePrefix}policy1"
        String policyNameLong = "${policyName}${('a' * 128)}"
        N4j.print( "Creating user policy ${policyNameLong} with invalid (long) name" )
        try {
          putUserPolicy( new PutUserPolicyRequest(
              userName: userName,
              policyName: policyNameLong,
              policyDocument: '''\
              {
                 "Statement":[{
                    "Effect":"Allow",
                    "Action":"ec2:*",
                    "Resource":"*"
                 }]
              }
              '''.stripIndent( )
          ) )
          cleanupTasks.add{
            N4j.print( "Deleting user policy ${policyNameLong}" )
            deleteUserPolicy( new DeleteUserPolicyRequest(
                userName: userName,
                policyName: policyNameLong
            ) )
          }
          assertTrue('Expected user policy creation failure due to invalid name', false)
        } catch ( AmazonServiceException e ) {
          N4j.print( "Exception for invalid policy name : ${e}" )
          assertTrue("Expected ValidationError, but was ${e.errorCode}", 'ValidationError' == e.errorCode)
        }        
        
        N4j.print( "Creating user policy ${policyName}" )
        putUserPolicy( new PutUserPolicyRequest(
            userName: userName,
            policyName: policyName,
            policyDocument: '''\
              {
                 "Statement":[{
                    "Effect":"Allow",
                    "Action":"ec2:*",
                    "Resource":"*"
                 }]
              }
              '''.stripIndent( )
        ) )
        cleanupTasks.add{
          N4j.print( "Deleting user policy ${policyName}" )
          deleteUserPolicy( new DeleteUserPolicyRequest(
              userName: userName,
              policyName: policyName
          ) )
        }
        
        cleanupTasks.add{
          N4j.print( "Deleting login profile for user" )
          deleteLoginProfile( new DeleteLoginProfileRequest(
              userName: userName
          ) )
        }
        N4j.print( "Creating login profile for user with invalid (long) password" )
        try {
          createLoginProfile( new CreateLoginProfileRequest(
              userName: userName,
              password: "aA1-${('a' * 128)}"
          ) )
          assertTrue('Expected login profile creation failure due to invalid password', false)
        } catch ( AmazonServiceException e ) {
          N4j.print( "Exception for invalid password : ${e}" )
          assertTrue("Expected ValidationError, but was ${e.errorCode}", 'ValidationError' == e.errorCode)
        }

        N4j.print( "Creating login profile for user" )
        createLoginProfile( new CreateLoginProfileRequest(
            userName: userName,
            password: "aA1-${('a' * 32)}"
        ) )
        
        N4j.print( "Setting invalid (long) alias for account" )
        try {
          createAccountAlias( new CreateAccountAliasRequest(
              accountAlias: "${namePrefix}${('a' * 63)}"
          ) )
          assertTrue('Expected account alias creation failure due to invalid alias', false)
        } catch ( AmazonServiceException e ) {
          N4j.print( "Exception for invalid account alias : ${e}" )
          assertTrue("Expected ValidationError, but was ${e.errorCode}", 'ValidationError' == e.errorCode)
        }
        
        N4j.print( "Sleeping to allow resource creation to complete ..." )
        sleep( 30000 )
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
