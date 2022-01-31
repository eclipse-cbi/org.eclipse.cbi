#!/bin/sh

##############################################################################
# Copyright (c) 2005-2016 Eclipse Foundation and others.
# This program and the accompanying materials
# are made available under the terms of the Eclipse Public License 2.0
# which accompanies this distribution, and is available at
# https://www.eclipse.org/legal/epl-2.0
#
# SPDX-License-Identifier: EPL-2.0
#
# Contributors:
#    Denis Roy (Eclipse Foundation)
#    Mikaël Barbero (Eclipse Foundation)
##############################################################################

# called by jarprocessor.jar to invoke jarsigner on a jar file. This is run by genie

# switch to strict mode
set -o nounset
set -o errexit
set -o pipefail

shopt -s nullglob
shopt -s extglob
shopt -s expand_aliases

SCRIPT_NAME="$(basename ${0})"
SCRIPT_PATH="$(dirname "${0}")"
SCRIPT_READLINK="$(readlink -e -n "${0}")"
SCRIPT_REALNAME="$(basename "${SCRIPT_READLINK}")"
SCRIPT_REALPATH="$(dirname "${SCRIPT_READLINK}")"

CONFIG="${CONFIG:-${SCRIPT_REALPATH}/config}"
source "${CONFIG}"

CONFIG_SECRET="${CONFIG_SECRET:-${SCRIPT_REALPATH}/config.secret}"
source "${CONFIG_SECRET}"

source "${SCRIPT_REALPATH}/sign-lib.shs"

if [[ $UID = 0 ]]; then
  error "Running as root is forbidden. Exiting." >> "${LOGFILE}" 2>&1
  exit 9
fi

function print_usage() {
  local scriptname="${0}"
  printf "Usage: %s <file>\n" "${scriptname}"
  printf "    Signs a JAR file <file>\n"
}

FILE="${1:-}"

if [[ -z "${FILE}" ]]; then
  print_usage "${SCRIPT_NAME}"
  exit 7
fi

function log_prefix() {
	local GPPID="/proc not available"
	if [[ -e /proc/$PPID/status ]]; then # to be able to run tests on other system
		GPPID=$(cat /proc/$PPID/status | grep "PPid" | awk '{print $2}')
	fi
  printf "%s:%s(%s):SIGNER(%s)" "$(date +%Y-%m-%d\ %H:%M:%S)" "$(hostname)" "${UID}" "${GPPID}"
}

# this script should be the target of a symlink whose name must end with java[0-9]+ 
JAVA_VERSION="$(batch_signer_java_version "${SCRIPT_NAME}")"
if [[ -z "${JAVA_VERSION}" ]]; then
  error "$(log_prefix):Variable 'JAVA_VERSION' cannot be inferred from '${SCRIPT_NAME}'." >> "${LOGFILE}" 2>&1
  error "    You should call this script from a symlink whose name ends with 'java[0-9]+'." >> "${LOGFILE}" 2>&1
  error "    Signing aborted." >> "${LOGFILE}" 2>&1
  exit 16
fi
JDK="$(dynvar "JDKS_${JAVA_VERSION}")"
if [[ -z "${JDK}" ]]; then
  error "$(log_prefix):Variable 'JDK' is undefined." >> "${LOGFILE}" 2>&1
  error "    Java version '${JAVA_VERSION}' is probably unknown." >> "${LOGFILE}" 2>&1
  error "    Signing aborted." >> "${LOGFILE}" 2>&1
  exit 16
fi

info "$(log_prefix):Asked to sign '${FILE}'" >> "${LOGFILE}" 2>&1

if [[ ! -f "${FILE}" ]]; then
  error "$(log_prefix):File '${FILE}' not found, or not a valid file. Exiting." >> "${LOGFILE}" 2>&1
  exit 2
fi;

if [[ "${FILE}" != *.jar ]]; then
  error "$(log_prefix):File '${FILE}' is not a Jar file. Exiting." >> "${LOGFILE}" 2>&1
  exit 3
fi

info "$(log_prefix):Signing Jar file '${FILE}' with '${JDK}/bin/jarsigner'" >> "${LOGFILE}" 2>&1

umask 0007
STOREPASS=$(cat $STOREPASSFILE)
"${JDK}/bin/jarsigner" ${JARSIGNER_OPTIONS:+${JARSIGNER_OPTIONS} }-tsa "${TSA_URL}" -verbose -keystore "${KEYSTORE}" -storepass "${STOREPASS}" "${FILE}" "${ALIASNAME}"  >> "${LOGFILE}" 2>&1
unset STOREPASS
chmod g+w ${FILE} # TODO: is it still required?

info "$(log_prefix):Finished signing '${FILE}'" >> "${LOGFILE}" 2>&1

exit 0