 ###############################################################################
 # Copyright (c) 2015 Eclipse Foundation and others
 # This program and the accompanying materials
 # are made available under the terms of the Eclipse Public License 2.0
 # which accompanies this distribution, and is available at
 # https://www.eclipse.org/legal/epl-2.0
 #
 # SPDX-License-Identifier: EPL-2.0
 #
 # Contributors:
 #   Mikaël Barbero - initial implementation
 ###############################################################################
#!/bin/bash

set -o nounset
set -o errexit
set -o errtrace
set -o functrace

shopt -s nullglob
shopt -s extglob
shopt -s expand_aliases

set -xe

SCRIPT_PATH="$(dirname "${0}")"
BUILD_DIR="$(pwd)/${SCRIPT_PATH}/../target/integration-tests"
JAVA8_HOME="/shared/common/jdk1.8.0_x64-latest"

SIGNING_SERVICE_PORT=64940
SIGNING_SERVICE_PATH_SPEC="signing-jar-testing"
SIGNING_SERVICE_VERSION="1.0.0-SNAPSHOT"
SIGNING_SERVICE_URL="http://localhost:${SIGNING_SERVICE_PORT}/${SIGNING_SERVICE_PATH_SPEC}/${SIGNING_SERVICE_VERSION}"

SIGNING_SERVICE_PROPERTIES="${BUILD_DIR}/jarsigner.properties"

KEYSTORE="${BUILD_DIR}/keystore"
KEYSTORE_PASSWD="integrationTest"
KEYSTORE_PASSWD_FILE="${BUILD_DIR}/keystore.passwd"

mkdir -p ${BUILD_DIR} && rm -rf ${BUILD_DIR}/*

echo ${KEYSTORE_PASSWD} > "${KEYSTORE_PASSWD_FILE}"
${JAVA8_HOME}/bin/keytool -genkey -keyalg RSA \
	-keystore "${KEYSTORE}" \
	-storepass "${KEYSTORE_PASSWD}" \
	-keypass "${KEYSTORE_PASSWD}" \
	-alias example.org \
	-dname "CN=localhost, O=Example.org"

cat > "${SIGNING_SERVICE_PROPERTIES}" <<- EOF
server.port=${SIGNING_SERVICE_PORT}
server.access.log=${BUILD_DIR}/jarsigner-yyyy_mm_dd.request.log
server.temp.folder=${BUILD_DIR}/tmp/
server.service.pathspec=/${SIGNING_SERVICE_PATH_SPEC}

jarsigner.bin=${JAVA8_HOME}/bin/jarsigner
jarsigner.keystore=${KEYSTORE}
jarsigner.keystore.password=${KEYSTORE_PASSWD_FILE}
jarsigner.keystore.alias=example.org
jarsigner.tsa=http://sha256timestamp.ws.symantec.com/sha256/timestamp
jarsigner.timeout=120

jarsigner.http.proxy.host=proxy.eclipse.org
jarsigner.http.proxy.port=9898
jarsigner.https.proxy.host=proxy.eclipse.org
jarsigner.https.proxy.port=9898

# Root logger option
log4j.rootLogger=INFO, file

# Redirect log messages to a log file, support file rolling.
log4j.appender.file=org.apache.log4j.RollingFileAppender
log4j.appender.file.File=${BUILD_DIR}/jarsigner.log
log4j.appender.file.MaxFileSize=10MB
log4j.appender.file.MaxBackupIndex=10
log4j.appender.file.layout=org.apache.log4j.PatternLayout
log4j.appender.file.layout.ConversionPattern=%d{yyyy-MM-dd HH:mm:ss} %-5p %c{1}:%L - %m%n
EOF

PID=""
function clean_up {
  if [ ! -z ${PID} ]; then
    kill -9 ${PID}
  fi
  exit
}

trap clean_up SIGHUP SIGINT SIGTERM EXIT

JAR_FILE=$(echo ${SCRIPT_PATH}/../target/jar-signing-service-*.jar | tr ' ' '\n' | grep -v tests.jar)
# starts the server and stores its PID
PID=$(nohup ${JAVA8_HOME}/bin/java -jar "${JAR_FILE}" -c "${SIGNING_SERVICE_PROPERTIES}" &> "${BUILD_DIR}/stdout_stderr" < /dev/null & echo $!)

#let the JVM and jetty start
sleep 5

# send the request
SIGNED_JAR=${BUILD_DIR}/$(basename ${JAR_FILE} | sed -e 's/\.jar/.signed.jar/g')
signRetCode=$(curl -o "${SIGNED_JAR}" --write-out '%{http_code}\n' -F file=@"${JAR_FILE}" "${SIGNING_SERVICE_URL}")

if [ "$signRetCode" -ne "200" ]; then
  echo "Signing has failed:"
  tail -n100 "${SIGNED_JAR}"
  exit 1
fi

${JAVA8_HOME}/bin/jarsigner -verify -strict -verbose "${SIGNED_JAR}" | grep "jar verified"

[ "$?" -eq "0" ] || { echo "jarsigner: '${SIGNED_JAR}' is not properly signed"; exit 1; }
