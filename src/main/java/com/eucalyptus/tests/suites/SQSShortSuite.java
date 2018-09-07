package com.eucalyptus.tests.suites;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;
import com.eucalyptus.tests.awssdk.TestSQSAdminFunctions;
import com.eucalyptus.tests.awssdk.TestSQSAnonymousAccess;
import com.eucalyptus.tests.awssdk.TestSQSAttributeValuesInMessages;
import com.eucalyptus.tests.awssdk.TestSQSAttributes;
import com.eucalyptus.tests.awssdk.TestSQSChangeMessageVisibility;
import com.eucalyptus.tests.awssdk.TestSQSChangeMessageVisibilityBatch;
import com.eucalyptus.tests.awssdk.TestSQSCreateQueue;
import com.eucalyptus.tests.awssdk.TestSQSDelaySeconds;
import com.eucalyptus.tests.awssdk.TestSQSDeleteMessage;
import com.eucalyptus.tests.awssdk.TestSQSDeleteMessageBatch;
import com.eucalyptus.tests.awssdk.TestSQSDeleteQueue;
import com.eucalyptus.tests.awssdk.TestSQSGetQueueUrl;
import com.eucalyptus.tests.awssdk.TestSQSListDeadLetterSourceQueues;
import com.eucalyptus.tests.awssdk.TestSQSListQueues;
import com.eucalyptus.tests.awssdk.TestSQSMessageExpirationPeriod;
import com.eucalyptus.tests.awssdk.TestSQSPermissions;
import com.eucalyptus.tests.awssdk.TestSQSPurgeQueue;
import com.eucalyptus.tests.awssdk.TestSQSQueuePolicy;
import com.eucalyptus.tests.awssdk.TestSQSQueueUrlBinding;
import com.eucalyptus.tests.awssdk.TestSQSQuotas;
import com.eucalyptus.tests.awssdk.TestSQSReadOnlyAttributes;
import com.eucalyptus.tests.awssdk.TestSQSReceiveMessage;
import com.eucalyptus.tests.awssdk.TestSQSSendMessage;
import com.eucalyptus.tests.awssdk.TestSQSSendMessageBatch;
import com.eucalyptus.tests.awssdk.TestSQSSenderId;
import com.eucalyptus.tests.awssdk.TestSQSStatusCodesForNonexistentQueues;
import com.eucalyptus.tests.awssdk.TestSQSVisibilityTimeout;

@RunWith(Suite.class)
@SuiteClasses({
    TestSQSAdminFunctions.class,
    TestSQSAnonymousAccess.class,
    TestSQSAttributes.class,
    TestSQSAttributeValuesInMessages.class,
    TestSQSChangeMessageVisibility.class,
    TestSQSChangeMessageVisibilityBatch.class,
    TestSQSCreateQueue.class,
    TestSQSDelaySeconds.class,
    TestSQSDeleteMessage.class,
    TestSQSDeleteMessageBatch.class,
    TestSQSDeleteQueue.class,
    TestSQSGetQueueUrl.class,
    TestSQSListDeadLetterSourceQueues.class,
    TestSQSListQueues.class,
    TestSQSMessageExpirationPeriod.class,
    TestSQSPermissions.class,
    TestSQSPurgeQueue.class,
    TestSQSQueuePolicy.class,
    TestSQSQueueUrlBinding.class,
    TestSQSQuotas.class,
    TestSQSReadOnlyAttributes.class,
    TestSQSReceiveMessage.class,
    TestSQSSenderId.class,
    TestSQSSendMessage.class,
    TestSQSSendMessageBatch.class,
    TestSQSStatusCodesForNonexistentQueues.class,
    TestSQSVisibilityTimeout.class,
})
public class SQSShortSuite {
  // junit test suite as defined by SuiteClasses annotation
}
