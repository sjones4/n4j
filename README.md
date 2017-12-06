# n4j
n4j is a collection of tests for Eucalyptus clouds. 

The framework is written in Java and primarily uses the Amazon SDK for Java. JUnit is used for tests and test suites and these can be written in Java or Groovy.

Prerequisites
------
1. Java (JDK8), e.g. yum install java-1.8.0-openjdk-devel

2. A Eucalyptus cloud to test

Installation
------
Clone the git repository:

```
  git clone https://github.com/eucalyptus/n4j.git
  cd n4j
```

Running Tests
------
Gradle is used to compile/run tests:

```
  ./gradlew -Dclcip=your_cloudcontroller_ip \
            -Duser=user_to_log_into_host_as \
            -Dpassword=host_user_password \
            test
```

The above command will fetch the required dependencies and an image to use to test the cloud (~500MB required)

To run a particular test or test suite use:

```
  ./gradlew -Dclcip=your_cloudcontroller_ip \
            -Duser=user_to_log_into_host_as \
            -Dpassword=host_user_password \
            -Dtest.single=suite_or_test_name \
            test
```

Example of a suite is Ec2Suite, an example test is TestEC2DescribeInstanceStatus.

Test results are output to the console, an HTML report is also generated, e.g.:

```
  firefox ./build/reports/tests/test/index.html
```

Development
------
Gradle can be used to generate an IntelliJ IDEA project:

```
  ./gradlew idea
```

Tests can be run in the IDE by specifying the necessary VM options.

How does it work?
------
The most basic element for starting any test is getting an authorized users credentials and making some connections to service endpoints such as ec3, s3, asutoscaling, etc. In order to achieve this for a private cloud such as Eucalyptus, we start by connecting to the Cloud Controller. From there we look to see if the test runner has already created cloud admin creds for itself. If it has, we pull down the ini file and parse it for the info we need. If we do not find test runner created creds, we generate a new key and write out the ini file and we pull that down to consume. Now that the setup can get admin creds anything is possible. It is recommended to create a new account and user in your test(s) and to use that user to perform the tests.
