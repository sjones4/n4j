package com.eucalyptus.tests.awssdk;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.model.CreateQueueRequest;
import com.amazonaws.services.sqs.model.ListQueuesResult;
import com.amazonaws.services.sqs.model.Message;
import com.amazonaws.services.sqs.model.MessageAttributeValue;
import com.amazonaws.services.sqs.model.ReceiveMessageRequest;
import com.amazonaws.services.sqs.model.ReceiveMessageResult;
import com.amazonaws.services.sqs.model.SendMessageRequest;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.testng.annotations.Test;

import java.io.IOException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import static com.eucalyptus.tests.awssdk.N4j.*;

/**
 * Created by ethomas on 9/21/16.
 */
public class TestSQSDeleteMessage {

  @Test
  public void testDeleteMessages() throws Exception {
    getCloudInfoAndSqs();

    String prefix = UUID.randomUUID().toString() + "-" + System.currentTimeMillis() + "-";
    String otherAccount = "account" + System.currentTimeMillis();
    createAccount(otherAccount);
    AmazonSQS otherSQS = getSqsClientWithNewAccount(otherAccount, "admin");

    try {
      // first create a queue
      String suffix = "-delete-message-test";
      String queueName = prefix + suffix;
      CreateQueueRequest createQueueRequest = new CreateQueueRequest();
      createQueueRequest.setQueueName(queueName);
      createQueueRequest.addAttributesEntry("VisibilityTimeout","5");
      String queueUrl = sqs.createQueue(createQueueRequest).getQueueUrl();
      print("Creating queue");
      // first make sure we have a queue url with an account id and a queue name
      List<String> pathParts = Lists.newArrayList(Splitter.on('/').omitEmptyStrings().split(new URL(queueUrl).getPath()));
      assertThat(pathParts.size() == 2, "The queue URL path needs two 'suffix' values: account id, and queue name");
      assertThat(pathParts.get(1).equals(queueName), "The queue URL path needs to end in the queue name");
      String accountId = pathParts.get(0);
      print("accountId="+accountId);

      print("Creating queue in other account");
      String otherAccountQueueUrl = otherSQS.createQueue(queueName).getQueueUrl();
      print("Testing simple send/receive");

      // first send and receive a message and get a receipt handle
      sqs.sendMessage(queueUrl, "hello");
      String firstReceiptHandle = receiveMessageHandle(sqs, queueUrl);
      assertThat(firstReceiptHandle != null, "Should receive a message after sending it");

      print("Testing delete message on non-existant account, should fail");
      try {
        sqs.deleteMessage(queueUrl.replace(accountId, "000000000000"), firstReceiptHandle);
        assertThat(false, "Should fail deleting message on a queue from a non-existent user");
      } catch (AmazonServiceException e) {
        assertThat(e.getStatusCode() == 404, "Correctly fail deleting a message on a queue from a non-existent user");
      }

      print("Testing deleting message on different account, should fail");
      try {
        sqs.deleteMessage(otherAccountQueueUrl, firstReceiptHandle);
        assertThat(false, "Should fail deleting a message on a queue from a different user");
      } catch (AmazonServiceException e) {
        assertThat(e.getStatusCode() == 403, "Correctly fail deleting a message on a queue from a different user");
      }

      print("Testing deleting message to non-existent queue, should fail");
      try {
        sqs.deleteMessage(queueUrl + "-bogus", firstReceiptHandle);
        assertThat(false, "Should fail deleting a message on non-existent queue");
      } catch (AmazonServiceException e) {
        assertThat(e.getStatusCode() == 400, "Correctly fail deleting a message on non-existent queue");
      }

      // Now mess with receipt handles
      Collection<String> bogusReceiptHandles = Lists.newArrayList(
        "bob", "a:b:c:d:e:f", "toot-toot", firstReceiptHandle + "-again",
        firstReceiptHandle.replaceAll(accountId, "000000000000"),
        firstReceiptHandle.replaceAll(accountId, otherAccount)
      );
      for (String bogusReceiptHandle: bogusReceiptHandles) {
        try {
          print("Trying to delete message with bogus receipt handle " + bogusReceiptHandle + ", should fail");
          sqs.deleteMessage(queueUrl, bogusReceiptHandle);
          assertThat(false, "Should fail deleting messages with bogus receipt handles");
        } catch (AmazonServiceException e) {
          assertThat(e.getStatusCode() == 404, "Correctly fail deleting messages with bogus receipt handles");
        }
      }

      // get a new receipt handle, then try to delete
      String secondReceiptHandle = receiveMessageHandle(sqs, queueUrl);
      print("Testing delete message with old receipt handle (should still be around)");
      sqs.deleteMessage(queueUrl, firstReceiptHandle);
      String thirdReceiptHandle = receiveMessageHandle(sqs, queueUrl);
      assertThat(thirdReceiptHandle != null, "Old receipt handle should not delete message");
      print("Testing delete message with newest receipt handle (should be gone -- might take a while to see)");
      sqs.deleteMessage(queueUrl, thirdReceiptHandle);
      String fourthReceiptHandle = receiveMessageHandle(sqs, queueUrl);
      assertThat(fourthReceiptHandle == null, "Message should be gone");
    } finally {
      ListQueuesResult listQueuesResult = sqs.listQueues(prefix);
      if (listQueuesResult != null) {
        listQueuesResult.getQueueUrls().forEach(sqs::deleteQueue);
      }
      ListQueuesResult listQueuesResult2 = otherSQS.listQueues(prefix);
      if (listQueuesResult2 != null) {
        listQueuesResult2.getQueueUrls().forEach(otherSQS::deleteQueue);
      }
      deleteAccount(otherAccount);
    }
  }

  private String receiveMessageHandle(AmazonSQS sqs, String queueUrl) throws InterruptedException {
    long startTime = System.currentTimeMillis();
    while ((System.currentTimeMillis() - startTime) < 15000L) {
      ReceiveMessageResult receiveMessageResult = sqs.receiveMessage(queueUrl);
      if (receiveMessageResult != null && receiveMessageResult.getMessages() != null && receiveMessageResult.getMessages().size() > 0) {
        return receiveMessageResult.getMessages().get(0).getReceiptHandle();
      }
      Thread.sleep(1000L);
    }
    return null;
  }

}
