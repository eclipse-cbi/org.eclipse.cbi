# Requirement

* Java 8+

# How to run it?

	java -jar target/signing-macosx-X.Y.Z.jar -c etc/signing-macosx.properties

# How to build it?

	mvn clean verify

# How to daemonize it?

Use launchd (see [http://launchd.info/](http://launchd.info/) and [Apple's documentation](https://developer.apple.com/library/mac/documentation/Darwin/Reference/ManPages/man5/launchd.plist.5.html))

In the `launchd/` folder, there are two files:

* `start.sh` a simple start script to put in `/Users/genie/signing` (along with the `jar`). It will automatically choose the latest version of the JAR in this directory.
* `org.eclipsefoundation.signing.macosx.plist`, the daemon descriptor, to copy in `/Library/LaunchDaemons`.

To start the daemon (**will** be started automatically in case of reboot thanks to `-w` option):

	launchctl load -w /Library/LaunchDaemons/org.eclipsefoundation.signing.macosx.plist

To stop the daemon (will **not** be restarted after the next reboot because of the `-w` option):

	launchctl unload -w /Library/LaunchDaemons/org.eclipsefoundation.signing.macosx.plist

ie `-w` make load and unload permanent.
