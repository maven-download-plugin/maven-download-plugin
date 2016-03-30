# Download Plugin for Maven
This is a plugin meant to help maven user to download different files on different protocol in part of maven build.

__WARNING about artifactId__: Until version 1.1, the plugin artifactId used to be _maven-download-plugin_, however Maven conventions makes that this name is not allowed for a plugin which is not part of the Apache Maven project. So starting from version 1.2-SNAPSHOT, the plugin artifactId is _download-maven-plugin_. The following documentation will get updated when releasing download-maven-plugin:1.2.

## Enable 

This Maven plugin should be available on Maven Central. But in case you can't find it on Central for some reason, here is the repository to add to your pom:

```xml
<pluginRepository>
	<id>sonatype-public-repository</id>
	<url>https://oss.sonatype.org/content/groups/public</url>
	<snapshots>
		<enabled>true</enabled>
	</snapshots>
	<releases>
		<enabled>true</enabled>
	</releases>
</pluginRepository>
````

You can use some alternative repositories. See https://docs.sonatype.org/display/Repository/Sonatype+OSS+Maven+Repository+Usage+Guide#SonatypeOSSMavenRepositoryUsageGuide-4.MavenRepositories for details.

## Basic Usage

### "Artifact" goal
Meant to be used from anywhere on the system to download an artifact at a specific location.  Does not need a pom file to be run and can be used directly from the command line.
Can be an alternative to [maven-dependency-plugin:get](http://maven.apache.org/plugins/maven-dependency-plugin/get-mojo.html) or [maven-depdendency-plugin:unpack](http://maven.apache.org/plugins/maven-dependency-plugin/unpack-mojo.html maven-dependency-plugin:unpack) mojoes.


```
mvn com.googlecode.maven-download-plugin:download-maven-plugin:1.3.0:artifact -DgroupId=com.googlecode -DartifactId=maven-download-plugin -Dversion=0.1 -DoutputDirectory=temp
```

### "WGet" goal
This is meant to provide the necessary tooling for downloading anything in your Maven build without having to use Ant scripts.
It provides caching and signature verification.
```xml
<plugin>
	<groupId>com.googlecode.maven-download-plugin</groupId>
	<artifactId>download-maven-plugin</artifactId>
	<version>1.3.0</version>
	<executions>
		<execution>
			<id>install-jbpm</id>
			<phase>pre-integration-test</phase>
			<goals>
				<goal>wget</goal>
			</goals>
			<configuration>
				<url>http://downloads.sourceforge.net/project/jbpm/jBPM%203/jbpm-3.1.4/jbpm-3.1.4.zip</url>
				<unpack>true</unpack>
				<outputDirectory>${project.build.directory}/jbpm-3.1.4</outputDirectory>
				<md5>df65b5642f33676313ebe4d5b69a3fff</md5>
			</configuration>
		</execution>
	</executions>
</plugin>
```

## Help

### Maven help

To get basic plugin help, type in the command : 
```
mvn help:describe -Dplugin=com.googlecode.maven-download-plugin:maven-download-plugin
```

To get a more detailed help, type command : 
```
mvn help:describe -Dplugin=com.googlecode.maven-download-plugin:maven-download-plugin -Ddetail
```
### Generated documentation

See also generated documentation pages [for 1.3.0](http://maven-download-plugin.github.com/maven-download-plugin/docsite/1.3.0/) and [for snapshot](http://maven-download-plugin.github.com/maven-download-plugin/docsite/snapshot/).

### Mailing-list

See https://groups.google.com/forum/?fromgroups#!forum/maven-download-plugin

### Issue Tracker and wikis...

Are maintained at GitHub (links above).

## Other links

Project metrics on Ohloh: [![Ohloh](https://www.ohloh.net/p/maven-download-plugin/widgets/project_partner_badge.gif)](https://www.ohloh.net/p/maven-download-plugin)

Former project page at Google Code: http://code.google.com/p/maven-download-plugin/
