 ###############################################################################
 # Copyright (c) 2015 Eclipse Foundation and others
 # All rights reserved. This program and the accompanying materials
 # are made available under the terms of the Eclipse Public License v1.0
 # which accompanies this distribution, and is available at
 # http://www.eclipse.org/legal/epl-v10.html
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

mkdir -p "${BUILD_DIR}"
rm -rf "${BUILD_DIR}/*"

TEST_APP_PATH="/shared/cbi/sanity-check/org.eclipse.rt.osgistarterkit.product-macosx.cocoa.x86_64.tar.gz"
TEST_APP=$(basename ${TEST_APP_PATH})

USER_NAME="genie.cbi"
USER_HOME="/Users/${USER_NAME}"

SERVER_PORT=59388
SERVER_NAME="mac-tests2"
SERVER="${USER_NAME}@${SERVER_NAME}"

REMOTE_PATH="${USER_HOME}/integration-tests/${JOB_NAME}/${BUILD_NUMBER}/dmg-packaging-service"
ssh ${SERVER} "find ${USER_HOME}/integration-tests/${JOB_NAME} ! -name ${JOB_NAME} ! -name ${BUILD_NUMBER} -maxdepth 1 -exec rm -rf {} \; && mkdir -p \"${REMOTE_PATH}\""

PACKAGING_SERVICE_PATH_SPEC="dmg-packaging-testing"
PACKAGING_SERVICE_URL="http://${SERVER_NAME}:${SERVER_PORT}/${PACKAGING_SERVICE_PATH_SPEC}/1.0.0-SNAPSHOT"
PACKAGING_SERVICE_PROPERTIES="dmg-packaging-service.properties"

cat > "${BUILD_DIR}/${PACKAGING_SERVICE_PROPERTIES}" <<- EOF
server.port=${SERVER_PORT}
server.access.log=${REMOTE_PATH}/dmg-packaging-service-yyyy_mm_dd.request.log
server.temp.folder=${REMOTE_PATH}/tmp/
server.service.pathspec=/${PACKAGING_SERVICE_PATH_SPEC}

macosx.keychain=${USER_HOME}/Library/Keychains/login.keychain
macosx.keychain.password=${USER_HOME}/login.keychain.passwd
macosx.certificate=Integration Test Certificate
macosx.security.unlock.timeout=20
macosx.codesign.timeout=600
macosx.codesign.timestamp=http://sha256timestamp.ws.symantec.com/sha256/timestamp

# Root logger option
log4j.rootLogger=INFO, file

# Redirect log messages to a log file, support file rolling.
log4j.appender.file=org.apache.log4j.RollingFileAppender
log4j.appender.file.File=${REMOTE_PATH}/dmg-packaging-service.log
log4j.appender.file.MaxFileSize=10MB
log4j.appender.file.MaxBackupIndex=10
log4j.appender.file.layout=org.apache.log4j.PatternLayout
log4j.appender.file.layout.ConversionPattern=%d{yyyy-MM-dd HH:mm:ss} %-5p %c{1}:%L - %m%n
EOF

# copying the newly build jar
scp "${SCRIPT_PATH}/../target/dmg-packaging-service-"*".jar" ${SERVER}:"${REMOTE_PATH}"
scp "${BUILD_DIR}/${PACKAGING_SERVICE_PROPERTIES}" ${SERVER}:${REMOTE_PATH}/${PACKAGING_SERVICE_PROPERTIES}
# finding it on the remote machine
JAR_FILE=$(ssh ${SERVER} "find \"${REMOTE_PATH}\" -name \"dmg-packaging-service-*.jar\" | sort | tail -n 1")

# clone create-dmg repo on remote
ssh ${SERVER} "cd ${REMOTE_PATH} && git clone https://github.com/andreyvit/create-dmg.git"

PID=""
function clean_up {
  if [ ! -z ${PID} ]; then
    ssh ${SERVER} "kill -9 ${PID}"
  fi
  exit
}

trap clean_up SIGHUP SIGINT SIGTERM EXIT

# starts the server on the remote machine and stores its PID
PID=$(ssh ${SERVER} "cd ${REMOTE_PATH}; nohup \$(/usr/libexec/java_home -v 1.8)/bin/java -jar \"${JAR_FILE}\" -c \"${PACKAGING_SERVICE_PROPERTIES}\" &> \"stdout_stderr\" < /dev/null & echo \$!")

#let the JVM and jetty start
sleep 5

# copy the App to be signed from a known location
cp "${TEST_APP_PATH}" "${BUILD_DIR}"
if [ ! -f "${BUILD_DIR}/${TEST_APP}" ]; then
  echo "Can not copy '${TEST_APP_PATH}'"
fi

TEST_APP_SIMPLE_NAME=$(echo ${TEST_APP} | sed -e 's/\.tar\.gz//g')

# invoke web service
signRetCode=$(curl -o "${BUILD_DIR}/${TEST_APP_SIMPLE_NAME}.dmg" --write-out '%{http_code}\n' -F sign=true -F source=@"${BUILD_DIR}/${TEST_APP}" ${PACKAGING_SERVICE_URL})

if [ "$signRetCode" -ne "200" ]; then
  echo "Packaging has failed:"
  tail "${BUILD_DIR}/${TEST_APP_SIMPLE_NAME}.dmg"
  exit 1
fi

scp "${BUILD_DIR}/${TEST_APP_SIMPLE_NAME}.dmg" ${SERVER}:${REMOTE_PATH}/
ssh ${SERVER} "hdiutil imageinfo -format ${REMOTE_PATH}/${TEST_APP_SIMPLE_NAME}.dmg"

[ "$?" -eq "0" ] || { echo "create-dmg: ${TEST_APP_SIMPLE_NAME}.dmg is not properly packaged"; exit 1; }
