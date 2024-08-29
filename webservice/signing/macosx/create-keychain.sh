#!/bin/bash

# Usage:
#
# ./create-keychain.sh <certificate file> <private key file> <private key password file>
#
# Create Application keychain:
# ./create-keychain.sh developerID_application.cer private_key.p12 private_key.passwd
#
# Create Installer keychain:
# ./create-keychain.sh developerID_installer.cer private_key.p12 private_key.passwd
#
# Resulting keychain is stored in file keychain-export.p12
# Keychain password is stored in file keychain-export.passwd

DIR=$(pwd)
KEYCHAIN="${DIR}/temp.keychain"
KEYCHAIN_PASSWD="$(pwgen -s -y -1 24)"

if [ -f "${KEYCHAIN}" ];
then
  echo "Deleting keychain: ${KEYCHAIN}"
  rm -f "${KEYCHAIN}"
fi

echo "Creating kechain: ${KEYCHAIN}"
security create-keychain -p "${KEYCHAIN_PASSWD}" "${KEYCHAIN}"

echo "Update keychain search list"
security list-keychain -s $(security list-keychains | grep -v "${KEYCHAIN}" | xargs) "${KEYCHAIN}"
security list-keychain

CERTIFICATE=${1}
echo "Import certificate: ${CERTIFICATE}"
security import "${CERTIFICATE}" -k "${KEYCHAIN}"
PRIVATE_KEY=${2}
PRIVATE_KEY_PASSWORD=${3}
echo "Import private key: ${PRIVATE_KEY}"
security import "${PRIVATE_KEY}" -k "${KEYCHAIN}" -P "$(cat $PRIVATE_KEY_PASSWORD)"

security show-keychain-info "${KEYCHAIN}"

EXPORT="${DIR}/keychain-export.p12"
echo "Export identity to ${EXPORT}"
security export -k "${KEYCHAIN}" -t identities -f pkcs12 -o "${EXPORT}" -P "${KEYCHAIN_PASSWD}"

echo "${KEYCHAIN_PASSWD}" > "${DIR}/keychain-export.passwd"

security list-keychain -s $(security list-keychains | grep -v "${KEYCHAIN}" | xargs)
