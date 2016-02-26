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

CONFIG="${CONFIG:-${SCRIPT_REALPATH}/config}"
source "${CONFIG}"
source "${SCRIPT_REALPATH}/sign-lib.shs"

umask 0007

LOCK="${QUEUE_LOCK_PREFIX}.${$}"

function log_prefix() {
  printf "%s:%s:QUEUE(%s)" "$(date +%Y-%m-%d\ %H:%M:%S)" "$(hostname)" "${$}"
}

function process_queue_file() {
  local queue_file="${1}"
  local log="${2}"

  for queue_line in $(cat "${queue_file}"); do
    local requestDate=$(echo -n ${queue_line} | awk -F: {'print $1'})
    local signerUsername=$(echo -n ${queue_line} | awk -F: {'print $2'})
    local file=$(echo -n ${queue_line} | awk -F: {'print $3'})
    local queueOption=$(echo -n ${queue_line} | awk -F: {'print $4'})
    local outputDir=$(echo -n ${queue_line} | awk -F: {'print $5'})
    local skiprepack=$(echo -n ${queue_line} | awk -F: {'print $6'})
    local javaVersion=$(echo -n ${queue_line} | awk -F: {'print $7'})
    
    if [[ -z "${file}" || ! -f "${file}" || ("${file}" != *.jar && "${file}" != *.zip) ]]; then
      error "$(log_prefix): File '${file}' is not valid jar or zip file. Skipping." >> "${log}" 2>&1
      continue
    fi
    
    if [[ -z "${outputDir}" ]]; then
      outputDir="$(dirname "${file}")"
    elif [[ ! -d "${outputDir}" ]]; then
      error "$(log_prefix): Output directory '${outputDir}' is not a valid directory. Skipping." >> "${log}" 2>&1
      continue
    fi
  
    if [[ ! "${javaVersion}" =~ ^java[0-9]+$ ]]; then 
      error "$(log_prefix): Java version '${javaVersion}' in queue file is invalid. Expecting something like '^java[0-9]+$'. Skipping." >> "${log}" 2>&1
      continue
    fi

    info "$(log_prefix): Processing queue item '${file}'" >> "${log}" 2>&1
    
    local jarProcessor="$(dynvar "JAR_PROCESSORS_${javaVersion}")"
    local JDK="$(dynvar "JDKS_${javaVersion}")"
    local signingScript="${SCRIPT_REALPATH}/jar_processor_signer_${javaVersion}.sh"
    info "$(log_prefix): Using '${JDK}/bin/java' to run '${jarProcessor}'" >> "${log}" 2>&1
    
    if [[ -z "$skiprepack" ]]; then
      local repack="-repack"
    else 
      local repack=""
    fi

    debug "$(log_prefix): Executing '${JDK}/bin/java -jar "${jarProcessor}" -outputDir "${outputDir}" "${repack}" -verbose -processAll -sign "${signingScript}" "${file}"'" >> "${log}" 2>&1
    "${JDK}/bin/java" -jar "${jarProcessor}" -outputDir "${outputDir}" "${repack}" -verbose -processAll -sign "${signingScript}" "${file}" >> "${log}" 2>&1
    
    #alter ownership to match that of the source file
    if [ -f "${outputDir}/$(basename "${file}")" ]; then
      # for some reasons, when -repack is not specified, jar files are not put in the -outputDir by the jarprocessor!!
      # do no try to alter ownership as "${outputDir}/$(basename "${file}")" does not exist
      chgrp "$(stat -c %G "${file}")" "${outputDir}/$(basename "${file}")" 2>/dev/null
    fi
    
  done
}

# read stdin to see if we need to sign now
read -t 2 stdin || true

if [[ -n "${stdin:-}" ]]; then
  
  printf "%s\n" "${stdin}" > "${LOCK}"
  process_queue_file "${LOCK}" "/dev/stdout"
  rm "${LOCK}"
  
elif [[ -f "${QUEUE}" ]]; then
  if [[ -e /proc/loadavg && $(awk -F. '{print $1}' /proc/loadavg) > 40 ]]; then 
    warning "Too busy to sign. Going to sleep." >> "${LOGFILE}" 2>&1
    exit 128; 
  fi
  
  info "$(log_prefix): Begin processing queue: ${$}" >> "${LOGFILE}" 2>&1
  
  mv "${QUEUE}" "${LOCK}"
  
  debug "====> start of (${$}) contents" >> "${LOGFILE}" 2>&1
  cat "${LOCK}" >> "${LOGFILE}" 2>&1
  debug "====> end of (${$}) contents" >> "${LOGFILE}" 2>&1
  
  process_queue_file "${LOCK}" "${LOGFILE}"
  
  info "$(log_prefix): Finished processing queue." >> "${LOGFILE}" 2>&1
  
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
