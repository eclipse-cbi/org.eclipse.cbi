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

function fail() {
  echo "[FAILURE] ${@}"
  if [[ ! -e "${LOGFILE}" ]]; then
    echo "[FAILURE] Content of '${LOGFILE}'"
    echo "=== $(basename "${LOGFILE}") ======================================"
    cat "${LOGFILE}"
    echo "=== $(basename "${LOGFILE}") ======================================"
  fi
  exit 1
}

function testSuccess() {
  local script="${1}"
  local expectedLog="${2}"

  if ${script}; then
    if ! cat "${LOGFILE}" | grep -q "${expectedLog}"; then
      fail "'${script}' should have written \"${expectedLog}\" in '${LOGFILE}'"
    else 
      echo -n "" > "${LOGFILE}"
    fi
  else
    fail "'${script}' should have passed"
  fi
}

function testFailure() {
  local script="${1}"
  local expectedLog="${2}"

  if ! ${script}; then
    if ! cat "${LOGFILE}" | grep -q "${expectedLog}"; then
      fail "'${script}' should have written \"${expectedLog}\" in '${LOGFILE}'"
    else 
      echo -n "" > "${LOGFILE}"
    fi
  else
    fail "'${script}' should have failed"
  fi
}

if [[ -z "${JAVA_HOME}" ]]; then
  fail "[FAILURE] 'JAVA_HOME' must be set for these tests to work"
fi

##############################################################################
## Test working directory creation
rm -rf "${SCRIPT_REALPATH}/target/${SCRIPT_REALNAME}"
mkdir -p "${SCRIPT_REALPATH}/target/${SCRIPT_REALNAME}"
pushd "${SCRIPT_REALPATH}/target/${SCRIPT_REALNAME}" > /dev/null
##############################################################################

##############################################################################
## Test Config
STOREPASS="dumbkeystorepass"

cat >testconfig <<EOL
KEYSTORE="$(pwd)/acme.jks"
STOREPASSFILE="$(pwd)/acme.jks.passwd"
ALIASNAME="Eclipse" # do not use somethign different, as ant-tasks search for ECLIPSE.SF

QUEUE="$(pwd)/queue"
QUEUE_LOCK_PREFIX="\${QUEUE}.lock"
LOGFILE="$(pwd)/signer.log"

SAFE_WD=("$(pwd)/test-staging" "$(pwd)/test-staging2")

SIGNER_USERNAME="$(whoami)"
SIGN_SERVER_HOSTNAME="127.0.0.1"
SIGN_SERVER_PORT="54783"

DEFAULT_JAVA_VERSION="java6"

JAR_PROCESSORS_java6="$(pwd)/jarprocessor.jar"
JAR_PROCESSORS_java7="$(pwd)/jarprocessor.jar"
JAR_PROCESSORS_java8="$(pwd)/jarprocessor.jar"

JDKS_java6="${JDKS_java6:-$(pwd)/jdks/1.6}"
JDKS_java7="${JDKS_java7:-$(pwd)/jdks/1.7}"
JDKS_java8="${JDKS_java8:-$(pwd)/jdks/1.8}"

JARSIGNER_OPTIONS="\${JARSIGNER_OPTIONS:-}"
TSA_URL="https://timestamp.geotrust.com/tsa"
EOL

## end of config
##############################################################################

export CONFIG="$(pwd)/testconfig"
export CONFIG_SECRET="$(pwd)/testconfig"
source "$(pwd)/testconfig" # to make the confi variable available to the test env.
source "${SCRIPT_REALPATH}/../sign-lib.shs"

##############################################################################
## Test data initialization 

# create log file
rm -f "${LOGFILE}"
touch "${LOGFILE}"

# JAVA_HOME will be used for all java versions, but we just want to test that
# the script retrieve the proper path.
if [[ ! -d "${JDKS_java6}" ]]; then
  mkdir -p "$(dirname "${JDKS_java6}")"
  ln -s "${JAVA_HOME}" "${JDKS_java6}"
fi
if [[ ! -d "${JDKS_java7}" ]]; then
  mkdir -p "$(dirname "${JDKS_java7}")"
  ln -s "${JAVA_HOME}" "${JDKS_java7}"
fi
if [[ ! -d "${JDKS_java8}" ]]; then
  mkdir -p "$(dirname "${JDKS_java8}")"
  ln -s "${JAVA_HOME}" "${JDKS_java8}"
fi

# store keystore password in a file
echo "${STOREPASS}" > "${STOREPASSFILE}"

# generate dummy keystore
rm -f "${KEYSTORE}"
${JAVA_HOME}/bin/keytool -genkey -keyalg RSA -alias "${ALIASNAME}" -keystore "${KEYSTORE}" -storepass "${STOREPASS}" -keypass "${STOREPASS}" -validity 360 -keysize 2048 -dname "CN=User, OU=Test, O=Acme, L=Bli, ST=World, C=Internet"

# get jarprocessor
wget -q "http://download.eclipse.org/equinox/drops/R-Mars.2-201602121500/org.eclipse.equinox.p2.jarprocessor_1.0.400.v20150430-1836.jar" -O jarprocessor.jar

## Copy test data into test-staging folder
mkdir test-staging
mkdir test-staging2
mkdir output-staging

cp "${SCRIPT_REALPATH}/data/hello.jar" "test-staging/hello.jar"

##############################################################################