package com.eucalyptus.tests.suites;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;
import com.eucalyptus.tests.awssdk.*;

@RunWith(Suite.class)
@SuiteClasses({
    TestAutoScalingAdministration.class,
    TestAutoScalingDescribePolicyAlarms.class,
    TestAutoScalingDescribeTags.class,
    TestAutoScalingEC2ReferenceValidation.class,
    TestAutoScalingELBReferenceValidation.class,
    TestAutoScalingMetricsManagement.class,
    TestAutoScalingTags.class,
    TestAutoScalingUnsupportedOptions.class,
    TestAutoScalingValidation.class,
    TestAutoScalingVMTypeReferenceValidation.class,
})
public class AutoScalingShortSuite {
  // junit test suite as defined by SuiteClasses annotation
}