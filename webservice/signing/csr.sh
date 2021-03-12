#! /usr/bin/env bash
#*******************************************************************************
# Copyright (c) 2021 Eclipse Foundation and others.
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

# must be in pkcs8.pem format
PRIVATE_KEY_PASS="${1}"
CERT_PASS="${2}"

# the output CSR file
CSR="${3}"

# Generating random password
KEYSTORE_PASSWD="$(mktemp)"
pwgen -s -a -1 63 > "${KEYSTORE_PASSWD}"

PKPEM="$(mktemp)"
# Extracting private key from pass
pass "${PRIVATE_KEY_PASS}" > "${PKPEM}"

CERT="$(mktemp)"
# Extracting certificate from pass (the Subject of the CSR will reuse the info from this certificate)
pass "${CERT_PASS}" > "${CERT}"

KEYSTORE="$(mktemp)"
# Converting private key to pkcs12
openssl pkcs12 -export -in "${CERT}" -inkey "${PKPEM}" -name entryname -passout "file:${KEYSTORE_PASSWD}" > "${KEYSTORE}"

# Generating CSR
keytool -certreq -alias entryname -file "${CSR}" -keystore "${KEYSTORE}" -storepass:file "${KEYSTORE_PASSWD}"

openssl req -text -in "${CSR}" -noout -verify

rm -f "${KEYSTORE}" "${CERT}" "${PKPEM}" "${KEYSTORE_PASSWD}"