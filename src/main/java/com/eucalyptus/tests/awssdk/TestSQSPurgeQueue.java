package com.eucalyptus.tests.awssdk;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.model.CreateQueueRequest;
import com.amazonaws.services.sqs.model.ListQueuesResult;
import com.amazonaws.services.sqs.model.PurgeQueueRequest;
import com.amazonaws.services.sqs.model.ReceiveMessageResult;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import org.testng.annotations.Test;

import java.net.URL;
import java.util.List;
import java.util.UUID;

import static com.eucalyptus.tests.awssdk.N4j.*;

/**
 * Created by ethomas on 9/21/16.
 */
public class TestSQSPurgeQueue {

  @Test
  public void testPurgeQueue() throws Exception {
    getCloudInfoAndSqs();

    String prefix = UUID.randomUUID().toString() + "-" + System.currentTimeMillis() + "-";
    String otherAccount = "account" + System.currentTimeMillis();
    createAccount(otherAccount);
    AmazonSQS otherSQS = getSqsClientWithNewAccount(otherAccount, "admin");

    try {
      // first create a queue
      String suffix = "-purge-queue-test";
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

      print("Testing purging queue in non-existant account, should fail");
      try {
        purgeQueue(queueUrl.replace(accountId, "000000000000"));
        assertThat(false, "Should fail purging queue non-existent user");
      } catch (AmazonServiceException e) {
        assertThat(e.getStatusCode() == 404, "Correctly fail purging a queue from a non-existent user");
      }

      print("Testing purging queue on different account, should fail");
      // Now try to receive message from an account with no access
      try {
        purgeQueue(otherAccountQueueUrl);
        assertThat(false, "Should fail purging queue on different account");
      } catch (AmazonServiceException e) {
        assertThat(e.getStatusCode() == 403, "Correctly fail purging queue on different account");
      }

      print("Testing purging non-existent queue, should fail");
      try {
        purgeQueue(queueUrl + "-bogus");
        assertThat(false, "Should fail purging non-existent queue");
      } catch (AmazonServiceException e) {
        assertThat(e.getStatusCode() == 400, "Correctly fail purging non-existent queue");
      }

      print("Testing purging this queue");
      purgeQueue(queueUrl);

      print("Testing purging queue with messages");
      queueName = queueName + "-messages";
      queueUrl = sqs.createQueue(queueName).getQueueUrl();
      sqs.sendMessage(queueUrl,"1");
      sqs.sendMessage(queueUrl,"2");
      sqs.sendMessage(queueUrl,"3");
      purgeQueue(queueUrl);
      // see if any messages
      print("Testing queue to see if any messages (should be gone -- might take a while to see)");
      String receiptHandle = receiveMessageHandle(sqs, queueUrl);
      assertThat(receiptHandle == null, "Message should be gone");


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

  private void purgeQueue(String queueUrl) {
    PurgeQueueRequest purgeQueueRequest = new PurgeQueueRequest();
    purgeQueueRequest.setQueueUrl(queueUrl);
    sqs.purgeQueue(purgeQueueRequest);

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
