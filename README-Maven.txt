
1. WORKING WITH MAVEN
=====================

Requirements:

* Java 5.0 and above
* Maven 2.0.8 and above


1.1. Quickstart: Typical lifecycle phases
-----------------------------------------

Maven will create a target/ directory under the root for the creation of
output at every stage.

* mvn clean: Cleans out any old builds and binaries

* mvn compile: Compiles java source code.

* mvn test: Runs the TestNG unit test suite on the compiled code.  Will also compile the tests. See the testing section
  below for more information on running different test groups.  The default test groups run are the "unit" and "functional".

* mvn package: Packages the module as a JAR file, the resulting JAR file will be in target/

* mvn package -Dmaven.test.skip=true: Creates a JAR file without running tests.

* mvn package -P Docs: Packages the module as a JAR file, and builds the javadocs and user documentation from docbook sources.

* mvn install: will install the artifacts in your local repo for use by other projects (such as JBoss Cache POJO edition
  which depends on JBoss Cache Core).  Will also use Maven's assembly plugin to build ZIP files for download
  (in target/distribution)

* mvn deploy: will build and deploy the project to the JBoss snapshots repository.  Note that you should have your WebDAV
  username and password set up.  (Deploys snapshots to http://snapshots.jboss.org/maven2/org/jboss/cache/).  If you have
  a non-SNAPSHOT version number in your pom.xml, it will be deployed to the live releases repository (see below)

* mvn clean site -Ptest-functional,codeCoverage: will run all tests in the test-functional profile and generate code
  coverage reports using EMMA.


1.2. Setting up your WebDAV username and password to deploy project snapshots
-----------------------------------------------------------------------------

You will also have to configure maven to use your username and password to access this repository. For this, you will
have to modify the servers section of maven settings file ($MAVEN_HOME/conf/settings.xml, or ~/.m2/settings.xml).
Something similar to the following should be added:

<settings>

...

  <servers>
...
    <server>
      <id>snapshots.jboss.org</id>
      <username>webdav-user</username>
      <password>webdav-pass</password>
    </server>
...

  </servers>

...

</settings>

1.3. Deploying a release to a live repository
---------------------------------------------

Very simple.  Make sure you have the version number in your pom.xml set to a non-SNAPSHOT version.  Maven will pick up
on this and deploy to your release repository rather than the snapshot repository.

JBoss release repository cannot be accessed via WebDAV, as the snapshot repository can.  So what you need to do is:

1) Check out the release repository from Subversion (svn co https://svn.jboss.org/repos/repository.jboss.org/maven2)
2) Add a property in ~/.m2/settings.xml to point to this 'local' copy of the repo.  (See maven settings below)
3) Update your project's pom.xml to reflect a non-SNAPSHOT version number
4) Deploy your project (mvn clean deploy)
5) Check in your 'local' copy of the repo checked out in (1), adding any new directories/files created by (4).

1.4. Maven settings.xml
-----------------------

Working with the JBoss Cache source tree, I have configured my ~/.m2/settings.xml to look like:

<settings>

 <servers>
    <server>
      <id>snapshots.jboss.org</id>
      <username>MY_JBOSS_ORG_USERNAME</username>
      <password>WONT_TELL_YOU</password>
    </server>
  </servers>


  <profiles>
    <profile>
      <id>jboss</id>
      <properties>
        <maven.repository.root>/Users/manik/Code/maven2</maven.repository.root>
      </properties>
    </profile>
  </profiles>

  <activeProfiles>
    <activeProfile>jboss</activeProfile>
  </activeProfiles>

</settings>

2. TESTING
==========

Tests are written against the TestNG testing framework. Each test should belong to one or more group. The group acts as
a filter, and is used to select which tests are ran as part of the maven test lifecycle. There are 3 groups that are
currently in use, but there is not formal, you can make up any group name if you like.

2.1. Current Groups
-------------------
* unit - Unit tests using stubs to isolate and test each major class in JBoss Cache.  This is the default group run if no test group is specified.
* functional - Tests which test the general functionality of JBoss Cache
* jgroups - Tests which need to send data on a JGroups Channel
* transaction - Tests which use a transaction manager
* profiling - Tests used for manual profiling, not meant for automated test runs
* manual - Other tests that are run manually
* integration - Integration tests emulating usage patterns for specific products such as JBoss AS Clustering and Hibernate clustering

It should be noted that every test (except those not intended to be run by Hudson) should at least be in the functional
group, since this is the default test group that is executed by maven, and the one that is required to prepare a release.

2.2. Executing the default test run
-----------------------------------
The default run executes all tests in the functional group. To just run the tests with txt and xml output the command is:

   $ mvn test

Alternatively, you can execute the tests AND generate a report with:

   $ mvn surefire-report:report

If you already have ran a test cycle, and you want to generate a report off the current reports, then you use the
report-only goal, ike so:

   $ mvn surefire-report:report-only

2.3. Executing different groups
-------------------------------
A group can be executed (using the default configuration) by simply using the appropriate profile, like so:

   $ mvn -P test-jgroups test

2.4. Executing a single test
----------------------------
A single test can be executed using the test property. The value is the short name (not the fully qualified package name)
of the test.

   $ mvn -P test-XXX -Dtest=FqnTest test

Alternatively, if there is more than one test with a given classname in your test suite, you could provide the path to
the test.

   $ mvn -P test-XXX -Dtest=org/jboss/cache/multiplexer/SyncReplTxTest test

2.5. Executing all tests in a given package
--------------------------------------------
This can be achieved by passing in the package name with a wildcard to the test parameter.

   $ mvn -P test-XXX -Dtest=org/jboss/cache/multiplexer/* test

2.6. Skipping the test run
--------------------------
It is sometimes desirable to install the jboss cache package in your local repository without performing a full test run.
To do this, simply use the maven.test.skip.exec property:

   $ mvn -Dmaven.test.skip.exec=true install

Again, this is just a shortcut for local use. It SHOULD NEVER BE USED when releasing. Also, make sure "exec" is included
in the property, if not the tests will not be built, which will prevent a test jar being produced (POJO Cache needs the
Core Cache test jar).

2.7. Permutations
-----------------
We use the term permutation to describe a group execution against a particular config. This allows us to test a variety
of environments and configurations without rewriting the same basic test over and over again. For example, the jgroups-tcp
permutation executes the jgroups group using the TCP config. Each permutation requires a maven profile which defines the
various options, environmental variables, etc. The command to run the jgroups-tcp permutatin is:

   $ mvn -Pjgroups-tcp surefire-report:report

Each permutation uses its own report directory, and its own html output file name. This allows you to execute multiple
permutations without wiping the results from the previous run. Note that due to the way maven  operates, only one
permutation can be executed per mvn command. So automating multiple runs requires shell scripting, or some other execution
framework to make multiple called to maven.

2.8. Running permutations manually or in an IDE
-----------------------------------------------

Sometimes you want to run a test using settings other than the defaults (such as UDP for "jgroups" group tests or the
DummyTransactionManager for "transaction" group tests).  This can be achieved by referring to the Maven POM file
to figure out which system properties are passed in to the test when doing something different.

E.g., to run a "jgroups" group test in your IDE using TCP instead of the default UDP, set the following:

   -Djgroups.stack=tcp

Or, to use JBoss JTA (Arjuna TM) instead of the DummyTransactionManager in a "transaction" group test, set:

   -Dorg.jboss.cache.test.tm=jboss-jta

Please refer to the POM file for more properties and permutations.

2.9. Integration with CruiseControl / Hudson
--------------------------------------------

CruiseControl should do the following:

* Run "mvn clean site" - will clean and run tests, and then prepare reports.  In addition to unit tests, this project is
  set up to run FindBugs, PMD, jxr, and a bunch of other code analysis tools and provide a report in
  target/site/project-reports.html - which should be linked from the CruiseControl summary page.

