package com.eucalyptus.tests.awssdk;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClient;
import com.amazonaws.services.sqs.model.ChangeMessageVisibilityBatchRequestEntry;
import com.amazonaws.services.sqs.model.DeleteMessageBatchRequestEntry;
import com.amazonaws.services.sqs.model.GetQueueUrlRequest;
import com.amazonaws.services.sqs.model.ListDeadLetterSourceQueuesRequest;
import com.amazonaws.services.sqs.model.ListQueuesResult;
import com.amazonaws.services.sqs.model.PurgeQueueRequest;
import com.amazonaws.services.sqs.model.SendMessageBatchRequestEntry;
import com.beust.jcommander.internal.Maps;
import com.beust.jcommander.internal.Sets;
import com.google.common.base.Splitter;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Table;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.eucalyptus.tests.awssdk.N4j.*;

/**
 * Created by ethomas on 10/4/16.
 */
public class TestSQSStatusCodesForNonexistentQueues {


  private String account;
  private String otherAccount;
  private AmazonSQS accountSQSClient;
  private AmazonSQS accountUserSQSClient;
  private AmazonSQS otherAccountSQSClient;
  private AmazonSQS otherAccountUserSQSClient;


  @BeforeClass
  public void init() throws Exception {
    print("### PRE SUITE SETUP - " + this.getClass().getSimpleName());

    try {
      getCloudInfoAndSqs();
      account = "sqs-account-a-" + System.currentTimeMillis();
      createAccount(account);
      accountSQSClient = getSqsClientWithNewAccount(account, "admin");
      AWSCredentials accountCredentials = getUserCreds(account, "admin");
      createUser(account, "user");
      accountUserSQSClient = getSqsClientWithNewAccount(account, "user");
      otherAccount = "sqs-account-b-" + System.currentTimeMillis();
      createAccount(otherAccount);
      otherAccountSQSClient = getSqsClientWithNewAccount(otherAccount, "admin");
      AWSCredentials otherAccountCredentials = getUserCreds(otherAccount, "admin");
      createUser(otherAccount, "user");
      otherAccountUserSQSClient = getSqsClientWithNewAccount(otherAccount, "user");
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
      deleteAccount(account);
    }
    if (otherAccount != null) {
      if (otherAccountSQSClient != null) {
        ListQueuesResult listQueuesResult = otherAccountSQSClient.listQueues();
        if (listQueuesResult != null) {
          listQueuesResult.getQueueUrls().forEach(otherAccountSQSClient::deleteQueue);
        }
      }
      deleteAccount(otherAccount);
    }
  }

  @Test
  public void testStatusCodesNonexistentQueue() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testStatusCodesNonexistentQueue");
    String queueName = "queue_name_status_codes_nonexistent_queue";
    final String queueUrl = accountSQSClient.createQueue(queueName).getQueueUrl();
    final String bogusQueueName = queueName + "-bogus";
    final String bogusQueueUrl = queueUrl + "-bogus";
    List<String> pathParts = Lists.newArrayList(Splitter.on('/').omitEmptyStrings().split(new URL(queueUrl).getPath()));
    final String accountId = pathParts.get(0);
    String otherQueueUrl = otherAccountSQSClient.createQueue(queueName).getQueueUrl();
    List<String> otherPathParts = Lists.newArrayList(Splitter.on('/').omitEmptyStrings().split(new URL(otherQueueUrl).getPath()));
    final String otherAccountId = otherPathParts.get(0);

    Table<String, String, Integer> expectedStatusCodes = HashBasedTable.create();
    Map<String, AmazonSQS> clients = Maps.newHashMap();
    clients.put("Main Account", accountSQSClient);
    clients.put("Main User", accountUserSQSClient);
    clients.put("Other Account", otherAccountSQSClient);
    clients.put("Other User", otherAccountUserSQSClient);

    Map<String, Command> commands = Maps.newHashMap();
    addCommand(commands,
      new Command("AddPermission") {
        @Override
        public void runCommand(AmazonSQS sqs) throws AmazonServiceException {
          sqs.addPermission(bogusQueueUrl, "label", Collections.singletonList(accountId), Collections.singletonList("SendMessage"));
        }
      }
    );
    addCommand(commands,
      new Command("ChangeMessageVisibility") {
        @Override
        public void runCommand(AmazonSQS sqs) throws AmazonServiceException {
          sqs.changeMessageVisibility(bogusQueueUrl, "blah", 0);
        }
      }
    );
    addCommand(commands,
      new Command("ChangeMessageVisibilityBatch") {
        @Override
        public void runCommand(AmazonSQS sqs) throws AmazonServiceException {
          List<ChangeMessageVisibilityBatchRequestEntry> x = new ArrayList<ChangeMessageVisibilityBatchRequestEntry>();
          ChangeMessageVisibilityBatchRequestEntry e = new ChangeMessageVisibilityBatchRequestEntry();
          e.setReceiptHandle("blah");
          e.setVisibilityTimeout(0);
          e.setId("id");
          x.add(e);
          sqs.changeMessageVisibilityBatch(bogusQueueUrl, x);
        }
      }
    );
    addCommand(commands,
      new Command("DeleteMessage") {
        @Override
        public void runCommand(AmazonSQS sqs) throws AmazonServiceException {
          sqs.deleteMessage(bogusQueueUrl, "blah");
        }
      }
    );
    addCommand(commands,
      new Command("DeleteMessageBatch") {
        @Override
        public void runCommand(AmazonSQS sqs) throws AmazonServiceException {
          List<DeleteMessageBatchRequestEntry> x = new ArrayList<DeleteMessageBatchRequestEntry>();
          DeleteMessageBatchRequestEntry e = new DeleteMessageBatchRequestEntry();
          e.setReceiptHandle("blah");
          e.setId("id");
          x.add(e);
          sqs.deleteMessageBatch(bogusQueueUrl, x);
        }
      }
    );
    addCommand(commands,
      new Command("DeleteQueue") {
        @Override
        public void runCommand(AmazonSQS sqs) throws AmazonServiceException {
          sqs.deleteQueue(bogusQueueUrl);
        }
      }
    );
    addCommand(commands,
      new Command("GetQueueAttributes") {
        @Override
        public void runCommand(AmazonSQS sqs) throws AmazonServiceException {
          sqs.getQueueAttributes(bogusQueueUrl, Collections.singletonList("All"));
        }
      }
    );
    addCommand(commands,
      new Command("GetQueueUrl") {
        @Override
        public void runCommand(AmazonSQS sqs) throws AmazonServiceException {
          GetQueueUrlRequest getQueueUrlRequest = new GetQueueUrlRequest();
          getQueueUrlRequest.setQueueOwnerAWSAccountId(accountId);
          getQueueUrlRequest.setQueueName(bogusQueueName);
          sqs.getQueueUrl(getQueueUrlRequest);
        }
      }
    );
    addCommand(commands,
      new Command("ListDeadLetterSourceQueues") {
        @Override
        public void runCommand(AmazonSQS sqs) throws AmazonServiceException {
          ListDeadLetterSourceQueuesRequest x = new ListDeadLetterSourceQueuesRequest();
          x.setQueueUrl(bogusQueueUrl);
          sqs.listDeadLetterSourceQueues(x);
        }
      }
    );
    addCommand(commands,
      new Command("PurgeQueue") {
        @Override
        public void runCommand(AmazonSQS sqs) throws AmazonServiceException {
          PurgeQueueRequest x = new PurgeQueueRequest();
          x.setQueueUrl(bogusQueueUrl);
          sqs.purgeQueue(x);
        }
      }
    );
    addCommand(commands,
      new Command("ReceiveMessage") {
        @Override
        public void runCommand(AmazonSQS sqs) throws AmazonServiceException {
          sqs.receiveMessage(bogusQueueUrl);
        }
      }
    );
    addCommand(commands,
      new Command("RemovePermission") {
        @Override
        public void runCommand(AmazonSQS sqs) throws AmazonServiceException {
          sqs.removePermission(bogusQueueUrl, "label");
        }
      }
    );
    addCommand(commands,
      new Command("SendMessage") {
        @Override
        public void runCommand(AmazonSQS sqs) throws AmazonServiceException {
          sqs.sendMessage(bogusQueueUrl, "hello");
        }
      }
    );
    addCommand(commands,
      new Command("SendMessageBatch") {
        @Override
        public void runCommand(AmazonSQS sqs) throws AmazonServiceException {
          List<SendMessageBatchRequestEntry> x = new ArrayList<SendMessageBatchRequestEntry>();
          SendMessageBatchRequestEntry e = new SendMessageBatchRequestEntry();
          e.setMessageBody("hello");
          e.setId("id");
          x.add(e);
          sqs.sendMessageBatch(bogusQueueUrl, x);
        }
      }
    );
    addCommand(commands,
      new Command("SetQueueAttributes") {
        @Override
        public void runCommand(AmazonSQS sqs) throws AmazonServiceException {
          sqs.setQueueAttributes(bogusQueueUrl, Collections.singletonMap("VisibilityTimeout", "0"));
        }
      }
    );

    // Most errors will return QUEUE_DOES_NOT_EXIST
    for (String commandName: commands.keySet()) {
      for (String clientName: clients.keySet()) {
        // some commands and clients should return ACCESS_DENIED
        // non "shared-queue" commands with cross-account credentials
        if (ImmutableSet.of("AddPermission","DeleteQueue","RemovePermission","SetQueueAttributes").contains(commandName) &&
            ImmutableSet.of("Other Account", "Other User").contains(clientName)
          ) {
          expectedStatusCodes.put(commandName, clientName, ACCESS_DENIED);
        } else {
          expectedStatusCodes.put(commandName, clientName, QUEUE_DOES_NOT_EXIST);
        }
      }
    }

    for (String commandName: commands.keySet()) {
      for (String clientName: clients.keySet()) {
        int expectedCode = expectedStatusCodes.get(commandName, clientName);
        int actualCode = commands.get(commandName).getStatusFromCommand(clients.get(clientName));
        assertThat(expectedCode == actualCode, "Calling " + commandName + " with client " + clientName + ", status code was " + actualCode + ", expected " + expectedCode);
      }
    }
  }

  public void addCommand(Map<String, Command> commands, Command command) {
    commands.put(command.getName(), command);
  }


  private static final int SUCCESS = 200;
  private static final int QUEUE_DOES_NOT_EXIST = 400;
  private static final int ACCESS_DENIED = 403;

  public abstract static class Command {
    public String getName() {
      return name;
    }

    private String name;

    protected Command(String name) {
      this.name = name;
    }

    public final int getStatusFromCommand(AmazonSQS sqs) {
      try {
        runCommand(sqs);
        return SUCCESS;
      } catch (AmazonServiceException e) {
        return e.getStatusCode();
      }
    }
    public abstract void runCommand(AmazonSQS sqs) throws AmazonServiceException;
  }


}