#! /usr/bin/env bash
#*******************************************************************************
# Copyright (c) 2020 Eclipse Foundation and others.
# This program and the accompanying materials are made available
# under the terms of the Eclipse Public License 2.0
# which is available at http://www.eclipse.org/legal/epl-v20.html
# SPDX-License-Identifier: EPL-2.0
#*******************************************************************************

# Bash strict-mode
set -o errexit
set -o nounset
set -o pipefail

IFS=$'\n\t'

# Json that will be used for finding keystore metadata can either be passed to stdin or
# as the file path in $1
JSON_FILE="${1:-"/dev/stdin"}"
SERVICE_JSON=$(<"${JSON_FILE}")

KUBECTL_OPT=()
TEMP_FILES=()

for ENTRY in $(jq -r '.keystore.entries | map(tostring) | join("\n")' <<<"${SERVICE_JSON}"); do
  ENTRY_NAME="$(jq -r '.name' <<<"${ENTRY}")"

  echo "INFO: Processing keychain '${ENTRY_NAME}'"

  KEYCHAIN_FILE="$(mktemp)"
  PASSWD_FILE="$(mktemp)"

  TEMP_FILES+=(${KEYCHAIN_FILE} ${PASSWD_FILE})

  pass $(jq -r '.keychain.pass' <<<"${ENTRY}") >> "${KEYCHAIN_FILE}"
  pass $(jq -r '.password.pass' <<<"${ENTRY}") >> "${PASSWD_FILE}"

  KEYCHAIN_FILENAME=$(jq -r '.keychain.filename' <<<"${ENTRY}")
  PASSWD_FILENAME=$(jq -r '.password.filename' <<<"${ENTRY}")

  KUBECTL_OPT+=("--from-file=${KEYCHAIN_FILENAME}=${KEYCHAIN_FILE}")
  KUBECTL_OPT+=("--from-file=${PASSWD_FILENAME}=${PASSWD_FILE}")
done

# apply keystore to the cluster
kubectl create secret generic "$(jq -r '.keystore.secretName' <<<"${SERVICE_JSON}")" \
  --namespace "$(jq -r '.kube.namespace' <<<"${SERVICE_JSON}")" \
  "${KUBECTL_OPT[@]}" \
  --dry-run=client -o yaml | kubectl apply -f -

for TMP_FILE in "${TEMP_FILES[@]}"
do
  # echo "Deleting temp file: ${TMP_FILE}"
  rm -f "${TMP_FILE}"
done
