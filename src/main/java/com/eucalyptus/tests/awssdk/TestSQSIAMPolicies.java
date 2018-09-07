package com.eucalyptus.tests.awssdk;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.AWSCredentials;
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
public class TestSQSIAMPolicies {

  private static String account;
  private static String otherAccount;
  private static long authorizationExpiryMs;
  private static AmazonSQS accountSQSClient;
  private static AmazonSQS accountUserSQSClient;
  private static AmazonSQS otherAccountSQSClient;
  private static AmazonSQS otherAccountUserSQSClient;
  private static Runnable restoreAuthorizationCache;

  @BeforeClass
  public static void init() throws Exception {
    print("### PRE SUITE SETUP - " + TestSQSIAMPolicies.class.getSimpleName());

    try {
      getCloudInfoAndSqs();
      authorizationExpiryMs = 0;
      restoreAuthorizationCache = disableAuthorizationCache();
      account = "sqs-account-iam-a-" + System.currentTimeMillis();
      synchronizedCreateAccount(account);
      accountSQSClient = getSqsClientWithNewAccount(account, "admin");
      AWSCredentials accountCredentials = getUserCreds(account, "admin");
      synchronizedCreateUser(account, "user");
      accountUserSQSClient = getSqsClientWithNewAccount(account, "user");
      otherAccount = "sqs-account-iam-b-" + System.currentTimeMillis();
      synchronizedCreateAccount(otherAccount);
      otherAccountSQSClient = getSqsClientWithNewAccount(otherAccount, "admin");
      AWSCredentials otherAccountCredentials = getUserCreds(otherAccount, "admin");
      synchronizedCreateUser(otherAccount, "user");
      otherAccountUserSQSClient = getSqsClientWithNewAccount(otherAccount, "user");
    } catch (Exception e) {
      try {
        teardown();
      } catch (Exception ignore) {
      }
      throw e;
    }
  }

  @AfterClass
  public static void teardown() throws Exception {
    print("### POST SUITE CLEANUP - " + TestSQSIAMPolicies.class.getSimpleName());
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

  @Test
  public void testParseInterval() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testParseInterval");
    assertThat(parseInterval("0", -1) == 1L * 0, "Testing value for parse Interval");
    assertThat(parseInterval("1", -1) == 1L * 1, "Testing value for parse Interval");
    assertThat(parseInterval("500", -1) == 1L * 500, "Testing value for parse Interval");
    assertThat(parseInterval("0ms", -1) == 1L * 0, "Testing value for parse Interval");
    assertThat(parseInterval("1ms", -1) == 1L * 1, "Testing value for parse Interval");
    assertThat(parseInterval("500ms", -1) == 1L * 500, "Testing value for parse Interval");
    assertThat(parseInterval("0s", -1) == 1L * 0 * 1000, "Testing value for parse Interval");
    assertThat(parseInterval("1s", -1) == 1L * 1 * 1000, "Testing value for parse Interval");
    assertThat(parseInterval("500s", -1) == 1L * 500 * 1000, "Testing value for parse Interval");
    assertThat(parseInterval("0m", -1) == 1L * 0 * 1000 * 60, "Testing value for parse Interval");
    assertThat(parseInterval("1m", -1) == 1L * 1 * 1000 * 60, "Testing value for parse Interval");
    assertThat(parseInterval("500m", -1) == 1L * 500 * 1000 * 60, "Testing value for parse Interval");
    assertThat(parseInterval("0h", -1) == 1L * 0 * 1000 * 60 * 60, "Testing value for parse Interval");
    assertThat(parseInterval("1h", -1) == 1L * 1 * 1000 * 60 * 60, "Testing value for parse Interval");
    assertThat(parseInterval("500h", -1) == 1L * 500 * 1000 * 60 * 60, "Testing value for parse Interval");
    assertThat(parseInterval("0d", -1) == 1L * 0 * 1000 * 60 * 60 * 24, "Testing value for parse Interval");
    assertThat(parseInterval("1d", -1) == 1L * 1 * 1000 * 60 * 60 * 24, "Testing value for parse Interval");
    assertThat(parseInterval("500d", -1) == 1L * 500 * 1000 * 60 * 60 * 24, "Testing value for parse Interval");
    assertThat(parseInterval("500t", -1) == 1L * -1, "Testing value for parse Interval");
  }

  void grantPermission(String account, String user, String permission, String resource) {
    createIAMPolicy(account, user, "userPolicy", "{\n" +
      "  \"Version\":\"2011-04-01\",\n" +
      "  \"Statement\": [{\n" +
      "    \"Sid\":\"4\",\n" +
      "    \"Effect\":\"Allow\",\n" +
      "    \"Action\":\"" + permission + "\",\n" +
      "    \"Resource\":\"" + resource + "\"\n" +
      "  }]\n" +
      "}");
  }

  void clearPermissions(String account, String user) {
    deleteIAMPolicy(account, user, "userPolicy");
  }

  @Test
  public void testCreateQueue() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testCreateQueue");
    // just to be safe
    clearPermissions(account, "user");
    clearPermissions(otherAccount, "user");
    Thread.sleep(authorizationExpiryMs);
    String queueName = "queue_name_create";
    String queueUrl = accountSQSClient.createQueue(queueName).getQueueUrl();
    accountSQSClient.deleteQueue(queueUrl);

    // try to create the queue as a user
    try {
      accountUserSQSClient.createQueue(queueName).getQueueUrl();
      assertThat(false, "Should not be able to create queue without permission");
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == 403, "Correctly fail creating a queue without permission");
    }
    grantPermission(account, "user", "sqs:CreateQueue", "*");
    // give it some time to propegate
    Thread.sleep(authorizationExpiryMs);
    // now we should be able to create the queue
    accountUserSQSClient.createQueue(queueName).getQueueUrl();

    accountSQSClient.deleteQueue(queueUrl);
    // remove the policy, see if we can't create again
    deleteIAMPolicy(account, "user", "userPolicy");
    // give it some time to propegate
    Thread.sleep(authorizationExpiryMs);
    try {
      accountUserSQSClient.createQueue(queueName).getQueueUrl();
      assertThat(false, "Should not be able to create queue without permission");
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == 403, "Correctly fail creating a queue without permission");
    }
  }

  @Test
  public void testDeleteQueue() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testDeleteQueue");
    // just to be safe
    clearPermissions(account, "user");
    clearPermissions(otherAccount, "user");
    Thread.sleep(authorizationExpiryMs);
    String queueName = "queue_name_delete";
    String queueUrl = accountSQSClient.createQueue(queueName).getQueueUrl();

    // try to delete the queue as a user
    try {
      accountUserSQSClient.deleteQueue(queueUrl);
      assertThat(false, "Should not be able to delete queue without permission");
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == 403, "Correctly fail deleting a queue without permission");
    }
    String queueArn = accountSQSClient.getQueueAttributes(queueUrl,
      Collections.singletonList("QueueArn")).getAttributes().get("QueueArn");

    grantPermission(account, "user", "sqs:DeleteQueue", queueArn);
    // give it some time to propegate
    Thread.sleep(authorizationExpiryMs);
    // now we should be able to delete the queue
    accountUserSQSClient.deleteQueue(queueUrl);

    // create again, and try with a wildcard resource
    accountSQSClient.createQueue(queueName).getQueueUrl();
    grantPermission(account, "user", "sqs:DeleteQueue", "*");

    // give it some time to propegate
    Thread.sleep(authorizationExpiryMs);
    // now we should be able to delete the queue
    accountUserSQSClient.deleteQueue(queueUrl);

    // Finally try to delete from a different account with a policy
    grantPermission(otherAccount, "user", "sqs:DeleteQueue", queueArn);
    accountSQSClient.createQueue(queueName).getQueueUrl();
    // give it some time to propegate
    Thread.sleep(authorizationExpiryMs);
    try {
      otherAccountUserSQSClient.deleteQueue(queueUrl);
      assertThat(false, "Should not be able to delete queue from another account");
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == 403, "Correctly fail delete queue from another account");
    }

    // revoke the policies and make sure we still can't delete
    clearPermissions(account, "user");
    clearPermissions(otherAccount, "user");
    try {
      accountUserSQSClient.deleteQueue(queueUrl);
      assertThat(false, "Should not be able to delete queue without permission");
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == 403, "Correctly fail delete queue without permission");
    }
    try {
      otherAccountUserSQSClient.deleteQueue(queueUrl);
      assertThat(false, "Should not be able to delete queue from another account");
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == 403, "Correctly fail delete queue from another account");
    }
    // now see if the admin can do it (should be able to I think)
    sqs.deleteQueue(queueUrl);
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
  public void testAddPermission() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testAddPermission");
    final String queueName = "queue_name_add_permission";
    final String queueUrl = accountSQSClient.createQueue(queueName).getQueueUrl();
    final String queueArn = accountSQSClient.getQueueAttributes(queueUrl,
      Collections.singletonList("QueueArn")).getAttributes().get("QueueArn");

    List<String> pathParts = Lists.newArrayList(Splitter.on('/').omitEmptyStrings().split(new URL(queueUrl).getPath()));
    final String accountId = pathParts.get(0);
    accountSQSClient.deleteQueue(queueUrl);

    testQueueAction(queueName, queueUrl, queueArn, "sqs:AddPermission",
      NOOP,
        sqsClient -> sqsClient.addPermission(queueUrl, "label", Collections.singletonList(accountId), Collections.singletonList("DeleteMessage")),
      403);
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

    testQueueAction(queueName, queueUrl, queueArn, "sqs:ChangeMessageVisibility",
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

    testQueueAction(queueName, queueUrl, queueArn, "sqs:ChangeMessageVisibility",
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

    testQueueAction(queueName, queueUrl, queueArn, "sqs:DeleteMessage",
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

    testQueueAction(queueName, queueUrl, queueArn, "sqs:DeleteMessage",
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

    testQueueAction(queueName, queueUrl, queueArn, "sqs:GetQueueAttributes",
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

    testQueueAction(queueName, queueUrl, queueArn, "sqs:GetQueueUrl",
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

    testQueueAction(queueName, queueUrl, queueArn, "sqs:ListDeadLetterSourceQueues",
      NOOP,
        sqsClient -> {
          ListDeadLetterSourceQueuesRequest listDeadLetterSourceQueuesRequest = new ListDeadLetterSourceQueuesRequest();
          listDeadLetterSourceQueuesRequest.setQueueUrl(queueUrl);
          sqsClient.listDeadLetterSourceQueues(listDeadLetterSourceQueuesRequest);
        },
      403);
  }

  @Test
  public void testListQueues() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testListQueues");
    final String queueName = "queue_name_list_queues";
    final String queueUrl = accountSQSClient.createQueue(queueName).getQueueUrl();
    final String queueArn = accountSQSClient.getQueueAttributes(queueUrl,
      Collections.singletonList("QueueArn")).getAttributes().get("QueueArn");

    // just to be safe
    clearPermissions(account, "user");
    clearPermissions(otherAccount, "user");
    Thread.sleep(authorizationExpiryMs);

    accountSQSClient.listQueues();

    // try the operation with no permissions
    try {
      accountUserSQSClient.listQueues();
      assertThat(false, "Should not be able to perform the operation without permission");
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == 403, "Correctly fail to perform the operation without permission");
    }

    grantPermission(account, "user", "sqs:ListQueues", queueArn);
    // give it some time to propegate
    Thread.sleep(authorizationExpiryMs);

    // now we should still not be able to perform the operation (requires * as resource)
    try {
      accountUserSQSClient.listQueues();
      assertThat(false, "Should not be able to perform the operation without permission");
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == 403, "Correctly fail to perform the operation without permission");
    }

    // try wildcard
    grantPermission(account, "user", "sqs:ListQueues", "*");
    // give it some time to propegate
    Thread.sleep(authorizationExpiryMs);

    // now we should be able to perform the operation
    accountUserSQSClient.listQueues();

    clearPermissions(account, "user");
    Thread.sleep(authorizationExpiryMs);
  }

  @Test
  public void testPurgeQueue() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testPurgeQueue");
    final String queueName = "queue_name_purge_queue";
    final String queueUrl = accountSQSClient.createQueue(queueName).getQueueUrl();
    final String queueArn = accountSQSClient.getQueueAttributes(queueUrl,
      Collections.singletonList("QueueArn")).getAttributes().get("QueueArn");
    accountSQSClient.deleteQueue(queueUrl);

    testQueueAction(queueName, queueUrl, queueArn, "sqs:PurgeQueue",
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

    testQueueAction(queueName, queueUrl, queueArn, "sqs:ReceiveMessage",
      NOOP,
        sqsClient -> sqsClient.receiveMessage(queueUrl),
      403);
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
    accountSQSClient.deleteQueue(queueUrl);

    testQueueAction(queueName, queueUrl, queueArn, "sqs:RemovePermission",
        sqsClient -> sqsClient.addPermission(queueUrl, "label", Collections.singletonList(accountId), Collections.singletonList("DeleteMessage")),
        sqsClient -> sqsClient.removePermission(queueUrl, "label"),
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

    testQueueAction(queueName, queueUrl, queueArn, "sqs:SendMessage",
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

    testQueueAction(queueName, queueUrl, queueArn, "sqs:SendMessage",
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

  @Test
  public void testSetQueueAttributes() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testSetQueueAttributes");
    final String queueName = "queue_name_get_queue_attributes";
    final String queueUrl = accountSQSClient.createQueue(queueName).getQueueUrl();
    final String queueArn = accountSQSClient.getQueueAttributes(queueUrl,
      Collections.singletonList("QueueArn")).getAttributes().get("QueueArn");
    accountSQSClient.deleteQueue(queueUrl);

    testQueueAction(queueName, queueUrl, queueArn, "sqs:SetQueueAttributes",
      NOOP,
        sqsClient -> sqsClient.setQueueAttributes(queueUrl, ImmutableMap.of("DelaySeconds","30")),
      403);
  }

  interface AWSOperation {
    void perform(AmazonSQS sqsClient) throws AmazonServiceException;
  }

  private void testQueueAction(String queueName, String queueUrl, String queueArn, String action, AWSOperation setupOperation, AWSOperation actionOperation, int errorCode) throws Exception {

    // just to be safe
    clearPermissions(account, "user");
    clearPermissions(otherAccount, "user");
    Thread.sleep(authorizationExpiryMs);

    queueUrl = accountSQSClient.createQueue(queueName).getQueueUrl();
    setupOperation.perform(accountSQSClient);

    // try the operation with no permissions
    try {
      actionOperation.perform(accountUserSQSClient);
      assertThat(false, "Should not be able to perform the operation without permission");
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == errorCode, "Correctly fail to perform the operation without permission");
    }

    grantPermission(account, "user", action, queueArn);
    // give it some time to propegate
    Thread.sleep(authorizationExpiryMs);

    // now we should be able to perform the operation
    actionOperation.perform(accountUserSQSClient);

    // delete and recreate the queue
    accountSQSClient.deleteQueue(queueUrl);
    queueUrl = accountSQSClient.createQueue(queueName).getQueueUrl();
    setupOperation.perform(accountSQSClient);

    // try wildcard
    grantPermission(account, "user", action, "*");
    // give it some time to propegate
    Thread.sleep(authorizationExpiryMs);

    // now we should be able to perform the operation
    actionOperation.perform(accountUserSQSClient);

    // delete and recreate the queue
    accountSQSClient.deleteQueue(queueUrl);
    queueUrl = accountSQSClient.createQueue(queueName).getQueueUrl();
    setupOperation.perform(accountSQSClient);

    // Finally try to delete from a different account with a policy
    grantPermission(otherAccount, "user", action, queueArn);
    // give it some time to propegate
    Thread.sleep(authorizationExpiryMs);


    try {
      actionOperation.perform(otherAccountUserSQSClient);
      assertThat(false, "Should not be able to perform action from another account");
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == errorCode, "Correctly fail to perform operation from another account");
    }

    // revoke the policies and make sure we still can't perform the operation
    clearPermissions(account, "user");
    clearPermissions(otherAccount, "user");
    // give it some time to propegate
    Thread.sleep(authorizationExpiryMs);

    try {
      actionOperation.perform(accountUserSQSClient);
      assertThat(false, "Should not be able to perform the operation without permission");
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == errorCode, "Correctly fail to perform the operation without permission");
    }

    try {
      actionOperation.perform(otherAccountUserSQSClient);
      assertThat(false, "Should not be able to perform action from another account");
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == errorCode, "Correctly fail to perform operation from another account");
    }

    // see if the admin can do it
    actionOperation.perform(sqs);
    accountSQSClient.deleteQueue(queueUrl);
  }

}