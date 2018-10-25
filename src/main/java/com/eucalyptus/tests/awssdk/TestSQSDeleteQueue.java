package com.eucalyptus.tests.awssdk;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.model.ListQueuesResult;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.net.URL;
import java.util.List;

import static com.eucalyptus.tests.awssdk.N4j.*;

/**
 * Created by ethomas on 9/21/16.
 */
public class TestSQSDeleteQueue {

  private static String account;
  private static String otherAccount;

  private static AmazonSQS accountSQSClient;
  private static AmazonSQS otherAccountSQSClient;

  @BeforeClass
  public static void init() throws Exception {
    print("### PRE SUITE SETUP - " + TestSQSDeleteQueue.class.getSimpleName());

    try {
      getCloudInfoAndSqs();
      account = "sqs-account-dq-a-" + System.currentTimeMillis();
      synchronizedCreateAccount(account);
      accountSQSClient = getSqsClientWithNewAccount(account, "admin");
      otherAccount = "sqs-account-dq-b-" + System.currentTimeMillis();
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

  @AfterClass
  public static void teardown() {
    print("### POST SUITE CLEANUP - " + TestSQSDeleteQueue.class.getSimpleName());
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

  @Test
  public void testDeleteQueueOtherAccount() {
    testInfo(this.getClass().getSimpleName() + " - testDeleteQueueOtherAccount");
    String queueName = "queue_name_delete_other_account";
    String queueUrl = accountSQSClient.createQueue(queueName).getQueueUrl();
    String otherAccountQueueUrl = otherAccountSQSClient.createQueue(queueName).getQueueUrl();
    try {
      accountSQSClient.deleteQueue(otherAccountQueueUrl);
      assertThat(false, "Should fail deleting queue on different account");
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == 403, "Correctly fail deleting queue on different account");
    }
  }

  @Test
  public void testDeleteQueueNonExistentAccount() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testDeleteQueueNonExistentAccount");
    String queueName = "queue_name_delete_nonexistent_account";

    String queueUrl = accountSQSClient.createQueue(queueName).getQueueUrl();
    List<String> pathParts = Lists.newArrayList(Splitter.on('/').omitEmptyStrings().split(new URL(queueUrl).getPath()));
    String accountId = pathParts.get(0);

    try {
      accountSQSClient.deleteQueue(queueUrl.replace(accountId, "000000000000"));
      assertThat(false, "Should fail deleting queue non-existent user");
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == 404, "Correctly fail deleting a queue from a non-existent user");
    }
  }

  @Test
  public void testDeleteNonExistentQueue() {
    testInfo(this.getClass().getSimpleName() + " - testDeleteNonExistentQueue");
    String queueName = "queue_name_delete_nonexistent_queue";
    String queueUrl = accountSQSClient.createQueue(queueName).getQueueUrl();
    try {
      accountSQSClient.deleteQueue(queueUrl + "-bogus");
      assertThat(false, "Should fail deleting non-existent queue");
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == 400, "Correctly fail deleting non-existent queue");
    }
  }

  @Test
  public void testDeleteQueue() {
    testInfo(this.getClass().getSimpleName() + " - testDeleteQueue");
    String queueName = "queue_name_delete";
    String queueUrl = accountSQSClient.createQueue(queueName).getQueueUrl();
    accountSQSClient.deleteQueue(queueUrl);
  }

  @Test
  public void testDeleteQueueWithMessages() {
    testInfo(this.getClass().getSimpleName() + " - testDeleteQueueWithMessages");
    String queueName = "queue_name_delete_with_messages";
    String queueUrl = accountSQSClient.createQueue(queueName).getQueueUrl();
    accountSQSClient.sendMessage(queueUrl,"1");
    accountSQSClient.sendMessage(queueUrl,"2");
    accountSQSClient.sendMessage(queueUrl,"3");
    accountSQSClient.deleteQueue(queueUrl);
  }

  @Test
  public void testDeleteAlreadyDeletedQueue() {
    testInfo(this.getClass().getSimpleName() + " - testDeleteAlreadyDeletedQueue");
    String queueName = "queue_name_delete_already_deleted_queue";
    String queueUrl = accountSQSClient.createQueue(queueName).getQueueUrl();
    accountSQSClient.deleteQueue(queueUrl);
    try {
      accountSQSClient.deleteQueue(queueUrl);
      assertThat(false, "Should fail deleting already deleted queue");
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == 400, "Correctly fail deleting already deleted queue");
    }
  }

}
