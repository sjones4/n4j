package com.eucalyptus.tests.suites;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;
import com.eucalyptus.tests.awssdk.*;

@RunWith(Suite.class)
@SuiteClasses({
    CloudWatchDeleteMetricAlarmTest.class,
    CloudWatchDescribeAlarmHistoryTest.class,
    CloudWatchDescribeAlarmsForMetricTest.class,
    CloudWatchDescribeAlarmsTest.class,
    CloudWatchDisableAlarmActionsTest.class,
    CloudWatchEnableAlarmActionsTest.class,
    CloudWatchGetMetricStatisticsTest.class,
    CloudWatchListMetricsTest.class,
    CloudWatchPutMetricAlarmTest.class,
    CloudWatchStateChangeTest.class,
})
public class CloudWatchSuite {
  // junit test suite as defined by SuiteClasses annotation
}