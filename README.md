CBI plugins is a set of Maven plugins that enable projects to use the
Eclipse infrastructure to sign their build artifacts via Maven.

wiki: [http://wiki.eclipse.org/CBI]([cbi-wiki])

mailing list: [https://dev.eclipse.org/mailman/listinfo/cbi-dev]([cbi-list])

bugzilla: [https://bugs.eclipse.org]([cbi-bugzilla])

Using the plugins
=================

Add the CBI repository to your Maven &lt;pluginRepositories&gt; section

    https://repo.eclipse.org/content/repositories/cbi-releases/

    *** Note the macsigner and winsigner plugins are available
        starting with version 1.0.4 or later.


Using eclipse-jarsigner-plugin
==============================

This plugin will sign your artifacts during the build and product signed jar
jar files. The implementation of this plugin signs the artifacts during when
a each specific artifact is built so they are signed individually allowing
Maven and Tycho to gather the already signed artifacts when creating
repositories or products. As such a signing profile should be created in your
parent pom to enable this plugin.

If your project is a Maven project using the plugin to sign your Maven
jars can be done by adding a section.


    <profiles>
      <profile>
        <id>sign</id>
        <build>
          <plugins>
            <plugin>
              <groupId>org.eclipse.cbi.maven.plugins</groupId>
              <artifactId>eclipse-jarsigner-plugin</artifactId>
              <version>1.0.5</version>
              <executions>
                <execution>
                  <id>sign</id>
                  <phase>verify</phase>
                  <goals>
                    <goal>sign</goal>
                  </goals>
                </execution>
              </executions>
            </plugin>
          </plugins>
        </build>
      </profile>
    </profiles>

It is possible to override the default signing URL to use a service outside
of the Eclipse Infrastructure. There are 2 ways to accomplish this.

### Method 1: via commandline

eclipse-jarsigner-plugin: -Dcbi.jarsigner.signerUrl=http://localhost

### Method 2: via pom.xml

    <configuration>
      <signerUrl>http://localhost</signerUrl>
    </configuration>

If your project's a Tycho project that needs pack200 to be included in
in your p2 repository you can use the Tycho pack200 plugin along with the
eclipse-jarsigner-plugin as follows:

    <profilse>
      <profile>
        <id>eclipse-sign</id>
        <build>
          <plugins>
            <plugin>
              <groupId>org.eclipse.tycho</groupId>
              <artifactId>target-platform-configuration</artifactId>
              <version>0.18.0</version>
              <configuration>
                <includePackedArtifacts>false</includePackedArtifacts>
              </configuration>
            </plugin>
            <plugin>
              <groupId>org.eclipse.tycho.extras</groupId>
              <artifactId>tycho-pack200a-plugin</artifactId>
              <version>0.18.0</version>
              <executions>
                <execution>
                  <id>pack200-normalize</id>
                  <goals>
                    <goal>normalize</goal>
                  </goals>
                  <phase>verify</phase>
                </execution>
              </executions>
            </plugin>
            <plugin>
              <groupId>org.eclipse.cbi.maven.plugins</groupId>
              <artifactId>eclipse-jarsigner-plugin</artifactId>
              <version>1.0.5</version>
              <executions>
                <execution>
                  <id>sign</id>
                  <goals>
                    <goal>sign</goal>
                  </goals>
                  <phase>verify</phase>
                </execution>
              </executions>
            </plugin>
            <plugin>
              <groupId>org.eclipse.tycho.extras</groupId>
              <artifactId>tycho-pack200b-plugin</artifactId>
              <version>0.18.0</version>
              <executions>
                <execution>
                  <id>pack200-pack</id>
                  <goals>
                    <goal>pack</goal>
                  </goals>
                  <phase>verify</phase>
                </execution>
              </executions>
            </plugin>
            <plugin>
              <groupId>org.eclipse.tycho</groupId>
              <artifactId>tycho-p2-plugin</artifactId>
              <version>0.18.0</version>
              <executions>
                <execution>
                  <id>p2-metadata</id>
                  <goals>
                    <goal>p2-metadata</goal>
                  </goals>
                  <phase>verify</phase>
                </execution>
              </executions>
              <configuration>
                <defaultP2Metadata>false</defaultP2Metadata>
              </configuration>
            </plugin>
          </plugins>
        </build>
      </profile>
    </profiles>

More details on Tycho pack200 can be found on the Tycho wiki page:
    http://wiki.eclipse.org/Tycho/Pack200


Using eclipse-macsigner-plugin
==============================

Here is the standard POM configuration:

    <plugin>
      <groupId>org.eclipse.cbi.maven.plugins</groupId>
      <artifactId>eclipse-macsigner-plugin</artifactId>
      <version>1.0.5</version>
      <executions>
        <execution>
          <id>sign</id>
          <phase>package</phase>
          <goals>
            <goal>sign</goal>
          </goals>
          <configuration>
            <signFiles>
              <signFile>${project.build.directory}/products/org.eclipse.sdk.ide/macosx/cocoa/x86/eclipse/eclipse.app</signFile>
              <signFile>${project.build.directory}/products/org.eclipse.sdk.ide/macosx/cocoa/x86_64/eclipse/eclipse.app</signFile>
            </signFiles>
          </configuration>
        </execution>
      </executions>
    </plugin>

An alternate configuration is:

        <configuration>
          <baseSearchDir>${project.build.directory}/products/org.eclipse.sdk.ide</baseSearchDir>
          <fileNames>
            <fileName>eclipse.app</fileName>
          </fileNames>
        </configuration>

${fileNames} sets the app names that you would like signed and
${baseSearchDir} sets the directory that the plugin will recursively
search for them from. eclipse.app is the default value for ${fileNames}
and ${project.build.directory}/products/${project.artifactId}/ is the
default value for ${baseSearchDir}, so it is not necessary to set these
variables if this is the configuration that you want.

If the ${signFiles} variable is set then the plugin will only sign those
specific directories (if they exists) and nothing else, even if
${fileNames} and  ${baseSearchDir} are set.

Please note that only the "Contents" folder will be added to a zip archive
and sent to the signing service for signing because only that folder is
relevant for Mac OS code signing. After signing, the signed archive will
be received from the download service and extracted back into the app
folder.

In case you need to provide custom rules for resource signing to the signing
service please create a standard CodeResources file in
"..app/Contents/\_CodeSignature" folder. The Eclipse signing service will
detect an existing file and use it as input for the the Mac OS code signing
tool to specify rules such as which resources should be excluded, etc.


It is possible to override the default signing URL to use a service outside
of the Eclipse Infrastructure. There are 2 ways to accomplish this.

### Method 1: via commandline

  -Dcbi.macsigner.signerUrl=http://localhost

### Method 2: via pom.xml

    <configuration>
      <signerUrl>http://localhost</signerUrl>
    </configuration>


Using eclipse-winsigner-plugin
==============================

Here is the standard POM configuration:

    <plugin>
      <groupId>org.eclipse.cbi.maven.plugins</groupId>
      <artifactId>eclipse-winsigner-plugin</artifactId>
      <version>1.0.5</version>
      <executions>
        <execution>
          <id>sign</id>
          <goals>
            <goal>sign</goal>
          </goals>
          <phase>package</phase>
          <configuration>
            <signFiles>
              <signFile>${project.build.directory}/products/org.eclipse.sdk.ide/win32/win32/x86/eclipse/eclipse.exe</signFile>
              <signFile>${project.build.directory}/products/org.eclipse.sdk.ide/win32/win32/x86/eclipse/eclipsec.exe</signFile>
            </signFiles>
          </configuration>
        </execution>
      </executions>
    </plugin>

An alternate configuration is:

        <configuration>
          <baseSearchDir>${project.build.directory}/products/org.eclipse.sdk.ide</baseSearchDir>
          <fileNames>
            <fileName>eclipse.exe</fileName>
            <fileName>eclipsec.exe</fileName>
          </fileNames>
        </configuration>

${fileNames} sets the executable names that you would like signed and
${baseSearchDir} sets the directory that the plugin will recursively
search for them from. eclipse.exe and eclipsec.exe are the default
values for ${fileNames} and ${project.build.directory}/products/${project.artifactId}/
is the defualt value for ${baseSearchDir}, so it is not necessary to
set these variables if this is the configuration that you want.

If the ${signFiles} variable is set then the plugin will only sign those
specific files (if they exists) and nothing else, even if ${fileNames}
and ${baseSearchDir} are set.

It is possible to override the default signing URL to use a service outside
of the Eclipse Infrastructure. There are 2 ways to accomplish this.

### Method 1: via commandline

  -Dcbi.winsigner.signerUrl=http://localhost

### Method 2: via pom.xml

    <configuration>
      <signerUrl>http://localhost</signerUrl>
    </configuration>


[cbi-wiki]: http://wiki.eclipse.org/CBI
[cbi-list]: https://dev.eclipse.org/mailman/listinfo/cbi-dev
[cbi-bugzilla]: https://bugs.eclipse.org/bugs/buglist.cgi?list_id=6321580&classification=Eclipse%20Foundation&query_format=advanced&bug_status=UNCONFIRMED&bug_status=NEW&bug_status=ASSIGNED&bug_status=REOPENED&product=CBI
