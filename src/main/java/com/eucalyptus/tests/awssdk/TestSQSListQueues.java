package com.eucalyptus.tests.awssdk;

import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.model.ListQueuesResult;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Collection;
import java.util.Map;

import static com.eucalyptus.tests.awssdk.N4j.*;

/**
 * Created by ethomas on 9/21/16.
 */
public class TestSQSListQueues {

  private static String account;
  private static String otherAccount;

  private static AmazonSQS accountSQSClient;
  private static AmazonSQS otherAccountSQSClient;

  @BeforeClass
  public static void init() throws Exception {
    print("### PRE SUITE SETUP - " + TestSQSListQueues.class.getSimpleName());

    try {
      getCloudInfoAndSqs();
      account = "sqs-account-lq-a-" + System.currentTimeMillis();
      synchronizedCreateAccount(account);
      accountSQSClient = getSqsClientWithNewAccount(account, "admin");
      otherAccount = "sqs-account-lq-b-" + System.currentTimeMillis();
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
    print("### POST SUITE CLEANUP - " + TestSQSListQueues.class.getSimpleName());
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
  public void testListQueues() {
    testInfo(this.getClass().getSimpleName() + " - testListQueues");
    Collection<String> queueNames = ImmutableSet.of(
      "a","b","c","aa","bb","cc","ab","ac","aab","aba","abb","abc"
    );
    Map<String, String> queueUrls = Maps.newHashMap();
    for (String queueName: queueNames) {
      queueUrls.put(queueName, accountSQSClient.createQueue(queueName).getQueueUrl());
    }

    assertThat(
      match(accountSQSClient.listQueues(), queueUrls,
        "a","b","c","aa","bb","cc","ab","ac","aab","aba","abb","abc"),
      "test no prefix");
    assertThat(
      match(accountSQSClient.listQueues("a"), queueUrls,
        "a","aa","ab","ac","aab","aba","abb","abc"),
      "test 'a' prefix");
    assertThat(
      match(accountSQSClient.listQueues("b"), queueUrls,
        "b","bb"),
      "test 'b' prefix");
    assertThat(
      match(accountSQSClient.listQueues("c"), queueUrls,
        "c","cc"),
      "test 'c' prefix");
    assertThat(
      match(accountSQSClient.listQueues("d"), queueUrls),
      "test 'd' prefix");
    assertThat(
      match(accountSQSClient.listQueues("aa"), queueUrls,
        "aa","aab"),
      "test 'aa' prefix");
    assertThat(
      match(accountSQSClient.listQueues("ab"), queueUrls,
        "ab","aba","abb","abc"),
      "test 'ab' prefix");
    assertThat(
      match(accountSQSClient.listQueues("ac"), queueUrls,
        "ac"),
      "test 'ac' prefix");
    assertThat(
      match(accountSQSClient.listQueues("aab"), queueUrls,
        "aab"),
      "test no prefix");
  }

  private boolean match(ListQueuesResult listQueueResult, Map<String, String> queueUrls, String... keys) {
    Collection<String> returnedQueueUrls = Sets.newHashSet();
    Collection<String> expectedQueueUrls = Sets.newHashSet();
    if (listQueueResult != null && listQueueResult.getQueueUrls() != null) {
      returnedQueueUrls.addAll(listQueueResult.getQueueUrls());
    }
    if (keys != null) {
      for (String key: keys) {
        expectedQueueUrls.add(queueUrls.get(key));
      }
    }
    return expectedQueueUrls.equals(returnedQueueUrls);
  }


}
