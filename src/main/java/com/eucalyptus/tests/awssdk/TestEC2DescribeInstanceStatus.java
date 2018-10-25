/*************************************************************************
 * Copyright 2009-2016 Eucalyptus Systems, Inc.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see http://www.gnu.org/licenses/.
 *
 * Please contact Eucalyptus Systems, Inc., 6755 Hollister Ave., Goleta
 * CA 93117, USA or visit http://www.eucalyptus.com/licenses/ if you need
 * additional information or have any questions.
 ************************************************************************/

package com.eucalyptus.tests.awssdk;

import static com.eucalyptus.tests.awssdk.N4j.IMAGE_ID;
import static com.eucalyptus.tests.awssdk.N4j.ec2;
import static com.eucalyptus.tests.awssdk.N4j.print;
import static com.eucalyptus.tests.awssdk.N4j.sleep;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Hashtable;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import javax.naming.Context;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import org.junit.Assert;
import org.junit.Test;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.model.DescribeInstanceStatusRequest;
import com.amazonaws.services.ec2.model.DescribeInstanceStatusResult;
import com.amazonaws.services.ec2.model.DescribeInstancesRequest;
import com.amazonaws.services.ec2.model.DescribeInstancesResult;
import com.amazonaws.services.ec2.model.Filter;
import com.amazonaws.services.ec2.model.GetConsoleOutputRequest;
import com.amazonaws.services.ec2.model.GetConsoleOutputResult;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.InstanceStatus;
import com.amazonaws.services.ec2.model.InstanceStatusSummary;
import com.amazonaws.services.ec2.model.Reservation;
import com.amazonaws.services.ec2.model.RunInstancesRequest;
import com.amazonaws.services.ec2.model.RunInstancesResult;
import com.amazonaws.services.ec2.model.TerminateInstancesRequest;
import com.github.sjones4.youcan.youserv.YouServ;
import com.github.sjones4.youcan.youserv.YouServClient;
import com.github.sjones4.youcan.youserv.model.DescribeServicesRequest;
import com.github.sjones4.youcan.youserv.model.DescribeServicesResult;
import com.github.sjones4.youcan.youserv.model.ServiceStatus;
import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.base.Throwables;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;

/**
 * This application tests the EC2 DescribeInstanceStatus operation.
 *
 * This is verification for the task:
 *
 * https://eucalyptus.atlassian.net/browse/EUCA-5052
 */
public class TestEC2DescribeInstanceStatus {

  private YouServ getServicesClient( ) {
    return getServicesClient( new AWSStaticCredentialsProvider(
        new BasicAWSCredentials( N4j.ACCESS_KEY, N4j.SECRET_KEY )
    ) );
  }

  private YouServ getServicesClient( final AWSCredentialsProvider credentials ) {
    YouServClient youServ = new YouServClient( credentials );
    youServ.setEndpoint( N4j.SERVICES_ENDPOINT );
    return youServ;
  }

  private Set<String> getDnsHosts( final YouServ youServ ) {
    final Set<String> dnsHosts = Sets.newLinkedHashSet( );
    final DescribeServicesResult describeServicesResult = youServ.describeServices( new DescribeServicesRequest( )
            .withFilters( new com.github.sjones4.youcan.youserv.model.Filter( )
                .withName( "service-type" )
                .withValues( "dns" ) ) );
    for ( final ServiceStatus status : describeServicesResult.getServiceStatuses( ) ) {
      dnsHosts.add( URI.create( status.getServiceId( ).getUri( ) ).getHost( ) );
    }
    return dnsHosts;
  }

  private String lookup( String name, Set<String> dnsServers ) {
    final Hashtable<String,String> env = new Hashtable<>();
    env.put( Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.dns.DnsContextFactory" );
    env.put(
        Context.PROVIDER_URL,
        dnsServers.stream( ).map( host -> "dns://"+host+"/" ).collect( Collectors.joining( " " ) ) );
    env.put( Context.AUTHORITATIVE, "true" );
    DirContext ictx = null;
    try {
      ictx = new InitialDirContext( env );
      final Attributes attrs = ictx.getAttributes( name, new String[]{"A"} );
      final Attribute attr = attrs.get( "a" );
      final String ip = attr==null ? null : String.valueOf( attr.get( ) );
      return ip;
    } catch ( NamingException e ) {
      throw Throwables.propagate( e );
    } finally {
      if (ictx != null) {
        try {
          ictx.close();
        } catch ( NamingException e ) {
          throw Throwables.propagate( e );
        }
      }
    }
  }

  @Test
  public void EC2DescribeInstanceStatusTest( ) throws Exception {
    N4j.testInfo( this.getClass( ).getSimpleName( ) );
    N4j.getCloudInfo( );

    final List<Runnable> cleanupTasks = new ArrayList<Runnable>( );
    try {

      // launch instance

      print( "Running instance" );
      final RunInstancesResult runResult = ec2
          .runInstances( new RunInstancesRequest( )
              .withImageId( IMAGE_ID ).withMinCount( 1 )
              .withMaxCount( 1 ) );
      final String instanceId = getInstancesIds(
          runResult.getReservation( ) ).get( 0 );
      print( "Launched instance: " + instanceId );
      cleanupTasks.add( new Runnable( ) {
        @Override
        public void run( ) {
          print( "Terminating instance: " + instanceId );
          ec2.terminateInstances( new TerminateInstancesRequest( )
              .withInstanceIds( instanceId ) );
        }
      } );

      // wait for instance to be running

      final long timeout = TimeUnit.MINUTES.toMillis( 15 );
      waitForInstance( ec2, timeout, instanceId, "pending" );
      final String az = waitForInstance( ec2, timeout, instanceId, "running" );

      // verify response format

      final DescribeInstanceStatusResult instanceStatusResult = ec2
          .describeInstanceStatus( new DescribeInstanceStatusRequest( )
              .withInstanceIds( instanceId ) );
      Assert.assertTrue( "Instance not found", instanceStatusResult.getInstanceStatuses( ).size( ) == 1
      );
      final InstanceStatus status = instanceStatusResult
          .getInstanceStatuses( ).get( 0 );
      Assert.assertTrue( "Null instance status", status != null );
      Assert.assertTrue( "Missing availability zone", status.getAvailabilityZone( ) != null );
      Assert.assertTrue(
          "Unexpected instance id : " + status.getInstanceId( ),
          instanceId.equals( status.getInstanceId( ) ) );
      Assert.assertTrue( "Missing instance state", status.getInstanceState( ) != null );
      Assert.assertTrue(
          "Unexpected instance state code : " + status.getInstanceState( ).getCode( ),
          status.getInstanceState( ).getCode( ) == 16 );
      Assert.assertTrue(
          "Unexpected instance state name : " + status.getInstanceState( ).getName( ),
          "running".equals( status.getInstanceState( ).getName( ) ) );
      assertStatusSummary( status.getInstanceStatus( ), "instance" );
      assertStatusSummary( status.getSystemStatus( ), "system" );

      // test filters

      String[][] filterTestValues = {
          { "availability-zone", az, "invalid-zone-name" },
          { "instance-state-name", "running", "pending" },
          { "instance-state-code", "16", "0" },
          { "system-status.status", "ok", "impaired" },
          { "system-status.reachability", "passed", "failed" },
          { "instance-status.status", "ok", "impaired" },
          { "instance-status.reachability", "passed", "failed" }, };
      for ( final String[] values : filterTestValues ) {
        final String filterName = values[ 0 ];
        final String filterGoodValue = values[ 1 ];
        final String filterBadValue = values[ 2 ];

        print( "Testing filter - " + filterName );
        Assert.assertTrue(
            "Expected result for " + filterName + "=" + filterGoodValue,
            describeInstanceStatus( ec2, instanceId, filterName, filterGoodValue, 1 ) );
        Assert.assertTrue(
            "Expected no results for " + filterName + "=" + filterBadValue,
            describeInstanceStatus( ec2, instanceId, filterName, filterBadValue, 0 ) );
      }

      // test public ip lookup

      print( "Testing instance describe" );
      final DescribeInstancesResult instancesResult =
          ec2.describeInstances( new DescribeInstancesRequest().withInstanceIds( instanceId ) );
      final Instance instance = instancesResult.getReservations( ).get( 0 ).getInstances( ).get( 0 );
      final String host = instance.getPublicDnsName( );
      final String ip = instance.getPublicIpAddress( );
      print( "Described instance with host [" + host + "] and ip [" + ip + "]" );

      if ( !Strings.isNullOrEmpty( host) && !host.equals( ip ) ) {
        final Set<String> dnsHosts = getDnsHosts( getServicesClient( ) );
        print( "Testing instance hostname lookup using dns hosts " + dnsHosts );
        final String resolvedIp = lookup( host, dnsHosts );
        print( "Resolved ip [" + resolvedIp + "] for host [" + host + "]" );
        Assert.assertEquals( "Instance ip", ip, resolvedIp );
      }

      // test console output

      final GetConsoleOutputResult outputResult =
          ec2.getConsoleOutput( new GetConsoleOutputRequest( ).withInstanceId( instanceId ) );
      Assert.assertNotNull( "Instance console output", outputResult.getDecodedOutput( ) );

      print( "Test complete" );
    } finally {
      // Attempt to clean up anything we created
      Collections.reverse( cleanupTasks );
      for ( final Runnable cleanupTask : cleanupTasks ) {
        try {
          cleanupTask.run( );
        } catch ( Exception e ) {
          e.printStackTrace( );
        }
      }
    }
  }

  private void assertStatusSummary( final InstanceStatusSummary status,
                                    final String description ) {
    Assert.assertTrue( "Missing " + description + " status", status != null );
    Assert.assertTrue(
        "Invalid status value: " + status.getStatus( ),
        Arrays.asList( "ok", "impaired", "initializing", "insufficient-data", "not-applicable" )
            .contains( status.getStatus( ) ) );
    Assert.assertTrue( "Missing status details", status.getDetails( ) != null );
    Assert.assertTrue(
        "Unexpected details count: " + status.getDetails( ).size( ),
        status.getDetails( ).size( ) == 1 );
    Assert.assertTrue(
        "Unexpected details type: " + status.getDetails( ).get( 0 ).getName( ),
        "reachability".equals( status.getDetails( ).get( 0 ).getName( ) ) );
    Assert.assertTrue(
        "Invalid details value: " + status.getDetails( ).get( 0 ).getStatus( ),
        Arrays.asList( "passed", "failed", "initializing", "insufficient-data" )
            .contains( status.getDetails( ).get( 0 ).getStatus( ) ) );
  }

  private boolean describeInstanceStatus( final AmazonEC2 ec2,
                                          final String instanceId, final String filterName,
                                          final String filterValue, final int expectedCount ) {
    final DescribeInstanceStatusResult instanceStatusResult = ec2
        .describeInstanceStatus( new DescribeInstanceStatusRequest( )
            .withInstanceIds( instanceId ).withFilters(
                new Filter( ).withName( filterName ).withValues(
                    filterValue ) ) );
    return instanceStatusResult.getInstanceStatuses( ).size( ) == expectedCount;
  }

  private String waitForInstance( final AmazonEC2 ec2, final long timeout,
                                  final String expectedId, final String state ) throws Exception {
    print( "Waiting for instance state " + state );
    String az = null;
    final long startTime = System.currentTimeMillis( );
    boolean completed = false;
    while ( !completed && ( System.currentTimeMillis( ) - startTime ) < timeout ) {
      final DescribeInstanceStatusResult instanceStatusResult = ec2
          .describeInstanceStatus( new DescribeInstanceStatusRequest( )
              .withInstanceIds( expectedId )
              .withIncludeAllInstances( true )
              .withFilters(
                  new Filter( )
                      .withName( "instance-state-name" )
                      .withValues( state ) ) );
      completed = instanceStatusResult.getInstanceStatuses( ).size( ) == 1;
      if ( completed ) {
        az = instanceStatusResult.getInstanceStatuses( ).get( 0 ).getAvailabilityZone( );
        Assert.assertTrue(
            "Incorrect instance id",
            expectedId.equals( instanceStatusResult.getInstanceStatuses( ).get( 0 ).getInstanceId( ) ) );
        Assert.assertTrue(
            "Incorrect instance state",
            state.equals( instanceStatusResult.getInstanceStatuses( ).get( 0 ).getInstanceState( ).getName( ) ) );
      }
      sleep( 5 );
    }
    Assert.assertTrue( "Instance not reported within the expected timeout", completed );
    print( "Instance reported " + state + " in " + ( System.currentTimeMillis( ) - startTime ) + "ms" );
    return az;
  }

  private List<String> getInstancesIds( final Reservation... reservations ) {
    final List<String> instances = new ArrayList<String>( );
    for ( final Reservation reservation : reservations ) {
      for ( final Instance instance : reservation.getInstances( ) ) {
        instances.add( instance.getInstanceId( ) );
      }
    }
    return instances;
  }
}
