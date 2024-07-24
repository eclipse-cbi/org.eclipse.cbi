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
TOOLS_IMAGE="eclipse-temurin:11-jdk"

KEYSTORE_TYPE="$(jq -r '.keystore.type // "P12"' <<<"${SERVICE_JSON}")"

TMPDIR="$(readlink -f "${TMPDIR:-/tmp}")"
KEYSTORE="$(mktemp)"
# remove the file immediately as keytool does not like empty files
rm -f "${KEYSTORE}"
KEYSTORE_PASSWD="$(mktemp)"

pass "$(jq -r '.keystore.password.pass' <<<"${SERVICE_JSON}")" > "${KEYSTORE_PASSWD}"

for entry in $(jq -r '.keystore.entries | map(tostring) | join("\n")' <<<"${SERVICE_JSON}"); do
  ENTRY_NAME="$(jq -r '.name' <<<"${entry}")"

  CERTIFICATE_CHAIN="$(mktemp)"
  # concatenate all certificates from entries.certificates inside certificateChain file
  for CERTIFICATE in $(jq -r '.certificates | map(.pass) | join("\n")' <<<"${entry}"); do
    pass "${CERTIFICATE}" >> "${CERTIFICATE_CHAIN}"
  done;

  if [ "${KEYSTORE_TYPE}" = "P12" ]; then
    PRIVATE_KEY="$(mktemp)"
    pass "$(jq -r '.privateKey.pass' <<<"${entry}")" > "${PRIVATE_KEY}"

    # create a proper pfx/p12 file with certificate chain + privatekey
    ENTRY_P12="$(mktemp)"
    docker run --pull=always --rm -u $(id -u):$(id -g) -v "${TMPDIR}:${TMPDIR}" "${TOOLS_IMAGE}" /bin/bash -c \
      "openssl pkcs12 -export -in \"${CERTIFICATE_CHAIN}\" -inkey \"${PRIVATE_KEY}\" -name \"${ENTRY_NAME}\" > \"${ENTRY_P12}\" -passout \"file:${KEYSTORE_PASSWD}\""

    # print certificate expiration date
    echo -n "INFO: Certificate '${ENTRY_NAME}' expires on "
    docker run --pull=always --rm -q -v "${TMPDIR}:${TMPDIR}" "${TOOLS_IMAGE}" /bin/bash -c \
      "openssl pkcs12 -in \"${ENTRY_P12}\" -nodes -passin \"file:${KEYSTORE_PASSWD}\" | openssl x509 -noout -enddate | cut -d'=' -f2"

    # add the p12 cert to a java p12 keystore
    docker run --pull=always --rm -u "${UID}" -v "${TMPDIR}:${TMPDIR}" "${TOOLS_IMAGE}" \
      "keytool" -importkeystore -alias "${ENTRY_NAME}" \
        -srckeystore "${ENTRY_P12}" \
        -srcstoretype pkcs12 \
        -srcstorepass:file "${KEYSTORE_PASSWD}" \
        -destkeystore "${KEYSTORE}" \
        -deststoretype pkcs12 \
        -storepass:file "${KEYSTORE_PASSWD}"

    rm -f "${PRIVATE_KEY}" "${CERTIFICATE_CHAIN}" "${ENTRY_P12}"

  elif [ "${KEYSTORE_TYPE}" = "GOOGLECLOUD" ]; then
    # print certificate expiration date
    echo -n "INFO: Certificate '${ENTRY_NAME}' expires on "
    docker run --pull=always --rm -q -v "${TMPDIR}:${TMPDIR}" "${TOOLS_IMAGE}" /bin/bash -c \
      "openssl x509 -in \"${CERTIFICATE_CHAIN}\" -noout -enddate | cut -d'=' -f2"

    # copy certificate chain to keystore file
    cp ${CERTIFICATE_CHAIN} ${KEYSTORE}

    rm -f "${CERTIFICATE_CHAIN}"
  else
    echo "ERROR: unexpected keystore type '${KEYSTORE_TYPE}'" 1>&2
    exit 1
  fi
done

# apply keystore to the cluster
kubectl create secret generic "$(jq -r '.keystore.secretName' <<<"${SERVICE_JSON}")" \
  --namespace "$(jq -r '.kube.namespace' <<<"${SERVICE_JSON}")" \
  --from-file="$(jq -r '.keystore.filename' <<<"${SERVICE_JSON}")"="${KEYSTORE}" \
  --from-file="$(jq -r '.keystore.password.filename' <<<"${SERVICE_JSON}")"="${KEYSTORE_PASSWD}" \
  --dry-run=client -o yaml | kubectl apply -f -

rm -f "${KEYSTORE}" "${KEYSTORE_PASSWD}"
