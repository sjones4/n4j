package com.eucalyptus.tests.awssdk;

import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.model.CreateQueueRequest;
import com.amazonaws.services.sqs.model.ListQueuesResult;
import com.amazonaws.services.sqs.model.Message;
import com.amazonaws.services.sqs.model.ReceiveMessageResult;
import com.google.common.collect.ImmutableMap;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static com.eucalyptus.tests.awssdk.N4j.*;

/**
 * Created by ethomas on 10/4/16.
 */
public class TestSQSMessageExpirationPeriod {

  private static String account;
  private static String otherAccount;

  private static AmazonSQS accountSQSClient;
  private static AmazonSQS otherAccountSQSClient;

  @BeforeClass
  public static void init() throws Exception {
    print("### PRE SUITE SETUP - " + TestSQSMessageExpirationPeriod.class.getSimpleName());

    try {
      getCloudInfoAndSqs();
      account = "sqs-account-mep-a-" + System.currentTimeMillis();
      synchronizedCreateAccount(account);
      accountSQSClient = getSqsClientWithNewAccount(account, "admin");
      otherAccount = "sqs-account-mep-b-" + System.currentTimeMillis();
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
    print("### POST SUITE CLEANUP - " + TestSQSMessageExpirationPeriod.class.getSimpleName());
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
  public void testMessageExpirationPeriod() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testMessageExpirationPeriod");
    String queueName = "queue_name_message_expiration_period";
    int errorSecs = 5;

    Map<String, String> attributeMap = new HashMap<String, String>();
    attributeMap.put("VisibilityTimeout","0");
    attributeMap.put("MessageRetentionPeriod", "70");
    CreateQueueRequest createQueueRequest = new CreateQueueRequest();
    createQueueRequest.setAttributes(attributeMap);
    createQueueRequest.setQueueName(queueName);
    String queueUrl = accountSQSClient.createQueue(createQueueRequest).getQueueUrl();
    String messageId1 = accountSQSClient.sendMessage(queueUrl, "hello").getMessageId();
    long startTime1Secs = System.currentTimeMillis() / 1000;
    accountSQSClient.setQueueAttributes(queueUrl, ImmutableMap.of("MessageRetentionPeriod", "60"));
    String messageId2 = accountSQSClient.sendMessage(queueUrl, "hello").getMessageId();
    long startTime2Secs = System.currentTimeMillis() / 1000;

    long endTime1Secs = startTime1Secs;
    long endTime2Secs = startTime2Secs;

    // Poll for 80 seconds
    for (int i = 0; i < 75; i++) {
      long startTimeLoop = System.currentTimeMillis();
      ReceiveMessageResult receiveMessageResult = accountSQSClient.receiveMessage(queueUrl);
      if (receiveMessageResult != null && receiveMessageResult.getMessages() != null) {
        for (Message message: receiveMessageResult.getMessages()) {
          long nowSecs = System.currentTimeMillis() / 1000;
          if (message.getMessageId().equals(messageId1)) {
            endTime1Secs = nowSecs;
          } else if (message.getMessageId().equals(messageId2)) {
            endTime2Secs = nowSecs;
          }
        }
      }
      long sleepTime = 1000 - (System.currentTimeMillis() - startTimeLoop);
      Thread.sleep(sleepTime > 0 ? sleepTime : 1L);
    }

    // first message should have been gone after 75 seconds, and the second one gone after 60 seconds
    assertThat(Math.abs(70 - (endTime1Secs - startTime1Secs)) < errorSecs, "First message should be gone after 70 seconds");
    assertThat(Math.abs(60 - (endTime2Secs - startTime2Secs)) < errorSecs, "Second message should be gone after 60 seconds");
  }

}
