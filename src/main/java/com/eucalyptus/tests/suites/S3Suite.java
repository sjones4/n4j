package com.eucalyptus.tests.suites;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;
import com.eucalyptus.tests.awssdk.*;

@RunWith(Suite.class)
@SuiteClasses({
    S3BucketACLTests.class,
    S3BucketTests.class,
    S3CopyObjectTests.class,
    S3CorsTests.class,
    S3ListMpuTests.class,
    S3ListObjectsTests.class,
    S3ListVersionsTests.class,
    S3MultiPartUploadTests.class,
    S3ObjectACLAcrossAccountsTests.class,
    S3ObjectACLTests.class,
    S3ObjectLifecycleTests.class,
    S3ObjectMultiDeleteTests.class,
    S3ObjectTests.class,
})
public class S3Suite {
  // junit test suite as defined by SuiteClasses annotation
}