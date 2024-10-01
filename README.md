[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.googlecode.maven-download-plugin/download-maven-plugin/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.googlecode.maven-download-plugin/download-maven-plugin)

# Download Plugin for Maven
This is a plugin meant to help maven user to download different files on different protocol in part of maven build.
The plugin caches downloaded files in maven cache directory, which saves network trafic and speedup build.

## Project Status

Functional but not under active development. We accept pull requests, and generally get them merged within a week or 2 depending on the complexity.

## Basic Usage

### "Artifact" goal
Meant to be used from anywhere on the system to download an artifact at a specific location.  Does not need a pom file to be run and can be used directly from the command line.
Can be an alternative to [maven-dependency-plugin:get](http://maven.apache.org/plugins/maven-dependency-plugin/get-mojo.html) or [maven-dependency-plugin:unpack](http://maven.apache.org/plugins/maven-dependency-plugin/unpack-mojo.html) mojoes.


```
mvn com.googlecode.maven-download-plugin:download-maven-plugin:<LATEST_VERSION>:artifact -DgroupId=com.googlecode -DartifactId=maven-download-plugin -Dversion=0.1 -DoutputDirectory=temp
```

### "WGet" goal
This is meant to provide the necessary tooling for downloading anything in your Maven build without having to use Ant scripts.
It provides caching and checksum verification.
```xml
<plugin>
	<groupId>com.googlecode.maven-download-plugin</groupId>
	<artifactId>download-maven-plugin</artifactId>
	<version>LATEST_VERSION</version>
	<executions>
		<execution>
			<id>install-jbpm</id>
			<phase>pre-integration-test</phase>
			<goals>
				<goal>wget</goal>
			</goals>
		</execution>
	</executions>
	<configuration>
		<url>http://downloads.sourceforge.net/project/jbpm/jBPM%203/jbpm-3.1.4/jbpm-3.1.4.zip</url>
		<unpack>true</unpack>
		<outputDirectory>${project.build.directory}/jbpm-3.1.4</outputDirectory>
		<md5>df65b5642f33676313ebe4d5b69a3fff</md5>
	</configuration>
</plugin>
```

You can also run it without a pom.xml i.e. 

`mvn -Ddownload.url=https://example.com -Ddownload.outputDirectory=. -Ddownload.outputFileName=example.html com.googlecode.maven-download-plugin:download-maven-plugin:<LATEST_VERSION>:wget`

## Requirements

Starting from version 1.6.9, the plugin requires Maven version 3.2.5 or above.

## Known issues and workarounds

### IO Error: No such archiver

Happens when the plugin is instructed to unarchive file but the file has unsupported extension

**Solution**: Specify `outputFilename` parameter with proper file extension

## Help

### Maven help

To get basic plugin help, type in the command : 
```
mvn com.googlecode.maven-download-plugin:download-maven-plugin:help
```

To get a more detailed help, type command : 
```
mvn com.googlecode.maven-download-plugin:download-maven-plugin:help -Ddetail
```

### Mailing-list

See https://groups.google.com/forum/?fromgroups#!forum/maven-download-plugin

### Issue Tracker and wikis...

Are maintained at GitHub (links above).

### Contribute

This project support GitHub PR, but enforce some rules for decent tracking: 1 Change Request == 1 PR == 1 commit, if a change can be made by iterations, then use a specific PR for each iteration.
Ideally, every bugfix should be supplied with a unit or integration test. 

## Other links

Former project page at Google Code: http://code.google.com/p/maven-download-plugin/
