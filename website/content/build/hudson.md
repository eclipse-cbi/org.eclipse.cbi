+++
title = "Hudson for Eclipse Projects"
date = ""
lastmod = "" 
tags = [
  "Build",
  "Hudson",
  "Continuous Integration",
]
toc = true
+++

Hudson is a continuous integration (CI) tool.  The [Hudson project](http://eclipse.org/hudson/ Hudson project) is hosted at Eclipse.org, and is in use on Eclipse servers for Eclipse projects as part of the [Common Build Infrastrure ]({{< ref "index.md" >}}).  This page is about the hosted service at Eclipse.org.  For more information on the Hudson project itself, or to download Hudson, please see the
[Hudson project page](http://eclipse.org/hudson/).

<!--more-->

# General Information

Hudson instances are maintained by the Eclipse Webmasters. The Hudson CI servers are available in two different offerings (each explained below):

* Hudson Shared instance: https://hudson.eclipse.org/hudson/
* Hudson Instance Per Project (HIPP): https://hudson.eclipse.org/

## Asking for Help 

* Need help setting up your instance: contact webmaster @ eclipse.org or your project mentors
* Need help actually building your code: ask your project mentors, or ask on the Common Build mailing list (cbi-dev).  There are no dumb questions.
* Subscribe to cbi-dev here: https://dev.eclipse.org/mailman/listinfo/cbi-dev

## Hudson configuration and tools  

Both Shared and HIPP Hudson setups use SLES 11 x86_64 machines for Linux slaves.  Windows 7 and Mac OS X slaves are available for UI testing on the Shared instance. These servers are behind a firewall so any outbound http(s) connections are proxied.

The following global variables are set(identically across installs):

* JVM_OPTS: proxy data (see "Accessing the Internet" below)
* ANT_ARGS: proxy data
* ANT_OPTS: proxy data

Each node also has a .m2/settings.xml file with the proxy data.

# HIPP 

HIPP (Hudson Instance Per Project) instances are recommended for those projects who prefer flexibility and convenience with their CI system, perhaps at the expense of security and webmaster support.  A single Linux master is provided, and the instance is run under the security context of your project. Optionally, a project's Hudson instance can be configured to write into a project's downloads area and can be given write access to the code repository for automatic tagging of builds. This does create a security risk - see https://bugs.eclipse.org/bugs/show_bug.cgi?id=375350#c42 for a fix.  Webmasters will install most plugins you request, including the Gerrit plugin, but will offer little support.  In time, projects will be offered self-serve restarts and re-imaging of their instances.

## Requesting a HIPP instance 

Please file [https://bugs.eclipse.org/bugs/enter_bug.cgi?product=Community&component=Hudson a bug] against Eclipse Foundation > Community > Hudson to request your project's own instance. Please ensure your project lead can +1 the request. Please specify if you would like to use the Gerrit Trigger plugin, and if you wish to grant write access to your download or code repositories.

{{Note|About write access| If your git repo is handled by gerrit, granting write access to your code repositories is a different procedure, so you must ask specifically for it. If you don't use gerrit, then granting write access to your download area automatically grants write access to your code repositories and vice-versa.}}

{{important|Security issues| There may be security issues related to using the Gerrit plugin and there may be security issues related to allowing the CI system to write directly to your code repos and downloads area. If you request plugins other than those available on the Shared instance, webmaster may not be able to help troubleshoot any issues that you may encounter with your instance.}}

## HIPP slaves 

Platforms available as HIPP slaves:
* Fedora 20 x86_64
* CentOS 7 x86_64
* OpenSuSE 13.1 x86_64

To request a HIPP slave, please [https://bugs.eclipse.org/bugs/enter_bug.cgi?product=Community&component=Hudson&short_desc=HIPP%20slave%20needed%20for%20my%20project file a bug].

# Shared Instance

The Shared instance is recommended for general purpose builds and tests, and for all UI tests. Shared Hudson has several build slaves, a limited yet stable tool set, and full webmaster support. Shared Hudson cannot write into your downloads area or tag releases in your Git repo.  Furthermore, the Gerrit trigger plugin is not permitted to run here.

## Choosing the right slave

* '''hudson-slave1, hudson-slave2, hudson-slave4 and hudson-slave6''' - these are the main build nodes for Hudson jobs. You can specify them by name or by using the 'build2' label.
* '''hudson-slave5''' - this is an ia64 slave.
* '''hudson-slave7, hudson-slave8''' - these are ppc64 slaves located at OSUOSL.
* '''fastlane''' - this slave is intended for usage during a release train crunch when re-spins may require more capacity than hudson-slave1&amp;2 can provide. By default jobs should not run here.
* '''hudson-perf1-tests''' - this slave is used for running performance tests ONLY.
* '''mac-tests and windows7tests''' - these 2 slaves are meant for running UI tests for their respective OS versions. By default jobs should not run on either slave.

See also: [[Hudson server performance metrics]]

# Server 

[[Image:Build infra layout.png|thumb|Build and Hudson storage layout]] Three tiers of storage are available for storing Workspaces, build artifacts, nightly and release builds. For optimal build performance and service availability, it is important that you use each storage device according to its intended purpose.

The image on the right illustrates the three storage tiers and their intended purpose.

## Tools (and locations)

* Maven 2.2.1 (installed automatically)
* Maven 3.0 alpha 5 (installed automatically)
* Maven 3.0-alpha-5-local (/shared/common/apache-maven-3.0-alpha-5)
* Maven 3.0-alpha-6-local (/shared/common/apache-maven-3.0-alpha-6)
* Maven 3.0-Beta-1 (/shared/common/apache-maven-3.0-beta-1)

* Sun Java 5 SR 22 64bit (/shared/common/jdk-1.5.0-22.x86_64)
* Sun Java 5 R 16 32bit (/shared/common/jdk-1.5.0_16)
* Sun Java 5 R 22 64bit (/shared/common/jdk-1.5.0-22.x86_64)
* Sun Java 6 R 21 32bit (/shared/common/sun-jdk1.6.0_21_i586)
* Sun Java 6 R 21 64bit (/shared/common/sun-jdk1.6.0_21_x64)

* Apache-ant-1.8.1 (/shared/common/apache-ant-1.8.1)
* Apache-ant-1.7.1 (/shared/common/apache-ant-1.7.1)
* Apache-ant-1.7.0 (/shared/common/apache-ant-1.7.0)
* Apache-ant-1.6.5 (/shared/common/apache-ant-1.6.5)

* Headless Buckminster 3.6 (/shared/common/buckminster-3.6)
* Buckminster 3.6 Integration (installed automatically)

## Accessing the Internet using Proxy 

Each Hudson instance has unrestricted access to the Internet by using proxy.eclipse.org. The shell environment variables below are set for the Hudson build user. If your build process overrides, or bypasses these variables, you must instruct your tools to use the proxy service to access external sites.

    ftp_proxy=http://proxy.eclipse.org:9898
    http_proxy=http://proxy.eclipse.org:9898
    https_proxy=http://proxy.eclipse.org:9898
    no_proxy='localhost, 127.0.0.1, 172.30.206.0, eclipse.org'

    JAVA_ARGS="-Dhttp.proxyHost=proxy.eclipse.org -Dhttp.proxyPort=9898 -Dhttps.proxyHost=proxy.eclipse.org -Dhttps.proxyPort=9898 -Dhttp.nonProxyHosts=*.eclipse.org -Dhttps.nonProxyHosts=*.eclipse.org -Dftp.proxyHost=proxy.eclipse.org -Dftp.proxyPort=9898 -Dftp.nonProxyHosts=*.eclipse.org"

    JVM_OPTS="-Dhttp.proxyHost=proxy.eclipse.org -Dhttp.proxyPort=9898 -Dhttps.proxyHost=proxy.eclipse.org -Dhttps.proxyPort=9898 -DhttpnonProxyHosts=*.eclipse.org -DhttpsnonProxyHosts=*.eclipse.org -Dftp.proxyHost=proxy.eclipse.org -Dftp.proxyPort=9898 -DftpnonProxyHosts=*.eclipse.org"

    ANT_ARGS="-Dhttp.proxyHost=proxy.eclipse.org -Dhttp.proxyPort=9898 -Dhttps.proxyHost=proxy.eclipse.org -Dhttps.proxyPort=9898 -DhttpnonProxyHosts=*.eclipse.org -DhttpsnonProxyHosts=*.eclipse.org -Dftp.proxyHost=proxy.eclipse.org -Dftp.proxyPort=9898 -DftpnonProxyHosts=*.eclipse.org"

    ANT_OPTS="-Dhttp.proxyHost=proxy.eclipse.org -Dhttp.proxyPort=9898 -Dhttps.proxyHost=proxy.eclipse.org -Dhttps.proxyPort=9898 -DhttpnonProxyHosts=*.eclipse.org -DhttpsnonProxyHosts=*.eclipse.org -Dftp.proxyHost=proxy.eclipse.org -Dftp.proxyPort=9898 -DftpnonProxyHosts=*.eclipse.org"

If you're experiencing connection issues while '''running Eclipse-based applications or tests on Hudson''', it most likely means that your RCP application or test runtime is missing the org.eclipse.core.net.''platform'' fragment. The simplest way to add it for all platforms is to make your test application includes the ''org.eclipse.platform'' feature. More details at [[Tycho/FAQ#How_to_configure_HTTP_proxy_settings_during_test_execution]]

### Why use a Proxy?  

The purpose of the Proxy for Hudson is not for security -- we use firewalls for that. The proxy is used for three specific reasons:

* The Eclipse Hudson environment is expected to grow to a large number of slaves for builds and for tests. If each of those slaves requires a routable IP address, the Foundaton will be required to acquire (at cost) additional IP blocks, which further complicates routing and firewall setups.
* A proxy will allow us to track and monitor external dependencies that are downloaded at build time, for IP purposes.
* A proxy will enable us to implement caching at the proxy level, should the CI mechanism begin to download the entire world and consume too much bandwidth.

### Configuring a proxy for the p2 director 

The p2 director does not respect the "http.proxyHost" etc. options passed in on command line. But, since the p2 director is an Eclipse application, one way to configure the proxy settings is to set the configuration file '''configuration/.settings/org.eclipse.core.net.prefs''' in the director installation as below. [Note: this are roughly accurate as of 03/15/2013. They may change from time to time, over the years, so if they don't seem to work, contact the webmasters to ask if they have changed or differ for different machines.] Notice this example has "systemProxiesEnabled" set to true. This is because on Hudson, from time to time, the webmasters may change the proxy configuration for each slave differently, so if you can take advantage of the system-set proxies, you will be better off. If not, the provided values will apply if you set "systemProxiesEnabled" to false. Also note that the "socks proxy" should not be set in Eclipse, unless you know for sure you have a true socks proxy, or else all traffic will be routed through that, so if its not a true socks-level proxy none of the others will work. The proxy for Hudson is not a socks proxy, just HTTP, HTTPS, and FTP protocols. Also note, a full "Eclipse Platform", where native providers are provided, such as on Windows, will automatically detect and fill-in the system proxies. But, this auto-detection won't work if you have a "bare" p2-director app or something like the platform's "basebuilder"; in which case you must provide them with a script such as the following. See {{bug|401964}} for some discussion of these issues, among many other network and infrastructure issues.
  # add proxy configuration
  cat > "${directorInstallDirectory}/director/configuration/.settings/org.eclipse.core.net.prefs" <<EOF
  eclipse.preferences.version=1
  org.eclipse.core.net.hasMigrated=true
  proxiesEnabled=true
  systemProxiesEnabled=true
  nonProxiedHosts=172.30.206.*
  proxyData/HTTP/hasAuth=false
  proxyData/HTTP/host=proxy.eclipse.org
  proxyData/HTTP/port=9898
  proxyData/HTTPS/hasAuth=false
  proxyData/HTTPS/host=proxy.eclipse.org
  proxyData/HTTPS/port=9898
  EOF
The easiest way to obtain this file is to configure an Eclipse installation on your own computer (in Eclipse, go to '''Window > Preferences > General > Network Connections'''), and then copy the configuration that Eclipse wrote in '''<eclipse>/configuration/.settings/org.eclipse.core.net.prefs'''.

### Configuring for a proxy on Windows OS 

On the Windows operating system, proxy settings (and exceptions to using the proxy) can be set in "Internet Options". These are "detected" by Eclipse and set in "native" values of proxy preferences, but, apparently, from searching eclipse bugs for "proxies", some functions in Eclipse use these preferences and others do not. In any case, you might HAVE to set the Windows Internet Options proxy exceptions and in some cases it might make things easier. (For one case of details/history, see bug {{bug|372880}}.

# Additional Troubleshooting Tips  

## Buckminster CVS materializing: proxy error: Forbidden 

From Martin Taal, via [http://www.eclipse.org/forums/index.php?t=tree&goto=628738#page_top Forums]:

Buckminster cvs materializing, uses a proxy, how is this configured?

To finish this thread. Michael Wenz pointed me to a change made in the cdo build (to solve this issue), a snippet from his email to me:

But I saw that the CDO build is green again and they still do an Ant call from Hudson that again triggers Buckminster. Previously that build failed with the same exception as ours did or do.

Not sure what these guys changed, but I saw that they added something in their build.xml that seems to fix this. I found 2 snippets that appear to be in connection with this: ... &lt;condition property="no.proxy" value="${env.no_proxy}, dev.eclipse.org" else="dev.eclipse.org"&gt; &lt;isset property="env.no_proxy" /&gt; &lt;/condition&gt; ...

... <!-- Launch the eclipse application --> &lt;java fork="true" jar="${@{app}.launcher}" dir="${@{app}.deploy.dir}" failonerror="true"&gt; &lt;env key="no_proxy" value="${no.proxy}" /&gt; &lt;properties /&gt; <!-- Uncomment to debug <jvmarg value=" -agentlib:jdwp=transport=dt_socket,address=8000,server=y,sus pend=y "/> --> &lt;args /&gt; &lt;/java&gt; ...

## Hudson for Committers  

* [[Common Build Infrastructure/Getting Started/Build In Hudson|Build in Hudson]] - Information on requesting jobs, running jobs, setting up builds.
* [[Building an RCP application with hudson (Buckminster)|RCP apps with Buckminster on Hudson]]
* [[Teneo/Teneo Build Setup|Building an Eclipse project (Teneo) with Buckminster and Hudson]]
* [[Common Build Infrastructure/Getting Started#In_Hudson|Athena Common Builder on Hudson]]
* [[Hudson/Maven|Maven on Hudson]]
* [[Hudson/HowTo|How to....]]

## Hudson for Committer Project-level Administration 

Normally "project level" administration is defined for a Hudson job. This allows for only one or a few committers to have "full access" to the job, to do builds, change the configuration, or even delete the job. To give access to everyone, say to "read" the builds, you can add the user "anonymous" and mark the "read" check box. Typically, it is desired to have some "in between" access to all the committers of a project, for example, to maybe any committer can kick off a build, but only the project-level administrator can change the configuration. If this is desired, there is a "role" groups that can be used instead of listing all committers by name. The "role" name is formed by perpending "ROLE_" to the upper case version of the Linux group that defines the committers. For example, EPP committers are authorized using the Linux group ''technology.packaging'', so their Hudson group would be ROLE_TECHNOLOGY.PACKAGING. So, as an example, the project level authorization might look like the following, from the Hudson "configure project" page: <br>

[[Image:Projectlevel.png|Example Project Level Security settings]]

If using the Promoted Builds plugin with a Promotion Criterion of "Only when manually approved", you can also use "role" groups (using the aforementioned "ROLE_" syntax). In fact, you *should* at least restrict the approvers to the group of project committers, as otherwise any anonymous can run a promotion job ([https://bugs.eclipse.org/bugs/show_bug.cgi?id=424619 Bug 424619]).

## Hudson for Administrators  

* [[Common Build Infrastructure/Managing Hudson|Manage Hudson]]
* [[Hudson/Admin/UpgradeHudson|Upgrade Hudson]]
* [[Hudson/Admin/Installed Plugins|Installed Plugins]]

### Duties of Administrators  

#Hudson upgrades and restarts
#New Hudson accounts
#Add plugins
#Set policy for Hudson usage
#Watch changes to this wiki page
#Monitor the Hudson Inbox.

### Who are the Administrators 

* Eclipse Webmasters - webmaster@eclipse.org
* David Williams

You can contact the Hudson admins by opening [https://bugs.eclipse.org/bugs/enter_bug.cgi?product=Community&component=Hudson a bug]

## Hudson for Distributed Builds  

*Testing on Multiple Platforms
*What is the Test-Slave Node?
*How do I use the Test Slave Node to run Tests?
