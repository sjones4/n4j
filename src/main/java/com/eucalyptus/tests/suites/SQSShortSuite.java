package com.eucalyptus.tests.suites;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;
import com.eucalyptus.tests.awssdk.TestSQSAdminFunctions;
import com.eucalyptus.tests.awssdk.TestSQSAttributes;
import com.eucalyptus.tests.awssdk.TestSQSChangeMessageVisibility;
import com.eucalyptus.tests.awssdk.TestSQSCreateQueue;
import com.eucalyptus.tests.awssdk.TestSQSDeleteMessage;
import com.eucalyptus.tests.awssdk.TestSQSDeleteQueue;
import com.eucalyptus.tests.awssdk.TestSQSGetQueueUrl;
import com.eucalyptus.tests.awssdk.TestSQSListQueues;
import com.eucalyptus.tests.awssdk.TestSQSSendMessage;
import com.eucalyptus.tests.awssdk.TestSQSSendMessageBatch;
import com.eucalyptus.tests.awssdk.TestSQSSenderId;

@RunWith(Suite.class)
@SuiteClasses({
    TestSQSAdminFunctions.class,
    TestSQSAttributes.class,
    TestSQSChangeMessageVisibility.class,
    TestSQSCreateQueue.class,
    TestSQSDeleteMessage.class,
    TestSQSDeleteQueue.class,
    TestSQSGetQueueUrl.class,
    TestSQSListQueues.class,
    TestSQSSenderId.class,
    TestSQSSendMessage.class,
    TestSQSSendMessageBatch.class,
})
public class SQSShortSuite {
  // junit test suite as defined by SuiteClasses annotation
}
