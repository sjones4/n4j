package com.eucalyptus.tests.awssdk;

import static com.eucalyptus.tests.awssdk.N4j.assertThat;
import static com.eucalyptus.tests.awssdk.N4j.eucaUUID;
import static com.eucalyptus.tests.awssdk.N4j.initS3ClientWithNewAccount;
import static com.eucalyptus.tests.awssdk.N4j.print;
import static com.eucalyptus.tests.awssdk.N4j.testInfo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NavigableSet;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.junit.AfterClass;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Before;
import org.junit.Test;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.Bucket;
import com.amazonaws.services.s3.model.ListObjectsRequest;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.PutObjectResult;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.amazonaws.util.BinaryUtils;
import com.amazonaws.util.Md5Utils;

/**
 * Tests for listing objects in a bucket.
 *
 * @author Swathi Gangisetty
 */
public class S3ListObjectsTests {

  private static String bucketName = null;
  private static List<Runnable> cleanupTasks = null;
  private static Random random = new Random();
  private static File fileToPut = new File("test.dat");
  private static long size = 0;
  private static String md5 = null;
  private static String ownerID = null;
  private static AmazonS3 s3 = null;
  private static String account = null;
  private static String VALID_CHARS = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
  private static int DEFAULT_MAX_KEYS = 1000;

  @BeforeClass
  public static void init() throws Exception {
    print("### PRE SUITE SETUP - " + S3ListObjectsTests.class.getSimpleName());
    try {
      account = S3ListObjectsTests.class.getSimpleName().toLowerCase();
      s3 = initS3ClientWithNewAccount(account, "admin");
    } catch (Exception e) {
      try {
        teardown();
      } catch (Exception ignored ) {
      }
      throw e;
    }
    ownerID = s3.getS3AccountOwner().getId();
    md5 = BinaryUtils.toHex(Md5Utils.computeMD5Hash(new FileInputStream(fileToPut)));
    size = fileToPut.length();
  }

  @AfterClass
  public static void teardown() {
    print("### POST SUITE CLEANUP - " + S3ListObjectsTests.class.getSimpleName());
    N4j.deleteAccount(account);
    s3 = null;
  }

  @Before
  public void setup() throws Exception {
    bucketName = eucaUUID();
    cleanupTasks = new ArrayList<>( );
    Bucket bucket = S3Utils.createBucket(s3, account, bucketName, S3Utils.BUCKET_CREATION_RETRIES);
    cleanupTasks.add( ( ) -> {
      print(account + ": Deleting bucket " + bucketName);
      try {
        s3.deleteBucket(bucketName);
      } catch (AmazonServiceException ase) {
        printException(ase);
        assertThat(false, "Failed to delete bucket " + bucketName);
      }
    } );

    assertNotNull( "Invalid reference to bucket", bucket );
    assertEquals( "Mismatch in bucket names. Expected bucket name to be " + bucketName + ", but got " + bucket.getName( ), bucketName, bucket.getName( ) );
  }

  @After
  public void cleanup() {
    Collections.reverse(cleanupTasks);
    for (final Runnable cleanupTask : cleanupTasks) {
      try {
        cleanupTask.run();
      } catch (Exception e) {
        print("Unable to run clean up task: " + e);
      }
    }
  }

  /**
   * Test for verifying ordering of list objects result
   *
   * This test uploads multiple objects each with a different key. It lists the objects in the bucket and verifies the list for lexicographic ordering
   * of key names
   */
  @Test
  public void multipleKeys() {
    testInfo(this.getClass().getSimpleName() + " - multipleKeys");

    try {
      int keys = 5 + random.nextInt(6);// 5-10 keys
      TreeSet<String> keySet = new TreeSet<>( );
      ObjectListing objects;

      print("Number of keys: " + keys);

      for (int i = 0; i < keys; i++) {
        // Upload an object using the key
        putObject(bucketName, eucaUUID(), fileToPut, keySet);

        // List objects and verify that they are ordered lexicographically
        objects = listObjects(bucketName, null, null, null, 10, false);
        verifyObjectSummaries(keySet, objects.getObjectSummaries());
      }
    } catch (AmazonServiceException ase) {
      printException(ase);
      assertThat(false, "Failed to run multipleKeys");
    }
  }

  /**
   * Test for verifying ordering of list objects using a prefix
   *
   * This test uploads multiple objects each with a different key and a prefix that is shared by a few keys. It lists the objects in the bucket and
   * verifies the list for lexicographic ordering of key names
   */
  @Test
  public void prefix() {
    testInfo(this.getClass().getSimpleName() + " - prefix");

    try {
      int prefixes = 3 + random.nextInt(4); // 3-6 prefixes
      int keys = 2 + random.nextInt(3);// 2-4 keys perfix
      Map<String, TreeSet<String>> prefixKeyMap = new TreeMap<>( );
      ObjectListing objects;

      print("Number of prefixes: " + prefixes);
      print("Number of keys per prefix: " + keys);

      for (int i = 0; i < prefixes; i++) {
        String prefix = VALID_CHARS.charAt(random.nextInt(VALID_CHARS.length())) + eucaUUID(); // Prefix it with any character in the valid chars
        print("Prefix name: " + prefix);
        TreeSet<String> keySet = new TreeSet<>( );

        // Upload objects with different keys that start with the same prefix
        for (int j = 0; j < keys; j++) {
          putObject(bucketName, prefix + eucaUUID(), fileToPut, keySet);
        }

        // List objects and verify that they are ordered lexicographically
        objects = listObjects(bucketName, prefix, null, null, null, false);
        verifyObjectSummaries(keySet, objects.getObjectSummaries());

        // Put the prefix and keys in the map
        prefixKeyMap.put(prefix, keySet);
      }

      // List objects and verify the results
      objects = listObjects(bucketName, null, null, null, null, false);
      assertEquals( "Expected object summary list to be of size " + ( prefixes * keys ) + ", but got a list of size "
          + objects.getObjectSummaries( ).size( ), objects.getObjectSummaries( ).size( ), ( prefixes * keys ) );
      Iterator<S3ObjectSummary> summaryIterator = objects.getObjectSummaries().iterator();

      for (Entry<String, TreeSet<String>> mapEntry : prefixKeyMap.entrySet()) {
        for (String key : mapEntry.getValue()) {
          S3ObjectSummary objectSummary = summaryIterator.next();
          assertEquals( "Expected keys to be ordered lexicographically", objectSummary.getKey( ), key );
          verifyObjectCommonElements(objectSummary);
        }
      }
    } catch (AmazonServiceException ase) {
      printException(ase);
      assertThat(false, "Failed to run prefix");
    }
  }

  /**
   * Test for verifying ordering of list objects using a marker
   */
  @Test
  public void marker() {
    testInfo(this.getClass().getSimpleName() + " - keyMarker");

    try {
      int keys = 3 + random.nextInt(8); // 3-10 keys
      TreeSet<String> keySet = new TreeSet<>( );
      ObjectListing objects;

      print("Number of keys: " + keys);

      for (int i = 0; i < keys; i++) {
        String key = VALID_CHARS.charAt(random.nextInt(VALID_CHARS.length())) + eucaUUID(); // Prefix it with any character in the valid chars

        // Upload an object using the key
        putObject(bucketName, key, fileToPut, keySet);
      }

      // List the objects and verify that they are ordered lexicographically
      objects = listObjects(bucketName, null, null, null, null, false);
      verifyObjectSummaries(keySet, objects.getObjectSummaries());

      // Starting with every key in the ascending order, list the objects using that key as the key marker and verify that the results.
      for (String marker : keySet) {
        // Compute what the sorted objects should look like
        NavigableSet<String> tailSet = keySet.tailSet(marker, false);

        // List the objects and verify that they are ordered lexicographically
        objects = listObjects(bucketName, null, marker, null, null, false);
        verifyObjectSummaries(tailSet, objects.getObjectSummaries());
      }
    } catch (AmazonServiceException ase) {
      printException(ase);
      assertThat(false, "Failed to run keyMarker");
    }
  }

  /**
   * Test for verifying common prefixes using a delimiter
   * 
   * Test fails against Walrus, prefixes in the common prefix list are incorrectly represented. The prefix part is not included, only the portion from
   * prefix to the first occurrence of the delimiter is returned
   */
  @Test
  public void delimiter() {
    testInfo(this.getClass().getSimpleName() + " - delimiter");

    try {
      int prefixes = 3 + random.nextInt(3); // 3-5 prefixes
      int keys = 2 + random.nextInt(3); // 2-4 keys
      String delimiter = "/"; // Pick a random delimiter afterwards
      Map<String, TreeSet<String>> prefixKeyMap = new TreeMap<>( );
      ObjectListing objects;

      print("Number of prefixes: " + prefixes);
      print("Number of keys per prefix: " + keys);

      for (int i = 0; i < prefixes; i++) {
        String prefix = VALID_CHARS.charAt(random.nextInt(VALID_CHARS.length())) + eucaUUID() + delimiter; // Prefix it with a char
        print("Prefix name: " + prefix);
        TreeSet<String> keySet = new TreeSet<>( );

        // Upload objects with different keys that start with the same prefix
        for (int j = 0; j < keys; j++) {
          putObject(bucketName, prefix + eucaUUID(), fileToPut, keySet);
        }

        // List objects and verify that they are ordered lexicographically
        objects = listObjects(bucketName, prefix, null, null, null, false);
        verifyObjectSummaries(keySet, objects.getObjectSummaries());

        // Put the prefix and keys in the map
        prefixKeyMap.put(prefix, keySet);
      }

      objects = listObjects(bucketName, null, null, delimiter, null, false);
      assertEquals( "Expected to not get any object summaries but got a list of size " + objects.getObjectSummaries( ).size( ), 0, objects
          .getObjectSummaries( ).size( ) );
      assertEquals( "Expected common prefixes list to be of size " + prefixKeyMap.size( ) + ", but got a list of size "
          + objects.getCommonPrefixes( ).size( ), objects.getCommonPrefixes( ).size( ), prefixKeyMap.size( ) );
      for (String prefix : prefixKeyMap.keySet()) {
        assertTrue("Expected common prefix list to contain " + prefix, objects.getCommonPrefixes().contains(prefix));
      }
    } catch (AmazonServiceException ase) {
      printException(ase);
      assertThat(false, "Failed to run delimiter");
    }
  }

  /**
   * Test for verifying the common prefixes using a prefix and delimiter
   */
  @Test
  public void delmiterAndPrefix() {
    testInfo(this.getClass().getSimpleName() + " - delmiterAndPrefix");

    try {
      int innerP = 2 + random.nextInt(4); // 2-5 inner prefixes
      int keys = 3 + random.nextInt(3); // 3-5 keys
      String delimiter = "/";
      String outerPrefix = VALID_CHARS.charAt(random.nextInt(VALID_CHARS.length())) + eucaUUID() + delimiter;
      TreeSet<String> allKeys = new TreeSet<>( );
      TreeSet<String> commonPrefixSet = new TreeSet<>( );
      ObjectListing objects;

      print("Number of inner prefixes: " + innerP);
      print("Number of keys per prefix: " + keys);
      print("Outer prefix: " + outerPrefix);

      for (int i = 0; i < innerP; i++) {
        String innerPrefix = outerPrefix + VALID_CHARS.charAt(random.nextInt(VALID_CHARS.length())) + eucaUUID() + delimiter;
        print("Inner prefix: " + innerPrefix);
        TreeSet<String> keySet = new TreeSet<>( );

        // Upload objects with different keys that start with the same prefix
        for (int j = 0; j < keys; j++) {
          putObject(bucketName, innerPrefix + eucaUUID(), fileToPut, keySet);
        }

        // List objects and verify that they are ordered lexicographically
        objects = listObjects(bucketName, innerPrefix, null, null, null, false);
        verifyObjectSummaries(keySet, objects.getObjectSummaries());

        // Store the common prefix and keys
        commonPrefixSet.add(innerPrefix);
        allKeys.addAll(keySet);
      }

      // Upload something of the form outerprefix/key, this should not be counted as the common prefix
      TreeSet<String> keySet = new TreeSet<>( );
      for (int i = 0; i < keys; i++) {
        putObject(bucketName, outerPrefix + eucaUUID(), fileToPut, keySet);
      }
      allKeys.addAll(keySet);

      // List objects and verify the results
      objects = listObjects(bucketName, null, null, null, null, false);
      assertEquals( "Expected object summary list to be of size " + allKeys.size( ) + ", but got a list of size " + objects.getObjectSummaries( ).size( ), objects.getObjectSummaries( ).size( ), allKeys.size( ) );
      Iterator<S3ObjectSummary> summaryIterator = objects.getObjectSummaries().iterator();

      for (String key : allKeys) {
        S3ObjectSummary objectSummary = summaryIterator.next();
        assertEquals( "Object keys are ordered lexicographically. Expected " + key + ", but got " + objectSummary.getKey( ), objectSummary.getKey( ), key );
        verifyObjectCommonElements(objectSummary);
      }

      // List objects with prefix and delimiter and verify again
      objects = listObjects(bucketName, outerPrefix, null, delimiter, null, false);
      assertEquals( "Expected object summaries list to be of size " + keySet.size( ) + "but got a list of size " + objects.getObjectSummaries( ).size( ), objects.getObjectSummaries( ).size( ), keySet.size( ) );
      assertEquals( "Expected common prefixes list to be of size " + commonPrefixSet.size( ) + ", but got a list of size "
          + objects.getCommonPrefixes( ).size( ), objects.getCommonPrefixes( ).size( ), commonPrefixSet.size( ) );

      Iterator<String> prefixIterator = objects.getCommonPrefixes().iterator();
      for (String prefix : commonPrefixSet) {
        String nextCommonPrefix = prefixIterator.next();
        assertEquals( "Common prefixes are not ordered lexicographically. Expected " + prefix + ", but got " + nextCommonPrefix, prefix, nextCommonPrefix );
      }

      // keys with only the outerprefix should be in the summary list
      summaryIterator = objects.getObjectSummaries().iterator();
      for (String key : keySet) {
        S3ObjectSummary objectSummary = summaryIterator.next();
        assertEquals( "Object keys are ordered lexicographically. Expected " + key + ", but got " + objectSummary.getKey( ), objectSummary.getKey( ), key );
        verifyObjectCommonElements(objectSummary);
      }
    } catch (AmazonServiceException ase) {
      printException(ase);
      assertThat(false, "Failed to run delmiterAndPrefixdelmiterAndPrefixdelmiterAndPrefixdelimiter");
    }
  }

  /**
   * Test for verifying paginated listing of objects
   */
  @Test
  public void maxKeys_1() {
    testInfo(this.getClass().getSimpleName() + " - maxKeys_1");

    try {
      int maxKeys = 3 + random.nextInt(6); // Max keys 3-5
      int multiplier = 4 + random.nextInt(2); // Max uploads 12-25
      TreeSet<String> keySet = new TreeSet<>( );
      ObjectListing objects;

      print("Number of keys: " + (maxKeys * multiplier));
      print("Number of max-keys in list objects request: " + maxKeys);

      for (int i = 0; i < (maxKeys * multiplier); i++) {
        // Upload an object using the key
        putObject(bucketName, eucaUUID(), fileToPut, keySet);
      }

      // List objects and verify that they are ordered lexicographically
      objects = listObjects(bucketName, null, null, null, null, false);
      verifyObjectSummaries(keySet, objects.getObjectSummaries());

      Iterator<String> keyIterator = keySet.iterator();
      String nextMarker = null;

      for (int i = 1; i <= multiplier; i++) {
        if (i != multiplier) {
          objects = listObjects(bucketName, null, nextMarker, null, maxKeys, true);
        } else {
          objects = listObjects(bucketName, null, nextMarker, null, maxKeys, false);
        }

        assertEquals( "Expected object summaries list to be of size " + maxKeys + "but got a list of size " + objects.getObjectSummaries( ).size( ), objects.getObjectSummaries( ).size( ), maxKeys );
        Iterator<S3ObjectSummary> summaryIterator = objects.getObjectSummaries().iterator();
        S3ObjectSummary objectSummary = null;

        // Verify the object list
        while (summaryIterator.hasNext()) {
          objectSummary = summaryIterator.next();
          assertEquals( "Expected keys to be ordered lexicographically", objectSummary.getKey( ), keyIterator.next( ) );
          verifyObjectCommonElements(objectSummary);
        }

        if (i != multiplier) {
          nextMarker = objects.getNextMarker();
          assertEquals( "Expected next-marker to be " + objectSummary.getKey( ) + ", but got " + nextMarker, objectSummary.getKey( ), nextMarker );
        } else {
          assertNull( "Expected next-marker to be null, but got " + objects.getNextMarker( ), objects.getNextMarker( ) );
        }
      }
    } catch (AmazonServiceException ase) {
      printException(ase);
      assertThat(false, "Failed to run maxKeys_1");
    }
  }

  /**
   * Test for verifying paginated listing of objects with incrementing key names
   */
  @Test
  public void maxKeys_2() {
    testInfo(this.getClass().getSimpleName() + " - maxKeys_2");

    try {
      int maxKeys = 2 + random.nextInt(3); // Max keys 3-5
      int multiplier = 3 + random.nextInt(4);
      TreeSet<String> keySet = new TreeSet<>( );
      ObjectListing objects;
      String key = "";

      for (int i = 0; i < (maxKeys * multiplier); i++) {
        key += VALID_CHARS.charAt(random.nextInt(VALID_CHARS.length()));
        putObject(bucketName, key, fileToPut, keySet);
      }

      // List objects and verify that they are ordered lexicographically
      objects = listObjects(bucketName, null, null, null, null, false);
      verifyObjectSummaries(keySet, objects.getObjectSummaries());

      Iterator<String> keyIterator = keySet.iterator();
      String nextMarker = null;

      for (int i = 1; i <= multiplier; i++) {
        if (i != multiplier) {
          objects = listObjects(bucketName, null, nextMarker, null, maxKeys, true);
        } else {
          objects = listObjects(bucketName, null, nextMarker, null, maxKeys, false);
        }

        assertEquals( "Expected object summaries list to be of size " + maxKeys + "but got a list of size " + objects.getObjectSummaries( ).size( ), objects.getObjectSummaries( ).size( ), maxKeys );
        Iterator<S3ObjectSummary> summaryIterator = objects.getObjectSummaries().iterator();
        S3ObjectSummary objectSummary = null;

        // Verify the object list
        while (summaryIterator.hasNext()) {
          objectSummary = summaryIterator.next();
          assertEquals( "Expected keys to be ordered lexicographically", objectSummary.getKey( ), keyIterator.next( ) );
          verifyObjectCommonElements(objectSummary);
        }

        if (i != multiplier) {
          nextMarker = objects.getNextMarker();
          assertEquals( "Expected next-marker to be " + objectSummary.getKey( ) + ", but got " + nextMarker, objectSummary.getKey( ), nextMarker );
        } else {
          assertNull( "Expected next-marker to be null, but got " + objects.getNextMarker( ), objects.getNextMarker( ) );
        }
      }
    } catch (AmazonServiceException ase) {
      printException(ase);
      assertThat(false, "Failed to run maxKeys_2");
    }
  }

  /**
   * Test for verifying paginated listing of common prefixes
   */
  @Test
  public void delimiterPrefixAndMaxKeys() {
    testInfo(this.getClass().getSimpleName() + " - delimiterPrefixAndMaxKeys");

    try {
      int maxKeys = 3 + random.nextInt(3); // Max keys 3-5
      int multiplier = 3 + random.nextInt(4);
      int prefixes = maxKeys * multiplier; // Max prefixes 9-18
      int keys = 2 + random.nextInt(3); // 2-4 keys
      String delimiter = "/"; // Pick a random delimiter afterwards
      TreeSet<String> prefixSet = new TreeSet<>( );
      ObjectListing objects;

      print("Number of prefixes: " + prefixes);
      print("Number of keys per prefix: " + keys);
      print("Number of max-keys in list objects request: " + maxKeys);

      for (int i = 0; i < prefixes; i++) {
        String prefix = VALID_CHARS.charAt(random.nextInt(VALID_CHARS.length())) + eucaUUID() + delimiter; // Prefix it with a char
        print("Prefix name: " + prefix);
        TreeSet<String> keySet = new TreeSet<>( );

        // Upload objects with different keys that start with the same prefix
        for (int j = 0; j < keys; j++) {
          putObject(bucketName, prefix + eucaUUID(), fileToPut, keySet);
        }

        // List objects and verify that they are ordered lexicographically
        objects = listObjects(bucketName, prefix, null, null, null, false);
        verifyObjectSummaries(keySet, objects.getObjectSummaries());

        prefixSet.add(prefix);
      }

      Iterator<String> prefixIterator = prefixSet.iterator();

      String nextMarker = null;

      for (int i = 1; i <= multiplier; i++) {
        if (i != multiplier) {
          objects = listObjects(bucketName, null, nextMarker, delimiter, maxKeys, true);
        } else {
          objects = listObjects(bucketName, null, nextMarker, delimiter, maxKeys, false);
        }

        assertEquals( "Expected to not get any object summaries but got a list of size " + objects.getObjectSummaries( ).size( ), 0, objects
            .getObjectSummaries( ).size( ) );
        assertEquals( "Expected common prefixes list to be of size " + maxKeys + ", but got a list of size " + objects.getCommonPrefixes( ).size( ), objects.getCommonPrefixes( ).size( ), maxKeys );

        Iterator<String> commonPrefixIterator = objects.getCommonPrefixes().iterator();
        String commonPrefix = null;

        while (commonPrefixIterator.hasNext()) {
          String expectedPrefix = prefixIterator.next();
          commonPrefix = commonPrefixIterator.next();
          assertEquals( "Expected common prefix " + expectedPrefix + ", but got " + commonPrefix, expectedPrefix, commonPrefix );
        }

        if (i != multiplier) {
          nextMarker = objects.getNextMarker();
          assertEquals( "Expected next-marker to be " + commonPrefix + ", but got " + nextMarker, commonPrefix, nextMarker );
        } else {
          assertNull( "Expected next-marker to be null, but got " + objects.getNextMarker( ), objects.getNextMarker( ) );
        }
      }
    } catch (AmazonServiceException ase) {
      printException(ase);
      assertThat(false, "Failed to run delimiterPrefixAndMaxKeys");
    }
  }

  private void putObject(final String bucketName, final String key, File fileToPut, Set<String> keySet) {
    print(account + ": Putting object " + key + " in bucket " + bucketName);
    ObjectMetadata metadata = new ObjectMetadata();
    metadata.addUserMetadata("foo", "bar");
    final PutObjectResult putResult = s3.putObject(new PutObjectRequest(bucketName, key, fileToPut).withMetadata(metadata));
    cleanupTasks.add( ( ) -> {
      print(account + ": Deleting object " + key);
      s3.deleteObject(bucketName, key);
    } );
    assertNotNull( "Invalid put object result", putResult );
    assertNull( "Expected version ID to be null, but got " + putResult.getVersionId( ), putResult.getVersionId( ) );
    assertTrue("Mimatch in md5sums between object and PUT result. Expected " + md5 + ", but got " + putResult.getETag(), putResult.getETag() != null
        && putResult.getETag().equals(md5));
    keySet.add(key);
  }

  private ObjectListing listObjects(String bucketName, String prefix, String marker, String delimiter, Integer maxKeys, boolean isTruncated) {

    StringBuilder sb = new StringBuilder(account + ": List objects using bucket=" + bucketName);

    ListObjectsRequest request = new ListObjectsRequest();
    request.setBucketName(bucketName);

    if (prefix != null) {
      request.setPrefix(prefix);
      sb.append(", prefix=").append(prefix);
    }
    if (marker != null) {
      request.setMarker(marker);
      sb.append(", key marker=").append(marker);
    }
    if (delimiter != null) {
      request.setDelimiter(delimiter);
      sb.append(", delimiter=").append(delimiter);
    }
    if (maxKeys != null) {
      request.setMaxKeys(maxKeys);
      sb.append(", max results=").append(maxKeys);
    }

    print(sb.toString());
    ObjectListing objectList = s3.listObjects(request);

    assertNotNull( "Invalid object list", objectList );
    assertEquals( "Expected object listing bucket name to be " + bucketName + ", but got " + objectList.getBucketName( ), objectList.getBucketName( ), bucketName );
    assertTrue("Expected delimiter to be " + delimiter + ", but got " + objectList.getDelimiter(),
        Objects.equals(objectList.getDelimiter(), delimiter));
    assertNotNull( "Expected common prefixes to be empty or populated, but got " + objectList.getCommonPrefixes( ), objectList.getCommonPrefixes( ) );
    assertTrue("Expected marker to be " + marker + ", but got " + objectList.getMarker(), Objects.equals(objectList.getMarker(), marker));
    assertEquals( "Expected max-keys to be " + ( maxKeys != null ? maxKeys : DEFAULT_MAX_KEYS ) + ", but got " + objectList.getMaxKeys( ), objectList.getMaxKeys( ), ( maxKeys != null ? maxKeys : DEFAULT_MAX_KEYS ) );
    assertTrue("Expected prefix to be " + prefix + ", but got " + objectList.getPrefix(), Objects.equals(objectList.getPrefix(), prefix));
    assertNotNull( "Invalid object summary list", objectList.getObjectSummaries( ) );
    assertEquals( "Expected is truncated to be " + isTruncated + ", but got " + objectList.isTruncated( ), objectList.isTruncated( ), isTruncated );
    if (objectList.isTruncated()) {
      assertNotNull( "Invalid next-marker, expected it to contain next key but got null", objectList.getNextMarker( ) );
    } else {
      assertNull( "Invalid next-marker, expected it to be null but got " + objectList.getNextMarker( ), objectList.getNextMarker( ) );
    }

    return objectList;
  }

  private void verifyObjectSummaries(Set<String> keySet, List<S3ObjectSummary> objectSummaries) {
    assertEquals( "Expected object summary list to be of size " + keySet.size( ) + ", but got a list of size " + objectSummaries.size( ), keySet.size( ), objectSummaries.size( ) );

    Iterator<String> keyIterator = keySet.iterator();
    Iterator<S3ObjectSummary> summaryIterator = objectSummaries.iterator();
    S3ObjectSummary objectSummary;

    // Verify the object summaries against the key set
    while (summaryIterator.hasNext()) {
      objectSummary = summaryIterator.next();
      assertEquals( "Expected keys to be ordered lexicographically", objectSummary.getKey( ), keyIterator.next( ) );
      verifyObjectCommonElements(objectSummary);
    }
  }

  private void verifyObjectCommonElements(S3ObjectSummary objectSummary) {
    assertEquals( "Expected bucket name to be " + bucketName + ", but got " + objectSummary.getBucketName( ), objectSummary.getBucketName( ), bucketName );
    assertEquals( "Expected etag to be " + md5 + ", but got " + objectSummary.getETag( ), objectSummary.getETag( ), md5 );
    assertNotNull( "Invalid last modified field", objectSummary.getLastModified( ) );
    assertEquals( "Expected owner ID to be " + ownerID + ", but got " + objectSummary.getOwner( ).getId( ), objectSummary.getOwner( ).getId( ), ownerID );
    assertEquals( "Expected size to be " + size + ", but got " + objectSummary.getSize( ), objectSummary.getSize( ), size );
  }

  private void printException(AmazonServiceException ase) {
    ase.printStackTrace();
    print("Caught Exception: " + ase.getMessage());
    print("HTTP Status Code: " + ase.getStatusCode());
    print("Amazon Error Code: " + ase.getErrorCode());
    print("Request ID: " + ase.getRequestId());
  }
}
