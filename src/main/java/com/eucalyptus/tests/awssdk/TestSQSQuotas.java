package com.eucalyptus.tests.awssdk;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.AmazonWebServiceClient;
import com.amazonaws.Request;
import com.amazonaws.handlers.RequestHandler2;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.model.AddPermissionRequest;
import com.amazonaws.services.sqs.model.ChangeMessageVisibilityBatchRequest;
import com.amazonaws.services.sqs.model.ChangeMessageVisibilityBatchRequestEntry;
import com.amazonaws.services.sqs.model.ChangeMessageVisibilityRequest;
import com.amazonaws.services.sqs.model.DeleteMessageBatchRequest;
import com.amazonaws.services.sqs.model.DeleteMessageBatchRequestEntry;
import com.amazonaws.services.sqs.model.DeleteMessageRequest;
import com.amazonaws.services.sqs.model.DeleteQueueRequest;
import com.amazonaws.services.sqs.model.GetQueueAttributesRequest;
import com.amazonaws.services.sqs.model.GetQueueAttributesResult;
import com.amazonaws.services.sqs.model.ListDeadLetterSourceQueuesRequest;
import com.amazonaws.services.sqs.model.ListDeadLetterSourceQueuesResult;
import com.amazonaws.services.sqs.model.ListQueuesResult;
import com.amazonaws.services.sqs.model.Message;
import com.amazonaws.services.sqs.model.PurgeQueueRequest;
import com.amazonaws.services.sqs.model.ReceiveMessageRequest;
import com.amazonaws.services.sqs.model.ReceiveMessageResult;
import com.amazonaws.services.sqs.model.RemovePermissionRequest;
import com.amazonaws.services.sqs.model.SendMessageBatchRequest;
import com.amazonaws.services.sqs.model.SendMessageBatchRequestEntry;
import com.amazonaws.services.sqs.model.SendMessageRequest;
import com.amazonaws.services.sqs.model.SetQueueAttributesRequest;
import com.github.sjones4.youcan.youare.model.PutAccountPolicyRequest;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.eucalyptus.tests.awssdk.N4j.*;

/**
 * Created by ethomas on 9/21/16.
 */
public class TestSQSQuotas {

  private String account;
  private AmazonSQS accountSQSClient;

  @BeforeClass
  public void init() throws Exception {
    print("### PRE SUITE SETUP - " + this.getClass().getSimpleName());

    try {
      getCloudInfoAndSqs();
      account = "sqs-account-quo-a-" + System.currentTimeMillis();
      synchronizedCreateAccount(account);
      accountSQSClient = getSqsClientWithNewAccount(account, "admin");
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
  public void testQueueQuota() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testQueueQuota");
    PutAccountPolicyRequest putAccountPolicyRequest = new PutAccountPolicyRequest();
    putAccountPolicyRequest.setAccountName(account);
    putAccountPolicyRequest.setPolicyName("queuequota");
    putAccountPolicyRequest.setPolicyDocument(
      "{\n" +
        "  \"Version\":\"2011-04-01\",\n" +
        "  \"Statement\":[{\n" +
        "  \"Sid\":\"4\",\n" +
        "  \"Effect\":\"Limit\",\n" +
        "  \"Action\":\"sqs:CreateQueue\",\n" +
        "   \"Resource\":\"*\",\n" +
        "   \"Condition\":{\n" +
        "     \"NumericLessThanEquals\": { \"sqs:quota-queuenumber\":\"5\" }\n" +
        "   }\n" +
        "   }]\n" +
        "}\n"
    );
    youAre.putAccountPolicy(putAccountPolicyRequest);
    for (int i = 0; i < 5; i++) {
      accountSQSClient.createQueue("queue-" + i);
    }
    try {
      accountSQSClient.createQueue("queue-6");
      assertThat(false, "Should not succeed in creating queue that exceeds quota");
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == 400, "Successfully did not succeed in creating queue that exceeds quota");
    }
  }
}
