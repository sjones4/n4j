package com.eucalyptus.tests.suites;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;
import com.eucalyptus.tests.awssdk.*;

@RunWith(Suite.class)
@SuiteClasses({
    // suites
    AutoScalingShortSuite.class,

    // tests
    TestAutoScalingActivities.class,
    TestAutoScalingAvailabilityZoneRebalancing.class,
    TestAutoScalingCooldown.class,
    TestAutoScalingDescribeGroupsInstances.class,
    TestAutoScalingDescribeInstances.class,
    TestAutoScalingEc2InstanceHealthMonitoring.class,
    TestAutoScalingEC2InstanceTerminationProtection.class,
    TestAutoScalingELBAddRemoveInstances.class,
    TestAutoScalingELBInstanceHealthMonitoring.class,
    TestAutoScalingInstanceProfile.class,
    TestAutoScalingLaunchAndTerminate.class,
    TestAutoScalingMetricsSubmission.class,
    TestAutoScalingMultipleAvailabilityZones.class,
    TestAutoScalingSuspendAndResumeProcesses.class,
    TestAutoScalingTerminateInstances.class,
})
public class AutoScalingFullSuite {
  // junit test suite as defined by SuiteClasses annotation
}