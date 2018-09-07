package com.eucalyptus.tests.suites;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;
import com.eucalyptus.tests.awssdk.TestSQSAnonymousAccess;
import com.eucalyptus.tests.awssdk.TestSQSAttributeValuesInMessages;
import com.eucalyptus.tests.awssdk.TestSQSChangeMessageVisibilityBatch;
import com.eucalyptus.tests.awssdk.TestSQSCloudWatchMetrics;
import com.eucalyptus.tests.awssdk.TestSQSCrossAccountStackPolicies;
import com.eucalyptus.tests.awssdk.TestSQSDeadLetterQueue;
import com.eucalyptus.tests.awssdk.TestSQSDelaySeconds;
import com.eucalyptus.tests.awssdk.TestSQSDeleteMessageBatch;
import com.eucalyptus.tests.awssdk.TestSQSIAMPolicies;
import com.eucalyptus.tests.awssdk.TestSQSListDeadLetterSourceQueues;
import com.eucalyptus.tests.awssdk.TestSQSLongPolling;
import com.eucalyptus.tests.awssdk.TestSQSMessageExpirationPeriod;
import com.eucalyptus.tests.awssdk.TestSQSPermissions;
import com.eucalyptus.tests.awssdk.TestSQSPurgeQueue;
import com.eucalyptus.tests.awssdk.TestSQSQueuePolicy;
import com.eucalyptus.tests.awssdk.TestSQSQueueUrlBinding;
import com.eucalyptus.tests.awssdk.TestSQSQuotas;
import com.eucalyptus.tests.awssdk.TestSQSReadOnlyAttributes;
import com.eucalyptus.tests.awssdk.TestSQSReceiveMessage;
import com.eucalyptus.tests.awssdk.TestSQSStatusCodesForNonexistentQueues;
import com.eucalyptus.tests.awssdk.TestSQSVisibilityTimeout;


/**
 *
 */
@RunWith(Suite.class)
@SuiteClasses({
    // suites
    SQSShortSuite.class,

    // tests
    TestSQSAnonymousAccess.class,
    TestSQSAttributeValuesInMessages.class,
    TestSQSChangeMessageVisibilityBatch.class,
    TestSQSCrossAccountStackPolicies.class,
    TestSQSDeadLetterQueue.class,
    TestSQSDelaySeconds.class,
    TestSQSDeleteMessageBatch.class,
    TestSQSIAMPolicies.class,
    TestSQSListDeadLetterSourceQueues.class,
    TestSQSLongPolling.class,
    TestSQSMessageExpirationPeriod.class,
    TestSQSPermissions.class,
    TestSQSPurgeQueue.class,
    TestSQSQueuePolicy.class,
    TestSQSQueueUrlBinding.class,
    TestSQSQuotas.class,
    TestSQSReadOnlyAttributes.class,
    TestSQSReceiveMessage.class,
    TestSQSStatusCodesForNonexistentQueues.class,
    TestSQSVisibilityTimeout.class,
})
public class SQSFullSuite {
  // junit test suite as defined by SuiteClasses annotation       
}
