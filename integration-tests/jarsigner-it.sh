#! /bin/bash

source "$(dirname "${0}")/init.sh"

TARGET_FOLDER="${TARGET_FOLDER}/jarsigner-it"

mkdir -p "${TARGET_FOLDER}"
pushd "${TARGET_FOLDER}" > /dev/null

GROUP_ID="org.acme"
ARTIFACT_ID="my-app"

# generate and compile a sample project
rm -rf "${ARTIFACT_ID}"
mvn -B -ff -U archetype:generate -DgroupId="${GROUP_ID}" -DartifactId="${ARTIFACT_ID}"
pushd ${ARTIFACT_ID} > /dev/null

java -cp "${TARGET_FOLDER}/../../../webservice/signing/jar/target/*" org.eclipse.cbi.webservice.signing.jar.TestServer -port 3831 &
SERVER_PID=$!

cat >pom.xml <<EOL
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
  xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/maven-v4_0_0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <groupId>org.acme</groupId>
  <artifactId>my-app</artifactId>
  <packaging>jar</packaging>
  <version>1.0-SNAPSHOT</version>
  <name>my-app</name>
  <url>http://maven.apache.org</url>
  <dependencies>
    <dependency>
      <groupId>junit</groupId>
      <artifactId>junit</artifactId>
      <version>3.8.1</version>
      <scope>test</scope>
    </dependency>
  </dependencies>
  <build>
    <pluginManagement>
      <plugins>
        <plugin>
          <groupId>org.eclipse.cbi.maven.plugins</groupId>
          <artifactId>eclipse-jarsigner-plugin</artifactId>
          <version>1.2.0-SNAPSHOT</version>
          <executions>
            <execution>
              <goals>
                <goal>sign</goal>
              </goals>
              <configuration>
                <signerUrl>http://localhost:3831/jarsigner</signerUrl>
              </configuration>
            </execution>
          </executions>
        </plugin>
      </plugins>
    </pluginManagement>
    <plugins>
      <plugin>
        <groupId>org.eclipse.cbi.maven.plugins</groupId>
        <artifactId>eclipse-jarsigner-plugin</artifactId>
        <version>1.2.0-SNAPSHOT</version>
      </plugin>
    </plugins>
  </build>
</project>
EOL

mvn -B -ff -U clean package

kill ${SERVER_PID}

jarsigner -verify -strict "target/${ARTIFACT_ID}-1.0-SNAPSHOT.jar" | grep "jar verified"

popd > /dev/null
popd > /dev/null
