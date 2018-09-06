package com.eucalyptus.tests.suites;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;
import com.eucalyptus.tests.awssdk.S3BucketPolicyTests;
import com.eucalyptus.tests.awssdk.S3CopyObjectTests;
import com.eucalyptus.tests.awssdk.S3CorsTests;
import com.eucalyptus.tests.awssdk.S3ListObjectsTests;
import com.eucalyptus.tests.awssdk.S3ListVersionsTests;
import com.eucalyptus.tests.awssdk.S3ObjectLifecycleTests;
import com.eucalyptus.tests.awssdk.S3SignatureTests;

@RunWith(Suite.class)
@SuiteClasses({
    // suites
    S3ShortSuite.class,

    // tests
    S3BucketPolicyTests.class,
    S3CopyObjectTests.class,
    S3CorsTests.class,
    S3ListObjectsTests.class,
    S3ListVersionsTests.class,
    S3ObjectLifecycleTests.class,
    S3SignatureTests.class,
})
public class S3FullSuite {
  // junit test suite as defined by SuiteClasses annotation
}
