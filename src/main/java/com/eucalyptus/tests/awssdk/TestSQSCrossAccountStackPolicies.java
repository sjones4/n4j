package com.eucalyptus.tests.awssdk;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.sqs.AmazonSQS;
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
import com.amazonaws.services.sqs.model.ReceiveMessageResult;
import com.amazonaws.services.sqs.model.SendMessageBatchRequest;
import com.amazonaws.services.sqs.model.SendMessageBatchRequestEntry;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.net.URL;
import java.util.Collections;
import java.util.List;

import static com.eucalyptus.tests.awssdk.N4j.*;

/**
 * Created by ethomas on 10/4/16.
 */
public class TestSQSCrossAccountStackPolicies {

  private static String account;
  private static String otherAccount;
  private static long authorizationExpiryMs;
  private static AmazonSQS accountSQSClient;
  private static AmazonSQS otherAccountSQSClient;
  private static AmazonSQS otherAccountUserSQSClient;
  private static String otherAccountId;
  private static Runnable restoreAuthorizationCache;

  @BeforeClass
  public static void init() throws Exception {
    print("### PRE SUITE SETUP - " + TestSQSCrossAccountStackPolicies.class.getSimpleName());

    try {
      getCloudInfoAndSqs();
      authorizationExpiryMs = 0;
      restoreAuthorizationCache = disableAuthorizationCache();
      account = "sqs-account-casp-a-" + System.currentTimeMillis();
      synchronizedCreateAccount(account);
      accountSQSClient = getSqsClientWithNewAccount(account, "admin");
      otherAccount = "sqs-account-casp-b-" + System.currentTimeMillis();
      synchronizedCreateAccount(otherAccount);
      otherAccountSQSClient = getSqsClientWithNewAccount(otherAccount, "admin");
      synchronizedCreateUser(otherAccount, "user");
      otherAccountUserSQSClient = getSqsClientWithNewAccount(otherAccount, "user");
      String otherQueueUrl = otherAccountSQSClient.createQueue("placeholder").getQueueUrl();
      List<String> otherPathParts = Lists.newArrayList(Splitter.on('/').omitEmptyStrings().split(new URL(otherQueueUrl).getPath()));
      otherAccountId = otherPathParts.get(0);
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
    print("### POST SUITE CLEANUP - " + TestSQSCrossAccountStackPolicies.class.getSimpleName());
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
    if ( restoreAuthorizationCache != null ) {
      restoreAuthorizationCache.run( );
    }
  }

  private static AWSOperation NOOP = sqsClient -> {
    ;
  };

  private static class SendMessageAndGetReceiptHandle implements AWSOperation {

    private String receiptHandle = null;
    private String queueUrl;

    public SendMessageAndGetReceiptHandle(String queueUrl) {
      this.queueUrl = queueUrl;
    }

    @Override
    public void perform(AmazonSQS sqsClient) throws AmazonServiceException {
      String messageId = sqsClient.sendMessage(queueUrl, "hello").getMessageId();
      receiptHandle = null;
      long startTime = System.currentTimeMillis();
      while (receiptHandle == null && System.currentTimeMillis() - startTime < 120000L) {
        ReceiveMessageResult receiveMessageResult = sqsClient.receiveMessage(queueUrl);
        if (receiveMessageResult != null && receiveMessageResult.getMessages() != null) {
          for (Message message: receiveMessageResult.getMessages()) {
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

    }

    public String getReceiptHandle() {
      return receiptHandle;
    }
  }

  @Test
  public void testChangeMessageVisibility() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testChangeMessageVisibility");
    final String queueName = "queue_name_change_message_visibility";
    final String queueUrl = accountSQSClient.createQueue(queueName).getQueueUrl();
    final String queueArn = accountSQSClient.getQueueAttributes(queueUrl,
      Collections.singletonList("QueueArn")).getAttributes().get("QueueArn");
    accountSQSClient.deleteQueue(queueUrl);


    final SendMessageAndGetReceiptHandle sendMessageAndGetReceiptHandle = new SendMessageAndGetReceiptHandle(queueUrl);

    testQueueAction(queueName, queueUrl, queueArn, "ChangeMessageVisibility",
      sendMessageAndGetReceiptHandle,
        sqsClient -> sqsClient.changeMessageVisibility(queueUrl, sendMessageAndGetReceiptHandle.getReceiptHandle(), 30),
      403);
  }

  @Test
  public void testChangeMessageVisibilityBatch() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testChangeMessageVisibilityBatch");
    final String queueName = "queue_name_change_message_visibility_batch";
    final String queueUrl = accountSQSClient.createQueue(queueName).getQueueUrl();
    final String queueArn = accountSQSClient.getQueueAttributes(queueUrl,
      Collections.singletonList("QueueArn")).getAttributes().get("QueueArn");
    accountSQSClient.deleteQueue(queueUrl);

    final SendMessageAndGetReceiptHandle sendMessageAndGetReceiptHandle = new SendMessageAndGetReceiptHandle(queueUrl);

    testQueueAction(queueName, queueUrl, queueArn, "ChangeMessageVisibility",
      sendMessageAndGetReceiptHandle,
        sqsClient -> {
          ChangeMessageVisibilityBatchRequest changeMessageVisibilityBatchRequest = new ChangeMessageVisibilityBatchRequest();
          changeMessageVisibilityBatchRequest.setQueueUrl(queueUrl);
          ChangeMessageVisibilityBatchRequestEntry entry = new ChangeMessageVisibilityBatchRequestEntry();
          entry.setId("id");
          entry.setVisibilityTimeout(30);
          entry.setReceiptHandle(sendMessageAndGetReceiptHandle.getReceiptHandle());
          changeMessageVisibilityBatchRequest.getEntries().add(entry);
          sqsClient.changeMessageVisibilityBatch(changeMessageVisibilityBatchRequest);
        },
      403);
  }

  @Test
  public void testDeleteMessage() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testDeleteMessage");
    final String queueName = "queue_name_delete_message";
    final String queueUrl = accountSQSClient.createQueue(queueName).getQueueUrl();
    final String queueArn = accountSQSClient.getQueueAttributes(queueUrl,
      Collections.singletonList("QueueArn")).getAttributes().get("QueueArn");
    accountSQSClient.deleteQueue(queueUrl);

    final SendMessageAndGetReceiptHandle sendMessageAndGetReceiptHandle = new SendMessageAndGetReceiptHandle(queueUrl);

    testQueueAction(queueName, queueUrl, queueArn, "DeleteMessage",
      sendMessageAndGetReceiptHandle,
        sqsClient -> sqsClient.deleteMessage(queueUrl, sendMessageAndGetReceiptHandle.getReceiptHandle()),
      403);
  }

  @Test
  public void testDeleteMessageBatch() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testDeleteMessageBatch");
    final String queueName = "queue_name_delete_message_batch";
    final String queueUrl = accountSQSClient.createQueue(queueName).getQueueUrl();
    final String queueArn = accountSQSClient.getQueueAttributes(queueUrl,
      Collections.singletonList("QueueArn")).getAttributes().get("QueueArn");
    accountSQSClient.deleteQueue(queueUrl);

    final SendMessageAndGetReceiptHandle sendMessageAndGetReceiptHandle = new SendMessageAndGetReceiptHandle(queueUrl);

    testQueueAction(queueName, queueUrl, queueArn, "DeleteMessage",
      sendMessageAndGetReceiptHandle,
        sqsClient -> {
          DeleteMessageBatchRequest deleteMessageBatchRequest = new DeleteMessageBatchRequest();
          deleteMessageBatchRequest.setQueueUrl(queueUrl);
          DeleteMessageBatchRequestEntry entry = new DeleteMessageBatchRequestEntry();
          entry.setId("id");
          entry.setReceiptHandle(sendMessageAndGetReceiptHandle.getReceiptHandle());
          deleteMessageBatchRequest.getEntries().add(entry);
          sqsClient.deleteMessageBatch(deleteMessageBatchRequest);
        },
      403);
  }

  @Test
  public void testGetQueueAttributes() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testGetQueueAttributes");
    final String queueName = "queue_name_get_queue_attributes";
    final String queueUrl = accountSQSClient.createQueue(queueName).getQueueUrl();
    final String queueArn = accountSQSClient.getQueueAttributes(queueUrl,
      Collections.singletonList("QueueArn")).getAttributes().get("QueueArn");
    accountSQSClient.deleteQueue(queueUrl);

    testQueueAction(queueName, queueUrl, queueArn, "GetQueueAttributes",
      NOOP,
        sqsClient -> sqsClient.getQueueAttributes(queueUrl, Collections.singletonList("All")),
      403);
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

    accountSQSClient.deleteQueue(queueUrl);

    testQueueAction(queueName, queueUrl, queueArn, "GetQueueUrl",
      NOOP,
        sqsClient -> {
          GetQueueUrlRequest getQueueUrlRequest = new GetQueueUrlRequest();
          getQueueUrlRequest.setQueueOwnerAWSAccountId(accountId);
          getQueueUrlRequest.setQueueName(queueName);
          sqsClient.getQueueUrl(getQueueUrlRequest);
        },
      400);
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
    accountSQSClient.createQueue(createQueueRequest);

    accountSQSClient.deleteQueue(queueUrl);

    testQueueAction(queueName, queueUrl, queueArn, "ListDeadLetterSourceQueues",
      NOOP,
        sqsClient -> {
          ListDeadLetterSourceQueuesRequest listDeadLetterSourceQueuesRequest = new ListDeadLetterSourceQueuesRequest();
          listDeadLetterSourceQueuesRequest.setQueueUrl(queueUrl);
          sqsClient.listDeadLetterSourceQueues(listDeadLetterSourceQueuesRequest);
        },
      403);
  }

  @Test
  public void testPurgeQueue() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testPurgeQueue");
    final String queueName = "queue_name_purge_queue";
    final String queueUrl = accountSQSClient.createQueue(queueName).getQueueUrl();
    final String queueArn = accountSQSClient.getQueueAttributes(queueUrl,
      Collections.singletonList("QueueArn")).getAttributes().get("QueueArn");
    accountSQSClient.deleteQueue(queueUrl);

    testQueueAction(queueName, queueUrl, queueArn, "PurgeQueue",
      NOOP,
        sqsClient -> {
          PurgeQueueRequest purgeQueueRequest = new PurgeQueueRequest();
          purgeQueueRequest.setQueueUrl(queueUrl);
          sqsClient.purgeQueue(purgeQueueRequest);
        },
      403);
  }

  @Test
  public void testReceiveMessage() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testReceiveMessage");
    final String queueName = "queue_name_receive_message";
    final String queueUrl = accountSQSClient.createQueue(queueName).getQueueUrl();
    final String queueArn = accountSQSClient.getQueueAttributes(queueUrl,
      Collections.singletonList("QueueArn")).getAttributes().get("QueueArn");
    accountSQSClient.deleteQueue(queueUrl);

    testQueueAction(queueName, queueUrl, queueArn, "ReceiveMessage",
      NOOP,
        sqsClient -> sqsClient.receiveMessage(queueUrl),
      403);
  }

  @Test
  public void testSendMessage() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testSendMessage");
    final String queueName = "queue_name_send_message";
    final String queueUrl = accountSQSClient.createQueue(queueName).getQueueUrl();
    final String queueArn = accountSQSClient.getQueueAttributes(queueUrl,
      Collections.singletonList("QueueArn")).getAttributes().get("QueueArn");
    accountSQSClient.deleteQueue(queueUrl);

    testQueueAction(queueName, queueUrl, queueArn, "SendMessage",
      NOOP,
        sqsClient -> sqsClient.sendMessage(queueUrl, "hi"),
      403);
  }

  @Test
  public void testSendMessageBatch() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testSendMessageBatch");
    final String queueName = "queue_name_send_message_batch";
    final String queueUrl = accountSQSClient.createQueue(queueName).getQueueUrl();
    final String queueArn = accountSQSClient.getQueueAttributes(queueUrl,
      Collections.singletonList("QueueArn")).getAttributes().get("QueueArn");
    accountSQSClient.deleteQueue(queueUrl);

    testQueueAction(queueName, queueUrl, queueArn, "SendMessage",
      NOOP,
        sqsClient -> {
          SendMessageBatchRequest sendMessageBatchRequest = new SendMessageBatchRequest();
          sendMessageBatchRequest.setQueueUrl(queueUrl);
          SendMessageBatchRequestEntry entry = new SendMessageBatchRequestEntry();
          entry.setId("id");
          entry.setMessageBody("hello");
          sendMessageBatchRequest.getEntries().add(entry);
          sqsClient.sendMessageBatch(sendMessageBatchRequest);
        },
      403);
  }

  interface AWSOperation {
    void perform(AmazonSQS sqsClient) throws AmazonServiceException;
  }

  private void testQueueAction(String queueName, String queueUrl, String queueArn, String action, AWSOperation setupOperation, AWSOperation actionOperation, int errorCode) throws Exception {

    queueUrl = accountSQSClient.createQueue(queueName).getQueueUrl();
    setupOperation.perform(accountSQSClient);

    // try the operation with no permissions
    try {
      actionOperation.perform(otherAccountSQSClient);
      assertThat(false, "Should not be able to perform the operation without permission");
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == errorCode, "Correctly fail to perform the operation without permission");
    }

    accountSQSClient.addPermission(queueUrl, "label", Collections.singletonList(otherAccountId), Collections.singletonList(action));
    // now we should be able to perform the operation
    actionOperation.perform(otherAccountSQSClient);

    // delete and recreate the queue
    accountSQSClient.deleteQueue(queueUrl);
    queueUrl = accountSQSClient.createQueue(queueName).getQueueUrl();
    setupOperation.perform(accountSQSClient);

    // try just 'account id' instead of arn
    accountSQSClient.setQueueAttributes(queueUrl, ImmutableMap.of("Policy",
      "{\n" +
        "  \"Version\": \"2012-10-17\",\n" +
        "  \"Id\": \""+queueArn+"/SQSDefaultPolicy\",\n" +
        "  \"Statement\": [\n" +
        "    {\n" +
        "      \"Sid\": \"label\",\n" +
        "      \"Effect\": \"Allow\",\n" +
        "      \"Principal\": {\"AWS\":\""+otherAccountId+"\"},\n" +
        "      \"Action\": \"SQS:"+action+"\",\n" +
        "      \"Resource\": \""+queueArn+"\"\n" +
        "    }\n" +
        "  ]\n" +
        "}"));
    // now we should be able to perform the operation
    actionOperation.perform(otherAccountSQSClient);

    // delete and recreate the queue
    accountSQSClient.deleteQueue(queueUrl);
    queueUrl = accountSQSClient.createQueue(queueName).getQueueUrl();
    setupOperation.perform(accountSQSClient);

    // try wildcard on account
    accountSQSClient.setQueueAttributes(queueUrl, ImmutableMap.of("Policy",
      "{\n" +
        "  \"Version\": \"2012-10-17\",\n" +
        "  \"Id\": \""+queueArn+"/SQSDefaultPolicy\",\n" +
        "  \"Statement\": [\n" +
        "    {\n" +
        "      \"Sid\": \"label\",\n" +
        "      \"Effect\": \"Allow\",\n" +
        "      \"Principal\": {\"AWS\":\"*\"},\n" +
        "      \"Action\": \"SQS:"+action+"\",\n" +
        "      \"Resource\": \""+queueArn+"\"\n" +
        "    }\n" +
        "  ]\n" +
        "}"));
    // now we should be able to perform the operation
    actionOperation.perform(otherAccountSQSClient);

    // delete and recreate the queue
    accountSQSClient.deleteQueue(queueUrl);
    queueUrl = accountSQSClient.createQueue(queueName).getQueueUrl();
    setupOperation.perform(accountSQSClient);

    // try wildcard on operation
    accountSQSClient.addPermission(queueUrl, "label", Collections.singletonList(otherAccountId), Collections.singletonList("*"));
    // now we should be able to perform the operation
    actionOperation.perform(otherAccountSQSClient);

    // delete and recreate the queue
    accountSQSClient.deleteQueue(queueUrl);
    queueUrl = accountSQSClient.createQueue(queueName).getQueueUrl();
    setupOperation.perform(accountSQSClient);

    // try wildcard on both
    accountSQSClient.setQueueAttributes(queueUrl, ImmutableMap.of("Policy",
      "{\n" +
        "  \"Version\": \"2012-10-17\",\n" +
        "  \"Id\": \""+queueArn+"/SQSDefaultPolicy\",\n" +
        "  \"Statement\": [\n" +
        "    {\n" +
        "      \"Sid\": \"label\",\n" +
        "      \"Effect\": \"Allow\",\n" +
        "      \"Principal\": {\"AWS\":\"*\"},\n" +
        "      \"Action\": \"SQS:"+action+"\",\n" +
        "      \"Resource\": \"*\"\n" +
        "    }\n" +
        "  ]\n" +
        "}"));
    // now we should be able to perform the operation
    actionOperation.perform(otherAccountSQSClient);


    // delete and recreate the queue
    accountSQSClient.deleteQueue(queueUrl);
    queueUrl = accountSQSClient.createQueue(queueName).getQueueUrl();
    setupOperation.perform(accountSQSClient);

    // try wildcard on both
    accountSQSClient.setQueueAttributes(queueUrl, ImmutableMap.of("Policy",
      "{\n" +
        "  \"Version\": \"2012-10-17\",\n" +
        "  \"Id\": \""+queueArn+"/SQSDefaultPolicy\",\n" +
        "  \"Statement\": [\n" +
        "    {\n" +
        "      \"Sid\": \"label\",\n" +
        "      \"Effect\": \"Allow\",\n" +
        "      \"Principal\": {\"AWS\":\"*\"},\n" +
        "      \"Action\": \"SQS:"+action+"\",\n" +
        "      \"Resource\": \"*\"\n" +
        "    }\n" +
        "  ]\n" +
        "}"));
    // now we should be able to perform the operation
    actionOperation.perform(otherAccountSQSClient);

    // delete and recreate the queue
    accountSQSClient.deleteQueue(queueUrl);
    queueUrl = accountSQSClient.createQueue(queueName).getQueueUrl();
    setupOperation.perform(accountSQSClient);

    // now for a regular user:
    // try the operation with no permissions
    try {
      actionOperation.perform(otherAccountUserSQSClient);
      assertThat(false, "Should not be able to perform the operation without permission");
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == errorCode, "Correctly fail to perform the operation without permission");
    }

    // now add a queue policy permission
    accountSQSClient.setQueueAttributes(queueUrl, ImmutableMap.of("Policy",
      "{\n" +
        "  \"Version\": \"2012-10-17\",\n" +
        "  \"Id\": \""+queueArn+"/SQSDefaultPolicy\",\n" +
        "  \"Statement\": [\n" +
        "    {\n" +
        "      \"Sid\": \"label\",\n" +
        "      \"Effect\": \"Allow\",\n" +
        "      \"Principal\": {\"AWS\":\"arn:aws:iam::"+otherAccountId + ":user/user\"},\n" +
        "      \"Action\": \"SQS:"+action+"\",\n" +
        "      \"Resource\": \""+queueArn+"\"\n" +
        "    }\n" +
        "  ]\n" +
        "}"));
    // still shouldn't be able to do it because of non-user policy
    try {
      actionOperation.perform(otherAccountUserSQSClient);
      assertThat(false, "Should not be able to perform the operation without permission");
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == errorCode, "Correctly fail to perform the operation without permission");
    }
    // now add a user policy
    createIAMPolicy(otherAccount, "user", "userPolicy", "{\n" +
      "  \"Version\":\"2011-04-01\",\n" +
      "  \"Statement\": [{\n" +
      "    \"Sid\":\"4\",\n" +
      "    \"Effect\":\"Allow\",\n" +
      "    \"Action\":\"sqs:" + action + "\",\n" +
      "    \"Resource\":\"" + queueArn + "\"\n" +
      "  }]\n" +
      "}");
    Thread.sleep(authorizationExpiryMs);
    actionOperation.perform(otherAccountUserSQSClient);

  }

}