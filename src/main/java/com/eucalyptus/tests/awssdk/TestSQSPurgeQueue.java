package com.eucalyptus.tests.awssdk;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.model.CreateQueueRequest;
import com.amazonaws.services.sqs.model.ListQueuesResult;
import com.amazonaws.services.sqs.model.PurgeQueueRequest;
import com.amazonaws.services.sqs.model.ReceiveMessageResult;
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
public class TestSQSPurgeQueue {

  private static String account;
  private static String otherAccount;

  private static AmazonSQS accountSQSClient;
  private static AmazonSQS otherAccountSQSClient;

  @BeforeClass
  public static void init() throws Exception {
    print("### PRE SUITE SETUP - " + TestSQSPurgeQueue.class.getSimpleName());

    try {
      getCloudInfoAndSqs();
      account = "sqs-account-pq-a-" + System.currentTimeMillis();
      synchronizedCreateAccount(account);
      accountSQSClient = getSqsClientWithNewAccount(account, "admin");
      otherAccount = "sqs-account-pq-b-" + System.currentTimeMillis();
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
    print("### POST SUITE CLEANUP - " + TestSQSPurgeQueue.class.getSimpleName());
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
  public void testPurgeQueueOtherAccount() {
    testInfo(this.getClass().getSimpleName() + " - testPurgeQueueOtherAccount");
    String queueName = "queue_name_purge_other_account";
    String queueUrl = accountSQSClient.createQueue(queueName).getQueueUrl();
    String otherAccountQueueUrl = otherAccountSQSClient.createQueue(queueName).getQueueUrl();
    try {
      PurgeQueueRequest purgeQueueRequest = new PurgeQueueRequest();
      purgeQueueRequest.setQueueUrl(otherAccountQueueUrl);
      accountSQSClient.purgeQueue(purgeQueueRequest);
      assertThat(false, "Should fail purging queue on different account");
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == 403, "Correctly fail purging queue on different account");
    }
  }

  @Test
  public void testPurgeQueueNonExistentAccount() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testPurgeQueueNonExistentAccount");
    String queueName = "queue_name_purge_nonexistent_account";

    String queueUrl = accountSQSClient.createQueue(queueName).getQueueUrl();
    List<String> pathParts = Lists.newArrayList(Splitter.on('/').omitEmptyStrings().split(new URL(queueUrl).getPath()));
    String accountId = pathParts.get(0);

    try {
      PurgeQueueRequest purgeQueueRequest = new PurgeQueueRequest();
      purgeQueueRequest.setQueueUrl(queueUrl.replace(accountId, "000000000000"));
      accountSQSClient.purgeQueue(purgeQueueRequest);

      assertThat(false, "Should fail purging queue non-existent user");
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == 404, "Correctly fail purging a queue from a non-existent user");
    }
  }

  @Test
  public void testPurgeNonExistentQueue() {
    testInfo(this.getClass().getSimpleName() + " - testPurgeNonExistentQueue");
    String queueName = "queue_name_purge_nonexistent_queue";
    String queueUrl = accountSQSClient.createQueue(queueName).getQueueUrl();
    try {
      PurgeQueueRequest purgeQueueRequest = new PurgeQueueRequest();
      purgeQueueRequest.setQueueUrl(queueUrl + "-bogus");
      accountSQSClient.purgeQueue(purgeQueueRequest);
      assertThat(false, "Should fail purging non-existent queue");
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == 400, "Correctly fail purging non-existent queue");
    }
  }

  @Test
  public void testPurgeQueue() {
    testInfo(this.getClass().getSimpleName() + " - testPurgeQueue");
    String queueName = "queue_name_purge";
    String queueUrl = accountSQSClient.createQueue(queueName).getQueueUrl();
    PurgeQueueRequest purgeQueueRequest = new PurgeQueueRequest();
    purgeQueueRequest.setQueueUrl(queueUrl);
    accountSQSClient.purgeQueue(purgeQueueRequest);
  }

  @Test
  public void testPurgeQueueWithMessages() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testPurgeQueueWithMessages");
    String queueName = "queue_name_purge_with_messages";
    CreateQueueRequest createQueueRequest = new CreateQueueRequest();
    createQueueRequest.setQueueName(queueName);
    createQueueRequest.getAttributes().put("VisibilityTimeout","0"); // messages should show up immediately
    createQueueRequest.getAttributes().put("DelaySeconds","0"); // messages should show up immediately
    String queueUrl = accountSQSClient.createQueue(queueName).getQueueUrl();
    accountSQSClient.sendMessage(queueUrl,"1");
    accountSQSClient.sendMessage(queueUrl,"2");
    accountSQSClient.sendMessage(queueUrl,"3");
    PurgeQueueRequest purgeQueueRequest = new PurgeQueueRequest();
    purgeQueueRequest.setQueueUrl(queueUrl);
    accountSQSClient.purgeQueue(purgeQueueRequest);

    // try receiving messages for a while (say 15 seconds)
    long startTime = System.currentTimeMillis();
    while ((System.currentTimeMillis() - startTime) < 15000L) {
      ReceiveMessageResult receiveMessageResult = accountSQSClient.receiveMessage(queueUrl);
      if (receiveMessageResult != null && receiveMessageResult.getMessages() != null && receiveMessageResult.getMessages().size() > 0) {
        assertThat(false, "Should have no more messages");
      }
      Thread.sleep(1000L);
    }
  }

}
