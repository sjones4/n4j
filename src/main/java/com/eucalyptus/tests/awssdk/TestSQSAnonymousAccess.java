package com.eucalyptus.tests.awssdk;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AnonymousAWSCredentials;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClient;
import com.amazonaws.services.sqs.model.ChangeMessageVisibilityBatchRequest;
import com.amazonaws.services.sqs.model.ChangeMessageVisibilityBatchRequestEntry;
import com.amazonaws.services.sqs.model.CreateQueueRequest;
import com.amazonaws.services.sqs.model.DeleteMessageBatchRequest;
import com.amazonaws.services.sqs.model.DeleteMessageBatchRequestEntry;
import com.amazonaws.services.sqs.model.GetQueueUrlRequest;
import com.amazonaws.services.sqs.model.ListDeadLetterSourceQueuesRequest;
import com.amazonaws.services.sqs.model.ListQueuesResult;
import com.amazonaws.services.sqs.model.Message;
import com.amazonaws.services.sqs.model.PurgeQueueRequest;
import com.amazonaws.services.sqs.model.ReceiveMessageRequest;
import com.amazonaws.services.sqs.model.ReceiveMessageResult;
import com.amazonaws.services.sqs.model.SendMessageBatchRequest;
import com.amazonaws.services.sqs.model.SendMessageBatchRequestEntry;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.net.InetAddresses;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static com.eucalyptus.tests.awssdk.N4j.*;

/**
 * Created by ethomas on 10/4/16.
 */
public class TestSQSAnonymousAccess {


  private String account;
  private long authorizationExpiryMs;
  private AmazonSQS accountSQSClient;
  private AmazonSQS anonymousSQSClient;

  private long parseInterval(String interval, long defaultValueMS) {
    try {
      String timePart;
      TimeUnit timeUnit = TimeUnit.MILLISECONDS;
      if (interval.endsWith("ms")) {
        timePart = interval.substring(0, interval.length() - 2);
        timeUnit = TimeUnit.MILLISECONDS;
      } else if (interval.endsWith("s")) {
        timePart = interval.substring(0, interval.length() - 1);
        timeUnit = TimeUnit.SECONDS;
      } else if (interval.endsWith("m")) {
        timePart = interval.substring(0, interval.length() - 1);
        timeUnit = TimeUnit.MINUTES;
      } else if (interval.endsWith("h")) {
        timePart = interval.substring(0, interval.length() - 1);
        timeUnit = TimeUnit.HOURS;
      } else if (interval.endsWith("d")) {
        timePart = interval.substring(0, interval.length() - 1);
        timeUnit = TimeUnit.DAYS;
      } else {
        timePart = interval;
        timeUnit = TimeUnit.MILLISECONDS;
      }
      return timeUnit.toMillis(Long.parseLong(timePart));
    } catch (Exception e) {
      print("Error parsing interval " + interval + ", using " + defaultValueMS);
      return defaultValueMS;
    }
  }

  @BeforeClass
  public void init() throws Exception {
    print("### PRE SUITE SETUP - " + this.getClass().getSimpleName());

    try {
      getCloudInfoAndSqs();
      authorizationExpiryMs = parseInterval(getConfigProperty(LOCAL_EUCTL_FILE, "authentication.authorization_expiry"), 5000L);
      account = "sqs-account-iam-a-" + System.currentTimeMillis();
      synchronizedCreateAccount(account);
      accountSQSClient = getSqsClientWithNewAccount(account, "admin");
      anonymousSQSClient = new AmazonSQSClient(new AnonymousAWSCredentials());
      anonymousSQSClient.setEndpoint(SQS_ENDPOINT);
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
  }

  @Test
  public void testCreateQueue() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testCreateQueue");
    String queueName = "queue_name_create";
    String queueUrl = accountSQSClient.createQueue(queueName).getQueueUrl();
    accountSQSClient.deleteQueue(queueUrl);

    // try to create the queue anonymously
    try {
      anonymousSQSClient.createQueue(queueName).getQueueUrl();
      assertThat(false, "Should not be able to create queue anonymously");
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == 403, "Correctly fail creating a queue anonymously");
    }
  }

  @Test
  public void testDeleteQueue() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testDeleteQueue");
    String queueName = "queue_name_delete";
    String queueUrl = accountSQSClient.createQueue(queueName).getQueueUrl();

    // try to delete the queue anonymously
    try {
      anonymousSQSClient.deleteQueue(queueUrl);
      assertThat(false, "Should not be able to delete queue anonymously without permission");
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == 403, "Correctly fail deleting a queue anonymously without permission");
    }

    String queueArn = accountSQSClient.getQueueAttributes(queueUrl,
      Collections.singletonList("QueueArn")).getAttributes().get("QueueArn");

    grantAnonymousPermission(queueUrl, "DeleteQueue", queueArn);
    // give it some time to propegate
    Thread.sleep(authorizationExpiryMs);
    // still can't delete queue as anonymous
    try {
      anonymousSQSClient.deleteQueue(queueUrl);
      assertThat(false, "Should not be able to delete queue anonymously even with permission");
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == 403, "Correctly fail deleting a queue anonymously even with permission");
    }
  }

  private void grantAnonymousPermission(String queueUrl, String action, String queueArn) {
    accountSQSClient.setQueueAttributes(queueUrl, ImmutableMap.of("Policy",
      "{\n" +
        "  \"Version\": \"2012-10-17\",\n" +
        "  \"Id\": \""+queueArn+"/SQSDefaultPolicy\",\n" +
        "  \"Statement\": [\n" +
        "    {\n" +
        "      \"Sid\": \"label\",\n" +
        "      \"Effect\": \"Allow\",\n" +
        "      \"Principal\": \"*\",\n" +
        "      \"Action\": \"SQS:"+action+"\",\n" +
        "      \"Resource\": \""+queueArn+"\"\n" +
        "    }\n" +
        "  ]\n" +
        "}"));
  }

  @Test
  public void testAddPermission() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testAddPermission");
    final String queueName = "queue_name_add_permission";
    final String queueUrl = accountSQSClient.createQueue(queueName).getQueueUrl();
    final String queueArn = accountSQSClient.getQueueAttributes(queueUrl,
      Collections.singletonList("QueueArn")).getAttributes().get("QueueArn");

    List<String> pathParts = Lists.newArrayList(Splitter.on('/').omitEmptyStrings().split(new URL(queueUrl).getPath()));
    final String accountId = pathParts.get(0);

    // try to add a permission anonymously
    try {
      anonymousSQSClient.addPermission(queueUrl, "label",
        Collections.singletonList(accountId),
        Collections.singletonList("sendmessage"));
      assertThat(false, "Should not be able to add a permission anonymously without permission");
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == 403, "Correctly fail add a permission anonymously without permission");
    }

    grantAnonymousPermission(queueUrl, "AddPermission", queueArn);
    // give it some time to propegate
    Thread.sleep(authorizationExpiryMs);
    try {
      anonymousSQSClient.addPermission(queueUrl, "label",
        Collections.singletonList(accountId),
        Collections.singletonList("sendmessage"));
      assertThat(false, "Should not be able to add a permission anonymously even with permission");
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == 403, "Correctly fail add a permission anonymously even with permission");
    }
  }

  @Test
  public void testRemovePermission() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testRemovePermission");
    final String queueName = "queue_name_remove_permission";
    final String queueUrl = accountSQSClient.createQueue(queueName).getQueueUrl();
    final String queueArn = accountSQSClient.getQueueAttributes(queueUrl,
      Collections.singletonList("QueueArn")).getAttributes().get("QueueArn");

    List<String> pathParts = Lists.newArrayList(Splitter.on('/').omitEmptyStrings().split(new URL(queueUrl).getPath()));
    final String accountId = pathParts.get(0);

    // try to remove a permission anonymously
    try {
      anonymousSQSClient.removePermission(queueUrl, "label");
      assertThat(false, "Should not be able to remove a permission anonymously without permission");
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == 403, "Correctly fail remove a permission anonymously without permission");
    }

    grantAnonymousPermission(queueUrl, "RemovePermission", queueArn);
    // give it some time to propegate
    Thread.sleep(authorizationExpiryMs);
    try {
      anonymousSQSClient.removePermission(queueUrl, "label");
      assertThat(false, "Should not be able to remove a permission anonymously even with permission");
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == 403, "Correctly fail remove a permission anonymously even with permission");
    }
  }

  @Test
  public void testListQueues() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testListQueues");
    accountSQSClient.listQueues();

    // try to list queues anonymously
    try {
      anonymousSQSClient.listQueues();
      assertThat(false, "Should not be able to list queues anonymously");
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == 403, "Correctly fail to list queues anonymously");
    }
  }


  private String sendMessageAndGetReceiptHandle(String queueUrl) {
    String messageId = accountSQSClient.sendMessage(queueUrl, "hello").getMessageId();
    String receiptHandle = null;
    long startTime = System.currentTimeMillis();
    while (receiptHandle == null && System.currentTimeMillis() - startTime < 120000L) {
      ReceiveMessageResult receiveMessageResult = accountSQSClient.receiveMessage(queueUrl);
      if (receiveMessageResult != null && receiveMessageResult.getMessages() != null) {
        for (Message message : receiveMessageResult.getMessages()) {
          if (message.getMessageId().equals(messageId)) {
            receiptHandle = message.getReceiptHandle();
          }
        }
        try {
          Thread.sleep(1000L);
        } catch (InterruptedException e) {
          throw new AmazonServiceException(e.getMessage());
        }
      }
    }
    return receiptHandle;
  }


  @Test
  public void testChangeMessageVisibility() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testChangeMessageVisibility");
    final String queueName = "queue_name_change_message_visibility";
    final String queueUrl = accountSQSClient.createQueue(queueName).getQueueUrl();
    final String queueArn = accountSQSClient.getQueueAttributes(queueUrl,
      Collections.singletonList("QueueArn")).getAttributes().get("QueueArn");
    final String receiptHandle = sendMessageAndGetReceiptHandle(queueUrl);

    // try to change message visibility anonymously
    try {
      anonymousSQSClient.changeMessageVisibility(queueUrl, receiptHandle, 30);
      assertThat(false, "Should not be able to change message visibility anonymously without permission");
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == 403, "Correctly fail changing message visibility anonymously without permission");
    }

    grantAnonymousPermission(queueUrl, "ChangeMessageVisibility", queueArn);
    // give it some time to propegate
    Thread.sleep(authorizationExpiryMs);

    anonymousSQSClient.changeMessageVisibility(queueUrl, receiptHandle, 30);
  }

  @Test
  public void testChangeMessageVisibilityBatch() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testChangeMessageVisibilityBatch");
    final String queueName = "queue_name_change_message_visibility_batch";
    final String queueUrl = accountSQSClient.createQueue(queueName).getQueueUrl();
    final String queueArn = accountSQSClient.getQueueAttributes(queueUrl,
      Collections.singletonList("QueueArn")).getAttributes().get("QueueArn");
    final String receiptHandle = sendMessageAndGetReceiptHandle(queueUrl);

    ChangeMessageVisibilityBatchRequest changeMessageVisibilityBatchRequest = new ChangeMessageVisibilityBatchRequest();
    changeMessageVisibilityBatchRequest.setQueueUrl(queueUrl);
    ChangeMessageVisibilityBatchRequestEntry entry = new ChangeMessageVisibilityBatchRequestEntry();
    entry.setId("id");
    entry.setVisibilityTimeout(30);
    entry.setReceiptHandle(receiptHandle);
    changeMessageVisibilityBatchRequest.getEntries().add(entry);

    // try to change message visibility batch anonymously
    try {
      anonymousSQSClient.changeMessageVisibilityBatch(changeMessageVisibilityBatchRequest);
      assertThat(false, "Should not be able to change message visibility batch anonymously without permission");
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == 403, "Correctly fail changing message visibility batch anonymously without permission");
    }

    grantAnonymousPermission(queueUrl, "ChangeMessageVisibility", queueArn);
    // give it some time to propegate
    Thread.sleep(authorizationExpiryMs);

    anonymousSQSClient.changeMessageVisibilityBatch(changeMessageVisibilityBatchRequest);
  }

  @Test
  public void testDeleteMessage() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testDeleteMessage");
    final String queueName = "queue_name_delete_message";
    final String queueUrl = accountSQSClient.createQueue(queueName).getQueueUrl();
    final String queueArn = accountSQSClient.getQueueAttributes(queueUrl,
      Collections.singletonList("QueueArn")).getAttributes().get("QueueArn");

    final String receiptHandle = sendMessageAndGetReceiptHandle(queueUrl);

    // try to delete the message anonymously
    try {
      anonymousSQSClient.deleteMessage(queueUrl, receiptHandle);
      assertThat(false, "Should not be able to delete a message anonymously without permission");
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == 403, "Correctly fail deleting a message anonymously without permission");
    }

    grantAnonymousPermission(queueUrl, "DeleteMessage", queueArn);
    // give it some time to propegate
    Thread.sleep(authorizationExpiryMs);

    anonymousSQSClient.deleteMessage(queueUrl, receiptHandle);
  }

  @Test
  public void testDeleteMessageBatch() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testDeleteMessageBatch");
    final String queueName = "queue_name_delete_message_batch";
    final String queueUrl = accountSQSClient.createQueue(queueName).getQueueUrl();
    final String queueArn = accountSQSClient.getQueueAttributes(queueUrl,
      Collections.singletonList("QueueArn")).getAttributes().get("QueueArn");

    final String receiptHandle = sendMessageAndGetReceiptHandle(queueUrl);

    // try to delete the message batch
    DeleteMessageBatchRequest deleteMessageBatchRequest = new DeleteMessageBatchRequest();
    deleteMessageBatchRequest.setQueueUrl(queueUrl);
    DeleteMessageBatchRequestEntry entry = new DeleteMessageBatchRequestEntry();
    entry.setId("id");
    entry.setReceiptHandle(receiptHandle);
    deleteMessageBatchRequest.getEntries().add(entry);

    try {
      anonymousSQSClient.deleteMessageBatch(deleteMessageBatchRequest);
      assertThat(false, "Should not be able to delete a message batch anonymously without permission");
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == 403, "Correctly fail deleting a message batch anonymously without permission");
    }

    grantAnonymousPermission(queueUrl, "DeleteMessage", queueArn);
    // give it some time to propegate
    Thread.sleep(authorizationExpiryMs);
    anonymousSQSClient.deleteMessageBatch(deleteMessageBatchRequest);
  }

  @Test
  public void testGetQueueAttributes() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testGetQueueAttributes");
    final String queueName = "queue_name_get_queue_attributes";
    final String queueUrl = accountSQSClient.createQueue(queueName).getQueueUrl();
    final String queueArn = accountSQSClient.getQueueAttributes(queueUrl,
      Collections.singletonList("QueueArn")).getAttributes().get("QueueArn");

    try {
      anonymousSQSClient.getQueueAttributes(queueUrl, Collections.singletonList("All"));
      assertThat(false, "Should not be able to get queue attributes anonymously without permission");
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == 403, "Correctly fail get queue attributes anonymously without permission");
    }

    grantAnonymousPermission(queueUrl, "GetQueueAttributes", queueArn);
    // give it some time to propegate
    Thread.sleep(authorizationExpiryMs);
    anonymousSQSClient.getQueueAttributes(queueUrl, Collections.singletonList("All"));
  }
  @Test
  public void testGetQueueUrl() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testGetQueueUrl");
    final String queueName = "queue_name_get_queue_url";
    final String queueUrl = accountSQSClient.createQueue(queueName).getQueueUrl();
    final String queueArn = accountSQSClient.getQueueAttributes(queueUrl,
      Collections.singletonList("QueueArn")).getAttributes().get("QueueArn");

    List<String> pathParts = Lists.newArrayList(Splitter.on('/').omitEmptyStrings().split(new URL(queueUrl).getPath()));
    final String accountId = pathParts.get(0);

    GetQueueUrlRequest getQueueUrlRequest = new GetQueueUrlRequest();
    getQueueUrlRequest.setQueueOwnerAWSAccountId(accountId);
    getQueueUrlRequest.setQueueName(queueName);

    // try to get the queue url anonymously
    try {
      anonymousSQSClient.getQueueUrl(getQueueUrlRequest);
      assertThat(false, "Should not be able to get queue url anonymously without permission");
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == 403, "Correctly fail to get queue url anonymously without permission");
    }

    grantAnonymousPermission(queueUrl, "GetQueueUrl", queueArn);
    // give it some time to propegate
    Thread.sleep(authorizationExpiryMs);
    // still can't get queue url as anonymous
    try {
      anonymousSQSClient.getQueueUrl(getQueueUrlRequest);
      assertThat(false, "Should not be able to get queue url anonymously even with permission");
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == 403, "Correctly fail to get queue url anonymously without permission");
    }
  }

  @Test
  public void testListDeadLetterSourceQueues() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testListDeadLetterSourceQueues");
    final String queueName = "queue_name_list_dead_letter_source_queues";
    final String queueUrl = accountSQSClient.createQueue(queueName).getQueueUrl();
    final String queueArn = accountSQSClient.getQueueAttributes(queueUrl,
      Collections.singletonList("QueueArn")).getAttributes().get("QueueArn");
    CreateQueueRequest createQueueRequest = new CreateQueueRequest();
    createQueueRequest.getAttributes().put("RedrivePolicy","{\"maxReceiveCount\":\"5\",\"deadLetterTargetArn\":\""+queueArn+"\"}");
    for (int i=1;i<=5;i++) {
      createQueueRequest.setQueueName("queue_name_dead_letter_source_" + i);
      accountSQSClient.createQueue(createQueueRequest);
    }

    ListDeadLetterSourceQueuesRequest listDeadLetterSourceQueuesRequest = new ListDeadLetterSourceQueuesRequest();
    listDeadLetterSourceQueuesRequest.setQueueUrl(queueUrl);

    try {
      anonymousSQSClient.listDeadLetterSourceQueues(listDeadLetterSourceQueuesRequest);
      assertThat(false, "Should not be able to list dead letter source queues anonymously without permission");
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == 403, "Correctly fail to list dead letter source queues anonymously without permission");
    }

    grantAnonymousPermission(queueUrl, "ListDeadLetterSourceQueues", queueArn);
    // give it some time to propegate
    Thread.sleep(authorizationExpiryMs);
    anonymousSQSClient.listDeadLetterSourceQueues(listDeadLetterSourceQueuesRequest);
  }

  @Test
  public void testPurgeQueue() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testPurgeQueue");
    final String queueName = "queue_name_purge_queue";
    final String queueUrl = accountSQSClient.createQueue(queueName).getQueueUrl();
    final String queueArn = accountSQSClient.getQueueAttributes(queueUrl,
      Collections.singletonList("QueueArn")).getAttributes().get("QueueArn");

    PurgeQueueRequest purgeQueueRequest = new PurgeQueueRequest();
    purgeQueueRequest.setQueueUrl(queueUrl);
    try {
      anonymousSQSClient.purgeQueue(purgeQueueRequest);
      assertThat(false, "Should not be able to purge queue anonymously without permission");
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == 403, "Correctly fail to purge queue anonymously without permission");
    }

    grantAnonymousPermission(queueUrl, "PurgeQueue", queueArn);
    // give it some time to propegate
    Thread.sleep(authorizationExpiryMs);
    anonymousSQSClient.purgeQueue(purgeQueueRequest);
  }

  @Test
  public void testReceiveMessage() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testReceiveMessage");
    final String queueName = "queue_name_receive_message";
    final String queueUrl = accountSQSClient.createQueue(queueName).getQueueUrl();
    final String queueArn = accountSQSClient.getQueueAttributes(queueUrl,
      Collections.singletonList("QueueArn")).getAttributes().get("QueueArn");

      try {
        anonymousSQSClient.receiveMessage(queueUrl);
        assertThat(false, "Should not be able to receive message anonymously without permission");
      } catch (AmazonServiceException e) {
        assertThat(e.getStatusCode() == 403, "Correctly fail to receive message anonymously without permission");
      }

      grantAnonymousPermission(queueUrl, "ReceiveMessage", queueArn);
      // give it some time to propegate
      Thread.sleep(authorizationExpiryMs);
      anonymousSQSClient.receiveMessage(queueUrl);
  }

  @Test
  public void testSendMessage() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testSendMessage");
    final String queueName = "queue_name_send_message";
    final String queueUrl = accountSQSClient.createQueue(queueName).getQueueUrl();
    final String queueArn = accountSQSClient.getQueueAttributes(queueUrl,
      Collections.singletonList("QueueArn")).getAttributes().get("QueueArn");

    try {
      anonymousSQSClient.sendMessage(queueUrl, "hi");
      assertThat(false, "Should not be able to send message anonymously without permission");
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == 403, "Correctly fail to send message anonymously without permission");
    }

    grantAnonymousPermission(queueUrl, "SendMessage", queueArn);
    // give it some time to propegate
    Thread.sleep(authorizationExpiryMs);
    anonymousSQSClient.sendMessage(queueUrl, "hi");
  }

  @Test
  public void testSendMessageBatch() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testSendMessageBatch");
    final String queueName = "queue_name_send_message_batch";
    final String queueUrl = accountSQSClient.createQueue(queueName).getQueueUrl();
    final String queueArn = accountSQSClient.getQueueAttributes(queueUrl,
      Collections.singletonList("QueueArn")).getAttributes().get("QueueArn");

    SendMessageBatchRequest sendMessageBatchRequest = new SendMessageBatchRequest();
    sendMessageBatchRequest.setQueueUrl(queueUrl);
    SendMessageBatchRequestEntry entry = new SendMessageBatchRequestEntry();
    entry.setId("id");
    entry.setMessageBody("hello");
    sendMessageBatchRequest.getEntries().add(entry);

    try {
      anonymousSQSClient.sendMessageBatch(sendMessageBatchRequest);
      assertThat(false, "Should not be able to send message anonymously without permission");
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == 403, "Correctly fail to send message anonymously without permission");
    }

    grantAnonymousPermission(queueUrl, "SendMessage", queueArn);
    // give it some time to propegate
    Thread.sleep(authorizationExpiryMs);
    anonymousSQSClient.sendMessageBatch(sendMessageBatchRequest);
  }

  @Test
  public void testSetQueueAttributes() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testSetQueueAttributes");
    final String queueName = "queue_name_set_queue_attributes";
    final String queueUrl = accountSQSClient.createQueue(queueName).getQueueUrl();
    final String queueArn = accountSQSClient.getQueueAttributes(queueUrl,
      Collections.singletonList("QueueArn")).getAttributes().get("QueueArn");

    try {
      anonymousSQSClient.setQueueAttributes(queueUrl, ImmutableMap.of("DelaySeconds","30"));
      assertThat(false, "Should not be able to set queue attributes anonymously without permission");
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == 403, "Correctly fail to set queue attributes anonymously without permission");
    }

    grantAnonymousPermission(queueUrl, "SetQueueAttributes", queueArn);
    // give it some time to propegate
    Thread.sleep(authorizationExpiryMs);

    try {
      anonymousSQSClient.setQueueAttributes(queueUrl, ImmutableMap.of("DelaySeconds","30"));
      assertThat(false, "Should not be able to set queue attributes anonymously even with permission");
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == 403, "Correctly fail to set queue attributes anonymously even with permission");
    }
  }

  @Test
  public void testSenderId() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testSendMessage");
    final String queueName = "queue_name_sender_id";
    final String queueUrl = accountSQSClient.createQueue(queueName).getQueueUrl();
    final String queueArn = accountSQSClient.getQueueAttributes(queueUrl,
      Collections.singletonList("QueueArn")).getAttributes().get("QueueArn");

    grantAnonymousPermission(queueUrl, "SendMessage", queueArn);
    String accountMessageId = accountSQSClient.sendMessage(queueUrl, "hi").getMessageId();
    String anonymousMessageId = anonymousSQSClient.sendMessage(queueUrl, "hi").getMessageId();
    String accountSenderId = null;
    String anonymousSenderId = null;
    long startTime = System.currentTimeMillis();
    ReceiveMessageRequest receiveMessageRequest = new ReceiveMessageRequest();
    receiveMessageRequest.setQueueUrl(queueUrl);
    receiveMessageRequest.setAttributeNames(Collections.singletonList("SenderId"));
    while ((accountSenderId == null || anonymousSenderId == null) && System.currentTimeMillis() - startTime < 120000L) {
      for (Message m : accountSQSClient.receiveMessage(receiveMessageRequest).getMessages()) {
        if (m.getMessageId().equals(accountMessageId)) {
          accountSenderId = m.getAttributes().get("SenderId");
        }
        if (m.getMessageId().equals(anonymousMessageId)) {
          anonymousSenderId = m.getAttributes().get("SenderId");
        }
      }
      Thread.sleep(10L);
    }
    if (accountSenderId == null || anonymousSenderId == null) {
      throw new Exception("timeout");
    }
    assertThat(InetAddresses.isInetAddress(anonymousSenderId), "Anonymous sender id should be ip address");
    assertThat(!InetAddresses.isInetAddress(accountSenderId), "Account sender id should be ip address");

  }
}