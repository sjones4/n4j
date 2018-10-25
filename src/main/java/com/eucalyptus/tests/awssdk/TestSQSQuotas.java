package com.eucalyptus.tests.awssdk;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.model.ListQueuesResult;
import com.github.sjones4.youcan.youare.model.PutAccountPolicyRequest;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import static com.eucalyptus.tests.awssdk.N4j.*;

/**
 * Created by ethomas on 9/21/16.
 */
public class TestSQSQuotas {

  private static String account;
  private static AmazonSQS accountSQSClient;

  @BeforeClass
  public static void init() throws Exception {
    print("### PRE SUITE SETUP - " + TestSQSQuotas.class.getSimpleName());

    try {
      getCloudInfoAndSqs();
      account = "sqs-account-quo-a-" + System.currentTimeMillis();
      synchronizedCreateAccount(account);
      accountSQSClient = getSqsClientWithNewAccount(account, "admin");
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
    print("### POST SUITE CLEANUP - " + TestSQSQuotas.class.getSimpleName());
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
  public void testQueueQuota() {
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
