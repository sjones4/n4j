package com.eucalyptus.tests.awssdk;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.model.ListQueuesResult;
import com.google.common.collect.Sets;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Set;

import static com.eucalyptus.tests.awssdk.N4j.*;

/**
 * Created by ethomas on 10/4/16.
 */
public class TestSQSAdminFunctions {

  private static String account;
  private static String otherAccount;
  private static AmazonSQS accountSQSClient;
  private static AmazonSQS otherAccountSQSClient;

  @BeforeClass
  public static void init() throws Exception {
    print("### PRE SUITE SETUP - " + TestSQSAdminFunctions.class.getSimpleName());

    try {
      getCloudInfoAndSqs();
      account = "sqs-account-admf-a-" + System.currentTimeMillis();
      synchronizedCreateAccount(account);
      accountSQSClient = getSqsClientWithNewAccount(account, "admin");
      otherAccount = "sqs-account-admf-b-" + System.currentTimeMillis();
      synchronizedCreateAccount(otherAccount);
      otherAccountSQSClient = getSqsClientWithNewAccount(otherAccount, "admin");
    } catch (Exception e) {
      try {
        teardown();
      } catch (Exception ignore) {
      }
      throw e;
    }
  }

  @Test
  public void testAdminFunctions( ) {
    testInfo(this.getClass().getSimpleName() + " - testAdminFunctions");
    String queueName = "queue_name_admin_functions";
    String queueUrl = accountSQSClient.createQueue(queueName).getQueueUrl();
    // see if the admin can see this queue by listing queues verbose
    Set<String> allQueueUrls = Sets.newHashSet();
    allQueueUrls.addAll(sqs.listQueues("verbose").getQueueUrls());
    assertThat(allQueueUrls.contains(queueUrl), "Admin list queues (verbose) should see the other queue url");
    try {
      otherAccountSQSClient.deleteQueue(queueUrl);
      assertThat(false, "Should fail deleting queue on different account");
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == 403, "Correctly fail deleting queue on different account");
    }
    sqs.deleteQueue(queueUrl);
    // should work
    allQueueUrls.clear();
    allQueueUrls.addAll(sqs.listQueues("verbose").getQueueUrls());
    assertThat(!allQueueUrls.contains(queueUrl), "Admin list queues (verbose) should no longer see the other queue url");
  }

  @AfterClass
  public static void teardown( ) {
    print("### POST SUITE CLEANUP - " + TestSQSAdminFunctions.class.getSimpleName());
    if (account != null) {
      if (accountSQSClient != null) {
        ListQueuesResult listQueuesResult = accountSQSClient.listQueues();
        if (listQueuesResult != null) {
          listQueuesResult.getQueueUrls().forEach(accountSQSClient::deleteQueue);
        }
      }
      synchronizedDeleteAccount(account);
    }
    if (otherAccount != null) {
      if (otherAccountSQSClient != null) {
        ListQueuesResult listQueuesResult = otherAccountSQSClient.listQueues();
        if (listQueuesResult != null) {
          listQueuesResult.getQueueUrls().forEach(otherAccountSQSClient::deleteQueue);
        }
      }
      synchronizedDeleteAccount(otherAccount);
    }
  }

}