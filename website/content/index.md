+++
type = "index"
toc = true
+++

The Eclipse Common Build Infrastructure (CBI) is an initiative combining technologies and practices for building Eclipse Software.

## What is CBI

The core of CBI today is Maven with the Tycho plugins. The Tycho plugins teach Maven how to build (and consume) Eclipse plugins and OSGi bundles. This enables building Eclipse projects with "maven clean install" just as one would build other Maven projects.

Common services such as the Jar signing facility, MacOS signing facility, and Windows signing facility are also included with CBI. Other tools and services may be included in the future as the need arises.

Over time mature templates and common pom.xml files will be provided that set common values finely honed with experience.

One might go so far as to include Git, Hudson, the build slaves, and Nexus (aka. the artifact repository & server side of Maven) as part of CBI since they are also common and crucial to builds.

Gerrit, Bugzilla, and the Downloads site are closely related. Some might consider them part of CBI as well.


### Who is using it?

There's a [list of projects](http://wiki.eclipse.org/CBI/Projects) building with CBI available.

The Eclipse Foundation would like to have more than 50% of projects building with CBI by end of 2013.

## Initiative Goals

### Primary
* Make it really easy to contribute Eclipse projects
* Make it really easy to copy & modify source
* Make it really easy to build
* Make it really easy to test
* Make it really easy to post a change for review
* Make it really easy to sign software

### Secondary
* Get all Eclipse projects building their software on Eclipse Foundation hardware.
* Enable the [Long Term Support Program](http://wiki.eclipse.org/EclipseLTS).
* Make it easy for people to build custom Eclipse distributions.


There is a strong link between CBI and the [Long Term Support Program](http://wiki.eclipse.org/EclipseLTS) which enables a marketplace of companies providing maintenance and support for Eclipse technologies for durations far beyond typical community support.

Please note: CBI features will be available to community.

It is our hope that this project develops an offering that is compelling so that many projects will move to use it.

## Resources
* Mailing list [cbi-dev](https://dev.eclipse.org/mailman/listinfo/cbi-dev)

### Bugs

[List of All Bugs](https://bugs.eclipse.org/bugs/buglist.cgi?action=wrap&product=CBI&list_id=38248)

## Tutorials, News, and other resources
* [Tycho tutorial by Lars Vogel](http://www.vogella.com/articles/EclipseTycho/article.html)
* [Video discussing JBoss tools use of Tycho](http://www.fosslc.org/drupal/content/tycho-good-bad-and-ugly)
* [Building Eclipse SDK locally with Maven](http://www.vogella.com/blog/2012/10/08/building-eclipse-sdk-locally-with-maven/)
* [Sonar at Eclipse.org](http://mickaelistria.wordpress.com/2012/10/08/sonar-at-eclipse-org/)
* [Tycho and CBI Adoption: Feedback from the trenches](http://youtu.be/KJUfLvXiTSw)
* [Eclipse Scout builds with CBI](http://www.bsiag.com/scout/?p=678)

## Eclipse platform CBI build
* [Eclipse Platform Build based on CBI](http://wiki.eclipse.org/Platform-releng/Platform_Build) see the [Platform Build Roadmap](http://wiki.eclipse.org/CBI/Eclipse)

## Preferred Build Technologies

### Hudson

* The Eclipse [Hudson instances](http://hudson.eclipse.org); and

### Maven

Maven 3.0 drives the builds. Projects are expected to provide standard Maven 3.0 POM files for their builds. The builds should be built in such a way that they can be run on the local workstation, or on the Eclipse build server. Note that builds can only be signed on the Eclipse build server.

### Tycho

Tycho is focused on a Maven-centric, manifest-first approach to building Eclipse plug-ins, features, update sites, RCP applications and OSGi bundles.

Helpful links:

* [Tycho project](http://wiki.eclipse.org/Tycho) information, including [demo projects](http://wiki.eclipse.org/Tycho/Demo)
* [Building Woolsey with Maven and Tycho](http://waynebeaton.wordpress.com/2010/09/23/building-woolsey-with-maven-and-tycho/)
* [Reference Card](http://wiki.eclipse.org/Tycho/Reference_Card)
* [Packaging Types](http://wiki.eclipse.org/Tycho/Packaging_Types)

### Nexus

http://wiki.eclipse.org/Services/Nexus

### Signing tool

* [Maven plugins for signing artifacts](http://git.eclipse.org/c/cbi/org.eclipse.cbi.git/)
* [On demand signing tool](http://wiki.eclipse.org/IT_Infrastructure_Doc#Sign_my_plugins.2FZIP_files.3F)

## CBI License bundle

We offer a P2 repository containing the org.eclipse.license bundle which is located at `http://download.eclipse.org/cbi/updates/license/`

This URL is a composite P2 repo containing the license bundle.

If you are using Tycho you can add the p2 repo to the <repositories> section of your pom.xml file. Something similar to this:

```xml
<repository>
  <id>license-feature</id>
  <url>http://download.eclipse.org/cbi/updates/license/</url>
  <layout>p2</layout>
</repository>
```

In any particular feature which you need the license you can use the usual feature.xml section:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<feature
  id="org.eclipse.help"
  label="%featureName"
  version="2.0.0.qualifier"
  provider-name="%providerName"
  plugin="org.eclipse.help.base"
  license-feature="org.eclipse.license"
  license-feature-version="1.0.0.qualifier">
....
```

## Related Topics and Links
* [Long Term Support](http://wiki.eclipse.org/EclipseLTS)
* [List of Build Technologies](http://wiki.eclipse.org/Build_Technologies)

## FAQ

* See your [Frequently Asked Question list](http://wiki.eclipse.org/CBI/FAQ)
