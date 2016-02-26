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
  echo "[FAILURE] Content of '${LOGFILE}'"
  cat "${LOGFILE}"
  exit 1
}

if [[ -z "${JAVA_HOME}" ]]; then
  fail "[FAILURE] 'JAVA_HOME' must be set for these tests to work"
fi

##############################################################################
## Test working directory creation
rm -rf "${SCRIPT_REALPATH}/target/${SCRIPT_REALNAME}"
mkdir -p "${SCRIPT_REALPATH}/target/${SCRIPT_REALNAME}"
pushd "${SCRIPT_REALPATH}/target/${SCRIPT_REALNAME}"
##############################################################################

##############################################################################
## Test Config
export KEYSTORE="$(pwd)/acme.jks"
STOREPASS="dumbkeystorepass"
export STOREPASSFILE="$(pwd)/acme.jks.passwd"
echo "${STOREPASS}" > "${STOREPASSFILE}"
export ALIAS="Acme Inc."

export LOGFILE="$(pwd)/signer.log"

export JAR_PROCESSORS_java6="$(pwd)/jarprocessor.jar"
export JAR_PROCESSORS_java7="$(pwd)/jarprocessor.jar"
export JAR_PROCESSORS_java8="$(pwd)/jarprocessor.jar"

export JDKS_java6="${JDKS_java6:-$(pwd)/jdks/1.6}"
export JDKS_java7="${JDKS_java7:-$(pwd)/jdks/1.7}"
export JDKS_java8="${JDKS_java8:-$(pwd)/jdks/1.8}"
## end of config
##############################################################################

# Will load the sample config, with overriden values above
export CONFIG="${SCRIPT_REALPATH}/../config.sample"
source "${SCRIPT_REALPATH}/../sign-lib.shs"

##############################################################################
## Test data initialization 
# generate dummy keystore
rm -f "${KEYSTORE}"
${JAVA_HOME}/bin/keytool -genkey -keyalg RSA -alias "${ALIAS}" -keystore "${KEYSTORE}" -storepass "${STOREPASS}" -keypass "${STOREPASS}" -validity 360 -keysize 2048 -dname "CN=User, OU=Test, O=Acme, L=Bli, ST=World, C=Internet"

# get jarprocessor
wget -q "http://download.eclipse.org/equinox/drops/R-Mars.2-201602121500/org.eclipse.equinox.p2.jarprocessor_1.0.400.v20150430-1836.jar" -O jarprocessor.jar

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

## Copy test data into test-staging folder
mkdir test-staging

cp "${SCRIPT_REALPATH}/data/hello.jar" "test-staging/hello.jar"

if jar_is_signed "test-staging/hello.jar"; then
  fail "test-staging/hello.jar should not be signed before"
fi
##############################################################################