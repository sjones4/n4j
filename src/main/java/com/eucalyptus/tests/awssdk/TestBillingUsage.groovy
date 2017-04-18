package com.eucalyptus.tests.awssdk

import com.amazonaws.AmazonServiceException
import com.amazonaws.ClientConfiguration
import com.amazonaws.auth.AWSCredentialsProvider
import com.amazonaws.auth.AWSStaticCredentialsProvider
import com.amazonaws.auth.BasicAWSCredentials
import com.github.sjones4.youcan.youbill.YouBill
import com.github.sjones4.youcan.youbill.YouBillClient
import com.github.sjones4.youcan.youec2reports.YouEc2Reports
import com.github.sjones4.youcan.youec2reports.YouEc2ReportsClient
import com.github.sjones4.youcan.youec2reports.model.InstanceUsageFilter
import com.github.sjones4.youcan.youec2reports.model.InstanceUsageFilters
import com.github.sjones4.youcan.youec2reports.model.InstanceUsageGroup
import com.github.sjones4.youcan.youec2reports.model.ViewInstanceUsageReportRequest
import com.github.sjones4.youcan.youbill.model.ViewMonthlyUsageRequest
import com.github.sjones4.youcan.youec2reports.model.ViewReservedInstanceUtilizationReportRequest
import com.github.sjones4.youcan.youbill.model.ViewUsageRequest
import com.google.common.collect.Lists
import org.testng.annotations.AfterMethod
import org.testng.annotations.BeforeClass
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test

import java.util.concurrent.TimeUnit

import static com.eucalyptus.tests.awssdk.N4j.ACCESS_KEY
import static com.eucalyptus.tests.awssdk.N4j.CLC_IP
import static com.eucalyptus.tests.awssdk.N4j.SECRET_KEY
import static com.eucalyptus.tests.awssdk.N4j.minimalInit

class TestBillingUsage {

    // for all tests
    private String host
    private AWSCredentialsProvider credentials

    // for each test
    private List<Runnable> cleanupTasks

    @BeforeClass
    void init( ) {
        N4j.print("### SETUP - ${getClass().simpleName}")
        minimalInit( )
        this.host = CLC_IP
        this.credentials = new AWSStaticCredentialsProvider( new BasicAWSCredentials( ACCESS_KEY, SECRET_KEY ) )
    }

    @BeforeMethod
    void initTest(  ) {
        N4j.print( "Initializing clean up tasks" )
        cleanupTasks = [ ]
    }

    @AfterMethod
    void cleanup( ) {
        N4j.print( "Running clean up tasks" )
        Collections.reverse(cleanupTasks)
        for (final Runnable cleanupTask : cleanupTasks) {
            try {
                cleanupTask.run()
            } catch (Exception e) {
                N4j.print("Unable to run clean up task: ${e}")
            }
        }
    }

    private String cloudUri( String servicePath ) {
        URI.create( "http://" + host + ":8773/" )
                .resolve( servicePath )
                .toString()
    }

    private YouBill getYouBillClient(final AWSCredentialsProvider credentials, String signerOverride = null ) {
        final YouBillClient bill = new YouBillClient(
                credentials,
                signerOverride ? new ClientConfiguration( signerOverride: signerOverride ) : new ClientConfiguration( )
        )
        bill.setEndpoint( cloudUri( '/services/Portal' ) )
        bill
    }

    private YouEc2Reports getYouEc2ReportsClient(final AWSCredentialsProvider credentials, String signerOverride = null ) {
        final YouEc2ReportsClient ec2reports = new YouEc2ReportsClient(
                credentials,
                signerOverride ? new ClientConfiguration( signerOverride: signerOverride ) : new ClientConfiguration( )
        )
        ec2reports.setEndpoint( cloudUri( '/services/Ec2Reports' ) )
        ec2reports
    }

    @Test
    void billingServiceUsageTest( ) throws Exception {
        // verify that signature v2 requests are rejected
        try {
            N4j.print( 'Making ec2reports service request with unsupported signature version' )
            getYouEc2ReportsClient(credentials, 'QueryStringSignerType').viewInstanceUsageReport( new ViewInstanceUsageReportRequest( ) )
            N4j.assertThat( false, 'Expected error due to request with unsupported signature version' )
        } catch ( AmazonServiceException e ) {
            N4j.print( "Exception for request with invalid signature version: ${e}" )
            N4j.assertThat(
                    (e.message?:'').contains( 'Signature version not supported' ),
                    'Expected failure due to signature version' )
        }

        getYouBillClient( credentials ).with {
            N4j.print('Calling viewUsage')
            viewUsage(new ViewUsageRequest(
                    services: 'Ec2',
                    usageTypes: 'all',
                    operations: 'all',
                    reportGranularity: 'hour',
                    timePeriodFrom: (new Date(System.currentTimeMillis()- TimeUnit.DAYS.toMillis(30))),
                    timePeriodTo: new Date()
            )).with {
                N4j.print("View usage data: ${it}")
                N4j.print("data: ${getData()}")
            }

            N4j.print('Calling viewMonthlyUsage')
            viewMonthlyUsage(new ViewMonthlyUsageRequest(
                    year: '2016',
                    month: '12'
            )).with {
                N4j.print("View monthly usage data: ${it}")
                N4j.print("data: ${getData()}")
            }
        }

        getYouEc2ReportsClient( credentials ).with {
            N4j.print('Calling ec2reports::viewInstanceUsageReport')
            viewInstanceUsageReport(new ViewInstanceUsageReportRequest(
                    granularity: 'Daily',
                    timeRangeStart: (new Date(System.currentTimeMillis()- TimeUnit.DAYS.toMillis(30))),
                    timeRangeEnd: new Date(),
                    groupBy: new InstanceUsageGroup(
                            type: 'Tag',
                            key: 'User'
                    ),
                    filters: new InstanceUsageFilters(
                            member: Lists.newArrayList(
                                    new InstanceUsageFilter(
                                            type: 'InstanceType',
                                            key: 'm1.small'
                                    ),
                                    new InstanceUsageFilter(
                                            type: 'Tag',
                                            key: 'User',
                                            value: 'EUCA'
                                    ),
                                    new InstanceUsageFilter(
                                            type: 'Platforms',
                                            key: 'Linux/Unix'
                                    ),
                                    new InstanceUsageFilter(
                                            type: 'Platforms',
                                            key: 'Windows'
                                    )
                            )
                    )
            ))
            .with {
                N4j.print("View instance usage report: ${it}")
                N4j.print("usage report: ${ getUsageReport()}")
            }
            N4j.print('Calling ec2reports::viewReservedInstanceUtilizationReport')
            viewReservedInstanceUtilizationReport(new ViewReservedInstanceUtilizationReportRequest())
            .with {
                N4j.print("View reserved instance utilization report: ${it}")
                N4j.print("utialization report: ${ getUtilizationReport() }")
            }
        }
        N4j.print( "Test complete" )
   }
}