#!/bin/bash

##############################################################################
# Copyright (c) 2005-2016 Eclipse Foundation and others.
# All rights reserved. This program and the accompanying materials
# are made available under the terms of the Eclipse Public License v1.0
# which accompanies this distribution, and is available at
# http://www.eclipse.org/legal/epl-v10.html
#
# Contributors:
#    Denis Roy (Eclipse Foundation)
#    Mikaël Barbero (Eclipse Foundation)
##############################################################################

# sign_queue_process.sh: Run as a cronjob, this script processes the signing queue
# and invokes jarprocessor.jar (and sign.sh) for files that need to be signed.

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

source "${SCRIPT_REALPATH}/config"
source "${SCRIPT_REALPATH}/sign-lib.shs"

umask 0007

LOCK="${QUEUE_LOCK_PREFIX}.${$}"

function log_prefix() {
  printf "%s:%s:QUEUE(%s)" "$(date +%Y-%m-%d\ %H:%M:%S)" "$(hostname)" "${$}"
}

function process_queue_file() {
  local queue_file="${1}"
  debug "====> start of (${$}) contents" >> "${LOGFILE}" 2>&1
  cat "${queue_file}" >> "${LOGFILE}" 2>&1
  debug "====> end of (${$}) contents" >> "${LOGFILE}" 2>&1
  
  for queue_line in $(cat "${queue_file}"); do
    REQUEST_DATE=$(echo -n ${queue_line} | awk -F: {'print $1'})
    SIGNER_USERNAME=$(echo -n ${queue_line} | awk -F: {'print $2'})
    FILE=$(echo -n ${queue_line} | awk -F: {'print $3'})
    QUEUE_OPTION=$(echo -n ${queue_line} | awk -F: {'print $4'})
    OUTPUT_DIR=$(echo -n ${queue_line} | awk -F: {'print $5'})
    SKIPREPACK=$(echo -n ${queue_line} | awk -F: {'print $6'})
    JAVA_VERSION=$(echo -n ${queue_line} | awk -F: {'print $7'})
    
    if [[ -z "${FILE}" || ! -f "${FILE}" || ("${FILE}" != *.jar && "${FILE}" != *.zip) ]]; then
      error "$(log_prefix): File '${FILE}' is not valid jar or zip file. Skipping." >> "${LOGFILE}" 2>&1
      continue
    fi
    
    if [[ -z "${OUTPUT_DIR}" ]]; then
      OUTPUT_DIR="$(dirname "${FILE}")"
    elif [[ ! -d "${OUTPUT_DIR}" ]]; then
      error "$(log_prefix): Output directory '${OUTPUT_DIR}' is not a valid directory. Skipping." >> "${LOGFILE}" 2>&1
      continue
    fi
  
    if [[ ! "${JAVA_VERSION}" =~ ^java[0-9]+$ ]]; then 
      error "$(log_prefix): Java version '${JAVA_VERSION}' in queue file is invalid. Expecting something like '^java[0-9]+$'. Skipping." >> "${LOGFILE}" 2>&1
      continue
    fi

    info "$(log_prefix): Processing queue item '${FILE}'" >> "${LOGFILE}" 2>&1
    
    JAR_PROCESSOR="$(dynvar "JAR_PROCESSORS_${JAVA_VERSION}")"
    JDK="$(dynvar "JDKS_${JAVA_VERSION}")"
    SIGNSCRIPT="${SCRIPT_REALPATH}/jar_processor_signer_${JAVA_VERSION}.sh"
    info "$(log_prefix): Using '${JDK}/bin/java' to run '${JAR_PROCESSOR}'" >> "${LOGFILE}" 2>&1
    
    if [[ -z "$SKIPREPACK" ]]; then
      REPACK="-repack"
    else 
      REPACK=""
    fi

    debug "$(log_prefix): Executing '${JDK}/bin/java -jar "${JAR_PROCESSOR}" -outputDir "${OUTPUT_DIR}" "${REPACK}" -verbose -processAll -sign "${SIGNSCRIPT}" "${FILE}"'" >> "${LOGFILE}" 2>&1
    "${JDK}/bin/java" -jar "${JAR_PROCESSOR}" -outputDir "${OUTPUT_DIR}" "${REPACK}" -verbose -processAll -sign "${SIGNSCRIPT}" "${FILE}" >> "${LOGFILE}" 2>&1
    
    #alter ownership to match that of the source file
    if [ -f "${OUTPUT_DIR}/$(basename "${FILE}")" ]; then
      # for some reasons, when -repack is not specified, jar files are not put in the -outputDir by the jarprocessor!!
      # do no try to alter ownership as "${OUTPUT_DIR}/$(basename "${FILE}")" does not exist
      chgrp "$(stat -c %G "${FILE}")" "${OUTPUT_DIR}/$(basename "${FILE}")" 2>/dev/null
    fi
    
  done

  info "$(log_prefix): Finished processing queue." >> "${LOGFILE}" 2>&1
}

# read stdin to see if we need to sign now
read -t 2 stdin || true

if [[ -n "${stdin:-}" ]]; then
  
  info "$(log_prefix): Begin processing queue NOW: ${$}"
  
  printf "%s\n" "${stdin}" > "${LOCK}"
  process_queue_file "${LOCK}"
  rm "${LOCK}"
  
  info "$(log_prefix): Finished signing '${FILE}'."
  
elif [[ -f "${QUEUE}" ]]; then
  if [[ $(awk -F. '{print $1}' /proc/loadavg) > 40 ]]; then 
    warning "Too busy to sign. Going to sleep." >> "${LOGFILE}" 2>&1
    exit 128; 
  fi
  
  info "$(log_prefix): Begin processing queue: ${$}" >> "${LOGFILE}" 2>&1
  
  mv "${QUEUE}" "${LOCK}"
  process_queue_file "${LOCK}"
  
  # send notification e-mails
  for userToBeMailed in $(cat "${LOCK}" | grep ":mail:" | awk -F: {'print $2'} | sort | uniq); do
    # updating the final awk to catch users like dmadruga that have more than a first and last name
    # M. Ward 06/10/09
    # MAIL=$(getent passwd | grep $USERID | awk -F: {'print $5'} | awk {'print $NF'})
    # With the email adress removed from teh gecos field this line needed to be updated. M. Ward 02/08/11
    MAIL="$(ldapsearch -LLL -x "uid=${userToBeMailed}" mail | awk {'getline;print $2'})"
    echo "One or more files placed in the signing queue are now signed." | /usr/bin/mail -s "File Signing Complete" -r "webmaster@eclipse.org" "${MAIL}"
  done
  
  rm "${LOCK}"
fi
