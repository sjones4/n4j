package com.eucalyptus.tests.awssdk;

/**
 * Created by tony on 7/28/14.
 */
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.BucketLifecycleConfiguration;
import com.amazonaws.services.s3.model.BucketLifecycleConfiguration.Rule;
import com.amazonaws.services.s3.model.Grant;

import static com.eucalyptus.tests.awssdk.N4j.initS3Client;
import static com.eucalyptus.tests.awssdk.N4j.s3;


public class GetBucketInfo {

    public static void main(String[] args) throws Exception {
        try {
            initS3Client();
        } catch (Exception e) {
            throw e;
        }
        printBucketInfo(s3,"my-named-bucket");
        printBucketInfo(s3, "bucket-test-bucket2-80ibw47rp1ani");
    }

    public static void printBucketInfo(AmazonS3 s3Client, String bucketName) {
        System.out.println("\n\nBucket:"+bucketName);
        System.out.println("Version-Status:" + s3Client.getBucketVersioningConfiguration(bucketName).getStatus());
        System.out.println("---ACL---");
        for (Grant grant: s3Client.getBucketAcl(bucketName).getGrantsAsList()) {
            System.out.println("Grant: " + grant.getGrantee() + ":" + grant.getPermission());
        }
        System.out.println("---LIFECYCLE CONFIGURATION---");
        BucketLifecycleConfiguration bucketLifecycleConfiguration = s3Client.getBucketLifecycleConfiguration(bucketName);
        if (bucketLifecycleConfiguration.getRules() != null) {
            for (Rule rule: bucketLifecycleConfiguration.getRules()) {
                System.out.println("Rule: ");
                System.out.println(" id = " + rule.getId());
                System.out.println(" prefix = " + rule.getPrefix());
                System.out.println(" status = " + rule.getStatus());
                System.out.println(" expirationInDays = " + rule.getExpirationInDays());
                System.out.println(" expirationDate = " + rule.getExpirationDate());
                for (BucketLifecycleConfiguration.Transition transition: rule.getTransitions()) {
                    System.out.println(" transition.days = " + transition.getDays());
                    System.out.println(" transition.date = " + transition.getDate());
                    System.out.println(" transition.storageClass = " + transition.getStorageClassAsString());
                }
            }
        }
    }
}