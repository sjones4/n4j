package com.eucalyptus.tests.awssdk;

import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.model.CreateQueueRequest;
import com.amazonaws.services.sqs.model.ListQueuesResult;
import com.amazonaws.services.sqs.model.Message;
import com.amazonaws.services.sqs.model.ReceiveMessageRequest;
import com.amazonaws.services.sqs.model.ReceiveMessageResult;
import com.amazonaws.services.sqs.model.SendMessageRequest;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Optional;
import org.testng.annotations.Parameters;
import org.testng.annotations.Test;

import java.util.Collections;
import java.util.Map;

import static com.eucalyptus.tests.awssdk.N4j.*;

/**
 * Created by ethomas on 10/4/16.
 */
public class TestSQSVisibilityTimeout {

  private String account;
  private String otherAccount;

  private AmazonSQS accountSQSClient;
  private AmazonSQS otherAccountSQSClient;

  @BeforeClass
  public void init() throws Exception {
    print("### PRE SUITE SETUP - " + this.getClass().getSimpleName());

    try {
      getCloudInfoAndSqs();
      account = "sqs-account-vt-a-" + System.currentTimeMillis();
      synchronizedCreateAccount(account);
      accountSQSClient = getSqsClientWithNewAccount(account, "admin");
      otherAccount = "sqs-account-vt-b-" + System.currentTimeMillis();
      synchronizedCreateAccount(otherAccount);
      otherAccountSQSClient = getSqsClientWithNewAccount(otherAccount, "admin");
    } catch (Exception e) {
      try {
        teardown();
      } catch (Exception ie) {
      }
      throw e;
    }
  }

  @AfterClass
  public void teardown() throws Exception {
    print("### POST SUITE CLEANUP - " + this.getClass().getSimpleName());
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
  @Parameters("concise")
  public void testVisibilityTimeout(@Optional("false") boolean concise) throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testVisibilityTimeout");
    String queueName = "queue_name_message_delay";
    int errorSecs = 5;

    int baseDelay = 15;
    if (concise) baseDelay = 10;
    // start with a delay seconds of base_delay seconds
    CreateQueueRequest createQueueRequest = new CreateQueueRequest();
    createQueueRequest.setQueueName(queueName);
    createQueueRequest.getAttributes().put("VisibilityTimeout", String.valueOf(baseDelay));
    String queueUrl = accountSQSClient.createQueue(createQueueRequest).getQueueUrl();

    String messageId = accountSQSClient.sendMessage(queueUrl, "hello").getMessageId();
    String receiptHandle = waitUntilReceiveMessage(accountSQSClient, queueUrl, messageId);
    long firstReceiveTime = System.currentTimeMillis() / 1000L;
    // wait until we get a message id
    receiptHandle = waitUntilReceiveMessage(accountSQSClient, queueUrl, messageId);
    long secondReceiveTime = System.currentTimeMillis() / 1000L;
    assertThat(Math.abs(baseDelay - (secondReceiveTime - firstReceiveTime)) < errorSecs, "Should receive the second time around " + baseDelay+ " secs after the first");

    // change the delay seconds (shouldn't affect the current time)
    accountSQSClient.setQueueAttributes(queueUrl, ImmutableMap.of("VisibilityTimeout", String.valueOf(2 * baseDelay)));
    receiptHandle = waitUntilReceiveMessage(accountSQSClient, queueUrl, messageId);
    long thirdReceiveTime = System.currentTimeMillis() / 1000L;
    assertThat(Math.abs(baseDelay - (thirdReceiveTime - secondReceiveTime)) < errorSecs, "Should receive the third time around " + baseDelay + " secs after the second");

    // test override
    receiptHandle = waitUntilReceiveMessage(accountSQSClient, queueUrl, messageId, baseDelay);
    long fourthReceiveTime = System.currentTimeMillis() / 1000L;
    assertThat(Math.abs(2 * baseDelay - (fourthReceiveTime - thirdReceiveTime)) < errorSecs, "Should receive the fourth time around " + (2 *baseDelay ) + " secs after the third");

    receiptHandle = waitUntilReceiveMessage(accountSQSClient, queueUrl, messageId);
    long fifthReceiveTime = System.currentTimeMillis() / 1000L;
    assertThat(Math.abs(baseDelay - (fifthReceiveTime - fourthReceiveTime)) < errorSecs, "Should receive the fifth time around " + (2 * baseDelay) + " secs after the fourth");
    if (!concise) { // no idea why this sleep is actually here, but keeping it for old compatibility, I guess
      Thread.sleep(25000L);
    }
    // test change message visibility
    accountSQSClient.changeMessageVisibility(queueUrl, receiptHandle, 3 * baseDelay);
    long startChangeMessageVisibility = System.currentTimeMillis() / 1000L;
    receiptHandle = waitUntilReceiveMessage(accountSQSClient, queueUrl, messageId);
    long sixthReceiveTime = System.currentTimeMillis() / 1000L;
    assertThat(Math.abs(3 * baseDelay - (sixthReceiveTime - startChangeMessageVisibility)) < errorSecs, "Should receive the sixth time around " + (3 * baseDelay) +" secs after changing the message availability");
  }

  private String waitUntilReceiveMessage(AmazonSQS sqsClient, String queueUrl, String messageId) throws InterruptedException {
    return waitUntilReceiveMessage(sqsClient, queueUrl, messageId, null);
  }

  private String waitUntilReceiveMessage(AmazonSQS sqsClient, String queueUrl, String messageId, Integer overrideVisibilityTimeout) throws InterruptedException {
    for (int i = 0; i < 60; i++) {
      ReceiveMessageRequest receiveMessageRequest = new ReceiveMessageRequest();
      if (overrideVisibilityTimeout != null) {
        receiveMessageRequest.setVisibilityTimeout(overrideVisibilityTimeout);
      }
      receiveMessageRequest.setQueueUrl(queueUrl);
      ReceiveMessageResult receiveMessageResult = sqsClient.receiveMessage(receiveMessageRequest);
      if (receiveMessageResult != null && receiveMessageResult.getMessages() != null) {
        for (Message message: receiveMessageResult.getMessages()) {
          if (message.getMessageId().equals(messageId)) {
            return message.getReceiptHandle();
          }
        }
      }
      Thread.sleep(1000L);
    }
    throw new InterruptedException("timeout waiting for message");
  }

}
