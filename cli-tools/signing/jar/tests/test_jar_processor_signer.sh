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

source "${SCRIPT_REALPATH}/init.sh"

function testSuccessJarProcessorSigner() {
  local script="${1}"
  local file="${2}"
  local expectedLog="${3}"

  if ${script} ${file}; then
    if ! cat "${LOGFILE}" | grep -q "${expectedLog}"; then
      fail "'${script}' should have written \"${expectedLog}\" in '${LOGFILE}'"
    else 
      echo "" > "${LOGFILE}"
    fi
  else
    fail "'${script}' should have worked on '${file}'"
  fi
}

function testFailureJarProcessorSigner() {
  local script="${1}"
  local file="${2}"
  local expectedLog="${3}"

  if ! ${script} ${file}; then
    if ! cat "${LOGFILE}" | grep -q "${expectedLog}"; then
      fail "'${script}' should have written \"${expectedLog}\" in '${LOGFILE}'"
    else 
      echo "" > "${LOGFILE}"
    fi
  else
    fail "'${script}' should have failed on '${file}'"
  fi
}

testFailureJarProcessorSigner ${SCRIPT_REALPATH}/../jar_processor_signer.sh test-staging/hello.jar "'JAVA_VERSION' cannot be inferred"
testFailureJarProcessorSigner ${SCRIPT_REALPATH}/../jar_processor_signer_java8.sh test-staging/notexisiting.jar "File 'test-staging/notexisiting.jar' not found"

cp test-staging/hello.jar test-staging/hello.notjar
testFailureJarProcessorSigner ${SCRIPT_REALPATH}/../jar_processor_signer_java8.sh test-staging/hello.notjar "File 'test-staging/hello.notjar' is not a Jar file"

cp test-staging/hello.jar test-staging/hello.8.jar
testSuccessJarProcessorSigner ${SCRIPT_REALPATH}/../jar_processor_signer_java8.sh test-staging/hello.8.jar "Signing Jar file 'test-staging/hello.8.jar' with '${JDKS_java8}/bin/jarsigner'"
if ! jar_is_signed test-staging/hello.8.jar; then
  fail "test-staging/hello.8.jar should be signed"
fi

cp test-staging/hello.jar test-staging/hello.7.jar
testSuccessJarProcessorSigner ${SCRIPT_REALPATH}/../jar_processor_signer_java7.sh test-staging/hello.7.jar "Signing Jar file 'test-staging/hello.7.jar' with '${JDKS_java7}/bin/jarsigner'"
if ! jar_is_signed test-staging/hello.7.jar; then
  fail "test-staging/hello.7.jar should be signed"
fi

cp test-staging/hello.jar test-staging/hello.6.jar
testSuccessJarProcessorSigner ${SCRIPT_REALPATH}/../jar_processor_signer_java6.sh test-staging/hello.6.jar "Signing Jar file 'test-staging/hello.6.jar' with '${JDKS_java6}/bin/jarsigner'"
if ! jar_is_signed test-staging/hello.6.jar; then
  fail "test-staging/hello.6.jar should be signed"
fi

echo "[SUCCESS] All tests passed"
