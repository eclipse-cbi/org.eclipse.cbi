#!/bin/bash

##############################################################################
# Copyright (c) 2016 Eclipse Foundation and others.
# All rights reserved. This program and the accompanying materials
# are made available under the terms of the Eclipse Public License v1.0
# which accompanies this distribution, and is available at
# http://www.eclipse.org/legal/epl-v10.html
#
# Contributors:
#    Mikaël Barbero (Eclipse Foundation)
##############################################################################

# switch to strict mode
set -o nounset
set -o errexit
set -o pipefail

shopt -s nullglob
shopt -s extglob
shopt -s expand_aliases

export SCRIPT_NAME="$(basename ${0})"
export SCRIPT_PATH="$(dirname "${0}")"
export SCRIPT_READLINK="$(readlink -e -n "${0}")"
export SCRIPT_REALNAME="$(basename "${SCRIPT_READLINK}")"
export SCRIPT_REALPATH="$(dirname "${SCRIPT_READLINK}")"

set -x
source "${SCRIPT_REALPATH}/init.sh"

# Script Under Test
SUT="${SCRIPT_REALPATH}/../sign"

if ${SUT} > /dev/null ; then
  fail "$(basename ${SUT}) should have failed with 0 arguments"
fi 

if ${SUT} test-staging/notexisiting.file > /dev/null 2>&1; then
  fail "$(basename ${SUT}) should have failed with non existing file"
fi

cp test-staging/hello.jar test-staging/hello.notjar
if ${SUT} test-staging/hello.notjar > /dev/null 2>&1; then
  fail "$(basename ${SUT}) should have failed with non jar/zip file"
fi

cp test-staging/hello.jar test-staging/hello.aleadysigned.jar
${SCRIPT_REALPATH}/../jar_processor_signer_java8.sh test-staging/hello.aleadysigned.jar
if ! jar_is_signed test-staging/hello.aleadysigned.jar; then
  fail "test-staging/hello.aleadysigned.jar should have been signed"
elif ! ${SUT} test-staging/hello.aleadysigned.jar 2>&1 | grep -q "already signed, skipping"; then
  fail "$(basename ${SUT}) should have skipped already signed jar 'test-staging/hello.aleadysigned.jar'"
fi
echo -n "" > "${LOGFILE}"

mkdir -p "test-staging.not"
cp test-staging/hello.jar test-staging.not/hello.1.jar
if ${SUT} test-staging.not/hello.1.jar > /dev/null 2>&1; then
  fail "Should have failed to sign in unchecked folder 'test-staging.not'"
fi

cp test-staging/hello.jar test-staging/hello.5.jar
if ${SUT} test-staging/hello.5.jar /tmp > /dev/null 2>&1; then
  fail "Should have failed to sign in unchecked output folder '/tmp'"
fi

cp test-staging/hello.jar test-staging/hello.2.jar
python "${SCRIPT_REALPATH}/tcpecho.py" "${SIGN_SERVER_HOSTNAME}" "${SIGN_SERVER_PORT}" > tcpecho 2>&1  &
${SUT} test-staging/hello.2.jar > /dev/null 2>&1
if ! grep -q "$(whoami):test-staging/hello.2.jar:now:test-staging::${DEFAULT_JAVA_VERSION}" tcpecho; then
  fail "Expecting '$(whoami):test-staging/hello.2.jar:now:test-staging::${DEFAULT_JAVA_VERSION}'"
fi

python "${SCRIPT_REALPATH}/tcpecho.py" "${SIGN_SERVER_HOSTNAME}" "${SIGN_SERVER_PORT}" > tcpecho 2>&1  &
${SUT} test-staging/hello.jar test-staging2 > /dev/null 2>&1 
if ! grep -q "$(whoami):test-staging/hello.jar:now:test-staging2::${DEFAULT_JAVA_VERSION}" tcpecho; then
  fail "Expecting '$(whoami):test-staging/hello.jar:now:test-staging2::${DEFAULT_JAVA_VERSION}'"
fi

python "${SCRIPT_REALPATH}/tcpecho.py" "${SIGN_SERVER_HOSTNAME}" "${SIGN_SERVER_PORT}" > tcpecho 2>&1  &
${SUT} test-staging/hello.jar skiprepack > /dev/null 2>&1
if ! grep -q "$(whoami):test-staging/hello.jar:now:test-staging:skiprepack:${DEFAULT_JAVA_VERSION}" tcpecho; then
 fail "Expecting '$(whoami):test-staging/hello.jar:now:test-staging:skiprepack:${DEFAULT_JAVA_VERSION}'"
fi

cp test-staging/hello.jar test-staging2/hello.3.jar
chmod 600 test-staging2/hello.3.jar
${SUT} test-staging2/hello.3.jar mail > /dev/null 2>&1
if [[ "$(stat -c %a test-staging2/hello.3.jar)" != *620 ]]; then
  fail "Should have changed permissions on 'test-staging2/hello.3.jar' (currently=$(stat -c %a test-staging2/hello.3.jar))"  
fi
echo -n "" > "${QUEUE}"

chmod 755 test-staging2
cp test-staging/hello.jar test-staging/hello.4.jar
${SUT} test-staging/hello.4.jar test-staging2 mail > /dev/null 2>&1
if [[ "$(stat -c %a test-staging2)" != *775 ]]; then
  fail "Should have changed permissions on output dir 'test-staging2' (currently=$(stat -c %a test-staging2))"
fi
echo -n "" > "${QUEUE}"

${SUT} test-staging/hello.jar nomail test-staging2 > /dev/null 2>&1
if ! grep -q "$(whoami):test-staging/hello.jar:nomail:test-staging2::${DEFAULT_JAVA_VERSION}" "${QUEUE}"; then
  fail "Expecting '$(whoami):test-staging/hello.jar:nomail:test-staging2::${DEFAULT_JAVA_VERSION}'"
fi
echo -n "" > "${QUEUE}"

${SUT} test-staging/hello.jar nomail skiprepack > /dev/null 2>&1
if ! grep -q "$(whoami):test-staging/hello.jar:nomail:test-staging:skiprepack:${DEFAULT_JAVA_VERSION}" "${QUEUE}"; then
 fail "Expecting '$(whoami):test-staging/hello.jar:nomail:test-staging:skiprepack:${DEFAULT_JAVA_VERSION}'"
fi
echo -n "" > "${QUEUE}"

${SUT} test-staging/hello.jar nomail skiprepack test-staging2 > /dev/null 2>&1
if ! grep -q "$(whoami):test-staging/hello.jar:nomail:test-staging2:skiprepack:${DEFAULT_JAVA_VERSION}" "${QUEUE}"; then
 fail "Expecting '$(whoami):test-staging/hello.jar:nomail:test-staging2:skiprepack:${DEFAULT_JAVA_VERSION}'"
fi
echo -n "" > "${QUEUE}"

 ${SUT} test-staging/hello.jar nomail test-staging2 skiprepack > /dev/null 2>&1
if ! grep -q "$(whoami):test-staging/hello.jar:nomail:test-staging2:skiprepack:${DEFAULT_JAVA_VERSION}" "${QUEUE}"; then
 fail "Expecting '$(whoami):test-staging/hello.jar:nomail:test-staging2:skiprepack:${DEFAULT_JAVA_VERSION}'"
fi
echo -n "" > "${QUEUE}"

${SUT} test-staging/hello.jar test-staging2 nomail > /dev/null 2>&1
if ! grep -q "$(whoami):test-staging/hello.jar:nomail:test-staging2::${DEFAULT_JAVA_VERSION}" "${QUEUE}"; then
  fail "Expecting '$(whoami):test-staging/hello.jar:nomail:test-staging2::${DEFAULT_JAVA_VERSION}'"
fi
echo -n "" > "${QUEUE}"

${SUT} test-staging/hello.jar skiprepack nomail > /dev/null 2>&1 
if ! grep -q "$(whoami):test-staging/hello.jar:nomail:test-staging:skiprepack:${DEFAULT_JAVA_VERSION}" "${QUEUE}"; then
 fail "Expecting '$(whoami):test-staging/hello.jar:nomail:test-staging:skiprepack:${DEFAULT_JAVA_VERSION}'"
fi
echo -n "" > "${QUEUE}"

${SUT} test-staging/hello.jar mail skiprepack > /dev/null 2>&1
if ! grep -q "$(whoami):test-staging/hello.jar:mail:test-staging:skiprepack:${DEFAULT_JAVA_VERSION}" "${QUEUE}"; then
 fail "Expecting '$(whoami):test-staging/hello.jar:mail:test-staging:skiprepack:${DEFAULT_JAVA_VERSION}'"
fi
echo -n "" > "${QUEUE}"

export JAR_PROCESSOR_JAVA="java8"
${SUT} test-staging/hello.jar mail skiprepack > /dev/null 2>&1
if ! grep -q "$(whoami):test-staging/hello.jar:mail:test-staging:skiprepack:java8" "${QUEUE}"; then
 fail "Expecting '$(whoami):test-staging/hello.jar:mail:test-staging:skiprepack:java8'"
fi
echo -n "" > "${QUEUE}"
unset JAR_PROCESSOR_JAVA

mkdir -p test-staging/java7/inputfile
cp test-staging/hello.jar test-staging/java7/inputfile
${SUT} test-staging/java7/inputfile/hello.jar test-staging2 nomail > /dev/null 2>&1
if ! grep -q "$(whoami):test-staging/java7/inputfile/hello.jar:nomail:test-staging2::java7" "${QUEUE}"; then
 fail "Expecting '$(whoami):test-staging/java7/inputfile/hello.jar:nomail:test-staging2::java7'"
fi
echo -n "" > "${QUEUE}"


mkdir -p "test-staging/plugins"
cp test-staging/hello.jar test-staging/plugins/a1.jar
cp test-staging/hello.jar test-staging/plugins/a2.jar
cp test-staging/hello.jar test-staging/plugins/a3.jar
pushd test-staging > /dev/null && zip -q tobesigned.zip plugins/ plugins/a*.jar && popd > /dev/null

${SUT} test-staging/tobesigned.zip test-staging2 nomail > /dev/null 2>&1
if ! grep -q "$(whoami):test-staging/tobesigned.zip:nomail:test-staging2::java6" "${QUEUE}"; then
 fail "Expecting '$(whoami):test-staging/tobesigned.zip:nomail:test-staging2::java6'"
fi
if ! unzip -l test-staging/tobesigned.zip | grep -q pack.properties; then
  fail "Was expecting a pack.properties file in the zip tobesigned.zip"
fi
if unzip -p test-staging/tobesigned.zip pack.properties | grep -q "pack.excludes"; then
  fail "Should not see pack.excludes in the pack.properties file (in tobesigned.zip)"
fi
if unzip -p test-staging/tobesigned.zip pack.properties | grep -q "sign.excludes"; then
  fail "Should not see sign.excludes in the pack.properties file (in tobesigned.zip)"
fi
echo -n "" > "${QUEUE}"


mkdir -p "test-staging/plugins"
cp test-staging/hello.jar test-staging/plugins/b1.jar
${SCRIPT_REALPATH}/../jar_processor_signer_java8.sh test-staging/plugins/b1.jar
cp test-staging/hello.jar test-staging/plugins/b2.jar
cp test-staging/hello.jar test-staging/plugins/b3.jar
${SCRIPT_REALPATH}/../jar_processor_signer_java8.sh test-staging/plugins/b3.jar

pushd test-staging > /dev/null && zip -q tobesigned_2.zip plugins/ plugins/b*.jar && popd > /dev/null
${SUT} test-staging/tobesigned_2.zip test-staging2 nomail > /dev/null 2>&1
if ! grep -q "$(whoami):test-staging/tobesigned_2.zip:nomail:test-staging2::java6" "${QUEUE}"; then
 fail "Expecting '$(whoami):test-staging/tobesigned_2.zip:nomail:test-staging2::java6'"
fi
if ! unzip -l test-staging/tobesigned_2.zip | grep -q pack.properties; then
  fail "Was expecting a pack.properties file in the zip tobesigned_2.zip"
  unzip -l test-staging/tobesigned_2.zip
fi
if ! unzip -p test-staging/tobesigned_2.zip pack.properties | grep "pack.excludes=plugins/b1.jar,plugins/b3.jar"; then
  fail "Should see pack.excludes in the pack.properties file (in tobesigned_2.zip)"
  unzip -p test-staging/tobesigned_2.zip pack.properties
fi
if ! unzip -p test-staging/tobesigned_2.zip pack.properties | grep "sign.excludes=plugins/b1.jar,plugins/b3.jar"; then
  fail "Should see sign.excludes in the pack.properties file (in tobesigned_2.zip)"
  unzip -p test-staging/tobesigned_2.zip pack.properties
fi
echo -n "" > "${QUEUE}"

echo "[SUCCESS] All tests passed [${SCRIPT_REALNAME}]"