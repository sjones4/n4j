package com.eucalyptus.tests.suites;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;
import com.eucalyptus.tests.awssdk.S3BucketACLTests;
import com.eucalyptus.tests.awssdk.S3BucketTests;
import com.eucalyptus.tests.awssdk.S3CopyObjectTests;
import com.eucalyptus.tests.awssdk.S3CorsTests;
import com.eucalyptus.tests.awssdk.S3ListMpuTests;
import com.eucalyptus.tests.awssdk.S3ListObjectsTests;
import com.eucalyptus.tests.awssdk.S3ListVersionsTests;
import com.eucalyptus.tests.awssdk.S3MultiPartUploadTests;
import com.eucalyptus.tests.awssdk.S3ObjectACLAcrossAccountsTests;
import com.eucalyptus.tests.awssdk.S3ObjectACLTests;
import com.eucalyptus.tests.awssdk.S3ObjectLifecycleTests;
import com.eucalyptus.tests.awssdk.S3ObjectMultiDeleteTests;
import com.eucalyptus.tests.awssdk.S3ObjectTests;

@RunWith(Suite.class)
@SuiteClasses({
    // suites
    S3ShortSuite.class,

    // tests
    S3CopyObjectTests.class,
    S3CorsTests.class,
    S3ListObjectsTests.class,
    S3ListVersionsTests.class,
    S3ObjectLifecycleTests.class,
})
public class S3FullSuite {
  // junit test suite as defined by SuiteClasses annotation
}
